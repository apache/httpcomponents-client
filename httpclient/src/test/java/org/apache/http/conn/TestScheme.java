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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
//import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.mockup.SecureSocketFactoryMockup;


/**
 * Unit tests for {@link Scheme} and {@link SchemeRegistry}.
 *
 */
public class TestScheme extends TestCase {

    public TestScheme(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestScheme.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestScheme.class);
    }

    public void testConstructor() {
        Scheme http = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);
        assertEquals("http", http.getName()); 
        assertEquals(80, http.getDefaultPort()); 
        assertSame(PlainSocketFactory.getSocketFactory(),
                   http.getSocketFactory()); 
        assertFalse(http.isLayered()); 
        Scheme https = new Scheme
            ("https", SecureSocketFactoryMockup.INSTANCE, 443);
        //  ("https", SSLSocketFactory.getSocketFactory(), 443);
        assertEquals("https", https.getName()); 
        assertEquals(443, https.getDefaultPort()); 
        assertSame(//SSLSocketFactory.getSocketFactory()
                   SecureSocketFactoryMockup.INSTANCE,
                   https.getSocketFactory()); 
        assertTrue(https.isLayered());

        Scheme hTtP = new Scheme
            ("hTtP", PlainSocketFactory.getSocketFactory(), 80);
        assertEquals("http", hTtP.getName());
        // the rest is no different from above

        try {
            new Scheme(null, PlainSocketFactory.getSocketFactory(), 80);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new Scheme("http", null, 80);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new Scheme("http", PlainSocketFactory.getSocketFactory(), -1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new Scheme("http", PlainSocketFactory.getSocketFactory(), 70000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testRegisterUnregister() {
        SchemeRegistry schmreg = new SchemeRegistry();

        Scheme http = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme https = new Scheme
            ("https", SecureSocketFactoryMockup.INSTANCE, 443);
        //  ("https", SSLSocketFactory.getSocketFactory(), 443);
        Scheme myhttp = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);

        HttpHost host  = new HttpHost("www.test.invalid", -1, "http");
        HttpHost hosts = new HttpHost("www.test.invalid", -1, "https");

        assertNull(schmreg.register(myhttp));
        assertNull(schmreg.register(https));
        assertSame(myhttp, schmreg.register(http));
        assertSame(http, schmreg.getScheme("http"));
        assertSame(http, schmreg.getScheme(host));
        assertSame(https, schmreg.getScheme("https"));
        assertSame(https, schmreg.getScheme(hosts));

        schmreg.unregister("http");
        schmreg.unregister("https");

        assertNull(schmreg.get("http")); // get() does not throw exception
        try {
            schmreg.getScheme("http"); // getScheme() does throw exception
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
    }


    public void testIterator() {
        SchemeRegistry schmreg = new SchemeRegistry();

        List<String> names = schmreg.getSchemeNames();
        assertNotNull(names);
        assertTrue(names.isEmpty());

        Scheme http = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme https = new Scheme
            ("https", SecureSocketFactoryMockup.INSTANCE, 443);
        //  ("https", SSLSocketFactory.getSocketFactory(), 443);

        schmreg.register(http);
        schmreg.register(https);

        names = schmreg.getSchemeNames();
        assertNotNull(names);
        assertFalse(names.isEmpty());

        boolean flaghttp  = false;
        boolean flaghttps = false;
        String name = names.get(0);

        if ("http".equals(name))
            flaghttp = true;
        else if ("https".equals(name))
            flaghttps = true;
        else
            fail("unexpected name in iterator: " + name);

        assertNotNull(schmreg.get(name));
        schmreg.unregister(name);
        assertNull(schmreg.get(name));

        name = names.get(1);

        if ("http".equals(name)) {
            if (flaghttp) fail("name 'http' found twice");
        } else if ("https".equals(name)) {
            if (flaghttps) fail("name 'https' found twice");
        } else {
            fail("unexpected name in iterator: " + name);
        }

        assertNotNull(schmreg.get(name));
    }

    public void testIllegalRegisterUnregister() {
        SchemeRegistry schmreg = new SchemeRegistry();
        try {
            schmreg.register(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            schmreg.unregister(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            schmreg.get(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            schmreg.getScheme((String)null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            schmreg.getScheme((HttpHost)null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
    public void testResolvePort() {
        Scheme http = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);

        assertEquals(8080, http.resolvePort(8080));
        assertEquals(80, http.resolvePort(-1));
    }
    
    public void testHashCode() {
        Scheme http = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme myhttp = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme https = new Scheme
            ("https", SecureSocketFactoryMockup.INSTANCE, 443);
        //  ("https", SSLSocketFactory.getSocketFactory(), 443);

        assertTrue(http.hashCode() != https.hashCode()); // not guaranteed
        assertTrue(http.hashCode() == myhttp.hashCode());
    }
    
    public void testEquals() {
        Scheme http = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme myhttp = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme https = new Scheme
            ("https", SecureSocketFactoryMockup.INSTANCE, 443);
        //  ("https", SSLSocketFactory.getSocketFactory(), 443);

        assertFalse(http.equals(https));
        assertFalse(http.equals(null));
        assertFalse(http.equals("http"));
        assertTrue(http.equals(http));
        assertTrue(http.equals(myhttp));
        assertFalse(http.equals(https));
    }

    public void testToString() {
        Scheme http = new Scheme
            ("http", PlainSocketFactory.getSocketFactory(), 80);
        // test it twice, the result is cached
        assertEquals("http:80", http.toString());
        assertEquals("http:80", http.toString());
    }
    
}
