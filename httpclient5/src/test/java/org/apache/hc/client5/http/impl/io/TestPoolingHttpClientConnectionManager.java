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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * {@link PoolingHttpClientConnectionManager} tests.
 */
@SuppressWarnings({"boxing","static-access","resource"}) // test code
public class TestPoolingHttpClientConnectionManager {

    @Mock
    private ManagedHttpClientConnection conn;
    @Mock
    private Lookup<ConnectionSocketFactory> socketFactoryRegistry;
    @Mock
    private ConnectionSocketFactory plainSocketFactory;
    @Mock
    private ConnectionSocketFactory sslSocketFactory;
    @Mock
    private Socket socket;
    @Mock
    private SchemePortResolver schemePortResolver;
    @Mock
    private DnsResolver dnsResolver;
    @Mock
    private Future<PoolEntry<HttpRoute, ManagedHttpClientConnection>> future;
    @Mock
    private StrictConnPool<HttpRoute, ManagedHttpClientConnection> pool;
    private PoolingHttpClientConnectionManager mgr;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        mgr = new PoolingHttpClientConnectionManager(
                new DefaultHttpClientConnectionOperator(socketFactoryRegistry, schemePortResolver, dnsResolver), pool, null);
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = new PoolEntry<>(route, TimeValue.NEG_ONE_MILLISECOND);
        entry.assignConnection(conn);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(conn.isConsistent()).thenReturn(true);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(
                Mockito.eq(route),
                Mockito.eq(null),
                Mockito.any(),
                Mockito.eq(null)))
                .thenReturn(future);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ofSeconds(1));
        Assertions.assertNotNull(endpoint1);
        Assertions.assertNotSame(conn, endpoint1);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        Mockito.verify(pool).release(entry, true);
    }

    @Test
    public void testReleaseRouteIncomplete() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = new PoolEntry<>(route, TimeValue.NEG_ONE_MILLISECOND);

        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(
                Mockito.eq(route),
                Mockito.eq(null),
                Mockito.any(),
                Mockito.eq(null)))
                .thenReturn(future);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ofSeconds(1));
        Assertions.assertNotNull(endpoint1);
        Assertions.assertNotSame(conn, endpoint1);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        Mockito.verify(pool).release(entry, false);
    }

    @Test
    public void testLeaseFutureTimeout() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
        Mockito.when(pool.lease(
                Mockito.eq(route),
                Mockito.eq(null),
                Mockito.any(),
                Mockito.eq(null)))
                .thenReturn(future);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        Assertions.assertThrows(TimeoutException.class, () ->
                connRequest1.get(Timeout.ofSeconds(1)));
    }

    @Test
    public void testReleaseReusable() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = new PoolEntry<>(route, TimeValue.NEG_ONE_MILLISECOND);
        entry.assignConnection(conn);

        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(
                Mockito.eq(route),
                Mockito.eq(null),
                Mockito.any(),
                Mockito.eq(null)))
                .thenReturn(future);
        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(conn.isConsistent()).thenReturn(true);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ofSeconds(1));
        Assertions.assertNotNull(endpoint1);
        Assertions.assertTrue(endpoint1.isConnected());

        mgr.release(endpoint1, "some state", TimeValue.NEG_ONE_MILLISECOND);

        Mockito.verify(pool).release(entry, true);
        Assertions.assertEquals("some state", entry.getState());
    }

    @Test
    public void testReleaseNonReusable() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = new PoolEntry<>(route, TimeValue.NEG_ONE_MILLISECOND);
        entry.assignConnection(conn);

        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(
                Mockito.eq(route),
                Mockito.eq(null),
                Mockito.any(),
                Mockito.eq(null)))
                .thenReturn(future);
        Mockito.when(conn.isOpen()).thenReturn(Boolean.FALSE);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ofSeconds(1));
        Assertions.assertNotNull(endpoint1);
        Assertions.assertFalse(endpoint1.isConnected());

        mgr.release(endpoint1, "some state", TimeValue.NEG_ONE_MILLISECOND);

        Mockito.verify(pool).release(entry, false);
        Assertions.assertNull(entry.getState());
    }

    @Test
    public void testTargetConnect() throws Exception {
        final HttpHost target = new HttpHost("https", "somehost", 443);
        final InetAddress remote = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress local = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        final HttpRoute route = new HttpRoute(target, local, true);

        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = new PoolEntry<>(route, TimeValue.NEG_ONE_MILLISECOND);
        entry.assignConnection(conn);

        Mockito.when(conn.isOpen()).thenReturn(false);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(
                Mockito.eq(route),
                Mockito.eq(null),
                Mockito.any(),
                Mockito.eq(null)))
                .thenReturn(future);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ofSeconds(1));
        Assertions.assertNotNull(endpoint1);

        final HttpClientContext context = HttpClientContext.create();
        final SocketConfig sconfig = SocketConfig.custom().build();

        mgr.setDefaultSocketConfig(sconfig);

        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(234, TimeUnit.MILLISECONDS)
                .build();
        mgr.setDefaultConnectionConfig(connectionConfig);
        final TlsConfig tlsConfig = TlsConfig.custom()
                .setHandshakeTimeout(345, TimeUnit.MILLISECONDS)
                .build();
        mgr.setDefaultTlsConfig(tlsConfig);

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[]{remote});
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(8443);
        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(plainSocketFactory);
        Mockito.when(plainSocketFactory.createSocket(Mockito.any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.eq(socket),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenReturn(socket);

        mgr.connect(endpoint1, null, context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("somehost");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(target);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).createSocket(context);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).connectSocket(
                socket,
                target,
                new InetSocketAddress(remote, 8443),
                new InetSocketAddress(local, 0),
                Timeout.ofMilliseconds(234),
                tlsConfig,
                context);

        mgr.connect(endpoint1, TimeValue.ofMilliseconds(123), context);

        Mockito.verify(dnsResolver, Mockito.times(2)).resolve("somehost");
        Mockito.verify(schemePortResolver, Mockito.times(2)).resolve(target);
        Mockito.verify(plainSocketFactory, Mockito.times(2)).createSocket(context);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).connectSocket(
                socket,
                target,
                new InetSocketAddress(remote, 8443),
                new InetSocketAddress(local, 0),
                Timeout.ofMilliseconds(123),
                tlsConfig,
                context);
    }

    @Test
    public void testProxyConnectAndUpgrade() throws Exception {
        final HttpHost target = new HttpHost("https", "somehost", 443);
        final HttpHost proxy = new HttpHost("someproxy", 8080);
        final InetAddress remote = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final HttpRoute route = new HttpRoute(target, local, proxy, true);

        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = new PoolEntry<>(route, TimeValue.NEG_ONE_MILLISECOND);
        entry.assignConnection(conn);

        Mockito.when(conn.isOpen()).thenReturn(false);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(
                Mockito.eq(route),
                Mockito.eq(null),
                Mockito.any(),
                Mockito.eq(null)))
                .thenReturn(future);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ofSeconds(1));
        Assertions.assertNotNull(endpoint1);

        final ConnectionSocketFactory plainsf = Mockito.mock(ConnectionSocketFactory.class);
        final LayeredConnectionSocketFactory sslsf = Mockito.mock(LayeredConnectionSocketFactory.class);
        final Socket mockSock = Mockito.mock(Socket.class);
        final HttpClientContext context = HttpClientContext.create();
        final SocketConfig sconfig = SocketConfig.custom().build();

        mgr.setDefaultSocketConfig(sconfig);

        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(234, TimeUnit.MILLISECONDS)
                .build();
        mgr.setDefaultConnectionConfig(connectionConfig);
        final TlsConfig tlsConfig = TlsConfig.custom()
                .setHandshakeTimeout(345, TimeUnit.MILLISECONDS)
                .build();
        mgr.setDefaultTlsConfig(tlsConfig);

        Mockito.when(dnsResolver.resolve("someproxy")).thenReturn(new InetAddress[] {remote});
        Mockito.when(schemePortResolver.resolve(proxy)).thenReturn(8080);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(8443);
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainsf);
        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(sslsf);
        Mockito.when(plainsf.createSocket(Mockito.any())).thenReturn(mockSock);
        Mockito.when(plainsf.connectSocket(
                Mockito.eq(mockSock),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenReturn(mockSock);

        mgr.connect(endpoint1, null, context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("someproxy");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(proxy);
        Mockito.verify(plainsf, Mockito.times(1)).createSocket(context);
        Mockito.verify(plainsf, Mockito.times(1)).connectSocket(
                mockSock,
                proxy,
                new InetSocketAddress(remote, 8080),
                new InetSocketAddress(local, 0),
                Timeout.ofMilliseconds(234),
                tlsConfig,
                context);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(conn.getSocket()).thenReturn(mockSock);

        mgr.upgrade(endpoint1, context);

        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(target);
        Mockito.verify(sslsf, Mockito.times(1)).createLayeredSocket(
                mockSock, "somehost", 8443, tlsConfig, context);
    }

}
