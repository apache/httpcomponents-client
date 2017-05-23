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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeProvider;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.protocol.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.sync.BasicCredentialsProvider;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.testing.auth.RequestBasicAuth;
import org.apache.hc.client5.testing.auth.ResponseBasicUnauthorized;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicAsyncResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestClientAuthentication extends IntegrationTestBase {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {URIScheme.HTTP},
                {URIScheme.HTTPS},
        });
    }

    public TestClientAuthentication(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    public HttpHost start() throws Exception {
        return super.start(HttpProcessors.customServer(null)
                .add(new RequestBasicAuth())
                .add(new ResponseBasicUnauthorized())
                .build(),
                H1Config.DEFAULT);
    }

    static class AuthHandler extends AbstractSimpleServerExchangeHandler {

        private final boolean keepAlive;

        AuthHandler(final boolean keepAlive) {
            super();
            this.keepAlive = keepAlive;
        }

        AuthHandler() {
            this(true);
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request,
                final HttpCoreContext context) throws HttpException {
            final String creds = (String) context.getAttribute("creds");
            final SimpleHttpResponse response;
            if (creds == null || !creds.equals("test:test")) {
                response = new SimpleHttpResponse(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response = new SimpleHttpResponse(HttpStatus.SC_OK, "success", ContentType.TEXT_PLAIN);
            }
            response.setHeader(HttpHeaders.CONNECTION, this.keepAlive ? HeaderElements.KEEP_ALIVE : HeaderElements.CLOSE);
            return response;
        }

    }

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
                return new AuthHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(null);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequest.get(target, "/"), context, null);
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
                return new AuthHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequest.get(target, "/"), context, null);
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
                return new AuthHandler();
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequest.get(target, "/"), context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

    @Test
    public void testBasicAuthenticationSuccessNonPersistentConnection() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AuthHandler(false);
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequest.get(target, "/"), context, null);
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
                return new AuthHandler() {

                    @Override
                    protected AsyncResponseProducer verify(
                            final HttpRequest request,
                            final HttpContext context) throws IOException, HttpException {

                        final String creds = (String) context.getAttribute("creds");
                        if (creds == null || !creds.equals("test:test")) {
                            return new BasicAsyncResponseProducer(new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED),
                                    new StringAsyncEntityProducer("Unauthorized"));
                        } else {
                            return null;
                        }
                    }

                };
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "all-wrong".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.put(target, "/", "Some important stuff", ContentType.TEXT_PLAIN), context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
    }

    @Test
    public void testBasicAuthenticationExpectationSuccess() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AuthHandler() {

                    @Override
                    protected AsyncResponseProducer verify(
                            final HttpRequest request,
                            final HttpContext context) throws IOException, HttpException {

                        final String creds = (String) context.getAttribute("creds");
                        if (creds == null || !creds.equals("test:test")) {
                            return new BasicAsyncResponseProducer(new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED),
                                    new StringAsyncEntityProducer("Unauthorized"));
                        } else {
                            return null;
                        }
                    }

                };
            }

        });
        final HttpHost target = start();

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.put(target, "/", "Some important stuff", ContentType.TEXT_PLAIN), context, null);
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
                return new AuthHandler();
            }

        });

        final AtomicLong count = new AtomicLong(0);
        this.clientBuilder.setTargetAuthenticationStrategy(new DefaultAuthenticationStrategy() {

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
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future1 = httpclient.execute(SimpleHttpRequest.get(target, "/"), context, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

        final Future<SimpleHttpResponse> future2 = httpclient.execute(SimpleHttpRequest.get(target, "/"), context, null);
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
                return new AuthHandler();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target.getSchemeName() + "://test:test@" +  target.toHostString() + "/"), context, null);
        final SimpleHttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("success", response.getBody());
    }

    @Test
    public void testAuthenticationUserinfoInRequestFailure() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AuthHandler();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target.getSchemeName() + "://test:all-worng@" +  target.toHostString() + "/"), context, null);
        final SimpleHttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
    }

    @Test
    public void testAuthenticationUserinfoInRedirectSuccess() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AuthHandler();
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
                SimpleHttpRequest.get(target.getSchemeName() + "://test:test@" +  target.toHostString() + "/thatway"), context, null);
        final SimpleHttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("success", response.getBody());
    }

    @Test
    public void testReauthentication() throws Exception {
        final AtomicLong count = new AtomicLong(0);

        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {

                return new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) throws HttpException {
                        final String creds = (String) context.getAttribute("creds");
                        if (creds == null || !creds.equals("test:test")) {
                            return new SimpleHttpResponse(HttpStatus.SC_UNAUTHORIZED);
                        } else {
                            // Make client re-authenticate on each fourth request
                            if (count.incrementAndGet() % 4 == 0) {
                                return new SimpleHttpResponse(HttpStatus.SC_UNAUTHORIZED);
                            } else {
                                return new SimpleHttpResponse(HttpStatus.SC_OK, "success", ContentType.TEXT_PLAIN);
                            }
                        }
                    }

                };
            }

        });

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register("MyBasic", new AuthSchemeProvider() {

                    @Override
                    public AuthScheme create(final HttpContext context) {
                        return new BasicScheme() {

                            @Override
                            public String getName() {
                                return "MyBasic";
                            }

                        };
                    }

                })
                .build();
        this.clientBuilder.setDefaultAuthSchemeRegistry(authSchemeRegistry);

        final HttpHost target = start(HttpProcessors.customServer(null)
                        .add(new RequestBasicAuth())
                        .add(new HttpResponseInterceptor() {

                            @Override
                            public void process(
                                    final HttpResponse response,
                                    final EntityDetails entityDetails,
                                    final HttpContext context) throws HttpException, IOException {
                                if (response.getCode() == HttpStatus.SC_UNAUTHORIZED) {
                                    response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "MyBasic realm=\"test realm\"");
                                }
                            }

                        })
                        .build(),
                H1Config.DEFAULT);

        final RequestConfig config = RequestConfig.custom()
                .setTargetPreferredAuthSchemes(Arrays.asList("MyBasic"))
                .build();
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        for (int i = 0; i < 10; i++) {
            final HttpGet httpget = new HttpGet("/");
            httpget.setConfig(config);
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    new SimpleRequestProducer(SimpleHttpRequest.get(target, "/"), config),
                    new SimpleResponseConsumer(),
                    context, null);
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

                return new AuthHandler();
            }

        });

        final HttpHost target = start(HttpProcessors.customServer(null)
                        .add(new RequestBasicAuth())
                        .add(new HttpResponseInterceptor() {

                            @Override
                            public void process(
                                    final HttpResponse response,
                                    final EntityDetails entityDetails,
                                    final HttpContext context) throws HttpException, IOException {
                                if (response.getCode() == HttpStatus.SC_UNAUTHORIZED) {
                                    response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"test realm\" invalid");
                                    response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test realm\"");
                                }
                            }

                        })
                        .build(),
                H1Config.DEFAULT);


        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequest.get(target, "/"), context, null);
        final SimpleHttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("success", response.getBody());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

}