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

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.BearerToken;
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
import org.apache.hc.client5.testing.auth.BearerAuthenticationHandler;
import org.apache.hc.client5.testing.classic.AuthenticatingDecorator;
import org.apache.hc.client5.testing.classic.EchoHandler;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.client5.testing.extension.sync.TestServerBootstrap;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.net.URIAuthority;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for automatic client authentication.
 */
abstract  class TestClientAuthentication extends AbstractIntegrationTestBase {

    protected TestClientAuthentication(final URIScheme scheme) {
        super(scheme, ClientProtocolLevel.STANDARD);
    }

    public void configureServerWithBasicAuth(final Authenticator authenticator,
                                             final Consumer<TestServerBootstrap> serverCustomizer) {
        configureServer(bootstrap -> {
            bootstrap.setExchangeHandlerDecorator(requestHandler ->
                    new AuthenticatingDecorator(requestHandler, authenticator));
            serverCustomizer.accept(bootstrap);
        });
    }

    public void configureServerWithBasicAuth(final Consumer<TestServerBootstrap> serverCustomizer) {
        configureServerWithBasicAuth(
                new BasicTestAuthenticator("test:test", "test realm"),
                serverCustomizer);
    }

    @Test
    void testBasicAuthenticationNoCreds() throws Exception {
        configureServerWithBasicAuth(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        client.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    void testBasicAuthenticationFailure() throws Exception {
        configureServerWithBasicAuth(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        client.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    void testBasicAuthenticationSuccess() throws Exception {
        configureServerWithBasicAuth(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpGet httpget = new HttpGet("/");
        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        client.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    void testBasicAuthenticationSuccessOnNonRepeatablePutExpectContinue() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final RequestConfig config = RequestConfig.custom()
                .setExpectContinueEnabled(true)
                .build();
        final HttpPut httpput = new HttpPut("/");
        httpput.setConfig(config);
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}),
                -1, null));
        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        client.execute(target, httpput, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity);
            return null;
        });
    }

    @Test
    void testBasicAuthenticationFailureOnNonRepeatablePutDontExpectContinue() throws Exception {
        configureServerWithBasicAuth(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(false).build();
        final HttpPut httpput = new HttpPut("/");
        httpput.setConfig(config);
        httpput.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}),
                -1, null));

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "boom".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        client.execute(target, httpput, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(401, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
    }

    @Test
    void testBasicAuthenticationSuccessOnRepeatablePost() throws Exception {
        configureServerWithBasicAuth(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new StringEntity("some important stuff", StandardCharsets.US_ASCII));

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        client.execute(target, httppost, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    void testBasicAuthenticationFailureOnNonRepeatablePost() throws Exception {
        configureServerWithBasicAuth(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}), -1, null));

        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom()
                .setExpectContinueEnabled(false)
                .build());
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);

        client.execute(target, httppost, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(401, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
    }

    @Test
    void testBasicAuthenticationCredentialsCaching() throws Exception {
        configureServerWithBasicAuth(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final DefaultAuthenticationStrategy authStrategy = Mockito.spy(new DefaultAuthenticationStrategy());
        final Queue<HttpResponse> responseQueue = new ConcurrentLinkedQueue<>();

        configureClient(builder -> builder
                .setTargetAuthenticationStrategy(authStrategy)
                .addResponseInterceptorLast((response, entity, context)
                        -> responseQueue.add(BasicResponseBuilder.copy(response).build())));
        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build());

        for (int i = 0; i < 5; i++) {
            final HttpGet httpget = new HttpGet("/");
            client.execute(target, httpget, context, response -> {
                final HttpEntity entity1 = response.getEntity();
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertNotNull(entity1);
                EntityUtils.consume(entity1);
                return null;
            });
        }

        Mockito.verify(authStrategy).select(Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(
                responseQueue.stream().map(HttpResponse::getCode).collect(Collectors.toList()),
                CoreMatchers.equalTo(Arrays.asList(401, 200, 200, 200, 200, 200)));
    }

    @Test
    void testBasicAuthenticationCredentialsCachingByPathPrefix() throws Exception {
        configureServerWithBasicAuth(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final DefaultAuthenticationStrategy authStrategy = Mockito.spy(new DefaultAuthenticationStrategy());
        final Queue<HttpResponse> responseQueue = new ConcurrentLinkedQueue<>();

        configureClient(builder -> builder
                .setTargetAuthenticationStrategy(authStrategy)
                .addResponseInterceptorLast((response, entity, context)
                        -> responseQueue.add(BasicResponseBuilder.copy(response).build())));
        final TestClient client = client();

        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build();

        final AuthCache authCache = new BasicAuthCache();
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(authCache);
        context.setCredentialsProvider(credentialsProvider);

        for (final String requestPath : new String[]{"/blah/a", "/blah/b?huh", "/blah/c", "/bl%61h/%61"}) {
            final HttpGet httpget = new HttpGet(requestPath);
            client.execute(target, httpget, context, response -> {
                final HttpEntity entity1 = response.getEntity();
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertNotNull(entity1);
                EntityUtils.consume(entity1);
                return null;
            });
        }

        // There should be only single auth strategy call for all successful message exchanges
        Mockito.verify(authStrategy).select(Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(
                responseQueue.stream().map(HttpResponse::getCode).collect(Collectors.toList()),
                CoreMatchers.equalTo(Arrays.asList(401, 200, 200, 200, 200)));

        responseQueue.clear();
        authCache.clear();
        Mockito.reset(authStrategy);

        for (final String requestPath : new String[]{"/blah/a", "/yada/a", "/blah/blah/", "/buh/a"}) {
            final HttpGet httpget = new HttpGet(requestPath);
            client.execute(target, httpget, context, response -> {
                final HttpEntity entity1 = response.getEntity();
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertNotNull(entity1);
                EntityUtils.consume(entity1);
                return null;
            });
        }

        // There should be an auth strategy call for all successful message exchanges
        Mockito.verify(authStrategy, Mockito.times(2)).select(Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(
                responseQueue.stream().map(HttpResponse::getCode).collect(Collectors.toList()),
                CoreMatchers.equalTo(Arrays.asList(200, 401, 200, 200, 401, 200)));
    }

    @Test
    void testAuthenticationCredentialsCachingReAuthenticationOnDifferentRealm() throws Exception {
        configureServerWithBasicAuth(new Authenticator() {

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

        }, bootstrap -> bootstrap.register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final DefaultAuthenticationStrategy authStrategy = Mockito.spy(new DefaultAuthenticationStrategy());

        configureClient(builder -> builder
                .setTargetAuthenticationStrategy(authStrategy)
        );
        final TestClient client = client();

        final CredentialsProvider credsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(target, "this realm", null), "test", "this".toCharArray())
                .add(new AuthScope(target, "that realm", null), "test", "that".toCharArray())
                .build();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget1 = new HttpGet("/this");

        client.execute(target, httpget1, context, response -> {
            final HttpEntity entity1 = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity1);
            EntityUtils.consume(entity1);
            return null;
        });

        final HttpGet httpget2 = new HttpGet("/this");

        client.execute(target, httpget2, context, response -> {
            final HttpEntity entity2 = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity2);
            EntityUtils.consume(entity2);
            return null;
        });

        final HttpGet httpget3 = new HttpGet("/that");

        client.execute(target, httpget3, context, response -> {
            final HttpEntity entity3 = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity3);
            EntityUtils.consume(entity3);
            return null;
        });

        Mockito.verify(authStrategy, Mockito.times(2)).select(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testAuthenticationUserinfoInRequest() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpGet httpget = new HttpGet("http://test:test@" + target.toHostString() + "/");

        final HttpClientContext context = HttpClientContext.create();
        Assertions.assertThrows(ClientProtocolException.class, () -> client.execute(target, httpget, context, response -> null));
    }

    @Test
    void testPreemptiveAuthentication() throws Exception {
        final Authenticator authenticator = Mockito.spy(new BasicTestAuthenticator("test:test", "test realm"));
        configureServerWithBasicAuth(authenticator,
                bootstrap -> bootstrap
                        .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        context.setAuthCache(authCache);

        final HttpGet httpget = new HttpGet("/");
        client.execute(target, httpget, context, response -> {
            final HttpEntity entity1 = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity1);
            EntityUtils.consume(entity1);
            return null;
        });

        Mockito.verify(authenticator).authenticate(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testPreemptiveAuthenticationFailure() throws Exception {
        final Authenticator authenticator = Mockito.spy(new BasicTestAuthenticator("test:test", "test realm"));
        configureServerWithBasicAuth(authenticator,
                bootstrap -> bootstrap
                        .register("*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, new BasicScheme());
        context.setAuthCache(authCache);
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "test", "stuff".toCharArray())
                .build());

        final HttpGet httpget = new HttpGet("/");
        client.execute(target, httpget, context, response -> {
            final HttpEntity entity1 = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            Assertions.assertNotNull(entity1);
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
                final HttpContext context) {
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
    void testAuthenticationTargetAsProxy() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("*", new ProxyAuthHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        context.setCredentialsProvider(credsProvider);

        final HttpGet httpget = new HttpGet("/");
        client.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, response.getCode());
            EntityUtils.consume(entity);
            return null;
        });
    }

    @Test
    void testConnectionCloseAfterAuthenticationSuccess() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler ->
                        new AuthenticatingDecorator(requestHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                            @Override
                            protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                                unauthorized.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                            }

                        }
                )
                .register("*", new EchoHandler()));

        final HttpHost target = startServer();

        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build();
        context.setCredentialsProvider(credsProvider);

        for (int i = 0; i < 2; i++) {
            final HttpGet httpget = new HttpGet("/");

            client.execute(target, httpget, context, response -> {
                EntityUtils.consume(response.getEntity());
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                return null;
            });
        }
    }

    @Test
    void testReauthentication() throws Exception {
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

        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler ->
                        new AuthenticatingDecorator(requestHandler, authenticator) {

                            @Override
                            protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                                unauthorized.removeHeaders(HttpHeaders.WWW_AUTHENTICATE);
                                unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, "MyBasic realm=\"test realm\"");
                            }

                        }
                )
                .register("*", new EchoHandler()));

        final HttpHost target = startServer();

        configureClient(builder -> builder
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
        );
        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        for (int i = 0; i < 10; i++) {
            final HttpGet httpget = new HttpGet("/");
            httpget.setConfig(config);
            client.execute(target, httpget, context, response -> {
                final HttpEntity entity = response.getEntity();
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertNotNull(entity);
                EntityUtils.consume(entity);
                return null;
            });
        }
    }

    @Test
    void testAuthenticationFallback() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler ->
                        new AuthenticatingDecorator(requestHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                            @Override
                            protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
                                unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"test realm\" invalid");
                            }

                        }
                )
                .register("*", new EchoHandler()));

        final HttpHost target = startServer();

        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        client.execute(target, httpget, context, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    private final static String CHARS = "0123456789abcdef";

    @Test
    void testBearerTokenAuthentication() throws Exception {
        final SecureRandom secureRandom = SecureRandom.getInstanceStrong();
        secureRandom.setSeed(System.currentTimeMillis());
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            buf.append(CHARS.charAt(secureRandom.nextInt(CHARS.length() - 1)));
        }
        final String token = buf.toString();
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler ->
                        new AuthenticatingDecorator(
                                requestHandler,
                                new BearerAuthenticationHandler(),
                                new BasicTestAuthenticator(token, "test realm"))
                )
                .register("*", new EchoHandler()));

        final HttpHost target = startServer();

        final TestClient client = client();

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);

        final HttpClientContext context1 = HttpClientContext.create();
        context1.setCredentialsProvider(credsProvider);
        final HttpGet httpget1 = new HttpGet("/");
        client.execute(target, httpget1, context1, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "bearer")), Mockito.any());

        final HttpClientContext context2 = HttpClientContext.create();
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new BearerToken(token));
        context2.setCredentialsProvider(credsProvider);
        final HttpGet httpget2 = new HttpGet("/");
        client.execute(target, httpget2, context2, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });

        final HttpClientContext context3 = HttpClientContext.create();
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new BearerToken(token + "-expired"));
        context3.setCredentialsProvider(credsProvider);
        final HttpGet httpget3 = new HttpGet("/");
        client.execute(target, httpget3, context3, response -> {
            final HttpEntity entity = response.getEntity();
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            Assertions.assertNotNull(entity);
            EntityUtils.consume(entity);
            return null;
        });
    }

}
