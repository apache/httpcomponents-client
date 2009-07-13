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

import java.util.Iterator;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.Header;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SetCookie;

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

        public List<Header> formatCookies(List<Cookie> cookies) {
            return null;
        }

        public boolean match(Cookie cookie, CookieOrigin origin) {
            return true;
        }

        public List<Cookie> parse(Header header, CookieOrigin origin) throws MalformedCookieException {
            return null;
        }

        public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
        }
        
        public int getVersion() {
            return 0;
        }

        public Header getVersionHeader() {
            return null;
        }
        
    }
    
    private static class DummyCookieAttribHandler implements CookieAttributeHandler {

        public boolean match(Cookie cookie, CookieOrigin origin) {
            return true;
        }

        public void parse(SetCookie cookie, String value) throws MalformedCookieException {
        }

        public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
        }
        
    }
    
    public void testSimpleRegisterAndGet() {
        CookieAttributeHandler h1 = new DummyCookieAttribHandler();
        CookieAttributeHandler h2 = new DummyCookieAttribHandler();
        
        AbstractCookieSpec cookiespec = new DummyCookieSpec();
        cookiespec.registerAttribHandler("this", h1);
        cookiespec.registerAttribHandler("that", h2);
        cookiespec.registerAttribHandler("thistoo", h1);
        cookiespec.registerAttribHandler("thattoo", h2);

        assertTrue(h1 == cookiespec.getAttribHandler("this"));
        assertTrue(h2 == cookiespec.getAttribHandler("that"));
        assertTrue(h1 == cookiespec.getAttribHandler("thistoo"));
        assertTrue(h2 == cookiespec.getAttribHandler("thattoo"));
        
        Iterator<CookieAttributeHandler> it = cookiespec.getAttribHandlers().iterator();
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertFalse(it.hasNext());
    }

    public void testInvalidHandler() {
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

    public void testBasicPathInvalidInput() throws Exception {
        AbstractCookieSpec cookiespec = new DummyCookieSpec();
        try {
            cookiespec.registerAttribHandler(null, null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            cookiespec.registerAttribHandler("whatever", null);
            fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}
