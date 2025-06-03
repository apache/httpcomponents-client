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

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestConnectionTimeouts {
    private static final Duration EXPECTED_TIMEOUT = Duration.ofMillis(500);

    @Timeout(5)
    @ParameterizedTest
    @ValueSource(strings = { "http", "https" })
    @SuppressWarnings("deprecation")
    void testRequestConfig(final String scheme) throws Exception {
        try (final CloseableHttpClient client = HttpClientBuilder.create()
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(EXPECTED_TIMEOUT.toMillis(), MILLISECONDS)
                .build())
            .build()) {
            assertTimeout(getRequest(scheme), client);
        }
    }

    @Timeout(5)
    @ParameterizedTest
    @ValueSource(strings = { "http", "https" })
    void testConnectionConfig(final String scheme) throws Exception {
        final PoolingHttpClientConnectionManager connMgr = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(EXPECTED_TIMEOUT.toMillis(), MILLISECONDS)
                    .build())
            .build();
        try (final CloseableHttpClient client = HttpClientBuilder.create().setConnectionManager(connMgr).build()) {
            assertTimeout(getRequest(scheme), client);
        }
    }

    private static HttpUriRequestBase getRequest(final String scheme) {
        return new HttpGet(scheme + "://198.51.100.1/ping");
    }

    private static void assertTimeout(final ClassicHttpRequest request, final HttpClient client) {
        final long startTime = System.nanoTime();
        final ConnectTimeoutException ex = assertThrows(ConnectTimeoutException.class,
            () -> client.execute(request, new BasicHttpClientResponseHandler()));
        final Duration actualTime = Duration.of(System.nanoTime() - startTime, ChronoUnit.NANOS);
        assertTrue(actualTime.toMillis() > EXPECTED_TIMEOUT.toMillis() / 2,
            format("Connection attempt timed out too soon (only %,d out of %,d ms)",
                actualTime.toMillis(),
                EXPECTED_TIMEOUT.toMillis()));
        assertTrue(actualTime.toMillis() < EXPECTED_TIMEOUT.toMillis() * 2,
            format("Connection attempt timed out too late (%,d out of %,d ms)",
                actualTime.toMillis(),
                EXPECTED_TIMEOUT.toMillis()));
        // Capitalization (`connect` vs `Connect`) varies by Java version
        final String message = ex.getMessage();
        assertTrue(message.toLowerCase().contains("connect timed out"), message);
    }
}
