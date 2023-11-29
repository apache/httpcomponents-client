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
package org.apache.hc.client5.http.cache;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.HttpTestUtils;
import org.apache.hc.client5.http.impl.cache.ManagedHttpCacheStorage;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;

class ManagedHttpCacheStorageTest {

    @Test
    void putEntry() throws ResourceIOException {

        final CacheConfig cacheConfig = getCacheConfig();
        try (final ManagedHttpCacheStorage cacheStorage = new ManagedHttpCacheStorage(cacheConfig)) {
            final String key = "foo";
            final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
            cacheStorage.putEntry(key, value);
            assertEquals(HttpStatus.SC_OK, cacheStorage.getEntry(key).getStatus());
        }
    }

    @Test
    void isActive() throws ResourceIOException {

        final CacheConfig cacheConfig = getCacheConfig();
        final ManagedHttpCacheStorage cacheStorage = new ManagedHttpCacheStorage(cacheConfig);
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        cacheStorage.putEntry(key, value);
        assertTrue(cacheStorage.isActive());
        cacheStorage.close();
        assertFalse(cacheStorage.isActive());
    }

    @Test
    void cacheDisableThrowsIllegalStateException() {
        final CacheConfig cacheConfig = getCacheConfig();
        final ManagedHttpCacheStorage cacheStorage = new ManagedHttpCacheStorage(cacheConfig);
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        cacheStorage.close();
        assertFalse(cacheStorage.isActive());
        assertThrows(IllegalStateException.class, () -> cacheStorage.putEntry(key, value));
    }

    private CacheConfig getCacheConfig() {
        return CacheConfig.custom()
                .setSharedCache(true)
                .setMaxObjectSize(262144) //256kb
                .build();
    }
}
