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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.protocol.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.sync.BasicCredentialsProvider;
import org.apache.hc.client5.http.localserver.BasicAuthTokenExtractor;
import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.client5.http.localserver.RequestBasicAuth;
import org.apache.hc.client5.http.localserver.ResponseBasicUnauthorized;
import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.NonRepeatableRequestException;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.HttpPost;
import org.apache.hc.client5.http.sync.methods.HttpPut;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.io.HttpExpectationVerifier;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for automatic client authentication.
 */
public class TestClientAuthentication extends LocalServerTestBase {

    @Before @Override
    public void setUp() throws Exception {
        super.setUp();
        final HttpProcessor httpproc = HttpProcessors.customServer(null)
            .add(new RequestBasicAuth())
            .add(new ResponseBasicUnauthorized()).build();
        this.serverBootstrap.setHttpProcessor(httpproc);
    }

    static class AuthHandler implements HttpRequestHandler {

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", StandardCharsets.US_ASCII);
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

        @Override
        public void verify(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException {
            final String creds = this.authTokenExtractor.extract(request);
            if (creds == null || !creds.equals("test:test")) {
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setCode(HttpStatus.SC_CONTINUE);
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

        @Override
        public Credentials getCredentials(final AuthScope authscope, final HttpContext context) {
            this.authscope = authscope;
            return this.creds;
        }

        public AuthScope getAuthScope() {
            return this.authscope;
        }

    }

    @Test
    public void testBasicAuthenticationNoCreds() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(null);
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationFailure() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationSuccess() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final HttpGet httpget = new HttpGet("/");
        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final HttpHost target = start();

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationSuccessOnNonRepeatablePutExpectContinue() throws Exception {
        final HttpProcessor httpproc = HttpProcessors.customServer(null)
            .add(new RequestBasicAuth())
            .add(new ResponseBasicUnauthorized()).build();
        this.serverBootstrap.setHttpProcessor(httpproc)
            .setExpectationVerifier(new AuthExpectationVerifier())
            .registerHandler("*", new AuthHandler());

        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom()
                .setExpectContinueEnabled(true)
                .build();
        final HttpPut httpput = new HttpPut("/");
        httpput.setConfig(config);
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));
        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final ClassicHttpResponse response = this.httpclient.execute(target, httpput, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertNotNull(entity);
    }

    @Test(expected=ClientProtocolException.class)
    public void testBasicAuthenticationFailureOnNonRepeatablePutDontExpectContinue() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        final HttpPut httpput = new HttpPut("/");
        httpput.setConfig(config);
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));

        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "boom".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        try {
            this.httpclient.execute(target, httpput, context);
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
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final HttpHost target = start();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new StringEntity("some important stuff", StandardCharsets.US_ASCII));

        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final ClassicHttpResponse response = this.httpclient.execute(target, httppost, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test(expected=ClientProtocolException.class)
    public void testBasicAuthenticationFailureOnNonRepeatablePost() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final HttpHost target = start();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 0,1,2,3,4,5,6,7,8,9 }), -1));

        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        try {
            this.httpclient.execute(target, httppost, context);
            Assert.fail("ClientProtocolException should have been thrown");
        } catch (final ClientProtocolException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertNotNull(cause);
            Assert.assertTrue(cause instanceof NonRepeatableRequestException);
            throw ex;
        }
    }

    static class TestTargetAuthenticationStrategy extends DefaultAuthenticationStrategy {

        private final AtomicLong count;

        public TestTargetAuthenticationStrategy() {
            super();
            this.count = new AtomicLong();
        }

        @Override
        public List<AuthScheme> select(
                final ChallengeType challengeType,
                final Map<String, AuthChallenge> challenges,
                final HttpContext context) {
            final List<AuthScheme> authSchemes = super.select(challengeType, challenges, context);
            this.count.incrementAndGet();
            return authSchemes;
        }

        public long getCount() {
            return this.count.get();
        }

    }

    @Test
    public void testBasicAuthenticationCredentialsCaching() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final TestTargetAuthenticationStrategy authStrategy = new TestTargetAuthenticationStrategy();
        this.clientBuilder.setTargetAuthenticationStrategy(authStrategy);

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget = new HttpGet("/");

        final ClassicHttpResponse response1 = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        final ClassicHttpResponse response2 = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity2 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertNotNull(entity2);
        EntityUtils.consume(entity2);

        Assert.assertEquals(1, authStrategy.getCount());
    }

    static class RealmAuthHandler implements HttpRequestHandler {

        final String realm;
        final String realmCreds;

        public RealmAuthHandler(final String realm, final String realmCreds) {
            this.realm = realm;
            this.realmCreds = realmCreds;
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String givenCreds = (String) context.getAttribute("creds");
            if (givenCreds == null || !givenCreds.equals(this.realmCreds)) {
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + this.realm + "\"");
            } else {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", StandardCharsets.US_ASCII);
                response.setEntity(entity);
            }
        }

    }

    @Test
    public void testAuthenticationCredentialsCachingReauthenticationOnDifferentRealm() throws Exception {
        this.serverBootstrap.registerHandler("/this", new RealmAuthHandler("this realm", "test:this"));
        this.serverBootstrap.registerHandler("/that", new RealmAuthHandler("that realm", "test:that"));

        this.server = this.serverBootstrap.create();
        this.server.start();

        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), this.scheme.name());

        final TestTargetAuthenticationStrategy authStrategy = new TestTargetAuthenticationStrategy();
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(target, "this realm", null),
                new UsernamePasswordCredentials("test", "this".toCharArray()));
        credsProvider.setCredentials(new AuthScope(target, "that realm", null),
                new UsernamePasswordCredentials("test", "that".toCharArray()));

        this.clientBuilder.setTargetAuthenticationStrategy(authStrategy);
        this.httpclient = this.clientBuilder.build();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget1 = new HttpGet("/this");

        final ClassicHttpResponse response1 = this.httpclient.execute(target, httpget1, context);
        final HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        final HttpGet httpget2 = new HttpGet("/this");

        final ClassicHttpResponse response2 = this.httpclient.execute(target, httpget2, context);
        final HttpEntity entity2 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertNotNull(entity2);
        EntityUtils.consume(entity2);

        final HttpGet httpget3 = new HttpGet("/that");

        final ClassicHttpResponse response3 = this.httpclient.execute(target, httpget3, context);
        final HttpEntity entity3 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response3.getCode());
        Assert.assertNotNull(entity3);
        EntityUtils.consume(entity3);

        Assert.assertEquals(2, authStrategy.getCount());
    }

    @Test
    public void testAuthenticationUserinfoInRequestSuccess() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final HttpHost target = start();
        final HttpGet httpget = new HttpGet("http://test:test@" +  target.toHostString() + "/");

        final HttpClientContext context = HttpClientContext.create();
        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    @Test
    public void testAuthenticationUserinfoInRequestFailure() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final HttpHost target = start();
        final HttpGet httpget = new HttpGet("http://test:all-wrong@" +  target.toHostString() + "/");

        final HttpClientContext context = HttpClientContext.create();
        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    private static class RedirectHandler implements HttpRequestHandler {

        public RedirectHandler() {
            super();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final HttpConnection conn = (HttpConnection) context.getAttribute(HttpCoreContext.HTTP_CONNECTION);
            final InetSocketAddress socketAddress = (InetSocketAddress) conn.getLocalAddress();
            final String localhost = socketAddress.getHostName();
            final int port = socketAddress.getPort();
            response.setCode(HttpStatus.SC_MOVED_PERMANENTLY);
            response.addHeader(new BasicHeader("Location",
                    "http://test:test@" + localhost + ":" + port + "/"));
        }

    }

    @Test
    public void testAuthenticationUserinfoInRedirectSuccess() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());
        this.serverBootstrap.registerHandler("/thatway", new RedirectHandler());

        final HttpHost target = start();

        final HttpGet httpget = new HttpGet("http://" +  target.toHostString() + "/thatway");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    static class CountingAuthHandler implements HttpRequestHandler {

        private final AtomicLong count;

        public CountingAuthHandler() {
            super();
            this.count = new AtomicLong();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            this.count.incrementAndGet();
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", StandardCharsets.US_ASCII);
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
        this.serverBootstrap.registerHandler("*", requestHandler);

        final HttpHost target = start();

        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        context.setAuthCache(authCache);

        final HttpGet httpget = new HttpGet("/");
        final ClassicHttpResponse response1 = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        Assert.assertEquals(1, requestHandler.getCount());
    }

    @Test
    public void testPreemptiveAuthenticationFailure() throws Exception {
        final CountingAuthHandler requestHandler = new CountingAuthHandler();
        this.serverBootstrap.registerHandler("*", requestHandler);

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, new BasicScheme());
        context.setAuthCache(authCache);
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "stuff".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget = new HttpGet("/");
        final ClassicHttpResponse response1 = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        Assert.assertEquals(1, requestHandler.getCount());
    }

    static class ProxyAuthHandler implements HttpRequestHandler {

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setCode(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED);
            } else {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", StandardCharsets.US_ASCII);
                response.setEntity(entity);
            }
        }

    }

    @Test
    public void testAuthenticationTargetAsProxy() throws Exception {
        this.serverBootstrap.registerHandler("*", new ProxyAuthHandler());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(null);
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget = new HttpGet("/");
        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
                response.getCode());
        EntityUtils.consume(entity);
    }

    static class ClosingAuthHandler implements HttpRequestHandler {

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("success", StandardCharsets.US_ASCII);
                response.setEntity(entity);
                response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
            }
        }

    }

    @Test
    public void testConnectionCloseAfterAuthenticationSuccess() throws Exception {
        this.serverBootstrap.registerHandler("*", new ClosingAuthHandler());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        for (int i = 0; i < 2; i++) {
            final HttpGet httpget = new HttpGet("/");

            final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
            EntityUtils.consume(response.getEntity());
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

}
