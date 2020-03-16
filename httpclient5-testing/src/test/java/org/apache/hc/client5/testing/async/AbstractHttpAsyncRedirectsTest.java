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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.OldPathRedirectResolver;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.client5.testing.redirect.RedirectResolver;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactive.ReactiveServerExchangeHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.testing.reactive.ReactiveRandomProcessor;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Test;

public abstract class AbstractHttpAsyncRedirectsTest <T extends CloseableHttpAsyncClient> extends AbstractIntegrationTestBase<T> {

    protected final HttpVersion version;

    public AbstractHttpAsyncRedirectsTest(final HttpVersion version, final URIScheme scheme) {
        super(scheme);
        this.version = version;
    }

    @Override
    public final HttpHost start() throws Exception {
        if (version.greaterEquals(HttpVersion.HTTP_2)) {
            return super.start(null, H2Config.DEFAULT);
        } else {
            return super.start(null, Http1Config.DEFAULT);
        }
    }

    public final HttpHost start(final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        if (version.greaterEquals(HttpVersion.HTTP_2)) {
            return super.start(null, exchangeHandlerDecorator, H2Config.DEFAULT);
        } else {
            return super.start(null, exchangeHandlerDecorator, Http1Config.DEFAULT);
        }
    }

    @Test
    public void testBasicRedirect300() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES));
            }

        });

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect301() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY));
            }

        });
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/100"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/100", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testBasicRedirect302() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY));
            }

        });
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/123"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/123", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testBasicRedirect302NoLocation() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new RedirectResolver() {

                            @Override
                            public Redirect resolve(final URI requestUri) throws URISyntaxException {
                                final String path = requestUri.getPath();
                                if (path.startsWith("/oldlocation")) {
                                    return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, null);
                                }
                                return null;
                            }

                        });
            }

        });
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/100"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();
        Assert.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getCode());
        Assert.assertEquals("/oldlocation/100", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testBasicRedirect303() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_SEE_OTHER));
            }

        });
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/123"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/123", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testBasicRedirect304() throws Exception {
        server.register("/oldlocation/*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {

                return new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(final SimpleHttpRequest request,
                                                        final HttpCoreContext context) throws HttpException {
                        return SimpleHttpResponse.create(HttpStatus.SC_NOT_MODIFIED, (String) null);
                    }
                };

            }
        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect305() throws Exception {
        server.register("/oldlocation/*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {

                return new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(final SimpleHttpRequest request,
                                                        final HttpCoreContext context) throws HttpException {
                        return SimpleHttpResponse.create(HttpStatus.SC_USE_PROXY, (String) null);
                    }
                };

            }
        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_USE_PROXY, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect307() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_TEMPORARY_REDIRECT));
            }

        });
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/123"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/123", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test(expected=ExecutionException.class)
    public void testMaxRedirectCheck() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                                HttpStatus.SC_MOVED_TEMPORARILY));
            }

        });

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(5).build();
        try {
            final SimpleHttpRequest request = SimpleHttpRequests.get(target, "/circular-oldlocation/");
            request.setConfig(config);
            final Future<SimpleHttpResponse> future = httpclient.execute(request, null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof RedirectException);
            throw e;
        }
    }

    @Test(expected=ExecutionException.class)
    public void testCircularRedirect() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                                HttpStatus.SC_MOVED_TEMPORARILY));
            }

        });

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(false)
                .build();
        try {
            final SimpleHttpRequest request = SimpleHttpRequests.get(target, "/circular-oldlocation/");
            request.setConfig(config);
            final Future<SimpleHttpResponse> future = httpclient.execute(request, null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof CircularRedirectException);
            throw e;
        }
    }

    @Test
    public void testPostRedirectSeeOther() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/echo", HttpStatus.SC_SEE_OTHER));
            }

        });

        final HttpClientContext context = HttpClientContext.create();

        final SimpleHttpRequest post = SimpleHttpRequests.post(target, "/oldlocation/stuff");
        post.setBody("stuff", ContentType.TEXT_PLAIN);
        final Future<SimpleHttpResponse> future = httpclient.execute(post, context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/echo/stuff", request.getRequestUri());
        Assert.assertEquals("GET", request.getMethod());
    }

    @Test
    public void testRelativeRedirect() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new RedirectResolver() {

                            @Override
                            public Redirect resolve(final URI requestUri) throws URISyntaxException {
                                final String path = requestUri.getPath();
                                if (path.startsWith("/oldlocation")) {
                                    return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/random/100");

                                }
                                return null;
                            }

                        });
            }

        });

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/stuff"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/100", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testRelativeRedirect2() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new RedirectResolver() {

                            @Override
                            public Redirect resolve(final URI requestUri) throws URISyntaxException {
                                final String path = requestUri.getPath();
                                if (path.equals("/random/oldlocation")) {
                                    return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "100");

                                }
                                return null;
                            }

                        });
            }

        });

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/random/oldlocation"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/100", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test(expected=ExecutionException.class)
    public void testRejectBogusRedirectLocation() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new RedirectResolver() {

                            @Override
                            public Redirect resolve(final URI requestUri) throws URISyntaxException {
                                final String path = requestUri.getPath();
                                if (path.equals("/oldlocation/")) {
                                    return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "xxx://bogus");

                                }
                                return null;
                            }

                        });
            }

        });

        try {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequests.get(target, "/oldlocation/"), null);
            future.get();
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof HttpException);
            throw ex;
        }
    }

    @Test(expected=ExecutionException.class)
    public void testRejectInvalidRedirectLocation() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new RedirectResolver() {

                            @Override
                            public Redirect resolve(final URI requestUri) throws URISyntaxException {
                                final String path = requestUri.getPath();
                                if (path.equals("/oldlocation/")) {
                                    return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/newlocation/?p=I have spaces");

                                }
                                return null;
                            }

                        });
            }

        });

        try {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequests.get(target, "/oldlocation/"), null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof ProtocolException);
            throw e;
        }
    }

    @Test
    public void testRedirectWithCookie() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY));
            }

        });

        final CookieStore cookieStore = new BasicCookieStore();
        final HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");

        cookieStore.addCookie(cookie);

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/100"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/100", request.getRequestUri());

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertEquals("There can only be one (cookie)", 1, headers.length);
    }

    @Test
    public void testCrossSiteRedirect() throws Exception {
        final H2TestServer secondServer = new H2TestServer(IOReactorConfig.DEFAULT,
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null, null, null);
        try {
            secondServer.register("/random/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    if (isReactive()) {
                        return new ReactiveServerExchangeHandler(new ReactiveRandomProcessor());
                    } else {
                        return new AsyncRandomHandler();
                    }
                }

            });
            final InetSocketAddress address2;
            if (version.greaterEquals(HttpVersion.HTTP_2)) {
                address2 = secondServer.start(H2Config.DEFAULT);
            } else {
                address2 = secondServer.start(Http1Config.DEFAULT);
            }
            final HttpHost redirectTarget = new HttpHost(scheme.name(), "localhost", address2.getPort());

            final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                    return new RedirectingAsyncDecorator(
                            exchangeHandler,
                            new RedirectResolver() {

                                @Override
                                public Redirect resolve(final URI requestUri) throws URISyntaxException {
                                    final String path = requestUri.getPath();
                                    if (path.equals("/oldlocation")) {
                                        final URI location = new URIBuilder(requestUri)
                                                .setHttpHost(redirectTarget)
                                                .setPath("/random/100")
                                                .build();
                                        return new Redirect(HttpStatus.SC_MOVED_PERMANENTLY, location.toString());
                                    }
                                    return null;
                                }

                            });
                }

            });

            final HttpClientContext context = HttpClientContext.create();
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequests.get(target, "/oldlocation"), context, null);
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);

            final HttpRequest request = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals("/random/100", request.getRequestUri());
            Assert.assertEquals(redirectTarget, new HttpHost(request.getScheme(), request.getAuthority()));
        } finally {
            server.shutdown(TimeValue.ofSeconds(5));
        }
    }

}
