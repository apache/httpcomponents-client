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

package org.apache.http.impl.client.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@code PoolingHttpClientConnectionManager} that do require a server
 * to communicate with.
 */
public class TestConnectionManagement extends LocalServerTestBase {

    private static HttpClientConnection getConnection(
            final HttpClientConnectionManager mgr,
            final HttpRoute route,
            final long timeout,
            final TimeUnit unit) throws ConnectionPoolTimeoutException, ExecutionException, InterruptedException {
        final ConnectionRequest connRequest = mgr.requestConnection(route, null);
        return connRequest.get(timeout, unit);
    }

    private static HttpClientConnection getConnection(
            final HttpClientConnectionManager mgr,
            final HttpRoute route) throws ConnectionPoolTimeoutException, ExecutionException, InterruptedException {
        final ConnectionRequest connRequest = mgr.requestConnection(route, null);
        return connRequest.get(0, TimeUnit.MILLISECONDS);
    }

    /**
     * Tests releasing and re-using a connection after a response is read.
     */
    @Test
    public void testReleaseConnection() throws Exception {

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        final HttpRequest request = new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);
        final HttpContext context = new BasicHttpContext();

        HttpClientConnection conn = getConnection(this.connManager, route);
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, target);

        final HttpProcessor httpProcessor = new ImmutableHttpProcessor(
                new HttpRequestInterceptor[] { new RequestContent(), new RequestConnControl() });

        final HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        HttpResponse response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        byte[] data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of first response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        // check that there is no auto-release by default
        try {
            // this should fail quickly, connection has not been released
            getConnection(this.connManager, route, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final ConnectionPoolTimeoutException e) {
            // expected
        }

        conn.close();
        this.connManager.releaseConnection(conn, null, -1, null);
        conn = getConnection(this.connManager, route);
        Assert.assertFalse("connection should have been closed", conn.isOpen());

        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        // repeat the communication, no need to prepare the request again
        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in second response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of second response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        // release connection after marking it for re-use
        // expect the next connection obtained to be open
        this.connManager.releaseConnection(conn, null, -1, null);
        conn = getConnection(this.connManager, route);
        Assert.assertTrue("connection should have been open", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of third response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        this.connManager.releaseConnection(conn, null, -1, null);
        this.connManager.shutdown();
    }

    /**
     * Tests releasing with time limits.
     */
    @Test
    public void testReleaseConnectionWithTimeLimits() throws Exception {

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        final HttpRequest request = new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);
        final HttpContext context = new BasicHttpContext();

        HttpClientConnection conn = getConnection(this.connManager, route);
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, target);

        final HttpProcessor httpProcessor = new ImmutableHttpProcessor(
                new HttpRequestInterceptor[] { new RequestContent(), new RequestConnControl() });

        final HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        HttpResponse response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        byte[] data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of first response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        // check that there is no auto-release by default
        try {
            // this should fail quickly, connection has not been released
            getConnection(this.connManager, route, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final ConnectionPoolTimeoutException e) {
            // expected
        }

        conn.close();
        this.connManager.releaseConnection(conn, null, 100, TimeUnit.MILLISECONDS);
        conn = getConnection(this.connManager, route);
        Assert.assertFalse("connection should have been closed", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in second response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of second response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        this.connManager.releaseConnection(conn, null, 100, TimeUnit.MILLISECONDS);
        conn = getConnection(this.connManager, route);
        Assert.assertTrue("connection should have been open", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of third response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        this.connManager.releaseConnection(conn, null, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(150);
        conn = getConnection(this.connManager, route);
        Assert.assertTrue("connection should have been closed", !conn.isOpen());

        // repeat the communication, no need to prepare the request again
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of fourth response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        this.connManager.shutdown();
    }

    @Test
    public void testCloseExpiredIdleConnections() throws Exception {

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final HttpClientConnection conn = getConnection(this.connManager, route);
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getLeased());
        Assert.assertEquals(1, this.connManager.getStats(route).getLeased());

        this.connManager.releaseConnection(conn, null, 100, TimeUnit.MILLISECONDS);

        // Released, still active.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        this.connManager.closeExpiredConnections();

        // Time has not expired yet.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        Thread.sleep(150);

        this.connManager.closeExpiredConnections();

        // Time expired now, connections are destroyed.
        Assert.assertEquals(Collections.emptySet(), this.connManager.getRoutes());
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(0, this.connManager.getStats(route).getAvailable());

        this.connManager.shutdown();
    }

    @Test
    public void testCloseExpiredTTLConnections() throws Exception {

        this.connManager = new PoolingHttpClientConnectionManager(
                100, TimeUnit.MILLISECONDS);
        this.clientBuilder.setConnectionManager(this.connManager);

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final HttpClientConnection conn = getConnection(this.connManager, route);
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getLeased());
        Assert.assertEquals(1, this.connManager.getStats(route).getLeased());
        // Release, let remain idle for forever
        this.connManager.releaseConnection(conn, null, -1, TimeUnit.MILLISECONDS);

        // Released, still active.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        this.connManager.closeExpiredConnections();

        // Time has not expired yet.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        Thread.sleep(150);

        this.connManager.closeExpiredConnections();

        // TTL expired now, connections are destroyed.
        Assert.assertEquals(Collections.emptySet(), this.connManager.getRoutes());
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(0, this.connManager.getStats(route).getAvailable());

        this.connManager.shutdown();
    }

    /**
     * Tests releasing connection from #abort method called from the
     * main execution thread while there is no blocking I/O operation.
     */
    @Test
    public void testReleaseConnectionOnAbort() throws Exception {

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;
        final HttpContext context = new BasicHttpContext();

        final HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        HttpClientConnection conn = getConnection(this.connManager, route);
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, target);

        final HttpProcessor httpProcessor = new ImmutableHttpProcessor(
                new HttpRequestInterceptor[] { new RequestContent(), new RequestConnControl() });

        final HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        final HttpResponse response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());

        // check that there are no connections available
        try {
            // this should fail quickly, connection has not been released
            getConnection(this.connManager, route, 100L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final ConnectionPoolTimeoutException e) {
            // expected
        }

        // abort the connection
        Assert.assertTrue(conn instanceof HttpClientConnection);
        conn.shutdown();
        this.connManager.releaseConnection(conn, null, -1, null);

        // the connection is expected to be released back to the manager
        conn = getConnection(this.connManager, route, 5L, TimeUnit.SECONDS);
        Assert.assertFalse("connection should have been closed", conn.isOpen());

        this.connManager.releaseConnection(conn, null, -1, null);
        this.connManager.shutdown();
    }

    @Test
    public void testAbortDuringConnecting() throws Exception {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final StallingSocketFactory stallingSocketFactory = new StallingSocketFactory(
                connectLatch, WaitPolicy.BEFORE_CONNECT, PlainConnectionSocketFactory.getSocketFactory());
        final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", stallingSocketFactory)
            .build();

        this.connManager = new PoolingHttpClientConnectionManager(registry);
        this.clientBuilder.setConnectionManager(this.connManager);

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final HttpClientConnection conn = getConnection(this.connManager, route);

        final AtomicReference<Throwable> throwRef = new AtomicReference<Throwable>();
        final Thread abortingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stallingSocketFactory.waitForState();
                    conn.shutdown();
                    connManager.releaseConnection(conn, null, -1, null);
                    connectLatch.countDown();
                } catch (final Throwable e) {
                    throwRef.set(e);
                }
            }
        });
        abortingThread.start();

        try {
            this.connManager.connect(conn, route, 0, context);
            this.connManager.routeComplete(conn, route, context);
            Assert.fail("expected SocketException");
        } catch(final SocketException expected) {}

        abortingThread.join(5000);
        if(throwRef.get() != null) {
            throw new RuntimeException(throwRef.get());
        }

        Assert.assertFalse(conn.isOpen());

        // the connection is expected to be released back to the manager
        final HttpClientConnection conn2 = getConnection(this.connManager, route, 5L, TimeUnit.SECONDS);
        Assert.assertFalse("connection should have been closed", conn2.isOpen());

        this.connManager.releaseConnection(conn2, null, -1, null);
        this.connManager.shutdown();
    }

    @Test
    public void testAbortBeforeSocketCreate() throws Exception {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final StallingSocketFactory stallingSocketFactory = new StallingSocketFactory(
                connectLatch, WaitPolicy.BEFORE_CREATE, PlainConnectionSocketFactory.getSocketFactory());
        final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", stallingSocketFactory)
            .build();

        this.connManager = new PoolingHttpClientConnectionManager(registry);
        this.clientBuilder.setConnectionManager(this.connManager);

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final HttpClientConnection conn = getConnection(this.connManager, route);

        final AtomicReference<Throwable> throwRef = new AtomicReference<Throwable>();
        final Thread abortingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stallingSocketFactory.waitForState();
                    conn.shutdown();
                    connManager.releaseConnection(conn, null, -1, null);
                    connectLatch.countDown();
                } catch (final Throwable e) {
                    throwRef.set(e);
                }
            }
        });
        abortingThread.start();

        try {
            this.connManager.connect(conn, route, 0, context);
            this.connManager.routeComplete(conn, route, context);
            Assert.fail("IOException expected");
        } catch(final IOException expected) {
        }

        abortingThread.join(5000);
        if(throwRef.get() != null) {
            throw new RuntimeException(throwRef.get());
        }

        Assert.assertFalse(conn.isOpen());

        // the connection is expected to be released back to the manager
        final HttpClientConnection conn2 = getConnection(this.connManager, route, 5L, TimeUnit.SECONDS);
        Assert.assertFalse("connection should have been closed", conn2.isOpen());

        this.connManager.releaseConnection(conn2, null, -1, null);
        this.connManager.shutdown();
    }

    @Test
    public void testAbortAfterSocketConnect() throws Exception {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final StallingSocketFactory stallingSocketFactory = new StallingSocketFactory(
                connectLatch, WaitPolicy.AFTER_CONNECT, PlainConnectionSocketFactory.getSocketFactory());
        final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", stallingSocketFactory)
            .build();

        this.connManager = new PoolingHttpClientConnectionManager(registry);
        this.clientBuilder.setConnectionManager(this.connManager);

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final HttpClientConnection conn = getConnection(this.connManager, route);

        final AtomicReference<Throwable> throwRef = new AtomicReference<Throwable>();
        final Thread abortingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stallingSocketFactory.waitForState();
                    conn.shutdown();
                    connManager.releaseConnection(conn, null, -1, null);
                    connectLatch.countDown();
                } catch (final Throwable e) {
                    throwRef.set(e);
                }
            }
        });
        abortingThread.start();

        try {
            this.connManager.connect(conn, route, 0, context);
            this.connManager.routeComplete(conn, route, context);
            Assert.fail("IOException expected");
        } catch(final IOException expected) {
        }

        abortingThread.join(5000);
        if(throwRef.get() != null) {
            throw new RuntimeException(throwRef.get());
        }

        Assert.assertFalse(conn.isOpen());

        // the connection is expected to be released back to the manager
        final HttpClientConnection conn2 = getConnection(this.connManager, route, 5L, TimeUnit.SECONDS);
        Assert.assertFalse("connection should have been closed", conn2.isOpen());

        this.connManager.releaseConnection(conn2, null, -1, null);
        this.connManager.shutdown();
    }

    static class LatchSupport {

        private final CountDownLatch continueLatch;
        private final CountDownLatch waitLatch = new CountDownLatch(1);
        protected final WaitPolicy waitPolicy;

        LatchSupport(final CountDownLatch continueLatch, final WaitPolicy waitPolicy) {
            this.continueLatch = continueLatch;
            this.waitPolicy = waitPolicy;
        }

        void waitForState() throws InterruptedException {
            if(!waitLatch.await(1, TimeUnit.SECONDS)) {
                throw new RuntimeException("waited too long");
            }
        }

        void latch() {
            waitLatch.countDown();
            try {
                if (!continueLatch.await(60, TimeUnit.SECONDS)) {
                    throw new RuntimeException("waited too long!");
                }
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class StallingSocketFactory extends LatchSupport implements ConnectionSocketFactory {

        private final ConnectionSocketFactory delegate;

        public StallingSocketFactory(
                final CountDownLatch continueLatch,
                final WaitPolicy waitPolicy,
                final ConnectionSocketFactory delegate) {
            super(continueLatch, waitPolicy);
            this.delegate = delegate;
        }

        @Override
        public Socket connectSocket(
                final int connectTimeout,
                final Socket sock,
                final HttpHost host,
                final InetSocketAddress remoteAddress,
                final InetSocketAddress localAddress,
                final HttpContext context) throws IOException, ConnectTimeoutException {
            if(waitPolicy == WaitPolicy.BEFORE_CONNECT) {
                latch();
            }

            final Socket socket = delegate.connectSocket(
                    connectTimeout, sock, host, remoteAddress, localAddress, context);

            if(waitPolicy == WaitPolicy.AFTER_CONNECT) {
                latch();
            }

            return socket;
        }

        @Override
        public Socket createSocket(final HttpContext context) throws IOException {
            if(waitPolicy == WaitPolicy.BEFORE_CREATE) {
                latch();
            }

            return delegate.createSocket(context);
        }

    }

    private enum WaitPolicy { BEFORE_CREATE, BEFORE_CONNECT, AFTER_CONNECT, AFTER_OPEN }

}
