/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

package org.apache.http.cookie.impl;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestAbstractCookieSpec extends TestCase {

    public TestAbstractCookieSpec(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(TestAbstractCookieSpec.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestAbstractCookieSpec.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    private static class DummyCookieSpec extends AbstractCookieSpec {

        public Header[] formatCookies(Cookie[] cookies) {
            return null;
        }

        public boolean match(Cookie cookie, CookieOrigin origin) {
            return true;
        }

        public Cookie[] parse(Header header, CookieOrigin origin) throws MalformedCookieException {
            return null;
        }

        public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
        }
        
    }
    
    private static class DummyCookieAttribHandler implements CookieAttributeHandler {

        public boolean match(Cookie cookie, CookieOrigin origin) {
            return true;
        }

        public void parse(Cookie cookie, String value) throws MalformedCookieException {
        }

        public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
        }
        
    }
    
    public void testSimpleRegisterAndGet() throws IOException {
        CookieAttributeHandler h1 = new DummyCookieAttribHandler();
        CookieAttributeHandler h2 = new DummyCookieAttribHandler();
        
        AbstractCookieSpec cookiespec = new DummyCookieSpec();
        cookiespec.registerAttribHandler("this", h1);
        cookiespec.registerAttribHandler("that", h2);
        
        assertTrue(h1 == cookiespec.getAttribHandler("this"));
        assertTrue(h2 == cookiespec.getAttribHandler("that"));
    }

    public void testInvalidHandler() throws IOException {
        CookieAttributeHandler h1 = new DummyCookieAttribHandler();
        CookieAttributeHandler h2 = new DummyCookieAttribHandler();
        
        AbstractCookieSpec cookiespec = new DummyCookieSpec();
        cookiespec.registerAttribHandler("this", h1);
        cookiespec.registerAttribHandler("that", h2);
        
        assertNull(cookiespec.findAttribHandler("whatever"));
        try {
            cookiespec.getAttribHandler("whatever");
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
    }
    
}