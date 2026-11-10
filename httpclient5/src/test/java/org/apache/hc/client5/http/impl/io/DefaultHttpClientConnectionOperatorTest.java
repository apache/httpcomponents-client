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
package org.apache.hc.client5.http.impl.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class DefaultHttpClientConnectionOperatorTest {

    /**
     * Regression test: when {@code localAddress} specifies a non-zero (explicit) local port,
     * Happy Eyeballs racing must not bind more than one socket to it concurrently, which the
     * OS would reject for all but one attempt as a bind conflict rather than a real connect
     * failure. The operator falls back to the existing sequential path in that case, so a
     * dead first address followed by a real, reachable second address must still succeed,
     * exactly as it would against master without this feature.
     */
    @Test
    void happyEyeballsWithExplicitLocalPort_fallsBackToSequential_stillSucceeds() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ServerSocket deadPortProbe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final int listeningPort = server.getLocalPort();
            final int deadPort = deadPortProbe.getLocalPort();
            deadPortProbe.close(); // nothing listens here once closed: instant refusal

            final int explicitLocalPort;
            try (ServerSocket localPortProbe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                explicitLocalPort = localPortProbe.getLocalPort();
            }

            final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), deadPort),
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), listeningPort));
            final DnsResolver dnsResolver = Mockito.mock(DnsResolver.class);
            Mockito.when(dnsResolver.resolve(Mockito.anyString(), Mockito.anyInt())).thenReturn(remoteAddresses);

            final DefaultHttpClientConnectionOperator operator = new DefaultHttpClientConnectionOperator(
                    DefaultHttpClientConnectionOperator.PLAIN_SOCKET_FACTORY,
                    DefaultSchemePortResolver.INSTANCE,
                    dnsResolver,
                    (Lookup<org.apache.hc.client5.http.ssl.TlsSocketStrategy>) name -> null,
                    true,
                    TimeValue.ofMilliseconds(20));

            final DefaultManagedHttpClientConnection conn = new DefaultManagedHttpClientConnection(
                    "test", null, null, null, null, null, null, null, null);
            final InetSocketAddress localAddress = new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), explicitLocalPort);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    operator.connect(conn, new HttpHost("http", "somehost", 80), localAddress,
                            TimeValue.ofSeconds(5), SocketConfig.DEFAULT, HttpClientContext.create()));

            assertEquals(listeningPort, ((InetSocketAddress) conn.getRemoteAddress()).getPort());
            assertEquals(explicitLocalPort, ((InetSocketAddress) conn.getLocalAddress()).getPort());
            conn.close();
        }
    }

    /**
     * Regression test: on the Happy Eyeballs racing path, a TLS handshake failure after the
     * TCP race has already picked a winner must still close that winning socket, exactly as
     * the sequential path's own try/catch around bind + TLS upgrade already does.
     */
    @Test
    void happyEyeballsRacing_tlsHandshakeFailure_closesWinningSocket() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ServerSocket deadPortProbe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final int listeningPort = server.getLocalPort();
            final int deadPort = deadPortProbe.getLocalPort();
            deadPortProbe.close();

            final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), deadPort),
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), listeningPort));
            final DnsResolver dnsResolver = Mockito.mock(DnsResolver.class);
            Mockito.when(dnsResolver.resolve(Mockito.anyString(), Mockito.anyInt())).thenReturn(remoteAddresses);

            final AtomicReference<Socket> winningSocket = new AtomicReference<>();
            final DetachedSocketFactory trackingFactory = (final Proxy proxy) -> {
                final Socket s = new Socket();
                winningSocket.set(s);
                return s;
            };

            final TlsSocketStrategy failingTls = Mockito.mock(TlsSocketStrategy.class);
            Mockito.when(failingTls.upgrade(Mockito.any(), Mockito.anyString(), Mockito.anyInt(), Mockito.any(), Mockito.any()))
                    .thenThrow(new IOException("handshake failed"));
            final Lookup<TlsSocketStrategy> tlsLookup = scheme -> failingTls;

            final DefaultHttpClientConnectionOperator operator = new DefaultHttpClientConnectionOperator(
                    trackingFactory, DefaultSchemePortResolver.INSTANCE, dnsResolver, tlsLookup, true, TimeValue.ofMilliseconds(20));
            final DefaultManagedHttpClientConnection conn = new DefaultManagedHttpClientConnection(
                    "test", null, null, null, null, null, null, null, null);

            assertThrows(IOException.class, () ->
                    assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                            operator.connect(conn, new HttpHost("https", "somehost", 443), null,
                                    TimeValue.ofSeconds(5), SocketConfig.DEFAULT, HttpClientContext.create())));

            assertTrue(winningSocket.get().isClosed(), "the winning socket must be closed when the TLS handshake fails");
        }
    }

    /** Same as above, but the TLS strategy throws a RuntimeException rather than an IOException. */
    @Test
    void happyEyeballsRacing_tlsHandshakeRuntimeFailure_closesWinningSocket() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ServerSocket deadPortProbe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final int listeningPort = server.getLocalPort();
            final int deadPort = deadPortProbe.getLocalPort();
            deadPortProbe.close();

            final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), deadPort),
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), listeningPort));
            final DnsResolver dnsResolver = Mockito.mock(DnsResolver.class);
            Mockito.when(dnsResolver.resolve(Mockito.anyString(), Mockito.anyInt())).thenReturn(remoteAddresses);

            final AtomicReference<Socket> winningSocket = new AtomicReference<>();
            final DetachedSocketFactory trackingFactory = (final Proxy proxy) -> {
                final Socket s = new Socket();
                winningSocket.set(s);
                return s;
            };

            final TlsSocketStrategy failingTls = Mockito.mock(TlsSocketStrategy.class);
            Mockito.when(failingTls.upgrade(Mockito.any(), Mockito.anyString(), Mockito.anyInt(), Mockito.any(), Mockito.any()))
                    .thenThrow(new IllegalStateException("handshake blew up"));
            final Lookup<TlsSocketStrategy> tlsLookup = scheme -> failingTls;

            final DefaultHttpClientConnectionOperator operator = new DefaultHttpClientConnectionOperator(
                    trackingFactory, DefaultSchemePortResolver.INSTANCE, dnsResolver, tlsLookup, true, TimeValue.ofMilliseconds(20));
            final DefaultManagedHttpClientConnection conn = new DefaultManagedHttpClientConnection(
                    "test", null, null, null, null, null, null, null, null);

            assertThrows(IllegalStateException.class, () ->
                    assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                            operator.connect(conn, new HttpHost("https", "somehost", 443), null,
                                    TimeValue.ofSeconds(5), SocketConfig.DEFAULT, HttpClientContext.create())));

            assertTrue(winningSocket.get().isClosed(), "the winning socket must be closed when the TLS handshake throws unexpectedly");
        }
    }

    /**
     * Regression test (reviewer-supplied probe, PR #936): a TLS failure on the address that
     * won the TCP race must fall back to the remaining, untried address, exactly as the
     * sequential (non-racing) path already does. Passes for both {@code happyEyeballs} values.
     */
    @ParameterizedTest(name = "happyEyeballs={0}")
    @ValueSource(booleans = {false, true})
    void tlsFailureOnFirstAddressFallsBackToNextAddress(final boolean happyEyeballs) throws Exception {
        try (ServerSocket broken = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             ServerSocket healthy = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), broken.getLocalPort()),
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), healthy.getLocalPort()));
            final DnsResolver dnsResolver = Mockito.mock(DnsResolver.class);
            Mockito.when(dnsResolver.resolve(Mockito.anyString(), Mockito.anyInt())).thenReturn(remoteAddresses);

            final AtomicInteger handshakes = new AtomicInteger();
            final AtomicInteger upgradedPort = new AtomicInteger();
            final TlsSocketStrategy tls = (socket, target, port, attachment, context) -> {
                handshakes.incrementAndGet();
                if (socket.getPort() == broken.getLocalPort()) {
                    throw new IOException("handshake failed on " + socket.getRemoteSocketAddress());
                }
                upgradedPort.set(socket.getPort());
                return Mockito.mock(SSLSocket.class);
            };
            final Lookup<TlsSocketStrategy> tlsLookup = scheme -> tls;

            final DefaultHttpClientConnectionOperator operator = new DefaultHttpClientConnectionOperator(
                    proxy -> new Socket(), DefaultSchemePortResolver.INSTANCE, dnsResolver, tlsLookup,
                    happyEyeballs, TimeValue.ofMilliseconds(250));
            final DefaultManagedHttpClientConnection conn = new DefaultManagedHttpClientConnection(
                    "test", null, null, null, null, null, null, null, null);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    operator.connect(conn, new HttpHost("https", "somehost", 443), null,
                            TimeValue.ofSeconds(5), SocketConfig.DEFAULT, HttpClientContext.create()));

            assertEquals(healthy.getLocalPort(), upgradedPort.get());
            assertEquals(2, handshakes.get());
        }
    }

    /**
     * Regression test: when the race winner is the only address (no address left to fall
     * back to), a TLS failure on it still propagates immediately, exactly as before this fix.
     */
    @Test
    void happyEyeballsRacing_tlsHandshakeFailure_noRemainingAddress_propagatesImmediately() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final int listeningPort = server.getLocalPort();
            final List<InetSocketAddress> remoteAddresses = Collections.singletonList(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), listeningPort));
            final DnsResolver dnsResolver = Mockito.mock(DnsResolver.class);
            Mockito.when(dnsResolver.resolve(Mockito.anyString(), Mockito.anyInt())).thenReturn(remoteAddresses);

            final TlsSocketStrategy failingTls = Mockito.mock(TlsSocketStrategy.class);
            Mockito.when(failingTls.upgrade(Mockito.any(), Mockito.anyString(), Mockito.anyInt(), Mockito.any(), Mockito.any()))
                    .thenThrow(new IOException("handshake failed"));
            final Lookup<TlsSocketStrategy> tlsLookup = scheme -> failingTls;

            final DefaultHttpClientConnectionOperator operator = new DefaultHttpClientConnectionOperator(
                    DefaultHttpClientConnectionOperator.PLAIN_SOCKET_FACTORY, DefaultSchemePortResolver.INSTANCE,
                    dnsResolver, tlsLookup, true, TimeValue.ofMilliseconds(20));
            final DefaultManagedHttpClientConnection conn = new DefaultManagedHttpClientConnection(
                    "test", null, null, null, null, null, null, null, null);

            // Happy Eyeballs only races when there is more than one address; with a single
            // address this exercises the sequential path, which is the baseline this fix must
            // not change when there is nothing left to fall back to.
            assertThrows(IOException.class, () ->
                    assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                            operator.connect(conn, new HttpHost("https", "somehost", 443), null,
                                    TimeValue.ofSeconds(5), SocketConfig.DEFAULT, HttpClientContext.create())));
        }
    }
}
