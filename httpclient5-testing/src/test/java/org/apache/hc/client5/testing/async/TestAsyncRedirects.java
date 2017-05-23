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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.CircularRedirectException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectException;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Supplier;
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
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.nio.Http2TestServer;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Redirection test cases.
 */
@RunWith(Parameterized.class)
public class TestAsyncRedirects extends IntegrationTestBase {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {URIScheme.HTTP},
                {URIScheme.HTTPS},
        });
    }

    public TestAsyncRedirects(final URIScheme scheme) {
        super(scheme);
    }

    static class BasicRedirectService extends AbstractSimpleServerExchangeHandler {

        private final int statuscode;
        private final boolean keepAlive;

        public BasicRedirectService(final int statuscode, final boolean keepAlive) {
            super();
            this.statuscode = statuscode;
            this.keepAlive = keepAlive;
        }

        public BasicRedirectService(final int statuscode) {
            this(statuscode, true);
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/oldlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(statuscode);
                    response.addHeader(new BasicHeader("Location",
                            new URIBuilder(requestURI).setPath("/newlocation/").build()));
                    if (!keepAlive) {
                        response.addHeader(new BasicHeader("Connection", "close"));
                    }
                    return response;
                } else if (path.equals("/newlocation/")) {
                    return new SimpleHttpResponse(HttpStatus.SC_OK, "Successful redirect", ContentType.TEXT_PLAIN);
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND, null, null);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    static class CircularRedirectService extends AbstractSimpleServerExchangeHandler {

        public CircularRedirectService() {
            super();
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.startsWith("/circular-oldlocation")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/circular-location2"));
                    return response;
                } else if (path.startsWith("/circular-location2")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/circular-oldlocation"));
                    return response;
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND, null, null);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    static class RelativeRedirectService extends AbstractSimpleServerExchangeHandler {

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/oldlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/relativelocation/"));
                    return response;
                } else if (path.equals("/relativelocation/")) {
                    return new SimpleHttpResponse(HttpStatus.SC_OK, "Successful redirect", ContentType.TEXT_PLAIN);
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }
    }

    static class RelativeRedirectService2 extends AbstractSimpleServerExchangeHandler {

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/test/oldlocation")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "relativelocation"));
                    return response;
                } else if (path.equals("/test/relativelocation")) {
                    return new SimpleHttpResponse(HttpStatus.SC_OK, "Successful redirect", ContentType.TEXT_PLAIN);
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    @Test
    public void testBasicRedirect300() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MULTIPLE_CHOICES, false);
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect301KeepAlive() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MOVED_PERMANENTLY, true);
            }

        });

        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testBasicRedirect301NoKeepAlive() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MOVED_PERMANENTLY, false);
            }

        });

        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testBasicRedirect302() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MOVED_TEMPORARILY);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testBasicRedirect302NoLocation() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
                        return new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    }

                };
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();
        Assert.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testBasicRedirect303() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_SEE_OTHER);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testBasicRedirect304() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_NOT_MODIFIED);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect305() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_USE_PROXY);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_USE_PROXY, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect307() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_TEMPORARY_REDIRECT);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test(expected=ExecutionException.class)
    public void testMaxRedirectCheck() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new CircularRedirectService();
            }

        });
        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(5).build();
        try {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    new SimpleRequestProducer(
                            SimpleHttpRequest.get(target, "/circular-oldlocation/"), config),
                    new SimpleResponseConsumer(), null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof RedirectException);
            throw e;
        }
    }

    @Test(expected=ExecutionException.class)
    public void testCircularRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new CircularRedirectService();
            }

        });
        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(false)
                .build();
        try {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    new SimpleRequestProducer(
                            SimpleHttpRequest.get(target, "/circular-oldlocation/"), config),
                    new SimpleResponseConsumer(), null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof CircularRedirectException);
            throw e;
        }
    }

    @Test
    public void testPostRedirectSeeOther() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_SEE_OTHER);
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.post(target, "/oldlocation/", "stuff", ContentType.TEXT_PLAIN), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals("GET", request.getMethod());
    }

    @Test
    public void testRelativeRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RelativeRedirectService();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/relativelocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testRelativeRedirect2() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RelativeRedirectService2();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/test/oldlocation"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/test/relativelocation", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    static class BogusRedirectService extends AbstractSimpleServerExchangeHandler {

        private final String url;

        public BogusRedirectService(final String url) {
            super();
            this.url = url;
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/oldlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", url));
                    return response;
                } else if (path.equals("/relativelocation/")) {
                    return new SimpleHttpResponse(HttpStatus.SC_OK, "Successful redirect", ContentType.TEXT_PLAIN);
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    @Test(expected=ExecutionException.class)
    public void testRejectBogusRedirectLocation() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BogusRedirectService("xxx://bogus");
            }

        });
        final HttpHost target = start();

        try {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequest.get(target, "/oldlocation/"), null);
            future.get();
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof HttpException);
            throw ex;
        }
    }

    @Test(expected=ExecutionException.class)
    public void testRejectInvalidRedirectLocation() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BogusRedirectService("/newlocation/?p=I have spaces");
            }

        });
        final HttpHost target = start();

        try {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequest.get(target, "/oldlocation/"), null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof ProtocolException);
            throw e;
        }
    }

    @Test
    public void testRedirectWithCookie() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MOVED_TEMPORARILY);
            }

        });
        final HttpHost target = start();

        final CookieStore cookieStore = new BasicCookieStore();
        final HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");

        cookieStore.addCookie(cookie);

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertEquals("There can only be one (cookie)", 1, headers.length);
    }

    @Test
    public void testDefaultHeadersRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MOVED_TEMPORARILY);
            }

        });

        final List<Header> defaultHeaders = new ArrayList<>(1);
        defaultHeaders.add(new BasicHeader(HttpHeaders.USER_AGENT, "my-test-client"));
        clientBuilder.setDefaultHeaders(defaultHeaders);

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());

        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assert.assertEquals("my-test-client", header.getValue());
    }

    static class CrossSiteRedirectService extends AbstractSimpleServerExchangeHandler {

        private final HttpHost host;

        public CrossSiteRedirectService(final HttpHost host) {
            super();
            this.host = host;
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            final String location;
            try {
                final URIBuilder uribuilder = new URIBuilder(request.getUri());
                uribuilder.setScheme(host.getSchemeName());
                uribuilder.setHost(host.getHostName());
                uribuilder.setPort(host.getPort());
                uribuilder.setPath("/random/1024");
                location = uribuilder.build().toASCIIString();
            } catch (final URISyntaxException ex) {
                throw new ProtocolException("Invalid request URI", ex);
            }
            final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_TEMPORARY_REDIRECT);
            response.addHeader(new BasicHeader("Location", location));
            return response;
        }
    }

    @Test
    public void testCrossSiteRedirect() throws Exception {
        server.register("/random/*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncRandomHandler();
            }

        });
        final HttpHost redirectTarget = start();

        final Http2TestServer secondServer = new Http2TestServer(IOReactorConfig.DEFAULT,
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null);
        try {
            secondServer.register("/redirect/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    return new CrossSiteRedirectService(redirectTarget);
                }

            });

            secondServer.start();
            final ListenerEndpoint endpoint2 = secondServer.listen(new InetSocketAddress(0));
            endpoint2.waitFor();

            final InetSocketAddress address2 = (InetSocketAddress) endpoint2.getAddress();
            final HttpHost initialTarget = new HttpHost("localhost", address2.getPort(), scheme.name());

            final Queue<Future<SimpleHttpResponse>> queue = new ConcurrentLinkedQueue<>();
            for (int i = 0; i < 1; i++) {
                queue.add(httpclient.execute(SimpleHttpRequest.get(initialTarget, "/redirect/anywhere"), null));
            }
            while (!queue.isEmpty()) {
                final Future<SimpleHttpResponse> future = queue.remove();
                final HttpResponse response = future.get();
                Assert.assertNotNull(response);
                Assert.assertEquals(200, response.getCode());
            }
        } finally {
            server.shutdown(TimeValue.ofSeconds(5));
        }
    }

    private static class RomeRedirectService extends AbstractSimpleServerExchangeHandler {

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/rome")) {
                    return new SimpleHttpResponse(HttpStatus.SC_OK, "Successful redirect", ContentType.TEXT_PLAIN);
                } else {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/rome"));
                    return response;
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    @Test
    public void testRepeatRequest() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RomeRedirectService();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future1 = httpclient.execute(
                SimpleHttpRequest.get(target, "/rome"), context, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);

        final Future<SimpleHttpResponse> future2 = httpclient.execute(
                SimpleHttpRequest.get(target, "/rome"), context, null);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertEquals("/rome", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testRepeatRequestRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RomeRedirectService();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future1 = httpclient.execute(
                SimpleHttpRequest.get(target, "/lille"), context, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);

        final Future<SimpleHttpResponse> future2 = httpclient.execute(
                SimpleHttpRequest.get(target, "/lille"), context, null);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertEquals("/rome", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testDifferentRequestSameRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RomeRedirectService();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future1 = httpclient.execute(
                SimpleHttpRequest.get(target, "/alian"), context, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);

        final Future<SimpleHttpResponse> future2 = httpclient.execute(
                SimpleHttpRequest.get(target, "/lille"), context, null);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);


        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertEquals("/rome", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

}