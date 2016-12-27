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

package org.apache.hc.client5.http.impl.integration;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.ConnectionPoolTimeoutException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.ConnectionRequest;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
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

        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", uri);
        final HttpContext context = new BasicHttpContext();

        HttpClientConnection conn = getConnection(this.connManager, route);
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);

        final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                new RequestTargetHost(), new RequestContent(), new RequestConnControl());

        final HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        ClassicHttpResponse response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getCode());
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
                     response.getCode());
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
                     response.getCode());
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

        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", uri);
        final HttpContext context = new BasicHttpContext();

        HttpClientConnection conn = getConnection(this.connManager, route);
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);

        final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                new RequestTargetHost(), new RequestContent(), new RequestConnControl());

        final HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        ClassicHttpResponse response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getCode());
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
                     response.getCode());
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
                     response.getCode());
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
                     response.getCode());
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

        this.connManager.closeExpired();

        // Time has not expired yet.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        Thread.sleep(150);

        this.connManager.closeExpired();

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

        this.connManager.closeExpired();

        // Time has not expired yet.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        Thread.sleep(150);

        this.connManager.closeExpired();

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

        final ClassicHttpRequest request =
            new BasicClassicHttpRequest("GET", uri);

        HttpClientConnection conn = getConnection(this.connManager, route);
        this.connManager.connect(conn, route, 0, context);
        this.connManager.routeComplete(conn, route, context);

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);

        final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                new RequestTargetHost(), new RequestContent(), new RequestConnControl());

        final HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        final ClassicHttpResponse response = exec.execute(request, conn, context);

        Assert.assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getCode());

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

}
