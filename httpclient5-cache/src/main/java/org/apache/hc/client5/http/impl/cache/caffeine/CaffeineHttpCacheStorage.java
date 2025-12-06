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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;

import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.cache.AbstractSerializingCacheStorage;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.HttpByteArrayCacheEntrySerializer;
import org.apache.hc.client5.http.impl.cache.NoopCacheEntrySerializer;
import org.apache.hc.core5.util.Args;


/**
 * <p>This class is a storage backend for cache entries that uses the
 * <a href="https://github.com/ben-manes/caffeine">Caffeine</a>
 * cache implementation.</p>
 *
 * <p>The size limits, eviction policy, and expiry policy are configured
 * on the underlying Caffeine cache. The setting for
 * {@link CacheConfig#getMaxCacheEntries()} is effectively ignored and
 * should be enforced via the Caffeine configuration instead.</p>
 *
 * <p>Please refer to the Caffeine documentation for details on how to
 * configure the cache itself.</p>
 *
 * @since 5.7
 */
public class CaffeineHttpCacheStorage<T> extends AbstractSerializingCacheStorage<T, T> {

    /**
     * Creates cache that stores {@link HttpCacheStorageEntry}s without direct serialization.
     *
     * @since 5.6
     */
    public static CaffeineHttpCacheStorage<HttpCacheStorageEntry> createObjectCache(
            final Cache<String, HttpCacheStorageEntry> cache, final CacheConfig config) {
        return new CaffeineHttpCacheStorage<>(cache, config, NoopCacheEntrySerializer.INSTANCE);
    }

    /**
     * Creates cache that stores serialized {@link HttpCacheStorageEntry}s.
     *
     * @since 5.6
     */
    public static CaffeineHttpCacheStorage<byte[]> createSerializedCache(
            final Cache<String, byte[]> cache, final CacheConfig config) {
        return new CaffeineHttpCacheStorage<>(cache, config, HttpByteArrayCacheEntrySerializer.INSTANCE);
    }

    private final Cache<String, T> cache;

    /**
     * Constructs a storage backend using the provided Caffeine cache
     * with the given configuration options, but using an alternative
     * cache entry serialization strategy.
     *
     * @param cache      where to store cached origin responses
     * @param config     cache storage configuration options - note that
     *                   the setting for max object size and max entries
     *                   should be configured on the Caffeine cache instead.
     * @param serializer alternative serialization mechanism
     */
    public CaffeineHttpCacheStorage(
            final Cache<String, T> cache,
            final CacheConfig config,
            final HttpCacheEntrySerializer<T> serializer) {
        super((config != null ? config : CacheConfig.DEFAULT).getMaxUpdateRetries(),
                Args.notNull(serializer, "Cache entry serializer"));
        this.cache = Args.notNull(cache, "Caffeine cache");
    }

    @Override
    protected String digestToStorageKey(final String key) {
        return key;
    }

    @Override
    protected void store(final String storageKey, final T storageObject) throws ResourceIOException {
        cache.put(storageKey, storageObject);
    }

    @Override
    protected T restore(final String storageKey) throws ResourceIOException {
        return cache.getIfPresent(storageKey);
    }

    @Override
    protected T getForUpdateCAS(final String storageKey) throws ResourceIOException {
        return cache.getIfPresent(storageKey);
    }

    @Override
    protected T getStorageObject(final T element) throws ResourceIOException {
        return element;
    }

    @Override
    protected boolean updateCAS(
            final String storageKey, final T oldStorageObject, final T storageObject) throws ResourceIOException {
        return cache.asMap().replace(storageKey, oldStorageObject, storageObject);
    }

    @Override
    protected void delete(final String storageKey) throws ResourceIOException {
        cache.invalidate(storageKey);
    }

    @Override
    protected Map<String, T> bulkRestore(final Collection<String> storageKeys) throws ResourceIOException {
        final Map<String, T> present = cache.getAllPresent(storageKeys);
        return new HashMap<>(present);
    }

}
