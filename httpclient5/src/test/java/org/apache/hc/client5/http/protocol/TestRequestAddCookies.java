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
package org.apache.hc.client5.http.protocol;

import java.util.Date;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo.LayerType;
import org.apache.hc.client5.http.RouteInfo.TunnelType;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieSpec;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.cookie.IgnoreCookieSpecFactory;
import org.apache.hc.client5.http.impl.cookie.RFC6265CookieSpecFactory;
import org.apache.hc.client5.http.impl.cookie.RFC6265StrictSpec;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestRequestAddCookies {

    private HttpHost target;
    private CookieStore cookieStore;
    private Lookup<CookieSpecFactory> cookieSpecRegistry;

    @Before
    public void setUp() {
        this.target = new HttpHost("localhost.local", 80);
        this.cookieStore = new BasicCookieStore();
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value1");
        cookie1.setDomain("localhost.local");
        cookie1.setPath("/");
        this.cookieStore.addCookie(cookie1);
        final BasicClientCookie cookie2 = new BasicClientCookie("name2", "value2");
        cookie2.setDomain("localhost.local");
        cookie2.setPath("/");
        this.cookieStore.addCookie(cookie2);

        this.cookieSpecRegistry = RegistryBuilder.<CookieSpecFactory>create()
            .register(StandardCookieSpec.RELAXED, new RFC6265CookieSpecFactory(
                    RFC6265CookieSpecFactory.CompatibilityLevel.RELAXED, null))
            .register(StandardCookieSpec.STRICT,  new RFC6265CookieSpecFactory(
                    RFC6265CookieSpecFactory.CompatibilityLevel.STRICT, null))
            .register(StandardCookieSpec.IGNORE, new IgnoreCookieSpecFactory())
            .build();
    }

    @Test(expected=NullPointerException.class)
    public void testRequestParameterCheck() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(null, null, context);
    }

    @Test(expected=NullPointerException.class)
    public void testContextParameterCheck() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, null);
    }

    @Test
    public void testAddCookies() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals("name1=value1; name2=value2", headers[0].getValue());

        final CookieOrigin cookieOrigin = context.getCookieOrigin();
        Assert.assertNotNull(cookieOrigin);
        Assert.assertEquals(this.target.getHostName(), cookieOrigin.getHost());
        Assert.assertEquals(this.target.getPort(), cookieOrigin.getPort());
        Assert.assertEquals("/", cookieOrigin.getPath());
        Assert.assertFalse(cookieOrigin.isSecure());
    }

    @Test
    public void testCookiesForConnectRequest() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", "www.somedomain.com");

        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertNotNull(headers);
        Assert.assertEquals(0, headers.length);
    }

    @Test
    public void testNoCookieStore() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, null);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertNotNull(headers);
        Assert.assertEquals(0, headers.length);
    }

    @Test
    public void testNoCookieSpecRegistry() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, null);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertNotNull(headers);
        Assert.assertEquals(0, headers.length);
    }

    @Test
    public void testNoHttpConnection() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, null);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertNotNull(headers);
        Assert.assertEquals(0, headers.length);
    }

    @Test
    public void testAddCookiesUsingExplicitCookieSpec() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final RequestConfig config = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.STRICT)
                .build();
        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final CookieSpec cookieSpec = context.getCookieSpec();
        Assert.assertTrue(cookieSpec instanceof RFC6265StrictSpec);

        final Header[] headers1 = request.getHeaders("Cookie");
        Assert.assertNotNull(headers1);
        Assert.assertEquals(1, headers1.length);
        Assert.assertEquals("name1=value1; name2=value2", headers1[0].getValue());
    }

    @Test
    public void testAuthScopeInvalidRequestURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "crap:");

        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);
    }

    @Test
    public void testAuthScopeRemotePortWhenDirect() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/stuff");

        this.target = new HttpHost("localhost.local");
        final HttpRoute route = new HttpRoute(new HttpHost("localhost.local", 1234), null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final CookieOrigin cookieOrigin = context.getCookieOrigin();
        Assert.assertNotNull(cookieOrigin);
        Assert.assertEquals(this.target.getHostName(), cookieOrigin.getHost());
        Assert.assertEquals(1234, cookieOrigin.getPort());
        Assert.assertEquals("/stuff", cookieOrigin.getPath());
        Assert.assertFalse(cookieOrigin.isSecure());
    }

    @Test
    public void testAuthDefaultHttpPortWhenProxy() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/stuff");

        this.target = new HttpHost("localhost.local");
        final HttpRoute route = new HttpRoute(
                new HttpHost("localhost.local", 80), null, new HttpHost("localhost", 8888), false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final CookieOrigin cookieOrigin = context.getCookieOrigin();
        Assert.assertNotNull(cookieOrigin);
        Assert.assertEquals(this.target.getHostName(), cookieOrigin.getHost());
        Assert.assertEquals(80, cookieOrigin.getPort());
        Assert.assertEquals("/stuff", cookieOrigin.getPath());
        Assert.assertFalse(cookieOrigin.isSecure());
    }

    @Test
    public void testAuthDefaultHttpsPortWhenProxy() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/stuff");

        this.target = new HttpHost("https", "localhost", -1);
        final HttpRoute route = new HttpRoute(
                new HttpHost("https", "localhost", 443), null,
                new HttpHost("http", "localhost", 8888), true, TunnelType.TUNNELLED, LayerType.LAYERED);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final CookieOrigin cookieOrigin = context.getCookieOrigin();
        Assert.assertNotNull(cookieOrigin);
        Assert.assertEquals(this.target.getHostName(), cookieOrigin.getHost());
        Assert.assertEquals(443, cookieOrigin.getPort());
        Assert.assertEquals("/stuff", cookieOrigin.getPath());
        Assert.assertTrue(cookieOrigin.isSecure());
    }

    @Test
    public void testExcludeExpiredCookies() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final BasicClientCookie cookie3 = new BasicClientCookie("name3", "value3");
        cookie3.setDomain("localhost.local");
        cookie3.setPath("/");
        cookie3.setExpiryDate(new Date(System.currentTimeMillis() + 100));
        this.cookieStore.addCookie(cookie3);

        Assert.assertEquals(3, this.cookieStore.getCookies().size());

        this.cookieStore = Mockito.spy(this.cookieStore);

        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        // Make sure the third cookie expires
        Thread.sleep(200);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals("name1=value1; name2=value2", headers[0].getValue());

        Mockito.verify(this.cookieStore, Mockito.times(1)).clearExpired(ArgumentMatchers.<Date>any());
    }

    @Test
    public void testNoMatchingCookies() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        this.cookieStore.clear();
        final BasicClientCookie cookie3 = new BasicClientCookie("name3", "value3");
        cookie3.setDomain("www.somedomain.com");
        cookie3.setPath("/");
        this.cookieStore.addCookie(cookie3);

        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertNotNull(headers);
        Assert.assertEquals(0, headers.length);
    }

    // Helper method
    private BasicClientCookie makeCookie(final String name, final String value, final String domain, final String path) {
        final BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath(path);
        return cookie;
    }

    @Test
    // Test for ordering adapted from test in Commons HC 3.1
    public void testCookieOrder() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/foobar/yada/yada");

        this.cookieStore.clear();

        cookieStore.addCookie(makeCookie("nomatch", "value", "localhost.local", "/noway"));
        cookieStore.addCookie(makeCookie("name2",   "value", "localhost.local", "/foobar/yada"));
        cookieStore.addCookie(makeCookie("name3",   "value", "localhost.local", "/foobar"));
        cookieStore.addCookie(makeCookie("name1",   "value", "localhost.local", "/foobar/yada/yada"));

        final HttpRoute route = new HttpRoute(this.target, null, false);

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);

        final HttpRequestInterceptor interceptor = new RequestAddCookies();
        interceptor.process(request, null, context);

        final Header[] headers1 = request.getHeaders("Cookie");
        Assert.assertNotNull(headers1);
        Assert.assertEquals(1, headers1.length);

        Assert.assertEquals("name1=value; name2=value; name3=value", headers1[0].getValue());
    }

}
