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


import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;


/**
 * Tests for <code>ThreadSafeClientConnManager</code> that do require
 * a server to communicate with.
 */
public class TestTSCCMWithServer extends ServerTestBase {

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
        ConnManagerParams.setMaxTotalConnections
            (mgrpar, COUNT/2);
        ConnManagerParams.setMaxConnectionsPerRoute
            (mgrpar, new ConnPerRouteBean(COUNT/2));
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
            ertrs.context = new BasicHttpContext(null);
            ertrs.params = defaultParams;

            ertrs.context.setAttribute
                (ExecutionContext.HTTP_TARGET_HOST, target);
            ertrs.context.setAttribute
                (ExecutionContext.HTTP_REQUEST, request);

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
     * Tests releasing and re-using a connection after a response is read.
     */
    public void testReleaseConnection() throws Exception {

        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = getConnection(mgr, route);
        conn.open(route, httpContext, defaultParams);

        // a new context is created for each testcase, no need to reset
        HttpResponse response = Helper.execute(
                request, conn, target, 
                httpExecutor, httpProcessor, defaultParams, httpContext);

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
            getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // release connection without marking for re-use
        // expect the next connection obtained to be closed
        mgr.releaseConnection(conn, -1, null);
        conn = getConnection(mgr, route);
        assertFalse("connection should have been closed", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        conn.open(route, httpContext, defaultParams);
        httpContext.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
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
        mgr.releaseConnection(conn, -1, null);
        conn = getConnection(mgr, route);
        assertTrue("connection should have been open", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        httpContext.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        response = httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        assertEquals("wrong length of third response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        mgr.releaseConnection(conn, -1, null);
        mgr.shutdown();
    }

    /**
     * Tests releasing with time limits.
     */
    public void testReleaseConnectionWithTimeLimits() throws Exception {

        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = getConnection(mgr, route);
        conn.open(route, httpContext, defaultParams);

        // a new context is created for each testcase, no need to reset
        HttpResponse response = Helper.execute(
                request, conn, target, 
                httpExecutor, httpProcessor, defaultParams, httpContext);

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
            getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // release connection without marking for re-use
        // expect the next connection obtained to be closed
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);
        conn = getConnection(mgr, route);
        assertFalse("connection should have been closed", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        conn.open(route, httpContext, defaultParams);
        httpContext.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
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
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);
        conn = getConnection(mgr, route);
        assertTrue("connection should have been open", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        httpContext.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        response = httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        assertEquals("wrong length of third response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        conn.markReusable();
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(150);
        conn = getConnection(mgr, route);
        assertTrue("connection should have been closed", !conn.isOpen());
        
        // repeat the communication, no need to prepare the request again
        conn.open(route, httpContext, defaultParams);
        httpContext.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        response = httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        assertEquals("wrong length of fourth response entity",
                     rsplen, data.length);
        // ignore data, but it must be read
        
        mgr.shutdown();
    }


    public void testCloseExpiredConnections() throws Exception {

        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn = getConnection(mgr, route);
        conn.open(route, httpContext, defaultParams);

        assertEquals("connectionsInPool", 1, mgr.getConnectionsInPool());
        assertEquals("connectionsInPool(host)", 1, mgr.getConnectionsInPool(route));
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);

        // Released, still active.
        assertEquals("connectionsInPool", 1, mgr.getConnectionsInPool());
        assertEquals("connectionsInPool(host)", 1,  mgr.getConnectionsInPool(route));

        mgr.closeExpiredConnections();
        
        // Time has not expired yet.
        assertEquals("connectionsInPool", 1, mgr.getConnectionsInPool());
        assertEquals("connectionsInPool(host)", 1,  mgr.getConnectionsInPool(route));
        
        Thread.sleep(150);
        
        mgr.closeExpiredConnections();

        // Time expired now, connections are destroyed.
        assertEquals("connectionsInPool", 0, mgr.getConnectionsInPool());
        assertEquals("connectionsInPool(host)", 0, mgr.getConnectionsInPool(route));

        mgr.shutdown();
    }
    

    /**
     * Tests releasing connection from #abort method called from the
     * main execution thread while there is no blocking I/O operation.
     */
    public void testReleaseConnectionOnAbort() throws Exception {

        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = getConnection(mgr, route);
        conn.open(route, httpContext, defaultParams);

        // a new context is created for each testcase, no need to reset
        HttpResponse response = Helper.execute(
                request, conn, target, 
                httpExecutor, httpProcessor, defaultParams, httpContext);

        assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());

        // check that there are no connections available
        try {
            // this should fail quickly, connection has not been released
            getConnection(mgr, route, 100L, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // abort the connection
        assertTrue(conn instanceof AbstractClientConnAdapter);
        ((AbstractClientConnAdapter) conn).abortConnection();
        
        // the connection is expected to be released back to the manager
        conn = getConnection(mgr, route, 5L, TimeUnit.SECONDS);
        assertFalse("connection should have been closed", conn.isOpen());

        mgr.releaseConnection(conn, -1, null);
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

        ManagedClientConnection conn = getConnection(mgr, route);
        conn.open(route, httpContext, defaultParams);

        // a new context is created for each testcase, no need to reset
        HttpResponse response = Helper.execute(request, conn, target, 
                httpExecutor, httpProcessor, defaultParams, httpContext);
        EntityUtils.toByteArray(response.getEntity());

        // release connection after marking it for re-use
        conn.markReusable();
        mgr.releaseConnection(conn, -1, null);

        // We now have a manager with an open connection in its pool.
        // We drop all potential hard reference to the manager and check
        // whether it is GCed. Internal references might prevent that
        // if set up incorrectly.
        // Note that we still keep references to the connection wrapper
        // we got from the manager, directly as well as in the request
        // and in the context. The manager will be GCed only if the
        // connection wrapper is truly detached.
        WeakReference<ThreadSafeClientConnManager> wref = 
            new WeakReference<ThreadSafeClientConnManager>(mgr);
        mgr = null;

        // Java does not guarantee that this will trigger the GC, but
        // it does in the test environment. GC is asynchronous, so we
        // need to give the garbage collector some time afterwards.
        System.gc();
        Thread.sleep(1000);

        assertNull("TSCCM not garbage collected", wref.get());
    }
    
    public void testAbortDuringConnecting() throws Exception {
        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);
        
        final CountDownLatch connectLatch = new CountDownLatch(1);        
        final StallingSocketFactory stallingSocketFactory = new StallingSocketFactory(connectLatch, WaitPolicy.BEFORE_CONNECT, PlainSocketFactory.getSocketFactory());
        Scheme scheme = new Scheme("http", stallingSocketFactory, 80);
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(scheme);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, registry);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        final ManagedClientConnection conn = getConnection(mgr, route);
        assertTrue(conn instanceof AbstractClientConnAdapter);
        
        final AtomicReference<Throwable> throwRef = new AtomicReference<Throwable>();
        Thread abortingThread = new Thread(new Runnable() {
            public void run() {
                try {
                    stallingSocketFactory.waitForState();
                    conn.abortConnection();
                    connectLatch.countDown();
                } catch (Throwable e) {
                    throwRef.set(e);
                }
            }
        });
        abortingThread.start();
        
        try {
            conn.open(route, httpContext, defaultParams);
            fail("expected SocketException");
        } catch(SocketException expected) {}

        abortingThread.join(5000);
        if(throwRef.get() != null)
            throw new RuntimeException(throwRef.get());
        
        assertFalse(conn.isOpen());
        assertEquals(0, localServer.getAcceptedConnectionCount());
        
        // the connection is expected to be released back to the manager
        ManagedClientConnection conn2 = getConnection(mgr, route, 5L, TimeUnit.SECONDS);
        assertFalse("connection should have been closed", conn2.isOpen());

        mgr.releaseConnection(conn2, -1, null);
        mgr.shutdown();
    }
    
    public void testAbortBeforeSocketCreate() throws Exception {
        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);
        
        final CountDownLatch connectLatch = new CountDownLatch(1);        
        final StallingSocketFactory stallingSocketFactory = new StallingSocketFactory(connectLatch, WaitPolicy.BEFORE_CREATE, PlainSocketFactory.getSocketFactory());
        Scheme scheme = new Scheme("http", stallingSocketFactory, 80);
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(scheme);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, registry);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        final ManagedClientConnection conn = getConnection(mgr, route);
        assertTrue(conn instanceof AbstractClientConnAdapter);
        
        final AtomicReference<Throwable> throwRef = new AtomicReference<Throwable>();
        Thread abortingThread = new Thread(new Runnable() {
            public void run() {
                try {
                    stallingSocketFactory.waitForState();
                    conn.abortConnection();
                    connectLatch.countDown();
                } catch (Throwable e) {
                    throwRef.set(e);
                }
            }
        });
        abortingThread.start();
        
        try {
            conn.open(route, httpContext, defaultParams);
            fail("expected exception");
        } catch(IOException expected) {
            assertEquals("Connection already shutdown", expected.getMessage());
        }

        abortingThread.join(5000);
        if(throwRef.get() != null)
            throw new RuntimeException(throwRef.get());
        
        assertFalse(conn.isOpen());
        assertEquals(0, localServer.getAcceptedConnectionCount());
        
        // the connection is expected to be released back to the manager
        ManagedClientConnection conn2 = getConnection(mgr, route, 5L, TimeUnit.SECONDS);
        assertFalse("connection should have been closed", conn2.isOpen());

        mgr.releaseConnection(conn2, -1, null);
        mgr.shutdown();
    }
    
    public void testAbortAfterSocketConnect() throws Exception {
        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);
        
        final CountDownLatch connectLatch = new CountDownLatch(1);        
        final StallingSocketFactory stallingSocketFactory = new StallingSocketFactory(connectLatch, WaitPolicy.AFTER_CONNECT, PlainSocketFactory.getSocketFactory());
        Scheme scheme = new Scheme("http", stallingSocketFactory, 80);
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(scheme);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, registry);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        final ManagedClientConnection conn = getConnection(mgr, route);
        assertTrue(conn instanceof AbstractClientConnAdapter);
        
        final AtomicReference<Throwable> throwRef = new AtomicReference<Throwable>();
        Thread abortingThread = new Thread(new Runnable() {
            public void run() {
                try {
                    stallingSocketFactory.waitForState();
                    conn.abortConnection();
                    connectLatch.countDown();
                } catch (Throwable e) {
                    throwRef.set(e);
                }
            }
        });
        abortingThread.start();
        
        try {
            conn.open(route, httpContext, defaultParams);
            fail("expected SocketException");
        } catch(SocketException expected) {}

        abortingThread.join(5000);
        if(throwRef.get() != null)
            throw new RuntimeException(throwRef.get());
        
        assertFalse(conn.isOpen());
        // Give the server a bit of time to accept the connection, but
        // ensure that it can accept it.
        for(int i = 0; i < 10; i++) {
            if(localServer.getAcceptedConnectionCount() == 1)
                break;
            Thread.sleep(100);
        }
        assertEquals(1, localServer.getAcceptedConnectionCount());
        
        // the connection is expected to be released back to the manager
        ManagedClientConnection conn2 = getConnection(mgr, route, 5L, TimeUnit.SECONDS);
        assertFalse("connection should have been closed", conn2.isOpen());

        mgr.releaseConnection(conn2, -1, null);
        mgr.shutdown();
    }
    
    public void testAbortAfterOperatorOpen() throws Exception {
        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);
        
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final AtomicReference<StallingOperator> operatorRef = new AtomicReference<StallingOperator>();
        
        ThreadSafeClientConnManager mgr = new ThreadSafeClientConnManager(mgrpar, supportedSchemes) {
            @Override
            protected ClientConnectionOperator createConnectionOperator(
                    SchemeRegistry schreg) {
                operatorRef.set(new StallingOperator(connectLatch, WaitPolicy.AFTER_OPEN, super.createConnectionOperator(schreg)));
                return operatorRef.get();
            }
        };
        assertNotNull(operatorRef.get());

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        final ManagedClientConnection conn = getConnection(mgr, route);
        assertTrue(conn instanceof AbstractClientConnAdapter);
        
        final AtomicReference<Throwable> throwRef = new AtomicReference<Throwable>();
        Thread abortingThread = new Thread(new Runnable() {
            public void run() {
                try {
                    operatorRef.get().waitForState();
                    conn.abortConnection();
                    connectLatch.countDown();
                } catch (Throwable e) {
                    throwRef.set(e);
                }
            }
        });
        abortingThread.start();
        
        try {
            conn.open(route, httpContext, defaultParams);
            fail("expected exception");
        } catch(IOException iox) {
            assertEquals("Request aborted", iox.getMessage());
        }

        abortingThread.join(5000);
        if(throwRef.get() != null)
            throw new RuntimeException(throwRef.get());
        
        assertFalse(conn.isOpen());
        // Give the server a bit of time to accept the connection, but
        // ensure that it can accept it.
        for(int i = 0; i < 10; i++) {
            if(localServer.getAcceptedConnectionCount() == 1)
                break;
            Thread.sleep(100);
        }
        assertEquals(1, localServer.getAcceptedConnectionCount());
        
        // the connection is expected to be released back to the manager
        ManagedClientConnection conn2 = getConnection(mgr, route, 5L, TimeUnit.SECONDS);
        assertFalse("connection should have been closed", conn2.isOpen());

        mgr.releaseConnection(conn2, -1, null);
        mgr.shutdown();
    }
    
    private static class LatchSupport {
        private final CountDownLatch continueLatch;
        private final CountDownLatch waitLatch = new CountDownLatch(1);
        protected final WaitPolicy waitPolicy;
        
        LatchSupport(CountDownLatch continueLatch, WaitPolicy waitPolicy) {
            this.continueLatch = continueLatch;
            this.waitPolicy = waitPolicy;
        }

        void waitForState() throws InterruptedException {
            if(!waitLatch.await(1, TimeUnit.SECONDS))
                throw new RuntimeException("waited too long");
        }
        
        void latch() {
            waitLatch.countDown();
            try {
                if (!continueLatch.await(1, TimeUnit.SECONDS))
                    throw new RuntimeException("waited too long!");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    private static class StallingOperator extends LatchSupport implements ClientConnectionOperator {
        private final ClientConnectionOperator delegate;

        public StallingOperator(CountDownLatch continueLatch,
                WaitPolicy waitPolicy, ClientConnectionOperator delegate) {
            super(continueLatch, waitPolicy);
            this.delegate = delegate;
        }

        public OperatedClientConnection createConnection() {
            return delegate.createConnection();
        }

        public void openConnection(OperatedClientConnection conn,
                HttpHost target, InetAddress local, HttpContext context,
                HttpParams params) throws IOException {
            delegate.openConnection(conn, target, local, context, params);
            if(waitPolicy == WaitPolicy.AFTER_OPEN)
                latch();
        }

        public void updateSecureConnection(OperatedClientConnection conn,
                HttpHost target, HttpContext context, HttpParams params)
                throws IOException {
            delegate.updateSecureConnection(conn, target, context, params);
        }
    }
    
    private static class StallingSocketFactory extends LatchSupport implements SocketFactory {
        private final SocketFactory delegate;

        public StallingSocketFactory(CountDownLatch continueLatch,
                WaitPolicy waitPolicy, SocketFactory delegate) {
            super(continueLatch, waitPolicy);
            this.delegate = delegate;
        }

        public Socket connectSocket(Socket sock, String host, int port,
                InetAddress localAddress, int localPort, HttpParams params)
                throws IOException, UnknownHostException,
                ConnectTimeoutException {
            if(waitPolicy == WaitPolicy.BEFORE_CONNECT)
                latch();
            
            Socket socket = delegate.connectSocket(sock, host, port, localAddress,
                    localPort, params);
            
            if(waitPolicy == WaitPolicy.AFTER_CONNECT)
                latch();
            
            return socket;
        }

        public Socket createSocket() throws IOException {
            if(waitPolicy == WaitPolicy.BEFORE_CREATE)
                latch();
            
            return delegate.createSocket();
        }

        public boolean isSecure(Socket sock) throws IllegalArgumentException {
            return delegate.isSecure(sock);
        }
    }

    private enum WaitPolicy { BEFORE_CREATE, BEFORE_CONNECT, AFTER_CONNECT, AFTER_OPEN }
    

} // class TestTSCCMWithServer
