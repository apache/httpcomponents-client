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

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestBasicHttpClientConnectionManager {

    @Mock
    private ManagedHttpClientConnection conn;
    @Mock
    private HttpConnectionFactory<ManagedHttpClientConnection> connFactory;
    @Mock
    private Lookup<ConnectionSocketFactory> socketFactoryRegistry;
    @Mock
    private ConnectionSocketFactory plainSocketFactory;
    @Mock
    private LayeredConnectionSocketFactory sslSocketFactory;
    @Mock
    private Socket socket;
    @Mock
    private SchemePortResolver schemePortResolver;
    @Mock
    private DnsResolver dnsResolver;

    private BasicHttpClientConnectionManager mgr;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mgr = new BasicHttpClientConnectionManager(
                socketFactoryRegistry, connFactory, schemePortResolver, dnsResolver);
    }

    @Test
    public void testLeaseReleaseNonReusable() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);
        Assert.assertFalse(endpoint1.isConnected());

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        Assert.assertNull(mgr.getRoute());
        Assert.assertNull(mgr.getState());

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertFalse(conn2.isConnected());

        Mockito.verify(connFactory, Mockito.times(2)).createConnection(Mockito.<Socket>any());
    }

    @Test
    public void testLeaseReleaseReusable() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertTrue(conn2.isConnected());

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());
    }

    @Test
    public void testLeaseReleaseReusableWithState() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, "some state");
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        mgr.release(endpoint1, "some other state", TimeValue.ofMilliseconds(10000));

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals("some other state", mgr.getState());

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, "some other state");
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertTrue(conn2.isConnected());

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());
    }

    @Test
    public void testLeaseDifferentRoute() throws Exception {
        final HttpHost target1 = new HttpHost("somehost", 80);
        final HttpRoute route1 = new HttpRoute(target1);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route1, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        Assert.assertEquals(route1, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        final HttpHost target2 = new HttpHost("otherhost", 80);
        final HttpRoute route2 = new HttpRoute(target2);
        final LeaseRequest connRequest2 = mgr.lease("some-id", route2, null);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertFalse(conn2.isConnected());

        Mockito.verify(conn).close(CloseMode.GRACEFUL);
        Mockito.verify(connFactory, Mockito.times(2)).createConnection(Mockito.<Socket>any());
    }

    @Test
    public void testLeaseExpired() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(10));

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        Thread.sleep(50);

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertFalse(conn2.isConnected());

        Mockito.verify(conn).close(CloseMode.GRACEFUL);
        Mockito.verify(connFactory, Mockito.times(2)).createConnection(Mockito.<Socket>any());
    }

    @Test(expected=NullPointerException.class)
    public void testReleaseInvalidArg() throws Exception {
        mgr.release(null, null, TimeValue.NEG_ONE_MILLISECOND);
    }

    @Test(expected=IllegalStateException.class)
    public void testReleaseAnotherConnection() throws Exception {
        final ConnectionEndpoint wrongCon = Mockito.mock(ConnectionEndpoint.class);
        mgr.release(wrongCon, null, TimeValue.NEG_ONE_MILLISECOND);
    }

    @Test
    public void testShutdown() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        mgr.close();

        Mockito.verify(conn, Mockito.times(1)).close(CloseMode.GRACEFUL);

        try {
            final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
            connRequest2.get(Timeout.ZERO_MILLISECONDS);
            Assert.fail("IllegalStateException expected");
        } catch (final IllegalStateException ex) {
        }

        // Should have no effect
        mgr.closeExpired();
        mgr.closeIdle(TimeValue.ZERO_MILLISECONDS);
        mgr.close();

        Mockito.verify(conn, Mockito.times(1)).close(CloseMode.GRACEFUL);
    }

    @Test
    public void testCloseExpired() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(10));

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        Thread.sleep(50);

        mgr.closeExpired();

        Mockito.verify(conn).close(CloseMode.GRACEFUL);
    }

    @Test
    public void testCloseIdle() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.<Socket>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        Thread.sleep(100);

        mgr.closeIdle(TimeValue.ofMilliseconds(50));

        Mockito.verify(conn).close(CloseMode.GRACEFUL);
    }

    @Test(expected=IllegalStateException.class)
    public void testAlreadyLeased() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);
        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        mgr.getConnection(route, null);
        mgr.getConnection(route, null);
    }

    @Test
    public void testTargetConnect() throws Exception {
        final HttpHost target = new HttpHost("https", "somehost", 443);
        final InetAddress remote = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final HttpRoute route = new HttpRoute(target, local, true);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        final HttpClientContext context = HttpClientContext.create();
        final SocketConfig sconfig = SocketConfig.custom().build();

        mgr.setSocketConfig(sconfig);

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] {remote});
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(8443);
        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(plainSocketFactory);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.<TimeValue>any(),
                Mockito.eq(socket),
                Mockito.<HttpHost>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenReturn(socket);

        mgr.connect(endpoint1, TimeValue.ofMilliseconds(123), context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("somehost");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(target);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).createSocket(context);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).connectSocket(TimeValue.ofMilliseconds(123), socket, target,
                new InetSocketAddress(remote, 8443),
                new InetSocketAddress(local, 0), context);
    }

    @Test
    public void testProxyConnectAndUpgrade() throws Exception {
        final HttpHost target = new HttpHost("https", "somehost", 443);
        final HttpHost proxy = new HttpHost("someproxy", 8080);
        final InetAddress remote = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final HttpRoute route = new HttpRoute(target, local, proxy, true);

        Mockito.when(connFactory.createConnection(Mockito.<Socket>any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assert.assertNotNull(endpoint1);

        final HttpClientContext context = HttpClientContext.create();
        final SocketConfig sconfig = SocketConfig.custom().build();

        mgr.setSocketConfig(sconfig);

        Mockito.when(dnsResolver.resolve("someproxy")).thenReturn(new InetAddress[] {remote});
        Mockito.when(schemePortResolver.resolve(proxy)).thenReturn(8080);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(8443);
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(sslSocketFactory);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.<TimeValue>any(),
                Mockito.eq(socket),
                Mockito.<HttpHost>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenReturn(socket);

        mgr.connect(endpoint1, TimeValue.ofMilliseconds(123), context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("someproxy");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(proxy);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).createSocket(context);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).connectSocket(TimeValue.ofMilliseconds(123), socket, proxy,
                new InetSocketAddress(remote, 8080),
                new InetSocketAddress(local, 0), context);

        Mockito.when(conn.getSocket()).thenReturn(socket);

        mgr.upgrade(endpoint1, context);

        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(target);
        Mockito.verify(sslSocketFactory, Mockito.times(1)).createLayeredSocket(
                socket, "somehost", 8443, context);
    }

}
