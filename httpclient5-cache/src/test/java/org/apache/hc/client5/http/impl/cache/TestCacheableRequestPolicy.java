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
package org.apache.hc.client5.http.impl.cache;

import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestCacheableRequestPolicy {

    private CacheableRequestPolicy policy;

    @BeforeEach
    public void setUp() throws Exception {
        policy = new CacheableRequestPolicy();
    }

    @Test
    public void testIsGetServableFromCache() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "someUri");

        Assertions.assertTrue(policy.isServableFromCache(request));
    }

    @Test
    public void testIsGetWithCacheControlServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Cache-Control", "no-cache");

        Assertions.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Cache-Control", "no-store");
        request.addHeader("Cache-Control", "max-age=20");

        Assertions.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Cache-Control", "public");
        request.addHeader("Cache-Control", "no-store, max-age=20");

        Assertions.assertFalse(policy.isServableFromCache(request));
    }

    @Test
    public void testIsGetWithPragmaServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Pragma", "no-cache");

        Assertions.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("GET", "someUri");
        request.addHeader("Pragma", "value1");
        request.addHeader("Pragma", "value2");

        Assertions.assertFalse(policy.isServableFromCache(request));
    }

    @Test
    public void testIsHeadServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("HEAD", "someUri");

        Assertions.assertTrue(policy.isServableFromCache(request));

        request = new BasicHttpRequest("HEAD", "someUri");
        request.addHeader("Cache-Control", "public");
        request.addHeader("Cache-Control", "max-age=20");

        Assertions.assertTrue(policy.isServableFromCache(request));
    }

    @Test
    public void testIsHeadWithCacheControlServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("HEAD", "someUri");
        request.addHeader("Cache-Control", "no-cache");

        Assertions.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("HEAD", "someUri");
        request.addHeader("Cache-Control", "no-store");
        request.addHeader("Cache-Control", "max-age=20");

        Assertions.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("HEAD", "someUri");
        request.addHeader("Cache-Control", "public");
        request.addHeader("Cache-Control", "no-store, max-age=20");

        Assertions.assertFalse(policy.isServableFromCache(request));
    }

    @Test
    public void testIsHeadWithPragmaServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("HEAD", "someUri");
        request.addHeader("Pragma", "no-cache");

        Assertions.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("HEAD", "someUri");
        request.addHeader("Pragma", "value1");
        request.addHeader("Pragma", "value2");

        Assertions.assertFalse(policy.isServableFromCache(request));
    }

    @Test
    public void testIsArbitraryMethodServableFromCache() {
        BasicHttpRequest request = new BasicHttpRequest("TRACE", "someUri");

        Assertions.assertFalse(policy.isServableFromCache(request));

        request = new BasicHttpRequest("get", "someUri");

        Assertions.assertFalse(policy.isServableFromCache(request));

    }

}
