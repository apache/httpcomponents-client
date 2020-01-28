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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.auth.Authenticator;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.Assert;
import org.junit.Test;

public abstract class AbstractHttpAsyncClientAuthentication<T extends CloseableHttpAsyncClient> extends AbstractIntegrationTestBase<T> {

    protected final HttpVersion protocolVersion;

    public AbstractHttpAsyncClientAuthentication(final URIScheme scheme, final HttpVersion protocolVersion) {
        super(scheme);
        this.protocolVersion = protocolVersion;
    }

    @Override
    public final HttpHost start() throws Exception {
        return start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler requestHandler) {
                return new AuthenticatingAsyncDecorator(requestHandler, new BasicTestAuthenticator("test:test", "test realm"));
            }

        });
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

    static class TestCredentialsProvider implements CredentialsStore {

        private final Credentials creds;
        private AuthScope authscope;

        TestCredentialsProvider(final Credentials creds) {
            super();
            this.creds = creds;
        }

        @Override
        public void clear() {
        }

        @Override
        public Credentials getCredentials(final AuthScope authscope, final HttpContext context) {
            this.authscope = authscope;
            return this.creds;
        }

        @Override
        public void setCredentials(final AuthScope authscope, final Credentials credentials) {
        }

        public AuthScope getAuthScope() {
            return this.authscope;
        }

    }

    @Test
    public void testBasicAuthenticationNoCreds() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(null);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequests.get(target, "/"), context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationFailure() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequests.get(target, "/"), context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationSuccess() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequests.get(target, "/"), context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationWithEntitySuccess() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final SimpleHttpRequest put = SimpleHttpRequests.put(target, "/");
        put.setBody("Some important stuff", ContentType.TEXT_PLAIN);
        final Future<SimpleHttpResponse> future = httpclient.execute(put, context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationExpectationFailure() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setRequestConfig(RequestConfig.custom().setExpectContinueEnabled(true).build());

        final SimpleHttpRequest put = SimpleHttpRequests.put(target, "/");
        put.setBody("Some important stuff", ContentType.TEXT_PLAIN);
        final Future<SimpleHttpResponse> future = httpclient.execute(put, context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
    }

    @Test
    public void testBasicAuthenticationExpectationSuccess() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setRequestConfig(RequestConfig.custom().setExpectContinueEnabled(true).build());

        final SimpleHttpRequest put = SimpleHttpRequests.put(target, "/");
        put.setBody("Some important stuff", ContentType.TEXT_PLAIN);
        final Future<SimpleHttpResponse> future = httpclient.execute(put, context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationCredentialsCaching() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });

        final AtomicLong count = new AtomicLong(0);
        setTargetAuthenticationStrategy(new DefaultAuthenticationStrategy() {

            @Override
            public List<AuthScheme> select(
                    final ChallengeType challengeType,
                    final Map<String, AuthChallenge> challenges,
                    final HttpContext context) {
                count.incrementAndGet();
                return super.select(challengeType, challenges, context);
            }
        });
        final HttpHost target = start();

        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(null, null, -1, null ,null),
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future1 = httpclient.execute(SimpleHttpRequests.get(target, "/"), context, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

        final Future<SimpleHttpResponse> future2 = httpclient.execute(SimpleHttpRequests.get(target, "/"), context, null);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);
        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());

        Assert.assertEquals(1, count.get());
    }

    @Test
    public void testAuthenticationUserinfoInRequestSuccess() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target.getSchemeName() + "://test:test@" +  target.toHostString() + "/"), context, null);
        final SimpleHttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
    }

    @Test
    public void testAuthenticationUserinfoInRequestFailure() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target.getSchemeName() + "://test:all-worng@" +  target.toHostString() + "/"), context, null);
        final SimpleHttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
    }

    @Test
    public void testAuthenticationUserinfoInRedirectSuccess() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start();
        server.register("/thatway", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_PERMANENTLY);
                        response.addHeader(new BasicHeader("Location", target.getSchemeName() + "://test:test@" + target.toHostString() + "/"));
                        return response;
                    }
                };
            }

        });

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target.getSchemeName() + "://test:test@" +  target.toHostString() + "/thatway"), context, null);
        final SimpleHttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
    }

    @Test
    public void testReauthentication() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));

        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
                .register("MyBasic", new AuthSchemeFactory() {

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
                new Decorator<AsyncServerExchangeHandler>() {

                    @Override
                    public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                        return new AuthenticatingAsyncDecorator(exchangeHandler, authenticator) {

                            @Override
                            protected void customizeUnauthorizedResponse(final HttpResponse unauthorized) {
                                unauthorized.removeHeaders(HttpHeaders.WWW_AUTHENTICATE);
                                unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, "MyBasic realm=\"test realm\"");
                            }

                        };
                    }

                });

        final RequestConfig config = RequestConfig.custom()
                .setTargetPreferredAuthSchemes(Arrays.asList("MyBasic"))
                .build();
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        for (int i = 0; i < 10; i++) {
            final SimpleHttpRequest request = SimpleHttpRequests.get(target, "/");
            request.setConfig(config);
            final Future<SimpleHttpResponse> future = httpclient.execute(request, context, null);
            final SimpleHttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

    @Test
    public void testAuthenticationFallback() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start(
                new Decorator<AsyncServerExchangeHandler>() {

                    @Override
                    public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                        return new AuthenticatingAsyncDecorator(exchangeHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                            @Override
                            protected void customizeUnauthorizedResponse(final HttpResponse unauthorized) {
                                unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"test realm\" invalid");
                            }

                        };
                    }

                });

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequests.get(target, "/"), context, null);
        final SimpleHttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

}
