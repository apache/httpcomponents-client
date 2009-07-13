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

package org.apache.http.impl.cookie;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

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

/**
 * Test cases for RFC2965 cookie spec
 *
 */
public class TestCookieRFC2965Spec extends TestCase {

    // ------------------------------------------------------------ Constructor

    public TestCookieRFC2965Spec(String name) {
        super(name);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestCookieRFC2965Spec.class);
    }

    // ------------------------------------------------------- Test Cookie Parsing

    /**
     * Test parsing cookie <tt>"Path"</tt> attribute.
     */
    public void testParsePath() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Path=/;Version=1;Path=");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        // only the first occurrence of path attribute is considered, others ignored
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.containsAttribute(ClientCookie.PATH_ATTR));
    }

    public void testParsePathDefault() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/path/", false);
        // Path is OPTIONAL, defaults to the request path
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("/path", cookie.getPath());
        assertFalse(cookie.containsAttribute(ClientCookie.PATH_ATTR));
    }

    public void testParseNullPath() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Path=;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.containsAttribute(ClientCookie.PATH_ATTR));
    }

    public void testParseBlankPath() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Path=\"   \";Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.containsAttribute(ClientCookie.PATH_ATTR));
    }
    
    /**
     * Test parsing cookie <tt>"Domain"</tt> attribute.
     */
    public void testParseDomain() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Domain=.domain.com;Version=1;Domain=");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        // only the first occurrence of domain attribute is considered, others ignored
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals(".domain.com", cookie.getDomain());
        assertTrue(cookie.containsAttribute(ClientCookie.DOMAIN_ATTR));

        // should put a leading dot if there is no dot in front of domain
        header = new BasicHeader("Set-Cookie2", "name=value;Domain=domain.com;Version=1");
        cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        cookie = (ClientCookie) cookies.get(0);
        assertEquals(".domain.com", cookie.getDomain());
    }

    public void testParseDomainDefaultValue() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        // Domain is OPTIONAL, defaults to the request host
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("www.domain.com", cookie.getDomain());
        assertFalse(cookie.containsAttribute(ClientCookie.DOMAIN_ATTR));
    }

    public void testParseNullDomain() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        // domain cannot be null
        Header header = new BasicHeader("Set-Cookie2", "name=value;Domain=;Version=1");
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testParseBlankDomain() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Domain=\"   \";Version=1");
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    /**
     * Test parsing cookie <tt>"Port"</tt> attribute.
     */
    public void testParsePort() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Port=\"80,800,8000\";Version=1;Port=nonsense");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        // only the first occurrence of port attribute is considered, others ignored
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        int[] ports = cookie.getPorts();
        assertNotNull(ports);
        assertEquals(3, ports.length);
        assertEquals(80, ports[0]);
        assertEquals(800, ports[1]);
        assertEquals(8000, ports[2]);
        assertTrue(cookie.containsAttribute(ClientCookie.PORT_ATTR));
    }

    public void testParsePortDefault() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        // Port is OPTIONAL, cookie can be accepted from any port
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertFalse(cookie.containsAttribute(ClientCookie.PORT_ATTR));
    }

    public void testParseNullPort() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        // null port defaults to request port
        Header header = new BasicHeader("Set-Cookie2", "name=value;Port=;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        int[] ports = cookie.getPorts();
        assertNotNull(ports);
        assertEquals(1, ports.length);
        assertEquals(80, ports[0]);
        assertEquals("", cookie.getAttribute(ClientCookie.PORT_ATTR));
    }

    public void testParseBlankPort() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        // blank port defaults to request port
        Header header = new BasicHeader("Set-Cookie2", "name=value;Port=\"  \";Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        int[] ports = cookie.getPorts();
        assertNotNull(ports);
        assertEquals(1, ports.length);
        assertEquals(80, ports[0]);
        assertEquals("  ", cookie.getAttribute(ClientCookie.PORT_ATTR));
    }

    public void testParseInvalidPort() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Port=nonsense;Version=1");
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testParseNegativePort() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Port=\"80,-800,8000\";Version=1");
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    /**
     * test parsing cookie name/value.
     */
    public void testParseNameValue() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=1;");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("name", cookie.getName());
        assertEquals("value", cookie.getValue());
    }

    /**
     * test parsing cookie <tt>"Version"</tt> attribute.
     */
    public void testParseVersion() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=1;");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals(1, cookie.getVersion());
        assertTrue(cookie.containsAttribute(ClientCookie.VERSION_ATTR));
    }

    public void testParseNullVersion() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        // version cannot be null
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=;");
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }
    
    public void testParseNegativeVersion() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=-1;");
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }
    /**
     * test parsing cookie <tt>"Max-age"</tt> attribute.
     */
    public void testParseMaxage() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Max-age=3600;Version=1;Max-age=nonsense");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        // only the first occurence of max-age attribute is considered, others ignored
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertFalse(cookie.isExpired(new Date()));
    }

    public void testParseMaxageDefault() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        // Max-age is OPTIONAL, defaults to session cookie
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertFalse(cookie.isPersistent());
    }

    public void testParseNullMaxage() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Max-age=;Version=1");
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testParseNegativeMaxage() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Max-age=-3600;Version=1;");
        try {
            cookiespec.parse(header, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    /**
     * test parsing <tt>"Secure"</tt> attribute.
     */
    public void testParseSecure() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Secure;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertTrue(cookie.isSecure());
    }

    /**
     * test parsing <tt>"Discard"</tt> attribute.
     */
    public void testParseDiscard() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Discard;Max-age=36000;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        // discard overrides max-age
        assertFalse(cookie.isPersistent());

        // Discard is OPTIONAL, default behavior is dictated by max-age
        header = new BasicHeader("Set-Cookie2", "name=value;Max-age=36000;Version=1");
        cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        cookie = (ClientCookie) cookies.get(0);
        assertTrue(cookie.isPersistent());
    }

    /**
     * test parsing <tt>"Comment"</tt>, <tt>"CommentURL"</tt> and
     * <tt>"Secure"</tt> attributes.
     */
    public void testParseOtherAttributes() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Comment=\"good cookie\";" +
                "CommentURL=\"www.domain.com/goodcookie/\";Secure;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("good cookie", cookie.getComment());
        assertEquals("www.domain.com/goodcookie/", cookie.getCommentURL());
        assertTrue(cookie.isSecure());

        // Comment, CommentURL, Secure are OPTIONAL
        header = new BasicHeader("Set-Cookie2", "name=value;Version=1");
        cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        cookie = (ClientCookie) cookies.get(0);
        assertFalse(cookie.isSecure());
    }

    /**
     * Test parsing header with 2 cookies (separated by comma)
     */
    public void testCookiesWithComma() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "a=b,c");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());
        assertEquals("a", cookies.get(0).getName());
        assertEquals("b", cookies.get(0).getValue());
        assertEquals("c", cookies.get(1).getName());
        assertEquals(null, cookies.get(1).getValue());
    }

    // ------------------------------------------------------- Test Cookie Validation

    /**
     * Test <tt>Domain</tt> validation when domain is not specified
     * in <tt>Set-Cookie2</tt> header.
     */
    public void testValidateNoDomain() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        // cookie domain must string match request host
        assertEquals("www.domain.com", cookie.getDomain());
    }

    /**
     * Test <tt>Domain</tt> validation. Cookie domain attribute must have a
     * leading dot.
     */
    public void testValidateDomainLeadingDot() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value;Domain=domain.com;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals(".domain.com", cookie.getDomain());
    }

    /**
     * Test <tt>Domain</tt> validation. Domain must have at least one embedded dot.
     */
    public void testValidateDomainEmbeddedDot() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("b.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value; domain=.com; version=1");
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException expected) {}

        origin = new CookieOrigin("www.domain.com", 80, "/", false);
        header = new BasicHeader("Set-Cookie2", "name=value;Domain=domain.com;Version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
    }

    /**
     * Test local <tt>Domain</tt> validation. Simple host names
     * (without any dots) are valid only when cookie domain is specified
     * as ".local".
     */
    public void testValidateDomainLocal() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("simplehost", 80, "/", false);
        // when domain is specified as .local, simple host names are valid
        Header header = new BasicHeader("Set-Cookie2", "name=value; domain=.local; version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals(".local", cookie.getDomain());

        // when domain is NOT specified as .local, simple host names are invalid
        header = new BasicHeader("Set-Cookie2", "name=value; domain=domain.com; version=1");
        try {
            // since domain is not .local, this must fail
            cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException expected) {}
    }

    public void testValidateDomainLocalhost() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("localhost", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value; version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("localhost.local", cookie.getDomain());
    }

    /**
     * Test <tt>Domain</tt> validation. Effective host name
     * must domain-match domain attribute.
     */
    public void testValidateDomainEffectiveHost() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();

        // cookie domain does not domain-match request host
        Header header = new BasicHeader("Set-Cookie2", "name=value; domain=.domain.com; version=1");
        try {
            CookieOrigin origin = new CookieOrigin("www.domain.org", 80, "/", false);
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException expected) {}

        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        // cookie domain domain-matches request host
        header = new BasicHeader("Set-Cookie2", "name=value; domain=.domain.com; version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
    }

    /**
     * Test local <tt>Domain</tt> validation.
     * Effective host name minus domain must not contain any dots.
     */
    public void testValidateDomainIllegal() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("a.b.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie2", "name=value; domain=.domain.com; version=1");
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException expected) {}
    }

    /**
     * Test cookie <tt>Path</tt> validation. Cookie path attribute must path-match
     * request path.
     */
    public void testValidatePath() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        Header header = new BasicHeader("Set-Cookie2", "name=value;path=/path;version=1");
        try {
            CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException expected) {}

        // path-matching is case-sensitive
        header = new BasicHeader("Set-Cookie2", "name=value;path=/Path;version=1");
        try {
            CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/path", false);
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException expected) {}

        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/path/path1", false);
        header = new BasicHeader("Set-Cookie2", "name=value;path=/path;version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        assertEquals("/path", cookies.get(0).getPath());
    }

    /**
     * Test cookie name validation.
     */
    public void testValidateCookieName() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("127.0.0.1", 80, "/", false);
        // cookie name must not contain blanks
        Header header = new BasicHeader("Set-Cookie2", "invalid name=value; version=1");
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException expected) {}

        // cookie name must not start with '$'.
        header = new BasicHeader("Set-Cookie2", "$invalid_name=value; version=1");
        try {
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException exception should have been thrown");
        } catch (MalformedCookieException expected) {}

        // valid name
        header = new BasicHeader("Set-Cookie2", "name=value; version=1");
        List<Cookie> cookies = cookiespec.parse(header, origin);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        assertEquals("name", cookie.getName());
        assertEquals("value", cookie.getValue());
    }

    /**
     * Test cookie <tt>Port</tt> validation. Request port must be in the
     * port attribute list.
     */
    public void testValidatePort() throws Exception {
        Header header = new BasicHeader("Set-Cookie2", "name=value; Port=\"80,800\"; version=1");
        CookieSpec cookiespec = new RFC2965Spec();
        try {
            CookieOrigin origin = new CookieOrigin("www.domain.com", 8000, "/", false);
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {}

        // valid port list
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        List<Cookie> cookies = cookiespec.parse(header, origin);
        for (int i = 0; i < cookies.size(); i++) {
            cookiespec.validate(cookies.get(i), origin);
        }
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        ClientCookie cookie = (ClientCookie) cookies.get(0);
        int[] ports = cookie.getPorts();
        assertNotNull(ports);
        assertEquals(2, ports.length);
        assertEquals(80, ports[0]);
        assertEquals(800, ports[1]);
    }

    /**
     * Test cookie <tt>Version</tt> validation.
     */
    public void testValidateVersion() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        // version attribute is REQUIRED
        Header header = new BasicHeader("Set-Cookie2", "name=value");
        try {
            CookieOrigin origin = new CookieOrigin("www.domain.com", 8000, "/", false);
            List<Cookie> cookies = cookiespec.parse(header, origin);
            for (int i = 0; i < cookies.size(); i++) {
                cookiespec.validate(cookies.get(i), origin);
            }
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException e) {}
    }

    // ------------------------------------------------------- Test Cookie Matching

    /**
     * test cookie <tt>Path</tt> matching. Cookie path attribute must path-match
     * path of the request URI.
     */
    public void testMatchPath() throws Exception {
        BasicClientCookie2 cookie = new BasicClientCookie2("name", "value");
        cookie.setDomain(".domain.com");
        cookie.setPath("/path");
        cookie.setPorts(new int[] {80});
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin1 = new CookieOrigin("www.domain.com", 80, "/", false);
        assertFalse(cookiespec.match(cookie, origin1));
        CookieOrigin origin2 = new CookieOrigin("www.domain.com", 80, "/path/path1", false);
        assertTrue(cookiespec.match(cookie, origin2));
    }

    /**
     * test cookie <tt>Domain</tt> matching.
     */
    public void testMatchDomain() throws Exception {
        BasicClientCookie2 cookie = new BasicClientCookie2("name", "value");
        cookie.setDomain(".domain.com");
        cookie.setPath("/");
        cookie.setPorts(new int[] {80});
        CookieSpec cookiespec = new RFC2965Spec();
        // effective host name minus domain must not contain any dots
        CookieOrigin origin1 = new CookieOrigin("a.b.domain.com" /* request host */, 80, "/", false);
        assertFalse(cookiespec.match(cookie, origin1));
        // The effective host name MUST domain-match the Domain
        // attribute of the cookie.
        CookieOrigin origin2 = new CookieOrigin("www.domain.org" /* request host */, 80, "/", false);
        assertFalse(cookiespec.match(cookie, origin2));
        CookieOrigin origin3 = new CookieOrigin("www.domain.com" /* request host */, 80, "/", false);
        assertTrue(cookiespec.match(cookie, origin3));
    }

    /**
     * test cookie local <tt>Domain</tt> matching.
     */
    public void testMatchDomainLocal() throws Exception {
        BasicClientCookie2 cookie = new BasicClientCookie2("name", "value");
        cookie.setDomain(".local");
        cookie.setPath("/");
        cookie.setPorts(new int[] {80});
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin1 = new CookieOrigin("host" /* request host */, 80, "/", false);
        assertTrue(cookiespec.match(cookie, origin1));
        CookieOrigin origin2 = new CookieOrigin("host.com" /* request host */, 80, "/", false);
        assertFalse(cookiespec.match(cookie, origin2));
    }

    /**
     * test cookie <tt>Port</tt> matching.
     */
    public void testMatchPort() throws Exception {
        // cookie can be sent to any port if port attribute not specified
        BasicClientCookie2 cookie = new BasicClientCookie2("name", "value");
        cookie.setDomain(".domain.com");
        cookie.setPath("/");
        cookie.setPorts(null);
        
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin1 = new CookieOrigin("www.domain.com", 8080 /* request port */, "/", false);
        assertTrue(cookiespec.match(cookie, origin1));
        CookieOrigin origin2 = new CookieOrigin("www.domain.com", 323 /* request port */, "/", false);
        assertTrue(cookiespec.match(cookie, origin2));

        // otherwise, request port must be in cookie's port list
        cookie = new BasicClientCookie2("name", "value");
        cookie.setDomain(".domain.com");
        cookie.setPath("/");
        cookie.setPorts(new int[] {80, 8080});
        cookie.setAttribute(ClientCookie.PORT_ATTR, "80, 8080");
        CookieOrigin origin3 = new CookieOrigin("www.domain.com", 434 /* request port */, "/", false);
        assertFalse(cookiespec.match(cookie, origin3));
        CookieOrigin origin4 = new CookieOrigin("www.domain.com", 8080 /* request port */, "/", false);
        assertTrue(cookiespec.match(cookie, origin4));
    }

    /**
     * test cookie expiration.
     */
    public void testCookieExpiration() throws Exception {
        Date now = new Date();

        Date beforeOneHour = new Date(now.getTime() - 3600 * 1000L);
        BasicClientCookie2 cookie = new BasicClientCookie2("name", "value");
        cookie.setDomain(".domain.com");
        cookie.setPath("/");
        cookie.setPorts(null);
        cookie.setExpiryDate(beforeOneHour);

        assertTrue(cookie.isExpired(now));

        Date afterOneHour = new Date(now.getTime() + 3600 * 1000L);
        cookie = new BasicClientCookie2("name", "value");
        cookie.setDomain(".domain.com");
        cookie.setPath("/");
        cookie.setPorts(null);
        cookie.setExpiryDate(afterOneHour);

        assertFalse(cookie.isExpired(now));

        // discard attributes overrides cookie age, makes it a session cookie.
        cookie.setDiscard(true);
        assertFalse(cookie.isPersistent());
        assertTrue(cookie.isExpired(now));
    }

    /**
     * test cookie <tt>Secure</tt> attribute.
     */
    public void testCookieSecure() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        // secure cookie can only be sent over a secure connection
        BasicClientCookie2 cookie = new BasicClientCookie2("name", "value");
        cookie.setDomain(".domain.com");
        cookie.setPath("/");
        cookie.setSecure(true);
        CookieOrigin origin1 = new CookieOrigin("www.domain.com", 80, "/", false);
        assertFalse(cookiespec.match(cookie, origin1));
        CookieOrigin origin2 = new CookieOrigin("www.domain.com", 80, "/", true);
        assertTrue(cookiespec.match(cookie, origin2));
    }

    // ------------------------------------------------------- Test Cookie Formatting

    /**
     * Tests RFC 2965 compliant cookie formatting.
     */
    public void testRFC2965CookieFormatting() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec(null, true);
        BasicClientCookie2 cookie1 = new BasicClientCookie2("name1", "value");
        cookie1.setDomain(".domain.com");
        cookie1.setPath("/");
        cookie1.setPorts(new int[] {80,8080});
        cookie1.setVersion(1);
        // domain, path, port specified
        cookie1.setAttribute(ClientCookie.DOMAIN_ATTR, ".domain.com");
        cookie1.setAttribute(ClientCookie.PATH_ATTR, "/");
        cookie1.setAttribute(ClientCookie.PORT_ATTR, "80,8080");
        
        List<Cookie> cookies = new ArrayList<Cookie>();
        cookies.add(cookie1);
        List<Header> headers = cookiespec.formatCookies(cookies);
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("$Version=1; name1=\"value\"; $Path=\"/\"; $Domain=\".domain.com\"; $Port=\"80,8080\"",
                headers.get(0).getValue());

        
        BasicClientCookie2 cookie2 = new BasicClientCookie2("name2", "value");
        cookie2.setDomain(".domain.com");
        cookie2.setPath("/a/");
        cookie2.setPorts(new int[] {80,8080});
        cookie2.setVersion(2);
        // domain, path specified  but port unspecified
        cookie2.setAttribute(ClientCookie.DOMAIN_ATTR, ".domain.com");
        cookie2.setAttribute(ClientCookie.PATH_ATTR, "/a/");
        
        cookies = new ArrayList<Cookie>();
        cookies.add(cookie2);
        headers = cookiespec.formatCookies(cookies);
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("$Version=2; name2=\"value\"; $Path=\"/a/\"; $Domain=\".domain.com\"",
                headers.get(0).getValue());

        BasicClientCookie2 cookie3 = new BasicClientCookie2("name3", "value");
        cookie3.setDomain(".domain.com");
        cookie3.setPath("/a/b/");
        cookie3.setPorts(new int[] {80,8080});
        cookie3.setVersion(1);
        // path specified, port specified but blank, domain unspecified
        cookie3.setAttribute(ClientCookie.PATH_ATTR, "/a/b/");
        cookie3.setAttribute(ClientCookie.PORT_ATTR, "  ");

        cookies = new ArrayList<Cookie>();
        cookies.add(cookie3);
        headers = cookiespec.formatCookies(cookies);
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("$Version=1; name3=\"value\"; $Path=\"/a/b/\"; $Port=\"\"",
                headers.get(0).getValue());

        cookies = new ArrayList<Cookie>();
        cookies.add(cookie3);
        cookies.add(cookie2);
        cookies.add(cookie1);
        headers = cookiespec.formatCookies(cookies);
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("$Version=1; " +
                "name3=\"value\"; $Path=\"/a/b/\"; $Port=\"\"; " +
                "name2=\"value\"; $Path=\"/a/\"; $Domain=\".domain.com\"; " +
                "name1=\"value\"; $Path=\"/\"; $Domain=\".domain.com\"; $Port=\"80,8080\"",
                headers.get(0).getValue());
    }

    /**
     * Tests RFC 2965 compliant cookies formatting.
     */
    public void testRFC2965CookiesFormatting() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec(null, true);
        BasicClientCookie2 cookie1 = new BasicClientCookie2("name1", "value1");
        cookie1.setDomain(".domain.com");
        cookie1.setPath("/");
        cookie1.setPorts(new int[] {80,8080});
        cookie1.setVersion(1);
        // domain, path, port specified
        cookie1.setAttribute(ClientCookie.DOMAIN_ATTR, ".domain.com");
        cookie1.setAttribute(ClientCookie.PATH_ATTR, "/");
        cookie1.setAttribute(ClientCookie.PORT_ATTR, "80,8080");
        
        BasicClientCookie2 cookie2 = new BasicClientCookie2("name2", "");
        cookie2.setDomain(".domain.com");
        cookie2.setPath("/");
        cookie2.setPorts(new int[] {80,8080});
        cookie2.setVersion(1);
        // value null, domain, path specified
        cookie2.setAttribute(ClientCookie.DOMAIN_ATTR, ".domain.com");
        cookie2.setAttribute(ClientCookie.PATH_ATTR, "/");
        
        List<Cookie> cookies = new ArrayList<Cookie>();
        cookies.add(cookie1);
        cookies.add(cookie2);
        List<Header> headers = cookiespec.formatCookies(cookies);
        assertNotNull(headers);
        assertEquals(1, headers.size());
        
        assertEquals("$Version=1; name1=\"value1\"; $Path=\"/\"; $Domain=\".domain.com\"; $Port=\"80,8080\"; " +
            "name2=\"\"; $Path=\"/\"; $Domain=\".domain.com\"", 
            headers.get(0).getValue());
    }

    // ------------------------------------------------------- Backward compatibility tests

    /**
     * Test rejection of <tt>Set-Cookie</tt> header.
     */
    public void testRejectSetCookie() throws Exception {
        CookieSpec cookiespec = new RFC2965Spec();
        CookieOrigin origin = new CookieOrigin("www.domain.com", 80, "/", false);
        Header header = new BasicHeader("Set-Cookie", "name=value; domain=.domain.com; version=1");
        try {
            cookiespec.parse(header, origin);
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

}

