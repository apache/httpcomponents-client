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

package org.apache.hc.client5.http.psl;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestPublicSuffixMatcher {

    private static final String SOURCE_FILE = "suffixlistmatcher.txt";

    private PublicSuffixMatcher matcher;
    private PublicSuffixMatcher pslMatcher;

    @BeforeEach
    void setUp() throws Exception {
        final ClassLoader classLoader = getClass().getClassLoader();
        // Create a matcher using a custom crafted public suffix list file
        try (InputStream in = classLoader.getResourceAsStream(SOURCE_FILE)) {
            Assertions.assertNotNull(in, SOURCE_FILE);
            final List<PublicSuffixList> lists = PublicSuffixListParser.INSTANCE.parseByType(new InputStreamReader(in, StandardCharsets.UTF_8));
            matcher = new PublicSuffixMatcher(lists);
        }
        // Create a matcher using the public suffix list file provided by publicsuffix.org (Mozilla).
        pslMatcher = PublicSuffixMatcherLoader.getDefault();
    }

    @Test
    void testGetDomainRootAnyType() {
        // ICANN
        Assertions.assertEquals(null, matcher.getDomainRoot("com"));
        Assertions.assertEquals("blah.com", matcher.getDomainRoot("blah.com"));
        Assertions.assertEquals("foo.com", matcher.getDomainRoot("foo.com"));
        Assertions.assertEquals(null, matcher.getDomainRoot("blah.foo.com"));
        Assertions.assertEquals(null, matcher.getDomainRoot("booh.foo.com"));
        Assertions.assertEquals("blah.blah.foo.com", matcher.getDomainRoot("blah.blah.foo.com"));

        Assertions.assertEquals(null, matcher.getDomainRoot("kioto.jp"));
        Assertions.assertEquals(null, matcher.getDomainRoot("tokyo.jp"));
        Assertions.assertEquals(null, matcher.getDomainRoot("blah.tokyo.jp"));
        Assertions.assertEquals(null, matcher.getDomainRoot("booh.tokyo.jp"));
        Assertions.assertEquals("blah.blah.tokyo.jp", matcher.getDomainRoot("blah.blah.tokyo.jp"));
        Assertions.assertEquals("metro.tokyo.jp", matcher.getDomainRoot("metro.tokyo.jp"));
        Assertions.assertEquals("blah.ac.jp", matcher.getDomainRoot("blah.ac.jp"));
        Assertions.assertEquals("blah.ac.jp", matcher.getDomainRoot("blah.blah.ac.jp"));
        Assertions.assertEquals("metro.tokyo.jp", matcher.getDomainRoot("metro.tokyo.jp"));

        // Private
        Assertions.assertEquals("example.xx", matcher.getDomainRoot("example.XX"));
        Assertions.assertEquals("example.xx", matcher.getDomainRoot("www.example.XX"));
        Assertions.assertEquals("example.xx", matcher.getDomainRoot("www.blah.blah.example.XX"));
        Assertions.assertEquals(null, matcher.getDomainRoot("appspot.com"));
        Assertions.assertEquals("example.appspot.com", matcher.getDomainRoot("example.appspot.com"));
        Assertions.assertEquals(null, matcher.getDomainRoot("s3.amazonaws.com"));
        Assertions.assertEquals(null, matcher.getDomainRoot("blah.s3.amazonaws.com"));
        // Too short
        Assertions.assertNull(matcher.getDomainRoot("jp"));
        Assertions.assertNull(matcher.getDomainRoot("ac.jp"));
        Assertions.assertNull(matcher.getDomainRoot("any.tokyo.jp"));
        // Unknown
        Assertions.assertEquals(null, matcher.getDomainRoot("garbage"));
        Assertions.assertEquals("garbage.garbage", matcher.getDomainRoot("garbage.garbage"));
        Assertions.assertEquals("garbage.garbage", matcher.getDomainRoot("*.garbage.garbage"));
        Assertions.assertEquals("garbage.garbage", matcher.getDomainRoot("*.garbage.garbage.garbage"));

        Assertions.assertEquals(null, matcher.getDomainRoot("*.compute-1.amazonaws.com"));
        Assertions.assertEquals(null, matcher.getDomainRoot("blah.compute-1.amazonaws.com"));
        Assertions.assertEquals("blah.blah.compute-1.amazonaws.com", matcher.getDomainRoot("blah.blah.compute-1.amazonaws.com"));
    }

    @Test
    void testGetDomainRootOnlyPRIVATE() {
        // Private
        Assertions.assertEquals("example.xx", matcher.getDomainRoot("example.XX", DomainType.PRIVATE));
        Assertions.assertEquals("example.xx", matcher.getDomainRoot("www.example.XX", DomainType.PRIVATE));
        Assertions.assertEquals("example.xx", matcher.getDomainRoot("www.blah.blah.example.XX", DomainType.PRIVATE));
        Assertions.assertEquals("example.appspot.com", matcher.getDomainRoot("example.appspot.com"));
        // Too short
        Assertions.assertNull(matcher.getDomainRoot("jp", DomainType.PRIVATE));
        Assertions.assertNull(matcher.getDomainRoot("ac.jp", DomainType.PRIVATE));
        Assertions.assertNull(matcher.getDomainRoot("any.tokyo.jp", DomainType.PRIVATE));
        // ICANN
        Assertions.assertNull(matcher.getDomainRoot("metro.tokyo.jp", DomainType.PRIVATE));
        Assertions.assertNull(matcher.getDomainRoot("blah.blah.tokyo.jp", DomainType.PRIVATE));
        Assertions.assertNull(matcher.getDomainRoot("blah.blah.ac.jp", DomainType.PRIVATE));
        // Unknown
        Assertions.assertNull(matcher.getDomainRoot("garbage", DomainType.PRIVATE));
        Assertions.assertNull(matcher.getDomainRoot("garbage.garbage", DomainType.PRIVATE));
        Assertions.assertNull(matcher.getDomainRoot("*.garbage.garbage", DomainType.PRIVATE));
        Assertions.assertNull(matcher.getDomainRoot("*.garbage.garbage.garbage", DomainType.PRIVATE));
        Assertions.assertNull(matcher.getDomainRoot("s3.amazonaws.com"));
        Assertions.assertNull(matcher.getDomainRoot("blah.s3.amazonaws.com"));
    }

    @Test
    void testGetDomainRootOnlyICANN() {
        // Private
        Assertions.assertNull(matcher.getDomainRoot("example.XX", DomainType.ICANN));
        Assertions.assertNull(matcher.getDomainRoot("www.example.XX", DomainType.ICANN));
        Assertions.assertNull(matcher.getDomainRoot("www.blah.blah.example.XX", DomainType.ICANN));
        // Too short
        Assertions.assertNull(matcher.getDomainRoot("xx", DomainType.ICANN));
        Assertions.assertNull(matcher.getDomainRoot("jp", DomainType.ICANN));
        Assertions.assertNull(matcher.getDomainRoot("ac.jp", DomainType.ICANN));
        Assertions.assertNull(matcher.getDomainRoot("any.tokyo.jp", DomainType.ICANN));
        // ICANN
        Assertions.assertEquals("metro.tokyo.jp", matcher.getDomainRoot("metro.tokyo.jp", DomainType.ICANN));
        Assertions.assertEquals("blah.blah.tokyo.jp", matcher.getDomainRoot("blah.blah.tokyo.jp", DomainType.ICANN));
        Assertions.assertEquals("blah.ac.jp", matcher.getDomainRoot("blah.blah.ac.jp", DomainType.ICANN));
        // Unknown
        Assertions.assertNull(matcher.getDomainRoot("garbage", DomainType.ICANN));
        Assertions.assertNull(matcher.getDomainRoot("garbage.garbage", DomainType.ICANN));
        Assertions.assertNull(matcher.getDomainRoot("*.garbage.garbage", DomainType.ICANN));
        Assertions.assertNull(matcher.getDomainRoot("*.garbage.garbage.garbage", DomainType.ICANN));
    }

    @Test
    void testMaySetCookies() {
        Assertions.assertTrue(matcher.verify("foo.com"));

        Assertions.assertFalse(matcher.verify("bar.foo.com"));
        Assertions.assertTrue(matcher.verify("example.bar.foo.com"));

        Assertions.assertTrue(matcher.verify("foo.bar.jp"));
        Assertions.assertFalse(matcher.verify("bar.jp"));

        Assertions.assertTrue(matcher.verify("foo.bar.hokkaido.jp"));
        Assertions.assertFalse(matcher.verify("bar.hokkaido.jp"));

        Assertions.assertTrue(matcher.verify("foo.bar.tokyo.jp"));
        Assertions.assertFalse(matcher.verify("bar.tokyo.jp"));

        Assertions.assertTrue(matcher.verify("pref.hokkaido.jp")); // exception from a wildcard rule
        Assertions.assertTrue(matcher.verify("metro.tokyo.jp")); // exception from a wildcard rule
    }

    @Test
    void testVerifyPrivate() {
        Assertions.assertTrue(matcher.verify("s3.amazonaws.com"));
        Assertions.assertTrue(matcher.verify("blah.s3.amazonaws.com"));
        Assertions.assertTrue(matcher.verify("blah.xxx.uk"));
    }

    @Test
    void testMatch() {
        Assertions.assertTrue(matcher.matches(".jp"));
        Assertions.assertTrue(matcher.matches(".ac.jp"));
        Assertions.assertTrue(matcher.matches(".any.tokyo.jp"));
        Assertions.assertTrue(matcher.matches(".xx"));
        Assertions.assertTrue(matcher.matches(".appspot.com"));
        // exception
        Assertions.assertFalse(matcher.matches(".metro.tokyo.jp"));
    }

    @Test
    void testMatchUnicode() {
        Assertions.assertTrue(matcher.matches(".h\u00E5.no")); // \u00E5 is <aring>
        Assertions.assertTrue(matcher.matches(".xn--h-2fa.no"));
        Assertions.assertTrue(matcher.matches(".h\u00E5.no"));
        Assertions.assertTrue(matcher.matches(".xn--h-2fa.no"));
    }

    private void checkPublicSuffix(final String input, final String expected) {
        Assertions.assertEquals(expected, pslMatcher.getDomainRoot(input));
    }

    //see https://github.com/publicsuffix/list/blob/master/tests/test_psl.txt
    @Test
    void testGetDomainRootPublicSuffixList() {
         // null input.
        checkPublicSuffix(null, null);
        // Mixed case.
        checkPublicSuffix("COM", null);
        checkPublicSuffix("example.COM", "example.com");
        checkPublicSuffix("WwW.example.COM", "example.com");
        // Leading dot.
        checkPublicSuffix(".com", null);
        checkPublicSuffix(".example", null);
        checkPublicSuffix(".example.com", null);
        checkPublicSuffix(".example.example", null);
        // Unlisted TLD.
        checkPublicSuffix("example", null);
        checkPublicSuffix("example.example", "example.example");
        checkPublicSuffix("b.example.example", "example.example");
        checkPublicSuffix("a.b.example.example", "example.example");
        // Listed, but non-Internet, TLD.
        //checkPublicSuffix("local", null);
        //checkPublicSuffix("example.local", null);
        //checkPublicSuffix("b.example.local", null);
        //checkPublicSuffix("a.b.example.local", null);
        // TLD with only 1 rule.
        checkPublicSuffix("biz", null);
        checkPublicSuffix("domain.biz", "domain.biz");
        checkPublicSuffix("b.domain.biz", "domain.biz");
        checkPublicSuffix("a.b.domain.biz", "domain.biz");
        // TLD with some 2-level rules.
        checkPublicSuffix("com", null);
        checkPublicSuffix("example.com", "example.com");
        checkPublicSuffix("b.example.com", "example.com");
        checkPublicSuffix("a.b.example.com", "example.com");
        checkPublicSuffix("uk.com", null);
        checkPublicSuffix("example.uk.com", "example.uk.com");
        checkPublicSuffix("b.example.uk.com", "example.uk.com");
        checkPublicSuffix("a.b.example.uk.com", "example.uk.com");
        checkPublicSuffix("test.ac", "test.ac");
        // TLD with only 1 (wildcard) rule.
        checkPublicSuffix("mm", null);
        checkPublicSuffix("c.mm", null);
        checkPublicSuffix("b.c.mm", "b.c.mm");
        checkPublicSuffix("a.b.c.mm", "b.c.mm");
        // More complex TLD.
        checkPublicSuffix("jp", null);
        checkPublicSuffix("test.jp", "test.jp");
        checkPublicSuffix("www.test.jp", "test.jp");
        checkPublicSuffix("ac.jp", null);
        checkPublicSuffix("test.ac.jp", "test.ac.jp");
        checkPublicSuffix("www.test.ac.jp", "test.ac.jp");
        checkPublicSuffix("kyoto.jp", null);
        checkPublicSuffix("test.kyoto.jp", "test.kyoto.jp");
        checkPublicSuffix("ide.kyoto.jp", null);
        checkPublicSuffix("b.ide.kyoto.jp", "b.ide.kyoto.jp");
        checkPublicSuffix("a.b.ide.kyoto.jp", "b.ide.kyoto.jp");
        checkPublicSuffix("c.kobe.jp", null);
        checkPublicSuffix("b.c.kobe.jp", "b.c.kobe.jp");
        checkPublicSuffix("a.b.c.kobe.jp", "b.c.kobe.jp");
        checkPublicSuffix("city.kobe.jp", "city.kobe.jp");
        checkPublicSuffix("www.city.kobe.jp", "city.kobe.jp");
        // TLD with a wildcard rule and exceptions.
        checkPublicSuffix("ck", null);
        checkPublicSuffix("test.ck", null);
        checkPublicSuffix("b.test.ck", "b.test.ck");
        checkPublicSuffix("a.b.test.ck", "b.test.ck");
        checkPublicSuffix("www.ck", "www.ck");
        checkPublicSuffix("www.www.ck", "www.ck");
        // US K12.
        checkPublicSuffix("us", null);
        checkPublicSuffix("test.us", "test.us");
        checkPublicSuffix("www.test.us", "test.us");
        checkPublicSuffix("ak.us", null);
        checkPublicSuffix("test.ak.us", "test.ak.us");
        checkPublicSuffix("www.test.ak.us", "test.ak.us");
        checkPublicSuffix("k12.ak.us", null);
        checkPublicSuffix("test.k12.ak.us", "test.k12.ak.us");
        checkPublicSuffix("www.test.k12.ak.us", "test.k12.ak.us");
        // IDN labels.
        checkPublicSuffix("食狮.com.cn", "食狮.com.cn");
        checkPublicSuffix("食狮.公司.cn", "食狮.公司.cn");
        checkPublicSuffix("www.食狮.公司.cn", "食狮.公司.cn");
        checkPublicSuffix("shishi.公司.cn", "shishi.公司.cn");
        checkPublicSuffix("公司.cn", null);
        checkPublicSuffix("食狮.中国", "食狮.中国");
        checkPublicSuffix("www.食狮.中国", "食狮.中国");
        checkPublicSuffix("shishi.中国", "shishi.中国");
        checkPublicSuffix("中国", null);
        // Same as above, but punycoded.
        checkPublicSuffix("xn--85x722f.com.cn", "xn--85x722f.com.cn");
        checkPublicSuffix("xn--85x722f.xn--55qx5d.cn", "xn--85x722f.xn--55qx5d.cn");
        checkPublicSuffix("www.xn--85x722f.xn--55qx5d.cn", "xn--85x722f.xn--55qx5d.cn");
        checkPublicSuffix("shishi.xn--55qx5d.cn", "shishi.xn--55qx5d.cn");
        checkPublicSuffix("xn--55qx5d.cn", null);
        checkPublicSuffix("xn--85x722f.xn--fiqs8s", "xn--85x722f.xn--fiqs8s");
        checkPublicSuffix("www.xn--85x722f.xn--fiqs8s", "xn--85x722f.xn--fiqs8s");
        checkPublicSuffix("shishi.xn--fiqs8s", "shishi.xn--fiqs8s");
        checkPublicSuffix("xn--fiqs8s", null);
    }

}
