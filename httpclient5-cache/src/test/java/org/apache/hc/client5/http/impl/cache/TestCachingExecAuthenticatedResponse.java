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
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A shared cache must not store a response to a request that the protocol layer
 * authenticated with credentials from the credentials provider.
 */
class TestCachingExecAuthenticatedResponse {

    private HttpServer server;
    private ExecutorService executorService;
    private AtomicInteger originHits;
    private HttpHost target;

    @BeforeEach
    void setUp() throws IOException {
        originHits = new AtomicInteger(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleOrigin);
        executorService = Executors.newCachedThreadPool();
        server.setExecutor(executorService);
        server.start();
        target = new HttpHost("http", "localhost", server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executorService.shutdownNow();
    }

    private HttpCacheContext authenticatedContext() {
        final HttpCacheContext context = HttpCacheContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "alice", "secret".toCharArray())
                .build());
        return context;
    }

    @Test
    void testAuthenticatedResponseNotServedToAnotherContext() throws Exception {
        try (final CloseableHttpClient client = CachingHttpClients.custom()
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .build()) {
            final String first = client.execute(target, new HttpGet("/private"), authenticatedContext(),
                    response -> response.getCode() + " " + EntityUtils.toString(response.getEntity()));
            Assertions.assertEquals("200 private data of alice", first);

            final HttpCacheContext anonymous = HttpCacheContext.create();
            final int code = client.execute(target, new HttpGet("/private"), anonymous, response -> {
                EntityUtils.consume(response.getEntity());
                return response.getCode();
            });
            Assertions.assertEquals(401, code);
            Assertions.assertEquals(3, originHits.get());
        }
    }

    @Test
    void testAuthenticatedResponseNotServedToAnotherContextAsyncViaProxy() throws Exception {
        // The origin server doubles as a plain HTTP proxy, so the request is sent in absolute form
        // and the protocol layer authenticates a copy of the request passed down by the cache
        final CloseableHttpAsyncClient client = CachingHttpAsyncClients.custom()
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .setProxy(new HttpHost("http", "127.0.0.1", server.getAddress().getPort()))
                .build();
        client.start();
        try {
            final SimpleHttpResponse first = client.execute(
                    SimpleRequestProducer.create(SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/private")
                            .build()),
                    SimpleResponseConsumer.create(),
                    authenticatedContext(),
                    null).get(1, TimeUnit.MINUTES);
            Assertions.assertEquals(200, first.getCode());
            Assertions.assertEquals("private data of alice", first.getBodyText());

            final SimpleHttpResponse second = client.execute(
                    SimpleRequestProducer.create(SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/private")
                            .build()),
                    SimpleResponseConsumer.create(),
                    HttpCacheContext.create(),
                    null).get(1, TimeUnit.MINUTES);
            Assertions.assertEquals(401, second.getCode());
            Assertions.assertEquals(3, originHits.get());
        } finally {
            client.close(CloseMode.IMMEDIATE);
        }
    }

    private void handleOrigin(final HttpExchange exchange) throws IOException {
        originHits.incrementAndGet();
        exchange.getResponseHeaders().add("Date", DateUtils.formatStandardDate(Instant.now()));
        final String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null) {
            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"test\"");
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        final byte[] body = "private data of alice".getBytes(StandardCharsets.US_ASCII);
        exchange.getResponseHeaders().add("Cache-Control", "max-age=60");
        exchange.sendResponseHeaders(200, body.length);
        try (final OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
