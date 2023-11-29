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
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.OldPathRedirectResolver;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.core5.function.Decorator;
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
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.util.TimeValue;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public abstract class AbstractHttpAsyncRedirectsTest <T extends CloseableHttpAsyncClient> extends AbstractIntegrationTestBase {

    private final HttpVersion version;

    public AbstractHttpAsyncRedirectsTest(final URIScheme scheme, final HttpVersion version) {
        super(scheme);
        this.version = version;
    }

    abstract protected H2TestServer startServer(final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception;

    protected H2TestServer startServer() throws Exception {
        return startServer(null);
    }

    abstract protected T startClient() throws Exception;

    @Test
    public void testBasicRedirect300() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES)));

        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testBasicRedirect301() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testBasicRedirect302() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testBasicRedirect302NoLocation() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.startsWith("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, null);
                    }
                    return null;
                }));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testBasicRedirect303() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_SEE_OTHER)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testBasicRedirect304() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", AsyncRandomHandler::new);
        server.register("/oldlocation/*", () -> new AbstractSimpleServerExchangeHandler() {

            @Override
            protected SimpleHttpResponse handle(final SimpleHttpRequest request,
                                                final HttpCoreContext context) throws HttpException {
                return SimpleHttpResponse.create(HttpStatus.SC_NOT_MODIFIED, (String) null);
            }
        });
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testBasicRedirect305() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", AsyncRandomHandler::new);
        server.register("/oldlocation/*", () -> new AbstractSimpleServerExchangeHandler() {

            @Override
            protected SimpleHttpResponse handle(final SimpleHttpRequest request,
                                                final HttpCoreContext context) throws HttpException {
                return SimpleHttpResponse.create(HttpStatus.SC_USE_PROXY, (String) null);
            }
        });
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testBasicRedirect307() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_TEMPORARY_REDIRECT)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testMaxRedirectCheck() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                        HttpStatus.SC_MOVED_TEMPORARILY)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testCircularRedirect() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                        HttpStatus.SC_MOVED_TEMPORARILY)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testPostRedirect() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/echo", HttpStatus.SC_TEMPORARY_REDIRECT)));

        server.register("/echo/*", AsyncEchoHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testPostRedirectSeeOther() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/echo", HttpStatus.SC_SEE_OTHER)));

        server.register("/echo/*", AsyncEchoHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testRelativeRedirect() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.startsWith("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/random/100");

                    }
                    return null;
                }));

        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testRelativeRedirect2() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/random/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "100");

                    }
                    return null;
                }));

        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testRejectBogusRedirectLocation() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/oldlocation/")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "xxx://bogus");

                    }
                    return null;
                }));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testRejectInvalidRedirectLocation() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/oldlocation/")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/newlocation/?p=I have spaces");

                    }
                    return null;
                }));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testRedirectWithCookie() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)));

        final CookieStore cookieStore = new BasicCookieStore();
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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
    public void testCrossSiteRedirect() throws Exception {
        final URIScheme scheme = scheme();
        final H2TestServer secondServer = new H2TestServer(IOReactorConfig.DEFAULT,
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null, null, null);
        try {
            secondServer.register("/random/*", AsyncRandomHandler::new);
            final InetSocketAddress address2;
            if (version.greaterEquals(HttpVersion.HTTP_2)) {
                address2 = secondServer.start(H2Config.DEFAULT);
            } else {
                address2 = secondServer.start(Http1Config.DEFAULT);
            }
            final HttpHost redirectTarget = new HttpHost(scheme.name(), "localhost", address2.getPort());

            final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
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
                    }));

            server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final T client = startClient();

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

}
