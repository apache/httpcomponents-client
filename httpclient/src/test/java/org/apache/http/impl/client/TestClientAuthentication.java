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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.NonRepeatableRequestException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.localserver.BasicAuthTokenExtractor;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for automatic client authentication.
 */
public class TestClientAuthentication extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());
        httpproc.addInterceptor(new RequestBasicAuth());
        httpproc.addInterceptor(new ResponseBasicUnauthorized());

        this.localServer = new LocalTestServer(httpproc, null);
        this.httpclient = new DefaultHttpClient();
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
                StringEntity entity = new StringEntity("success", Consts.ASCII);
                response.setEntity(entity);
            }
        }

    }

    static class AuthExpectationVerifier implements HttpExpectationVerifier {

        private final BasicAuthTokenExtractor authTokenExtractor;

        public AuthExpectationVerifier() {
            super();
            this.authTokenExtractor = new BasicAuthTokenExtractor();
        }

        public void verify(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException {
            String creds = this.authTokenExtractor.extract(request);
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setStatusCode(HttpStatus.SC_CONTINUE);
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

    @Test
    public void testBasicAuthenticationNoCreds() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        TestCredentialsProvider credsProvider = new TestCredentialsProvider(null);


        this.httpclient.setCredentialsProvider(credsProvider);

        HttpGet httpget = new HttpGet("/");

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationFailure() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "all-wrong"));


        this.httpclient.setCredentialsProvider(credsProvider);

        HttpGet httpget = new HttpGet("/");

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationSuccess() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));


        this.httpclient.setCredentialsProvider(credsProvider);

        HttpGet httpget = new HttpGet("/");

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationSuccessOnNonRepeatablePutExpectContinue() throws Exception {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());
        httpproc.addInterceptor(new RequestBasicAuth());
        httpproc.addInterceptor(new ResponseBasicUnauthorized());
        this.localServer = new LocalTestServer(
                httpproc, null, null, new AuthExpectationVerifier(), null, null);
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));


        this.httpclient.setCredentialsProvider(credsProvider);

        HttpPut httpput = new HttpPut("/");
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));
        httpput.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpput);
        HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
    }

    @Test(expected=ClientProtocolException.class)
    public void testBasicAuthenticationFailureOnNonRepeatablePutDontExpectContinue() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));


        this.httpclient.setCredentialsProvider(credsProvider);

        HttpPut httpput = new HttpPut("/");
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));
        httpput.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);

        try {
            this.httpclient.execute(getServerHttp(), httpput);
            Assert.fail("ClientProtocolException should have been thrown");
        } catch (ClientProtocolException ex) {
            Throwable cause = ex.getCause();
            Assert.assertNotNull(cause);
            Assert.assertTrue(cause instanceof NonRepeatableRequestException);
            throw ex;
        }
    }

    @Test
    public void testBasicAuthenticationSuccessOnRepeatablePost() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));


        this.httpclient.setCredentialsProvider(credsProvider);

        HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new StringEntity("some important stuff", Consts.ASCII));

        HttpResponse response = this.httpclient.execute(getServerHttp(), httppost);
        HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test(expected=ClientProtocolException.class)
    public void testBasicAuthenticationFailureOnNonRepeatablePost() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));


        this.httpclient.setCredentialsProvider(credsProvider);

        HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 0,1,2,3,4,5,6,7,8,9 }), -1));

        try {
            this.httpclient.execute(getServerHttp(), httppost);
            Assert.fail("ClientProtocolException should have been thrown");
        } catch (ClientProtocolException ex) {
            Throwable cause = ex.getCause();
            Assert.assertNotNull(cause);
            Assert.assertTrue(cause instanceof NonRepeatableRequestException);
            throw ex;
        }
    }

    static class TestTargetAuthenticationStrategy extends TargetAuthenticationStrategy {

        private AtomicLong count;

        public TestTargetAuthenticationStrategy() {
            super();
            this.count = new AtomicLong();
        }

        @Override
        public boolean isAuthenticationRequested(
                final HttpHost host,
                final HttpResponse response,
                final HttpContext context) {
            boolean res = super.isAuthenticationRequested(host, response, context);
            if (res == true) {
                this.count.incrementAndGet();
            }
            return res;
        }

        public long getCount() {
            return this.count.get();
        }

    }

    @Test
    public void testBasicAuthenticationCredentialsCaching() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test"));

        TestTargetAuthenticationStrategy authStrategy = new TestTargetAuthenticationStrategy();

        this.httpclient.setCredentialsProvider(credsProvider);
        this.httpclient.setTargetAuthenticationStrategy(authStrategy);

        HttpContext context = new BasicHttpContext();

        HttpHost targethost = getServerHttp();
        HttpGet httpget = new HttpGet("/");

        HttpResponse response1 = this.httpclient.execute(targethost, httpget, context);
        HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        HttpResponse response2 = this.httpclient.execute(targethost, httpget, context);
        HttpEntity entity2 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity2);
        EntityUtils.consume(entity2);

        Assert.assertEquals(1, authStrategy.getCount());
    }

    @Test
    public void testAuthenticationUserinfoInRequestSuccess() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        HttpHost target = getServerHttp();
        HttpGet httpget = new HttpGet("http://test:test@" +  target.toHostString() + "/");

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    @Test
    public void testAuthenticationUserinfoInRequestFailure() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        HttpHost target = getServerHttp();
        HttpGet httpget = new HttpGet("http://test:all-wrong@" +  target.toHostString() + "/");

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    static class CountingAuthHandler implements HttpRequestHandler {

        private AtomicLong count;

        public CountingAuthHandler() {
            super();
            this.count = new AtomicLong();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            this.count.incrementAndGet();
            String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("success", Consts.ASCII);
                response.setEntity(entity);
            }
        }

        public long getCount() {
            return this.count.get();
        }

    }

    @Test
    public void testPreemptiveAuthentication() throws Exception {
        CountingAuthHandler requestHandler = new CountingAuthHandler();
        this.localServer.register("*", requestHandler);
        this.localServer.start();

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test"));

        this.httpclient.setCredentialsProvider(credsProvider);

        HttpHost targethost = getServerHttp();

        HttpContext context = new BasicHttpContext();
        AuthCache authCache = new BasicAuthCache();
        authCache.put(targethost, new BasicScheme());
        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        HttpGet httpget = new HttpGet("/");

        HttpResponse response1 = this.httpclient.execute(targethost, httpget, context);
        HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        Assert.assertEquals(1, requestHandler.getCount());
    }

    @Test
    public void testPreemptiveAuthenticationFailure() throws Exception {
        CountingAuthHandler requestHandler = new CountingAuthHandler();
        this.localServer.register("*", requestHandler);
        this.localServer.start();

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "stuff"));

        this.httpclient.setCredentialsProvider(credsProvider);

        HttpHost targethost = getServerHttp();

        HttpContext context = new BasicHttpContext();
        AuthCache authCache = new BasicAuthCache();
        authCache.put(targethost, new BasicScheme());
        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        HttpGet httpget = new HttpGet("/");

        HttpResponse response1 = this.httpclient.execute(targethost, httpget, context);
        HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        Assert.assertEquals(1, requestHandler.getCount());
    }

    static class ProxyAuthHandler implements HttpRequestHandler {

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED);
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("success", Consts.ASCII);
                response.setEntity(entity);
            }
        }

    }

    @Test
    public void testAuthenticationTargetAsProxy() throws Exception {
        this.localServer.register("*", new ProxyAuthHandler());
        this.localServer.start();

        TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));


        this.httpclient.setCredentialsProvider(credsProvider);

        HttpGet httpget = new HttpGet("/");

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
                response.getStatusLine().getStatusCode());
        EntityUtils.consume(entity);
    }

    static class ClosingAuthHandler implements HttpRequestHandler {

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("success", Consts.ASCII);
                response.setEntity(entity);
                response.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
            }
        }

    }

    @Test
    public void testConnectionCloseAfterAuthenticationSuccess() throws Exception {
        this.localServer.register("*", new ClosingAuthHandler());
        this.localServer.start();

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test"));

        TestTargetAuthenticationStrategy authStrategy = new TestTargetAuthenticationStrategy();

        this.httpclient.setCredentialsProvider(credsProvider);
        this.httpclient.setTargetAuthenticationStrategy(authStrategy);

        HttpContext context = new BasicHttpContext();

        HttpHost targethost = getServerHttp();

        for (int i = 0; i < 2; i++) {
            HttpGet httpget = new HttpGet("/");

            HttpResponse response = this.httpclient.execute(targethost, httpget, context);
            EntityUtils.consume(response.getEntity());
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

}
