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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.client5.http.utils.DateUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class TestAsyncCacheStatus {

    @Test
    void testCacheStatusMissThenHit() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            final byte[] body = "OK".getBytes(StandardCharsets.US_ASCII);
            exchange.getResponseHeaders().add("Cache-Control", "public, max-age=60");
            exchange.getResponseHeaders().add("Date", DateUtils.formatStandardDate(Instant.now()));
            exchange.getResponseHeaders().add("ETag", "\"v1\"");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        final ExecutorService executorService = Executors.newCachedThreadPool();
        server.setExecutor(executorService);
        server.start();
        try {
            final HttpHost target = new HttpHost("http", "localhost", server.getAddress().getPort());
            try (final CloseableHttpAsyncClient client = CachingHttpAsyncClients.custom()
                    .setCacheConfig(CacheConfig.custom().setCacheStatusEnabled(true).build())
                    .setResourceFactory(HeapResourceFactory.INSTANCE)
                    .build()) {
                client.start();

                final SimpleHttpResponse miss = client.execute(
                        SimpleRequestProducer.create(SimpleRequestBuilder.get().setHttpHost(target).setPath("/").build()),
                        SimpleResponseConsumer.create(), HttpCacheContext.create(), null).get(30, TimeUnit.SECONDS);
                Assertions.assertEquals("Apache-HttpClient; fwd=uri-miss",
                        miss.getFirstHeader("Cache-Status").getValue());

                final SimpleHttpResponse hit = client.execute(
                        SimpleRequestProducer.create(SimpleRequestBuilder.get().setHttpHost(target).setPath("/").build()),
                        SimpleResponseConsumer.create(), HttpCacheContext.create(), null).get(30, TimeUnit.SECONDS);
                Assertions.assertEquals("Apache-HttpClient; hit",
                        hit.getFirstHeader("Cache-Status").getValue());

                client.close(CloseMode.GRACEFUL);
            }
        } finally {
            server.stop(0);
            executorService.shutdownNow();
        }
    }

}
