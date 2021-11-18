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
package org.apache.hc.client5.testing.async;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.auth.Authenticator;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.net.URIAuthority;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

public abstract class AbstractHttpAsyncClientAuthentication<T extends CloseableHttpAsyncClient> extends AbstractIntegrationTestBase<T> {

    protected final HttpVersion protocolVersion;

    public AbstractHttpAsyncClientAuthentication(final URIScheme scheme, final HttpVersion protocolVersion) {
        super(scheme);
        this.protocolVersion = protocolVersion;
    }

    @Override
    public final HttpHost start() throws Exception {
        return start(requestHandler -> new AuthenticatingAsyncDecorator(requestHandler, new BasicTestAuthenticator("test:test", "test realm")));
    }

    public final HttpHost start(
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        if (protocolVersion.greaterEquals(HttpVersion.HTTP_2_0)) {
            return super.start(
                    H2Processors.server(),
                    exchangeHandlerDecorator,
                    H2Config.DEFAULT);
        } else {
            return super.start(
                    HttpProcessors.server(),
                    exchangeHandlerDecorator,
                    Http1Config.DEFAULT);
        }
    }

    abstract void setDefaultAuthSchemeRegistry(Lookup<AuthSchemeFactory> authSchemeRegistry);

    abstract void setTargetAuthenticationStrategy(AuthenticationStrategy targetAuthStrategy);

    abstract void addResponseInterceptor(HttpResponseInterceptor responseInterceptor);

    abstract void addRequestInterceptor(final HttpRequestInterceptor requestInterceptor);

    @Test
    public void testBasicAuthenticationNoCreds() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = start();

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/")
                        .build(), context, null);
        final HttpResponse response = future.get();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    public void testBasicAuthenticationFailure() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = start();

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/")
                        .build(), context, null);
        final HttpResponse response = future.get();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    public void testBasicAuthenticationSuccess() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = start();

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/")
                        .build(), context, null);
        final HttpResponse response = future.get();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    public void testBasicAuthenticationWithEntitySuccess() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = start();

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleRequestBuilder.put()
                        .setHttpHost(target)
                        .setPath("/")
                        .setBody("Some important stuff", ContentType.TEXT_PLAIN)
                        .build(), context, null);
        final HttpResponse response = future.get();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    public void testBasicAuthenticationExpectationFailure() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = start();

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setRequestConfig(RequestConfig.custom().setExpectContinueEnabled(true).build());
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleRequestBuilder.put()
                        .setHttpHost(target)
                        .setPath("/")
                        .setBody("Some important stuff", ContentType.TEXT_PLAIN)
                        .build(), context, null);
        final HttpResponse response = future.get();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
    }

    @Test
    public void testBasicAuthenticationExpectationSuccess() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = start();

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setRequestConfig(RequestConfig.custom().setExpectContinueEnabled(true).build());
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleRequestBuilder.put()
                        .setHttpHost(target)
                        .setPath("/")
                        .setBody("Some important stuff", ContentType.TEXT_PLAIN)
                        .build(), context, null);
        final HttpResponse response = future.get();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

    @Test
    public void testBasicAuthenticationCredentialsCaching() throws Exception {
        server.register("*", AsyncEchoHandler::new);

        final DefaultAuthenticationStrategy authStrategy = Mockito.spy(new DefaultAuthenticationStrategy());
        setTargetAuthenticationStrategy(authStrategy);
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build());

        for (int i = 0; i < 5; i++) {
            final Future<SimpleHttpResponse> future = httpclient.execute(SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build(), context, null);
            final HttpResponse response = future.get();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        }

        Mockito.verify(authStrategy).select(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testBasicAuthenticationCredentialsCachingByPathPrefix() throws Exception {
        server.register("*", AsyncEchoHandler::new);

        final DefaultAuthenticationStrategy authStrategy = Mockito.spy(new DefaultAuthenticationStrategy());
        setTargetAuthenticationStrategy(authStrategy);
        final Queue<HttpResponse> responseQueue = new ConcurrentLinkedQueue<>();
        addResponseInterceptor((response, entity, context)
                -> responseQueue.add(BasicResponseBuilder.copy(response).build()));

        final HttpHost target = start();

        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build();

        final AuthCache authCache = new BasicAuthCache();

        for (final String requestPath: new String[] {"/blah/a", "/blah/b?huh", "/blah/c", "/bl%61h/%61"}) {
            final HttpClientContext context = HttpClientContext.create();
            context.setAuthCache(authCache);
            context.setCredentialsProvider(credentialsProvider);
            final Future<SimpleHttpResponse> future = httpclient.execute(SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(requestPath)
                    .build(), context, null);
            final HttpResponse response = future.get();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        }

        // There should be only single auth strategy call for all successful message exchanges
        Mockito.verify(authStrategy).select(Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(
                responseQueue.stream().map(HttpResponse::getCode).collect(Collectors.toList()),
                CoreMatchers.equalTo(Arrays.asList(401, 200, 200, 200, 200)));

        responseQueue.clear();
        authCache.clear();
        Mockito.reset(authStrategy);

        for (final String requestPath: new String[] {"/blah/a", "/yada/a", "/blah/blah/"}) {
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);
            context.setAuthCache(authCache);
            final Future<SimpleHttpResponse> future = httpclient.execute(SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(requestPath)
                    .build(), context, null);
            final HttpResponse response = future.get();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        }

        // There should be an auth strategy call for all successful message exchanges
        Mockito.verify(authStrategy, Mockito.times(3)).select(Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(
                responseQueue.stream().map(HttpResponse::getCode).collect(Collectors.toList()),
                CoreMatchers.equalTo(Arrays.asList(401, 200, 401, 200, 401, 200)));
    }

    @Test
    public void testAuthenticationUserinfoInRequestFailure() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleRequestBuilder.get()
                        .setScheme(target.getSchemeName())
                        .setAuthority(new URIAuthority("test:test", target.getHostName(), target.getPort()))
                        .setPath("/")
                        .build(), context, null);
        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () -> future.get());
        assertThat(exception.getCause(), CoreMatchers.instanceOf(ProtocolException.class));
    }

    @Test
    public void testReauthentication() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));

        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
                .register("MyBasic", context -> new BasicScheme() {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public String getName() {
                        return "MyBasic";
                    }

                })
                .build();
        setDefaultAuthSchemeRegistry(authSchemeRegistry);

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
                exchangeHandler -> new AuthenticatingAsyncDecorator(exchangeHandler, authenticator) {

                    @Override
                    protected void customizeUnauthorizedResponse(final HttpResponse unauthorized) {
                        unauthorized.removeHeaders(HttpHeaders.WWW_AUTHENTICATE);
                        unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, "MyBasic realm=\"test realm\"");
                    }

                });

        final RequestConfig config = RequestConfig.custom()
                .setTargetPreferredAuthSchemes(Collections.singletonList("MyBasic"))
                .build();
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        for (int i = 0; i < 10; i++) {
            final SimpleHttpRequest request = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/")
                        .build();
            request.setConfig(config);
            final Future<SimpleHttpResponse> future = httpclient.execute(request, context, null);
            final SimpleHttpResponse response = future.get();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

    @Test
    public void testAuthenticationFallback() throws Exception {
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = start(
                exchangeHandler -> new AuthenticatingAsyncDecorator(exchangeHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                    @Override
                    protected void customizeUnauthorizedResponse(final HttpResponse unauthorized) {
                        unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"test realm\" invalid");
                    }

                });

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/")
                        .build(), context, null);
        final SimpleHttpResponse response = future.get();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

}
