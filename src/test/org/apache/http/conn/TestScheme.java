/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * 
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http;

import org.apache.http.impl.io.PlainSocketFactory;
import org.apache.http.impl.io.SSLSocketFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link Scheme}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
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
        Scheme http = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        assertEquals("http", http.getName()); 
        assertEquals(80, http.getDefaultPort()); 
        assertEquals(PlainSocketFactory.getSocketFactory(), http.getSocketFactory()); 
        assertFalse(http.isSecure()); 
        Scheme https = new Scheme("http", SSLSocketFactory.getSocketFactory(), 443);
        assertEquals("http", https.getName()); 
        assertEquals(443, https.getDefaultPort()); 
        assertEquals(SSLSocketFactory.getSocketFactory(), https.getSocketFactory()); 
        assertTrue(https.isSecure());
        
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
    }

    public void testRegisterUnregister() {
        Scheme http = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme https = new Scheme("http", SSLSocketFactory.getSocketFactory(), 443);
    	Scheme.registerScheme("http", http);
    	Scheme.registerScheme("https", https);
    	assertEquals(http, Scheme.getScheme("http"));
    	assertEquals(https, Scheme.getScheme("https"));
    	Scheme.unregisterScheme("http");
    	Scheme.unregisterScheme("https");
    	
    	try {
        	Scheme.getScheme("http");
        	fail("IllegalStateException should have been thrown");
    	} catch (IllegalStateException ex) {
        	// expected
    	}
    }

    public void testIllegalRegisterUnregister() {
        Scheme http = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        try {
        	Scheme.registerScheme(null, http);
        	fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
        	// expected
        }
        try {
        	Scheme.registerScheme("http", null);
        	fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
        	// expected
        }
        try {
        	Scheme.unregisterScheme(null);
        	fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
        	// expected
        }
        try {
        	Scheme.getScheme(null);
        	fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
        	// expected
        }
    }
    
    public void testResolvePort() {
        Scheme http = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        assertEquals(8080, http.resolvePort(8080));
        assertEquals(80, http.resolvePort(-1));
    }
    
    public void testHashCode() {
        Scheme http = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme myhttp = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme https = new Scheme("http", SSLSocketFactory.getSocketFactory(), 443);
        assertTrue(http.hashCode() != https.hashCode());
        assertTrue(http.hashCode() == myhttp.hashCode());
    }
    
    public void testEquals() {
        Scheme http = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme myhttp = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        Scheme https = new Scheme("http", SSLSocketFactory.getSocketFactory(), 443);
        assertFalse(http.equals(https));
        assertFalse(http.equals(null));
        assertFalse(http.equals("http"));
        assertTrue(http.equals(http));
        assertTrue(http.equals(myhttp));
        assertFalse(http.equals(https));
    }
    
    public void testToString() {
        Scheme http = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        assertEquals("http:80", http.toString());
    }
    
}
