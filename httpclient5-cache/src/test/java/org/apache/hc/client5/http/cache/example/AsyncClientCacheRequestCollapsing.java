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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
import org.apache.hc.client5.http.cache.CacheContextBuilder;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpAsyncClients;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * Demonstrates request collapsing for the async HTTP cache ({@code HTTPCLIENT-1165}).
 * <p>
 * The example starts a local HTTP server that delays responses so concurrent cache misses overlap.
 * With request collapsing enabled, only one request should hit the origin server.
 */
public class AsyncClientCacheRequestCollapsing {

    public static void main(final String[] args) throws Exception {

        final AtomicInteger originHits = new AtomicInteger(0);
        final HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            originHits.incrementAndGet();
            try {
                Thread.sleep(1500);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            final byte[] body = "Hello from origin".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Cache-Control", "max-age=60");
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        final int port = server.getAddress().getPort();
        final HttpHost target = new HttpHost("http", "localhost", port);

        try {
            runRound(target, originHits, false);
            runRound(target, originHits, true);
        } finally {
            server.stop(0);
        }
    }

    private static void runRound(final HttpHost target, final AtomicInteger originHits, final boolean collapsing) throws Exception {
        final int requests = 20;
        final int before = originHits.get();

        try (final CloseableHttpAsyncClient httpclient = CachingHttpAsyncClients.custom()
                .setCacheConfig(CacheConfig.custom()
                        .setMaxObjectSize(200000)
                        .build())
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .setRequestCollapsingEnabled(collapsing)
                .build()) {

            httpclient.start();

            final ExecutorService executor = Executors.newFixedThreadPool(requests);
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>(requests);

            for (int i = 0; i < requests; i++) {
                final int id = i;
                futures.add(executor.submit(() -> {
                    start.await();

                    final SimpleHttpRequest request = SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/")
                            .build();

                    final HttpCacheContext context = CacheContextBuilder.create()
                            .setCacheControl(RequestCacheControl.DEFAULT)
                            .build();

                    final Future<SimpleHttpResponse> future = httpclient.execute(
                            SimpleRequestProducer.create(request),
                            SimpleResponseConsumer.create(),
                            context,
                            null);

                    final SimpleHttpResponse response = future.get();
                    System.out.println("[" + (collapsing ? "collapsed" : "baseline") + "/" + id + "] " + request + " -> " + new StatusLine(response)
                            + " (cache=" + context.getCacheResponseStatus() + ")");
                    return response;
                }));
            }

            start.countDown();
            for (final Future<SimpleHttpResponse> future : futures) {
                future.get();
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        final int after = originHits.get();
        System.out.println();
        System.out.println("Round: collapsing=" + collapsing + ", origin hits=" + (after - before) + " (total=" + after + ")");
        System.out.println();
    }

}
