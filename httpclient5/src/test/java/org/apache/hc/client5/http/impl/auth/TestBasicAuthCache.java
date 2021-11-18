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

package org.apache.hc.client5.http.impl.auth;

import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BasicAuthCache}.
 */
@SuppressWarnings("boxing") // test code
public class TestBasicAuthCache {

    @Test
    public void testBasicStoreRestore() throws Exception {
        final BasicAuthCache cache = new BasicAuthCache();
        final AuthScheme authScheme = new BasicScheme();
        cache.put(new HttpHost("localhost", 80), authScheme);
        Assertions.assertNotNull(cache.get(new HttpHost("localhost", 80)));
        cache.remove(new HttpHost("localhost", 80));
        Assertions.assertNull(cache.get(new HttpHost("localhost", 80)));
        cache.put(new HttpHost("localhost", 80), authScheme);
        cache.clear();
        Assertions.assertNull(cache.get(new HttpHost("localhost", 80)));
    }

    @Test
    public void testNullKey() throws Exception {
        final BasicAuthCache cache = new BasicAuthCache();
        final AuthScheme authScheme = new BasicScheme();
        Assertions.assertThrows(NullPointerException.class, () ->
                cache.put(null, authScheme));
    }

    @Test
    public void testNullAuthScheme() throws Exception {
        final BasicAuthCache cache = new BasicAuthCache();
        cache.put(new HttpHost("localhost", 80), null);
        Assertions.assertNull(cache.get(new HttpHost("localhost", 80)));
    }

    @Test
    public void testStoreNonSerializable() throws Exception {
        final BasicAuthCache cache = new BasicAuthCache();
        final AuthScheme authScheme = new NTLMScheme();
        cache.put(new HttpHost("localhost", 80), authScheme);
        Assertions.assertNull(cache.get(new HttpHost("localhost", 80)));
    }

}
