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

package org.apache.http.client.protocol;

import java.util.List;

import junit.framework.Assert;

import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.SM;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BestMatchSpec;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;

public class TestResponseProcessCookies {

    private CookieOrigin cookieOrigin;
    private CookieSpec cookieSpec;
    private CookieStore cookieStore;

    @Before
    public void setUp() throws Exception {
        this.cookieOrigin = new CookieOrigin("localhost", 80, "/", false);
        this.cookieSpec = new BestMatchSpec();
        this.cookieStore = new BasicCookieStore();
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResponseParameterCheck() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(null, context);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testContextParameterCheck() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, null);
    }

    @Test
    public void testParseCookies() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(SM.SET_COOKIE, "name1=value1");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(ClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);

        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, context);

        List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        Cookie cookie = cookies.get(0);
        Assert.assertEquals(0, cookie.getVersion());
        Assert.assertEquals("name1", cookie.getName());
        Assert.assertEquals("value1", cookie.getValue());
        Assert.assertEquals("localhost", cookie.getDomain());
        Assert.assertEquals("/", cookie.getPath());
    }

    @Test
    public void testNoCookieOrigin() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(SM.SET_COOKIE, "name1=value1");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_ORIGIN, null);
        context.setAttribute(ClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);

        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, context);

        List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(0, cookies.size());
    }

    @Test
    public void testNoCookieSpec() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(SM.SET_COOKIE, "name1=value1");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(ClientContext.COOKIE_SPEC, null);
        context.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);

        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, context);

        List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(0, cookies.size());
    }

    @Test
    public void testNoCookieStore() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(SM.SET_COOKIE, "name1=value1");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(ClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(ClientContext.COOKIE_STORE, null);

        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, context);

        List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(0, cookies.size());
    }

    @Test
    public void testSetCookie2OverrideSetCookie() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(SM.SET_COOKIE, "name1=value1");
        response.addHeader(SM.SET_COOKIE2, "name1=value2; Version=1");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(ClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);

        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, context);

        List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        Cookie cookie = cookies.get(0);
        Assert.assertEquals(1, cookie.getVersion());
        Assert.assertEquals("name1", cookie.getName());
        Assert.assertEquals("value2", cookie.getValue());
        Assert.assertEquals("localhost.local", cookie.getDomain());
        Assert.assertEquals("/", cookie.getPath());
    }

    @Test
    public void testInvalidHeader() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(SM.SET_COOKIE2, "name=value; Version=crap");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(ClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);

        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, context);

        List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertTrue(cookies.isEmpty());
    }

    @Test
    public void testCookieRejected() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(SM.SET_COOKIE2, "name=value; Domain=www.somedomain.com; Version=1");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_ORIGIN, this.cookieOrigin);
        context.setAttribute(ClientContext.COOKIE_SPEC, this.cookieSpec);
        context.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);

        HttpResponseInterceptor interceptor = new ResponseProcessCookies();
        interceptor.process(response, context);

        List<Cookie> cookies = this.cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertTrue(cookies.isEmpty());
    }

}
