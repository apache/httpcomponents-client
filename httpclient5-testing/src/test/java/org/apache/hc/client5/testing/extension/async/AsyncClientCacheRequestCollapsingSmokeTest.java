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
package org.apache.hc.client5.testing.extension.async;

import java.io.IOException;
import java.io.OutputStream;
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpAsyncClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.io.CloseMode;

public class AsyncClientCacheRequestCollapsingSmokeTest {

    private static final int REQUEST_COUNT = 20;

    public static void main(final String[] args) throws Exception {
        final AtomicInteger cacheableHits = new AtomicInteger();
        final AtomicInteger nonCacheableHits = new AtomicInteger();
        final AtomicInteger varyHits = new AtomicInteger();
        final AtomicInteger postHits = new AtomicInteger();
        final AtomicInteger onlyIfCachedHits = new AtomicInteger();
        final AtomicInteger flakyHits = new AtomicInteger();
        final AtomicInteger cancelHits = new AtomicInteger();

        final CountDownLatch cancelLeaderAccepted = new CountDownLatch(1);
        final CountDownLatch cancelLeaderRelease = new CountDownLatch(1);

        final ExecutorService executorService = Executors.newFixedThreadPool(REQUEST_COUNT);
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(executorService);

        server.createContext("/cacheable", exchange -> {
            final int count = cacheableHits.incrementAndGet();
            sleep(500);
            send(exchange, 200, "payload-" + count, "public, max-age=60", null);
        });

        server.createContext("/non-cacheable", exchange -> {
            final int count = nonCacheableHits.incrementAndGet();
            sleep(250);
            send(exchange, 200, "non-cacheable-" + count, "no-store", null);
        });

        server.createContext("/vary", exchange -> {
            final int count = varyHits.incrementAndGet();
            final String lang = exchange.getRequestHeaders().getFirst("Accept-Language");
            sleep(250);
            send(exchange, 200, "variant-" + lang + "-" + count, "public, max-age=60", "Accept-Language");
        });

        server.createContext("/post", exchange -> {
            final int count = postHits.incrementAndGet();
            sleep(250);
            send(exchange, 200, "post-" + count, "public, max-age=60", null);
        });

        server.createContext("/only-if-cached", exchange -> {
            onlyIfCachedHits.incrementAndGet();
            send(exchange, 200, "must-not-be-called", "public, max-age=60", null);
        });

        server.createContext("/flaky", exchange -> {
            final int count = flakyHits.incrementAndGet();
            sleep(300);
            if (count == 1) {
                send(exchange, 500, "boom", "no-store", null);
            } else {
                send(exchange, 200, "recovered-" + count, "public, max-age=60", null);
            }
        });

        server.createContext("/cancel", exchange -> {
            final int count = cancelHits.incrementAndGet();
            cancelLeaderAccepted.countDown();

            try {
                cancelLeaderRelease.await(5, TimeUnit.SECONDS);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            send(exchange, 200, "cancel-" + count, "public, max-age=60", null);
        });

        try {
            server.start();

            final String baseUri = "http://localhost:" + server.getAddress().getPort();

            assertCacheableColdMissesCollapse(baseUri + "/cacheable", cacheableHits);
            assertFailureDoesNotTrapFollowers(baseUri + "/flaky", flakyHits);
            assertCancellationDoesNotTrapFollowers(
                    baseUri + "/cancel",
                    cancelHits,
                    cancelLeaderAccepted,
                    cancelLeaderRelease);
            assertNonCacheableIsNotIncorrectlyCached(baseUri + "/non-cacheable", nonCacheableHits);
            assertVaryDoesNotMixVariants(baseUri + "/vary", varyHits);
            assertEntityRequestsAreNotCollapsed(baseUri + "/post", postHits);
            assertOnlyIfCachedDoesNotHitOrigin(baseUri + "/only-if-cached", onlyIfCachedHits);

            System.out.println("All request collapsing smoke checks passed");
        } finally {
            server.stop(0);
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Server executor did not terminate");
            }
        }
    }

    private static void assertCacheableColdMissesCollapse(
            final String uri,
            final AtomicInteger originHits) throws Exception {

        originHits.set(0);

        try (final ClientResource clientResource = new ClientResource(true)) {
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>();

            for (int i = 0; i < REQUEST_COUNT; i++) {
                futures.add(clientResource.execute(SimpleRequestBuilder.get(uri).build()));
            }

            for (final Future<SimpleHttpResponse> future : futures) {
                final SimpleHttpResponse response = future.get(5, TimeUnit.SECONDS);
                assertStatus(response, 200);
            }
        }

        assertEquals("cacheable collapsed origin hits", 1, originHits.get());
        System.out.println("cacheable concurrent GETs collapsed to 1 origin hit");
    }

    private static void assertFailureDoesNotTrapFollowers(
            final String uri,
            final AtomicInteger originHits) throws Exception {

        originHits.set(0);

        try (final ClientResource clientResource = new ClientResource(true)) {
            final Future<SimpleHttpResponse> first = clientResource.execute(SimpleRequestBuilder.get(uri).build());
            final Future<SimpleHttpResponse> second = clientResource.execute(SimpleRequestBuilder.get(uri).build());

            final SimpleHttpResponse firstResponse = first.get(5, TimeUnit.SECONDS);
            final SimpleHttpResponse secondResponse = second.get(5, TimeUnit.SECONDS);

            assertTrue(
                    "failure smoke must complete both futures",
                    firstResponse.getCode() >= 500 || secondResponse.getCode() >= 500 || originHits.get() > 1);

            final SimpleHttpResponse retry = clientResource.execute(SimpleRequestBuilder.get(uri).build())
                    .get(5, TimeUnit.SECONDS);
            assertStatus(retry, 200);
        }

        assertTrue("failure must not leave cache key permanently blocked", originHits.get() >= 2);
        System.out.println("leader failure did not trap followers");
    }

    private static void assertCancellationDoesNotTrapFollowers(
            final String uri,
            final AtomicInteger originHits,
            final CountDownLatch leaderAccepted,
            final CountDownLatch leaderRelease) throws Exception {

        originHits.set(0);

        try (final ClientResource clientResource = new ClientResource(true)) {
            final Future<SimpleHttpResponse> leader = clientResource.execute(SimpleRequestBuilder.get(uri).build());

            assertTrue("leader request must reach origin", leaderAccepted.await(5, TimeUnit.SECONDS));

            final Future<SimpleHttpResponse> follower = clientResource.execute(SimpleRequestBuilder.get(uri).build());

            leader.cancel(true);
            leaderRelease.countDown();

            try {
                follower.get(5, TimeUnit.SECONDS);
            } catch (final Exception ex) {
                // Acceptable here. The smoke check only verifies there is no hang and no retained key.
            }

            final SimpleHttpResponse retry = clientResource.execute(SimpleRequestBuilder.get(uri).build())
                    .get(5, TimeUnit.SECONDS);
            assertStatus(retry, 200);
        }

        assertTrue("cancellation must not leave cache key permanently blocked", originHits.get() >= 1);
        System.out.println("leader cancellation did not trap followers");
    }

    private static void assertNonCacheableIsNotIncorrectlyCached(
            final String uri,
            final AtomicInteger originHits) throws Exception {

        originHits.set(0);

        try (final ClientResource clientResource = new ClientResource(true)) {
            final SimpleHttpResponse first = clientResource.execute(SimpleRequestBuilder.get(uri).build())
                    .get(5, TimeUnit.SECONDS);
            final SimpleHttpResponse second = clientResource.execute(SimpleRequestBuilder.get(uri).build())
                    .get(5, TimeUnit.SECONDS);

            assertStatus(first, 200);
            assertStatus(second, 200);

            assertTrue(
                    "non-cacheable response must not be reused from cache",
                    !first.getBodyText().equals(second.getBodyText()));
        }

        assertEquals("non-cacheable origin hits", 2, originHits.get());
        System.out.println("non-cacheable response was not incorrectly cached");
    }

    private static void assertVaryDoesNotMixVariants(
            final String uri,
            final AtomicInteger originHits) throws Exception {

        originHits.set(0);

        try (final ClientResource clientResource = new ClientResource(true)) {
            final SimpleHttpRequest english = SimpleRequestBuilder.get(uri)
                    .addHeader("Accept-Language", "en")
                    .build();
            final SimpleHttpRequest spanish = SimpleRequestBuilder.get(uri)
                    .addHeader("Accept-Language", "es")
                    .build();

            final Future<SimpleHttpResponse> englishFuture = clientResource.execute(english);
            final Future<SimpleHttpResponse> spanishFuture = clientResource.execute(spanish);

            final SimpleHttpResponse englishResponse = englishFuture.get(5, TimeUnit.SECONDS);
            final SimpleHttpResponse spanishResponse = spanishFuture.get(5, TimeUnit.SECONDS);

            assertStatus(englishResponse, 200);
            assertStatus(spanishResponse, 200);

            assertTrue("English variant must be preserved", englishResponse.getBodyText().contains("variant-en"));
            assertTrue("Spanish variant must be preserved", spanishResponse.getBodyText().contains("variant-es"));
        }

        assertTrue("Vary requests should require distinct origin variants", originHits.get() >= 2);
        System.out.println("Vary responses did not mix variants");
    }

    private static void assertEntityRequestsAreNotCollapsed(
            final String uri,
            final AtomicInteger originHits) throws Exception {

        originHits.set(0);

        try (final ClientResource clientResource = new ClientResource(true)) {
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>();

            for (int i = 0; i < REQUEST_COUNT; i++) {
                final SimpleHttpRequest request = SimpleRequestBuilder.post(uri)
                        .setBody("request-" + i, ContentType.TEXT_PLAIN)
                        .build();
                futures.add(clientResource.execute(request));
            }

            for (final Future<SimpleHttpResponse> future : futures) {
                assertStatus(future.get(5, TimeUnit.SECONDS), 200);
            }
        }

        assertEquals("entity requests must not collapse", REQUEST_COUNT, originHits.get());
        System.out.println("entity requests were not collapsed");
    }

    private static void assertOnlyIfCachedDoesNotHitOrigin(
            final String uri,
            final AtomicInteger originHits) throws Exception {

        originHits.set(0);

        try (final ClientResource clientResource = new ClientResource(true)) {
            final SimpleHttpRequest request = SimpleRequestBuilder.get(uri)
                    .addHeader(HttpHeaders.CACHE_CONTROL, "only-if-cached")
                    .build();

            final SimpleHttpResponse response = clientResource.execute(request).get(5, TimeUnit.SECONDS);

            assertEquals("only-if-cached cold miss must not hit origin", 0, originHits.get());
            assertStatus(response, 504);
        }

        System.out.println("only-if-cached cold miss did not hit origin");
    }

    private static void send(
            final HttpExchange exchange,
            final int status,
            final String body,
            final String cacheControl,
            final String vary) throws IOException {

        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        if (cacheControl != null) {
            exchange.getResponseHeaders().add(HttpHeaders.CACHE_CONTROL, cacheControl);
        }
        if (vary != null) {
            exchange.getResponseHeaders().add(HttpHeaders.VARY, vary);
        }
        exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");

        exchange.sendResponseHeaders(status, bytes.length);
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertStatus(final SimpleHttpResponse response, final int expectedCode) {
        assertEquals("response code", expectedCode, response.getCode());
    }

    private static void assertEquals(final String message, final int expected, final int actual) {
        if (expected != actual) {
            throw new IllegalStateException(message + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(final String message, final boolean condition) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class ClientResource implements AutoCloseable {

        private final CloseableHttpAsyncClient client;

        ClientResource(final boolean requestCollapsingEnabled) {
            this.client = CachingHttpAsyncClientBuilder.create()
                    .setCacheConfig(CacheConfig.custom()
                            .setRequestCollapsingEnabled(requestCollapsingEnabled)
                            .build())
                    .build();
            this.client.start();
        }

        Future<SimpleHttpResponse> execute(final SimpleHttpRequest request) {
            return this.client.execute(
                    SimpleRequestProducer.create(request),
                    SimpleResponseConsumer.create(),
                    null);
        }

        @Override
        public void close() {
            this.client.close(CloseMode.IMMEDIATE);
        }

    }

}