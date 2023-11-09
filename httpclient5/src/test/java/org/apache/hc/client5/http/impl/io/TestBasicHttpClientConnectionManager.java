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
import java.util.concurrent.TimeUnit;

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
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        mgr = new BasicHttpClientConnectionManager(
                socketFactoryRegistry, connFactory, schemePortResolver, dnsResolver);
    }

    @Test
    public void testLeaseReleaseNonReusable() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);
        Assertions.assertFalse(endpoint1.isConnected());

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        Assertions.assertNull(mgr.getRoute());
        Assertions.assertNull(mgr.getState());

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(conn2);
        Assertions.assertFalse(conn2.isConnected());

        Mockito.verify(connFactory, Mockito.times(2)).createConnection(Mockito.any());
    }

    @Test
    public void testLeaseReleaseReusable() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE);

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        Assertions.assertEquals(route, mgr.getRoute());
        Assertions.assertNull(mgr.getState());

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(conn2);
        Assertions.assertTrue(conn2.isConnected());

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());
    }

    @Test
    public void testLeaseReleaseReusableWithState() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, "some state");
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE);

        mgr.release(endpoint1, "some other state", TimeValue.ofMilliseconds(10000));

        Assertions.assertEquals(route, mgr.getRoute());
        Assertions.assertEquals("some other state", mgr.getState());

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, "some other state");
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(conn2);
        Assertions.assertTrue(conn2.isConnected());

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());
    }

    @Test
    public void testLeaseDifferentRoute() throws Exception {
        final HttpHost target1 = new HttpHost("somehost", 80);
        final HttpRoute route1 = new HttpRoute(target1);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route1, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        Assertions.assertEquals(route1, mgr.getRoute());
        Assertions.assertNull(mgr.getState());

        final HttpHost target2 = new HttpHost("otherhost", 80);
        final HttpRoute route2 = new HttpRoute(target2);
        final LeaseRequest connRequest2 = mgr.lease("some-id", route2, null);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(conn2);
        Assertions.assertFalse(conn2.isConnected());

        Mockito.verify(conn).close(CloseMode.GRACEFUL);
        Mockito.verify(connFactory, Mockito.times(2)).createConnection(Mockito.any());
    }

    @Test
    public void testLeaseExpired() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(10));

        Assertions.assertEquals(route, mgr.getRoute());
        Assertions.assertNull(mgr.getState());

        Thread.sleep(50);

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(conn2);
        Assertions.assertFalse(conn2.isConnected());

        Mockito.verify(conn).close(CloseMode.GRACEFUL);
        Mockito.verify(connFactory, Mockito.times(2)).createConnection(Mockito.any());
    }

    @Test
    public void testReleaseInvalidArg() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () ->
                mgr.release(null, null, TimeValue.NEG_ONE_MILLISECOND));
    }

    @Test
    public void testReleaseAnotherConnection() throws Exception {
        final ConnectionEndpoint wrongCon = Mockito.mock(ConnectionEndpoint.class);
        Assertions.assertThrows(IllegalStateException.class, () ->
                mgr.release(wrongCon, null, TimeValue.NEG_ONE_MILLISECOND));
    }

    @Test
    public void testShutdown() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        mgr.close();

        Mockito.verify(conn, Mockito.times(1)).close(CloseMode.GRACEFUL);

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        Assertions.assertThrows(IllegalStateException.class, () -> connRequest2.get(Timeout.ZERO_MILLISECONDS));

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

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(10));

        Assertions.assertEquals(route, mgr.getRoute());
        Assertions.assertNull(mgr.getState());

        Thread.sleep(50);

        mgr.closeExpired();

        Mockito.verify(conn).close(CloseMode.GRACEFUL);
    }

    @Test
    public void testCloseIdle() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        Assertions.assertEquals(route, mgr.getRoute());
        Assertions.assertNull(mgr.getState());

        Thread.sleep(100);

        mgr.closeIdle(TimeValue.ofMilliseconds(50));

        Mockito.verify(conn).close(CloseMode.GRACEFUL);
    }

    @Test
    public void testAlreadyLeased() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);
        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        mgr.getConnection(route, null);
        Assertions.assertThrows(IllegalStateException.class, () ->
                mgr.getConnection(route, null));
    }

    @Test
    public void testTargetConnect() throws Exception {
        final HttpHost target = new HttpHost("https", "somehost", 443);
        final InetAddress remote = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final HttpRoute route = new HttpRoute(target, local, true);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        final HttpClientContext context = HttpClientContext.create();
        final SocketConfig sconfig = SocketConfig.custom().build();

        mgr.setSocketConfig(sconfig);

        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(234, TimeUnit.MILLISECONDS)
                .build();
        mgr.setConnectionConfig(connectionConfig);
        final TlsConfig tlsConfig = TlsConfig.custom()
                .setHandshakeTimeout(345, TimeUnit.MILLISECONDS)
                .build();
        mgr.setTlsConfig(tlsConfig);

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] {remote});
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(8443);
        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(plainSocketFactory);
        Mockito.when(plainSocketFactory.createSocket(Mockito.any(), Mockito.any())).thenReturn(socket);
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
        Mockito.verify(plainSocketFactory, Mockito.times(1)).createSocket(null, context);
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
        Mockito.verify(plainSocketFactory, Mockito.times(2)).createSocket(null, context);
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

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        final HttpClientContext context = HttpClientContext.create();
        final SocketConfig sconfig = SocketConfig.custom().build();

        mgr.setSocketConfig(sconfig);

        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(234, TimeUnit.MILLISECONDS)
                .build();
        mgr.setConnectionConfig(connectionConfig);
        final TlsConfig tlsConfig = TlsConfig.custom()
                .setHandshakeTimeout(345, TimeUnit.MILLISECONDS)
                .build();
        mgr.setTlsConfig(tlsConfig);

        Mockito.when(dnsResolver.resolve("someproxy")).thenReturn(new InetAddress[] {remote});
        Mockito.when(schemePortResolver.resolve(proxy)).thenReturn(8080);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(8443);
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(sslSocketFactory);
        Mockito.when(plainSocketFactory.createSocket(Mockito.any(), Mockito.any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.eq(socket),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenReturn(socket);

        mgr.connect(endpoint1, null, context);

        Mockito.verify(dnsResolver, Mockito.times(1)).resolve("someproxy");
        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(proxy);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).createSocket(null, context);
        Mockito.verify(plainSocketFactory, Mockito.times(1)).connectSocket(
                socket,
                proxy,
                new InetSocketAddress(remote, 8080),
                new InetSocketAddress(local, 0),
                Timeout.ofMilliseconds(234),
                tlsConfig,
                context);

        Mockito.when(conn.getSocket()).thenReturn(socket);

        mgr.upgrade(endpoint1, context);

        Mockito.verify(schemePortResolver, Mockito.times(1)).resolve(target);
        Mockito.verify(sslSocketFactory, Mockito.times(1)).createLayeredSocket(
                socket, "somehost", 8443, tlsConfig, context);
    }


    @Test
    public void shouldCloseStaleConnectionAndCreateNewOne() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE);

        mgr.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        Assertions.assertEquals(route, mgr.getRoute());
        Assertions.assertNull(mgr.getState());

        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        Mockito.when(conn.isStale()).thenReturn(Boolean.TRUE);
        final ConnectionEndpoint conn2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(conn2);
        Assertions.assertTrue(conn2.isConnected());

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());
    }

    @Test
    public void shouldCloseGRACEFULStaleConnection() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.createConnection(Mockito.any())).thenReturn(conn);

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint1);

        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(conn.isConsistent()).thenReturn(Boolean.TRUE);

        // Simulate the connection being released with no keep-alive (it should be closed)
        mgr.release(endpoint1, null, null);

        // Ensure the connection was closed
        Mockito.verify(conn, Mockito.times(1)).close(CloseMode.GRACEFUL);

        // Now, when a new lease request is made, the connection is stale
        Mockito.when(conn.isStale()).thenReturn(Boolean.TRUE);

        // Attempt to lease a new connection
        final LeaseRequest connRequest2 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint2 = connRequest2.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertNotNull(endpoint2);

        // The connection should be closed and a new one created because the old one was stale
        Mockito.verify(connFactory, Mockito.times(1)).createConnection(Mockito.any());

        // The new connection should be connected
        Assertions.assertTrue(endpoint2.isConnected());
    }

}
