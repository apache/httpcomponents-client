/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.SocketFactory;
import org.apache.http.conn.PlainSocketFactory;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.HostConfiguration; //@@@ deprecated
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.params.HttpConnectionManagerParams;


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

        final String paramkey = "test.parameter";
        final String paramval = "Value of the test parameter.";
        params.setParameter(paramkey, paramval);

        ThreadSafeClientConnManager mgr =
            new ThreadSafeClientConnManager(params, schreg);
        assertNotNull(mgr);
        assertNotNull(mgr.getParams());
        assertEquals(paramval, mgr.getParams().getParameter(paramkey));
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
        assertNotNull(mgr.getParams());
        assertEquals(paramval, mgr.getParams().getParameter(paramkey));
        mgr.shutdown();
        mgr = null;

    } // testConstructor


    public void testGetConnection() {
        ThreadSafeClientConnManager mgr = createTSCCM(null, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn = mgr.getConnection(route);
        assertNotNull(conn);
        assertNull(conn.getRoute());
        assertFalse(conn.isOpen());

        mgr.releaseConnection(conn);

        try {
            conn = mgr.getConnection(null);
            fail("null route not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        mgr.shutdown();
    }

    // testTimeout in 3.x TestHttpConnectionManager is redundant
    // several other tests here rely on timeout behavior


    public void testMaxConnTotal() {

        HttpParams params = createDefaultParams();
        HttpConnectionManagerParams.setDefaultMaxConnectionsPerHost(params, 1);
        HttpConnectionManagerParams.setMaxTotalConnections(params, 2);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);

        ManagedClientConnection conn1 = mgr.getConnection(route1);
        ManagedClientConnection conn2 = mgr.getConnection(route2);

        try {
            // this should fail quickly, connection has not been released
            mgr.getConnection(route2, 100L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        
        // release one of the connections
        mgr.releaseConnection(conn2);
        conn2 = null;

        // there should be a connection available now
        try {
            conn2 = mgr.getConnection(route2, 100L);
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
        HttpConnectionManagerParams.setMaxTotalConnections(params, 100);
        HttpConnectionManagerParams.setDefaultMaxConnectionsPerHost(params, 1);
        //@@@ HostConfiguration is deprecated
        //@@@ should it be mapped to HttpHost or HttpRoute here?
        //@@@ provide setter in TSCCM, it is implementation specific
        HttpConnectionManagerParams.setMaxConnectionsPerHost
            (params, route2.toHostConfig(), 2);
        HttpConnectionManagerParams.setMaxConnectionsPerHost
            (params, route3.toHostConfig(), 3);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        // route 3, limit 3
        ManagedClientConnection conn1 = mgr.getConnection(route3, 10L);
        ManagedClientConnection conn2 = mgr.getConnection(route3, 10L);
        ManagedClientConnection conn3 = mgr.getConnection(route3, 10L);
        try {
            // should fail quickly, connection has not been released
            mgr.getConnection(route3, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        
        // route 2, limit 2
        conn1 = mgr.getConnection(route2, 10L);
        conn2 = mgr.getConnection(route2, 10L);
        try {
            // should fail quickly, connection has not been released
            mgr.getConnection(route2, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // route 1, should use default limit of 1
        conn1 = mgr.getConnection(route1, 10L);
        try {
            // should fail quickly, connection has not been released
            mgr.getConnection(route1, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }


        // check releaseConnection with invalid arguments
        try {
            mgr.releaseConnection(null);
            fail("null connection adapter not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }
        try {
            mgr.releaseConnection(new ClientConnAdapterMockup());
            fail("foreign connection adapter not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        mgr.shutdown();
    }    


    public void testReleaseConnection() throws Exception {

        HttpParams params = createDefaultParams();
        HttpConnectionManagerParams.setDefaultMaxConnectionsPerHost(params, 1);
        HttpConnectionManagerParams.setMaxTotalConnections(params, 3);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);
        HttpHost target3 = new HttpHost("www.test3.invalid", 80, "http");
        HttpRoute route3 = new HttpRoute(target3, null, false);

        // the first three allocations should pass
        ManagedClientConnection conn1 = mgr.getConnection(route1, 10L);
        ManagedClientConnection conn2 = mgr.getConnection(route2, 10L);
        ManagedClientConnection conn3 = mgr.getConnection(route3, 10L);
        assertNotNull(conn1);
        assertNotNull(conn2);
        assertNotNull(conn3);

        // obtaining another connection for either of the three should fail
        // this is somehow redundant with testMaxConnPerHost
        try {
            mgr.getConnection(route1, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        try {
            mgr.getConnection(route2, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        try {
            mgr.getConnection(route3, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // now release one and check that exactly that one can be obtained then
        mgr.releaseConnection(conn2);
        conn2 = null;
        try {
            mgr.getConnection(route1, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        // this one succeeds
        conn2 = mgr.getConnection(route2, 10L);
        assertNotNull(conn2);
        try {
            mgr.getConnection(route3, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.shutdown();
    }


    public void testDeleteClosedConnections() {
        
        ThreadSafeClientConnManager mgr = createTSCCM(null, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);
        HostConfiguration hcfg = route.toHostConfig(); //@@@ deprecated

        ManagedClientConnection conn = mgr.getConnection(route);

        assertEquals("connectionsInPool",
                     mgr.getConnectionsInPool(), 1);
        assertEquals("connectionsInPool(host)",
                     mgr.getConnectionsInPool(hcfg), 1);
        mgr.releaseConnection(conn);

        assertEquals("connectionsInPool",
                     mgr.getConnectionsInPool(), 1);
        assertEquals("connectionsInPool(host)",
                     mgr.getConnectionsInPool(hcfg), 1);

        mgr.closeIdleConnections(0L); // implicitly deletes them, too

        assertEquals("connectionsInPool",
                     mgr.getConnectionsInPool(), 0);
        assertEquals("connectionsInPool(host)",
                     mgr.getConnectionsInPool(hcfg), 0);

        mgr.shutdown();
    }


    public void testShutdown() throws Exception {
        // 3.x: TestHttpConnectionManager.testShutdown

        HttpParams params = createDefaultParams();
        HttpConnectionManagerParams.setDefaultMaxConnectionsPerHost(params, 1);
        HttpConnectionManagerParams.setMaxTotalConnections(params, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        // get the only connection, then start an extra thread
        // on shutdown, the extra thread should get an exception

        ManagedClientConnection conn = mgr.getConnection(route, 1L);
        GetConnThread gct = new GetConnThread(mgr, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block


        mgr.shutdown();

        // First release the connection. If the manager keeps working
        // despite the shutdown, this will deblock the extra thread.
        // The release itself should turn into a no-op, without exception.
        mgr.releaseConnection(conn);


        gct.join(10000);
        assertNull("thread should not have obtained connection",
                   gct.getConnection());
        assertNotNull("thread should have gotten an exception",
                      gct.getException());
        assertSame("thread got wrong exception",
                   IllegalStateException.class, gct.getException().getClass());

        // the manager is down, we should not be able to get a connection
        try {
            conn = mgr.getConnection(route, 1L);
            fail("shut-down manager does not raise exception");
        } catch (IllegalStateException isx) {
            // expected
        }
    }


    public void testInterruptThread() throws Exception {
        // 3.x: TestHttpConnectionManager.testWaitingThreadInterrupted

        HttpParams params = createDefaultParams();
        HttpConnectionManagerParams.setMaxTotalConnections(params, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        // get the only connection, then start an extra thread
        ManagedClientConnection conn = mgr.getConnection(route, 1L);
        GetConnThread gct = new GetConnThread(mgr, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block


        // interrupt the thread, it should cancel waiting with an exception
        gct.interrupt();


        gct.join(10000);
        assertNotNull("thread should have gotten an exception",
                      gct.getException());
        assertSame("thread got wrong exception",
                   IllegalThreadStateException.class,
                   gct.getException().getClass());

        // make sure the manager is still working
        try {
            mgr.getConnection(route, 10L);
            fail("should have gotten a timeout");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.releaseConnection(conn);
        conn = mgr.getConnection(route, 10L); // this time, no exception
        assertNotNull("should have gotten a connection", conn);

        mgr.shutdown();
    }



    public void testReusePreference() throws Exception {
        // 3.x: TestHttpConnectionManager.testHostReusePreference

        HttpParams params = createDefaultParams();
        HttpConnectionManagerParams.setMaxTotalConnections(params, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);

        // get the only connection, then start two extra threads
        ManagedClientConnection conn = mgr.getConnection(route1, 1L);
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
        mgr.releaseConnection(conn);

        gct1.join(10000);
        gct2.join(10000);

        assertNotNull("thread 1 should have gotten a connection",
                      gct1.getConnection());
        assertNull   ("thread 2 should NOT have gotten a connection",
                      gct2.getConnection());

        mgr.shutdown();
    }

    // 3.x TestHttpConnectionManager.testShutdownAll is not ported
    // the shutdownAll() method is scheduled for removal

} // class TestTSCCMNoServer
