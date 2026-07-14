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
package org.apache.hc.client5.http.impl.cache;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestAsyncCachingExecRequestNoStore {

    @Test
    void testResponseToRequestWithNoStoreIsNotCached() throws Exception {
        final AtomicInteger originHits = new AtomicInteger(0);
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> handleOrigin(exchange, originHits));
        final ExecutorService executorService = Executors.newCachedThreadPool();
        server.setExecutor(executorService);
        server.start();

        final CloseableHttpAsyncClient client = CachingHttpAsyncClients.custom()
                .setCacheConfig(CacheConfig.custom().setHeuristicCachingEnabled(false).build())
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .build();
        client.start();
        try {
            final HttpHost target = new HttpHost("http", "localhost", server.getAddress().getPort());

            // First request forbids storage via Cache-Control: no-store.
            final SimpleHttpResponse first = client.execute(
                    SimpleRequestProducer.create(SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/")
                            .addHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                            .build()),
                    SimpleResponseConsumer.create(),
                    HttpCacheContext.create(),
                    null).get(1, TimeUnit.MINUTES);
            Assertions.assertEquals(200, first.getCode());

            // The response was cacheable on its own, so a follow-up request would be a cache hit
            // if it had been stored; since no-store forbade storage, it must miss the cache.
            final HttpCacheContext context = HttpCacheContext.create();
            final SimpleHttpResponse second = client.execute(
                    SimpleRequestProducer.create(SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/")
                            .build()),
                    SimpleResponseConsumer.create(),
                    context,
                    null).get(1, TimeUnit.MINUTES);
            Assertions.assertEquals(200, second.getCode());

            // The no-store response was not stored, so the origin is hit twice.
            Assertions.assertEquals(2, originHits.get());
            Assertions.assertEquals(CacheResponseStatus.CACHE_MISS, context.getCacheResponseStatus());
        } finally {
            client.close(CloseMode.IMMEDIATE);
            server.stop(0);
            executorService.shutdownNow();
        }
    }

    private static void handleOrigin(final HttpExchange exchange, final AtomicInteger originHits) throws IOException {
        originHits.incrementAndGet();
        final byte[] body = "OK".getBytes(StandardCharsets.US_ASCII);
        exchange.getResponseHeaders().add("Cache-Control", "public, max-age=60");
        exchange.getResponseHeaders().add("Date", DateUtils.formatStandardDate(Instant.now()));
        exchange.sendResponseHeaders(200, body.length);
        try (final OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
