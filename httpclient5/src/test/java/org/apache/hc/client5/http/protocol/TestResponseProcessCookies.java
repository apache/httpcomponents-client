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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestResponseProcessCookies {

    private CookieOrigin cookieOrigin;
    private CookieSpec cookieSpec;
    private CookieStore cookieStore;

    @BeforeEach
    void setUp() {
        this.cookieOrigin = new CookieOrigin("localhost", 80, "/", false);
        this.cookieSpec = new RFC6265LaxSpec();
        this.cookieStore = new BasicCookieStore();
    }

    @Test
    void testResponseParameterCheck() {
        final HttpClientContext context = HttpClientContext.create();
        final HttpResponseInterceptor interceptor = ResponseProcessCookies.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, context));
    }

    @Test
    void testContextParameterCheck() {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final HttpResponseInterceptor interceptor = ResponseProcessCookies.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(response, null, null));
    }

    @Test
    void testParseCookies() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1");

        final HttpClientContext context = HttpClientContext.create();
        context.setCookieOrigin(this.cookieOrigin);
        context.setCookieSpec(this.cookieSpec);
        context.setCookieStore(this.cookieStore);

        final HttpResponseInterceptor interceptor = ResponseProcessCookies.INSTANCE;
        interceptor.process(response, null, context);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assertions.assertNotNull(cookies);
        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertEquals("name1", cookie.getName());
        Assertions.assertEquals("value1", cookie.getValue());
        Assertions.assertEquals("localhost", cookie.getDomain());
        Assertions.assertEquals("/", cookie.getPath());
    }

    @Test
    void testNoCookieOrigin() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1");

        final HttpClientContext context = HttpClientContext.create();
        context.setCookieOrigin(null);
        context.setCookieSpec(this.cookieSpec);
        context.setCookieStore(this.cookieStore);

        final HttpResponseInterceptor interceptor = ResponseProcessCookies.INSTANCE;
        interceptor.process(response, null, context);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assertions.assertNotNull(cookies);
        Assertions.assertEquals(0, cookies.size());
    }

    @Test
    void testNoCookieSpec() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1");

        final HttpClientContext context = HttpClientContext.create();
        context.setCookieOrigin(this.cookieOrigin);
        context.setCookieSpec(null);
        context.setCookieStore(this.cookieStore);

        final HttpResponseInterceptor interceptor = ResponseProcessCookies.INSTANCE;
        interceptor.process(response, null, context);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assertions.assertNotNull(cookies);
        Assertions.assertEquals(0, cookies.size());
    }

    @Test
    void testNoCookieStore() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1");

        final HttpClientContext context = HttpClientContext.create();
        context.setCookieOrigin(this.cookieOrigin);
        context.setCookieSpec(this.cookieSpec);
        context.setCookieStore(null);

        final HttpResponseInterceptor interceptor = ResponseProcessCookies.INSTANCE;
        interceptor.process(response, null, context);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assertions.assertNotNull(cookies);
        Assertions.assertEquals(0, cookies.size());
    }

    @Test
    void testInsecureOriginCannotSetSecureCookie() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Set-Cookie", "name1=value1; Secure");

        final HttpClientContext context = HttpClientContext.create();
        context.setCookieOrigin(this.cookieOrigin);
        context.setCookieSpec(this.cookieSpec);
        context.setCookieStore(this.cookieStore);

        final HttpResponseInterceptor interceptor = ResponseProcessCookies.INSTANCE;
        interceptor.process(response, null, context);

        Assertions.assertEquals(0, this.cookieStore.getCookies().size());
    }

    @Test
    void testInsecureOriginCannotOverwriteSecureCookie() throws Exception {
        final HttpResponseInterceptor interceptor = ResponseProcessCookies.INSTANCE;

        final HttpResponse secureResponse = new BasicHttpResponse(200, "OK");
        secureResponse.addHeader("Set-Cookie", "session=secure-value; Secure; Path=/");
        final HttpClientContext secureContext = HttpClientContext.create();
        secureContext.setCookieOrigin(new CookieOrigin("www.example.com", 443, "/", true));
        secureContext.setCookieSpec(this.cookieSpec);
        secureContext.setCookieStore(this.cookieStore);
        interceptor.process(secureResponse, null, secureContext);

        final HttpResponse insecureResponse = new BasicHttpResponse(200, "OK");
        insecureResponse.addHeader("Set-Cookie", "session=insecure-value; Path=/");
        insecureResponse.addHeader("Set-Cookie", "session=insecure-value; Domain=example.com; Path=/sub");
        insecureResponse.addHeader("Set-Cookie", "other=insecure-value; Path=/");
        final HttpClientContext insecureContext = HttpClientContext.create();
        insecureContext.setCookieOrigin(new CookieOrigin("www.example.com", 80, "/", false));
        insecureContext.setCookieSpec(this.cookieSpec);
        insecureContext.setCookieStore(this.cookieStore);
        interceptor.process(insecureResponse, null, insecureContext);

        final List<Cookie> cookies = this.cookieStore.getCookies();
        Assertions.assertEquals(2, cookies.size());
        for (final Cookie cookie : cookies) {
            if (cookie.getName().equals("session")) {
                Assertions.assertEquals("secure-value", cookie.getValue());
                Assertions.assertTrue(cookie.isSecure());
            } else {
                Assertions.assertEquals("other", cookie.getName());
            }
        }

        final HttpResponse secureUpdate = new BasicHttpResponse(200, "OK");
        secureUpdate.addHeader("Set-Cookie", "session=new-secure-value; Secure; Path=/");
        interceptor.process(secureUpdate, null, secureContext);
        Assertions.assertTrue(this.cookieStore.getCookies().stream().anyMatch(c ->
                c.getName().equals("session") && c.getValue().equals("new-secure-value")));
    }

}
