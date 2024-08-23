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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
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
class TestPoolingHttpClientConnectionManager {

    @Mock
    private ManagedHttpClientConnection conn;
    @Mock
    private Lookup<TlsSocketStrategy> tlsSocketStrategyLookup;
    @Mock
    private DetachedSocketFactory detachedSocketFactory;
    @Mock
    private TlsSocketStrategy tlsSocketStrategy;
    @Mock
    private Socket socket;
    @Mock
    private SSLSocket upgradedSocket;
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
    void setup() {
        MockitoAnnotations.openMocks(this);
        mgr = new PoolingHttpClientConnectionManager(new DefaultHttpClientConnectionOperator(
                detachedSocketFactory, schemePortResolver, dnsResolver, tlsSocketStrategyLookup), pool,
                null);
    }

    @Test
    void testLeaseRelease() throws Exception {
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
    void testReleaseRouteIncomplete() throws Exception {
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
    void testLeaseFutureTimeout() throws Exception {
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
    void testReleaseReusable() throws Exception {
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
    void testReleaseNonReusable() throws Exception {
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
    void testTargetConnect() throws Exception {
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
        Mockito.when(schemePortResolver.resolve(target.getSchemeName(), target)).thenReturn(8443);
        Mockito.when(detachedSocketFactory.create(Mockito.any())).thenReturn(socket);

        Mockito.when(tlsSocketStrategyLookup.lookup("https")).thenReturn(tlsSocketStrategy);
        Mockito.when(tlsSocketStrategy.upgrade(
                Mockito.same(socket),
                Mockito.eq("somehost"),
                Mockito.anyInt(),
                Mockito.any(),
                Mockito.any())).thenReturn(upgradedSocket);

        mgr.connect(endpoint1, null, context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("somehost");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(target.getSchemeName(), target);
        Mockito.verify(detachedSocketFactory, Mockito.times(1)).create(null);
        Mockito.verify(socket, Mockito.times(1)).connect(new InetSocketAddress(remote, 8443), 234);
        Mockito.verify(tlsSocketStrategy).upgrade(socket, "somehost", 443, tlsConfig, context);

        mgr.connect(endpoint1, TimeValue.ofMilliseconds(123), context);

        Mockito.verify(dnsResolver, Mockito.times(2)).resolve("somehost");
        Mockito.verify(schemePortResolver, Mockito.times(2)).resolve(target.getSchemeName(), target);
        Mockito.verify(detachedSocketFactory, Mockito.times(2)).create(null);
        Mockito.verify(socket, Mockito.times(1)).connect(new InetSocketAddress(remote, 8443), 123);
        Mockito.verify(tlsSocketStrategy, Mockito.times(2)).upgrade(socket, "somehost", 443, tlsConfig, context);
    }

    @Test
    void testProxyConnectAndUpgrade() throws Exception {
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
        Mockito.when(schemePortResolver.resolve(proxy.getSchemeName(), proxy)).thenReturn(8080);
        Mockito.when(schemePortResolver.resolve(target.getSchemeName(), target)).thenReturn(8443);
        Mockito.when(tlsSocketStrategyLookup.lookup("https")).thenReturn(tlsSocketStrategy);
        Mockito.when(detachedSocketFactory.create(Mockito.any())).thenReturn(socket);

        mgr.connect(endpoint1, null, context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("someproxy");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(proxy.getSchemeName(), proxy);
        Mockito.verify(detachedSocketFactory, Mockito.times(1)).create(null);
        Mockito.verify(socket, Mockito.times(1)).connect(new InetSocketAddress(remote, 8080), 234);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(conn.getSocket()).thenReturn(socket);

        mgr.upgrade(endpoint1, context);

        Mockito.verify(tlsSocketStrategy, Mockito.times(1)).upgrade(
                socket, "somehost", 443, tlsConfig, context);
    }

    @Test
    void testIsShutdownInitially() {
        Assertions.assertFalse(mgr.isClosed(), "Connection manager should not be shutdown initially.");
    }

    @Test
    void testShutdownIdempotency() {
        mgr.close();
        Assertions.assertTrue(mgr.isClosed(), "Connection manager should remain shutdown after the first call to shutdown.");
        mgr.close(); // Second call to shutdown
        Assertions.assertTrue(mgr.isClosed(), "Connection manager should still be shutdown after subsequent calls to shutdown.");
    }

    @Test
    void testLeaseAfterShutdown() {
        mgr.close();
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // Attempt to lease a connection after shutdown
            mgr.lease("some-id", new HttpRoute(new HttpHost("localhost")), null);
        }, "Attempting to lease a connection after shutdown should throw an exception.");
    }


    @Test
    void testIsShutdown() {
        // Setup phase
        Mockito.when(pool.isShutdown()).thenReturn(false, true); // Simulate changing states

        // Execution phase: Initially, the manager should not be shutdown
        Assertions.assertFalse(mgr.isClosed(), "Connection manager should not be shutdown initially.");

        // Simulate shutting down the manager
        mgr.close();

        // Verification phase: Now, the manager should be reported as shutdown
        Assertions.assertTrue(mgr.isClosed(), "Connection manager should be shutdown after close() is called.");
    }


    @Test
    void testConcurrentShutdown() throws InterruptedException {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        // Submit two shutdown tasks to be run in parallel, explicitly calling close() with no arguments
        executor.submit(() -> mgr.close());
        executor.submit(() -> mgr.close());
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        Assertions.assertTrue(mgr.isClosed(), "Connection manager should be shutdown after concurrent calls to shutdown.");
    }


}
