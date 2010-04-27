/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
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
 */

package org.apache.http.impl.client;

import org.apache.http.cookie.CookieIdentityComparator;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link CookieIdentityComparator}.
 */
public class TestCookieIdentityComparator {

    @Test
    public void testCookieIdentityComparasionByName() {
        CookieIdentityComparator comparator = new CookieIdentityComparator();
        BasicClientCookie c1 = new BasicClientCookie("name", "value1");
        BasicClientCookie c2 = new BasicClientCookie("name", "value2");
        Assert.assertTrue(comparator.compare(c1, c2) == 0);

        BasicClientCookie c3 = new BasicClientCookie("name1", "value");
        BasicClientCookie c4 = new BasicClientCookie("name2", "value");
        Assert.assertFalse(comparator.compare(c3, c4) == 0);
    }

    @Test
    public void testCookieIdentityComparasionByNameAndDomain() {
        CookieIdentityComparator comparator = new CookieIdentityComparator();
        BasicClientCookie c1 = new BasicClientCookie("name", "value1");
        c1.setDomain("www.domain.com");
        BasicClientCookie c2 = new BasicClientCookie("name", "value2");
        c2.setDomain("www.domain.com");
        Assert.assertTrue(comparator.compare(c1, c2) == 0);

        BasicClientCookie c3 = new BasicClientCookie("name", "value1");
        c3.setDomain("www.domain.com");
        BasicClientCookie c4 = new BasicClientCookie("name", "value2");
        c4.setDomain("domain.com");
        Assert.assertFalse(comparator.compare(c3, c4) == 0);
    }

    @Test
    public void testCookieIdentityComparasionByNameAndNullDomain() {
        CookieIdentityComparator comparator = new CookieIdentityComparator();
        BasicClientCookie c1 = new BasicClientCookie("name", "value1");
        c1.setDomain(null);
        BasicClientCookie c2 = new BasicClientCookie("name", "value2");
        c2.setDomain(null);
        Assert.assertTrue(comparator.compare(c1, c2) == 0);

        BasicClientCookie c3 = new BasicClientCookie("name", "value1");
        c3.setDomain("www.domain.com");
        BasicClientCookie c4 = new BasicClientCookie("name", "value2");
        c4.setDomain(null);
        Assert.assertFalse(comparator.compare(c3, c4) == 0);
    }

    @Test
    public void testCookieIdentityComparasionByNameAndLocalHost() {
        CookieIdentityComparator comparator = new CookieIdentityComparator();
        BasicClientCookie c1 = new BasicClientCookie("name", "value1");
        c1.setDomain("localhost");
        BasicClientCookie c2 = new BasicClientCookie("name", "value2");
        c2.setDomain("localhost");
        Assert.assertTrue(comparator.compare(c1, c2) == 0);

        BasicClientCookie c3 = new BasicClientCookie("name", "value1");
        c3.setDomain("localhost.local");
        BasicClientCookie c4 = new BasicClientCookie("name", "value2");
        c4.setDomain("localhost");
        Assert.assertTrue(comparator.compare(c3, c4) == 0);
    }

    @Test
    public void testCookieIdentityComparasionByNameDomainAndPath() {
        CookieIdentityComparator comparator = new CookieIdentityComparator();
        BasicClientCookie c1 = new BasicClientCookie("name", "value1");
        c1.setDomain("www.domain.com");
        c1.setPath("/whatever");
        BasicClientCookie c2 = new BasicClientCookie("name", "value2");
        c2.setDomain("www.domain.com");
        c2.setPath("/whatever");
        Assert.assertTrue(comparator.compare(c1, c2) == 0);

        BasicClientCookie c3 = new BasicClientCookie("name", "value1");
        c3.setDomain("www.domain.com");
        c3.setPath("/whatever");
        BasicClientCookie c4 = new BasicClientCookie("name", "value2");
        c4.setDomain("domain.com");
        c4.setPath("/whatever-not");
        Assert.assertFalse(comparator.compare(c3, c4) == 0);
    }

    @Test
    public void testCookieIdentityComparasionByNameDomainAndNullPath() {
        CookieIdentityComparator comparator = new CookieIdentityComparator();
        BasicClientCookie c1 = new BasicClientCookie("name", "value1");
        c1.setDomain("www.domain.com");
        c1.setPath("/");
        BasicClientCookie c2 = new BasicClientCookie("name", "value2");
        c2.setDomain("www.domain.com");
        c2.setPath(null);
        Assert.assertTrue(comparator.compare(c1, c2) == 0);

        BasicClientCookie c3 = new BasicClientCookie("name", "value1");
        c3.setDomain("www.domain.com");
        c3.setPath("/whatever");
        BasicClientCookie c4 = new BasicClientCookie("name", "value2");
        c4.setDomain("domain.com");
        c4.setPath(null);
        Assert.assertFalse(comparator.compare(c3, c4) == 0);
    }

}
