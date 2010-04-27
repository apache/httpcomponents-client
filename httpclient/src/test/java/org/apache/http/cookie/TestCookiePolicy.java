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

package org.apache.http.cookie;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.impl.cookie.NetscapeDraftSpecFactory;
import org.apache.http.impl.cookie.RFC2109SpecFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link CookieSpecRegistry}.
 */
public class TestCookiePolicy {

    private static final String BROWSER_COMPATIBILITY    = "BROWSER_COMPATIBILITY";
    private static final String NETSCAPE                 = "NETSCAPE";
    private static final String RFC_2109                 = "RFC_2109";

    @Test
    public void testRegisterUnregisterCookieSpecFactory() {
        CookieSpecRegistry registry  = new CookieSpecRegistry();
        List<String> names = registry.getSpecNames();
        Assert.assertNotNull(names);
        Assert.assertEquals(0, names.size());

        registry.register(BROWSER_COMPATIBILITY,
                new BrowserCompatSpecFactory());
        registry.register(NETSCAPE,
                new NetscapeDraftSpecFactory());
        registry.register(RFC_2109,
                new RFC2109SpecFactory());
        registry.register(RFC_2109,
                new RFC2109SpecFactory());
        registry.register(NETSCAPE,
                new NetscapeDraftSpecFactory());

        names = registry.getSpecNames();
        Assert.assertNotNull(names);
        Assert.assertEquals(3, names.size());
        HashSet<String> m = new HashSet<String>(names);
        Assert.assertTrue(m.contains(BROWSER_COMPATIBILITY.toLowerCase(Locale.ENGLISH)));
        Assert.assertTrue(m.contains(NETSCAPE.toLowerCase(Locale.ENGLISH)));
        Assert.assertTrue(m.contains(RFC_2109.toLowerCase(Locale.ENGLISH)));

        registry.unregister(NETSCAPE);
        registry.unregister(NETSCAPE);
        registry.unregister(RFC_2109);
        registry.unregister(BROWSER_COMPATIBILITY);
        registry.unregister("whatever");

        names = registry.getSpecNames();
        Assert.assertNotNull(names);
        Assert.assertEquals(0, names.size());
    }

    @Test
    public void testGetNewCookieSpec() {
        CookieSpecRegistry registry  = new CookieSpecRegistry();
        registry.register(BROWSER_COMPATIBILITY,
                new BrowserCompatSpecFactory());
        registry.register(NETSCAPE,
                new NetscapeDraftSpecFactory());
        registry.register(RFC_2109,
                new RFC2109SpecFactory());

        Assert.assertNotNull(registry.getCookieSpec(NETSCAPE));
        Assert.assertNotNull(registry.getCookieSpec(RFC_2109));
        Assert.assertNotNull(registry.getCookieSpec(BROWSER_COMPATIBILITY));
        try {
            registry.getCookieSpec("whatever");
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
        HttpParams params = new BasicHttpParams();
        Assert.assertNotNull(registry.getCookieSpec(NETSCAPE, params));
        Assert.assertNotNull(registry.getCookieSpec(RFC_2109, params));
        Assert.assertNotNull(registry.getCookieSpec(BROWSER_COMPATIBILITY, params));
        try {
            registry.getCookieSpec("whatever", params);
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
    }

    @Test
    public void testInvalidInput() {
        CookieSpecRegistry registry  = new CookieSpecRegistry();
        try {
            registry.register(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            registry.register("whatever", null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            registry.unregister(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            registry.getCookieSpec((String)null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}

