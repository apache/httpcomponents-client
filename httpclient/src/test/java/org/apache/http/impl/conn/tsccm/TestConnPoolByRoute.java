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

package org.apache.http.impl.conn.tsccm;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.params.BasicHttpParams;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@SuppressWarnings("deprecation")
@RunWith(MockitoJUnitRunner.class)
@Deprecated
public class TestConnPoolByRoute extends ServerTestBase {

    private ConnPoolByRoute impl;
    private HttpRoute route = new HttpRoute(new HttpHost("localhost"));
    private HttpRoute route2 = new HttpRoute(new HttpHost("localhost:8080"));

    @Mock private OperatedClientConnection mockConnection;
    @Mock private OperatedClientConnection mockConnection2;
    @Mock private ClientConnectionOperator mockOperator;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        impl = new ConnPoolByRoute(
                new DefaultClientConnectionOperator(supportedSchemes),
                new ConnPerRouteBean(), 1, -1, TimeUnit.MILLISECONDS);
    }

    private void useMockOperator() {
        reset(mockOperator);
        impl = new ConnPoolByRoute(
                mockOperator, new ConnPerRouteBean(), 1, -1, TimeUnit.MILLISECONDS);
        when(mockOperator.createConnection()).thenReturn(mockConnection);
    }

    @Test
    public void testStatelessConnections() throws Exception {
        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ClientConnectionOperator operator = new DefaultClientConnectionOperator(
                supportedSchemes);

        ConnPerRouteBean connPerRoute = new ConnPerRouteBean(3);
        ConnPoolByRoute connPool = new ConnPoolByRoute(operator, connPerRoute, 20);
        try {
            // Allocate max possible entries
            PoolEntryRequest r1 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e1 = r1.getPoolEntry(10, TimeUnit.SECONDS);
            Assert.assertNotNull(e1);

            PoolEntryRequest r2 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e2 = r2.getPoolEntry(10, TimeUnit.SECONDS);
            Assert.assertNotNull(e2);

            PoolEntryRequest r3 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e3 = r3.getPoolEntry(10, TimeUnit.SECONDS);
            Assert.assertNotNull(e3);

            // Attempt to allocate one more. Expected to fail
            PoolEntryRequest r4 = connPool.requestPoolEntry(route, null);
            try {
                r4.getPoolEntry(250, TimeUnit.MICROSECONDS);
                Assert.fail("ConnectionPoolTimeoutException should have been thrown");
            } catch (ConnectionPoolTimeoutException expected) {
            }

            // Free one
            connPool.freeEntry(e3, true, -1, null);

            // This time the request should succeed
            PoolEntryRequest r5 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e5 = r5.getPoolEntry(10, TimeUnit.SECONDS);
            Assert.assertNotNull(e5);

        } finally {
            connPool.shutdown();
        }
    }

    @Test
    public void testStatefullConnections() throws Exception {
        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ClientConnectionOperator operator = new DefaultClientConnectionOperator(
                supportedSchemes);

        ConnPerRouteBean connPerRoute = new ConnPerRouteBean(3);
        ConnPoolByRoute connPool = new ConnPoolByRoute(operator, connPerRoute, 20);
        try {
            // Allocate max possible entries
            PoolEntryRequest r1 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e1 = r1.getPoolEntry(10, TimeUnit.SECONDS);

            PoolEntryRequest r2 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e2 = r2.getPoolEntry(10, TimeUnit.SECONDS);

            PoolEntryRequest r3 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e3 = r3.getPoolEntry(10, TimeUnit.SECONDS);

            // Set states
            e1.setState(Integer.valueOf(1));
            e2.setState(Integer.valueOf(2));
            e3.setState(Integer.valueOf(3));

            // Release entries
            connPool.freeEntry(e1, true, -1, null);
            connPool.freeEntry(e2, true, -1, null);
            connPool.freeEntry(e3, true, -1, null);

            // Request statefull entries
            PoolEntryRequest r4 = connPool.requestPoolEntry(route, Integer.valueOf(2));
            BasicPoolEntry e4 = r4.getPoolEntry(10, TimeUnit.SECONDS);

            PoolEntryRequest r5 = connPool.requestPoolEntry(route, Integer.valueOf(3));
            BasicPoolEntry e5 = r5.getPoolEntry(10, TimeUnit.SECONDS);

            PoolEntryRequest r6 = connPool.requestPoolEntry(route, Integer.valueOf(1));
            BasicPoolEntry e6 = r6.getPoolEntry(10, TimeUnit.SECONDS);

            Assert.assertNotNull(e4.getState());
            Assert.assertNotNull(e5.getState());
            Assert.assertNotNull(e6.getState());

            // Check whether we got the same objects
            Assert.assertTrue(e4 == e2);
            Assert.assertTrue(e5 == e3);
            Assert.assertTrue(e6 == e1);

            // Release entries again
            connPool.freeEntry(e4, true, -1, null);
            connPool.freeEntry(e5, true, -1, null);
            connPool.freeEntry(e6, true, -1, null);

            // Request an entry with a state not avaialable in the pool
            PoolEntryRequest r7 = connPool.requestPoolEntry(route, Integer.valueOf(4));
            BasicPoolEntry e7 = r7.getPoolEntry(10, TimeUnit.SECONDS);

            // Make sure we got a closed connection and a stateless entry back
            Assert.assertFalse(e7.getConnection().isOpen());
            Assert.assertNull(e7.getState());

        } finally {
            connPool.shutdown();
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void nullOperatorIsNotAllowed() {
        new ConnPoolByRoute(null, new ConnPerRouteBean(), 1, -1, TimeUnit.MILLISECONDS);
    }

    @Test(expected=IllegalArgumentException.class)
    public void nullConnPerRouteIsNotAllowed() {
        new ConnPoolByRoute(new DefaultClientConnectionOperator(supportedSchemes),
                null, 1, -1, TimeUnit.MILLISECONDS);
    }

    @Test
    public void deprecatedConstructorIsStillSupported() {
        new ConnPoolByRoute(new DefaultClientConnectionOperator(supportedSchemes),
                new BasicHttpParams());
    }

    @Test
    public void emptyPoolHasNoConnections() {
        assertEquals(0, impl.getConnectionsInPool());
    }

    @Test
    public void poolHasOneConnectionAfterRequestingOne() throws Exception {
        useMockOperator();
        impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        assertEquals(1, impl.getConnectionsInPool());
    }

    @Test
    public void emptyPoolHasNoRouteSpecificConnections() {
        assertEquals(0, impl.getConnectionsInPool(route));
    }

    @Test
    public void routeSpecificPoolHasOneConnectionAfterRequestingOne() throws Exception {
        useMockOperator();
        impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        assertEquals(1, impl.getConnectionsInPool(route));
    }

    @Test
    public void abortingPoolEntryRequestEarlyDoesNotCreateConnection() {
        PoolEntryRequest req = impl.requestPoolEntry(route, new Object());
        req.abortRequest();
        assertEquals(0, impl.getConnectionsInPool(route));
    }

    @Test(expected=IllegalStateException.class)
    public void cannotAcquireConnectionIfPoolShutdown() throws Exception {
        impl.shutdown();
        impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
    }

    @Test
    public void multipleShutdownsAreOk() {
        impl.shutdown();
        impl.shutdown();
    }

    @Test
    public void canAcquirePoolEntry() throws Exception {
        impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
    }

    @Test
    public void canRetrieveMaxTotalConnections() {
        int max = (new Random()).nextInt(10) + 2;
        impl.setMaxTotalConnections(max);
        assertEquals(max, impl.getMaxTotalConnections());
    }

    @Test
    public void closesFreedConnectionsWhenShutdown() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.shutdown();
        impl.freeEntry(entry, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        verify(mockConnection, atLeastOnce()).close();
    }

    @Test
    public void deleteClosedConnectionsReclaimsPoolSpace() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        assertFalse(impl.freeConnections.isEmpty());
        when(mockConnection.isOpen()).thenReturn(false);
        impl.deleteClosedConnections();
        assertTrue(impl.freeConnections.isEmpty());
        assertEquals(0, impl.numConnections);
    }

    @Test
    public void deleteClosedConnectionsDoesNotReclaimOpenConnections() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        assertFalse(impl.freeConnections.isEmpty());
        when(mockConnection.isOpen()).thenReturn(true);
        impl.deleteClosedConnections();
        assertFalse(impl.freeConnections.isEmpty());
        assertEquals(1, impl.numConnections);
    }

    @Test
    public void closeIdleConnectionsClosesThoseThatHaveTimedOut() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        Thread.sleep(200L);
        impl.closeIdleConnections(1, TimeUnit.MILLISECONDS);
        verify(mockConnection, atLeastOnce()).close();
    }

    @Test
    public void closeIdleConnectionsDoesNotCloseThoseThatHaveNotTimedOut() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        impl.closeIdleConnections(3, TimeUnit.SECONDS);
        verify(mockConnection, never()).close();
    }

    @Test
    public void closeExpiredConnectionsClosesExpiredOnes() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(200L);
        impl.closeExpiredConnections();
        verify(mockConnection, atLeastOnce()).close();
    }

    @Test
    public void closeExpiredConnectionsDoesNotCloseUnexpiredOnes() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, 10, TimeUnit.SECONDS);
        Thread.sleep(200L);
        impl.closeExpiredConnections();
        verify(mockConnection, never()).close();
    }

    @Test
    public void closesNonReusableConnections() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, false, 0, TimeUnit.MILLISECONDS);
        verify(mockConnection, atLeastOnce()).close();
    }

    @Test
    public void handlesExceptionsWhenClosingConnections() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        doThrow(new IOException()).when(mockConnection).close();
        impl.freeEntry(entry, false, 0, TimeUnit.MILLISECONDS);
    }

    @Test
    public void wakesUpWaitingThreadsWhenEntryAvailable() throws Exception {
        useMockOperator();
        when(mockOperator.createConnection()).thenReturn(mockConnection);
        impl.setMaxTotalConnections(1);
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        final Flag f = new Flag(false);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
                    f.flag = true;
                } catch (ConnectionPoolTimeoutException e) {
                } catch (InterruptedException e) {
                }
            }
        });
        t.start();
        Thread.sleep(100);
        impl.freeEntry(entry, true, 1000, TimeUnit.MILLISECONDS);
        Thread.sleep(100);
        assertTrue(f.flag);
    }

    @Test
    public void wakesUpWaitingThreadsOnOtherRoutesWhenEntryAvailable() throws Exception {
        useMockOperator();
        when(mockOperator.createConnection()).thenReturn(mockConnection);
        impl.setMaxTotalConnections(1);
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        final Flag f = new Flag(false);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    impl.requestPoolEntry(route2, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
                    f.flag = true;
                } catch (ConnectionPoolTimeoutException e) {
                } catch (InterruptedException e) {
                }
            }
        });
        t.start();
        Thread.sleep(100);
        impl.freeEntry(entry, true, 1000, TimeUnit.MILLISECONDS);
        Thread.sleep(100);
        assertTrue(f.flag);
    }

    @Test
    public void doesNotRecycleExpiredConnections() throws Exception {
        useMockOperator();
        when(mockOperator.createConnection()).thenReturn(mockConnection, mockConnection2);
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(200L);
        BasicPoolEntry entry2 = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        assertNotSame(mockConnection, entry2.getConnection());
    }

    @Test
    public void closesExpiredConnectionsWhenNotReusingThem() throws Exception {
        useMockOperator();
        when(mockOperator.createConnection()).thenReturn(mockConnection, mockConnection2);
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(200L);
        impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        verify(mockConnection, atLeastOnce()).close();
    }


    @Test
    public void wakesUpWaitingThreadsOnShutdown() throws Exception {
        useMockOperator();
        when(mockOperator.createConnection()).thenReturn(mockConnection);
        when(mockOperator.createConnection()).thenReturn(mockConnection);
        impl.setMaxTotalConnections(1);
        impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        final Flag f = new Flag(false);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
                } catch (IllegalStateException expected) {
                    f.flag = true;
                } catch (ConnectionPoolTimeoutException e) {
                } catch (InterruptedException e) {
                }
            }
        });
        t.start();
        Thread.sleep(1);
        impl.shutdown();
        Thread.sleep(1);
        assertTrue(f.flag);
    }

    private static class Flag {
        public boolean flag;
        public Flag(boolean init) { flag = init; }
    }
}
