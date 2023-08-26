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

import java.util.LinkedList;
import java.util.Queue;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestInternalCacheStorage {

    @Test
    public void testCacheBasics() {
        final InternalCacheStorage storage = new InternalCacheStorage();
        final String key1 = "some-key-1";
        Assertions.assertNull(storage.get(key1));
        Assertions.assertNull(storage.remove(key1));
        final HttpCacheEntry entry1 = HttpTestUtils.makeCacheEntry();
        storage.put(key1, entry1);
        Assertions.assertSame(entry1, storage.get(key1));
        Assertions.assertSame(entry1, storage.remove(key1));
        Assertions.assertNull(storage.get(key1));
        Assertions.assertNull(storage.remove(key1));
        final String key2 = "some-key-2";
        final HttpCacheEntry entry2 = HttpTestUtils.makeCacheEntry();
        final String key3 = "some-key-3";
        final HttpCacheEntry entry3 = HttpTestUtils.makeCacheEntry();
        storage.put(key2, entry2);
        storage.put(key3, entry3);
        Assertions.assertSame(entry2, storage.get(key2));
        Assertions.assertSame(entry3, storage.get(key3));
        storage.clear();
        Assertions.assertNull(storage.get(key2));
        Assertions.assertNull(storage.get(key3));
    }

    @Test
    public void testCacheEviction() {
        final Queue<HttpCacheEntry> evictedEntries = new LinkedList<>();
        final InternalCacheStorage storage = new InternalCacheStorage(2, e -> evictedEntries.add(e.getContent()));
        final String key1 = "some-key-1";
        final String key2 = "some-key-2";
        final String key3 = "some-key-3";
        final String key4 = "some-key-4";
        final HttpCacheEntry entry1 = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry entry2 = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry entry3 = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry entry4 = HttpTestUtils.makeCacheEntry();

        storage.put(key1, entry1);
        storage.put(key2, entry2);
        storage.put(key3, entry3);
        storage.put(key4, entry4);
        Assertions.assertSame(entry1, evictedEntries.poll());
        Assertions.assertSame(entry2, evictedEntries.poll());
        Assertions.assertNull(evictedEntries.poll());

        storage.clear();
        storage.put(key1, entry1);
        storage.put(key2, entry2);
        storage.get(key1);
        storage.put(key3, entry3);
        storage.get(key1);
        storage.put(key4, entry4);

        Assertions.assertSame(entry2, evictedEntries.poll());
        Assertions.assertSame(entry3, evictedEntries.poll());
        Assertions.assertNull(evictedEntries.poll());
    }

}
