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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.testing.OldPathRedirectResolver;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.client5.testing.extension.async.TestAsyncServer;
import org.apache.hc.client5.testing.extension.async.TestAsyncServerBootstrap;
import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class AbstractHttpAsyncRedirectsTest extends AbstractIntegrationTestBase {

    public AbstractHttpAsyncRedirectsTest(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel, final ServerProtocolLevel serverProtocolLevel) {
        super(scheme, clientProtocolLevel, serverProtocolLevel);
    }

    @Test
    void testBasicRedirect300() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
        Assertions.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    void testBasicRedirect301() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/100")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/100", request.getRequestUri());
        Assertions.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    void testBasicRedirect302() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/123")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/123", request.getRequestUri());
        Assertions.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    void testBasicRedirect302NoLocation() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.startsWith("/oldlocation")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, null);
                            }
                            return null;
                        })));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/100")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();
        Assertions.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getCode());
        Assertions.assertEquals("/oldlocation/100", request.getRequestUri());
        Assertions.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    void testBasicRedirect303() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_SEE_OTHER))));

        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/123")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/123", request.getRequestUri());
        Assertions.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    void testBasicRedirect304() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .register("/oldlocation/*", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(final SimpleHttpRequest request,
                                                        final HttpCoreContext context) {
                        return SimpleHttpResponse.create(HttpStatus.SC_NOT_MODIFIED, (String) null);
                    }
                }));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getCode());
        Assertions.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    void testBasicRedirect305() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .register("/oldlocation/*", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(final SimpleHttpRequest request,
                                                        final HttpCoreContext context) {
                        return SimpleHttpResponse.create(HttpStatus.SC_USE_PROXY, (String) null);
                    }
                }));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_USE_PROXY, response.getCode());
        Assertions.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    void testBasicRedirect307() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_TEMPORARY_REDIRECT))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/123")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/123", request.getRequestUri());
        Assertions.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    void testMaxRedirectCheck() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                                HttpStatus.SC_MOVED_TEMPORARILY))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(5).build();
        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () -> {
            final Future<SimpleHttpResponse> future = client.execute(SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/circular-oldlocation/")
                    .setRequestConfig(config)
                    .build(), null);
            future.get();
        });
        assertThat(exception.getCause(), CoreMatchers.instanceOf(RedirectException.class));
    }

    @Test
    void testCircularRedirect() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                                HttpStatus.SC_MOVED_TEMPORARILY))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(false)
                .build();
        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () -> {
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/circular-oldlocation/")
                            .setRequestConfig(config)
                            .build(), null);
            future.get();
        });
        assertThat(exception.getCause(), CoreMatchers.instanceOf(CircularRedirectException.class));
    }

    @Test
    void testPostRedirect() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/echo/*", AsyncEchoHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/echo", HttpStatus.SC_TEMPORARY_REDIRECT))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.post()
                        .setHttpHost(target)
                        .setPath("/oldlocation/stuff")
                        .setBody("stuff", ContentType.TEXT_PLAIN)
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/echo/stuff", request.getRequestUri());
        Assertions.assertEquals("POST", request.getMethod());
    }

    @Test
    void testPostRedirectSeeOther() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/echo/*", AsyncEchoHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/echo", HttpStatus.SC_SEE_OTHER))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.post()
                        .setHttpHost(target)
                        .setPath("/oldlocation/stuff")
                        .setBody("stuff", ContentType.TEXT_PLAIN)
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/echo/stuff", request.getRequestUri());
        Assertions.assertEquals("GET", request.getMethod());
    }

    @Test
    void testRelativeRedirect() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.startsWith("/oldlocation")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/random/100");

                            }
                            return null;
                        })));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/stuff")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/100", request.getRequestUri());
        Assertions.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    void testRelativeRedirect2() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.equals("/random/oldlocation")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "100");

                            }
                            return null;
                        })));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/oldlocation")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/100", request.getRequestUri());
        Assertions.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    void testRejectBogusRedirectLocation() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.equals("/oldlocation/")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "xxx://bogus");

                            }
                            return null;
                        })));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () -> {
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/oldlocation/")
                            .build(), null);
            future.get();
        });
        assertThat(exception.getCause(), CoreMatchers.instanceOf(HttpException.class));
    }

    @Test
    void testRejectInvalidRedirectLocation() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.equals("/oldlocation/")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/newlocation/?p=I have spaces");
                            }
                            return null;
                        })));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () -> {
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/oldlocation/")
                            .build(), null);
            future.get();
        });
        assertThat(exception.getCause(), CoreMatchers.instanceOf(ProtocolException.class));
    }

    @Test
    void testRedirectWithCookie() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY))));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final CookieStore cookieStore = new BasicCookieStore();
        final HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");

        cookieStore.addCookie(cookie);

        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/100")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/100", request.getRequestUri());

        final Header[] headers = request.getHeaders("Cookie");
        Assertions.assertEquals(1, headers.length, "There can only be one (cookie)");
    }

    @Test
    void testCrossSiteRedirect() throws Exception {
        final URIScheme scheme = scheme();
        final TestAsyncServer secondServer = new TestAsyncServerBootstrap(scheme(), getServerProtocolLevel())
                .register("/random/*", AsyncRandomHandler::new)
                .build();
        try {
            final InetSocketAddress address2 = secondServer.start();

            final HttpHost redirectTarget = new HttpHost(scheme.name(), "localhost", address2.getPort());

            configureServer(bootstrap -> bootstrap
                    .register("/random/*", AsyncRandomHandler::new)
                    .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                            exchangeHandler,
                            requestUri -> {
                                final String path = requestUri.getPath();
                                if (path.equals("/oldlocation")) {
                                    final URI location = new URIBuilder(requestUri)
                                            .setHttpHost(redirectTarget)
                                            .setPath("/random/100")
                                            .build();
                                    return new Redirect(HttpStatus.SC_MOVED_PERMANENTLY, location.toString());
                                }
                                return null;
                            })));
            final HttpHost target = startServer();

            final TestAsyncClient client = startClient();

            final HttpClientContext context = HttpClientContext.create();
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/oldlocation")
                            .build(), context, null);
            final HttpResponse response = future.get();
            Assertions.assertNotNull(response);

            final HttpRequest request = context.getRequest();

            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertEquals("/random/100", request.getRequestUri());
            Assertions.assertEquals(redirectTarget, new HttpHost(request.getScheme(), request.getAuthority()));
        } finally {
            secondServer.shutdown(TimeValue.ofSeconds(5));
        }
    }

    @ParameterizedTest(name = "{displayName}; manually added header: {0}")
    @ValueSource(strings = {HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE})
    void testCrossSiteRedirectWithSensitiveHeaders(final String headerName) throws Exception {
        final URIScheme scheme = scheme();
        final TestAsyncServer secondServer = new TestAsyncServerBootstrap(scheme(), getServerProtocolLevel())
                .register("/random/*", AsyncRandomHandler::new)
                .build();
        try {
            final InetSocketAddress address2 = secondServer.start();

            final HttpHost redirectTarget = new HttpHost(scheme.name(), "localhost", address2.getPort());

            configureServer(bootstrap -> bootstrap
                    .register("/random/*", AsyncRandomHandler::new)
                    .setExchangeHandlerDecorator(exchangeHandler -> new RedirectingAsyncDecorator(
                            exchangeHandler,
                            requestUri -> {
                                final String path = requestUri.getPath();
                                if (path.equals("/oldlocation")) {
                                    final URI location = new URIBuilder(requestUri)
                                            .setHttpHost(redirectTarget)
                                            .setPath("/random/100")
                                            .build();
                                    return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, location.toString());
                                }
                                return null;
                            })));
            final HttpHost target = startServer();

            final TestAsyncClient client = startClient();

            final HttpClientContext context = HttpClientContext.create();
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/oldlocation")
                            .setHeader(headerName, "custom header")
                            .build(), context, null);
            final HttpResponse response = future.get();
            Assertions.assertNotNull(response);

            Assertions.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getCode());

            final RedirectLocations redirects = context.getRedirectLocations();
            Assertions.assertNotNull(redirects);
            Assertions.assertEquals(0, redirects.size());
        } finally {
            secondServer.shutdown(TimeValue.ofSeconds(5));
        }
    }

}
