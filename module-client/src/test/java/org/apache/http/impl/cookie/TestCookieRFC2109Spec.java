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
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.DateUtils;

/**
 * Test cases for RFC2109 cookie spec
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 */
public class TestCookieRFC2109Spec extends TestCase {


    // ------------------------------------------------------------ Constructor

    public TestCookieRFC2109Spec(String name) {
        super(name);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestCookieRFC2109Spec.class);
    }

    public void testConstructor() throws Exception {
        new RFC2109Spec();
        new RFC2109Spec(null, false);
        new RFC2109Spec(new String[] { DateUtils.PATTERN_RFC1036 }, false);
    }
    
    public void testParseVersion() throws Exception {
        Header header = new BasicHeader("Set-Cookie","cookie-name=cookie-value; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        Cookie[] parsed = cookiespec.parse(header, origin);
        for (int i = 0; i < parsed.length; i++) {
            cookiespec.validate(parsed[i], origin);
        }
        assertEquals("Found 1 cookie.",1,parsed.length);
        assertEquals("Name","cookie-name",parsed[0].getName());
        assertEquals("Value","cookie-value",parsed[0].getValue());
        assertEquals("Version",1,parsed[0].getVersion());
    }

    /**
     * Test domain equals host 
     */
    public void testParseDomainEqualsHost() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=www.b.com; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("www.b.com", 80, "/", false);
        Cookie[] parsed = cookiespec.parse(header, origin);
        for (int i = 0; i < parsed.length; i++) {
            cookiespec.validate(parsed[i], origin);
        }
        assertNotNull(parsed);
        assertEquals(1, parsed.length);
        assertEquals("www.b.com", parsed[0].getDomain());
    }

    /**
     * Domain does not start with a dot
     */
    public void testParseWithIllegalDomain1() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=a.b.com; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("www.a.b.com", 80, "/", false);
        try {
            Cookie[] parsed = cookiespec.parse(header, origin);
            for (int i = 0; i < parsed.length; i++) {
                cookiespec.validate(parsed[i], origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Domain must have alt least one embedded dot
     */
    public void testParseWithIllegalDomain2() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=.com; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("b.com", 80, "/", false);
        try {
            Cookie[] parsed = cookiespec.parse(header, origin);
            for (int i = 0; i < parsed.length; i++) {
                cookiespec.validate(parsed[i], origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Host minus domain may not contain any dots
     */
    public void testParseWithIllegalDomain4() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=.c.com; version=1");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("a.b.c.com", 80, "/", false);
        try {
            Cookie[] parsed = cookiespec.parse(header, origin);
            for (int i = 0; i < parsed.length; i++) {
                cookiespec.validate(parsed[i], origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests if that invalid second domain level cookie gets 
     * rejected in the strict mode, but gets accepted in the
     * browser compatibility mode.
     */
    public void testSecondDomainLevelCookie() throws Exception {
        Cookie cookie = new Cookie("name", null);
        cookie.setDomain(".sourceforge.net");
        cookie.setPath("/");
        cookie.setDomainAttributeSpecified(true);
        cookie.setPathAttributeSpecified(true);

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("sourceforge.net", 80, "/", false);
        try {
            cookiespec.validate(cookie, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {
            // Expected
        }
    }    

    public void testSecondDomainLevelCookieMatch() throws Exception {
        Cookie cookie = new Cookie("name", null);
        cookie.setDomain(".sourceforge.net");
        cookie.setPath("/");
        cookie.setDomainAttributeSpecified(true);
        cookie.setPathAttributeSpecified(true);

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("sourceforge.net", 80, "/", false);
        assertFalse(cookiespec.match(cookie, origin));
    }
    
    public void testParseWithWrongPath() throws Exception {
        Header header = new BasicHeader("Set-Cookie",
            "cookie-name=cookie-value; domain=127.0.0.1; path=/not/just/root");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        try {
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
     * Tests if cookie constructor rejects cookie name containing blanks.
     */
    public void testCookieNameWithBlanks() throws Exception {
        Header setcookie = new BasicHeader("Set-Cookie", "invalid name=");
        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        try {
            Cookie[] parsed = cookiespec.parse(setcookie, origin);
            for (int i = 0; i < parsed.length; i++) {
                cookiespec.validate(parsed[i], origin);
            }
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests if cookie constructor rejects cookie name starting with $.
     */
    public void testCookieNameStartingWithDollarSign() throws Exception {
        Header setcookie = new BasicHeader("Set-Cookie", "$invalid_name=");
        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        try {
            Cookie[] parsed = cookiespec.parse(setcookie, origin);
            for (int i = 0; i < parsed.length; i++) {
                cookiespec.validate(parsed[i], origin);
            }
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException e) {
            // expected
        }
    }

    /**
     * Tests if default cookie validator rejects cookies originating from a host without domain
     * where domain attribute does not match the host of origin 
     */
    public void testInvalidDomainWithSimpleHostName() throws Exception {    
        CookieSpec cookiespec = new RFC2109Spec();
        Header header = new BasicHeader("Set-Cookie", 
            "name=\"value\"; version=\"1\"; path=\"/\"; domain=\".mydomain.com\"");
        CookieOrigin origin1 = new CookieOrigin("host", 80, "/", false);
        Cookie[]cookies = cookiespec.parse(header, origin1);
        try {
            cookiespec.validate(cookies[0], origin1);
            fail("MalformedCookieException must have thrown");
        }
        catch(MalformedCookieException expected) {
        }
        CookieOrigin origin2 = new CookieOrigin("host2", 80, "/", false);
        header = new BasicHeader("Set-Cookie", 
            "name=\"value\"; version=\"1\"; path=\"/\"; domain=\"host1\"");
        cookies = cookiespec.parse(header, origin2);
        try {
            cookiespec.validate(cookies[0], origin2);
            fail("MalformedCookieException must have thrown");
        }
        catch(MalformedCookieException expected) {
        }
    }

    /**
     * Tests if cookie values with embedded comma are handled correctly.
     */
    public void testCookieWithComma() throws Exception {
        Header header = new BasicHeader("Set-Cookie", "a=b,c");

        CookieSpec cookiespec = new RFC2109Spec();
        CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);
        Cookie[] cookies = cookiespec.parse(header, origin);
        assertEquals("number of cookies", 2, cookies.length);
        assertEquals("a", cookies[0].getName());
        assertEquals("b", cookies[0].getValue());
        assertEquals("c", cookies[1].getName());
        assertEquals(null, cookies[1].getValue());
    }

    /**
     * Tests RFC 2109 compiant cookie formatting.
     */
    public void testRFC2109CookieFormatting() throws Exception {
        CookieSpec cookiespec = new RFC2109Spec(null, false);
        Header header = new BasicHeader("Set-Cookie", 
            "name=\"value\"; version=\"1\"; path=\"/\"; domain=\".mydomain.com\"");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        Cookie[] cookies  = cookiespec.parse(header, origin);
        cookiespec.validate(cookies[0], origin);
        Header[] headers = cookiespec.formatCookies(cookies);
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("$Version=\"1\"; name=\"value\"; $Path=\"/\"; $Domain=\".mydomain.com\"", 
                headers[0].getValue());

        header = new BasicHeader( "Set-Cookie", 
            "name=value; path=/; domain=.mydomain.com");
        cookies = cookiespec.parse(header, origin);
        cookiespec.validate(cookies[0], origin);
        headers = cookiespec.formatCookies(cookies);
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("$Version=0; name=value; $Path=/; $Domain=.mydomain.com", 
                headers[0].getValue());
    }

    public void testRFC2109CookiesFormatting() throws Exception {
        CookieSpec cookiespec = new RFC2109Spec(null, true);
        Header header = new BasicHeader("Set-Cookie", 
            "name1=value1; path=/; domain=.mydomain.com, " + 
            "name2=\"value2\"; version=\"1\"; path=\"/\"; domain=\".mydomain.com\"");
        CookieOrigin origin = new CookieOrigin("myhost.mydomain.com", 80, "/", false);
        Cookie[] cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.length; i++) {
            cookiespec.validate(cookies[i], origin);
        }
        assertNotNull(cookies);
        assertEquals(2, cookies.length);
        Header[] headers  = cookiespec.formatCookies(cookies);
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals( 
            "$Version=0; name1=value1; $Path=/; $Domain=.mydomain.com; " + 
            "name2=value2; $Path=/; $Domain=.mydomain.com",
            headers[0].getValue());

        header = new BasicHeader("Set-Cookie", 
            "name1=value1; version=1; path=/; domain=.mydomain.com, " + 
            "name2=\"value2\"; version=\"1\"; path=\"/\"; domain=\".mydomain.com\"");
        cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.length; i++) {
            cookiespec.validate(cookies[i], origin);
        }
        assertNotNull(cookies);
        assertEquals(2, cookies.length);
        headers = cookiespec.formatCookies(cookies);
        assertEquals( 
            "$Version=\"1\"; name1=\"value1\"; $Path=\"/\"; $Domain=\".mydomain.com\"; " + 
            "name2=\"value2\"; $Path=\"/\"; $Domain=\".mydomain.com\"",
            headers[0].getValue());
    }
    
    /**
     * Tests if null cookie values are handled correctly.
     */
    public void testNullCookieValueFormatting() {
        Cookie cookie = new Cookie("name", null);
        cookie.setDomain(".whatever.com");
        cookie.setPath("/");
        cookie.setDomainAttributeSpecified(true);
        cookie.setPathAttributeSpecified(true);

        CookieSpec cookiespec = new RFC2109Spec();
        Header[] headers = cookiespec.formatCookies(new Cookie[] { cookie });
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("$Version=0; name=; $Path=/; $Domain=.whatever.com", 
                headers[0].getValue());

        cookie.setVersion(1);
        headers = cookiespec.formatCookies(new Cookie[] { cookie });
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("$Version=\"1\"; name=; $Path=\"/\"; $Domain=\".whatever.com\"", 
                headers[0].getValue());
    }

    public void testCookieNullDomainNullPathFormatting() {
        Cookie cookie = new Cookie("name", null); 
        cookie.setDomainAttributeSpecified(true);
        cookie.setPath("/");
        cookie.setPathAttributeSpecified(true);

        CookieSpec cookiespec = new RFC2109Spec();
        Header[] headers = cookiespec.formatCookies(new Cookie[] { cookie });
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("$Version=0; name=; $Path=/", headers[0].getValue());

        cookie.setDomainAttributeSpecified(false);
        cookie.setPathAttributeSpecified(false);
        headers = cookiespec.formatCookies(new Cookie[] { cookie });
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("$Version=0; name=", headers[0].getValue());
    }

    public void testCookieOrderingByPath() {
        Cookie c1 = new Cookie("name1", "value1");
        c1.setPath("/a/b/c");
        c1.setPathAttributeSpecified(true);
        Cookie c2 = new Cookie("name2", "value2");
        c2.setPath("/a/b");
        c2.setPathAttributeSpecified(true);
        Cookie c3 = new Cookie("name3", "value3");
        c3.setPath("/a");
        c3.setPathAttributeSpecified(true);
        Cookie c4 = new Cookie("name4", "value4");
        c4.setPath("/");
        c4.setPathAttributeSpecified(true);

        CookieSpec cookiespec = new RFC2109Spec(null, true);
        Header[] headers = cookiespec.formatCookies(new Cookie[] { c2, c4, c1, c3 });
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("$Version=0; name1=value1; $Path=/a/b/c; " +
                "name2=value2; $Path=/a/b; " +
                "name3=value3; $Path=/a; " +
                "name4=value4; $Path=/", headers[0].getValue());
    }

    public void testInvalidInput() throws Exception {
        CookieSpec cookiespec = new RFC2109Spec();
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
            cookiespec.validate(null, null);
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
            cookiespec.formatCookies(new Cookie[] {});
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
        
}
