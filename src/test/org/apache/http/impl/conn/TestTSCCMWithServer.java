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
     * Instantiates default parameters for a connection manager.
     *
     * @return  the default parameters
     */
    public HttpParams createManagerParams() {

        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);

        return params;
    }


    /**
     * Tests releasing and re-using a connection after a response is read.
     */
    // public void testReleaseConnection() throws Exception {
    public void testSkeleton() throws Exception {
        //@@@ this testcase is not yet complete

        HttpParams mgrpar = createManagerParams();
        HttpConnectionManagerParams.setDefaultMaxConnectionsPerHost(mgrpar, 1);
        HttpConnectionManagerParams.setMaxTotalConnections(mgrpar, 3);

        ThreadSafeClientConnManager mgr = createTSCCM(mgrpar, null);

        HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);
        final String    uri   = "/random/8"; // read 8 bytes

        HttpRequest request =
            new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);

        ManagedClientConnection conn = mgr.getConnection(route);
        conn.open(route, httpContext, defaultParams);

        httpContext.setAttribute(
                HttpExecutionContext.HTTP_CONNECTION, conn);
        httpContext.setAttribute(
                HttpExecutionContext.HTTP_TARGET_HOST, target);
        httpContext.setAttribute(
                HttpExecutionContext.HTTP_REQUEST, request);
        
        httpExecutor.preProcess
            (request, httpProcessor, httpContext);
        HttpResponse response =
            httpExecutor.execute(request, conn, httpContext);
        httpExecutor.postProcess
            (response, httpProcessor, httpContext);

        assertEquals("wrong status in response",
                     HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        byte[] data = EntityUtils.toByteArray(response.getEntity());
        // ignore data, but it must be read

        mgr.releaseConnection(conn);

        //@@@ to be checked:
        // - connection is NOT re-used if not marked before release
        // - connection is re-used if marked before release
        // - re-using the connections works
    }


    // List of server-based tests in 3.x TestHttpConnectionManager
    // The execution framework (HttpClient) used by some of them
    // can probably be replaced by hand-coded request execution
    //
    // testConnectMethodFailureRelease
    // testDroppedThread
    // testWriteRequestReleaseConnection, depends on execution framework
    // testReleaseConnection, depends on execution framework
    // testResponseAutoRelease
    // testMaxConnectionsPerServer - what's the server used/needed for?
    // testReclaimUnusedConnection, depends on execution framework
    // testGetFromMultipleThreads, depends on execution framework

} // class TestTSCCMWithServer
