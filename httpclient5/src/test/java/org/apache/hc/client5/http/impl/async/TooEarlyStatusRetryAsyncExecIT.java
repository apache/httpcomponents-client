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
package org.apache.hc.client5.http.impl.async;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.TooEarlyRetryStrategy;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
class TooEarlyStatusRetryAsyncExecIT {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        port = server.getAddress().getPort();

        final AtomicInteger c425 = new AtomicInteger();
        server.createContext("/once-425-then-200", ex -> {
            try {
                drain(ex);
                if (c425.getAndIncrement() == 0) {
                    ex.getResponseHeaders().add("Retry-After", "0");
                    final byte[] one = new byte[]{'x'};
                    ex.sendResponseHeaders(425, one.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(one);
                    }
                } else {
                    final byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, ok.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(ok);
                    }
                }
            } finally {
                ex.close();
            }
        });

        server.createContext("/post-425-no-retry", ex -> {
            try {
                drain(ex);
                ex.getResponseHeaders().add("Retry-After", "0");
                final byte[] one = new byte[]{'x'};
                ex.sendResponseHeaders(425, one.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(one);
                }
            } finally {
                ex.close();
            }
        });

        final AtomicInteger c429 = new AtomicInteger();
        server.createContext("/once-429-then-200", ex -> {
            try {
                drain(ex);
                if (c429.getAndIncrement() == 0) {
                    ex.getResponseHeaders().add("Retry-After", "0");
                    final byte[] one = new byte[]{'x'};
                    ex.sendResponseHeaders(429, one.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(one);
                    }
                } else {
                    final byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, ok.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(ok);
                    }
                }
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

    private static void drain(final HttpExchange ex) throws IOException {
        final byte[] buf = new byte[1024];
        try (final java.io.InputStream in = ex.getRequestBody()) {
            while (in.read(buf) != -1) { /* drain */ }
        }
    }

    private CloseableHttpAsyncClient newClient() {
        return HttpAsyncClientBuilder.create()
                .setRetryStrategy(new TooEarlyRetryStrategy(true))
                .addExecInterceptorLast("too-early-status-retry",
                        new TooEarlyStatusRetryAsyncExec(true))
                .build();
    }

    @Test
    void asyncGetIsRetriedOnceOn425() throws Exception {
        final CloseableHttpAsyncClient client = newClient();
        client.start();
        try {
            final SimpleHttpRequest req = SimpleRequestBuilder.get(
                    "http://localhost:" + port + "/once-425-then-200").build();

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
        } finally {
            client.close();
        }
    }

    @Test
    void asyncPostIsNotRetriedOn425() throws Exception {
        final CloseableHttpAsyncClient client = newClient();
        client.start();
        try {
            final SimpleHttpRequest req = SimpleRequestBuilder.post(
                    "http://localhost:" + port + "/post-425-no-retry").build();
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
        } finally {
            client.close();
        }
    }

    @Test
    void asyncGetIsRetriedOnceOn429WhenEnabled() throws Exception {
        final CloseableHttpAsyncClient client = newClient();
        client.start();
        try {
            final SimpleHttpRequest req = SimpleRequestBuilder.get(
                    "http://localhost:" + port + "/once-429-then-200").build();

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
        } finally {
            client.close();
        }
    }
}
