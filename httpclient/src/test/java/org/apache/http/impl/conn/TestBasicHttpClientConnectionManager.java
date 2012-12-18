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

import java.util.concurrent.TimeUnit;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Lookup;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.SocketClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestBasicHttpClientConnectionManager {

    private SocketClientConnection conn;
    private HttpConnectionFactory<SocketClientConnection> connFactory;
    private Lookup<ConnectionSocketFactory> socketFactoryRegistry;
    private BasicHttpClientConnectionManager mgr;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception {
        conn = Mockito.mock(SocketClientConnection.class);
        connFactory = Mockito.mock(HttpConnectionFactory.class);
        socketFactoryRegistry = Mockito.mock(Lookup.class);
        mgr = new BasicHttpClientConnectionManager(socketFactoryRegistry, connFactory);
    }

    @Test
    public void testLeaseReleaseNonReusable() throws Exception {
        HttpHost target = new HttpHost("localhost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertFalse(conn1.isOpen());

        mgr.releaseConnection(conn1, null, 100, TimeUnit.MILLISECONDS);

        Assert.assertNull(mgr.getRoute());
        Assert.assertNull(mgr.getState());

        ConnectionRequest connRequest2 = mgr.requestConnection(route, null);
        HttpClientConnection conn2 = connRequest2.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertFalse(conn2.isOpen());

        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.<ConnectionConfig>any());
    }

    @Test
    public void testLeaseReleaseReusable() throws Exception {
        HttpHost target = new HttpHost("somehost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        mgr.releaseConnection(conn1, null, 10000, TimeUnit.MILLISECONDS);

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        ConnectionRequest connRequest2 = mgr.requestConnection(route, null);
        HttpClientConnection conn2 = connRequest2.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertTrue(conn2.isOpen());

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());
    }

    @Test
    public void testLeaseReleaseReusableWithState() throws Exception {
        HttpHost target = new HttpHost("somehost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, "some state");
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        mgr.releaseConnection(conn1, "some other state", 10000, TimeUnit.MILLISECONDS);

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals("some other state", mgr.getState());

        ConnectionRequest connRequest2 = mgr.requestConnection(route, "some other state");
        HttpClientConnection conn2 = connRequest2.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertTrue(conn2.isOpen());

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());
    }

    @Test
    public void testLeaseDifferentRoute() throws Exception {
        HttpHost target1 = new HttpHost("somehost");
        HttpRoute route1 = new HttpRoute(target1);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route1, null);
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.releaseConnection(conn1, null, 0, TimeUnit.MILLISECONDS);

        Assert.assertEquals(route1, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        HttpHost target2 = new HttpHost("otherhost");
        HttpRoute route2 = new HttpRoute(target2);
        ConnectionRequest connRequest2 = mgr.requestConnection(route2, null);
        HttpClientConnection conn2 = connRequest2.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertFalse(conn2.isOpen());

        Mockito.verify(conn).close();
        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.<ConnectionConfig>any());
    }

    @Test
    public void testLeaseExpired() throws Exception {
        HttpHost target = new HttpHost("somehost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.releaseConnection(conn1, null, 10, TimeUnit.MILLISECONDS);

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        Thread.sleep(50);

        ConnectionRequest connRequest2 = mgr.requestConnection(route, null);
        HttpClientConnection conn2 = connRequest2.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn2);
        Assert.assertFalse(conn2.isOpen());

        Mockito.verify(conn).close();
        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.<ConnectionConfig>any());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testLeaseInvalidArg() throws Exception {
        mgr.requestConnection(null, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testReleaseInvalidArg() throws Exception {
        mgr.releaseConnection(null, null, 0, TimeUnit.MILLISECONDS);
    }

    @Test(expected=IllegalStateException.class)
    public void testReleaseAnotherConnection() throws Exception {
        HttpClientConnection wrongCon = Mockito.mock(HttpClientConnection.class);
        mgr.releaseConnection(wrongCon, null, 0, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testShutdown() throws Exception {
        HttpHost target = new HttpHost("somehost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        mgr.releaseConnection(conn1, null, 0, TimeUnit.MILLISECONDS);

        mgr.shutdown();

        Mockito.verify(conn, Mockito.times(1)).shutdown();

        try {
            ConnectionRequest connRequest2 = mgr.requestConnection(route, null);
            connRequest2.get(0, TimeUnit.MILLISECONDS);
            Assert.fail("IllegalStateException expected");
        } catch (IllegalStateException ex) {
        }
    }

    @Test
    public void testCloseExpired() throws Exception {
        HttpHost target = new HttpHost("somehost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.releaseConnection(conn1, null, 10, TimeUnit.MILLISECONDS);

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        Thread.sleep(50);

        mgr.closeExpiredConnections();

        Mockito.verify(conn).close();
    }

    @Test
    public void testCloseIdle() throws Exception {
        HttpHost target = new HttpHost("somehost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.<ConnectionConfig>any());

        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE, Boolean.FALSE);

        mgr.releaseConnection(conn1, null, 0, TimeUnit.MILLISECONDS);

        Assert.assertEquals(route, mgr.getRoute());
        Assert.assertEquals(null, mgr.getState());

        Thread.sleep(100);

        mgr.closeIdleConnections(50, TimeUnit.MILLISECONDS);

        Mockito.verify(conn).close();
    }

    @Test(expected=IllegalStateException.class)
    public void testAlreadyLeased() throws Exception {
        HttpHost target = new HttpHost("somehost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(connFactory.create(Mockito.<ConnectionConfig>any())).thenReturn(conn);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(0, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);
        mgr.releaseConnection(conn1, null, 100, TimeUnit.MILLISECONDS);

        mgr.getConnection(route, null);
        mgr.getConnection(route, null);
    }

}
