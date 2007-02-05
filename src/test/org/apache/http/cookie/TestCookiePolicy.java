/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.impl.cookie.NetscapeDraftSpecFactory;
import org.apache.http.impl.cookie.RFC2109SpecFactory;
import org.apache.http.cookie.params.CookieSpecParams;
import org.apache.http.impl.params.DefaultHttpParams;
import org.apache.http.params.HttpParams;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test cases for {@link CookiePolicy}.
 */
public class TestCookiePolicy extends TestCase {


    // ------------------------------------------------------------ Constructor

    public TestCookiePolicy(String name) {
        super(name);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestCookiePolicy.class);
    }

    public void testRegisterUnregisterCookieSpecFactory() {
        String[] specs = CookiePolicy.getRegisteredCookieSpecs();
        assertNotNull(specs);
        assertEquals(0, specs.length);
        
        CookiePolicy.register(CookieSpecParams.BROWSER_COMPATIBILITY, 
                new BrowserCompatSpecFactory());
        CookiePolicy.register(CookieSpecParams.NETSCAPE, 
                new NetscapeDraftSpecFactory());
        CookiePolicy.register(CookieSpecParams.RFC_2109, 
                new RFC2109SpecFactory());
        CookiePolicy.register(CookieSpecParams.RFC_2109, 
                new RFC2109SpecFactory());

        specs = CookiePolicy.getRegisteredCookieSpecs();
        assertNotNull(specs);
        assertEquals(3, specs.length);

        CookiePolicy.unregister(CookieSpecParams.NETSCAPE); 
        CookiePolicy.unregister(CookieSpecParams.NETSCAPE); 
        CookiePolicy.unregister(CookieSpecParams.RFC_2109); 
        CookiePolicy.unregister(CookieSpecParams.BROWSER_COMPATIBILITY); 
        CookiePolicy.unregister("whatever"); 
        
        specs = CookiePolicy.getRegisteredCookieSpecs();
        assertNotNull(specs);
        assertEquals(0, specs.length);
    }

    public void testGetNewCookieSpec() {
        CookiePolicy.register(CookieSpecParams.BROWSER_COMPATIBILITY, 
                new BrowserCompatSpecFactory());
        CookiePolicy.register(CookieSpecParams.NETSCAPE, 
                new NetscapeDraftSpecFactory());
        CookiePolicy.register(CookieSpecParams.RFC_2109, 
                new RFC2109SpecFactory());
        
        assertNotNull(CookiePolicy.getCookieSpec(CookieSpecParams.NETSCAPE));
        assertNotNull(CookiePolicy.getCookieSpec(CookieSpecParams.RFC_2109));
        assertNotNull(CookiePolicy.getCookieSpec(CookieSpecParams.BROWSER_COMPATIBILITY));
        try {
            CookiePolicy.getCookieSpec("whatever");
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
        HttpParams params = new DefaultHttpParams();
        assertNotNull(CookiePolicy.getCookieSpec(CookieSpecParams.NETSCAPE, params));
        assertNotNull(CookiePolicy.getCookieSpec(CookieSpecParams.RFC_2109, params));
        assertNotNull(CookiePolicy.getCookieSpec(CookieSpecParams.BROWSER_COMPATIBILITY, params));
        try {
            CookiePolicy.getCookieSpec("whatever", params);
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
        CookieSpecParams.setCookiePolicy(params, CookieSpecParams.BROWSER_COMPATIBILITY);
        CookieSpec cookiespec = CookiePolicy.getCookieSpec(params);
        assertTrue(cookiespec instanceof BrowserCompatSpec);
    }

    public void testInvalidInput() {
        try {
            CookiePolicy.register(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            CookiePolicy.register("whatever", null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            CookiePolicy.unregister(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            CookiePolicy.getCookieSpec((String)null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            CookiePolicy.getCookieSpec((HttpParams)null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}

