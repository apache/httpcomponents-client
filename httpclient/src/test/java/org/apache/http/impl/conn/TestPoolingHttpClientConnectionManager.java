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

package org.apache.http.impl.conn;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Lookup;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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
    private Future<CPoolEntry> future;
    @Mock
    private CPool pool;
    private PoolingHttpClientConnectionManager mgr;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mgr = new PoolingHttpClientConnectionManager(
                pool, socketFactoryRegistry, schemePortResolver, dnsResolver);
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final CPoolEntry entry = new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS);
        entry.markRouteComplete();

        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(future.isCancelled()).thenReturn(false);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        final ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        final HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertNotSame(conn, conn1);

        mgr.releaseConnection(conn1, null, 0, TimeUnit.MILLISECONDS);

        Mockito.verify(pool).release(entry, true);
    }

    @Test
    public void testReleaseRouteIncomplete() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final CPoolEntry entry = new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS);

        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(future.isCancelled()).thenReturn(false);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        final ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        final HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertNotSame(conn, conn1);

        mgr.releaseConnection(conn1, null, 0, TimeUnit.MILLISECONDS);

        Mockito.verify(pool).release(entry, false);
    }

    @Test(expected=InterruptedException.class)
    public void testLeaseFutureCancelled() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final CPoolEntry entry = new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS);
        entry.markRouteComplete();

        Mockito.when(future.isCancelled()).thenReturn(Boolean.TRUE);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        final ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        connRequest1.get(1, TimeUnit.SECONDS);
    }

    @Test(expected=ConnectionPoolTimeoutException.class)
    public void testLeaseFutureTimeout() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(future.isCancelled()).thenReturn(Boolean.TRUE);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        final ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        connRequest1.get(1, TimeUnit.SECONDS);
    }

    @Test
    public void testReleaseReusable() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final CPoolEntry entry = Mockito.spy(new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS));
        entry.markRouteComplete();

        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);
        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        final ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        final HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertTrue(conn1.isOpen());

        mgr.releaseConnection(conn1, "some state", 0, TimeUnit.MILLISECONDS);

        Mockito.verify(pool).release(entry, true);
        Mockito.verify(entry).setState("some state");
        Mockito.verify(entry).updateExpiry(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testReleaseNonReusable() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final CPoolEntry entry = Mockito.spy(new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS));
        entry.markRouteComplete();

        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);
        Mockito.when(conn.isOpen()).thenReturn(Boolean.FALSE);

        final ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        final HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertFalse(conn1.isOpen());

        mgr.releaseConnection(conn1, "some state", 0, TimeUnit.MILLISECONDS);

        Mockito.verify(pool).release(entry, false);
        Mockito.verify(entry, Mockito.never()).setState(Mockito.anyObject());
        Mockito.verify(entry, Mockito.never()).updateExpiry(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testTargetConnect() throws Exception {
        final HttpHost target = new HttpHost("somehost", 443, "https");
        final InetAddress remote = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress local = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        final HttpRoute route = new HttpRoute(target, local, true);

        final CPoolEntry entry = new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS);
        entry.markRouteComplete();
        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(future.isCancelled()).thenReturn(false);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        final ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        final HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);

        final HttpClientContext context = HttpClientContext.create();
        final SocketConfig sconfig = SocketConfig.custom().build();

        mgr.setDefaultSocketConfig(sconfig);

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[]{remote});
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(8443);
        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(plainSocketFactory);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.anyInt(),
                Mockito.eq(socket),
                Mockito.<HttpHost>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenReturn(socket);

        mgr.connect(conn1, route, 123, context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("somehost");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(target);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).createSocket(context);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).connectSocket(123, socket, target,
                new InetSocketAddress(remote, 8443),
                new InetSocketAddress(local, 0), context);

        mgr.routeComplete(conn1, route, context);
    }

    @Test
    public void testProxyConnectAndUpgrade() throws Exception {
        final HttpHost target = new HttpHost("somehost", 443, "https");
        final HttpHost proxy = new HttpHost("someproxy", 8080);
        final InetAddress remote = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final HttpRoute route = new HttpRoute(target, local, proxy, true);

        final CPoolEntry entry = new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS);
        entry.markRouteComplete();
        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(future.isCancelled()).thenReturn(false);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        final ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        final HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);

        final ConnectionSocketFactory plainsf = Mockito.mock(ConnectionSocketFactory.class);
        final LayeredConnectionSocketFactory sslsf = Mockito.mock(LayeredConnectionSocketFactory.class);
        final Socket mockSock = Mockito.mock(Socket.class);
        final HttpClientContext context = HttpClientContext.create();
        final SocketConfig sconfig = SocketConfig.custom().build();
        final ConnectionConfig cconfig = ConnectionConfig.custom().build();

        mgr.setDefaultSocketConfig(sconfig);
        mgr.setDefaultConnectionConfig(cconfig);

        Mockito.when(dnsResolver.resolve("someproxy")).thenReturn(new InetAddress[] {remote});
        Mockito.when(schemePortResolver.resolve(proxy)).thenReturn(8080);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(8443);
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainsf);
        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(sslsf);
        Mockito.when(plainsf.createSocket(Mockito.<HttpContext>any())).thenReturn(mockSock);
        Mockito.when(plainsf.connectSocket(
                Mockito.anyInt(),
                Mockito.eq(mockSock),
                Mockito.<HttpHost>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenReturn(mockSock);

        mgr.connect(conn1, route, 123, context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("someproxy");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(proxy);
        Mockito.verify(plainsf, Mockito.times(1)).createSocket(context);
        Mockito.verify(plainsf, Mockito.times(1)).connectSocket(123, mockSock, proxy,
                new InetSocketAddress(remote, 8080),
                new InetSocketAddress(local, 0), context);

        Mockito.when(conn.getSocket()).thenReturn(mockSock);

        mgr.upgrade(conn1, route, context);

        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(target);
        Mockito.verify(sslsf, Mockito.times(1)).createLayeredSocket(
                mockSock, "somehost", 8443, context);

        mgr.routeComplete(conn1, route, context);
    }

}
