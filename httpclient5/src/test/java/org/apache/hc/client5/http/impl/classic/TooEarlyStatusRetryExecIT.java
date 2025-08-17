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
package org.apache.hc.client5.http.impl.classic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.TooEarlyRetryStrategy;
import org.apache.hc.client5.http.impl.TooEarlyStatusRetryExec;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
class TooEarlyStatusRetryExecIT {

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

        final AtomicInteger c425nr = new AtomicInteger();
        server.createContext("/put-425-nonrepeatable", ex -> {
            try {
                drain(ex);
                if (c425nr.getAndIncrement() == 0) {
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

    private HttpHost target() {
        return new HttpHost("http", "localhost", port);
    }

    private static String path(final String url) {
        final URI uri = URI.create(url);
        return uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
    }

    private CloseableHttpClient newClient() {
        return HttpClientBuilder.create()
                .setRetryStrategy(new TooEarlyRetryStrategy(true))
                .addExecInterceptorLast("too-early-status-retry",
                        new TooEarlyStatusRetryExec(true))
                .build();
    }

    @Test
    void classicGetIsRetriedOnceOn425() throws Exception {
        try (CloseableHttpClient client = newClient()) {
            final HttpHost tgt = target();
            final HttpGet get = new HttpGet(path("http://localhost:" + port + "/once-425-then-200"));
            try (ClassicHttpResponse resp = client.executeOpen(tgt, get, null)) {
                assertEquals(200, resp.getCode());
                if (resp.getEntity() != null) {
                    EntityUtils.consume(resp.getEntity()); // <-- consume body before close
                }
            }
        }
    }

    @Test
    void classicPostIsNotRetriedOn425() throws Exception {
        try (CloseableHttpClient client = newClient()) {
            final HttpHost tgt = target();
            final HttpPost post = new HttpPost(path("http://localhost:" + port + "/post-425-no-retry"));
            try (ClassicHttpResponse resp = client.executeOpen(tgt, post, null)) {
                assertEquals(425, resp.getCode());
                if (resp.getEntity() != null) {
                    EntityUtils.consume(resp.getEntity()); // 1-byte body
                }
            }
        }
    }

    @Test
    void classicPutWithNonRepeatableEntityIsNotRetried() throws Exception {
        try (CloseableHttpClient client = newClient()) {
            final HttpHost tgt = target();
            final HttpPut put = new HttpPut(path("http://localhost:" + port + "/put-425-nonrepeatable"));
            put.setEntity(new InputStreamEntity(
                    new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
                    ContentType.TEXT_PLAIN)); // non-repeatable

            try (ClassicHttpResponse resp = client.executeOpen(tgt, put, null)) {
                assertEquals(425, resp.getCode());
                if (resp.getEntity() != null) {
                    EntityUtils.consume(resp.getEntity()); // 1-byte body
                }
            }
        }
    }
}
