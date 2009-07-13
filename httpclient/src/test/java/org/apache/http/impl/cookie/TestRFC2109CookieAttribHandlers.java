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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;

public class TestRFC2109CookieAttribHandlers extends TestCase {

    public TestRFC2109CookieAttribHandlers(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(TestRFC2109CookieAttribHandlers.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestRFC2109CookieAttribHandlers.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testRFC2109DomainParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieAttributeHandler h = new RFC2109DomainHandler();
        
        h.parse(cookie, "somehost");
        assertEquals("somehost", cookie.getDomain());

        try {
            h.parse(cookie, null);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        try {
            h.parse(cookie, "  ");
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testRFC2109DomainValidate1() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109DomainHandler();
        
        cookie.setDomain("somehost");
        h.validate(cookie, origin);

        cookie.setDomain("otherhost");
        try {
            h.validate(cookie, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        cookie.setDomain(null);
        try {
            h.validate(cookie, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testRFC2109DomainValidate2() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieOrigin origin = new CookieOrigin("www.somedomain.com", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109DomainHandler();
        
        cookie.setDomain(".somedomain.com");
        h.validate(cookie, origin);

        cookie.setDomain(".otherdomain.com");
        try {
            h.validate(cookie, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        cookie.setDomain("www.otherdomain.com");
        try {
            h.validate(cookie, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testRFC2109DomainValidate3() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieOrigin origin = new CookieOrigin("www.a.com", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109DomainHandler();
        
        cookie.setDomain(".a.com");
        h.validate(cookie, origin);

        cookie.setDomain(".com");
        try {
            h.validate(cookie, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testRFC2109DomainValidate4() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieOrigin origin = new CookieOrigin("www.a.b.c", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109DomainHandler();
        
        cookie.setDomain(".a.b.c");
        h.validate(cookie, origin);

        cookie.setDomain(".b.c");
        try {
            h.validate(cookie, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        cookie.setDomain(".a.a.b.c");
        try {
            h.validate(cookie, origin);
            fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }
    
    public void testRFC2109DomainMatch1() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieOrigin origin = new CookieOrigin("www.somedomain.com", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109DomainHandler();

        cookie.setDomain(null);
        assertFalse(h.match(cookie, origin));
        
        cookie.setDomain(".somedomain.com");
        assertTrue(h.match(cookie, origin));
    }

    public void testRFC2109DomainMatch2() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieOrigin origin = new CookieOrigin("www.whatever.somedomain.com", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109DomainHandler();

        cookie.setDomain(".somedomain.com");
        assertTrue(h.match(cookie, origin));
    }

    public void testRFC2109DomainMatch3() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109DomainHandler();

        cookie.setDomain("somedomain.com");
        assertTrue(h.match(cookie, origin));
    }

    public void testRFC2109DomainMatch4() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieOrigin origin = new CookieOrigin("www.somedomain.com", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109DomainHandler();

        cookie.setDomain("somedomain.com");
        assertFalse(h.match(cookie, origin));
    }

    public void testRFC2109DomainInvalidInput() throws Exception {
        CookieAttributeHandler h = new RFC2109DomainHandler();
        try {
            h.parse(null, null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.validate(null, null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.validate(new BasicClientCookie("name", "value"), null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.match(null, null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.match(new BasicClientCookie("name", "value"), null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
 
    public void testRFC2109VersionParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieAttributeHandler h = new RFC2109VersionHandler();
        h.parse(cookie, "12");
        assertEquals(12, cookie.getVersion());
    }

    public void testRFC2109VersionParseInvalid() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value"); 
        CookieAttributeHandler h = new RFC2109VersionHandler();
        try {
            h.parse(cookie, "garbage");
            fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        try {
            h.parse(cookie, null);
            fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        try {
            h.parse(cookie, "  ");
            fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testRFC2109VersionValidate() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false); 
        CookieAttributeHandler h = new RFC2109VersionHandler();

        cookie.setVersion(12);
        h.validate(cookie, origin);
        
        cookie.setVersion(-12);
        try {
            h.validate(cookie, origin);
            fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    public void testRFC2109VersionInvalidInput() throws Exception {
        CookieAttributeHandler h = new RFC2109VersionHandler();
        try {
            h.parse(null, null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.validate(null, null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
        
}
