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

import java.io.IOException;
import java.util.LinkedHashMap;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheUpdateCallback;

/**
 * Basic {@link HttpCache} implementation backed by an instance of {@link LinkedHashMap}.
 * This cache does NOT deallocate resources associated with the cache entries. It is intended
 * for use with {@link BasicHttpCacheEntry} and similar.
 *
 * @since 4.1
 */
@ThreadSafe
public class BasicHttpCache implements HttpCache {

    private final CacheMap entries;

    public BasicHttpCache(int maxEntries) {
        this.entries = new CacheMap(maxEntries);
    }

    /**
     * Places a HttpCacheEntry in the cache
     *
     * @param url
     *            Url to use as the cache key
     * @param entry
     *            HttpCacheEntry to place in the cache
     */
    public synchronized void putEntry(String url, HttpCacheEntry entry) throws IOException {
        entries.put(url, entry);
    }

    /**
     * Gets an entry from the cache, if it exists
     *
     * @param url
     *            Url that is the cache key
     * @return HttpCacheEntry if one exists, or null for cache miss
     */
    public synchronized HttpCacheEntry getEntry(String url) throws IOException {
        return entries.get(url);
    }

    /**
     * Removes a HttpCacheEntry from the cache
     *
     * @param url
     *            Url that is the cache key
     */
    public synchronized void removeEntry(String url) throws IOException {
        entries.remove(url);
    }

    public synchronized void updateEntry(
            String url,
            HttpCacheUpdateCallback callback) throws IOException {
        HttpCacheEntry existingEntry = entries.get(url);
        entries.put(url, callback.update(existingEntry));
    }

}
