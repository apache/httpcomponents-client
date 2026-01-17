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
package org.apache.hc.client5.testing.async;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TooEarlyStatusRetryAsyncExecTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        // GET: 425 once, then 200
        final AtomicInteger count425 = new AtomicInteger();
        server.createContext("/once-425-then-200", ex -> {
            try {
                final int n = count425.incrementAndGet();
                if (n == 1) {
                    ex.getResponseHeaders().add("Retry-After", "0");
                    ex.sendResponseHeaders(425, -1);
                } else {
                    final byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, body.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(body);
                    }
                }
            } catch (final Exception ignore) {
            } finally {
                ex.close();
            }
        });

        // POST: always 425 -> must NOT retry
        server.createContext("/post-425-no-retry", ex -> {
            try {
                ex.getResponseHeaders().add("Retry-After", "0");
                ex.sendResponseHeaders(425, -1);
            } catch (final Exception ignore) {
            } finally {
                ex.close();
            }
        });

        // GET: 429 once, then 200
        final AtomicInteger count429 = new AtomicInteger();
        server.createContext("/once-429-then-200", ex -> {
            try {
                final int n = count429.incrementAndGet();
                if (n == 1) {
                    ex.getResponseHeaders().add("Retry-After", "0");
                    ex.sendResponseHeaders(429, -1);
                } else {
                    final byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, body.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(body);
                    }
                }
            } catch (final Exception ignore) {
            } finally {
                ex.close();
            }
        });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void asyncGetIsRetriedOn425() throws Exception {
        final CloseableHttpAsyncClient client =
                HttpAsyncClients.customTooEarlyAware(true).build(); // ensure this method registers the async exec interceptor
        client.start();

        final SimpleHttpRequest req = SimpleRequestBuilder.get("http://localhost:" + port + "/once-425-then-200").build();
        final CompletableFuture<SimpleHttpResponse> fut = new CompletableFuture<>();

        client.execute(req, null, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(final SimpleHttpResponse result) {
                fut.complete(result);
            }

            @Override
            public void failed(final Exception ex) {
                fut.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                fut.cancel(true);
            }
        });

        final SimpleHttpResponse resp = fut.get();
        assertEquals(200, resp.getCode());
        client.close();
    }

    @Test
    void asyncPostIsNotRetriedOn425() throws Exception {
        final CloseableHttpAsyncClient client =
                HttpAsyncClients.customTooEarlyAware(true).build();
        client.start();

        final SimpleHttpRequest req = SimpleRequestBuilder.post("http://localhost:" + port + "/post-425-no-retry").build();
        req.setBody("x", null);

        final CompletableFuture<SimpleHttpResponse> fut = new CompletableFuture<>();
        client.execute(req, null, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(final SimpleHttpResponse result) {
                fut.complete(result);
            }

            @Override
            public void failed(final Exception ex) {
                fut.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                fut.cancel(true);
            }
        });

        final SimpleHttpResponse resp = fut.get();
        assertEquals(425, resp.getCode());
        client.close();
    }

    @Test
    void asyncGetIsRetriedOn429WhenEnabled() throws Exception {
        final CloseableHttpAsyncClient client =
                HttpAsyncClients.customTooEarlyAware(true).build();
        client.start();

        final SimpleHttpRequest req = SimpleRequestBuilder.get("http://localhost:" + port + "/once-429-then-200").build();
        final CompletableFuture<SimpleHttpResponse> fut = new CompletableFuture<>();

        client.execute(req, null, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(final SimpleHttpResponse result) {
                fut.complete(result);
            }

            @Override
            public void failed(final Exception ex) {
                fut.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                fut.cancel(true);
            }
        });

        final SimpleHttpResponse resp = fut.get();
        assertEquals(200, resp.getCode());
        client.close();
    }
}
