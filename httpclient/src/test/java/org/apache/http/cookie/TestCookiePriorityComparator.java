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

import java.util.Comparator;
import java.util.Date;

import org.apache.http.impl.cookie.BasicClientCookie;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for {@link org.apache.http.cookie.CookiePriorityComparator}.
 */
public class TestCookiePriorityComparator {

    private Comparator<Cookie> comparator;

    @Before
    public void setup() {
        comparator = CookiePriorityComparator.INSTANCE;
    }

    @Test
    public void testUnequality() {
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value");
        cookie1.setPath("/a/b/");
        final BasicClientCookie cookie2 = new BasicClientCookie("name1", "value");
        cookie2.setPath("/a/");
        Assert.assertTrue(comparator.compare(cookie1, cookie2) < 0);
        Assert.assertTrue(comparator.compare(cookie2, cookie1) > 0);
    }

    @Test
    public void testEquality() {
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value");
        cookie1.setPath("/a");
        final BasicClientCookie cookie2 = new BasicClientCookie("name1", "value");
        cookie2.setPath("/a");
        Assert.assertTrue(comparator.compare(cookie1, cookie2) == 0);
        Assert.assertTrue(comparator.compare(cookie2, cookie1) == 0);
    }

    @Test
    public void testUnequalityTrailingSlash() {
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value");
        cookie1.setPath("/a/");
        final BasicClientCookie cookie2 = new BasicClientCookie("name1", "value");
        cookie2.setPath("/a");
        Assert.assertTrue(comparator.compare(cookie1, cookie2) < 0);
        Assert.assertTrue(comparator.compare(cookie2, cookie1) > 0);
    }

    @Test
    public void testEqualityNullPath() {
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value");
        cookie1.setPath(null);
        final BasicClientCookie cookie2 = new BasicClientCookie("name1", "value");
        cookie2.setPath("/");
        Assert.assertTrue(comparator.compare(cookie1, cookie2) == 0);
        Assert.assertTrue(comparator.compare(cookie2, cookie1) == 0);
    }

    @Test
    public void testEqualitySameLength() {
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value");
        cookie1.setPath("/this");
        final BasicClientCookie cookie2 = new BasicClientCookie("name1", "value");
        cookie2.setPath("/that");
        Assert.assertTrue(comparator.compare(cookie1, cookie2) == 0);
        Assert.assertTrue(comparator.compare(cookie2, cookie1) == 0);
    }

    @Test
    public void testUnequalityCreationDate() {
        final BasicClientCookie cookie1 = new BasicClientCookie("name1", "value");
        cookie1.setPath("/blah");
        cookie1.setCreationDate(new Date(System.currentTimeMillis() - 200000));
        final BasicClientCookie cookie2 = new BasicClientCookie("name1", "value");
        cookie2.setPath("/blah");
        cookie2.setCreationDate(new Date(System.currentTimeMillis()));
        Assert.assertTrue(comparator.compare(cookie1, cookie2) < 0);
        Assert.assertTrue(comparator.compare(cookie2, cookie1) > 0);
    }

}

