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

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.testing.OldPathRedirectResolver;
import org.apache.hc.client5.testing.classic.RedirectingDecorator;
import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

/**
 * Redirection test cases.
 */
public class TestRedirects extends LocalServerTestBase {

    @Test
    public void testBasicRedirect300() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES)));

        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("/oldlocation/100");
        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/oldlocation/100").build(),
                    reqWrapper.getUri());

            final RedirectLocations redirects = context.getRedirectLocations();
            Assert.assertNotNull(redirects);
            Assert.assertEquals(0, redirects.size());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testBasicRedirect300NoKeepAlive() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES,
                        Redirect.ConnControl.CLOSE)));

        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("/oldlocation/100");
        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/oldlocation/100").build(),
                    reqWrapper.getUri());

            final RedirectLocations redirects = context.getRedirectLocations();
            Assert.assertNotNull(redirects);
            Assert.assertEquals(0, redirects.size());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testBasicRedirect301() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY)));

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                    reqWrapper.getUri());

            final RedirectLocations redirects = context.getRedirectLocations();
            Assert.assertNotNull(redirects);
            Assert.assertEquals(1, redirects.size());

            final URI redirect = new URIBuilder().setHttpHost(target).setPath("/random/100").build();
            Assert.assertTrue(redirects.contains(redirect));

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testBasicRedirect302() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)));

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/50");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/50").build(),
                    reqWrapper.getUri());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testBasicRedirect302NoLocation() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.startsWith("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, null);
                    }
                    return null;
                }));

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getCode());
            Assert.assertEquals("/oldlocation/100", reqWrapper.getRequestUri());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testBasicRedirect303() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_SEE_OTHER)));

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/123");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/123").build(),
                    reqWrapper.getUri());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testBasicRedirect304() throws Exception {
        this.server.registerHandler("/oldlocation/*", (request, response, context) -> {
            response.setCode(HttpStatus.SC_NOT_MODIFIED);
            response.addHeader(HttpHeaders.LOCATION, "/random/100");
        });

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/stuff");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/oldlocation/stuff").build(),
                    reqWrapper.getUri());

            final RedirectLocations redirects = context.getRedirectLocations();
            Assert.assertNotNull(redirects);
            Assert.assertEquals(0, redirects.size());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testBasicRedirect305() throws Exception {
        this.server.registerHandler("/oldlocation/*", (request, response, context) -> {
            response.setCode(HttpStatus.SC_USE_PROXY);
            response.addHeader(HttpHeaders.LOCATION, "/random/100");
        });

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/stuff");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_USE_PROXY, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/oldlocation/stuff").build(),
                    reqWrapper.getUri());

            final RedirectLocations redirects = context.getRedirectLocations();
            Assert.assertNotNull(redirects);
            Assert.assertEquals(0, redirects.size());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testBasicRedirect307() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_TEMPORARY_REDIRECT)));

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/123");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/123").build(),
                    reqWrapper.getUri());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testMaxRedirectCheck() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                        HttpStatus.SC_MOVED_TEMPORARILY)));

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(5)
                .build();

        final HttpGet httpget = new HttpGet("/circular-oldlocation/123");
        httpget.setConfig(config);
        final ClientProtocolException exception = Assert.assertThrows(ClientProtocolException.class, () ->
                this.httpclient.execute(target, httpget));
        Assert.assertTrue(exception.getCause() instanceof RedirectException);
    }

    @Test
    public void testCircularRedirect() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/circular-oldlocation/", "/circular-oldlocation/",
                        HttpStatus.SC_MOVED_TEMPORARILY)));

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(false)
                .build();

        final HttpGet httpget = new HttpGet("/circular-oldlocation/123");
        httpget.setConfig(config);
        final ClientProtocolException exception = Assert.assertThrows(ClientProtocolException.class, () ->
                this.httpclient.execute(target, httpget));
        Assert.assertTrue(exception.getCause() instanceof CircularRedirectException);
    }

    @Test
    public void testPostRedirectSeeOther() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/echo", HttpStatus.SC_SEE_OTHER)));

        final HttpClientContext context = HttpClientContext.create();

        final HttpPost httppost = new HttpPost("/oldlocation/stuff");
        httppost.setEntity(new StringEntity("stuff"));

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httppost, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/echo/stuff").build(),
                    reqWrapper.getUri());
            Assert.assertEquals("GET", reqWrapper.getMethod());

            EntityUtils.consume(response.getEntity());
        }

    }

    @Test
    public void testRelativeRedirect() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.startsWith("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/random/100");

                    }
                    return null;
                }));
        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/stuff");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                    reqWrapper.getUri());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testRelativeRedirect2() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/random/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "100");

                    }
                    return null;
                }));

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/random/oldlocation");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                    reqWrapper.getUri());

            EntityUtils.consume(response.getEntity());
        }

    }

    @Test
    public void testRejectBogusRedirectLocation() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "xxx://bogus");

                    }
                    return null;
                }));

        final HttpGet httpget = new HttpGet("/oldlocation");

        final ClientProtocolException exception = Assert.assertThrows(ClientProtocolException.class, () ->
                this.httpclient.execute(target, httpget));
        MatcherAssert.assertThat(exception.getCause(), CoreMatchers.instanceOf(HttpException.class));
    }

    @Test
    public void testRejectInvalidRedirectLocation() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                requestUri -> {
                    final String path = requestUri.getPath();
                    if (path.equals("/oldlocation")) {
                        return new Redirect(HttpStatus.SC_MOVED_TEMPORARILY, "/newlocation/?p=I have spaces");

                    }
                    return null;
                }));

        final HttpGet httpget = new HttpGet("/oldlocation");

        final ClientProtocolException exception = Assert.assertThrows(ClientProtocolException.class, () ->
                this.httpclient.execute(target, httpget));
        MatcherAssert.assertThat(exception.getCause(), CoreMatchers.instanceOf(ProtocolException.class));
    }

    @Test
    public void testRedirectWithCookie() throws Exception {
        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)));

        final CookieStore cookieStore = new BasicCookieStore();

        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");

        cookieStore.addCookie(cookie);

        final HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);
        final HttpGet httpget = new HttpGet("/oldlocation/100");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                    reqWrapper.getUri());

            final Header[] headers = reqWrapper.getHeaders("Cookie");
            Assert.assertEquals("There can only be one (cookie)", 1, headers.length);

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testDefaultHeadersRedirect() throws Exception {
        this.clientBuilder.setDefaultHeaders(Collections.singletonList(new BasicHeader(HttpHeaders.USER_AGENT, "my-test-client")));

        final HttpHost target = start(null, requestHandler -> new RedirectingDecorator(
                requestHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_TEMPORARILY)));

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                    reqWrapper.getUri());

            final Header header = reqWrapper.getFirstHeader(HttpHeaders.USER_AGENT);
            Assert.assertEquals("my-test-client", header.getValue());

            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    public void testCompressionHeaderRedirect() throws Exception {
        final Queue<String> values = new ConcurrentLinkedQueue<>();
        final HttpHost target = start(null, new Decorator<HttpServerRequestHandler>() {

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

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/100");

        try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context)) {
            final HttpRequest reqWrapper = context.getRequest();

            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assert.assertEquals(new URIBuilder().setHttpHost(target).setPath("/random/100").build(),
                    reqWrapper.getUri());

            EntityUtils.consume(response.getEntity());
        }

        MatcherAssert.assertThat(values.poll(), CoreMatchers.equalTo("gzip, x-gzip, deflate"));
        MatcherAssert.assertThat(values.poll(), CoreMatchers.equalTo("gzip, x-gzip, deflate"));
        MatcherAssert.assertThat(values.poll(), CoreMatchers.nullValue());
    }

}
