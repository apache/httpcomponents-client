/*
 * $HeadURL:$
 * $Revision:$
 * $Date:$
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.http.impl.client;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.PlainSocketFactory;
import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.ClientConnAdapterMockup;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.mockup.SocketFactoryMockup;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/**
 * Unit tests for {@link DefaultClientRequestDirector}
 */
public class TestDefaultClientRequestDirector extends ServerTestBase {

    public TestDefaultClientRequestDirector(final String testName) throws IOException {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestDefaultClientRequestDirector.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestDefaultClientRequestDirector.class);
    }
    
    /**
     * Tests that if a socket fails to connect, the allocated connection is
     * properly released back to the connection manager.
     */
    public void testSocketConnectFailureReleasesConnection() throws Exception {
        final ConnMan2 conMan = new ConnMan2();
        final DefaultHttpClient client = new DefaultHttpClient(conMan, new BasicHttpParams()); 
        final HttpContext context = client.getDefaultContext();
        final HttpGet httpget = new HttpGet("http://www.example.com/a");
        
        try {
            client.execute(httpget, context);
            fail("expected IOException");
        } catch(IOException expected) {}
        
        assertNotNull(conMan.allocatedConnection);
        assertSame(conMan.allocatedConnection, conMan.releasedConnection);
    }
    
    public void testRequestFailureReleasesConnection() throws Exception {
        this.localServer.register("*", new ThrowingService());

        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        
        ConnMan3 conMan = new ConnMan3(new BasicHttpParams(), registry);
        DefaultHttpClient client = new DefaultHttpClient(conMan, new BasicHttpParams());
        HttpGet httpget = new HttpGet("/a");

        try {
            client.execute(getServerHttp(), httpget);
            fail("expected IOException");
        } catch (IOException expected) {}

        assertNotNull(conMan.allocatedConnection);
        assertSame(conMan.allocatedConnection, conMan.releasedConnection);
    }
    
    private static class ThrowingService implements HttpRequestHandler {
        public void handle(
                final HttpRequest request, 
                final HttpResponse response, 
                final HttpContext context) throws HttpException, IOException {
            throw new IOException();
        }
    }
    
    private static class ConnMan3 extends SingleClientConnManager {
        private ManagedClientConnection allocatedConnection;
        private ManagedClientConnection releasedConnection;
        
        public ConnMan3(HttpParams params, SchemeRegistry schreg) {
            super(params, schreg);
        }
        
        @Override
        public ManagedClientConnection getConnection(HttpRoute route) {
            allocatedConnection = super.getConnection(route);
            return allocatedConnection;
        }
        
        @Override
        public void releaseConnection(ManagedClientConnection conn) {
            releasedConnection = conn;
            super.releaseConnection(conn);
        }
        
        
    }
    
    private static class ConnMan2 implements ClientConnectionManager {
        private ManagedClientConnection allocatedConnection;
        private ManagedClientConnection releasedConnection;
        
        public ConnMan2() {
        }

        public void closeIdleConnections(long idletime, TimeUnit tunit) {
            throw new UnsupportedOperationException("just a mockup");
        }

        public ManagedClientConnection getConnection(HttpRoute route)
                throws InterruptedException {
            throw new UnsupportedOperationException("just a mockup");
        }

        public ManagedClientConnection getConnection(HttpRoute route,
                long timeout, TimeUnit tunit)
                throws ConnectionPoolTimeoutException, InterruptedException {
            allocatedConnection = new ClientConnAdapterMockup() {
                @Override
                public void open(HttpRoute route, HttpContext context,
                        HttpParams params) throws IOException {
                    throw new ConnectException();
                }
            };
            return allocatedConnection;
        }

        public HttpParams getParams() {
            throw new UnsupportedOperationException("just a mockup");
        }

        public SchemeRegistry getSchemeRegistry() {
            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", new SocketFactoryMockup(null), 80));
            return registry;
        }

        public void releaseConnection(ManagedClientConnection conn) {
            this.releasedConnection = conn;
        }

        public void shutdown() {
            throw new UnsupportedOperationException("just a mockup");
        }
    }
    
}
