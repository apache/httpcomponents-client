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

package org.apache.http.conn.util;

import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.Consts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestPublicSuffixMatcher {

    private static final String SOURCE_FILE = "suffixlist.txt";

    private PublicSuffixMatcher matcher;

    @Before
    public void setUp() throws Exception {
        final ClassLoader classLoader = getClass().getClassLoader();
        final InputStream in = classLoader.getResourceAsStream(SOURCE_FILE);
        Assert.assertNotNull(in);
        final PublicSuffixList suffixList;
        try {
            final PublicSuffixListParser parser = new PublicSuffixListParser();
            suffixList = parser.parse(new InputStreamReader(in, Consts.UTF_8));
        } finally {
            in.close();
        }
        matcher = new PublicSuffixMatcher(suffixList.getRules(), suffixList.getExceptions());
    }

    @Test
    public void testGetDomainRoot() throws Exception {
        Assert.assertEquals("example.xx", matcher.getDomainRoot("example.XX"));
        Assert.assertEquals("example.xx", matcher.getDomainRoot("www.example.XX"));
        Assert.assertEquals("example.xx", matcher.getDomainRoot("www.blah.blah.example.XX"));
        Assert.assertEquals(null, matcher.getDomainRoot("xx"));
        Assert.assertEquals(null, matcher.getDomainRoot("jp"));
        Assert.assertEquals(null, matcher.getDomainRoot("example"));
        Assert.assertEquals("example.example", matcher.getDomainRoot("example.example"));
        Assert.assertEquals(null, matcher.getDomainRoot("ac.jp"));
        Assert.assertEquals(null, matcher.getDomainRoot("any.tokyo.jp"));
        Assert.assertEquals("metro.tokyo.jp", matcher.getDomainRoot("metro.tokyo.jp"));
        Assert.assertEquals("blah.blah.tokyo.jp", matcher.getDomainRoot("blah.blah.tokyo.jp"));
        Assert.assertEquals("blah.ac.jp", matcher.getDomainRoot("blah.blah.ac.jp"));
    }

    @Test
    public void testMatch() throws Exception {
        Assert.assertTrue(matcher.matches(".jp"));
        Assert.assertTrue(matcher.matches(".ac.jp"));
        Assert.assertTrue(matcher.matches(".any.tokyo.jp"));
        // exception
        Assert.assertFalse(matcher.matches(".metro.tokyo.jp"));
    }

    @Test
    public void testMatchUnicode() throws Exception {
        Assert.assertTrue(matcher.matches(".h\u00E5.no")); // \u00E5 is <aring>
        Assert.assertTrue(matcher.matches(".xn--h-2fa.no"));
        Assert.assertTrue(matcher.matches(".h\u00E5.no"));
        Assert.assertTrue(matcher.matches(".xn--h-2fa.no"));
    }

}
