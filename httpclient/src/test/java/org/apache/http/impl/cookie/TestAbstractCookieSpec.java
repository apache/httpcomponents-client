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

import org.apache.http.Header;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SetCookie;
import org.junit.Assert;
import org.junit.Test;

public class TestAbstractCookieSpec {

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

    @Test
    public void testSimpleRegisterAndGet() {
        CookieAttributeHandler h1 = new DummyCookieAttribHandler();
        CookieAttributeHandler h2 = new DummyCookieAttribHandler();

        AbstractCookieSpec cookiespec = new DummyCookieSpec();
        cookiespec.registerAttribHandler("this", h1);
        cookiespec.registerAttribHandler("that", h2);
        cookiespec.registerAttribHandler("thistoo", h1);
        cookiespec.registerAttribHandler("thattoo", h2);

        Assert.assertTrue(h1 == cookiespec.getAttribHandler("this"));
        Assert.assertTrue(h2 == cookiespec.getAttribHandler("that"));
        Assert.assertTrue(h1 == cookiespec.getAttribHandler("thistoo"));
        Assert.assertTrue(h2 == cookiespec.getAttribHandler("thattoo"));

        Iterator<CookieAttributeHandler> it = cookiespec.getAttribHandlers().iterator();
        Assert.assertNotNull(it.next());
        Assert.assertNotNull(it.next());
        Assert.assertNotNull(it.next());
        Assert.assertNotNull(it.next());
        Assert.assertFalse(it.hasNext());
    }

    @Test(expected=IllegalStateException.class)
    public void testInvalidHandler() {
        CookieAttributeHandler h1 = new DummyCookieAttribHandler();
        CookieAttributeHandler h2 = new DummyCookieAttribHandler();

        AbstractCookieSpec cookiespec = new DummyCookieSpec();
        cookiespec.registerAttribHandler("this", h1);
        cookiespec.registerAttribHandler("that", h2);

        Assert.assertNull(cookiespec.findAttribHandler("whatever"));
        cookiespec.getAttribHandler("whatever");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBasicPathInvalidInput1() throws Exception {
        AbstractCookieSpec cookiespec = new DummyCookieSpec();
        cookiespec.registerAttribHandler(null, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBasicPathInvalidInput2() throws Exception {
        AbstractCookieSpec cookiespec = new DummyCookieSpec();
        cookiespec.registerAttribHandler("whatever", null);
    }

}
