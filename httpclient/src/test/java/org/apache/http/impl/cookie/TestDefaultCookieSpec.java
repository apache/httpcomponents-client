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

package org.apache.http.impl.cookie;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SetCookie2;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for 'best match' cookie policy
 */
public class TestDefaultCookieSpec {

    @Test
    public void testCookieBrowserCompatParsing() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        final CookieOrigin origin = new CookieOrigin("a.b.domain.com", 80, "/", false);

        // Make sure the lenient (browser compatible) cookie parsing
        // and validation is used for Netscape style cookies
        final Header header = new BasicHeader("Set-Cookie", "name=value;path=/;domain=domain.com");

        final List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
    }

    @Test
    public void testNetscapeCookieParsing() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        final CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);

        Header header = new BasicHeader("Set-Cookie",
            "name=value; path=/; domain=.mydomain.com; expires=Thu, 01-Jan-2070 00:00:10 GMT; comment=no_comment");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        Assert.assertEquals(1, cookies.size());
        header = new BasicHeader("Set-Cookie",
            "name=value; path=/; domain=.mydomain.com; expires=Thu, 01-Jan-2070 00:00:10 GMT; version=1");
        cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        Assert.assertEquals(1, cookies.size());
    }

    @Test
    public void testCookieStandardCompliantParsing() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        final CookieOrigin origin = new CookieOrigin("a.b.domain.com", 80, "/", false);

        // Make sure the strict (RFC2965) cookie parsing
        // and validation is used for version 1 Set-Cookie2 headers
        Header header = new BasicHeader("Set-Cookie2", "name=value;path=/;domain=b.domain.com; version=1");

        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }

        // Make sure the strict (RFC2109) cookie parsing
        // and validation is used for version 1 Set-Cookie headers
        header = new BasicHeader("Set-Cookie", "name=value;path=/;domain=.b.domain.com; version=1");

        cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }

        header = new BasicHeader("Set-Cookie2", "name=value;path=/;domain=domain.com; version=1");
        try {
            cookies = cookiespec.parse(header, origin);
            cookiespec.validate(cookies.get(0), origin);
            Assert.fail("MalformedCookieException exception should have been thrown");
        } catch (final MalformedCookieException e) {
            // expected
        }
    }

    @Test
    public void testCookieStandardCompliantParsingLocalHost() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        final CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);

        final Header header = new BasicHeader("Set-Cookie", "special=\"abcdigh\"; Version=1");

        final List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            final Cookie cookie = cookies.get(i);
            cookiespec.validate(cookie, origin);
            Assert.assertEquals("localhost", cookie.getDomain());
            Assert.assertFalse(cookie instanceof SetCookie2);
        }
    }

    @Test
    public void testCookieStandardCompliantParsingLocalHost2() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        final CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);

        final Header header = new BasicHeader("Set-Cookie2", "special=\"abcdigh\"; Version=1");

        final List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            final Cookie cookie = cookies.get(i);
            cookiespec.validate(cookie, origin);
            Assert.assertEquals("localhost.local", cookie.getDomain());
            Assert.assertTrue(cookie instanceof SetCookie2);
        }
    }

    @Test
    public void testCookieBrowserCompatMatch() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        final CookieOrigin origin = new CookieOrigin("a.b.domain.com", 80, "/", false);

        // Make sure the lenient (browser compatible) cookie matching
        // is used for Netscape style cookies
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(".domain.com");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieStandardCompliantMatch() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        final CookieOrigin origin = new CookieOrigin("a.b.domain.com", 80, "/", false);

        // Make sure the strict (RFC2965) cookie matching
        // is used for version 1 cookies
        final BasicClientCookie2 cookie = new BasicClientCookie2("name", "value");
        cookie.setVersion(1);
        cookie.setDomain(".domain.com");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        Assert.assertFalse(cookiespec.match(cookie, origin));

        cookie.setDomain(".b.domain.com");

        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieBrowserCompatFormatting() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();

        // Make sure the lenient (browser compatible) cookie formatting
        // is used for Netscape style cookies
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value1");
        cookie1.setDomain(".domain.com");
        cookie1.setAttribute(ClientCookie.DOMAIN_ATTR, cookie1.getDomain());
        cookie1.setPath("/");
        cookie1.setAttribute(ClientCookie.PATH_ATTR, cookie1.getPath());

        final BasicClientCookie cookie2 = new BasicClientCookie("name2", "value2");
        cookie2.setVersion(1);
        cookie2.setDomain(".domain.com");
        cookie2.setAttribute(ClientCookie.DOMAIN_ATTR, cookie2.getDomain());
        cookie2.setPath("/");
        cookie2.setAttribute(ClientCookie.PATH_ATTR, cookie2.getPath());

        final List<Cookie> cookies = new ArrayList<Cookie>();
        cookies.add(cookie1);
        cookies.add(cookie2);

        final List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());

        final Header header = headers.get(0);
        Assert.assertEquals("name1=value1; name2=value2", header.getValue());

    }

    @Test
    public void testCookieStandardCompliantFormatting() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec(null, true);

        // Make sure the strict (RFC2965) cookie formatting
        // is used for Netscape style cookies
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value1");
        cookie1.setVersion(1);
        cookie1.setDomain(".domain.com");
        cookie1.setAttribute(ClientCookie.DOMAIN_ATTR, cookie1.getDomain());
        cookie1.setPath("/");
        cookie1.setAttribute(ClientCookie.PATH_ATTR, cookie1.getPath());

        final BasicClientCookie cookie2 = new BasicClientCookie("name2", "value2");
        cookie2.setVersion(1);
        cookie2.setDomain(".domain.com");
        cookie2.setAttribute(ClientCookie.DOMAIN_ATTR, cookie2.getDomain());
        cookie2.setPath("/");
        cookie2.setAttribute(ClientCookie.PATH_ATTR, cookie2.getPath());

        final List<Cookie> cookies = new ArrayList<Cookie>();
        cookies.add(cookie1);
        cookies.add(cookie2);

        final List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());

        final Header header = headers.get(0);
        Assert.assertEquals("$Version=1; name1=\"value1\"; $Path=\"/\"; $Domain=\".domain.com\"; " +
                "name2=\"value2\"; $Path=\"/\"; $Domain=\".domain.com\"",
                header.getValue());

    }

    @Test
    public void testInvalidInput() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        try {
            cookiespec.parse(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.parse(new BasicHeader("Set-Cookie", "name=value"), null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.formatCookies(null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            final List<Cookie> cookies = new ArrayList<Cookie>();
            cookiespec.formatCookies(cookies);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testVersion1CookieWithInvalidExpires() throws Exception {
        final CookieSpec cookiespec = new DefaultCookieSpec();
        final CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);

        final Header origHeader = new BasicHeader("Set-Cookie",
                "test=\"test\"; Version=1; Expires=Mon, 11-Feb-2013 10:39:19 GMT; Path=/");
        final List<Cookie> cookies = cookiespec.parse(origHeader, origin);
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());

        final List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        final Header header1 = headers.get(0);
        Assert.assertEquals("test=\"test\"", header1.getValue());
    }

}

