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

import java.util.concurrent.TimeUnit;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.params.BasicHttpParams;

public class TestConnPoolByRoute extends ServerTestBase {

    public TestConnPoolByRoute(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestConnPoolByRoute.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestConnPoolByRoute.class);
    }

    public void testStatelessConnections() throws Exception {
        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ClientConnectionOperator operator = new DefaultClientConnectionOperator(
                supportedSchemes);
        
        BasicHttpParams params = new BasicHttpParams(); 
        ConnPerRouteBean connPerRoute = new ConnPerRouteBean(3); 
        ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);
        
        ConnPoolByRoute connPool = new ConnPoolByRoute(operator, params);
        try {
            // Allocate max possible entries
            PoolEntryRequest r1 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e1 = r1.getPoolEntry(10, TimeUnit.SECONDS);
            assertNotNull(e1);
            
            PoolEntryRequest r2 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e2 = r2.getPoolEntry(10, TimeUnit.SECONDS);
            assertNotNull(e2);

            PoolEntryRequest r3 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e3 = r3.getPoolEntry(10, TimeUnit.SECONDS);
            assertNotNull(e3);

            // Attempt to allocate one more. Expected to fail
            PoolEntryRequest r4 = connPool.requestPoolEntry(route, null);
            try {
                r4.getPoolEntry(250, TimeUnit.MICROSECONDS);
                fail("ConnectionPoolTimeoutException should have been thrown");
            } catch (ConnectionPoolTimeoutException expected) {
            }

            // Free one
            connPool.freeEntry(e3, true, -1, null);

            // This time the request should succeed
            PoolEntryRequest r5 = connPool.requestPoolEntry(route, null);
            BasicPoolEntry e5 = r5.getPoolEntry(10, TimeUnit.SECONDS);
            assertNotNull(e5);
            
        } finally {
            connPool.shutdown();
        }
    }

    public void testStatefullConnections() throws Exception {
        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ClientConnectionOperator operator = new DefaultClientConnectionOperator(
                supportedSchemes);
        
        BasicHttpParams params = new BasicHttpParams(); 
        ConnPerRouteBean connPerRoute = new ConnPerRouteBean(3); 
        ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);
        
        ConnPoolByRoute connPool = new ConnPoolByRoute(operator, params);
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

            assertNotNull(e4.getState());
            assertNotNull(e5.getState());
            assertNotNull(e6.getState());
            
            // Check whether we got the same objects
            assertTrue(e4 == e2);
            assertTrue(e5 == e3);
            assertTrue(e6 == e1);

            // Release entries again
            connPool.freeEntry(e4, true, -1, null);
            connPool.freeEntry(e5, true, -1, null);
            connPool.freeEntry(e6, true, -1, null);

            // Request an entry with a state not avaialable in the pool
            PoolEntryRequest r7 = connPool.requestPoolEntry(route, Integer.valueOf(4));
            BasicPoolEntry e7 = r7.getPoolEntry(10, TimeUnit.SECONDS);

            // Make sure we got a closed connection and a stateless entry back
            assertFalse(e7.getConnection().isOpen());
            assertNull(e7.getState());
            
        } finally {
            connPool.shutdown();
        }
    }

}
