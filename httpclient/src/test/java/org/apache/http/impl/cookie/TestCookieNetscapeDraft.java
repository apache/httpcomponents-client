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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.Header;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.message.BasicHeader;

/**
 * Test cases for Netscape cookie draft
 *
 * 
 */
public class TestCookieNetscapeDraft extends TestCase {

    // ------------------------------------------------------------ Constructor

    public TestCookieNetscapeDraft(String name) {
        super(name);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestCookieNetscapeDraft.class);
    }

    public void testParseAbsPath() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=/path/");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertEquals("Found 1 cookies.",1,cookies.size());
        assertEquals("Name","name1",cookies.get(0).getName());
        assertEquals("Value","value1",cookies.get(0).getValue());
        assertEquals("Domain","host",cookies.get(0).getDomain());
        assertEquals("Path","/path/",cookies.get(0).getPath());
    }

    public void testParseAbsPath2() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=/");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("host", 80, "/", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertEquals("Found 1 cookies.",1,cookies.size());
        assertEquals("Name","name1",cookies.get(0).getName());
        assertEquals("Value","value1",cookies.get(0).getValue());
        assertEquals("Domain","host",cookies.get(0).getDomain());
        assertEquals("Path","/",cookies.get(0).getPath());
    }

    public void testParseRelativePath() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=whatever");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("host", 80, "whatever", true);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertEquals("Found 1 cookies.",1,cookies.size());
        assertEquals("Name","name1",cookies.get(0).getName());
        assertEquals("Value","value1",cookies.get(0).getValue());
        assertEquals("Domain","host",cookies.get(0).getDomain());
        assertEquals("Path","whatever",cookies.get(0).getPath());
    }

    public void testParseWithIllegalNetscapeDomain1() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; domain=.com");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        try {
            CookieOrigin origin = new CookieOrigin("a.com", 80, "/", false);
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    public void testParseWithWrongNetscapeDomain2() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; domain=.y.z");
        
        CookieSpec cookiespec = new NetscapeDraftSpec();
        try {
            CookieOrigin origin = new CookieOrigin("x.y.z", 80, "/", false);
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests Netscape specific cookie formatting.
     */
    public void testNetscapeCookieFormatting() throws Exception {
        Header header = new BasicHeader(
          "Set-Cookie", "name=value; path=/; domain=.mydomain.com");
        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        List<Header> headers = cookiespec.formatCookies(cookies);
        assertEquals(1, headers.size());
        assertEquals("name=value", headers.get(0).getValue());
    }
    
    /**
     * Tests Netscape specific expire attribute parsing.
     */
    public void testNetscapeCookieExpireAttribute() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
        Header header = new BasicHeader("Set-Cookie", 
            "name=value; path=/; domain=.mydomain.com; expires=Thu, 01-Jan-2070 00:00:10 GMT; comment=no_comment");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies.get(0), origin);
        header = new BasicHeader("Set-Cookie", 
            "name=value; path=/; domain=.mydomain.com; expires=Thu 01-Jan-2070 00:00:10 GMT; comment=no_comment");
        try {
            cookies = cookiespec.parse(header, origin);
            cookiespec.validate(cookies.get(0), origin);
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests Netscape specific expire attribute without a time zone.
     */
    public void testNetscapeCookieExpireAttributeNoTimeZone() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
        Header header = new BasicHeader("Set-Cookie", 
            "name=value; expires=Thu, 01-Jan-2006 00:00:00 ");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }
    
    /**
     * Tests if cookie values with embedded comma are handled correctly.
     */
    public void testCookieWithComma() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "a=b,c");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertEquals("number of cookies", 1, cookies.size());
        assertEquals("a", cookies.get(0).getName());
        assertEquals("b,c", cookies.get(0).getValue());
    }
 
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
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("name1=value1; name2=value2; name3", headers.get(0).getValue());
    }    

    public void testInvalidInput() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
        try {
            cookiespec.parse(null, null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.parse(new BasicHeader("Set-Cookie", "name=value"), null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.formatCookies(null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            List<Cookie> cookies = new ArrayList<Cookie>();
            cookiespec.formatCookies(cookies);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}

