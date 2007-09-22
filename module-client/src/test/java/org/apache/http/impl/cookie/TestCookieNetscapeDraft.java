/*
 * $HeadURL$
 * $Revision$
 * $Date$
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
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
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
        Cookie[] parsed = cookiespec.parse(header, origin);
        for (int i = 0; i < parsed.length; i++) {
            cookiespec.validate(parsed[i], origin);
        }
        assertEquals("Found 1 cookies.",1,parsed.length);
        assertEquals("Name","name1",parsed[0].getName());
        assertEquals("Value","value1",parsed[0].getValue());
        assertEquals("Domain","host",parsed[0].getDomain());
        assertEquals("Path","/path/",parsed[0].getPath());
    }

    public void testParseAbsPath2() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=/");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("host", 80, "/", true);
        Cookie[] parsed = cookiespec.parse(header, origin);
        for (int i = 0; i < parsed.length; i++) {
            cookiespec.validate(parsed[i], origin);
        }
        assertEquals("Found 1 cookies.",1,parsed.length);
        assertEquals("Name","name1",parsed[0].getName());
        assertEquals("Value","value1",parsed[0].getValue());
        assertEquals("Domain","host",parsed[0].getDomain());
        assertEquals("Path","/",parsed[0].getPath());
    }

    public void testParseRelativePath() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "name1=value1;Path=whatever");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        CookieOrigin origin = new CookieOrigin("host", 80, "whatever", true);
        Cookie[] parsed = cookiespec.parse(header, origin);
        for (int i = 0; i < parsed.length; i++) {
            cookiespec.validate(parsed[i], origin);
        }
        assertEquals("Found 1 cookies.",1,parsed.length);
        assertEquals("Name","name1",parsed[0].getName());
        assertEquals("Value","value1",parsed[0].getValue());
        assertEquals("Domain","host",parsed[0].getDomain());
        assertEquals("Path","whatever",parsed[0].getPath());
    }

    public void testParseWithIllegalNetscapeDomain1() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; domain=.com");

        CookieSpec cookiespec = new NetscapeDraftSpec();
        try {
            CookieOrigin origin = new CookieOrigin("a.com", 80, "/", false);
            Cookie[] parsed = cookiespec.parse(header, origin);
            for (int i = 0; i < parsed.length; i++) {
                cookiespec.validate(parsed[i], origin);
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
            Cookie[] parsed = cookiespec.parse(header, origin);
            for (int i = 0; i < parsed.length; i++) {
                cookiespec.validate(parsed[i], origin);
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
        Cookie[] cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies[0], origin);
        Header[] headers = cookiespec.formatCookies(cookies);
        assertEquals(1, headers.length);
        assertEquals("name=value", headers[0].getValue());
    }
    
    /**
     * Tests Netscape specific expire attribute parsing.
     */
    public void testNetscapeCookieExpireAttribute() throws Exception {
        CookieSpec cookiespec = new NetscapeDraftSpec();
        Header header = new BasicHeader("Set-Cookie", 
            "name=value; path=/; domain=.mydomain.com; expires=Thu, 01-Jan-2070 00:00:10 GMT; comment=no_comment");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        Cookie[] cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies[0], origin);
        header = new BasicHeader("Set-Cookie", 
            "name=value; path=/; domain=.mydomain.com; expires=Thu 01-Jan-2070 00:00:10 GMT; comment=no_comment");
        try {
            cookies = cookiespec.parse(header, origin);
            cookiespec.validate(cookies[0], origin);
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
        Cookie[] cookies = cookiespec.parse(header, origin);
        assertEquals("number of cookies", 1, cookies.length);
        assertEquals("a", cookies[0].getName());
        assertEquals("b,c", cookies[0].getValue());
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
        Header[] headers = cookiespec.formatCookies(new Cookie[] {c1, c2, c3});
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("name1=value1; name2=value2; name3", headers[0].getValue());
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
            cookiespec.formatCookies(new BasicClientCookie[] {});
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}

