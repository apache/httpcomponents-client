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
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.tls.TlsHandshakeTimeoutServer;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.SocketConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.hc.core5.util.ReflectionUtils.determineJRELevel;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class TestTlsHandshakeTimeout {
    private static final Duration EXPECTED_TIMEOUT = Duration.ofMillis(500);

    @Tag("slow")
    @Timeout(5)
    @ParameterizedTest
    @ValueSource(strings = { "false", "true" })
    void testTimeout(final boolean sendServerHello) throws Exception {
        final PoolingHttpClientConnectionManager connMgr = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(5, SECONDS)
                .setSocketTimeout(5, SECONDS)
                .build())
            .setDefaultSocketConfig(SocketConfig.custom()
                .setTcpKeepIdle(2)
                .setTcpKeepInterval(1)
                .setTcpKeepCount(5)
                .build())
            .setTlsSocketStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext(), HostnameVerificationPolicy.CLIENT, NoopHostnameVerifier.INSTANCE))
            .setDefaultTlsConfig(TlsConfig.custom()
                .setHandshakeTimeout(EXPECTED_TIMEOUT.toMillis(), MILLISECONDS)
                .build())
            .build();
        try (
            final TlsHandshakeTimeoutServer server = new TlsHandshakeTimeoutServer(sendServerHello);
            final CloseableHttpClient client = HttpClientBuilder.create()
                .setConnectionManager(connMgr)
                .build()
        ) {
            server.start();

            final HttpUriRequestBase request = new HttpGet("https://127.0.0.1:" + server.getPort());
            assertTimeout(request, client);
        }
    }

    @SuppressWarnings("deprecation")
    private static void assertTimeout(final ClassicHttpRequest request, final HttpClient client) {
        // There is a bug in Java 11, and some releases of Java 8: after the
        // handshake times out, the SSLSocket implementation performs a
        // blocking read on the socket to wait for close_notify or alert. This
        // operation blocks until the read times out, which means that TLS
        // handshakes take twice as long to time out on Java 11. Without a
        // workaround, the only option is to skip the timeout assertions on
        // older versions of Java.
        assumeFalse(determineJRELevel() <= 11, "TLS handshake timeouts are buggy on Java 11 and earlier");

        final long startTime = System.nanoTime();
        final ConnectTimeoutException ex = assertThrows(ConnectTimeoutException.class, () -> client.execute(request));
        assertTrue(ex.getMessage().contains("Read timed out"), ex.getMessage());

        final Duration actualTime = Duration.of(System.nanoTime() - startTime, ChronoUnit.NANOS);
        assertTrue(actualTime.toMillis() > EXPECTED_TIMEOUT.toMillis() / 2,
            format("Handshake attempt timed out too soon (only %,d out of %,d ms)",
                actualTime.toMillis(),
                EXPECTED_TIMEOUT.toMillis()));
        assertTrue(actualTime.toMillis() < EXPECTED_TIMEOUT.toMillis() * 2,
            format("Handshake attempt timed out too late (%,d out of %,d ms)",
                actualTime.toMillis(),
                EXPECTED_TIMEOUT.toMillis()));
    }
}
