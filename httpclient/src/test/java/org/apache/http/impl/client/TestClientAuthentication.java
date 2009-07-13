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
 */

package org.apache.http.impl.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.NonRepeatableRequestException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

/**
 * Unit tests for automatic client authentication.
 */
public class TestClientAuthentication extends BasicServerTestBase {

    public TestClientAuthentication(final String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestClientAuthentication.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestClientAuthentication.class);
    }

    @Override
    protected void setUp() throws Exception {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());
        httpproc.addInterceptor(new RequestBasicAuth());
        httpproc.addInterceptor(new ResponseBasicUnauthorized());
        
        localServer = new LocalTestServer(httpproc, null);
    }
    
    static class AuthHandler implements HttpRequestHandler {

        public void handle(
                final HttpRequest request, 
                final HttpResponse response, 
                final HttpContext context) throws HttpException, IOException {
            String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("success", HTTP.ASCII);
                response.setEntity(entity);
            }
        }
        
    }
    
    static class TestCredentialsProvider implements CredentialsProvider {

        private final Credentials creds;
        private AuthScope authscope;
        
        TestCredentialsProvider(final Credentials creds) {
            super();
            this.creds = creds;
        }
        
        public void clear() {
        }

        public Credentials getCredentials(AuthScope authscope) {
            this.authscope = authscope;
            return this.creds;
        }

        public void setCredentials(AuthScope authscope, Credentials credentials) {
        }
        
        public AuthScope getAuthScope() {
            return this.authscope;
        }
        
    }
    
    public void testBasicAuthenticationNoCreds() throws Exception {
        localServer.register("*", new AuthHandler());
        localServer.start();
        
        TestCredentialsProvider credsProvider = new TestCredentialsProvider(null);
        
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.setCredentialsProvider(credsProvider);
        
        HttpGet httpget = new HttpGet("/");
        
        HttpResponse response = httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        assertNotNull(entity);
        entity.consumeContent();
        AuthScope authscope = credsProvider.getAuthScope();
        assertNotNull(authscope);
        assertEquals("test realm", authscope.getRealm());
    }
    
    public void testBasicAuthenticationFailure() throws Exception {
        localServer.register("*", new AuthHandler());
        localServer.start();
        
        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "all-wrong"));
        
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.setCredentialsProvider(credsProvider);
        
        HttpGet httpget = new HttpGet("/");
        
        HttpResponse response = httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        assertNotNull(entity);
        entity.consumeContent();
        AuthScope authscope = credsProvider.getAuthScope();
        assertNotNull(authscope);
        assertEquals("test realm", authscope.getRealm());
    }
    
    public void testBasicAuthenticationSuccess() throws Exception {
        localServer.register("*", new AuthHandler());
        localServer.start();
        
        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));
        
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.setCredentialsProvider(credsProvider);
        
        HttpGet httpget = new HttpGet("/");
        
        HttpResponse response = httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertNotNull(entity);
        entity.consumeContent();
        AuthScope authscope = credsProvider.getAuthScope();
        assertNotNull(authscope);
        assertEquals("test realm", authscope.getRealm());
    }

    public void testBasicAuthenticationSuccessOnRepeatablePost() throws Exception {
        localServer.register("*", new AuthHandler());
        localServer.start();
        
        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));
        
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.setCredentialsProvider(credsProvider);
        
        HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new StringEntity("some important stuff", HTTP.ISO_8859_1));
        
        HttpResponse response = httpclient.execute(getServerHttp(), httppost);
        HttpEntity entity = response.getEntity();
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertNotNull(entity);
        entity.consumeContent();
        AuthScope authscope = credsProvider.getAuthScope();
        assertNotNull(authscope);
        assertEquals("test realm", authscope.getRealm());
    }

    public void testBasicAuthenticationFailureOnNonRepeatablePost() throws Exception {
        localServer.register("*", new AuthHandler());
        localServer.start();
        
        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));
        
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.setCredentialsProvider(credsProvider);
        
        HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 0,1,2,3,4,5,6,7,8,9 }), -1));
        
        try {
            httpclient.execute(getServerHttp(), httppost);
            fail("ClientProtocolException should have been thrown");
        } catch (ClientProtocolException ex) {
            Throwable cause = ex.getCause();
            assertNotNull(cause);
            assertTrue(cause instanceof NonRepeatableRequestException);
        }
    }

}
