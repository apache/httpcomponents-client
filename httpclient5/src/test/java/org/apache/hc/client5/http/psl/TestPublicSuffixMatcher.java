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

public class TestPublicSuffixMatcher {

    private static final String SOURCE_FILE = "suffixlistmatcher.txt";

    private PublicSuffixMatcher matcher;

    @BeforeEach
    public void setUp() throws Exception {
        final ClassLoader classLoader = getClass().getClassLoader();
        final InputStream in = classLoader.getResourceAsStream(SOURCE_FILE);
        Assertions.assertNotNull(in);
        final List<PublicSuffixList> lists = PublicSuffixListParser.INSTANCE.parseByType(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        matcher = new PublicSuffixMatcher(lists);
    }

    @Test
    public void testGetDomainRootAnyType() {
        // Private
        Assertions.assertEquals("xx", matcher.getDomainRoot("example.XX"));
        Assertions.assertEquals("xx", matcher.getDomainRoot("www.example.XX"));
        Assertions.assertEquals("xx", matcher.getDomainRoot("www.blah.blah.example.XX"));
        Assertions.assertEquals("appspot.com", matcher.getDomainRoot("example.appspot.com"));
        // Too short
        Assertions.assertNull(matcher.getDomainRoot("jp"));
        Assertions.assertNull(matcher.getDomainRoot("ac.jp"));
        Assertions.assertNull(matcher.getDomainRoot("any.tokyo.jp"));
        // ICANN
        Assertions.assertEquals("metro.tokyo.jp", matcher.getDomainRoot("metro.tokyo.jp"));
        Assertions.assertEquals("blah.blah.tokyo.jp", matcher.getDomainRoot("blah.blah.tokyo.jp"));
        Assertions.assertEquals("blah.ac.jp", matcher.getDomainRoot("blah.blah.ac.jp"));
        // Unknown
        Assertions.assertEquals("garbage", matcher.getDomainRoot("garbage"));
        Assertions.assertEquals("garbage", matcher.getDomainRoot("garbage.garbage"));
        Assertions.assertEquals("garbage", matcher.getDomainRoot("*.garbage.garbage"));
        Assertions.assertEquals("garbage", matcher.getDomainRoot("*.garbage.garbage.garbage"));

        Assertions.assertEquals("*.compute-1.amazonaws.com", matcher.getDomainRoot("*.compute-1.amazonaws.com"));
    }

    @Test
    public void testGetDomainRootOnlyPRIVATE() {
        // Private
        Assertions.assertEquals("xx", matcher.getDomainRoot("example.XX", DomainType.PRIVATE));
        Assertions.assertEquals("xx", matcher.getDomainRoot("www.example.XX", DomainType.PRIVATE));
        Assertions.assertEquals("xx", matcher.getDomainRoot("www.blah.blah.example.XX", DomainType.PRIVATE));
        Assertions.assertEquals("appspot.com", matcher.getDomainRoot("example.appspot.com"));
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
    }

    @Test
    public void testGetDomainRootOnlyICANN() {
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
    public void testMatch() {
        Assertions.assertTrue(matcher.matches(".jp"));
        Assertions.assertTrue(matcher.matches(".ac.jp"));
        Assertions.assertTrue(matcher.matches(".any.tokyo.jp"));
        // exception
        Assertions.assertFalse(matcher.matches(".metro.tokyo.jp"));
        Assertions.assertFalse(matcher.matches(".xx"));
        Assertions.assertFalse(matcher.matches(".appspot.com"));
    }

    @Test
    public void testMatchUnicode() {
        Assertions.assertTrue(matcher.matches(".h\u00E5.no")); // \u00E5 is <aring>
        Assertions.assertTrue(matcher.matches(".xn--h-2fa.no"));
        Assertions.assertTrue(matcher.matches(".h\u00E5.no"));
        Assertions.assertTrue(matcher.matches(".xn--h-2fa.no"));
    }

}
