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

import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.cookie.params.CookieSpecParams;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.impl.cookie.NetscapeDraftSpecFactory;
import org.apache.http.impl.cookie.RFC2109SpecFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

/**
 * Test cases for {@link CookieSpecRegistry}.
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
        CookieSpecRegistry registry  = new CookieSpecRegistry(); 
        List names = registry.getSpecNames();
        assertNotNull(names);
        assertEquals(0, names.size());
        
        registry.register(CookieSpecParams.BROWSER_COMPATIBILITY, 
                new BrowserCompatSpecFactory());
        registry.register(CookieSpecParams.NETSCAPE, 
                new NetscapeDraftSpecFactory());
        registry.register(CookieSpecParams.RFC_2109, 
                new RFC2109SpecFactory());
        registry.register(CookieSpecParams.RFC_2109, 
                new RFC2109SpecFactory());
        registry.register(CookieSpecParams.NETSCAPE, 
                new NetscapeDraftSpecFactory());

        names = registry.getSpecNames();
        assertNotNull(names);
        assertEquals(3, names.size());
        assertEquals(CookieSpecParams.BROWSER_COMPATIBILITY, (String) names.get(0));
        assertEquals(CookieSpecParams.NETSCAPE, (String) names.get(1));
        assertEquals(CookieSpecParams.RFC_2109, (String) names.get(2));

        registry.unregister(CookieSpecParams.NETSCAPE); 
        registry.unregister(CookieSpecParams.NETSCAPE); 
        registry.unregister(CookieSpecParams.RFC_2109); 
        registry.unregister(CookieSpecParams.BROWSER_COMPATIBILITY); 
        registry.unregister("whatever"); 
        
        names = registry.getSpecNames();
        assertNotNull(names);
        assertEquals(0, names.size());
    }

    public void testGetNewCookieSpec() {
        CookieSpecRegistry registry  = new CookieSpecRegistry(); 
        registry.register(CookieSpecParams.BROWSER_COMPATIBILITY, 
                new BrowserCompatSpecFactory());
        registry.register(CookieSpecParams.NETSCAPE, 
                new NetscapeDraftSpecFactory());
        registry.register(CookieSpecParams.RFC_2109, 
                new RFC2109SpecFactory());
        
        assertNotNull(registry.getCookieSpec(CookieSpecParams.NETSCAPE));
        assertNotNull(registry.getCookieSpec(CookieSpecParams.RFC_2109));
        assertNotNull(registry.getCookieSpec(CookieSpecParams.BROWSER_COMPATIBILITY));
        try {
            registry.getCookieSpec("whatever");
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
        HttpParams params = new BasicHttpParams();
        assertNotNull(registry.getCookieSpec(CookieSpecParams.NETSCAPE, params));
        assertNotNull(registry.getCookieSpec(CookieSpecParams.RFC_2109, params));
        assertNotNull(registry.getCookieSpec(CookieSpecParams.BROWSER_COMPATIBILITY, params));
        try {
            registry.getCookieSpec("whatever", params);
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
        CookieSpecParams.setCookiePolicy(params, CookieSpecParams.BROWSER_COMPATIBILITY);
        CookieSpec cookiespec = registry.getCookieSpec(params);
        assertTrue(cookiespec instanceof BrowserCompatSpec);
    }

    public void testInvalidInput() {
        CookieSpecRegistry registry  = new CookieSpecRegistry(); 
        try {
            registry.register(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            registry.register("whatever", null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            registry.unregister(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            registry.getCookieSpec((String)null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            registry.getCookieSpec((HttpParams)null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}

