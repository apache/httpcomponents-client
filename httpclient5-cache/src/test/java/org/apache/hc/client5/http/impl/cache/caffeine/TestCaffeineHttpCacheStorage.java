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
package org.apache.hc.client5.http.impl.cache.caffeine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.HeapResource;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.junit.jupiter.api.Test;

class TestCaffeineHttpCacheStorage {

    private static HttpCacheEntry newEntry(final int status) throws ResourceIOException {
        final Instant now = Instant.now();
        final Header[] responseHeaders = new Header[0];
        final Resource resource = new HeapResource(new byte[]{1, 2, 3});

        final HeaderGroup requestHeaderGroup = new HeaderGroup();
        final HeaderGroup responseHeaderGroup = new HeaderGroup();
        responseHeaderGroup.setHeaders(responseHeaders);

        // Use the non-deprecated @Internal constructor
        return new HttpCacheEntry(
                now,
                now,
                "GET",
                "/",
                requestHeaderGroup,
                status,
                responseHeaderGroup,
                resource,
                null);
    }

    private static CacheConfig newConfig() {
        return CacheConfig.custom()
                .setMaxUpdateRetries(3)
                .build();
    }

    @Test
    void testPutGetRemoveObjectCache() throws Exception {
        final Cache<String, HttpCacheStorageEntry> cache = Caffeine.newBuilder().build();
        final CacheConfig config = newConfig();
        final CaffeineHttpCacheStorage<HttpCacheStorageEntry> storage =
                CaffeineHttpCacheStorage.createObjectCache(cache, config);

        final String key = "foo";
        final HttpCacheEntry entry = newEntry(HttpStatus.SC_OK);

        storage.putEntry(key, entry);

        final HttpCacheEntry result = storage.getEntry(key);
        assertNotNull(result);
        assertEquals(HttpStatus.SC_OK, result.getStatus());

        storage.removeEntry(key);
        assertNull(storage.getEntry(key));
    }

    @Test
    void testUpdateEntryObjectCache() throws Exception {
        final Cache<String, HttpCacheStorageEntry> cache = Caffeine.newBuilder().build();
        final CacheConfig config = newConfig();
        final CaffeineHttpCacheStorage<HttpCacheStorageEntry> storage =
                CaffeineHttpCacheStorage.createObjectCache(cache, config);

        final String key = "bar";
        final HttpCacheEntry original = newEntry(HttpStatus.SC_OK);
        storage.putEntry(key, original);

        final HttpCacheCASOperation casOperation = existing -> {
            assertNotNull(existing);

            final HeaderGroup requestHeaderGroup = new HeaderGroup();
            requestHeaderGroup.setHeaders(existing.requestHeaders().getHeaders());

            final HeaderGroup responseHeaderGroup = new HeaderGroup();
            responseHeaderGroup.setHeaders(existing.responseHeaders().getHeaders());

            return new HttpCacheEntry(
                    existing.getRequestInstant(),
                    existing.getResponseInstant(),
                    existing.getRequestMethod(),
                    existing.getRequestURI(),
                    requestHeaderGroup,
                    HttpStatus.SC_NOT_MODIFIED,
                    responseHeaderGroup,
                    existing.getResource(),
                    existing.getVariants());
        };

        storage.updateEntry(key, casOperation);

        final HttpCacheEntry updated = storage.getEntry(key);
        assertNotNull(updated);
        assertEquals(HttpStatus.SC_NOT_MODIFIED, updated.getStatus());
    }

    @Test
    void testGetEntriesUsesBulkRestore() throws Exception {
        final Cache<String, HttpCacheStorageEntry> cache = Caffeine.newBuilder().build();
        final CacheConfig config = newConfig();
        final CaffeineHttpCacheStorage<HttpCacheStorageEntry> storage =
                CaffeineHttpCacheStorage.createObjectCache(cache, config);

        final HttpCacheEntry entry1 = newEntry(HttpStatus.SC_OK);
        final HttpCacheEntry entry2 = newEntry(HttpStatus.SC_CREATED);

        storage.putEntry("k1", entry1);
        storage.putEntry("k2", entry2);

        final Map<String, HttpCacheEntry> result =
                storage.getEntries(Arrays.asList("k1", "k2", "k3"));

        assertEquals(2, result.size());
        assertEquals(HttpStatus.SC_OK, result.get("k1").getStatus());
        assertEquals(HttpStatus.SC_CREATED, result.get("k2").getStatus());
        assertFalse(result.containsKey("k3"));
    }

    @Test
    void testSerializedCacheStoresBytes() throws Exception {
        final Cache<String, byte[]> cache = Caffeine.<String, byte[]>newBuilder().build();
        final CacheConfig config = newConfig();
        final CaffeineHttpCacheStorage<byte[]> storage =
                CaffeineHttpCacheStorage.createSerializedCache(cache, config);

        final String key = "baz";
        final HttpCacheEntry entry = newEntry(HttpStatus.SC_OK);

        storage.putEntry(key, entry);

        // Underlying cache should contain serialized bytes
        final byte[] stored = cache.getIfPresent(key);
        assertNotNull(stored);
        assertTrue(stored.length > 0);

        final HttpCacheEntry result = storage.getEntry(key);
        assertNotNull(result);
        assertEquals(HttpStatus.SC_OK, result.getStatus());
    }

}
