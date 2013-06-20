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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpInetConnection;
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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.TargetAuthenticationStrategy;
import org.apache.http.localserver.BasicAuthTokenExtractor;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
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
public class TestClientAuthentication extends IntegrationTestBase {

    @Before
    public void setUp() throws Exception {
        final HttpProcessor httpproc = HttpProcessorBuilder.create()
            .add(new ResponseDate())
            .add(new ResponseServer())
            .add(new ResponseContent())
            .add(new ResponseConnControl())
            .add(new RequestBasicAuth())
            .add(new ResponseBasicUnauthorized()).build();
        this.localServer = new LocalTestServer(httpproc, null);
        startServer();
    }

    static class AuthHandler implements HttpRequestHandler {

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", Consts.ASCII);
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
            final String creds = this.authTokenExtractor.extract(request);
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

        public Credentials getCredentials(final AuthScope authscope) {
            this.authscope = authscope;
            return this.creds;
        }

        public void setCredentials(final AuthScope authscope, final Credentials credentials) {
        }

        public AuthScope getAuthScope() {
            return this.authscope;
        }

    }

    @Test
    public void testBasicAuthenticationNoCreds() throws Exception {
        this.localServer.register("*", new AuthHandler());

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(null);
        this.httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationFailure() throws Exception {
        this.localServer.register("*", new AuthHandler());

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "all-wrong"));

        this.httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationSuccess() throws Exception {
        this.localServer.register("*", new AuthHandler());

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));

        this.httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationSuccessOnNonRepeatablePutExpectContinue() throws Exception {
        final HttpProcessor httpproc = HttpProcessorBuilder.create()
            .add(new ResponseDate())
            .add(new ResponseServer(LocalTestServer.ORIGIN))
            .add(new ResponseContent())
            .add(new ResponseConnControl())
            .add(new RequestBasicAuth())
            .add(new ResponseBasicUnauthorized()).build();
        this.localServer = new LocalTestServer(
                httpproc, null, null, new AuthExpectationVerifier(), null, false);
        this.localServer.register("*", new AuthHandler());
        this.localServer.start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));

        this.httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        final HttpPut httpput = new HttpPut("/");
        httpput.setConfig(config);
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httpput);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
    }

    @Test(expected=ClientProtocolException.class)
    public void testBasicAuthenticationFailureOnNonRepeatablePutDontExpectContinue() throws Exception {
        this.localServer.register("*", new AuthHandler());

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "boom"));

        this.httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        final HttpPut httpput = new HttpPut("/");
        httpput.setConfig(config);
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));

        try {
            this.httpclient.execute(getServerHttp(), httpput);
            Assert.fail("ClientProtocolException should have been thrown");
        } catch (final ClientProtocolException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertNotNull(cause);
            Assert.assertTrue(cause instanceof NonRepeatableRequestException);
            throw ex;
        }
    }

    @Test
    public void testBasicAuthenticationSuccessOnRepeatablePost() throws Exception {
        this.localServer.register("*", new AuthHandler());

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));

        this.httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new StringEntity("some important stuff", Consts.ASCII));

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httppost);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test(expected=ClientProtocolException.class)
    public void testBasicAuthenticationFailureOnNonRepeatablePost() throws Exception {
        this.localServer.register("*", new AuthHandler());

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test"));

        this.httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 0,1,2,3,4,5,6,7,8,9 }), -1));
        try {
            this.httpclient.execute(getServerHttp(), httppost);
            Assert.fail("ClientProtocolException should have been thrown");
        } catch (final ClientProtocolException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertNotNull(cause);
            Assert.assertTrue(cause instanceof NonRepeatableRequestException);
            throw ex;
        }
    }

    static class TestTargetAuthenticationStrategy extends TargetAuthenticationStrategy {

        private final AtomicLong count;

        public TestTargetAuthenticationStrategy() {
            super();
            this.count = new AtomicLong();
        }

        @Override
        public boolean isAuthenticationRequested(
                final HttpHost host,
                final HttpResponse response,
                final HttpContext context) {
            final boolean res = super.isAuthenticationRequested(host, response, context);
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

        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test"));
        final TestTargetAuthenticationStrategy authStrategy = new TestTargetAuthenticationStrategy();

        this.httpclient = HttpClients.custom()
            .setDefaultCredentialsProvider(credsProvider)
            .setTargetAuthenticationStrategy(authStrategy)
            .build();

        final HttpClientContext context = HttpClientContext.create();

        final HttpHost targethost = getServerHttp();
        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response1 = this.httpclient.execute(targethost, httpget, context);
        final HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        final HttpResponse response2 = this.httpclient.execute(targethost, httpget, context);
        final HttpEntity entity2 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity2);
        EntityUtils.consume(entity2);

        Assert.assertEquals(1, authStrategy.getCount());
    }

    @Test
    public void testAuthenticationUserinfoInRequestSuccess() throws Exception {
        this.localServer.register("*", new AuthHandler());

        final HttpHost target = getServerHttp();
        final HttpGet httpget = new HttpGet("http://test:test@" +  target.toHostString() + "/");

        this.httpclient = HttpClients.custom().build();

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    @Test
    public void testAuthenticationUserinfoInRequestFailure() throws Exception {
        this.localServer.register("*", new AuthHandler());

        final HttpHost target = getServerHttp();
        final HttpGet httpget = new HttpGet("http://test:all-wrong@" +  target.toHostString() + "/");

        this.httpclient = HttpClients.custom().build();

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    private static class RedirectHandler implements HttpRequestHandler {

        public RedirectHandler() {
            super();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final HttpInetConnection conn = (HttpInetConnection) context.getAttribute(HttpCoreContext.HTTP_CONNECTION);
            final String localhost = conn.getLocalAddress().getHostName();
            final int port = conn.getLocalPort();
            response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
            response.addHeader(new BasicHeader("Location",
                    "http://test:test@" + localhost + ":" + port + "/"));
        }

    }

    @Test
    public void testAuthenticationUserinfoInRedirectSuccess() throws Exception {
        this.localServer.register("*", new AuthHandler());
        this.localServer.register("/thatway", new RedirectHandler());

        final HttpHost target = getServerHttp();
        final HttpGet httpget = new HttpGet("http://" +  target.toHostString() + "/thatway");

        this.httpclient = HttpClients.custom().build();

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    static class CountingAuthHandler implements HttpRequestHandler {

        private final AtomicLong count;

        public CountingAuthHandler() {
            super();
            this.count = new AtomicLong();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            this.count.incrementAndGet();
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", Consts.ASCII);
                response.setEntity(entity);
            }
        }

        public long getCount() {
            return this.count.get();
        }

    }

    @Test
    public void testPreemptiveAuthentication() throws Exception {
        final CountingAuthHandler requestHandler = new CountingAuthHandler();
        this.localServer.register("*", requestHandler);

        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test"));

        this.httpclient = HttpClients.custom()
            .setDefaultCredentialsProvider(credsProvider)
            .build();

        final HttpHost targethost = getServerHttp();

        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(targethost, new BasicScheme());
        context.setAuthCache(authCache);

        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response1 = this.httpclient.execute(targethost, httpget, context);
        final HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getStatusLine().getStatusCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        Assert.assertEquals(1, requestHandler.getCount());
    }

    @Test
    public void testPreemptiveAuthenticationFailure() throws Exception {
        final CountingAuthHandler requestHandler = new CountingAuthHandler();
        this.localServer.register("*", requestHandler);

        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "stuff"));

        this.httpclient = HttpClients.custom()
            .setDefaultCredentialsProvider(credsProvider)
            .build();

        final HttpHost targethost = getServerHttp();

        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(targethost, new BasicScheme());
        context.setAuthCache(authCache);

        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response1 = this.httpclient.execute(targethost, httpget, context);
        final HttpEntity entity1 = response1.getEntity();
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
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED);
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", Consts.ASCII);
                response.setEntity(entity);
            }
        }

    }

    @Test
    public void testAuthenticationTargetAsProxy() throws Exception {
        this.localServer.register("*", new ProxyAuthHandler());

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(null);
        this.httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(getServerHttp(), httpget);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
                response.getStatusLine().getStatusCode());
        EntityUtils.consume(entity);
    }

    static class ClosingAuthHandler implements HttpRequestHandler {

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", Consts.ASCII);
                response.setEntity(entity);
                response.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
            }
        }

    }

    @Test
    public void testConnectionCloseAfterAuthenticationSuccess() throws Exception {
        this.localServer.register("*", new ClosingAuthHandler());

        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test"));

        this.httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        final HttpClientContext context = HttpClientContext.create();

        final HttpHost targethost = getServerHttp();

        for (int i = 0; i < 2; i++) {
            final HttpGet httpget = new HttpGet("/");

            final HttpResponse response = this.httpclient.execute(targethost, httpget, context);
            EntityUtils.consume(response.getEntity());
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

}
