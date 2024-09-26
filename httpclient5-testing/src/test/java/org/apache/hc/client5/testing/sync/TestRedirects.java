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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.testing.OldPathRedirectResolver;
import org.apache.hc.client5.testing.classic.EchoHandler;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.client5.testing.classic.RedirectingDecorator;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Redirection test cases.
 */
abstract class TestRedirects extends AbstractIntegrationTestBase {

    protected TestRedirects(final URIScheme scheme) {
        super(scheme, ClientProtocolLevel.STANDARD);
    }

    @Test
    void testBasicRedirect300() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES)))
                .register("/random/*", new RandomHandler())
        );
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");
        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();
        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/oldlocation/100").build(),
                reqWrapper.getUri());

        final RedirectLocations redirects = context.getRedirectLocations();
        Assertions.assertNotNull(redirects);
        Assertions.assertEquals(0, redirects.size());
    }

    @Test
    void testBasicRedirect300NoKeepAlive() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES,
                                Redirect.ConnControl.CLOSE)))
                .register("/random/*", new RandomHandler())
        );
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");
        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/oldlocation/100").build(),
                reqWrapper.getUri());

        final RedirectLocations redirects = context.getRedirectLocations();
        Assertions.assertNotNull(redirects);
        Assertions.assertEquals(0, redirects.size());
    }

    @Test
    void testBasicRedirect301() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY)))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                reqWrapper.getUri());

        final RedirectLocations redirects = context.getRedirectLocations();
        Assertions.assertNotNull(redirects);
        Assertions.assertEquals(1, redirects.size());

        final URI redirect = new URIBuilder().setHttpHost(target).setPath("/random/100").build();
        Assertions.assertTrue(redirects.contains(redirect));
    }

    @Test
    void testBasicRedirect302() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/50");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/50").build(),
                reqWrapper.getUri());

    }

    @Test
    void testBasicRedirect302NoLocation() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.startsWith("/oldlocation")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, null);
                            }
                            return null;
                        }))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");

        client.execute(target, httpget, context, response -> {
            final HttpRequest reqWrapper = context.getRequest();

            Assertions.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getCode());
            Assertions.assertEquals("/oldlocation/100", reqWrapper.getRequestUri());

            EntityUtils.consume(response.getEntity());
            return null;
        });
    }

    @Test
    void testBasicRedirect303() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_SEE_OTHER)))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/123");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/123").build(),
                reqWrapper.getUri());
    }

    @Test
    void testBasicRedirect304() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/oldlocation/*", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_NOT_MODIFIED);
                    response.addHeader(HttpHeaders.LOCATION, "/random/100");
                })
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/stuff");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/oldlocation/stuff").build(),
                reqWrapper.getUri());

        final RedirectLocations redirects = context.getRedirectLocations();
        Assertions.assertNotNull(redirects);
        Assertions.assertEquals(0, redirects.size());
    }

    @Test
    void testBasicRedirect305() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/oldlocation/*", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_USE_PROXY);
                    response.addHeader(HttpHeaders.LOCATION, "/random/100");
                })
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/stuff");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_USE_PROXY, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/oldlocation/stuff").build(),
                reqWrapper.getUri());

        final RedirectLocations redirects = context.getRedirectLocations();
        Assertions.assertNotNull(redirects);
        Assertions.assertEquals(0, redirects.size());
    }

    @Test
    void testBasicRedirect307() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_TEMPORARY_REDIRECT)))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/123");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/123").build(),
                reqWrapper.getUri());
    }

    @Test
    void testMaxRedirectCheck() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                                HttpStatus.SC_MOVED_TEMPORARILY)))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(5)
                .build();

        final HttpGet httpget = new HttpGet("/circular-oldlocation/123");
        httpget.setConfig(config);
        final ClientProtocolException exception = Assertions.assertThrows(ClientProtocolException.class, () ->
                client.execute(target, httpget, response -> null));
        Assertions.assertTrue(exception.getCause() instanceof RedirectException);
    }

    @Test
    void testCircularRedirect() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                                HttpStatus.SC_MOVED_TEMPORARILY)))
                .register("/random/*", new RandomHandler()));

        final HttpHost target = startServer();

        final TestClient client = client();
        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(false)
                .build();

        final HttpGet httpget = new HttpGet("/circular-oldlocation/123");
        httpget.setConfig(config);
        final ClientProtocolException exception = Assertions.assertThrows(ClientProtocolException.class, () ->
                client.execute(target, httpget, response -> null));
        Assertions.assertTrue(exception.getCause() instanceof CircularRedirectException);
    }

    @Test
    void testPostRedirectSeeOther() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/echo", HttpStatus.SC_SEE_OTHER)))
                .register("/echo/*", new EchoHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpPost httppost = new HttpPost("/oldlocation/stuff");
        httppost.setEntity(new StringEntity("stuff"));

        client.execute(target, httppost, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/echo/stuff").build(),
                reqWrapper.getUri());
        Assertions.assertEquals("GET", reqWrapper.getMethod());
    }

    @Test
    void testRelativeRedirect() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.startsWith("/oldlocation")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/random/100");

                            }
                            return null;
                        }))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/stuff");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                reqWrapper.getUri());
    }

    @Test
    void testRelativeRedirect2() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.equals("/random/oldlocation")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "100");

                            }
                            return null;
                        }))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/random/oldlocation");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                reqWrapper.getUri());
    }

    @Test
    void testRejectBogusRedirectLocation() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.equals("/oldlocation")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "xxx://bogus");

                            }
                            return null;
                        }))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpGet httpget = new HttpGet("/oldlocation");

        final ClientProtocolException exception = Assertions.assertThrows(ClientProtocolException.class, () ->
                client.execute(target, httpget, response -> null));
        assertThat(exception.getCause(), CoreMatchers.instanceOf(HttpException.class));
    }

    @Test
    void testRejectInvalidRedirectLocation() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        requestUri -> {
                            final String path = requestUri.getPath();
                            if (path.equals("/oldlocation")) {
                                return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/newlocation/?p=I have spaces");

                            }
                            return null;
                        }))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpGet httpget = new HttpGet("/oldlocation");

        final ClientProtocolException exception = Assertions.assertThrows(ClientProtocolException.class, () ->
                client.execute(target, httpget, response -> null));
        assertThat(exception.getCause(), CoreMatchers.instanceOf(ProtocolException.class));
    }

    @Test
    void testRedirectWithCookie() throws Exception {
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final CookieStore cookieStore = new BasicCookieStore();

        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");

        cookieStore.addCookie(cookie);

        final HttpClientContext context = HttpClientContext.create();

        context.setCookieStore(cookieStore);
        final HttpGet httpget = new HttpGet("/oldlocation/100");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                reqWrapper.getUri());

        final Header[] headers = reqWrapper.getHeaders("Cookie");
        Assertions.assertEquals(1, headers.length, "There can only be one (cookie)");
    }

    @Test
    void testDefaultHeadersRedirect() throws Exception {
        configureClient(builder -> builder
                .setDefaultHeaders(Collections.singletonList(new BasicHeader(HttpHeaders.USER_AGENT, "my-test-client")))
        );

        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)))
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                reqWrapper.getUri());

        final Header header = reqWrapper.getFirstHeader(HttpHeaders.USER_AGENT);
        Assertions.assertEquals("my-test-client", header.getValue());
    }

    @Test
    void testCompressionHeaderRedirect() throws Exception {
        final Queue<String> values = new ConcurrentLinkedQueue<>();
        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)) {

                    @Override
                    public void handle(final ClassicHttpRequest request,
                                       final ResponseTrigger responseTrigger,
                                       final HttpContext context) throws HttpException, IOException {
                        final Header header = request.getHeader(HttpHeaders.ACCEPT_ENCODING);
                        if (header != null) {
                            values.add(header.getValue());
                        }
                        super.handle(request, responseTrigger, context);
                    }

                })
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                reqWrapper.getUri());

        assertThat(values.poll(), CoreMatchers.equalTo("gzip, x-gzip, deflate"));
        assertThat(values.poll(), CoreMatchers.equalTo("gzip, x-gzip, deflate"));
        assertThat(values.poll(), CoreMatchers.nullValue());
    }

    @Test
    void testRetryUponRedirect() throws Exception {
        configureClient(builder -> builder
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(
                        3,
                        TimeValue.ofSeconds(1),
                        Arrays.asList(
                                InterruptedIOException.class,
                                UnknownHostException.class,
                                ConnectException.class,
                                ConnectionClosedException.class,
                                NoRouteToHostException.class),
                        Arrays.asList(
                                HttpStatus.SC_TOO_MANY_REQUESTS,
                                HttpStatus.SC_SERVICE_UNAVAILABLE)) {
                })
        );

        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new RedirectingDecorator(
                        requestHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)))
                .register("/random/*", new HttpRequestHandler() {

                    final AtomicLong count = new AtomicLong();

                    @Override
                    public void handle(final ClassicHttpRequest request,
                                       final ClassicHttpResponse response,
                                       final HttpContext context) throws HttpException, IOException {
                        if (count.incrementAndGet() == 1) {
                            throw new IOException("Boom");
                        }
                        response.setCode(200);
                        response.setEntity(new StringEntity("test"));
                    }

                }));

        final HttpHost target = startServer();

        final TestClient client = client();
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/50");

        client.execute(target, httpget, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        final HttpRequest reqWrapper = context.getRequest();

        Assertions.assertEquals(new URIBuilder()
                        .setHttpHost(target)
                        .setPath("/random/50")
                        .build(),
                reqWrapper.getUri());
    }

}
