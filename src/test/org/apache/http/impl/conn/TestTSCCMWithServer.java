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


import java.lang.ref.WeakReference;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.params.HttpConnectionManagerParams;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.util.EntityUtils;


/**
 * Tests for <code>ThreadSafeClientConnManager</code> that do require
 * a server to communicate with.
 */
public class TestTSCCMWithServer extends ServerTestBase {

    // Server-based tests not ported from 3.x TestHttpConnectionManager
    //
    // testWriteRequestReleaseConnection
    //      This tests auto-release in case of an I/O error while writing.
    //      It's a test of the execution framework, not of the manager.
    // testConnectMethodFailureRelease
    //      This tests method.fakeResponse() and auto-release. It's a
    //      test of a 3.x specific hack and the execution framework.
    // testResponseAutoRelease
    //      Auto-release is not part of the connection manager anymore.
    // testMaxConnectionsPerServer
    //      Connection limits are already tested without a server.


    public TestTSCCMWithServer(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestTSCCMWithServer.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestTSCCMWithServer.class);
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
            params = defaultParams;
        if (schreg == null)
            schreg = supportedSchemes;

        return new ThreadSafeClientConnManager(params, schreg);
    }



    /**
     * Tests executing several requests in parallel.
     */
    public void testParallelRequests() throws Exception {
        // 3.x: TestHttpConnectionManager.testGetFromMultipleThreads

        final int COUNT = 8; // adjust to execute more requests

        HttpParams mgrpar = defaultParams.copy();
        HttpConnectionManagerParams.setMaxTotalConnections
            (mgrpar, COUNT/2);
        HttpConnectionManagerParams.setDefaultMaxConnectionsPerHost
            (mgrpar, COUNT/2);
        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        ExecReqThread[] threads = new ExecReqThread [COUNT];
        for (int i=0; i<COUNT; i++) {

            HttpRequest request = new BasicHttpRequest
                ("GET", uri, HttpVersion.HTTP_1_1);

            ExecReqThread.RequestSpec ertrs = new ExecReqThread.RequestSpec();
            ertrs.executor = httpExecutor;
            ertrs.processor = httpProcessor;
            ertrs.context = new HttpExecutionContext(null);
            ertrs.params = defaultParams;

            ertrs.context.setAttribute
                (HttpExecutionContext.HTTP_TARGET_HOST, target);
            ertrs.context.setAttribute
                (HttpExecutionContext.HTTP_REQUEST, request);

            threads[i] = new ExecReqThread(mgr, route, 5000L, ertrs);
        }

        for (int i=0; i<threads.length; i++) {
            threads[i].start();
        }

        for (int i=0; i<threads.length; i++) {
            threads[i].join(10000);
            assertNull("exception in thread " + i,
                       threads[i].getException());
            assertNotNull("no response in thread " + i,
                          threads[i].getResponse());
            assertEquals("wrong status code in thread " + i, 200,
                         threads[i].getResponse()
                         .getStatusLine().getStatusCode());
            assertNotNull("no response data in thread " + i,
                          threads[i].getResponseData());
            assertEquals("wrong length of data in thread" + i, rsplen,
                         threads[i].getResponseData().length);
        }

        mgr.shutdown();
    }


    /**
     * Tests releasing and re-using a connection after a response is read.
     */
    public void testReleaseConnection() throws Exception {

        HttpParams mgrpar = defaultParams.copy();
        HttpConnectionManagerParams.setMaxTotalConnections(mgrpar, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = mgr.getConnection(route);
        conn.open(route, httpContext, defaultParams);

        // a new context is created for each testcase, no need to reset
        HttpResponse response = Helper.execute
            (request, conn, target, httpExecutor, httpProcessor, httpContext);

        assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        byte[] data = EntityUtils.toByteArray(response.getEntity());
        assertEquals("wrong length of first response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        // check that there is no auto-release by default
        try {
            // this should fail quickly, connection has not been released
            mgr.getConnection(route, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // release connection without marking for re-use
        // expect the next connection obtained to be closed
        mgr.releaseConnection(conn);
        conn = mgr.getConnection(route);
        assertFalse("connection should have been closed", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        conn.open(route, httpContext, defaultParams);
        httpContext.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
        response = httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        assertEquals("wrong status in second response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        assertEquals("wrong length of second response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        // release connection after marking it for re-use
        // expect the next connection obtained to be open
        conn.markReusable();
        mgr.releaseConnection(conn);
        conn = mgr.getConnection(route);
        assertTrue("connection should have been open", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        httpContext.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
        response = httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        assertEquals("wrong length of third response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        mgr.releaseConnection(conn);
        mgr.shutdown();
    }


    /**
     * Tests GC of an unreferenced connection.
     */
    public void testConnectionGC() throws Exception {
        // 3.x: TestHttpConnectionManager.testReclaimUnusedConnection

        HttpParams mgrpar = defaultParams.copy();
        HttpConnectionManagerParams.setMaxTotalConnections(mgrpar, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = mgr.getConnection(route);
        conn.open(route, httpContext, defaultParams);

        // a new context is created for each testcase, no need to reset
        HttpResponse response = Helper.execute
            (request, conn, target, httpExecutor, httpProcessor, httpContext);

        // we leave the connection in mid-use
        // it's not really re-usable, but it must be closed anyway
        conn.markReusable();

        // first check that we can't get another connection
        try {
            // this should fail quickly, connection has not been released
            mgr.getConnection(route, 10L);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // We now drop the hard references to the connection and trigger GC.
        WeakReference wref = new WeakReference(conn);
        conn = null;
        response = null;
        httpContext = null; // holds a reference to the connection

        // Java does not guarantee that this will trigger the GC, but
        // it does in the test environment. GC is asynchronous, so we
        // need to give the garbage collector some time afterwards.
        System.gc();
        Thread.sleep(1000);

        assertNull("connection not garbage collected", wref.get());
        conn = mgr.getConnection(route, 10L);
        assertFalse("GCed connection not closed", conn.isOpen());

        mgr.shutdown();
    }


    /**
     * Tests GC of an unreferenced connection manager.
     */
    public void testConnectionManagerGC() throws Exception {
        // 3.x: TestHttpConnectionManager.testDroppedThread

        ThreadSafeClientConnManager mgr = createTSCCM(null, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = mgr.getConnection(route);
        conn.open(route, httpContext, defaultParams);

        // a new context is created for each testcase, no need to reset
        HttpResponse response = Helper.execute
            (request, conn, target, httpExecutor, httpProcessor, httpContext);
        byte[] data = EntityUtils.toByteArray(response.getEntity());

        // release connection after marking it for re-use
        conn.markReusable();
        mgr.releaseConnection(conn);

        // We now have a manager with an open connection. We drop all
        // potential hard reference to it and check whether it is GCed.
        // Note that the connection keeps a reference even if detached.
        // Internal references might prevent that if set up incorrectly.

        WeakReference wref = new WeakReference(mgr);

        request = null;
        response = null;
        mgr = null;

        //@@@ the connection currently prevents the manager from being GCed
        conn = null;
        httpContext = null; // holds the connection and request

        // Java does not guarantee that this will trigger the GC, but
        // it does in the test environment. GC is asynchronous, so we
        // need to give the garbage collector some time afterwards.
        System.gc();
        Thread.sleep(1000);

        assertNull("TSCCM not garbage collected", wref.get());
    }


} // class TestTSCCMWithServer
