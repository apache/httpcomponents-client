/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.cache.example;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.io.CloseMode;

/**
 * Demonstrates optional request collapsing for concurrent async cache misses.
 */
public class AsyncClientCacheRequestCollapsing {

    private static final int REQUEST_COUNT = 20;

    public static void main(final String[] args) throws Exception {
        final AtomicInteger originHits = new AtomicInteger();

        final ExecutorService executorService = Executors.newFixedThreadPool(REQUEST_COUNT);
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(executorService);

        server.createContext("/resource", exchange -> {
            final int count = originHits.incrementAndGet();

            try {
                Thread.sleep(500);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            final byte[] body = ("payload-" + count).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CACHE_CONTROL, "public, max-age=60");
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (final OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });

        try {
            server.start();

            final String requestUri = "http://localhost:" + server.getAddress().getPort() + "/resource";

            originHits.set(0);
            executeBurst(requestUri, false);
            System.out.println("Origin hits without request collapsing: " + originHits.get());

            originHits.set(0);
            executeBurst(requestUri, true);
            System.out.println("Origin hits with request collapsing:    " + originHits.get());
        } finally {
            server.stop(0);
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Server executor did not terminate");
            }
        }
    }

    private static void executeBurst(final String requestUri, final boolean requestCollapsingEnabled) throws Exception {
        final CloseableHttpAsyncClient client = CachingHttpAsyncClientBuilder.create()
                .setCacheConfig(CacheConfig.custom()
                        .setRequestCollapsingEnabled(requestCollapsingEnabled)
                        .build())
                .build();

        client.start();
        try {
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>();

            for (int i = 0; i < REQUEST_COUNT; i++) {
                final SimpleHttpRequest request = SimpleRequestBuilder.get(requestUri).build();
                futures.add(client.execute(
                        SimpleRequestProducer.create(request),
                        SimpleResponseConsumer.create(),
                        null));
            }

            for (final Future<SimpleHttpResponse> future : futures) {
                final SimpleHttpResponse response = future.get(5, TimeUnit.SECONDS);
                if (response.getCode() != 200) {
                    throw new IllegalStateException("Unexpected response: " + response.getCode());
                }
            }
        } finally {
            client.close(CloseMode.IMMEDIATE);
        }
    }

}
