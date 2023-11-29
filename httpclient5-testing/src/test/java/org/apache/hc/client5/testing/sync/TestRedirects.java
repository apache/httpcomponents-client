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
import java.net.URI;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.testing.OldPathRedirectResolver;
import org.apache.hc.client5.testing.classic.EchoHandler;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.client5.testing.classic.RedirectingDecorator;
import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Redirection test cases.
 */
public abstract class TestRedirects {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources;

    protected TestRedirects(final URIScheme scheme) {
        this.testResources = new TestClientResources(scheme, TIMEOUT);
    }

    public URIScheme scheme() {
        return testResources.scheme();
    }

    public ClassicTestServer startServer(final HttpProcessor httpProcessor,
                                         final Decorator<HttpServerRequestHandler> handlerDecorator) throws IOException {
        return testResources.startServer(null, httpProcessor, handlerDecorator);
    }

    public CloseableHttpClient startClient(final Consumer<HttpClientBuilder> clientCustomizer) throws Exception {
        return testResources.startClient(clientCustomizer);
    }

    public CloseableHttpClient startClient() throws Exception {
        return testResources.startClient(builder -> {});
    }

    public HttpHost targetHost() {
        return testResources.targetHost();
    }

    @Test
    public void testBasicRedirect300() throws Exception {
        final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testBasicRedirect300NoKeepAlive() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES,
                        Redirect.ConnControl.CLOSE)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testBasicRedirect301() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testBasicRedirect302() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testBasicRedirect302NoLocation() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.startsWith("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, null);
                    }
                    return null;
                }));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testBasicRedirect303() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_SEE_OTHER)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testBasicRedirect304() throws Exception {
        final ClassicTestServer server = startServer(null ,null);
        server.registerHandler("/oldlocation/*", (request, response, context) -> {
            response.setCode(HttpStatus.SC_NOT_MODIFIED);
            response.addHeader(HttpHeaders.LOCATION, "/random/100");
        });

        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testBasicRedirect305() throws Exception {
        final ClassicTestServer server =  startServer(null ,null);
        server.registerHandler("/oldlocation/*", (request, response, context) -> {
            response.setCode(HttpStatus.SC_USE_PROXY);
            response.addHeader(HttpHeaders.LOCATION, "/random/100");
        });

        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testBasicRedirect307() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_TEMPORARY_REDIRECT)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testMaxRedirectCheck() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                        HttpStatus.SC_MOVED_TEMPORARILY)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testCircularRedirect() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                        HttpStatus.SC_MOVED_TEMPORARILY)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testPostRedirectSeeOther() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/echo", HttpStatus.SC_SEE_OTHER)));
        server.registerHandler("/echo/*", new EchoHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testRelativeRedirect() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.startsWith("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/random/100");

                    }
                    return null;
                }));        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testRelativeRedirect2() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/random/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "100");

                    }
                    return null;
                }));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testRejectBogusRedirectLocation() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "xxx://bogus");

                    }
                    return null;
                }));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
        final HttpGet httpget = new HttpGet("/oldlocation");

        final ClientProtocolException exception = Assertions.assertThrows(ClientProtocolException.class, () ->
                client.execute(target, httpget, response -> null));
        assertThat(exception.getCause(), CoreMatchers.instanceOf(HttpException.class));
    }

    @Test
    public void testRejectInvalidRedirectLocation() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/newlocation/?p=I have spaces");

                    }
                    return null;
                }));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
        final HttpGet httpget = new HttpGet("/oldlocation");

        final ClientProtocolException exception = Assertions.assertThrows(ClientProtocolException.class, () ->
                client.execute(target, httpget, response -> null));
        assertThat(exception.getCause(), CoreMatchers.instanceOf(ProtocolException.class));
    }

    @Test
    public void testRedirectWithCookie() throws Exception {
         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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
    public void testDefaultHeadersRedirect() throws Exception {
        final CloseableHttpClient client = startClient(builder -> builder
                .setDefaultHeaders(Collections.singletonList(new BasicHeader(HttpHeaders.USER_AGENT, "my-test-client")))
        );

         final ClassicTestServer server = startServer(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)));
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

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
    public void testCompressionHeaderRedirect() throws Exception {
        final Queue<String> values = new ConcurrentLinkedQueue<>();
         final ClassicTestServer server = startServer(null, new Decorator<HttpServerRequestHandler>() {

            @Override
            public HttpServerRequestHandler decorate(final HttpServerRequestHandler requestHandler) {
                return new RedirectingDecorator(
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

                };
            }

        });
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
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

}
