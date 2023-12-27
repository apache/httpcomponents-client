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

import org.apache.hc.client5.http.cache.RequestCacheControl;
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
        final RequestCacheControl cacheControl = RequestCacheControl.builder().build();

        Assertions.assertTrue(policy.canBeServedFromCache(cacheControl, request));
    }

    @Test
    public void testIsGetWithCacheControlServableFromCache() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "someUri");
        final RequestCacheControl cacheControl = RequestCacheControl.builder()
                .setNoCache(true)
                .build();

        Assertions.assertFalse(policy.canBeServedFromCache(cacheControl, request));

        final RequestCacheControl cacheControl2 = RequestCacheControl.builder()
                .setNoStore(true)
                .setMaxAge(20)
                .build();

        Assertions.assertFalse(policy.canBeServedFromCache(cacheControl2, request));
    }

    @Test
    public void testIsHeadServableFromCache() {
        final BasicHttpRequest request = new BasicHttpRequest("HEAD", "someUri");
        final RequestCacheControl cacheControl = RequestCacheControl.builder().build();

        Assertions.assertTrue(policy.canBeServedFromCache(cacheControl, request));

        final RequestCacheControl cacheControl2 = RequestCacheControl.builder()
                .setMaxAge(20)
                .build();

        Assertions.assertTrue(policy.canBeServedFromCache(cacheControl2, request));
    }

    @Test
    public void testIsHeadWithCacheControlServableFromCache() {
        final BasicHttpRequest request = new BasicHttpRequest("HEAD", "someUri");
        final RequestCacheControl cacheControl = RequestCacheControl.builder()
                .setNoCache(true)
                .build();

        Assertions.assertFalse(policy.canBeServedFromCache(cacheControl, request));

        request.addHeader("Cache-Control", "no-store");
        request.addHeader("Cache-Control", "max-age=20");
        final RequestCacheControl cacheControl2 = RequestCacheControl.builder()
                .setNoStore(true)
                .setMaxAge(20)
                .build();

        Assertions.assertFalse(policy.canBeServedFromCache(cacheControl2, request));
    }

    @Test
    public void testIsArbitraryMethodServableFromCache() {
        final BasicHttpRequest request = new BasicHttpRequest("TRACE", "someUri");
        final RequestCacheControl cacheControl = RequestCacheControl.builder()
                .build();

        Assertions.assertFalse(policy.canBeServedFromCache(cacheControl, request));

        final BasicHttpRequest request2 = new BasicHttpRequest("huh", "someUri");

        Assertions.assertFalse(policy.canBeServedFromCache(cacheControl, request2));

    }

}
