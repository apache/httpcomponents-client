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

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.tls.TlsHandshakeTimeoutServer;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;

import static org.apache.hc.core5.util.ReflectionUtils.determineJRELevel;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class TestAsyncTlsHandshakeTimeout {
    private static final Duration EXPECTED_TIMEOUT = Duration.ofMillis(500);

    @Tag("slow")
    @Timeout(5)
    @ParameterizedTest
    @ValueSource(strings = { "false", "true" })
    void testTimeout(final boolean sendServerHello) throws Exception {
        // There is a bug in Java 11: after the handshake times out, the SSLSocket implementation performs a blocking
        // read on the socket to wait for close_notify or alert. This operation blocks until the read times out,
        // which means that TLS handshakes take twice as long to time out on Java 11. Without a workaround, the only
        // option is to skip the timeout duration assertions on Java 11.
        assumeFalse(determineJRELevel() == 11, "TLS handshake timeouts are buggy on Java 11");

        final PoolingAsyncClientConnectionManager connMgr = PoolingAsyncClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(5, SECONDS)
                .setSocketTimeout(5, SECONDS)
                .build())
            .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
            .setDefaultTlsConfig(TlsConfig.custom()
                .setHandshakeTimeout(EXPECTED_TIMEOUT.toMillis(), MILLISECONDS)
                .build())
            .build();
        try (
            final TlsHandshakeTimeoutServer server = new TlsHandshakeTimeoutServer(sendServerHello);
            final CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
                .setIOReactorConfig(IOReactorConfig.custom()
                    .setSelectInterval(TimeValue.ofMilliseconds(50))
                    .build())
                .setConnectionManager(connMgr)
                .build()
        ) {
            server.start();
            client.start();

            final SimpleHttpRequest request = SimpleHttpRequest.create("GET", "https://127.0.0.1:" + server.getPort());
            assertTimeout(request, client);
        }
    }

    private static void assertTimeout(final SimpleHttpRequest request, final CloseableHttpAsyncClient client) {
        final long startTime = System.nanoTime();
        final Throwable ex = assertThrows(ExecutionException.class,
            () -> client.execute(request, null).get()).getCause();
        final Duration actualTime = Duration.of(System.nanoTime() - startTime, ChronoUnit.NANOS);
        assertTrue(actualTime.toMillis() > EXPECTED_TIMEOUT.toMillis() / 2,
            format("Handshake attempt timed out too soon (only %,d out of %,d ms)",
                actualTime.toMillis(),
                EXPECTED_TIMEOUT.toMillis()));
        assertTrue(actualTime.toMillis() < EXPECTED_TIMEOUT.toMillis() * 2,
            format("Handshake attempt timed out too late (%,d out of %,d ms)",
                actualTime.toMillis(),
                EXPECTED_TIMEOUT.toMillis()));
        assertTrue(ex.getMessage().contains(EXPECTED_TIMEOUT.toMillis() + " MILLISECONDS"), ex.getMessage());
    }
}
