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
package org.apache.hc.client5.testing.sync;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.auth.Authenticator;
import org.apache.hc.client5.testing.classic.AuthenticatingDecorator;
import org.apache.hc.client5.testing.classic.EchoHandler;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for automatic client authentication.
 */
public class TestClientAuthentication extends LocalServerTestBase {

    public HttpHost start(final Authenticator authenticator) throws IOException {
        return super.start(null, new Decorator<HttpServerRequestHandler>() {

            @Override
            public HttpServerRequestHandler decorate(final HttpServerRequestHandler requestHandler) {
                return new AuthenticatingDecorator(requestHandler, authenticator);
            }

        });
    }

    @Override
    public HttpHost start() throws IOException {
        return start(new BasicTestAuthenticator("test:test", "test realm"));
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
        this.server.registerHandler("*", new EchoHandler());
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
        this.server.registerHandler("*", new EchoHandler());
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
        this.server.registerHandler("*", new EchoHandler());
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
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom()
                .setExpectContinueEnabled(true)
                .build();
        final HttpPut httpput = new HttpPut("/");
        httpput.setConfig(config);
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1, null));
        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final ClassicHttpResponse response = this.httpclient.execute(target, httpput, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertNotNull(entity);
    }

    @Test
    public void testBasicAuthenticationFailureOnNonRepeatablePutDontExpectContinue() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(false).build();
        final HttpPut httpput = new HttpPut("/");
        httpput.setConfig(config);
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1, null));

        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "boom".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final CloseableHttpResponse response = this.httpclient.execute(target, httpput, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(401, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    @Test
    public void testBasicAuthenticationSuccessOnRepeatablePost() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
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

    @Test
    public void testBasicAuthenticationFailureOnNonRepeatablePost() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 0,1,2,3,4,5,6,7,8,9 }), -1, null));

        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom()
                .setExpectContinueEnabled(false)
                .build());
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final CloseableHttpResponse response = this.httpclient.execute(target, httppost, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(401, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
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
        this.server.registerHandler("*", new EchoHandler());
        final TestTargetAuthenticationStrategy authStrategy = new TestTargetAuthenticationStrategy();
        this.clientBuilder.setTargetAuthenticationStrategy(authStrategy);

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(null, null, -1, null ,null),
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

    @Test
    public void testAuthenticationCredentialsCachingReauthenticationOnDifferentRealm() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start(new Authenticator() {

            @Override
            public boolean authenticate(final URIAuthority authority, final String requestUri, final String credentials) {
                if (requestUri.equals("/this")) {
                    return "test:this".equals(credentials);
                } else if (requestUri.equals("/that")) {
                    return "test:that".equals(credentials);
                } else {
                    return "test:test".equals(credentials);
                }
            }

            @Override
            public String getRealm(final URIAuthority authority, final String requestUri) {
                if (requestUri.equals("/this")) {
                    return "this realm";
                } else if (requestUri.equals("/that")) {
                    return "that realm";
                } else {
                    return "test realm";
                }
            }

        });

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
        this.server.registerHandler("*", new EchoHandler());
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
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start();
        final HttpGet httpget = new HttpGet("http://test:all-wrong@" +  target.toHostString() + "/");

        final HttpClientContext context = HttpClientContext.create();
        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    @Test
    public void testAuthenticationUserinfoInRedirectSuccess() throws Exception {
        this.server.registerHandler("/*", new EchoHandler());
        this.server.registerHandler("/thatway", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final EndpointDetails endpoint = (EndpointDetails) context.getAttribute(HttpCoreContext.CONNECTION_ENDPOINT);
                final InetSocketAddress socketAddress = (InetSocketAddress) endpoint.getLocalAddress();
                final int port = socketAddress.getPort();
                response.setCode(HttpStatus.SC_MOVED_PERMANENTLY);
                response.addHeader(new BasicHeader("Location", "http://test:test@localhost:" + port + "/secure"));
            }

        });

        final HttpHost target = start(new BasicTestAuthenticator("test:test", "test realm") {

            @Override
            public boolean authenticate(final URIAuthority authority, final String requestUri, final String credentials) {
                if (requestUri.equals("/secure") || requestUri.startsWith("/secure/")) {
                    return super.authenticate(authority, requestUri, credentials);
                }
                return true;
            }
        });

        final HttpGet httpget = new HttpGet("/thatway");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
    }

    static class CountingAuthenticator extends BasicTestAuthenticator {

        private final AtomicLong count;

        public CountingAuthenticator(final String userToken, final String realm) {
            super(userToken, realm);
            this.count = new AtomicLong();
        }

        @Override
        public boolean authenticate(final URIAuthority authority, final String requestUri, final String credentials) {
            this.count.incrementAndGet();
            return super.authenticate(authority, requestUri, credentials);
        }

        public long getCount() {
            return this.count.get();
        }

    }

    @Test
    public void testPreemptiveAuthentication() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final CountingAuthenticator countingAuthenticator = new CountingAuthenticator("test:test", "test realm");
        final HttpHost target = start(countingAuthenticator);

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

        Assert.assertEquals(1, countingAuthenticator.getCount());
    }

    @Test
    public void testPreemptiveAuthenticationFailure() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final CountingAuthenticator countingAuthenticator = new CountingAuthenticator("test:test", "test realm");

        final HttpHost target = start(countingAuthenticator);

        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, new BasicScheme());
        context.setAuthCache(authCache);
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(null, null, -1, null ,null),
                new UsernamePasswordCredentials("test", "stuff".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget = new HttpGet("/");
        final ClassicHttpResponse response1 = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity1 = response1.getEntity();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        Assert.assertNotNull(entity1);
        EntityUtils.consume(entity1);

        Assert.assertEquals(1, countingAuthenticator.getCount());
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
        this.server.registerHandler("*", new ProxyAuthHandler());

        final HttpHost target = super.start();

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

    @Test
    public void testConnectionCloseAfterAuthenticationSuccess() throws Exception {
        this.server.registerHandler("*", new EchoHandler());

        final HttpHost target = start(
                HttpProcessors.server(),
                new Decorator<HttpServerRequestHandler>() {

                    @Override
                    public HttpServerRequestHandler decorate(final HttpServerRequestHandler requestHandler) {
                        return new AuthenticatingDecorator(requestHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                            @Override
                            protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                                unauthorized.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                            }

                        };
                    }

                });

        final HttpClientContext context = HttpClientContext.create();
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(null, null, -1, null ,null),
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        for (int i = 0; i < 2; i++) {
            final HttpGet httpget = new HttpGet("/");

            final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
            EntityUtils.consume(response.getEntity());
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

    @Test
    public void testReauthentication() throws Exception {
        this.server.registerHandler("*", new EchoHandler());

        final BasicSchemeFactory myBasicAuthSchemeFactory = new BasicSchemeFactory() {

            @Override
            public AuthScheme create(final HttpContext context) {
                return new BasicScheme() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public String getName() {
                        return "MyBasic";
                    }

                };
            }

        };

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));

        final RequestConfig config = RequestConfig.custom()
                .setTargetPreferredAuthSchemes(Arrays.asList("MyBasic"))
                .build();
        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
                .register("MyBasic", myBasicAuthSchemeFactory)
                .build();
        this.httpclient = this.clientBuilder
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        final Authenticator authenticator = new BasicTestAuthenticator("test:test", "test realm") {

            private final AtomicLong count = new AtomicLong(0);

            @Override
            public boolean authenticate(final URIAuthority authority, final String requestUri, final String credentials) {
                final boolean authenticated = super.authenticate(authority, requestUri, credentials);
                if (authenticated) {
                    return this.count.incrementAndGet() % 4 != 0;
                }
                return false;
            }
        };

        final HttpHost target = start(
                HttpProcessors.server(),
                new Decorator<HttpServerRequestHandler>() {

                    @Override
                    public HttpServerRequestHandler decorate(final HttpServerRequestHandler requestHandler) {
                        return new AuthenticatingDecorator(requestHandler, authenticator) {

                            @Override
                            protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                                unauthorized.removeHeaders(HttpHeaders.WWW_AUTHENTICATE);
                                unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, "MyBasic realm=\"test realm\"");
                            }

                        };
                    }

                });

        final HttpClientContext context = HttpClientContext.create();
        for (int i = 0; i < 10; i++) {
            final HttpGet httpget = new HttpGet("/");
            httpget.setConfig(config);
            try (final CloseableHttpResponse response = this.httpclient.execute(target, httpget, context)) {
                final HttpEntity entity = response.getEntity();
                Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assert.assertNotNull(entity);
                EntityUtils.consume(entity);
            }
        }
    }

    @Test
    public void testAuthenticationFallback() throws Exception {
        this.server.registerHandler("*", new EchoHandler());

        final HttpHost target = start(
                HttpProcessors.server(),
                new Decorator<HttpServerRequestHandler>() {

                    @Override
                    public HttpServerRequestHandler decorate(final HttpServerRequestHandler requestHandler) {
                        return new AuthenticatingDecorator(requestHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                            @Override
                            protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                                unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"test realm\" invalid");
                            }

                        };
                    }

                });

        final HttpClientContext context = HttpClientContext.create();
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        final HttpEntity entity = response.getEntity();
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertNotNull(entity);
        EntityUtils.consume(entity);
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

}
