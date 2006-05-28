/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

package org.apache.http.cookie;

import java.util.Comparator;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test cases for {@link CookiePathComparator}.
 */
public class TestCookiePathComparator extends TestCase {


    // ------------------------------------------------------------ Constructor

    public TestCookiePathComparator(String name) {
        super(name);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestCookiePathComparator.class);
    }

    public void testUnequality1() {
        Cookie cookie1 = new Cookie("name1", "value");
        cookie1.setPath("/a/b/");
        Cookie cookie2 = new Cookie("name1", "value");
        cookie2.setPath("/a/");
        Comparator comparator = new CookiePathComparator();
        assertTrue(comparator.compare(cookie1, cookie2) < 0);
        assertTrue(comparator.compare(cookie2, cookie1) > 0);
    }

    public void testUnequality2() {
        Cookie cookie1 = new Cookie("name1", "value");
        cookie1.setPath("/a/b");
        Cookie cookie2 = new Cookie("name1", "value");
        cookie2.setPath("/a");
        Comparator comparator = new CookiePathComparator();
        assertTrue(comparator.compare(cookie1, cookie2) < 0);
        assertTrue(comparator.compare(cookie2, cookie1) > 0);
    }

    public void testEquality1() {
        Cookie cookie1 = new Cookie("name1", "value");
        cookie1.setPath("/a");
        Cookie cookie2 = new Cookie("name1", "value");
        cookie2.setPath("/a");
        Comparator comparator = new CookiePathComparator();
        assertTrue(comparator.compare(cookie1, cookie2) == 0);
        assertTrue(comparator.compare(cookie2, cookie1) == 0);
    }

    public void testEquality2() {
        Cookie cookie1 = new Cookie("name1", "value");
        cookie1.setPath("/a/");
        Cookie cookie2 = new Cookie("name1", "value");
        cookie2.setPath("/a");
        Comparator comparator = new CookiePathComparator();
        assertTrue(comparator.compare(cookie1, cookie2) == 0);
        assertTrue(comparator.compare(cookie2, cookie1) == 0);
    }

    public void testEquality3() {
        Cookie cookie1 = new Cookie("name1", "value");
        cookie1.setPath(null);
        Cookie cookie2 = new Cookie("name1", "value");
        cookie2.setPath("/");
        Comparator comparator = new CookiePathComparator();
        assertTrue(comparator.compare(cookie1, cookie2) == 0);
        assertTrue(comparator.compare(cookie2, cookie1) == 0);
    }

    public void testEquality4() {
        Cookie cookie1 = new Cookie("name1", "value");
        cookie1.setPath("/this");
        Cookie cookie2 = new Cookie("name1", "value");
        cookie2.setPath("/that");
        Comparator comparator = new CookiePathComparator();
        assertTrue(comparator.compare(cookie1, cookie2) == 0);
        assertTrue(comparator.compare(cookie2, cookie1) == 0);
    }
    
}

