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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Basic {@link HttpCacheStorage} implementation backed by an instance of
 * {@link java.util.LinkedHashMap}. In other words, cache entries and
 * the cached response bodies are held in-memory. This cache does NOT
 * deallocate resources associated with the cache entries; it is intended
 * for use with {@link HeapResource} and similar. This is the default cache
 * storage backend used by {@link CachingHttpClients}.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class BasicHttpCacheStorage implements HttpCacheStorage {

    private final InternalCacheStorage entries;

    private final ReentrantLock lock;

    public BasicHttpCacheStorage(final CacheConfig config) {
        super();
        this.entries = new InternalCacheStorage(config.getMaxCacheEntries(), null);
        this.lock = new ReentrantLock();
    }

    /**
     * Places a HttpCacheEntry in the cache
     *
     * @param url
     *            Url to use as the cache key
     * @param entry
     *            HttpCacheEntry to place in the cache
     */
    @Override
    public void putEntry(
            final String url, final HttpCacheEntry entry) throws ResourceIOException {
        lock.lock();
        try {
            entries.put(url, entry);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets an entry from the cache, if it exists
     *
     * @param url
     *            Url that is the cache key
     * @return HttpCacheEntry if one exists, or null for cache miss
     */
    @Override
    public HttpCacheEntry getEntry(final String url) throws ResourceIOException {
        lock.lock();
        try {
            return entries.get(url);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a HttpCacheEntry from the cache
     *
     * @param url
     *            Url that is the cache key
     */
    @Override
    public void removeEntry(final String url) throws ResourceIOException {
        lock.lock();
        try {
            entries.remove(url);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void updateEntry(
            final String url, final HttpCacheCASOperation casOperation) throws ResourceIOException {
        lock.lock();
        try {
            final HttpCacheEntry existingEntry = entries.get(url);
            entries.put(url, casOperation.execute(existingEntry));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<String, HttpCacheEntry> getEntries(final Collection<String> keys) throws ResourceIOException {
        Args.notNull(keys, "Key");
        final Map<String, HttpCacheEntry> resultMap = new HashMap<>(keys.size());
        for (final String key: keys) {
            final HttpCacheEntry entry = getEntry(key);
            if (entry != null) {
                resultMap.put(key, entry);
            }
        }
        return resultMap;
    }

}
