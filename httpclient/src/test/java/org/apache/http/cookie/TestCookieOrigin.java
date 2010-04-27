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

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link CookieOrigin}.
 */
public class TestCookieOrigin {

    @Test
    public void testConstructor() {
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "/", false);
        Assert.assertEquals("www.apache.org", origin.getHost());
        Assert.assertEquals(80, origin.getPort());
        Assert.assertEquals("/", origin.getPath());
        Assert.assertFalse(origin.isSecure());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullHost() {
        new CookieOrigin(null, 80, "/", false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyHost() {
        new CookieOrigin("   ", 80, "/", false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNegativePort() {
        new CookieOrigin("www.apache.org", -80, "/", false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullPath() {
        new CookieOrigin("www.apache.org", 80, null, false);
    }

    @Test
    public void testEmptyPath() {
        CookieOrigin origin = new CookieOrigin("www.apache.org", 80, "", false);
        Assert.assertEquals("www.apache.org", origin.getHost());
        Assert.assertEquals(80, origin.getPort());
        Assert.assertEquals("/", origin.getPath());
        Assert.assertFalse(origin.isSecure());
    }

}

