/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.client.integration;

import java.io.IOException;
import java.util.Arrays;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.RedirectException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.SM;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Redirection test cases.
 */
public class TestRedirects extends IntegrationTestBase {

    @Before
    public void setUp() throws Exception {
        startServer();
        this.httpclient = HttpClients.createDefault();
    }

    private static class BasicRedirectService implements HttpRequestHandler {

        private final int statuscode;

        public BasicRedirectService(int statuscode) {
            super();
            this.statuscode = statuscode > 0 ? statuscode : HttpStatus.SC_MOVED_TEMPORARILY;
        }

        public BasicRedirectService() {
            this(-1);
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            HttpInetConnection conn = (HttpInetConnection) context.getAttribute(ExecutionContext.HTTP_CONNECTION);
            String localhost = conn.getLocalAddress().getHostName();
            int port = conn.getLocalPort();
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            String uri = request.getRequestLine().getUri();
            if (uri.equals("/oldlocation/")) {
                response.setStatusLine(ver, this.statuscode);
                response.addHeader(new BasicHeader("Location",
                        "http://" + localhost + ":" + port + "/newlocation/"));
                response.addHeader(new BasicHeader("Connection", "close"));
            } else if (uri.equals("/newlocation/")) {
                response.setStatusLine(ver, HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
            }
        }

    }

    private static class CircularRedirectService implements HttpRequestHandler {

        public CircularRedirectService() {
            super();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            String uri = request.getRequestLine().getUri();
            if (uri.startsWith("/circular-oldlocation")) {
                response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/circular-location2"));
            } else if (uri.startsWith("/circular-location2")) {
                response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/circular-oldlocation"));
            } else {
                response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    private static class RelativeRedirectService implements HttpRequestHandler {

        public RelativeRedirectService() {
            super();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            String uri = request.getRequestLine().getUri();
            if (uri.equals("/oldlocation/")) {
                response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/relativelocation/"));
            } else if (uri.equals("/relativelocation/")) {
                response.setStatusLine(ver, HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    private static class RelativeRedirectService2 implements HttpRequestHandler {

        public RelativeRedirectService2() {
            super();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            String uri = request.getRequestLine().getUri();
            if (uri.equals("/test/oldlocation")) {
                response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "relativelocation"));
            } else if (uri.equals("/test/relativelocation")) {
                response.setStatusLine(ver, HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    private static class BogusRedirectService implements HttpRequestHandler {
        private String url;

        public BogusRedirectService(String redirectUrl) {
            super();
            this.url = redirectUrl;
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            String uri = request.getRequestLine().getUri();
            if (uri.equals("/oldlocation/")) {
                response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", url));
            } else if (uri.equals("/relativelocation/")) {
                response.setStatusLine(ver, HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    @Test
    public void testBasicRedirect300() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*",
                new BasicRedirectService(HttpStatus.SC_MULTIPLE_CHOICES));

        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/oldlocation/", reqWrapper.getRequestLine().getUri());
    }

    @Test
    public void testBasicRedirect301() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*",
                new BasicRedirectService(HttpStatus.SC_MOVED_PERMANENTLY));

        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost host = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals(target, host);
    }

    @Test
    public void testBasicRedirect302() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*",
                new BasicRedirectService(HttpStatus.SC_MOVED_TEMPORARILY));

        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost host = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals(target, host);
    }

    @Test
    public void testBasicRedirect302NoLocation() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
            }

        });

        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost host = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        Assert.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/oldlocation/", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals(target, host);
    }

    @Test
    public void testBasicRedirect303() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*",
                new BasicRedirectService(HttpStatus.SC_SEE_OTHER));

        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost host = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals(target, host);
    }

    @Test
    public void testBasicRedirect304() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*",
                new BasicRedirectService(HttpStatus.SC_NOT_MODIFIED));

        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/oldlocation/", reqWrapper.getRequestLine().getUri());
    }

    @Test
    public void testBasicRedirect305() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*",
                new BasicRedirectService(HttpStatus.SC_USE_PROXY));
        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_USE_PROXY, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/oldlocation/", reqWrapper.getRequestLine().getUri());
    }

    @Test
    public void testBasicRedirect307() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*",
                new BasicRedirectService(HttpStatus.SC_TEMPORARY_REDIRECT));

        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost host = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals(target, host);
    }

    @Test(expected=ClientProtocolException.class)
    public void testMaxRedirectCheck() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new CircularRedirectService());

        RequestConfig config = RequestConfig.custom()
            .setCircularRedirectsAllowed(true)
            .setMaxRedirects(5)
            .build();

        HttpGet httpget = new HttpGet("/circular-oldlocation/");
        httpget.setConfig(config);
        try {
            this.httpclient.execute(target, httpget);
        } catch (ClientProtocolException e) {
            Assert.assertTrue(e.getCause() instanceof RedirectException);
            throw e;
        }
    }

    @Test(expected=ClientProtocolException.class)
    public void testCircularRedirect() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new CircularRedirectService());

        RequestConfig config = RequestConfig.custom()
            .setCircularRedirectsAllowed(false)
            .build();

        HttpGet httpget = new HttpGet("/circular-oldlocation/");
        httpget.setConfig(config);
        try {
            this.httpclient.execute(target, httpget);
        } catch (ClientProtocolException e) {
            Assert.assertTrue(e.getCause() instanceof CircularRedirectException);
            throw e;
        }
    }

    @Test
    public void testPostNoRedirect() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new BasicRedirectService());

        HttpContext context = new BasicHttpContext();

        HttpPost httppost = new HttpPost("/oldlocation/");
        httppost.setEntity(new StringEntity("stuff"));

        HttpResponse response = this.httpclient.execute(target, httppost, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/oldlocation/", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals("POST", reqWrapper.getRequestLine().getMethod());
    }

    @Test
    public void testPostRedirectSeeOther() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new BasicRedirectService(HttpStatus.SC_SEE_OTHER));

        HttpContext context = new BasicHttpContext();

        HttpPost httppost = new HttpPost("/oldlocation/");
        httppost.setEntity(new StringEntity("stuff"));

        HttpResponse response = this.httpclient.execute(target, httppost, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals("GET", reqWrapper.getRequestLine().getMethod());
    }

    @Test
    public void testRelativeRedirect() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new RelativeRedirectService());

        HttpContext context = new BasicHttpContext();

        RequestConfig config = RequestConfig.custom().setRelativeRedirectsAllowed(true).build();
        HttpGet httpget = new HttpGet("/oldlocation/");
        httpget.setConfig(config);

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost host = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/relativelocation/", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals(host, target);
    }

    @Test
    public void testRelativeRedirect2() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new RelativeRedirectService2());

        HttpContext context = new BasicHttpContext();

        RequestConfig config = RequestConfig.custom().setRelativeRedirectsAllowed(true).build();
        HttpGet httpget = new HttpGet("/test/oldlocation");
        httpget.setConfig(config);

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost host = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/test/relativelocation", reqWrapper.getRequestLine().getUri());
        Assert.assertEquals(host, target);
    }

    @Test(expected=ClientProtocolException.class)
    public void testRejectRelativeRedirect() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new RelativeRedirectService());

        RequestConfig config = RequestConfig.custom().setRelativeRedirectsAllowed(false).build();
        HttpGet httpget = new HttpGet("/oldlocation/");
        httpget.setConfig(config);
        try {
            this.httpclient.execute(target, httpget);
        } catch (ClientProtocolException e) {
            Assert.assertTrue(e.getCause() instanceof ProtocolException);
            throw e;
        }
    }

    @Test(expected=IOException.class)
    public void testRejectBogusRedirectLocation() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*", new BogusRedirectService("xxx://bogus"));

        HttpGet httpget = new HttpGet("/oldlocation/");

        this.httpclient.execute(target, httpget);
    }

    @Test(expected=ClientProtocolException.class)
    public void testRejectInvalidRedirectLocation() throws Exception {
        HttpHost target = getServerHttp();
        this.localServer.register("*",
                new BogusRedirectService("http://" + target.toHostString() +
                        "/newlocation/?p=I have spaces"));

        HttpGet httpget = new HttpGet("/oldlocation/");

        try {
            this.httpclient.execute(target, httpget);
        } catch (ClientProtocolException e) {
            Assert.assertTrue(e.getCause() instanceof ProtocolException);
            throw e;
        }
    }

    @Test
    public void testRedirectWithCookie() throws Exception {
        HttpHost target = getServerHttp();

        this.localServer.register("*", new BasicRedirectService());

        CookieStore cookieStore = new BasicCookieStore();

        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");

        cookieStore.addCookie(cookie);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
        HttpGet httpget = new HttpGet("/oldlocation/");


        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());

        Header[] headers = reqWrapper.getHeaders(SM.COOKIE);
        Assert.assertEquals("There can only be one (cookie)", 1, headers.length);
    }

    @Test
    public void testDefaultHeadersRedirect() throws Exception {
        this.httpclient = HttpClients.custom()
            .setDefaultHeaders(Arrays.asList(new BasicHeader(HTTP.USER_AGENT, "my-test-client")))
            .build();
        HttpHost target = getServerHttp();

        this.localServer.register("*", new BasicRedirectService());

        HttpContext context = new BasicHttpContext();

        HttpGet httpget = new HttpGet("/oldlocation/");


        HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());

        Header header = reqWrapper.getFirstHeader(HTTP.USER_AGENT);
        Assert.assertEquals("my-test-client", header.getValue());
    }

}
