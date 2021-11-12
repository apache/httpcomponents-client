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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.auth.Authenticator;
import org.apache.hc.client5.testing.classic.AuthenticatingDecorator;
import org.apache.hc.client5.testing.classic.EchoHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.net.URIAuthority;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for automatic client authentication.
 */
public class TestClientAuthentication extends LocalServerTestBase {

    public HttpHost start(final Authenticator authenticator) throws IOException {
        return super.start(null, requestHandler -> new AuthenticatingDecorator(requestHandler, authenticator));
    }

    @Override
    public HttpHost start() throws IOException {
        return start(new BasicTestAuthenticator("test:test", "test realm"));
    }

    @Test
    public void testBasicAuthenticationNoCreds() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        this.httpclient.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            Assert.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    public void testBasicAuthenticationFailure() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        this.httpclient.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            Assert.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    public void testBasicAuthenticationSuccess() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final HttpGet httpget = new HttpGet("/");
        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        final HttpHost target = start();

        this.httpclient.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
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
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        this.httpclient.execute(target, httpput, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertNotNull(entity);
            return null;
        });
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
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "boom".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        this.httpclient.execute(target, httpput, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(401, response.getCode());
            Assert.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
    }

    @Test
    public void testBasicAuthenticationSuccessOnRepeatablePost() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new StringEntity("some important stuff", StandardCharsets.US_ASCII));

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        this.httpclient.execute(target, httppost, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
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
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        this.httpclient.execute(target, httppost, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(401, response.getCode());
            Assert.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
    }

    @Test
    public void testBasicAuthenticationCredentialsCaching() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final DefaultAuthenticationStrategy authStrategy = Mockito.spy(new DefaultAuthenticationStrategy());
        this.clientBuilder.setTargetAuthenticationStrategy(authStrategy);
        final Queue<HttpResponse> responseQueue = new ConcurrentLinkedQueue<>();
        this.clientBuilder.addResponseInterceptorLast((response, entity, context)
                -> responseQueue.add(BasicResponseBuilder.copy(response).build()));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build());

        for (int i = 0; i < 5; i++) {
            final HttpGet httpget = new HttpGet("/");
            this.httpclient.execute(target, httpget, context, response -> {
                final HttpEntity entity1 = response.getEntity();
                Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assert.assertNotNull(entity1);
                EntityUtils.consume(entity1);
                return null;
            });
        }

        Mockito.verify(authStrategy).select(Mockito.any(), Mockito.any(), Mockito.any());

        MatcherAssert.assertThat(
                responseQueue.stream().map(HttpResponse::getCode).collect(Collectors.toList()),
                CoreMatchers.equalTo(Arrays.asList(401, 200, 200, 200, 200, 200)));
    }

    @Test
    public void testBasicAuthenticationCredentialsCachingByPathPrefix() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final DefaultAuthenticationStrategy authStrategy = Mockito.spy(new DefaultAuthenticationStrategy());
        this.clientBuilder.setTargetAuthenticationStrategy(authStrategy);
        final Queue<HttpResponse> responseQueue = new ConcurrentLinkedQueue<>();
        this.clientBuilder.addResponseInterceptorLast((response, entity, context)
                -> responseQueue.add(BasicResponseBuilder.copy(response).build()));

        final HttpHost target = start();

        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build();

        final AuthCache authCache = new BasicAuthCache();
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(authCache);
        context.setCredentialsProvider(credentialsProvider);

        for (final String requestPath: new String[] {"/blah/a", "/blah/b?huh", "/blah/c", "/bl%61h/%61"}) {
            final HttpGet httpget = new HttpGet(requestPath);
            this.httpclient.execute(target, httpget, context, response -> {
                final HttpEntity entity1 = response.getEntity();
                Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assert.assertNotNull(entity1);
                EntityUtils.consume(entity1);
                return null;
            });
        }

        // There should be only single auth strategy call for all successful message exchanges
        Mockito.verify(authStrategy).select(Mockito.any(), Mockito.any(), Mockito.any());

        MatcherAssert.assertThat(
                responseQueue.stream().map(HttpResponse::getCode).collect(Collectors.toList()),
                CoreMatchers.equalTo(Arrays.asList(401, 200, 200, 200, 200)));

        responseQueue.clear();
        authCache.clear();
        Mockito.reset(authStrategy);

        for (final String requestPath: new String[] {"/blah/a", "/yada/a", "/blah/blah/", "/buh/a"}) {
            final HttpGet httpget = new HttpGet(requestPath);
            this.httpclient.execute(target, httpget, context, response -> {
                final HttpEntity entity1 = response.getEntity();
                Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assert.assertNotNull(entity1);
                EntityUtils.consume(entity1);
                return null;
            });
        }

        // There should be an auth strategy call for all successful message exchanges
        Mockito.verify(authStrategy, Mockito.times(2)).select(Mockito.any(), Mockito.any(), Mockito.any());

        MatcherAssert.assertThat(
                responseQueue.stream().map(HttpResponse::getCode).collect(Collectors.toList()),
                CoreMatchers.equalTo(Arrays.asList(200, 401, 200, 200, 401, 200)));
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

        final DefaultAuthenticationStrategy authStrategy = Mockito.spy(new DefaultAuthenticationStrategy());
        final CredentialsProvider credsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(target, "this realm", null), "test", "this".toCharArray())
                .add(new AuthScope(target, "that realm", null), "test", "that".toCharArray())
                .build();

        this.clientBuilder.setTargetAuthenticationStrategy(authStrategy);
        this.httpclient = this.clientBuilder.build();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget1 = new HttpGet("/this");

        this.httpclient.execute(target, httpget1, context, response -> {
            final HttpEntity entity1 = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertNotNull(entity1);
            EntityUtils.consume(entity1);
            return null;
        });

        final HttpGet httpget2 = new HttpGet("/this");

        this.httpclient.execute(target, httpget2, context, response -> {
            final HttpEntity entity2 = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertNotNull(entity2);
            EntityUtils.consume(entity2);
            return null;
        });

        final HttpGet httpget3 = new HttpGet("/that");

        this.httpclient.execute(target, httpget3, context, response -> {
            final HttpEntity entity3 = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertNotNull(entity3);
            EntityUtils.consume(entity3);
            return null;
        });

        Mockito.verify(authStrategy, Mockito.times(2)).select(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testAuthenticationUserinfoInRequest() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final HttpHost target = start();
        final HttpGet httpget = new HttpGet("http://test:test@" +  target.toHostString() + "/");

        final HttpClientContext context = HttpClientContext.create();
        Assert.assertThrows(ClientProtocolException.class, () -> this.httpclient.execute(target, httpget, context, response -> null));
    }

    @Test
    public void testPreemptiveAuthentication() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final Authenticator authenticator = Mockito.spy(new BasicTestAuthenticator("test:test", "test realm"));
        final HttpHost target = start(authenticator);

        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        context.setAuthCache(authCache);

        final HttpGet httpget = new HttpGet("/");
        this.httpclient.execute(target, httpget, context, response -> {
            final HttpEntity entity1 = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertNotNull(entity1);
            EntityUtils.consume(entity1);
            return null;
        });

        Mockito.verify(authenticator).authenticate(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testPreemptiveAuthenticationFailure() throws Exception {
        this.server.registerHandler("*", new EchoHandler());
        final Authenticator authenticator = Mockito.spy(new BasicTestAuthenticator("test:test", "test realm"));
        final HttpHost target = start(authenticator);

        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, new BasicScheme());
        context.setAuthCache(authCache);
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "test", "stuff".toCharArray())
                .build());

        final HttpGet httpget = new HttpGet("/");
        this.httpclient.execute(target, httpget, context, response -> {
            final HttpEntity entity1 = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            Assert.assertNotNull(entity1);
            EntityUtils.consume(entity1);
            return null;
        });

        Mockito.verify(authenticator).authenticate(Mockito.any(), Mockito.any(), Mockito.any());
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
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget = new HttpGet("/");
        this.httpclient.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, response.getCode());
            EntityUtils.consume(entity);
            return null;
        });
    }

    @Test
    public void testConnectionCloseAfterAuthenticationSuccess() throws Exception {
        this.server.registerHandler("*", new EchoHandler());

        final HttpHost target = start(
                HttpProcessors.server(),
                requestHandler -> new AuthenticatingDecorator(requestHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                    @Override
                    protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                        unauthorized.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    }

                });

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build();
        context.setCredentialsProvider(credsProvider);

        for (int i = 0; i < 2; i++) {
            final HttpGet httpget = new HttpGet("/");

            this.httpclient.execute(target, httpget, context, response -> {
                EntityUtils.consume(response.getEntity());
                Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
                return null;
            });
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

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));

        final RequestConfig config = RequestConfig.custom()
                .setTargetPreferredAuthSchemes(Collections.singletonList("MyBasic"))
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
                requestHandler -> new AuthenticatingDecorator(requestHandler, authenticator) {

                    @Override
                    protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                        unauthorized.removeHeaders(HttpHeaders.WWW_AUTHENTICATE);
                        unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, "MyBasic realm=\"test realm\"");
                    }

                });

        final HttpClientContext context = HttpClientContext.create();
        for (int i = 0; i < 10; i++) {
            final HttpGet httpget = new HttpGet("/");
            httpget.setConfig(config);
            this.httpclient.execute(target, httpget, context, response -> {
                final HttpEntity entity = response.getEntity();
                Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assert.assertNotNull(entity);
                EntityUtils.consume(entity);
                return null;
            });
        }
    }

    @Test
    public void testAuthenticationFallback() throws Exception {
        this.server.registerHandler("*", new EchoHandler());

        final HttpHost target = start(
                HttpProcessors.server(),
                requestHandler -> new AuthenticatingDecorator(requestHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                    @Override
                    protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                        unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"test realm\" invalid");
                    }

                });

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        this.httpclient.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

}
