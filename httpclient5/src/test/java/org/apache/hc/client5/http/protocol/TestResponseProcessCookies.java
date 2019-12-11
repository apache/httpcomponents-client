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

import java.util.List;

import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieSpec;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.cookie.RFC6265LaxSpec;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestResponseProcessCookies {

    private CookieOrigin cookieOrigin;
    private CookieSpec cookieSpec;
    private CookieStore cookieStore;

    @Before
    public void setUp() throws Exception {
        this.cookieOrigin = new CookieOrigin("localhost", 80, "/", false);
        this.cookieSpec = new RFC6265LaxSpec();
        this.cookieStore = new BasicCookieStore();
    }

    @Test(expected=NullPointerException.class)
    public void testResponseParameterCheck() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(null, null, context);
    }

    @Test(expected=NullPointerException.class)
    public void testContextParameterCheck() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, null, null);
    }

    @Test
    public void testParseCookies() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(HttpClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);

        final HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, null, context);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assert.assertEquals("name1", cookie.getName());
        Assert.assertEquals("value1", cookie.getValue());
        Assert.assertEquals("localhost", cookie.getDomain());
        Assert.assertEquals("/", cookie.getPath());
    }

    @Test
    public void testNoCookieOrigin() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.COOKIE_ORIGIN, null);
        context.setAttribute(HttpClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);

        final HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, null, context);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(0, cookies.size());
    }

    @Test
    public void testNoCookieSpec() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(HttpClientContext.COOKIE_SPEC, null);
        context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);

        final HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, null, context);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(0, cookies.size());
    }

    @Test
    public void testNoCookieStore() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(HttpClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(HttpClientContext.COOKIE_STORE, null);

        final HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, null, context);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(0, cookies.size());
    }

}
