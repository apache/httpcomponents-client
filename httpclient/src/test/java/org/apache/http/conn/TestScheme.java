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

package org.apache.http.conn;

import java.util.List;

import org.apache.http.HttpHost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.mockup.SecureSocketFactoryMockup;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link Scheme} and {@link SchemeRegistry}.
 */
public class TestScheme {

    @Test
    public void testConstructor() {
        Scheme http = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
        Assert.assertEquals("http", http.getName());
        Assert.assertEquals(80, http.getDefaultPort());
        Assert.assertFalse(http.isLayered());
        Scheme https = new Scheme("https", 443, SecureSocketFactoryMockup.INSTANCE);
        Assert.assertEquals("https", https.getName());
        Assert.assertEquals(443, https.getDefaultPort());
        Assert.assertTrue(https.isLayered());

        Scheme hTtP = new Scheme("hTtP", 80, PlainSocketFactory.getSocketFactory());
        Assert.assertEquals("http", hTtP.getName());
        // the rest is no different from above

        try {
            new Scheme(null, 80, PlainSocketFactory.getSocketFactory());
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new Scheme("http", 80, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new Scheme("http", -1, PlainSocketFactory.getSocketFactory());
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new Scheme("http", 70000, PlainSocketFactory.getSocketFactory());
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRegisterUnregister() {
        SchemeRegistry schmreg = new SchemeRegistry();

        Scheme http = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
        Scheme https = new Scheme("https", 443, SecureSocketFactoryMockup.INSTANCE);
        Scheme myhttp = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());

        HttpHost host  = new HttpHost("www.test.invalid", -1, "http");
        HttpHost hosts = new HttpHost("www.test.invalid", -1, "https");

        Assert.assertNull(schmreg.register(myhttp));
        Assert.assertNull(schmreg.register(https));
        Assert.assertSame(myhttp, schmreg.register(http));
        Assert.assertSame(http, schmreg.getScheme("http"));
        Assert.assertSame(http, schmreg.getScheme(host));
        Assert.assertSame(https, schmreg.getScheme("https"));
        Assert.assertSame(https, schmreg.getScheme(hosts));

        schmreg.unregister("http");
        schmreg.unregister("https");

        Assert.assertNull(schmreg.get("http")); // get() does not throw exception
        try {
            schmreg.getScheme("http"); // getScheme() does throw exception
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
    }

    @Test
    public void testIterator() {
        SchemeRegistry schmreg = new SchemeRegistry();

        List<String> names = schmreg.getSchemeNames();
        Assert.assertNotNull(names);
        Assert.assertTrue(names.isEmpty());

        Scheme http = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
        Scheme https = new Scheme("https", 443, SecureSocketFactoryMockup.INSTANCE);

        schmreg.register(http);
        schmreg.register(https);

        names = schmreg.getSchemeNames();
        Assert.assertNotNull(names);
        Assert.assertFalse(names.isEmpty());

        boolean flaghttp  = false;
        boolean flaghttps = false;
        String name = names.get(0);

        if ("http".equals(name))
            flaghttp = true;
        else if ("https".equals(name))
            flaghttps = true;
        else
            Assert.fail("unexpected name in iterator: " + name);

        Assert.assertNotNull(schmreg.get(name));
        schmreg.unregister(name);
        Assert.assertNull(schmreg.get(name));

        name = names.get(1);

        if ("http".equals(name)) {
            if (flaghttp) Assert.fail("name 'http' found twice");
        } else if ("https".equals(name)) {
            if (flaghttps) Assert.fail("name 'https' found twice");
        } else {
            Assert.fail("unexpected name in iterator: " + name);
        }

        Assert.assertNotNull(schmreg.get(name));
    }

    @Test
    public void testIllegalRegisterUnregister() {
        SchemeRegistry schmreg = new SchemeRegistry();
        try {
            schmreg.register(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            schmreg.unregister(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            schmreg.get(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            schmreg.getScheme((String)null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            schmreg.getScheme((HttpHost)null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testResolvePort() {
        Scheme http = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());

        Assert.assertEquals(8080, http.resolvePort(8080));
        Assert.assertEquals(80, http.resolvePort(-1));
    }

    @Test
    public void testHashCode() {
        Scheme http = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
        Scheme myhttp = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
        Scheme https = new Scheme("https", 443, SecureSocketFactoryMockup.INSTANCE);

        Assert.assertTrue(http.hashCode() != https.hashCode()); // not guaranteed
        Assert.assertTrue(http.hashCode() == myhttp.hashCode());
    }

    @Test
    public void testEquals() {
        Scheme http = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
        Scheme myhttp = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
        Scheme https = new Scheme("https", 443, SecureSocketFactoryMockup.INSTANCE);

        Assert.assertFalse(http.equals(https));
        Assert.assertFalse(http.equals(null));
        Assert.assertFalse(http.equals("http"));
        Assert.assertTrue(http.equals(http));
        Assert.assertTrue(http.equals(myhttp));
        Assert.assertFalse(http.equals(https));
    }

    @Test
    public void testToString() {
        Scheme http = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
        // test it twice, the result is cached
        Assert.assertEquals("http:80", http.toString());
        Assert.assertEquals("http:80", http.toString());
    }

}
