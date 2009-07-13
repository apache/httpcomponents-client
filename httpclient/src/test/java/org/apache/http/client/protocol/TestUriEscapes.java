/*
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
 *
 */

package org.apache.http.client.protocol;

import java.io.IOException;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

public class TestUriEscapes extends BasicServerTestBase {

    public TestUriEscapes(final String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestUriEscapes.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestUriEscapes.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        localServer = new LocalTestServer(null, null);
        localServer.registerDefaultHandlers();
        localServer.start();
    }
    
    private class UriListeningService implements HttpRequestHandler {
        
        private volatile String requestedUri;

        public void handle(
                final HttpRequest request, 
                final HttpResponse response, 
                final HttpContext context) throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            this.requestedUri = request.getRequestLine().getUri();
            response.setStatusLine(ver, HttpStatus.SC_OK);
            StringEntity entity = new StringEntity("Response Body");
            response.setEntity(entity);
        }
        
        public String getRequestedUri() {
            return requestedUri;
        }
    }
    
    private void doTest(String uri, boolean relative) throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        UriListeningService listener = new UriListeningService();
        this.localServer.register("*", listener);
        
        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpResponse response;

        if(!relative) {
            String request = "http://" + host + ":" + port + uri;
            HttpGet httpget = new HttpGet(request); 
            response = client.execute(httpget);
            response.getEntity().consumeContent();  
        } else {
            HttpHost target = new HttpHost(host, port);
            HttpGet httpget = new HttpGet(uri); 
            response = client.execute(target, httpget);
            response.getEntity().consumeContent();  
        }
        
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());       
        assertEquals(uri, listener.getRequestedUri());
    }
    
    public void testEscapedAmpersandInQueryAbsolute() throws Exception {
        doTest("/path/a=b&c=%26d", false);
    }
    
    public void testEscapedAmpersandInQueryRelative() throws Exception {
        doTest("/path/a=b&c=%26d", true);
    }
    
    public void testPlusInPathAbsolute() throws Exception {
        doTest("/path+go", false);
    }
    
    public void testPlusInPathRelative() throws Exception {
        doTest("/path+go", true);
    }
    
    public void testEscapedSpaceInPathAbsolute() throws Exception {
        doTest("/path%20go?a=b&c=d", false);
    }
    
    public void testEscapedSpaceInPathRelative() throws Exception {
        doTest("/path%20go?a=b&c=d", true);
    }
    
    public void testEscapedAmpersandInPathAbsolute() throws Exception {
        doTest("/this%26that?a=b&c=d", false);
    }
    
    public void testEscapedAmpersandInPathRelative() throws Exception {
        doTest("/this%26that?a=b&c=d", true);
    }
    
    public void testEscapedSpaceInQueryAbsolute() throws Exception {
        doTest("/path?a=b&c=d%20e", false);
    }
    
    public void testEscapedSpaceInQueryRelative() throws Exception {
        doTest("/path?a=b&c=d%20e", true);
    }
    
    public void testPlusInQueryAbsolute() throws Exception {
        doTest("/path?a=b&c=d+e", false);
    }
    
    public void testPlusInQueryRelative() throws Exception {
        doTest("/path?a=b&c=d+e", true);
    }
}
