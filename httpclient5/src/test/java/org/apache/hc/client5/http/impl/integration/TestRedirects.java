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
package org.apache.hc.client5.http.impl.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.client5.http.protocol.CircularRedirectException;
import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectException;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.HttpPost;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.UriHttpRequestHandlerMapper;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * Redirection test cases.
 */
public class TestRedirects extends LocalServerTestBase {

    private static class BasicRedirectService implements HttpRequestHandler {

        private final int statuscode;

        public BasicRedirectService(final int statuscode) {
            super();
            this.statuscode = statuscode > 0 ? statuscode : HttpStatus.SC_MOVED_TEMPORARILY;
        }

        public BasicRedirectService() {
            this(-1);
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final HttpConnection conn = (HttpConnection) context.getAttribute(HttpCoreContext.HTTP_CONNECTION);
            final InetSocketAddress socketAddress = (InetSocketAddress) conn.getLocalAddress();
            String localhost = socketAddress.getHostName();
            if (localhost.equals("127.0.0.1")) {
                localhost = "localhost";
            }
            final int port = socketAddress.getPort();
            final String uri = request.getRequestUri();
            if (uri.equals("/oldlocation/")) {
                response.setCode(this.statuscode);
                response.addHeader(new BasicHeader("Location",
                        "http://" + localhost + ":" + port + "/newlocation/"));
                response.addHeader(new BasicHeader("Connection", "close"));
            } else if (uri.equals("/newlocation/")) {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setCode(HttpStatus.SC_NOT_FOUND);
            }
        }

    }

    private static class CircularRedirectService implements HttpRequestHandler {

        public CircularRedirectService() {
            super();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String uri = request.getRequestUri();
            if (uri.startsWith("/circular-oldlocation")) {
                response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/circular-location2"));
            } else if (uri.startsWith("/circular-location2")) {
                response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/circular-oldlocation"));
            } else {
                response.setCode(HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    private static class RelativeRedirectService implements HttpRequestHandler {

        public RelativeRedirectService() {
            super();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String uri = request.getRequestUri();
            if (uri.equals("/oldlocation/")) {
                response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/relativelocation/"));
            } else if (uri.equals("/relativelocation/")) {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setCode(HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    private static class RelativeRedirectService2 implements HttpRequestHandler {

        public RelativeRedirectService2() {
            super();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String uri = request.getRequestUri();
            if (uri.equals("/test/oldlocation")) {
                response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "relativelocation"));
            } else if (uri.equals("/test/relativelocation")) {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setCode(HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    private static class RomeRedirectService implements HttpRequestHandler {

        public RomeRedirectService() {
            super();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String uri = request.getRequestUri();
            if (uri.equals("/rome")) {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/rome"));
            }
        }
    }

    private static class BogusRedirectService implements HttpRequestHandler {
        private final String url;

        public BogusRedirectService(final String redirectUrl) {
            super();
            this.url = redirectUrl;
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String uri = request.getRequestUri();
            if (uri.equals("/oldlocation/")) {
                response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", url));
            } else if (uri.equals("/relativelocation/")) {
                response.setCode(HttpStatus.SC_OK);
                final StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setCode(HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    @Test
    public void testBasicRedirect300() throws Exception {
        this.serverBootstrap.registerHandler("*",
                new BasicRedirectService(HttpStatus.SC_MULTIPLE_CHOICES));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/oldlocation/"), reqWrapper.getUri());

        final List<URI> redirects = context.getRedirectLocations();
        Assert.assertNull(redirects);
    }

    @Test
    public void testBasicRedirect301() throws Exception {
        this.serverBootstrap.registerHandler("*",
                new BasicRedirectService(HttpStatus.SC_MOVED_PERMANENTLY));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/newlocation/"), reqWrapper.getUri());

        final List<URI> redirects = context.getRedirectLocations();
        Assert.assertNotNull(redirects);
        Assert.assertEquals(1, redirects.size());

        final URI redirect = URIUtils.rewriteURI(new URI("/newlocation/"), target);
        Assert.assertTrue(redirects.contains(redirect));
    }

    @Test
    public void testBasicRedirect302() throws Exception {
        this.serverBootstrap.registerHandler("*",
                new BasicRedirectService(HttpStatus.SC_MOVED_TEMPORARILY));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/newlocation/"), reqWrapper.getUri());
    }

    @Test
    public void testBasicRedirect302NoLocation() throws Exception {
        this.serverBootstrap.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
            }

        });

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getCode());
        Assert.assertEquals("/oldlocation/", reqWrapper.getRequestUri());
    }

    @Test
    public void testBasicRedirect303() throws Exception {
        this.serverBootstrap.registerHandler("*",
                new BasicRedirectService(HttpStatus.SC_SEE_OTHER));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/newlocation/"), reqWrapper.getUri());
    }

    @Test
    public void testBasicRedirect304() throws Exception {
        this.serverBootstrap.registerHandler("*",
                new BasicRedirectService(HttpStatus.SC_NOT_MODIFIED));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/oldlocation/"), reqWrapper.getUri());
    }

    @Test
    public void testBasicRedirect305() throws Exception {
        this.serverBootstrap.registerHandler("*",
                new BasicRedirectService(HttpStatus.SC_USE_PROXY));
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_USE_PROXY, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/oldlocation/"), reqWrapper.getUri());
    }

    @Test
    public void testBasicRedirect307() throws Exception {
        this.serverBootstrap.registerHandler("*",
                new BasicRedirectService(HttpStatus.SC_TEMPORARY_REDIRECT));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/newlocation/"), reqWrapper.getUri());
    }

    @Test(expected=ClientProtocolException.class)
    public void testMaxRedirectCheck() throws Exception {
        this.serverBootstrap.registerHandler("*", new CircularRedirectService());

        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom()
            .setCircularRedirectsAllowed(true)
            .setMaxRedirects(5)
            .build();

        final HttpGet httpget = new HttpGet("/circular-oldlocation/");
        httpget.setConfig(config);
        try {
            this.httpclient.execute(target, httpget);
        } catch (final ClientProtocolException e) {
            Assert.assertTrue(e.getCause() instanceof RedirectException);
            throw e;
        }
    }

    @Test(expected=ClientProtocolException.class)
    public void testCircularRedirect() throws Exception {
        this.serverBootstrap.registerHandler("*", new CircularRedirectService());

        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom()
            .setCircularRedirectsAllowed(false)
            .build();

        final HttpGet httpget = new HttpGet("/circular-oldlocation/");
        httpget.setConfig(config);
        try {
            this.httpclient.execute(target, httpget);
        } catch (final ClientProtocolException e) {
            Assert.assertTrue(e.getCause() instanceof CircularRedirectException);
            throw e;
        }
    }

    @Test
    public void testRepeatRequest() throws Exception {
        this.serverBootstrap.registerHandler("*", new RomeRedirectService());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet first = new HttpGet("/rome");

        EntityUtils.consume(this.httpclient.execute(target, first, context).getEntity());

        final HttpGet second = new HttpGet("/rome");

        final ClassicHttpResponse response = this.httpclient.execute(target, second, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/rome"), reqWrapper.getUri());
    }

    @Test
    public void testRepeatRequestRedirect() throws Exception {
        this.serverBootstrap.registerHandler("*", new RomeRedirectService());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet first = new HttpGet("/lille");
        final ClassicHttpResponse response1 = this.httpclient.execute(target, first, context);
        EntityUtils.consume(response1.getEntity());

        final HttpGet second = new HttpGet("/lille");

        final ClassicHttpResponse response2 = this.httpclient.execute(target, second, context);
        EntityUtils.consume(response2.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertEquals(URIUtils.create(target, "/rome"), reqWrapper.getUri());
    }

    @Test
    public void testDifferentRequestSameRedirect() throws Exception {
        this.serverBootstrap.registerHandler("*", new RomeRedirectService());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet first = new HttpGet("/alian");

        final ClassicHttpResponse response1 = this.httpclient.execute(target, first, context);
        EntityUtils.consume(response1.getEntity());

        final HttpGet second = new HttpGet("/lille");

        final ClassicHttpResponse response2 = this.httpclient.execute(target, second, context);
        EntityUtils.consume(response2.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertEquals(URIUtils.create(target, "/rome"), reqWrapper.getUri());
    }

    @Test
    public void testPostRedirectSeeOther() throws Exception {
        this.serverBootstrap.registerHandler("*", new BasicRedirectService(HttpStatus.SC_SEE_OTHER));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpPost httppost = new HttpPost("/oldlocation/");
        httppost.setEntity(new StringEntity("stuff"));

        final ClassicHttpResponse response = this.httpclient.execute(target, httppost, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/newlocation/"), reqWrapper.getUri());
        Assert.assertEquals("GET", reqWrapper.getMethod());
    }

    @Test
    public void testRelativeRedirect() throws Exception {
        this.serverBootstrap.registerHandler("*", new RelativeRedirectService());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/relativelocation/"), reqWrapper.getUri());
    }

    @Test
    public void testRelativeRedirect2() throws Exception {
        this.serverBootstrap.registerHandler("*", new RelativeRedirectService2());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/test/oldlocation");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/test/relativelocation"), reqWrapper.getUri());
    }

    @Test(expected=ClientProtocolException.class)
    public void testRejectBogusRedirectLocation() throws Exception {
        this.serverBootstrap.registerHandler("*", new BogusRedirectService("xxx://bogus"));

        final HttpHost target = start();

        final HttpGet httpget = new HttpGet("/oldlocation/");

        try {
            this.httpclient.execute(target, httpget);
        } catch (final ClientProtocolException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertTrue(cause instanceof HttpException);
            throw ex;
        }
    }

    @Test(expected=ClientProtocolException.class)
    public void testRejectInvalidRedirectLocation() throws Exception {
        final UriHttpRequestHandlerMapper reqistry = new UriHttpRequestHandlerMapper();
        this.serverBootstrap.setHandlerMapper(reqistry);

        final HttpHost target = start();

        reqistry.register("*",
                new BogusRedirectService("http://" + target.toHostString() +
                        "/newlocation/?p=I have spaces"));

        final HttpGet httpget = new HttpGet("/oldlocation/");

        try {
            this.httpclient.execute(target, httpget);
        } catch (final ClientProtocolException e) {
            Assert.assertTrue(e.getCause() instanceof ProtocolException);
            throw e;
        }
    }

    @Test
    public void testRedirectWithCookie() throws Exception {
        this.serverBootstrap.registerHandler("*", new BasicRedirectService());

        final HttpHost target = start();

        final CookieStore cookieStore = new BasicCookieStore();

        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");

        cookieStore.addCookie(cookie);

        final HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);
        final HttpGet httpget = new HttpGet("/oldlocation/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/newlocation/"), reqWrapper.getUri());

        final Header[] headers = reqWrapper.getHeaders("Cookie");
        Assert.assertEquals("There can only be one (cookie)", 1, headers.length);
    }

    @Test
    public void testDefaultHeadersRedirect() throws Exception {
        this.clientBuilder.setDefaultHeaders(Arrays.asList(new BasicHeader(HttpHeaders.USER_AGENT, "my-test-client")));

        this.serverBootstrap.registerHandler("*", new BasicRedirectService());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/oldlocation/");


        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals(URIUtils.create(target, "/newlocation/"), reqWrapper.getUri());

        final Header header = reqWrapper.getFirstHeader(HttpHeaders.USER_AGENT);
        Assert.assertEquals("my-test-client", header.getValue());
    }

}
