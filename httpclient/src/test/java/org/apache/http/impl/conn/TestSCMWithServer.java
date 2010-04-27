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

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestSCMWithServer extends ServerTestBase {

    /**
     * Helper to instantiate a <code>SingleClientConnManager</code>.
     *
     * @param schreg    the scheme registry, or
     *                  <code>null</code> to use defaults
     *
     * @return  a connection manager to test
     */
    public SingleClientConnManager createSCCM(SchemeRegistry schreg) {
        if (schreg == null)
            schreg = supportedSchemes;
        return new SingleClientConnManager(schreg);
    }

    /**
     * Tests that SCM can still connect to the same host after
     * a connection was aborted.
     */
    @Test
    public void testOpenAfterAbort() throws Exception {
        SingleClientConnManager mgr = createSCCM(null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn = mgr.getConnection(route, null);
        Assert.assertTrue(conn instanceof AbstractClientConnAdapter);
        ((AbstractClientConnAdapter) conn).abortConnection();

        conn = mgr.getConnection(route, null);
        Assert.assertFalse("connection should have been closed", conn.isOpen());
        conn.open(route, httpContext, defaultParams);

        mgr.releaseConnection(conn, -1, null);
        mgr.shutdown();
    }

    /**
     * Tests releasing with time limits.
     */
    @Test
    public void testReleaseConnectionWithTimeLimits() throws Exception {

        SingleClientConnManager mgr = createSCCM(null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = mgr.getConnection(route, null);
        conn.open(route, httpContext, defaultParams);

        // a new context is created for each testcase, no need to reset
        HttpResponse response = Helper.execute(
                request, conn, target,
                httpExecutor, httpProcessor, defaultParams, httpContext);

        Assert.assertEquals("wrong status in first response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        byte[] data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of first response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        // release connection without marking for re-use
        // expect the next connection obtained to be closed
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);
        conn = mgr.getConnection(route, null);
        Assert.assertFalse("connection should have been closed", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        conn.open(route, httpContext, defaultParams);
        httpContext.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        response = httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        Assert.assertEquals("wrong status in second response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of second response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        // release connection after marking it for re-use
        // expect the next connection obtained to be open
        conn.markReusable();
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);
        conn =  mgr.getConnection(route, null);
        Assert.assertTrue("connection should have been open", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        httpContext.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        response = httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        Assert.assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of third response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        conn.markReusable();
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(150);
        conn =  mgr.getConnection(route, null);
        Assert.assertTrue("connection should have been closed", !conn.isOpen());

        // repeat the communication, no need to prepare the request again
        conn.open(route, httpContext, defaultParams);
        httpContext.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        response = httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        Assert.assertEquals("wrong status in third response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        data = EntityUtils.toByteArray(response.getEntity());
        Assert.assertEquals("wrong length of fourth response entity",
                     rsplen, data.length);
        // ignore data, but it must be read

        mgr.shutdown();
    }

    @Test
    public void testCloseExpiredConnections() throws Exception {

        SingleClientConnManager mgr = createSCCM(null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn =  mgr.getConnection(route, null);
        conn.open(route, httpContext, defaultParams);
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);

        mgr.closeExpiredConnections();

        conn = mgr.getConnection(route, null);
        Assert.assertTrue(conn.isOpen());
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);

        Thread.sleep(150);
        mgr.closeExpiredConnections();
        conn = mgr.getConnection(route, null);
        Assert.assertFalse(conn.isOpen());

        mgr.shutdown();
    }

    @Test(expected=IllegalStateException.class)
    public void testAlreadyLeased() throws Exception {

        SingleClientConnManager mgr = createSCCM(null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn =  mgr.getConnection(route, null);
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);

        mgr.getConnection(route, null);
        mgr.getConnection(route, null);
    }

}
