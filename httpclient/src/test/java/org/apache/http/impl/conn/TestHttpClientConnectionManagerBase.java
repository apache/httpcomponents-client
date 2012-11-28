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

import java.net.Socket;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.config.Lookup;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.SocketClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.pool.ConnPool;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestHttpClientConnectionManagerBase {

    private SocketClientConnection conn;
    private Socket socket;
    private ConnectionSocketFactory plainSocketFactory;
    private Lookup<ConnectionSocketFactory> socketFactoryRegistry;
    private SchemePortResolver schemePortResolver;
    private DnsResolver dnsResolver;
    private Future<CPoolEntry> future;
    private ConnPool<HttpRoute, CPoolEntry> pool;
    private HttpClientConnectionManagerBase mgr;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception {
        conn = Mockito.mock(SocketClientConnection.class);
        socket = Mockito.mock(Socket.class);
        plainSocketFactory = Mockito.mock(ConnectionSocketFactory.class);
        socketFactoryRegistry = Mockito.mock(Lookup.class);
        schemePortResolver = Mockito.mock(SchemePortResolver.class);
        dnsResolver = Mockito.mock(DnsResolver.class);
        pool = Mockito.mock(ConnPool.class);
        future = Mockito.mock(Future.class);
        mgr = new HttpClientConnectionManagerBase(
                pool, socketFactoryRegistry, schemePortResolver, dnsResolver) {

            public void closeIdleConnections(long idletime, TimeUnit tunit) {
            }

            public void closeExpiredConnections() {
            }

            public void shutdown() {
            }

        };
    }

    @Test
    public void testLeaseRelease() throws Exception {
        HttpHost target = new HttpHost("localhost");
        HttpRoute route = new HttpRoute(target);

        CPoolEntry entry = new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS);

        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);

        Mockito.when(future.isCancelled()).thenReturn(false);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertFalse(conn1.isOpen());
        Assert.assertNotSame(conn, conn1);

        mgr.releaseConnection(conn1, null, 0, TimeUnit.MILLISECONDS);

        Mockito.verify(pool).release(entry, false);
    }

    @Test(expected=InterruptedException.class)
    public void testLeaseFutureCancelled() throws Exception {
        HttpHost target = new HttpHost("localhost");
        HttpRoute route = new HttpRoute(target);

        CPoolEntry entry = new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS);

        Mockito.when(future.isCancelled()).thenReturn(Boolean.TRUE);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        connRequest1.get(1, TimeUnit.SECONDS);
    }

    @Test(expected=ConnectionPoolTimeoutException.class)
    public void testLeaseFutureTimeout() throws Exception {
        HttpHost target = new HttpHost("localhost");
        HttpRoute route = new HttpRoute(target);

        Mockito.when(future.isCancelled()).thenReturn(Boolean.TRUE);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        connRequest1.get(1, TimeUnit.SECONDS);
    }

    @Test
    public void testReleaseReusable() throws Exception {
        HttpHost target = new HttpHost("localhost");
        HttpRoute route = new HttpRoute(target);

        CPoolEntry entry = Mockito.spy(new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS));

        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);
        Mockito.when(conn.isOpen()).thenReturn(Boolean.TRUE);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertTrue(conn1.isOpen());

        mgr.releaseConnection(conn1, "some state", 0, TimeUnit.MILLISECONDS);

        Mockito.verify(pool).release(entry, true);
        Mockito.verify(entry).setState("some state");
        Mockito.verify(entry).updateExpiry(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testReleaseNonReusable() throws Exception {
        HttpHost target = new HttpHost("localhost");
        HttpRoute route = new HttpRoute(target);

        CPoolEntry entry = Mockito.spy(new CPoolEntry(LogFactory.getLog(getClass()), "id", route, conn,
                -1, TimeUnit.MILLISECONDS));

        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(route, null, null)).thenReturn(future);
        Mockito.when(conn.isOpen()).thenReturn(Boolean.FALSE);

        ConnectionRequest connRequest1 = mgr.requestConnection(route, null);
        HttpClientConnection conn1 = connRequest1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertFalse(conn1.isOpen());

        mgr.releaseConnection(conn1, "some state", 0, TimeUnit.MILLISECONDS);

        Mockito.verify(pool).release(entry, false);
        Mockito.verify(entry, Mockito.never()).setState(Mockito.anyObject());
        Mockito.verify(entry, Mockito.never()).updateExpiry(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
    }

}
