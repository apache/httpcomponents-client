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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;


/**
 * Tests for <code>ThreadSafeClientConnManager</code> that do not require
 * a server to communicate with.
 */
public class TestTSCCMNoServer extends TestCase {

    public TestTSCCMNoServer(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestTSCCMNoServer.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestTSCCMNoServer.class);
    }


    private static ManagedClientConnection getConnection(
            final ClientConnectionManager mgr, 
            final HttpRoute route,
            long timeout,
            TimeUnit unit) throws ConnectionPoolTimeoutException, InterruptedException {
        ClientConnectionRequest connRequest = mgr.requestConnection(route, null);
        return connRequest.getConnection(timeout, unit);
    }
    
    private static ManagedClientConnection getConnection(
            final ClientConnectionManager mgr, 
            final HttpRoute route) throws ConnectionPoolTimeoutException, InterruptedException {
        ClientConnectionRequest connRequest = mgr.requestConnection(route, null);
        return connRequest.getConnection(0, null);
    }
    
    /**
     * Helper to instantiate a <code>ThreadSafeClientConnManager</code>.
     *
     * @param params    the parameters, or
     *                  <code>null</code> to use defaults
     * @param schreg    the scheme registry, or
     *                  <code>null</code> to use defaults
     *
     * @return  a connection manager to test
     */
    public ThreadSafeClientConnManager createTSCCM(HttpParams params,
                                                   SchemeRegistry schreg) {
        if (params == null)
            params = createDefaultParams();
        if (schreg == null)
            schreg = createSchemeRegistry();

        return new ThreadSafeClientConnManager(params, schreg);
    }


    /**
     * Instantiates default parameters.
     *
     * @return  the default parameters
     */
    public HttpParams createDefaultParams() {

        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);

        return params;
    }

    /**
     * Instantiates a default scheme registry.
     *
     * @return the default scheme registry
     */
    public SchemeRegistry createSchemeRegistry() {

        SchemeRegistry schreg = new SchemeRegistry();
        SocketFactory sf = PlainSocketFactory.getSocketFactory();
        schreg.register(new Scheme("http", sf, 80));

        return schreg;
    }


    public void testConstructor() {
        HttpParams     params = createDefaultParams();
        SchemeRegistry schreg = createSchemeRegistry();

        ThreadSafeClientConnManager mgr =
            new ThreadSafeClientConnManager(params, schreg);
        assertNotNull(mgr);
        mgr.shutdown();
        mgr = null;

        try {
            mgr = new ThreadSafeClientConnManager(null, schreg);
            fail("null parameters not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        } finally {
            if (mgr != null)
                mgr.shutdown();
        }
        mgr = null;

        mgr = new ThreadSafeClientConnManager(params, schreg);
        assertNotNull(mgr);
        mgr.shutdown();
        mgr = null;

    } // testConstructor


    public void testGetConnection() 
            throws InterruptedException, ConnectionPoolTimeoutException {
        ThreadSafeClientConnManager mgr = createTSCCM(null, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn = getConnection(mgr, route);
        assertNotNull(conn);
        assertNull(conn.getRoute());
        assertFalse(conn.isOpen());

        mgr.releaseConnection(conn, -1, null);

        try {
            conn = getConnection(mgr, null);
            fail("null route not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        mgr.shutdown();
    }

    // testTimeout in 3.x TestHttpConnectionManager is redundant
    // several other tests here rely on timeout behavior


    public void testMaxConnTotal() 
            throws InterruptedException, ConnectionPoolTimeoutException {

        HttpParams params = createDefaultParams();
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(1));
        ConnManagerParams.setMaxTotalConnections(params, 2);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);

        ManagedClientConnection conn1 = getConnection(mgr, route1);
        assertNotNull(conn1);
        ManagedClientConnection conn2 = getConnection(mgr, route2);
        assertNotNull(conn2);

        try {
            // this should fail quickly, connection has not been released
            getConnection(mgr, route2, 100L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        
        // release one of the connections
        mgr.releaseConnection(conn2, -1, null);
        conn2 = null;

        // there should be a connection available now
        try {
            conn2 = getConnection(mgr, route2, 100L, TimeUnit.MILLISECONDS);
        } catch (ConnectionPoolTimeoutException cptx) {
            cptx.printStackTrace();
            fail("connection should have been available: " + cptx);
        }

        mgr.shutdown();
    }


    public void testMaxConnPerHost() throws Exception {

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);
        HttpHost target3 = new HttpHost("www.test3.invalid", 80, "http");
        HttpRoute route3 = new HttpRoute(target3, null, false);
        
        HttpParams params = createDefaultParams();
        ConnManagerParams.setMaxTotalConnections(params, 100);
        
        ConnPerRouteBean connPerRoute = new ConnPerRouteBean(1);
        connPerRoute.setMaxForRoute(route2, 2);
        connPerRoute.setMaxForRoute(route3, 3);
        
        ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        // route 3, limit 3
        ManagedClientConnection conn1 =
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
        assertNotNull(conn1);
        ManagedClientConnection conn2 =
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
        assertNotNull(conn2);
        ManagedClientConnection conn3 =
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
        assertNotNull(conn3);
        try {
            // should fail quickly, connection has not been released
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        
        // route 2, limit 2
        conn1 = getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
        conn2 = getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
        try {
            // should fail quickly, connection has not been released
            getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // route 1, should use default limit of 1
        conn1 = getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
        try {
            // should fail quickly, connection has not been released
            getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }


        // check releaseConnection with invalid arguments
        try {
            mgr.releaseConnection(null, -1, null);
            fail("null connection adapter not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }
        try {
            mgr.releaseConnection(new ClientConnAdapterMockup(null), -1, null);
            fail("foreign connection adapter not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        mgr.shutdown();
    }    


    public void testReleaseConnection() throws Exception {

        HttpParams params = createDefaultParams();
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(1));
        ConnManagerParams.setMaxTotalConnections(params, 3);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);
        HttpHost target3 = new HttpHost("www.test3.invalid", 80, "http");
        HttpRoute route3 = new HttpRoute(target3, null, false);

        // the first three allocations should pass
        ManagedClientConnection conn1 =
            getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
        ManagedClientConnection conn2 =
            getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
        ManagedClientConnection conn3 =
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
        assertNotNull(conn1);
        assertNotNull(conn2);
        assertNotNull(conn3);

        // obtaining another connection for either of the three should fail
        // this is somehow redundant with testMaxConnPerHost
        try {
            getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        try {
            getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        try {
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // now release one and check that exactly that one can be obtained then
        mgr.releaseConnection(conn2, -1, null);
        conn2 = null;
        try {
            getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        // this one succeeds
        conn2 = getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
        assertNotNull(conn2);
        try {
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.shutdown();
    }


    public void testDeleteClosedConnections()
            throws InterruptedException, ConnectionPoolTimeoutException {
        
        ThreadSafeClientConnManager mgr = createTSCCM(null, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn = getConnection(mgr, route);

        assertEquals("connectionsInPool",
                     mgr.getConnectionsInPool(), 1);
        assertEquals("connectionsInPool(host)",
                     mgr.getConnectionsInPool(route), 1);
        mgr.releaseConnection(conn, -1, null);

        assertEquals("connectionsInPool",
                     mgr.getConnectionsInPool(), 1);
        assertEquals("connectionsInPool(host)",
                     mgr.getConnectionsInPool(route), 1);

        // this implicitly deletes them
        mgr.closeIdleConnections(0L, TimeUnit.MILLISECONDS);

        assertEquals("connectionsInPool",
                     mgr.getConnectionsInPool(), 0);
        assertEquals("connectionsInPool(host)",
                     mgr.getConnectionsInPool(route), 0);

        mgr.shutdown();
    }

    public void testShutdown() throws Exception {
        // 3.x: TestHttpConnectionManager.testShutdown

        HttpParams params = createDefaultParams();
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(1));
        ConnManagerParams.setMaxTotalConnections(params, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        // get the only connection, then start an extra thread
        // on shutdown, the extra thread should get an exception

        ManagedClientConnection conn =
            getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
        GetConnThread gct = new GetConnThread(mgr, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block


        mgr.shutdown();

        // First release the connection. If the manager keeps working
        // despite the shutdown, this will deblock the extra thread.
        // The release itself should turn into a no-op, without exception.
        mgr.releaseConnection(conn, -1, null);


        gct.join(10000);
        assertNull("thread should not have obtained connection",
                   gct.getConnection());
        assertNotNull("thread should have gotten an exception",
                      gct.getException());
        assertSame("thread got wrong exception",
                   IllegalStateException.class, gct.getException().getClass());

        // the manager is down, we should not be able to get a connection
        try {
            conn = getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
            fail("shut-down manager does not raise exception");
        } catch (IllegalStateException isx) {
            // expected
        }
    }


    public void testInterruptThread() throws Exception {
        // 3.x: TestHttpConnectionManager.testWaitingThreadInterrupted

        HttpParams params = createDefaultParams();
        ConnManagerParams.setMaxTotalConnections(params, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        // get the only connection, then start an extra thread
        ManagedClientConnection conn =
            getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
        GetConnThread gct = new GetConnThread(mgr, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block


        // interrupt the thread, it should cancel waiting with an exception
        gct.interrupt();


        gct.join(10000);
        assertNotNull("thread should have gotten an exception",
                      gct.getException());
        assertSame("thread got wrong exception",
                   InterruptedException.class,
                   gct.getException().getClass());

        // make sure the manager is still working
        try {
            getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
            fail("should have gotten a timeout");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.releaseConnection(conn, -1, null);
        // this time: no exception
        conn = getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
        assertNotNull("should have gotten a connection", conn);

        mgr.shutdown();
    }



    public void testReusePreference() throws Exception {
        // 3.x: TestHttpConnectionManager.testHostReusePreference

        HttpParams params = createDefaultParams();
        ConnManagerParams.setMaxTotalConnections(params, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);

        // get the only connection, then start two extra threads
        ManagedClientConnection conn =
            getConnection(mgr, route1, 1L, TimeUnit.MILLISECONDS);
        GetConnThread gct1 = new GetConnThread(mgr, route1, 1000L);
        GetConnThread gct2 = new GetConnThread(mgr, route2, 1000L);

        // the second thread is started first, to distinguish the
        // route-based reuse preference from first-come, first-served
        gct2.start();
        Thread.sleep(100); // give the thread time to block
        gct1.start();
        Thread.sleep(100); // give the thread time to block


        // releasing the connection for route1 should deblock thread1
        // the other thread gets a timeout
        mgr.releaseConnection(conn, -1, null);

        gct1.join(10000);
        gct2.join(10000);

        assertNotNull("thread 1 should have gotten a connection",
                      gct1.getConnection());
        assertNull   ("thread 2 should NOT have gotten a connection",
                      gct2.getConnection());

        mgr.shutdown();
    }
    
    public void testAbortAfterRequestStarts() throws Exception {
        HttpParams params = createDefaultParams();
        ConnManagerParams.setMaxTotalConnections(params, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);
        
        // get the only connection, then start an extra thread
        ManagedClientConnection conn = getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
        ClientConnectionRequest request = mgr.requestConnection(route, null);
        GetConnThread gct = new GetConnThread(request, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block

        request.abortRequest();

        gct.join(10000);
        assertNotNull("thread should have gotten an exception",
                      gct.getException());
        assertSame("thread got wrong exception",
                   InterruptedException.class,
                   gct.getException().getClass());

        // make sure the manager is still working
        try {
            getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
            fail("should have gotten a timeout");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.releaseConnection(conn, -1, null);
        // this time: no exception
        conn = getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
        assertNotNull("should have gotten a connection", conn);

        mgr.shutdown();
    }
    
    public void testAbortBeforeRequestStarts() throws Exception {
        HttpParams params = createDefaultParams();
        ConnManagerParams.setMaxTotalConnections(params, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);
        

        // get the only connection, then start an extra thread
        ManagedClientConnection conn = getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
        ClientConnectionRequest request = mgr.requestConnection(route, null);
        request.abortRequest();
        
        GetConnThread gct = new GetConnThread(request, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block

        gct.join(10000);
        assertNotNull("thread should have gotten an exception",
                      gct.getException());
        assertSame("thread got wrong exception",
                   InterruptedException.class,
                   gct.getException().getClass());

        // make sure the manager is still working
        try {
            getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
            fail("should have gotten a timeout");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.releaseConnection(conn, -1, null);
        // this time: no exception
        conn = getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
        assertNotNull("should have gotten a connection", conn);

        mgr.shutdown();
    }

} // class TestTSCCMNoServer
