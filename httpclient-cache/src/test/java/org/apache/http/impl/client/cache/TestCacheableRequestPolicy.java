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
package org.apache.http.impl.client.cache;

import org.apache.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCacheableRequestPolicy {

    private CacheableRequestPolicy policy;

    @Before
    public void setUp() throws Exception {
        policy = new CacheableRequestPolicy();
    }

    @Test
    public void testIsGetServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("GET", "someUri");

        Assert.assertTrue(policy.isServableFromCache(request));

    }

    @Test
    public void testIsGetWithCacheControlServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Cache-Control", "no-cache");

        Assert.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Cache-Control", "no-store");
        request.addHeader("Cache-Control", "max-age=20");

        Assert.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Cache-Control", "public");
        request.addHeader("Cache-Control", "no-store, max-age=20");

        Assert.assertFalse(policy.isServableFromCache(request));
    }

    @Test
    public void testIsGetWithPragmaServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Pragma", "no-cache");

        Assert.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Pragma", "value1");
        request.addHeader("Pragma", "value2");

        Assert.assertFalse(policy.isServableFromCache(request));
    }

    @Test
    public void testIsArbitraryMethodServableFromCache() {

        BasicHttpRequest request = new BasicHttpRequest("HEAD", "someUri");

        Assert.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("get", "someUri");

        Assert.assertFalse(policy.isServableFromCache(request));

    }

}
