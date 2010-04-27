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

import java.io.InputStreamReader;
import java.io.Reader;

import junit.framework.Assert;

import org.apache.http.cookie.CookieOrigin;
import org.junit.Before;
import org.junit.Test;

public class TestPublicSuffixListParser {
    private static final String LIST_FILE = "/suffixlist.txt";
    private PublicSuffixFilter filter;

    @Before
    public void setUp() throws Exception {
        Reader r = new InputStreamReader(getClass().getResourceAsStream(LIST_FILE), "UTF-8");
        filter = new PublicSuffixFilter(new RFC2109DomainHandler());
        PublicSuffixListParser parser = new PublicSuffixListParser(filter);
        parser.parse(r);
    }

    @Test
    public void testParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");

        cookie.setDomain(".jp");
        Assert.assertFalse(filter.match(cookie, new CookieOrigin("apache.jp", 80, "/stuff", false)));

        cookie.setDomain(".ac.jp");
        Assert.assertFalse(filter.match(cookie, new CookieOrigin("apache.ac.jp", 80, "/stuff", false)));

        cookie.setDomain(".any.tokyo.jp");
        Assert.assertFalse(filter.match(cookie, new CookieOrigin("apache.any.tokyo.jp", 80, "/stuff", false)));

        // exception
        cookie.setDomain(".metro.tokyo.jp");
        Assert.assertTrue(filter.match(cookie, new CookieOrigin("apache.metro.tokyo.jp", 80, "/stuff", false)));
    }

    @Test
    public void testUnicode() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");

        cookie.setDomain(".h\u00E5.no"); // \u00E5 is <aring>
        Assert.assertFalse(filter.match(cookie, new CookieOrigin("apache.h\u00E5.no", 80, "/stuff", false)));

        cookie.setDomain(".xn--h-2fa.no");
        Assert.assertFalse(filter.match(cookie, new CookieOrigin("apache.xn--h-2fa.no", 80, "/stuff", false)));

        cookie.setDomain(".h\u00E5.no");
        Assert.assertFalse(filter.match(cookie, new CookieOrigin("apache.xn--h-2fa.no", 80, "/stuff", false)));

        cookie.setDomain(".xn--h-2fa.no");
        Assert.assertFalse(filter.match(cookie, new CookieOrigin("apache.h\u00E5.no", 80, "/stuff", false)));
    }

    @Test
    public void testWhitespace() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(".xx");
        Assert.assertFalse(filter.match(cookie, new CookieOrigin("apache.xx", 80, "/stuff", false)));

        // yy appears after whitespace
        cookie.setDomain(".yy");
        Assert.assertTrue(filter.match(cookie, new CookieOrigin("apache.yy", 80, "/stuff", false)));

        // zz is commented
        cookie.setDomain(".zz");
        Assert.assertTrue(filter.match(cookie, new CookieOrigin("apache.zz", 80, "/stuff", false)));
    }

}
