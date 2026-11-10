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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.tls.TlsHandshakeTimeoutServer;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

/**
 * Regression tests for the Happy Eyeballs (RFC 8305) racing path of the Classic client,
 * against a real {@link CloseableHttpClient}, real local TCP/TLS servers and a real TLS
 * handshake (PR #936 review comment, 2026-11-19): a TLS failure on the address that won the
 * TCP race must still fall back to the remaining, untried address, the same as the
 * non-racing (sequential) path and master already do.
 */
class TestHappyEyeballsTlsFallback {

    private HttpServer healthyServer;
    private ServerSocket resetProbe;
    private TlsHandshakeTimeoutServer stallingServer;

    @AfterEach
    void shutDown() throws IOException {
        if (healthyServer != null) {
            healthyServer.close(CloseMode.GRACEFUL);
        }
        if (resetProbe != null) {
            resetProbe.close();
        }
        if (stallingServer != null) {
            stallingServer.close();
        }
    }

    private HttpServer startHealthyServer() throws Exception {
        final HttpServer server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .register("localhost", "*", (request, response, context) -> response.setCode(HttpStatus.SC_OK))
                .create();
        server.start();
        return server;
    }

    private CloseableHttpClient clientFor(final List<InetSocketAddress> remoteAddresses, final boolean happyEyeballs) throws Exception {
        final DnsResolver dnsResolver = Mockito.mock(DnsResolver.class);
        Mockito.when(dnsResolver.resolve(Mockito.anyString(), Mockito.anyInt())).thenReturn(remoteAddresses);
        final PoolingHttpClientConnectionManager connMgr = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(dnsResolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .setSocketTimeout(Timeout.ofSeconds(5))
                        .build())
                .setTlsSocketStrategy(new DefaultClientTlsStrategy(
                        SSLTestContexts.createClientSSLContext(), NoopHostnameVerifier.INSTANCE))
                .setHappyEyeballsEnabled(happyEyeballs)
                .setConnectionAttemptDelay(TimeValue.ofMilliseconds(50))
                .build();
        return HttpClientBuilder.create()
                .setConnectionManager(connMgr)
                .disableAutomaticRetries()
                .build();
    }

    private static void assertServedByHealthyServer(final CloseableHttpClient client) throws IOException {
        final HttpGet request = new HttpGet("https://localhost/");
        client.execute(request, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
    }

    /**
     * Scenario 1: TCP accepted, TLS handshake fails immediately (the server accepts the TCP
     * connection and resets it before any TLS record is exchanged).
     */
    @ParameterizedTest(name = "happyEyeballs={0}")
    @ValueSource(booleans = {false, true})
    void tcpAcceptedTlsFailsImmediately_fallsBackToHealthyAddress(final boolean happyEyeballs) throws Exception {
        resetProbe = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final int brokenPort = resetProbe.getLocalPort();
        final Thread resetThread = new Thread(() -> {
            try {
                while (!resetProbe.isClosed()) {
                    final java.net.Socket accepted = resetProbe.accept();
                    accepted.setSoLinger(true, 0); // RST on close instead of a clean FIN
                    accepted.close();
                }
            } catch (final IOException ignore) {
                // Expected once the probe socket is closed by shutDown().
            }
        }, "reset-probe");
        resetThread.setDaemon(true);
        resetThread.start();

        healthyServer = startHealthyServer();
        final int healthyPort = healthyServer.getLocalPort();

        final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), brokenPort),
                new InetSocketAddress(InetAddress.getLoopbackAddress(), healthyPort));

        try (CloseableHttpClient client = clientFor(remoteAddresses, happyEyeballs)) {
            assertServedByHealthyServer(client);
        }
    }

    /**
     * Scenario 2: TCP accepted, TLS handshake stalls until the handshake/connect timeout.
     */
    @ParameterizedTest(name = "happyEyeballs={0}")
    @ValueSource(booleans = {false, true})
    void tcpAcceptedTlsStallsUntilTimeout_fallsBackToHealthyAddress(final boolean happyEyeballs) throws Exception {
        stallingServer = new TlsHandshakeTimeoutServer(false);
        stallingServer.start();
        final int stallingPort = stallingServer.getPort();

        healthyServer = startHealthyServer();
        final int healthyPort = healthyServer.getLocalPort();

        final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), stallingPort),
                new InetSocketAddress(InetAddress.getLoopbackAddress(), healthyPort));

        try (CloseableHttpClient client = clientFor(remoteAddresses, happyEyeballs)) {
            assertServedByHealthyServer(client);
        }
    }

    /**
     * When every address fails TLS, the final exception must still be reported (no address is
     * left silently unreported, and the connection attempt does not hang).
     */
    @ParameterizedTest(name = "happyEyeballs={0}")
    @ValueSource(booleans = {false, true})
    void allAddressesFailTls_throws(final boolean happyEyeballs) throws Exception {
        resetProbe = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final int brokenPort1 = resetProbe.getLocalPort();
        final Thread resetThread = new Thread(() -> {
            try {
                while (!resetProbe.isClosed()) {
                    final java.net.Socket accepted = resetProbe.accept();
                    accepted.setSoLinger(true, 0);
                    accepted.close();
                }
            } catch (final IOException ignore) {
                // Expected once the probe socket is closed by shutDown().
            }
        }, "reset-probe-2");
        resetThread.setDaemon(true);
        resetThread.start();

        try (ServerSocket resetProbe2 = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            final int brokenPort2 = resetProbe2.getLocalPort();
            final Thread resetThread2 = new Thread(() -> {
                try {
                    while (!resetProbe2.isClosed()) {
                        final java.net.Socket accepted = resetProbe2.accept();
                        accepted.setSoLinger(true, 0);
                        accepted.close();
                    }
                } catch (final IOException ignore) {
                    // Expected once resetProbe2 is closed by the try-with-resources above.
                }
            }, "reset-probe-3");
            resetThread2.setDaemon(true);
            resetThread2.start();

            final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), brokenPort1),
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), brokenPort2));

            try (CloseableHttpClient client = clientFor(remoteAddresses, happyEyeballs)) {
                final HttpGet request = new HttpGet("https://localhost/");
                Assertions.assertThrows(IOException.class, () -> client.execute(request, response -> null));
            }
        }
    }
}
