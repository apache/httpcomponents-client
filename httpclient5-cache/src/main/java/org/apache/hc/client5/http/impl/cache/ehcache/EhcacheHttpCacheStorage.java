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
package org.apache.hc.client5.http.impl.cache.ehcache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.cache.AbstractSerializingCacheStorage;
import org.apache.hc.client5.http.impl.cache.ByteArrayCacheEntrySerializer;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.NoopCacheEntrySerializer;
import org.apache.hc.core5.util.Args;
import org.ehcache.Cache;

/**
 * <p>This class is a storage backend for cache entries that uses the
 * popular <a href="http://ehcache.org">Ehcache</a> cache implementation.
 * In particular, this backend allows for spillover to disk, where the
 * cache can be effectively larger than memory, and cached responses are
 * paged into and out of memory from disk as needed.</p>
 *
 * <p><b>N.B.</b> Since the Ehcache is configured ahead of time with a
 * maximum number of cache entries, this effectively ignores the
 * {@link CacheConfig#getMaxCacheEntries()}  maximum cache entries}
 * specified by a provided {@link CacheConfig}.</p>
 *
 * <p>Please refer to the <a href="http://ehcache.org/documentation/index.html">
 * Ehcache documentation</a> for details on how to configure the Ehcache
 * itself.</p>
 * @since 4.1
 */
public class EhcacheHttpCacheStorage<T> extends AbstractSerializingCacheStorage<T, T> {

    /**
     * Creates cache that stores {@link HttpCacheStorageEntry}s without direct serialization.
     *
     * @since 5.0
     */
    public static EhcacheHttpCacheStorage<HttpCacheStorageEntry> createObjectCache(
            final Cache<String, HttpCacheStorageEntry> cache, final CacheConfig config) {
        return new EhcacheHttpCacheStorage<>(cache, config, NoopCacheEntrySerializer.INSTANCE);
    }

    /**
     * Creates cache that stores serialized {@link HttpCacheStorageEntry}s.
     *
     * @since 5.0
     */
    public static EhcacheHttpCacheStorage<byte[]> createSerializedCache(
            final Cache<String, byte[]> cache, final CacheConfig config) {
        return new EhcacheHttpCacheStorage<>(cache, config, ByteArrayCacheEntrySerializer.INSTANCE);
    }

    private final Cache<String, T> cache;

    /**
     * Constructs a storage backend using the provided Ehcache
     * with the given configuration options, but using an alternative
     * cache entry serialization strategy.
     * @param cache where to store cached origin responses
     * @param config cache storage configuration options - note that
     *   the setting for max object size <b>will be ignored</b> and
     *   should be configured in the Ehcache instead.
     * @param serializer alternative serialization mechanism
     */
    public EhcacheHttpCacheStorage(
            final Cache<String, T> cache,
            final CacheConfig config,
            final HttpCacheEntrySerializer<T> serializer) {
        super((config != null ? config : CacheConfig.DEFAULT).getMaxUpdateRetries(), serializer);
        this.cache = Args.notNull(cache, "Ehcache");
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
        return cache.get(storageKey);
    }

    @Override
    protected T getForUpdateCAS(final String storageKey) throws ResourceIOException {
        return cache.get(storageKey);
    }

    @Override
    protected T getStorageObject(final T element) throws ResourceIOException {
        return element;
    }

    @Override
    protected boolean updateCAS(
            final String storageKey, final T oldStorageObject, final T storageObject) throws ResourceIOException {
        return cache.replace(storageKey, oldStorageObject, storageObject);
    }

    @Override
    protected void delete(final String storageKey) throws ResourceIOException {
        cache.remove(storageKey);
    }

    @Override
    protected Map<String, T> bulkRestore(final Collection<String> storageKeys) throws ResourceIOException {
        final Map<String, T> resultMap = new HashMap<>();
        for (final String storageKey: storageKeys) {
            final T storageObject = cache.get(storageKey);
            if (storageObject != null) {
                resultMap.put(storageKey, storageObject);
            }
        }
        return resultMap;
    }

}
