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
import java.util.Date;
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
 * Test cases for BrowserCompatSpec
 *
 */
public class TestBrowserCompatSpec {

    @Test
    public void testConstructor() throws Exception {
        new BrowserCompatSpec();
        new BrowserCompatSpec(null);
        new BrowserCompatSpec(new String[] { DateUtils.PATTERN_RFC1036 });
    }

    /**
     * Tests whether domain attribute check is case-insensitive.
     */
    @Test
    public void testDomainCaseInsensitivity() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "name=value; path=/; domain=.whatever.com");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.WhatEver.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        Assert.assertEquals(".whatever.com", cookies.get(0).getDomain());
    }

    /**
     * Test basic parse (with various spacings
     */
    @Test
    public void testParse1() throws Exception {
        String headerValue = "custno = 12345; comment=test; version=1," +
            " name=John; version=1; max-age=600; secure; domain=.apache.org";

        Header header = new BasicHeader("set-cookie", headerValue);

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals(2, cookies.size());

        Assert.assertEquals("custno", cookies.get(0).getName());
        Assert.assertEquals("12345", cookies.get(0).getValue());
        Assert.assertEquals("test", cookies.get(0).getComment());
        Assert.assertEquals(0, cookies.get(0).getVersion());
        Assert.assertEquals("www.apache.org", cookies.get(0).getDomain());
        Assert.assertEquals("/", cookies.get(0).getPath());
        Assert.assertFalse(cookies.get(0).isSecure());

        Assert.assertEquals("name", cookies.get(1).getName());
        Assert.assertEquals("John", cookies.get(1).getValue());
        Assert.assertEquals(null, cookies.get(1).getComment());
        Assert.assertEquals(0, cookies.get(1).getVersion());
        Assert.assertEquals(".apache.org", cookies.get(1).getDomain());
        Assert.assertEquals("/", cookies.get(1).getPath());
        Assert.assertTrue(cookies.get(1).isSecure());
    }

    /**
     * Test no spaces
     */
    @Test
    public void testParse2() throws Exception {
        String headerValue = "custno=12345;comment=test; version=1," +
            "name=John;version=1;max-age=600;secure;domain=.apache.org";

        Header header = new BasicHeader("set-cookie", headerValue);

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }

        Assert.assertEquals(2, cookies.size());

        Assert.assertEquals("custno", cookies.get(0).getName());
        Assert.assertEquals("12345", cookies.get(0).getValue());
        Assert.assertEquals("test", cookies.get(0).getComment());
        Assert.assertEquals(0, cookies.get(0).getVersion());
        Assert.assertEquals("www.apache.org", cookies.get(0).getDomain());
        Assert.assertEquals("/", cookies.get(0).getPath());
        Assert.assertFalse(cookies.get(0).isSecure());

        Assert.assertEquals("name", cookies.get(1).getName());
        Assert.assertEquals("John", cookies.get(1).getValue());
        Assert.assertEquals(null, cookies.get(1).getComment());
        Assert.assertEquals(0, cookies.get(1).getVersion());
        Assert.assertEquals(".apache.org", cookies.get(1).getDomain());
        Assert.assertEquals("/", cookies.get(1).getPath());
        Assert.assertTrue(cookies.get(1).isSecure());
    }


    /**
     * Test parse with quoted text
     */
    @Test
    public void testParse3() throws Exception {
        String headerValue =
            "name=\"Doe, John\";version=1;max-age=600;secure;domain=.apache.org";
        Header header = new BasicHeader("set-cookie", headerValue);

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }

        Assert.assertEquals(1, cookies.size());

        Assert.assertEquals("name", cookies.get(0).getName());
        Assert.assertEquals("Doe, John", cookies.get(0).getValue());
        Assert.assertEquals(null, cookies.get(0).getComment());
        Assert.assertEquals(0, cookies.get(0).getVersion());
        Assert.assertEquals(".apache.org", cookies.get(0).getDomain());
        Assert.assertEquals("/", cookies.get(0).getPath());
        Assert.assertTrue(cookies.get(0).isSecure());
    }


    // see issue #5279
    @Test
    public void testQuotedExpiresAttribute() throws Exception {
        String headerValue = "custno=12345;Expires='Thu, 01-Jan-2070 00:00:10 GMT'";

        Header header = new BasicHeader("set-cookie", headerValue);

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "/", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertNotNull("Expected some cookies",cookies);
        Assert.assertEquals("Expected 1 cookie",1,cookies.size());
        Assert.assertNotNull("Expected cookie to have getExpiryDate",cookies.get(0).getExpiryDate());
    }

    @Test
    public void testSecurityError() throws Exception {
        String headerValue = "custno=12345;comment=test; version=1," +
            "name=John;version=1;max-age=600;secure;domain=jakarta.apache.org";
        Header header = new BasicHeader("set-cookie", headerValue);

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "/", true);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testParseSimple() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/path/path", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertTrue("Comment",null == cookies.get(0).getComment());
        Assert.assertTrue("ExpiryDate",null == cookies.get(0).getExpiryDate());
        //Assert.assertTrue("isToBeDiscarded",cookies.get(0).isToBeDiscarded());
        Assert.assertTrue("isPersistent",!cookies.get(0).isPersistent());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/path",cookies.get(0).getPath());
        Assert.assertTrue("Secure",!cookies.get(0).isSecure());
        Assert.assertEquals("Version",0,cookies.get(0).getVersion());
    }

    @Test
    public void testParseSimple2() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "cookie-name=cookie-value");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/path", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.", 1, cookies.size());
        Assert.assertEquals("Name", "cookie-name", cookies.get(0).getName());
        Assert.assertEquals("Value", "cookie-value", cookies.get(0).getValue());
        Assert.assertTrue("Comment", null == cookies.get(0).getComment());
        Assert.assertTrue("ExpiryDate", null == cookies.get(0).getExpiryDate());
        //Assert.assertTrue("isToBeDiscarded",cookies.get(0).isToBeDiscarded());
        Assert.assertTrue("isPersistent", !cookies.get(0).isPersistent());
        Assert.assertEquals("Domain", "127.0.0.1", cookies.get(0).getDomain());
        Assert.assertEquals("Path", "/", cookies.get(0).getPath());
        Assert.assertTrue("Secure", !cookies.get(0).isSecure());
        Assert.assertEquals("Version", 0, cookies.get(0).getVersion());
    }

    @Test
    public void testParseNoName() throws Exception {
        Header header = new BasicHeader("Set-Cookie","=stuff; path=/");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testParseNoValue() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value", "", cookies.get(0).getValue());
        Assert.assertTrue("Comment",null == cookies.get(0).getComment());
        Assert.assertTrue("ExpiryDate",null == cookies.get(0).getExpiryDate());
        //Assert.assertTrue("isToBeDiscarded",cookies.get(0).isToBeDiscarded());
        Assert.assertTrue("isPersistent",!cookies.get(0).isPersistent());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/",cookies.get(0).getPath());
        Assert.assertTrue("Secure",!cookies.get(0).isSecure());
        Assert.assertEquals("Version",0,cookies.get(0).getVersion());
    }

    @Test
    public void testParseWithWhiteSpace() throws Exception {
        Header header = new BasicHeader("Set-Cookie"," cookie-name  =    cookie-value  ");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/",cookies.get(0).getPath());
        Assert.assertTrue("Secure",!cookies.get(0).isSecure());
        Assert.assertTrue("ExpiryDate",null == cookies.get(0).getExpiryDate());
        Assert.assertTrue("Comment",null == cookies.get(0).getComment());
    }

    @Test
    public void testParseWithQuotes() throws Exception {
        Header header = new BasicHeader("Set-Cookie"," cookie-name  =  \" cookie-value \" ;path=/");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1",80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","\" cookie-value \"",cookies.get(0).getValue());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/",cookies.get(0).getPath());
        Assert.assertTrue("Secure",!cookies.get(0).isSecure());
        Assert.assertTrue("ExpiryDate",null == cookies.get(0).getExpiryDate());
        Assert.assertTrue("Comment",null == cookies.get(0).getComment());
    }

    @Test
    public void testParseWithPath() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; Path=/path/");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/path/path", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/path/",cookies.get(0).getPath());
        Assert.assertTrue("Secure",!cookies.get(0).isSecure());
        Assert.assertTrue("ExpiryDate",null == cookies.get(0).getExpiryDate());
        Assert.assertTrue("Comment",null == cookies.get(0).getComment());
    }

    @Test
    public void testParseWithDomain() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; Domain=127.0.0.1");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/",cookies.get(0).getPath());
        Assert.assertTrue("Secure",!cookies.get(0).isSecure());
        Assert.assertTrue("ExpiryDate",null == cookies.get(0).getExpiryDate());
        Assert.assertTrue("Comment",null == cookies.get(0).getComment());
    }

    @Test
    public void testParseWithSecure() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; secure");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/",cookies.get(0).getPath());
        Assert.assertTrue("Secure",cookies.get(0).isSecure());
        Assert.assertTrue("ExpiryDate",null == cookies.get(0).getExpiryDate());
        Assert.assertTrue("Comment",null == cookies.get(0).getComment());
    }

    @Test
    public void testParseWithComment() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; comment=\"This is a comment.\"");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/",cookies.get(0).getPath());
        Assert.assertTrue("Secure",!cookies.get(0).isSecure());
        Assert.assertTrue("ExpiryDate",null == cookies.get(0).getExpiryDate());
        Assert.assertEquals("Comment","\"This is a comment.\"",cookies.get(0).getComment());
    }

    @Test
    public void testParseWithExpires() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value;Expires=Thu, 01-Jan-1970 00:00:10 GMT");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertEquals("Domain","127.0.0.1",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/",cookies.get(0).getPath());
        Assert.assertTrue("Secure",!cookies.get(0).isSecure());
        Assert.assertEquals(new Date(10000L),cookies.get(0).getExpiryDate());
        Assert.assertTrue("Comment",null == cookies.get(0).getComment());
    }

    @Test
    public void testParseWithAll() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value;Version=1;Path=/commons;Domain=.apache.org;" +
            "Comment=This is a comment.;secure;Expires=Thu, 01-Jan-1970 00:00:10 GMT");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "/commons/httpclient", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookie.",1,cookies.size());
        Assert.assertEquals("Name","cookie-name",cookies.get(0).getName());
        Assert.assertEquals("Value","cookie-value",cookies.get(0).getValue());
        Assert.assertEquals("Domain",".apache.org",cookies.get(0).getDomain());
        Assert.assertEquals("Path","/commons",cookies.get(0).getPath());
        Assert.assertTrue("Secure",cookies.get(0).isSecure());
        Assert.assertEquals(new Date(10000L),cookies.get(0).getExpiryDate());
        Assert.assertEquals("Comment","This is a comment.",cookies.get(0).getComment());
        Assert.assertEquals("Version",0,cookies.get(0).getVersion());
    }

    @Test
    public void testParseMultipleDifferentPaths() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "name1=value1;Version=1;Path=/commons,name1=value2;Version=1;" +
            "Path=/commons/httpclient;Version=1");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "/commons/httpclient", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Wrong number of cookies.",2,cookies.size());
        Assert.assertEquals("Name","name1",cookies.get(0).getName());
        Assert.assertEquals("Value","value1",cookies.get(0).getValue());
        Assert.assertEquals("Name","name1",cookies.get(1).getName());
        Assert.assertEquals("Value","value2",cookies.get(1).getValue());
    }

    @Test
    public void testParseRelativePath() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=whatever");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "whatever", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertEquals("Found 1 cookies.",1,cookies.size());
        Assert.assertEquals("Name","name1",cookies.get(0).getName());
        Assert.assertEquals("Value","value1",cookies.get(0).getValue());
        Assert.assertEquals("Path","whatever",cookies.get(0).getPath());
    }

    @Test
    public void testParseWithWrongDomain() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=127.0.0.1; version=1");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.2", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testParseWithPathMismatch() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; path=/path/path/path");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/path", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException should have been thrown.");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    @Test
    public void testParseWithPathMismatch2() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; path=/foobar");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/foo", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException should have been thrown.");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests if cookie constructor rejects cookie name containing blanks.
     */
    @Test
    public void testCookieNameWithBlanks() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "invalid name=");
        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
    }

    /**
     * Tests if cookie constructor rejects cookie name containing blanks.
     */
    @Test
    public void testCookieNameBlank() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "=stuff");
        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException expected) {
        }
    }

    /**
     * Tests if cookie constructor rejects cookie name starting with $.
     */
    @Test
    public void testCookieNameStartingWithDollarSign() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "$invalid_name=");
        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
    }


    /**
     * Tests if malformatted expires attribute is cookies correctly.
     */
    @Test
    public void testCookieWithComma() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name=value; expires=\"Thu, 01-Jan-1970 00:00:00 GMT");

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException expected) {
        }
    }


    /**
     * Tests several date formats.
     */
    @Test
    public void testDateFormats() throws Exception {
        //comma, dashes
        checkDate("Thu, 01-Jan-70 00:00:10 GMT");
        checkDate("Thu, 01-Jan-2070 00:00:10 GMT");
        //no comma, dashes
        checkDate("Thu 01-Jan-70 00:00:10 GMT");
        checkDate("Thu 01-Jan-2070 00:00:10 GMT");
        //comma, spaces
        checkDate("Thu, 01 Jan 70 00:00:10 GMT");
        checkDate("Thu, 01 Jan 2070 00:00:10 GMT");
        //no comma, spaces
        checkDate("Thu 01 Jan 70 00:00:10 GMT");
        checkDate("Thu 01 Jan 2070 00:00:10 GMT");
        //weird stuff
        checkDate("Wed, 20-Nov-2002 09-38-33 GMT");


        try {
            checkDate("this aint a date");
            Assert.fail("Date check is bogus");
        } catch(Exception e) {
        }
    }

    private void checkDate(String date) throws Exception {
        Header header = new BasicHeader("Set-Cookie", "custno=12345;Expires='"+date+"';");
        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
    }

    /**
     * Tests if invalid second domain level cookie gets accepted in the
     * browser compatibility mode.
     */
    @Test
    public void testSecondDomainLevelCookie() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain(".sourceforge.net");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("sourceforge.net", 80, "/", false);
        cookiespec.validate(cookie, origin);
    }

    @Test
    public void testSecondDomainLevelCookieMatch1() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain(".sourceforge.net");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("sourceforge.net", 80, "/", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testSecondDomainLevelCookieMatch2() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain("sourceforge.net");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("www.sourceforge.net", 80, "/", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testSecondDomainLevelCookieMatch3() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain(".sourceforge.net");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

         CookieSpec cookiespec = new BrowserCompatSpec();
         CookieOrigin origin = new CookieOrigin("www.sourceforge.net", 80, "/", false);
         Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testInvalidSecondDomainLevelCookieMatch1() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain(".sourceforge.net");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("antisourceforge.net", 80, "/", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testInvalidSecondDomainLevelCookieMatch2() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain("sourceforge.net");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("antisourceforge.net", 80, "/", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testMatchBlankPath() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host");
        cookie.setPath("/");
        CookieOrigin origin = new CookieOrigin("host", 80, "  ", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testMatchNullCookieDomain() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setPath("/");
        CookieOrigin origin = new CookieOrigin("host", 80, "/", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testMatchNullCookiePath() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host");
        CookieOrigin origin = new CookieOrigin("host", 80, "/", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMatch1() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host");
        cookie.setPath("/");
        CookieOrigin origin = new CookieOrigin("host", 80, "/", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMatch2() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(".whatever.com");
        cookie.setPath("/");
        CookieOrigin origin = new CookieOrigin(".whatever.com", 80, "/", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMatch3() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(".whatever.com");
        cookie.setPath("/");
        CookieOrigin origin = new CookieOrigin(".really.whatever.com", 80, "/", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMatch4() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host");
        cookie.setPath("/");
        CookieOrigin origin = new CookieOrigin("host", 80, "/foobar", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMismatch1() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host1");
        cookie.setPath("/");
        CookieOrigin origin = new CookieOrigin("host2", 80, "/", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMismatch2() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(".aaaaaaaaa.com");
        cookie.setPath("/");
        CookieOrigin origin = new CookieOrigin(".bbbbbbbb.com", 80, "/", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMismatch3() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host");
        cookie.setPath("/foobar");
        CookieOrigin origin = new CookieOrigin("host", 80, "/foo", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMismatch4() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host");
        cookie.setPath("/foobar");
        CookieOrigin origin = new CookieOrigin("host", 80, "/foobar/", false);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMatch5() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host");
        cookie.setPath("/foobar/r");
        CookieOrigin origin = new CookieOrigin("host", 80, "/foobar/", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testCookieMismatch6() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("host");
        cookie.setPath("/foobar");
        cookie.setSecure(true);
        CookieOrigin origin = new CookieOrigin("host", 80, "/foobar", false);
        Assert.assertFalse(cookiespec.match(cookie, origin));
    }

    @Test
    public void testInvalidMatchDomain() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", null);
        cookie.setDomain("beta.gamma.com");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, cookie.getDomain());
        cookie.setPath("/");
        cookie.setAttribute(ClientCookie.PATH_ATTR, cookie.getPath());

        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("alpha.beta.gamma.com", 80, "/", false);
        cookiespec.validate(cookie, origin);
        Assert.assertTrue(cookiespec.match(cookie, origin));
    }

    /**
     * Tests generic cookie formatting.
     */
    @Test
    public void testGenericCookieFormatting() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "name=value; path=/; domain=.mydomain.com");
        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("name=value", headers.get(0).getValue());
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

        CookieSpec cookiespec = new BrowserCompatSpec();
        List<Cookie> cookies = new ArrayList<Cookie>(1);
        cookies.add(cookie);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("name=", headers.get(0).getValue());
    }

    /**
     * Tests generic cookie formatting.
     */
    @Test
    public void testFormatSeveralCookies() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "name1=value1; path=/; domain=.mydomain.com, name2 = value2 ; path=/; domain=.mydomain.com; version=0");
        CookieSpec cookiespec = new BrowserCompatSpec();
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        List<Header> headers = cookiespec.formatCookies(cookies);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.size());
        Assert.assertEquals("name1=value1; name2=value2", headers.get(0).getValue());
    }

    @Test
    public void testKeepCloverHappy() throws Exception {
        new MalformedCookieException();
        new MalformedCookieException("whatever");
        new MalformedCookieException("whatever", null);
    }

    @Test
    public void testInvalidInput() throws Exception {
        CookieSpec cookiespec = new BrowserCompatSpec();
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
            cookiespec.validate(new BasicClientCookie("name", null), null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.match(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.match(new BasicClientCookie("name", null), null);
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
