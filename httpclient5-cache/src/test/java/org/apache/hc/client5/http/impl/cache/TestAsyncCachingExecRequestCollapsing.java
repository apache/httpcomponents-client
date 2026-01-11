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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestAsyncCachingExecRequestCollapsing {

    private static final class RoundResult {
        private final int originHits;
        private final int cacheMisses;
        private final int cacheHits;

        private RoundResult(final int originHits, final int cacheMisses, final int cacheHits) {
            this.originHits = originHits;
            this.cacheMisses = cacheMisses;
            this.cacheHits = cacheHits;
        }
    }

    @Test
    void testRequestCollapsingPreventsThunderingHerdOnColdMiss() throws Exception {
        final AtomicInteger originHits = new AtomicInteger(0);

        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> handleOrigin(exchange, originHits));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        try {
            final int port = server.getAddress().getPort();
            final HttpHost target = new HttpHost("http", "localhost", port);
            final int concurrent = 20;

            originHits.set(0);
            final RoundResult baseline = runRound(target, concurrent, false, originHits);
            Assertions.assertEquals(concurrent, baseline.originHits, "Baseline must hit origin N times");
            Assertions.assertEquals(concurrent, baseline.cacheMisses, "Baseline must be all CACHE_MISS on cold miss");
            Assertions.assertEquals(0, baseline.cacheHits, "Baseline must have no CACHE_HIT on cold miss");

            originHits.set(0);
            final RoundResult collapsed = runRound(target, concurrent, true, originHits);
            Assertions.assertEquals(1, collapsed.originHits, "Collapsing must allow only one origin request");
            Assertions.assertEquals(1, collapsed.cacheMisses, "Collapsing must have exactly one CACHE_MISS leader");
            Assertions.assertEquals(concurrent - 1, collapsed.cacheHits, "Collapsing must serve followers from cache");
        } finally {
            server.stop(0);
        }
    }

    private static void handleOrigin(final HttpExchange exchange, final AtomicInteger originHits) throws IOException {
        originHits.incrementAndGet();

        // Keep the origin "busy" so concurrent client requests overlap and all see a cold cache.
        try {
            Thread.sleep(250);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        final byte[] body = "OK".getBytes(StandardCharsets.US_ASCII);

        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=us-ascii");
        exchange.getResponseHeaders().add("Cache-Control", "public, max-age=60");
        exchange.getResponseHeaders().add("Date", DateUtils.formatStandardDate(Instant.now()));

        exchange.sendResponseHeaders(200, body.length);
        try (final OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static RoundResult runRound(
            final HttpHost target,
            final int concurrent,
            final boolean requestCollapsingEnabled,
            final AtomicInteger originHits) throws Exception {

        final CacheConfig cacheConfig = CacheConfig.custom()
                .setHeuristicCachingEnabled(false)
                .build();

        final AtomicInteger cacheMisses = new AtomicInteger(0);
        final AtomicInteger cacheHits = new AtomicInteger(0);

        final CloseableHttpAsyncClient client = CachingHttpAsyncClients.custom()
                .setCacheConfig(cacheConfig)
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .setRequestCollapsingEnabled(requestCollapsingEnabled) // <-- your new switch
                .build();

        client.start();

        try {
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>(concurrent);
            final CountDownLatch done = new CountDownLatch(concurrent);
            final AtomicInteger failures = new AtomicInteger(0);

            for (int i = 0; i < concurrent; i++) {
                final SimpleHttpRequest request = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/")
                        .build();

                // IMPORTANT: one context per request (context is not thread-safe).
                final HttpCacheContext context = HttpCacheContext.create();
                context.setRequestCacheControl(RequestCacheControl.DEFAULT);

                futures.add(client.execute(
                        SimpleRequestProducer.create(request),
                        SimpleResponseConsumer.create(),
                        context,
                        new FutureCallback<SimpleHttpResponse>() {

                            @Override
                            public void completed(final SimpleHttpResponse result) {
                                final CacheResponseStatus status = context.getCacheResponseStatus();
                                if (status == CacheResponseStatus.CACHE_MISS) {
                                    cacheMisses.incrementAndGet();
                                } else if (status == CacheResponseStatus.CACHE_HIT) {
                                    cacheHits.incrementAndGet();
                                } else {
                                    // For this test we only expect HIT or MISS.
                                    failures.incrementAndGet();
                                }
                                done.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                failures.incrementAndGet();
                                done.countDown();
                            }

                            @Override
                            public void cancelled() {
                                failures.incrementAndGet();
                                done.countDown();
                            }

                        }));
            }

            Assertions.assertTrue(done.await(30, TimeUnit.SECONDS), "Requests did not complete in time");
            Assertions.assertEquals(0, failures.get(), "Unexpected failures / cache statuses");

            // Also ensure futures are all done / propagate any hidden exception.
            for (final Future<SimpleHttpResponse> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }

            return new RoundResult(originHits.get(), cacheMisses.get(), cacheHits.get());
        } finally {
            client.close(CloseMode.GRACEFUL);
        }
    }

}
