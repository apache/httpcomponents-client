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

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestHttpClientConnectionOperator {

    private ManagedHttpClientConnection conn;
    private Socket socket;
    private DetachedSocketFactory detachedSocketFactory;
    private TlsSocketStrategy tlsSocketStrategy;
    private Lookup<TlsSocketStrategy> tlsSocketStrategyLookup;
    private SchemePortResolver schemePortResolver;
    private DnsResolver dnsResolver;
    private DefaultHttpClientConnectionOperator connectionOperator;

    @BeforeEach
    void setup() {
        conn = Mockito.mock(ManagedHttpClientConnection.class);
        socket = Mockito.mock(Socket.class);
        detachedSocketFactory = Mockito.mock(DetachedSocketFactory.class);
        tlsSocketStrategy = Mockito.mock(TlsSocketStrategy.class);
        tlsSocketStrategyLookup = Mockito.mock(Lookup.class);
        schemePortResolver = Mockito.mock(SchemePortResolver.class);
        dnsResolver = Mockito.mock(DnsResolver.class);
        connectionOperator = new DefaultHttpClientConnectionOperator(
                detachedSocketFactory, schemePortResolver, dnsResolver, tlsSocketStrategyLookup);
    }

    @Test
    void testConnect() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {127, 0, 0, 2});
        final int port = 80;
        final List<InetSocketAddress> resolvedAddresses = Arrays.asList(
                new InetSocketAddress(ip1, port),
                new InetSocketAddress(ip2, port)
        );
        Mockito.when(dnsResolver.resolve("somehost", port)).thenReturn(resolvedAddresses);

        Mockito.when(schemePortResolver.resolve(host.getSchemeName(), host)).thenReturn(port);
        Mockito.when(detachedSocketFactory.create(Mockito.any(), Mockito.any())).thenReturn(socket);

        final SocketConfig socketConfig = SocketConfig.custom()
            .setSoKeepAlive(true)
            .setSoReuseAddress(true)
            .setSoTimeout(5000, TimeUnit.MILLISECONDS)
            .setTcpNoDelay(true)
            .setSoLinger(50, TimeUnit.MILLISECONDS)
            .build();
        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        connectionOperator.connect(conn, host, null, localAddress, Timeout.ofMilliseconds(123), socketConfig, null, context);

        Mockito.verify(socket).setKeepAlive(true);
        Mockito.verify(socket).setReuseAddress(true);
        Mockito.verify(socket).setSoTimeout(5000);
        Mockito.verify(socket).setSoLinger(true, 50);
        Mockito.verify(socket).setTcpNoDelay(true);
        Mockito.verify(socket).bind(localAddress);

        Mockito.verify(socket).connect(new InetSocketAddress(ip1, port), 123);
        Mockito.verify(conn, Mockito.times(2)).bind(socket);
    }

    @Test
    void testConnectWithTLSUpgrade() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("https", "somehost");
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {127, 0, 0, 2});
        final int port = 443;

        final TlsConfig tlsConfig = TlsConfig.custom()
                .setHandshakeTimeout(Timeout.ofMilliseconds(345))
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                .build();

        final List<InetSocketAddress> resolvedAddresses = Arrays.asList(
                new InetSocketAddress(ip1, port),
                new InetSocketAddress(ip2, port)
        );
        Mockito.when(dnsResolver.resolve("somehost", port)).thenReturn(resolvedAddresses);

        Mockito.when(schemePortResolver.resolve(host.getSchemeName(), host)).thenReturn(port);
        Mockito.when(detachedSocketFactory.create(Mockito.any(), Mockito.any())).thenReturn(socket);

        Mockito.when(tlsSocketStrategyLookup.lookup("https")).thenReturn(tlsSocketStrategy);
        final SSLSocket upgradedSocket = Mockito.mock(SSLSocket.class);
        Mockito.when(tlsSocketStrategy.upgrade(
                Mockito.same(socket),
                Mockito.eq("somehost"),
                Mockito.anyInt(),
                Mockito.any(),
                Mockito.any())).thenReturn(upgradedSocket);

        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        connectionOperator.connect(conn, host, null, localAddress,
                Timeout.ofMilliseconds(123), SocketConfig.DEFAULT, tlsConfig, context);

        Mockito.verify(socket).connect(new InetSocketAddress(ip1, port), 123);
        Mockito.verify(conn, Mockito.times(2)).bind(socket);
        Mockito.verify(tlsSocketStrategy).upgrade(socket, "somehost", -1, tlsConfig, context);
        Mockito.verify(conn, Mockito.times(1)).bind(upgradedSocket, socket);
    }


    @Test
    void testConnectTimeout() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("somehost");
        final int port = 80;
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});
        final List<InetSocketAddress> resolvedAddresses = Arrays.asList(
                new InetSocketAddress(ip1, port),
                new InetSocketAddress(ip2, port)
        );
        Mockito.when(dnsResolver.resolve("somehost", port)).thenReturn(resolvedAddresses);
        Mockito.when(schemePortResolver.resolve(host.getSchemeName(), host)).thenReturn(port);
        Mockito.when(detachedSocketFactory.create(Mockito.any(), Mockito.any())).thenReturn(socket);
        Mockito.doThrow(new SocketTimeoutException()).when(socket).connect(Mockito.any(), Mockito.anyInt());
        Assertions.assertThrows(ConnectTimeoutException.class, () ->
                connectionOperator.connect(
                        conn, host, null, new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                        Timeout.ofMilliseconds(1000), SocketConfig.DEFAULT, null, context));
    }

    @Test
    void testConnectFailure() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});
        final int port = 80;
        final List<InetSocketAddress> resolvedAddresses = Arrays.asList(
                new InetSocketAddress(ip1, port),
                new InetSocketAddress(ip2, port)
        );
        Mockito.when(dnsResolver.resolve("somehost", port)).thenReturn(resolvedAddresses);

        Mockito.when(schemePortResolver.resolve(host.getSchemeName(), host)).thenReturn(port);
        Mockito.when(detachedSocketFactory.create(Mockito.any(), Mockito.any())).thenReturn(socket);
        Mockito.doThrow(new ConnectException()).when(socket).connect(Mockito.any(), Mockito.anyInt());

        Assertions.assertThrows(HttpHostConnectException.class, () ->
                connectionOperator.connect(
                        conn, host, null, TimeValue.ofMilliseconds(1000), SocketConfig.DEFAULT, context));
    }

    @Test
    void testConnectFailover() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        final InetSocketAddress ipAddress1 = new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 0, 0, 1}), 80);
        final InetSocketAddress ipAddress2 = new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), 80);

        Mockito.when(dnsResolver.resolve("somehost", 80)).thenReturn(Arrays.asList(ipAddress1, ipAddress2));
        Mockito.when(schemePortResolver.resolve(host.getSchemeName(), host)).thenReturn(80);
        Mockito.when(detachedSocketFactory.create(Mockito.any(), Mockito.any())).thenReturn(socket);
        Mockito.doThrow(new ConnectException()).when(socket).connect(
                Mockito.eq(ipAddress1),
                Mockito.anyInt());

        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        final TlsConfig tlsConfig = TlsConfig.custom()
                .build();
        connectionOperator.connect(conn, host, null, localAddress,
                Timeout.ofMilliseconds(123), SocketConfig.DEFAULT, tlsConfig, context);

        Mockito.verify(socket, Mockito.times(2)).bind(localAddress);
        Mockito.verify(socket).connect(ipAddress2, 123);
        Mockito.verify(conn, Mockito.times(3)).bind(socket);

    }

    @Test
    void testConnectExplicitAddress() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        final InetAddress ip = InetAddress.getByAddress(new byte[] {127, 0, 0, 23});
        final HttpHost host = new HttpHost(ip);

        Mockito.when(schemePortResolver.resolve(host.getSchemeName(), host)).thenReturn(80);
        Mockito.when(detachedSocketFactory.create(Mockito.any(), Mockito.any())).thenReturn(socket);

        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        final TlsConfig tlsConfig = TlsConfig.custom()
                .build();
        connectionOperator.connect(conn, host, null, localAddress,
                Timeout.ofMilliseconds(123), SocketConfig.DEFAULT, tlsConfig, context);

        Mockito.verify(socket).bind(localAddress);
        Mockito.verify(socket).connect(new InetSocketAddress(ip, 80), 123);
        Mockito.verify(dnsResolver, Mockito.never()).resolve(Mockito.anyString(), Mockito.anyInt());
        Mockito.verify(conn, Mockito.times(2)).bind(socket);
    }

    @Test
    void testUpgrade() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("https", "somehost", -1);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(conn.getSocket()).thenReturn(socket);
        Mockito.when(tlsSocketStrategyLookup.lookup("https")).thenReturn(tlsSocketStrategy);

        final SSLSocket upgradedSocket = Mockito.mock(SSLSocket.class);
        Mockito.when(tlsSocketStrategy.upgrade(
                Mockito.any(),
                Mockito.eq("somehost"),
                Mockito.anyInt(),
                Mockito.eq(Timeout.ofMilliseconds(345)),
                Mockito.any())).thenReturn(upgradedSocket);

        connectionOperator.upgrade(conn, host, null, Timeout.ofMilliseconds(345), context);

        Mockito.verify(conn).bind(upgradedSocket);
    }

    @Test
    void testUpgradeUpsupportedScheme() {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("httpsssss", "somehost", -1);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(conn.getSocket()).thenReturn(socket);

        Assertions.assertThrows(UnsupportedSchemeException.class, () ->
                connectionOperator.upgrade(conn, host, context));
    }

    @Test
    void testUpgradeNonLayeringScheme() {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("http", "somehost", -1);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(conn.getSocket()).thenReturn(socket);

        Assertions.assertThrows(UnsupportedSchemeException.class, () ->
                connectionOperator.upgrade(conn, host, context));
    }

    @Test
    void testConnectWithDisableDnsResolution() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("someonion.onion");
        final InetAddress local = InetAddress.getByAddress(new byte[]{127, 0, 0, 0});
        final int port = 80;

        final List<InetSocketAddress> resolvedAddresses = Collections.singletonList(
                InetSocketAddress.createUnresolved(host.getHostName(), port)
        );
        Mockito.when(dnsResolver.resolve(host.getHostName(), port)).thenReturn(resolvedAddresses);

        Mockito.when(schemePortResolver.resolve(host.getSchemeName(), host)).thenReturn(port);
        Mockito.when(detachedSocketFactory.create(Mockito.any(), Mockito.any())).thenReturn(socket);

        final SocketConfig socketConfig = SocketConfig.custom()
                .setSoKeepAlive(true)
                .setSoReuseAddress(true)
                .setSoTimeout(5000, TimeUnit.MILLISECONDS)
                .setTcpNoDelay(true)
                .setSoLinger(50, TimeUnit.MILLISECONDS)
                .build();
        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        final InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved(host.getHostName(), port);

        connectionOperator.connect(conn, host, null, localAddress, Timeout.ofMilliseconds(123), socketConfig, null, context);

        // Verify that the socket was created and attempted to connect without DNS resolution
        Mockito.verify(socket).setKeepAlive(true);
        Mockito.verify(socket).setReuseAddress(true);
        Mockito.verify(socket).setSoTimeout(5000);
        Mockito.verify(socket).setSoLinger(true, 50);
        Mockito.verify(socket).setTcpNoDelay(true);
        Mockito.verify(socket).bind(localAddress);

        Mockito.verify(socket).connect(remoteAddress, 123);
        Mockito.verify(conn, Mockito.times(2)).bind(socket);
        Mockito.verify(dnsResolver, Mockito.never()).resolve(Mockito.anyString());
    }

    @Test
    void testConnectWithDnsResolutionAndFallback() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost host = new HttpHost("fallbackhost.com");
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        final int port = 8080;
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});

        // Update to match the new `resolve` implementation that returns a list of SocketAddress
        final List<InetSocketAddress> resolvedAddresses = Arrays.asList(
                new InetSocketAddress(ip1, port),
                new InetSocketAddress(ip2, port)
        );
        Mockito.when(dnsResolver.resolve("fallbackhost.com", port)).thenReturn(resolvedAddresses);
        Mockito.when(schemePortResolver.resolve(host.getSchemeName(), host)).thenReturn(port);
        Mockito.when(detachedSocketFactory.create(Mockito.any(), Mockito.any())).thenReturn(socket);

        // Simulate failure to connect to the first resolved address
        Mockito.doThrow(new ConnectException()).when(socket).connect(Mockito.eq(new InetSocketAddress(ip1, port)), Mockito.anyInt());

        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        final SocketConfig socketConfig = SocketConfig.custom()
                .setSoKeepAlive(true)
                .setSoReuseAddress(true)
                .setSoTimeout(5000, TimeUnit.MILLISECONDS)
                .setTcpNoDelay(true)
                .setSoLinger(50, TimeUnit.MILLISECONDS)
                .build();

        // Connect using the updated connection operator
        connectionOperator.connect(conn, host, null, localAddress, Timeout.ofMilliseconds(123), socketConfig, null, context);

        // Verify fallback behavior after connection failure to the first address
        Mockito.verify(socket, Mockito.times(2)).bind(localAddress);
        Mockito.verify(socket).connect(new InetSocketAddress(ip2, port), 123);
        Mockito.verify(conn, Mockito.times(3)).bind(socket);
    }
}
