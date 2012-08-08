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
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestBasicConnManager extends LocalServerTestBase {

    @Before 
    public void setup() throws Exception {
        startServer();
    }
    
    /**
     * Tests that SCM can still connect to the same host after
     * a connection was aborted.
     */
    @Test
    public void testOpenAfterAbort() throws Exception {
        BasicClientConnectionManager mgr = new BasicClientConnectionManager();

        HttpHost target = getServerHttp();
        HttpRoute route = new HttpRoute(target, null, false);
        HttpContext context = new BasicHttpContext();
        HttpParams params = new BasicHttpParams();

        ManagedClientConnection conn = mgr.getConnection(route, null);
        conn.abortConnection();

        conn = mgr.getConnection(route, null);
        Assert.assertFalse("connection should have been closed", conn.isOpen());
        conn.open(route, context, params);

        mgr.releaseConnection(conn, -1, null);
        mgr.shutdown();
    }

    /**
     * Tests releasing with time limits.
     */
    @Test
    public void testReleaseConnectionWithTimeLimits() throws Exception {
        BasicClientConnectionManager mgr = new BasicClientConnectionManager();

        HttpHost target = getServerHttp();
        HttpRoute route = new HttpRoute(target, null, false);
        HttpContext context = new BasicHttpContext();
        HttpParams params = new BasicHttpParams();
        int      rsplen = 8;
        String      uri = "/random/" + rsplen;

        HttpRequest request = new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = mgr.getConnection(route, null);
        conn.open(route, context, params);

        // a new context is created for each test case, no need to reset
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, target);

        HttpProcessor httpProcessor = new ImmutableHttpProcessor(
                new HttpRequestInterceptor[] { new RequestContent(), new RequestConnControl() });
        
        HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        HttpResponse response = exec.execute(request, conn, context);

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
        conn.open(route, context, params);
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
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
        conn.markReusable();
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);
        conn =  mgr.getConnection(route, null);
        Assert.assertTrue("connection should have been open", conn.isOpen());

        // repeat the communication, no need to prepare the request again
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        response = exec.execute(request, conn, context);

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
        conn.open(route, context, params);
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        response = exec.execute(request, conn, context);

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
        BasicClientConnectionManager mgr = new BasicClientConnectionManager();

        HttpHost target = getServerHttp();
        HttpRoute route = new HttpRoute(target, null, false);
        HttpContext context = new BasicHttpContext();
        HttpParams params = new BasicHttpParams();

        ManagedClientConnection conn =  mgr.getConnection(route, null);
        conn.open(route, context, params);
        conn.markReusable();
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
        BasicClientConnectionManager mgr = new BasicClientConnectionManager();

        HttpHost target = getServerHttp();
        HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn =  mgr.getConnection(route, null);
        mgr.releaseConnection(conn, 100, TimeUnit.MILLISECONDS);

        mgr.getConnection(route, null);
        mgr.getConnection(route, null);
    }

}
