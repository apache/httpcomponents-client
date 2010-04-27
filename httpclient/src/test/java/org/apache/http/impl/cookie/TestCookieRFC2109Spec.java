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
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for RFC2109 cookie spec
 */
public class TestCookieRFC2109Spec {

    @Test
    public void testConstructor() throws Exception {
        new RFC2109Spec();
        new RFC2109Spec(null, false);
        new RFC2109Spec(new String[] { DateUtils.PATTERN_RFC1036 }, false);
    }

    @Test
    public void testParseVersion() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertEquals("Version",1,cookies.get(0).getVersion());
    }

    /**
     * Test domain equals host
     */
    @Test
    public void testCookiesomainEqualsHost() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=www.b.com; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("www.b.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        Assert.assertEquals("www.b.com", cookies.get(0).getDomain());
    }

    /**
     * Domain does not start with a dot
     */
    @Test
    public void testParseWithIllegalDomain1() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=a.b.com; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("www.a.b.com", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Domain must have alt least one embedded dot
     */
    @Test
    public void testParseWithIllegalDomain2() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=.com; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("b.com", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Host minus domain may not contain any dots
     */
    @Test
    public void testParseWithIllegalDomain4() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=.c.com; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("a.b.c.com", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests if that invalid second domain level cookie gets
     * rejected in the strict mode, but gets accepted in the
     * browser compatibility mode.
     */
    @Test
    public void testSecondDomainLevelCookie() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain(".sourceforge.net");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("sourceforge.net", 80, "/", false);
        try {
            cookiespec.validate(cookie, origin);
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {
            // Expected
        }
    }

    @Test
    public void testSecondDomainLevelCookieMatch() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain(".sourceforge.net");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("sourceforge.net", 80, "/", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testParseWithWrongPath() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=127.0.0.1; path=/not/just/root");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests if cookie constructor rejects cookie name containing blanks.
     */
    @Test
    public void testCookieNameWithBlanks() throws Exception {
        Header setcookie = new BasicHeader("Set-Cookie", "invalid name=");
        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(setcookie, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests if cookie constructor rejects cookie name starting with $.
     */
    @Test
    public void testCookieNameStartingWithDollarSign() throws Exception {
        Header setcookie = new BasicHeader("Set-Cookie", "$invalid_name=");
        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(setcookie, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests if default cookie validator rejects cookies originating from a host without domain
     * where domain attribute does not match the host of origin
     */
    @Test
    public void testInvalidDomainWithSimpleHostName() throws Exception {
        CookieSpec cookiespec = new RFC2109Spec();
        Header header = new BasicHeader("Set-Cookie",
            "name=\"value\"; version=\"1\"; path=\"/\"; domain=\".mydomain.com\"");
        CookieOrigin origin1 = new CookieOrigin("host", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin1);
        try {
            cookiespec.validate(cookies.get(0), origin1);
            Assert.fail("MalformedCookieException must have thrown");
        }
        catch(MalformedCookieException expected) {
        }
        CookieOrigin origin2 = new CookieOrigin("host2", 80, "/", false);
        header = new BasicHeader("Set-Cookie",
            "name=\"value\"; version=\"1\"; path=\"/\"; domain=\"host1\"");
        cookies = cookiespec.parse(header, origin2);
        try {
            cookiespec.validate(cookies.get(0), origin2);
            Assert.fail("MalformedCookieException must have thrown");
        }
        catch(MalformedCookieException expected) {
        }
    }

    /**
     * Tests if cookie values with embedded comma are handled correctly.
     */
    @Test
    public void testCookieWithComma() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "a=b,c");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        Assert.assertEquals("number of cookies", 2, cookies.size());
        Assert.assertEquals("a", cookies.get(0).getName());
        Assert.assertEquals("b", cookies.get(0).getValue());
        Assert.assertEquals("c", cookies.get(1).getName());
        Assert.assertEquals(null, cookies.get(1).getValue());
    }

    /**
     * Tests RFC 2109 compiant cookie formatting.
     */
    @Test
    public void testRFC2109CookieFormatting() throws Exception {
        CookieSpec cookiespec = new RFC2109Spec(null, false);
        Header header = new BasicHeader("Set-Cookie",
            "name=\"value\"; version=1; path=\"/\"; domain=\".mydomain.com\"");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies  = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("$Version=1; name=\"value\"; $Path=\"/\"; $Domain=\".mydomain.com\"",
                headers.get(0).getValue());

        header = new BasicHeader( "Set-Cookie",
            "name=value; path=/; domain=.mydomain.com");
        cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("$Version=0; name=value; $Path=/; $Domain=.mydomain.com",
                headers.get(0).getValue());
    }

    @Test
    public void testRFC2109CookiesFormatting() throws Exception {
        CookieSpec cookiespec = new RFC2109Spec(null, true);
        Header header = new BasicHeader("Set-Cookie",
            "name1=value1; path=/; domain=.mydomain.com, " +
            "name2=\"value2\"; version=\"1\"; path=\"/\"; domain=\".mydomain.com\"");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertNotNull(cookies);
        Assert.assertEquals(2, cookies.size());
        List<Header> headers  = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals(
            "$Version=0; name1=value1; $Path=/; $Domain=.mydomain.com; " +
            "name2=value2; $Path=/; $Domain=.mydomain.com",
            headers.get(0).getValue());

        header = new BasicHeader("Set-Cookie",
            "name1=value1; version=1; path=/; domain=.mydomain.com, " +
            "name2=\"value2\"; version=\"1\"; path=\"/\"; domain=\".mydomain.com\"");
        cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertNotNull(cookies);
        Assert.assertEquals(2, cookies.size());
        headers = cookiespec.formatCookies(cookies);
        Assert.assertEquals(
            "$Version=1; name1=\"value1\"; $Path=\"/\"; $Domain=\".mydomain.com\"; " +
            "name2=\"value2\"; $Path=\"/\"; $Domain=\".mydomain.com\"",
            headers.get(0).getValue());
    }

    /**
     * Tests if null cookie values are handled correctly.
     */
    @Test
    public void testNullCookieValueFormatting() {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain(".whatever.com");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new RFC2109Spec();
        List<Cookie> cookies = new ArrayList<Cookie>();
        cookies.add(cookie);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("$Version=0; name=; $Path=/; $Domain=.whatever.com",
                headers.get(0).getValue());

        cookie.setVersion(1);
        cookies = new ArrayList<Cookie>();
        cookies.add(cookie);
        headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("$Version=1; name=; $Path=\"/\"; $Domain=\".whatever.com\"",
                headers.get(0).getValue());
    }

    @Test
    public void testCookieNullDomainNullPathFormatting() {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new RFC2109Spec();
        List<Cookie> cookies = new ArrayList<Cookie>();
        cookies.add(cookie);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("$Version=0; name=; $Path=/", headers.get(0).getValue());

        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, null);
        cookie.setAttribute(ClientCookie.PATH_ATTR, null);
        cookies = new ArrayList<Cookie>();
        cookies.add(cookie);
        headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("$Version=0; name=", headers.get(0).getValue());
    }

    @Test
    public void testCookieOrderingByPath() {
        BasicClientCookie c1 = new BasicClientCookie("name1", "value1");
        c1.setPath("/a/b/c");
        c1.setAttribute(ClientCookie.PATH_ATTR, c1.getPath());
        BasicClientCookie c2 = new BasicClientCookie("name2", "value2");
        c2.setPath("/a/b");
        c2.setAttribute(ClientCookie.PATH_ATTR, c2.getPath());
        BasicClientCookie c3 = new BasicClientCookie("name3", "value3");
        c3.setPath("/a");
        c3.setAttribute(ClientCookie.PATH_ATTR, c3.getPath());
        BasicClientCookie c4 = new BasicClientCookie("name4", "value4");
        c4.setPath("/");
        c4.setAttribute(ClientCookie.PATH_ATTR, c4.getPath());

        CookieSpec cookiespec = new RFC2109Spec(null, true);
        List<Cookie> cookies = new ArrayList<Cookie>();
        cookies.add(c2);
        cookies.add(c4);
        cookies.add(c1);
        cookies.add(c3);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("$Version=0; name1=value1; $Path=/a/b/c; " +
                "name2=value2; $Path=/a/b; " +
                "name3=value3; $Path=/a; " +
                "name4=value4; $Path=/", headers.get(0).getValue());
    }

    @Test
    public void testInvalidInput() throws Exception {
        CookieSpec cookiespec = new RFC2109Spec();
        try {
            cookiespec.parse(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.parse(new BasicHeader("Set-Cookie", "name=value"), null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.validate(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.formatCookies(null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            List<Cookie> cookies = new ArrayList<Cookie>();
            cookiespec.formatCookies(cookies);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}
