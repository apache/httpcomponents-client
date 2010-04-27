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
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

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
 * Test cases for Netscape cookie draft
 */
public class TestCookieNetscapeDraft {

    @Test
    public void testParseAbsPath() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=/path/");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookies.",1,cookies.size());
        Assert.assertEquals("Name","name1",cookies.get(0).getName());
        Assert.assertEquals("Value","value1",cookies.get(0).getValue());
        Assert.assertEquals("Domain","host",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/path/",cookies.get(0).getPath());
    }

    @Test
    public void testParseAbsPath2() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=/");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("host", 80, "/", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookies.",1,cookies.size());
        Assert.assertEquals("Name","name1",cookies.get(0).getName());
        Assert.assertEquals("Value","value1",cookies.get(0).getValue());
        Assert.assertEquals("Domain","host",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/",cookies.get(0).getPath());
    }

    @Test
    public void testParseRelativePath() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=whatever");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("host", 80, "whatever", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookies.",1,cookies.size());
        Assert.assertEquals("Name","name1",cookies.get(0).getName());
        Assert.assertEquals("Value","value1",cookies.get(0).getValue());
        Assert.assertEquals("Domain","host",cookies.get(0).getDomain());
        Assert.assertEquals("Path","whatever",cookies.get(0).getPath());
    }

    @Test
    public void testParseWithIllegalNetscapeDomain1() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; domain=.com");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        try {
            CookieOrigin origin = new CookieOrigin("a.com", 80, "/", false);
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    @Test
    public void testParseWithWrongNetscapeDomain2() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; domain=.y.z");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        try {
            CookieOrigin origin = new CookieOrigin("x.y.z", 80, "/", false);
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
     * Tests Netscape specific cookie formatting.
     */
    @Test
    public void testNetscapeCookieFormatting() throws Exception {
        Header header = new BasicHeader(
          "Set-Cookie", "name=value; path=/; domain=.mydomain.com");
        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("name=value", headers.get(0).getValue());
    }

    /**
     * Tests Netscape specific expire attribute parsing.
     */
    @Test
    public void testNetscapeCookieExpireAttribute() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
        Header header = new BasicHeader("Set-Cookie",
            "name=value; path=/; domain=.mydomain.com; expires=Thu, 01-Jan-2070 00:00:10 GMT; comment=no_comment");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        Cookie cookie = cookies.get(0);
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT"));
        c.setTime(cookie.getExpiryDate());
        int year = c.get(Calendar.YEAR);
        Assert.assertEquals(2070, year);
    }

    /**
     * Expire attribute with two digit year.
     */
    @Test
    public void testNetscapeCookieExpireAttributeTwoDigitYear() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
        Header header = new BasicHeader("Set-Cookie",
            "name=value; path=/; domain=.mydomain.com; expires=Thursday, 01-Jan-70 00:00:10 GMT; comment=no_comment");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        Cookie cookie = cookies.get(0);
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT"));
        c.setTime(cookie.getExpiryDate());
        int year = c.get(Calendar.YEAR);
        Assert.assertEquals(2070, year);
    }

    /**
     * Invalid expire attribute.
     */
    @Test
    public void testNetscapeCookieInvalidExpireAttribute() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie",
            "name=value; path=/; domain=.mydomain.com; expires=Thu 01-Jan-2070 00:00:10 GMT; comment=no_comment");
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            cookiespec.validate(cookies.get(0), origin);
            Assert.fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests Netscape specific expire attribute without a time zone.
     */
    @Test
    public void testNetscapeCookieExpireAttributeNoTimeZone() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
        Header header = new BasicHeader("Set-Cookie",
            "name=value; expires=Thu, 01-Jan-2006 00:00:00 ");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        try {
            cookiespec.parse(header, origin);
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    /**
     * Tests if cookie values with embedded comma are handled correctly.
     */
    @Test
    public void testCookieWithComma() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "a=b,c");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        Assert.assertEquals("number of cookies", 1, cookies.size());
        Assert.assertEquals("a", cookies.get(0).getName());
        Assert.assertEquals("b,c", cookies.get(0).getValue());
    }

    @Test
    public void testFormatCookies() throws Exception {
        BasicClientCookie c1 = new BasicClientCookie("name1", "value1");
        c1.setDomain(".whatever.com");
        c1.setAttribute(ClientCookie.DOMAIN_ATTR, c1.getDomain());
        c1.setPath("/");
        c1.setAttribute(ClientCookie.PATH_ATTR, c1.getPath());

        Cookie c2 = new BasicClientCookie("name2", "value2");
        Cookie c3 = new BasicClientCookie("name3", null);

        CookieSpec cookiespec = new NetscapeDraftSpec();
        List<Cookie> cookies = new ArrayList<Cookie>();
        cookies.add(c1);
        cookies.add(c2);
        cookies.add(c3);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("name1=value1; name2=value2; name3", headers.get(0).getValue());
    }

    @Test
    public void testInvalidInput() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
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

