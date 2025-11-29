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
package org.apache.hc.client5.testing.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestRequestTimeoutClassic {

    private static HttpServer server;
    private static HttpHost target;

    private CloseableHttpClient client;

    private static final HttpRequestHandler DELAY_HANDLER = (request, response, context) -> {
        int seconds = 1;
        final String path = request.getPath(); // e.g. /delay/5
        final int idx = path.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < path.length()) {
            try {
                seconds = Integer.parseInt(path.substring(idx + 1));
            } catch (final NumberFormatException ignore) { /* default 1s */ }
        }
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        response.setCode(200);
        response.setEntity(new StringEntity("{\"ok\":true}", ContentType.APPLICATION_JSON));
    };

    @BeforeAll
    static void startServer() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .register("/delay/*", DELAY_HANDLER)
                .create();
        server.start();
        target = new HttpHost("http", "localhost", server.getLocalPort());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void createClient() {
        final PoolingHttpClientConnectionManager cm =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(5))
                                .setSocketTimeout(Timeout.ofSeconds(5))
                                .build())
                        .build();

        client = HttpClients.custom()
                .setConnectionManager(cm)
                .build();
    }

    @AfterEach
    void closeClient() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timesOutHard() throws Exception {
        final HttpGet req = new HttpGet(new URIBuilder()
                .setScheme(target.getSchemeName())
                .setHost(target.getHostName())
                .setPort(target.getPort())
                .setPath("/delay/5")
                .build());
        req.setConfig(RequestConfig.custom()
                .setRequestTimeout(Timeout.ofSeconds(1))           // hard end-to-end deadline
                .setConnectionRequestTimeout(Timeout.ofSeconds(2)) // pool lease cap
                .build());

        final IOException ex = assertThrows(IOException.class,
                () -> client.execute(req, resp -> resp.getCode()));
        assertTrue(ex instanceof java.io.InterruptedIOException,
                "Expected InterruptedIOException, got: " + ex.getClass());
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = TimeUnit.SECONDS)
    void succeedsWithinBudget() throws Exception {
        final HttpGet req = new HttpGet(new URIBuilder()
                .setScheme(target.getSchemeName())
                .setHost(target.getHostName())
                .setPort(target.getPort())
                .setPath("/delay/1")
                .build());
        req.setConfig(RequestConfig.custom()
                .setRequestTimeout(Timeout.ofSeconds(5))           // enough for lease+connect+1s delay
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .build());

        final int code = client.execute(req, resp -> resp.getCode());
        assertEquals(200, code);
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = TimeUnit.SECONDS)
    void immediateExpirationFailsBeforeSend() throws Exception {
        final HttpGet req = new HttpGet(new URIBuilder()
                .setScheme(target.getSchemeName())
                .setHost(target.getHostName())
                .setPort(target.getPort())
                .setPath("/delay/1")
                .build());
        req.setConfig(RequestConfig.custom()
                .setRequestTimeout(Timeout.ofMilliseconds(1))      // near-immediate expiry
                .setConnectionRequestTimeout(Timeout.ofSeconds(1))
                .build());

        assertThrows(java.io.InterruptedIOException.class,
                () -> client.execute(req, resp -> resp.getCode()));
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = TimeUnit.SECONDS)
    void largeBudgetStillHonorsPerOpTimeouts() throws Exception {
        final HttpGet req = new HttpGet(new URIBuilder()
                .setScheme(target.getSchemeName())
                .setHost(target.getHostName())
                .setPort(target.getPort())
                .setPath("/delay/1")
                .build());
        req.setConfig(RequestConfig.custom()
                .setRequestTimeout(Timeout.ofSeconds(30))
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .build());

        final int code = client.execute(req, resp -> resp.getCode());
        assertEquals(200, code);
    }
}
