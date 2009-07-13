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

package org.apache.http.conn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

public class TestConnectionAutoRelease extends ServerTestBase {

    public TestConnectionAutoRelease(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestConnectionAutoRelease.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestConnectionAutoRelease.class);
    }

    public ThreadSafeClientConnManager createTSCCM(HttpParams params,
                                                   SchemeRegistry schreg) {
        if (params == null)
            params = defaultParams;
        if (schreg == null)
            schreg = supportedSchemes;
        return new ThreadSafeClientConnManager(params, schreg);
    }

    public void testReleaseOnEntityConsumeContent() throws Exception {
        HttpParams params = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections
            (params, 1);
        ConnManagerParams.setMaxConnectionsPerRoute
            (params, new ConnPerRouteBean(1));
        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        // Zero connections in the pool
        assertEquals(0, mgr.getConnectionsInPool());
        
        DefaultHttpClient client = new DefaultHttpClient(mgr, params); 

        // Get some random data
        HttpGet httpget = new HttpGet("/random/20000"); 
        HttpHost target = getServerHttp();
        
        HttpResponse response = client.execute(target, httpget);

        ClientConnectionRequest connreq = mgr.requestConnection(new HttpRoute(target), null);
        try {
            connreq.getConnection(250, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException expected) {
        }
        
        HttpEntity e = response.getEntity();
        assertNotNull(e);
        e.consumeContent();
        
        // Expect one connection in the pool
        assertEquals(1, mgr.getConnectionsInPool());

        // Make sure one connection is available
        connreq = mgr.requestConnection(new HttpRoute(target), null);
        ManagedClientConnection conn = connreq.getConnection(250, TimeUnit.MILLISECONDS);
        
        mgr.releaseConnection(conn, -1, null);
        
        mgr.shutdown();
    }
    
    public void testReleaseOnEntityWriteTo() throws Exception {
        HttpParams params = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections
            (params, 1);
        ConnManagerParams.setMaxConnectionsPerRoute
            (params, new ConnPerRouteBean(1));
        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        // Zero connections in the pool
        assertEquals(0, mgr.getConnectionsInPool());
        
        DefaultHttpClient client = new DefaultHttpClient(mgr, params); 

        // Get some random data
        HttpGet httpget = new HttpGet("/random/20000"); 
        HttpHost target = getServerHttp();
        
        HttpResponse response = client.execute(target, httpget);

        ClientConnectionRequest connreq = mgr.requestConnection(new HttpRoute(target), null);
        try {
            connreq.getConnection(250, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException expected) {
        }
        
        HttpEntity e = response.getEntity();
        assertNotNull(e);
        ByteArrayOutputStream outsteam = new ByteArrayOutputStream(); 
        e.writeTo(outsteam);
        
        // Expect one connection in the pool
        assertEquals(1, mgr.getConnectionsInPool());

        // Make sure one connection is available
        connreq = mgr.requestConnection(new HttpRoute(target), null);
        ManagedClientConnection conn = connreq.getConnection(250, TimeUnit.MILLISECONDS);
        
        mgr.releaseConnection(conn, -1, null);
        
        mgr.shutdown();
    }
    
    public void testReleaseOnAbort() throws Exception {
        HttpParams params = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections
            (params, 1);
        ConnManagerParams.setMaxConnectionsPerRoute
            (params, new ConnPerRouteBean(1));
        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        // Zero connections in the pool
        assertEquals(0, mgr.getConnectionsInPool());
        
        DefaultHttpClient client = new DefaultHttpClient(mgr, params); 

        // Get some random data
        HttpGet httpget = new HttpGet("/random/20000"); 
        HttpHost target = getServerHttp();
        
        HttpResponse response = client.execute(target, httpget);

        ClientConnectionRequest connreq = mgr.requestConnection(new HttpRoute(target), null);
        try {
            connreq.getConnection(250, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException expected) {
        }
        
        HttpEntity e = response.getEntity();
        assertNotNull(e);
        httpget.abort();
        
        // Expect zero connections in the pool
        assertEquals(0, mgr.getConnectionsInPool());

        // Make sure one connection is available
        connreq = mgr.requestConnection(new HttpRoute(target), null);
        ManagedClientConnection conn = connreq.getConnection(250, TimeUnit.MILLISECONDS);
        
        mgr.releaseConnection(conn, -1, null);
        
        mgr.shutdown();
    }

    public void testReleaseOnIOException() throws Exception {
        
        localServer.register("/dropdead", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                BasicHttpEntity entity = new BasicHttpEntity() {

                    @Override
                    public void writeTo(
                            final OutputStream outstream) throws IOException {
                        byte[] tmp = new byte[5];
                        outstream.write(tmp);
                        outstream.flush();
                        
                        // do something comletely ugly in order to trigger 
                        // MalformedChunkCodingException
                        DefaultHttpServerConnection conn = (DefaultHttpServerConnection) 
                            context.getAttribute(ExecutionContext.HTTP_CONNECTION);
                        try {
                            conn.sendResponseHeader(response);
                        } catch (HttpException ignore) {
                        }
                    }
                    
                } ;
                entity.setChunked(true);
                response.setEntity(entity);
            }
            
        });
        
        HttpParams params = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections
            (params, 1);
        ConnManagerParams.setMaxConnectionsPerRoute
            (params, new ConnPerRouteBean(1));
        ThreadSafeClientConnManager mgr = createTSCCM(params, null);

        // Zero connections in the pool
        assertEquals(0, mgr.getConnectionsInPool());
        
        DefaultHttpClient client = new DefaultHttpClient(mgr, params); 

        // Get some random data
        HttpGet httpget = new HttpGet("/dropdead"); 
        HttpHost target = getServerHttp();
        
        HttpResponse response = client.execute(target, httpget);

        ClientConnectionRequest connreq = mgr.requestConnection(new HttpRoute(target), null);
        try {
            connreq.getConnection(250, TimeUnit.MILLISECONDS);
            fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException expected) {
        }
        
        HttpEntity e = response.getEntity();
        assertNotNull(e);
        // Read the content
        try {
            EntityUtils.toByteArray(e);
            fail("MalformedChunkCodingException should have been thrown");
        } catch (MalformedChunkCodingException expected) {
            
        }
        
        // Expect zero connections in the pool
        assertEquals(0, mgr.getConnectionsInPool());

        // Make sure one connection is available
        connreq = mgr.requestConnection(new HttpRoute(target), null);
        ManagedClientConnection conn = connreq.getConnection(250, TimeUnit.MILLISECONDS);
        
        mgr.releaseConnection(conn, -1, null);
        
        mgr.shutdown();
    }
    
}
