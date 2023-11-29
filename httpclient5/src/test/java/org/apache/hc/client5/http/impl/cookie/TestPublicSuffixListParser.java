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

package org.apache.hc.client5.http.impl.cookie;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.psl.PublicSuffixList;
import org.apache.hc.client5.http.psl.PublicSuffixListParser;
import org.apache.hc.client5.http.psl.PublicSuffixMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestPublicSuffixListParser {

    private static final String SOURCE_FILE = "suffixlist.txt";

    private PublicSuffixDomainFilter filter;

    @BeforeEach
    public void setUp() throws Exception {
        final ClassLoader classLoader = getClass().getClassLoader();
        final InputStream in = classLoader.getResourceAsStream(SOURCE_FILE);
        Assertions.assertNotNull(in);
        final PublicSuffixList suffixList;
        try {
            final PublicSuffixListParser parser = PublicSuffixListParser.INSTANCE;
            suffixList = parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } finally {
            in.close();
        }
        final PublicSuffixMatcher matcher = new PublicSuffixMatcher(suffixList.getRules(), suffixList.getExceptions());
        this.filter = new PublicSuffixDomainFilter(BasicDomainHandler.INSTANCE, matcher);
    }

    @Test
    public void testParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");

        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".jp");
        cookie.setDomain(".jp");
        Assertions.assertFalse(filter.match(cookie, new CookieOrigin("apache.jp", 80, "/stuff", false)));

        cookie.setDomain(".ac.jp");
        Assertions.assertFalse(filter.match(cookie, new CookieOrigin("apache.ac.jp", 80, "/stuff", false)));

        cookie.setDomain(".any.tokyo.jp");
        Assertions.assertFalse(filter.match(cookie, new CookieOrigin("apache.any.tokyo.jp", 80, "/stuff", false)));

        // exception
        cookie.setDomain(".metro.tokyo.jp");
        Assertions.assertTrue(filter.match(cookie, new CookieOrigin("apache.metro.tokyo.jp", 80, "/stuff", false)));
    }

    @Test
    public void testParseLocal() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");

        cookie.setDomain("localhost");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "localhost");
        Assertions.assertTrue(filter.match(cookie, new CookieOrigin("localhost", 80, "/stuff", false)));

        cookie.setDomain("somehost");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "somehost");
        Assertions.assertTrue(filter.match(cookie, new CookieOrigin("somehost", 80, "/stuff", false)));

        cookie.setDomain(".localdomain");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".localdomain");
        Assertions.assertTrue(filter.match(cookie, new CookieOrigin("somehost.localdomain", 80, "/stuff", false)));

        cookie.setDomain(".local.");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".local.");
        Assertions.assertTrue(filter.match(cookie, new CookieOrigin("somehost.local.", 80, "/stuff", false)));

        cookie.setDomain(".localhost.");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".localhost.");
        Assertions.assertTrue(filter.match(cookie, new CookieOrigin("somehost.localhost.", 80, "/stuff", false)));

        cookie.setDomain(".local");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".local");
        Assertions.assertTrue(filter.match(cookie, new CookieOrigin("somehost.local", 80, "/stuff", false)));

        cookie.setDomain(".blah");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".blah");
        Assertions.assertTrue(filter.match(cookie, new CookieOrigin("somehost.blah", 80, "/stuff", false)));
    }

    @Test
    public void testUnicode() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");

        cookie.setDomain(".h\u00E5.no"); // \u00E5 is <aring>
        Assertions.assertFalse(filter.match(cookie, new CookieOrigin("apache.h\u00E5.no", 80, "/stuff", false)));

        cookie.setDomain(".xn--h-2fa.no");
        Assertions.assertFalse(filter.match(cookie, new CookieOrigin("apache.xn--h-2fa.no", 80, "/stuff", false)));

        cookie.setDomain(".h\u00E5.no");
        Assertions.assertFalse(filter.match(cookie, new CookieOrigin("apache.xn--h-2fa.no", 80, "/stuff", false)));

        cookie.setDomain(".xn--h-2fa.no");
        Assertions.assertFalse(filter.match(cookie, new CookieOrigin("apache.h\u00E5.no", 80, "/stuff", false)));
    }

}
