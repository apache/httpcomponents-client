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


import java.net.URI;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestFileResourceFactory {

    CacheKeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        keyGenerator = new CacheKeyGenerator();
    }

    @Test
    void testViaValueLookup() throws Exception {
        final String requestId = keyGenerator.generateKey(new URI("http://localhost/stuff"));

        Assertions.assertEquals(
                "blah%20blah@http%3A%2F%2Flocalhost%3A80%2Fstuff",
                FileResourceFactory.generateUniqueCacheFileName(requestId, "blah blah", null, 0, 0));
        Assertions.assertEquals(
                "blah-blah@http%3A%2F%2Flocalhost%3A80%2Fstuff",
                FileResourceFactory.generateUniqueCacheFileName(requestId, "blah-blah", null, 0, 0));
        Assertions.assertEquals(
                "blah%40blah@http%3A%2F%2Flocalhost%3A80%2Fstuff",
                FileResourceFactory.generateUniqueCacheFileName(requestId, "blah@blah", null, 0, 0));
        Assertions.assertEquals(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81@http%3A%2F%2Flocalhost%3A80%2Fstuff",
                FileResourceFactory.generateUniqueCacheFileName(requestId, null, new byte[]{1, 2, 3}, 0, 3));
        Assertions.assertEquals(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81@http%3A%2F%2Flocalhost%3A80%2Fstuff",
                FileResourceFactory.generateUniqueCacheFileName(requestId, null, new byte[]{1, 2, 3, 4, 5}, 0, 3));
        Assertions.assertEquals(
                "http%3A%2F%2Flocalhost%3A80%2Fstuff",
                FileResourceFactory.generateUniqueCacheFileName(requestId, null, null, 0, 0));
    }

}
