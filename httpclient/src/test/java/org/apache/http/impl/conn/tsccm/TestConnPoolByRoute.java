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

@RunWith(MockitoJUnitRunner.class)
public class TestConnPoolByRoute extends ServerTestBase {

    private ConnPoolByRoute impl;
    private HttpRoute route = new HttpRoute(new HttpHost("localhost"));
    
    @Mock
    private OperatedClientConnection mockConnection;
    @Mock
    private ClientConnectionOperator mockOperator;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        impl = new ConnPoolByRoute(
                new DefaultClientConnectionOperator(supportedSchemes),
                new ConnPerRouteBean(), 1, -1, TimeUnit.MILLISECONDS);
    }
    
    private void useMockOperator() {
        impl = new ConnPoolByRoute(
                mockOperator,
                new ConnPerRouteBean(), 1, -1, TimeUnit.MILLISECONDS);
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
    @SuppressWarnings("deprecation")
    public void deprecatedConstructorIsStillSupported() {
        new ConnPoolByRoute(new DefaultClientConnectionOperator(supportedSchemes),
                new BasicHttpParams());
    }
    
    @Test
    public void emptyPoolHasNoConnections() {
        assertEquals(0, impl.getConnectionsInPool());
    }
    
    @Test
    public void emptyPoolHasNoRouteSpecificConnections() {
        assertEquals(0, impl.getConnectionsInPool(route));
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
        Thread.sleep(2);
        impl.closeIdleConnections(1, TimeUnit.MILLISECONDS);
        verify(mockConnection, atLeastOnce()).close();        
    }
    
    @Test
    public void closeIdleConnectionsDoesNotCloseThoseThatHaveNotTimedOut() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        Thread.sleep(1);
        impl.closeIdleConnections(3, TimeUnit.MILLISECONDS);
        verify(mockConnection, never()).close();        
    }

    @Test
    public void closeExpiredConnectionsClosesExpiredOnes() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(2);
        impl.closeExpiredConnections();
        verify(mockConnection, atLeastOnce()).close();        
    }

    @Test
    public void closeExpiredConnectionsDoesNotCloseUnexpiredOnes() throws Exception {
        useMockOperator();
        BasicPoolEntry entry = impl.requestPoolEntry(route, new Object()).getPoolEntry(-1, TimeUnit.MILLISECONDS);
        impl.freeEntry(entry, true, 10, TimeUnit.MILLISECONDS);
        Thread.sleep(1);
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

}
