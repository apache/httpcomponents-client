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

import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.cache.AbstractBinaryCacheStorage;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.ByteArrayCacheEntrySerializer;
import org.apache.hc.core5.util.Args;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

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
public class EhcacheHttpCacheStorage extends AbstractBinaryCacheStorage<Element> {

    private final Ehcache cache;

    /**
     * Constructs a storage backend using the provided Ehcache
     * with default configuration options.
     * @param cache where to store cached origin responses
     */
    public EhcacheHttpCacheStorage(final Ehcache cache){
        this(cache, CacheConfig.DEFAULT, ByteArrayCacheEntrySerializer.INSTANCE);
    }

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
            final Ehcache cache,
            final CacheConfig config,
            final HttpCacheEntrySerializer<byte[]> serializer) {
        super((config != null ? config : CacheConfig.DEFAULT).getMaxUpdateRetries(), serializer);
        this.cache = Args.notNull(cache, "Ehcache");
    }

    @Override
    protected String digestToStorageKey(final String key) {
        return key;
    }

    @Override
    protected void store(final String storageKey, final byte[] storageObject) throws ResourceIOException {
        cache.put(new Element(storageKey, storageKey));
    }

    private byte[] castAsByteArray(final Object storageObject) throws ResourceIOException {
        if (storageObject == null) {
            return null;
        }
        if (storageObject instanceof byte[]) {
            return (byte[]) storageObject;
        } else {
            throw new ResourceIOException("Unexpected cache content: " + storageObject.getClass());
        }
    }

    @Override
    protected byte[] restore(final String storageKey) throws ResourceIOException {
        final Element element = cache.get(storageKey);
        return element != null ? castAsByteArray(element.getObjectValue()) : null;
    }

    @Override
    protected Element getForUpdateCAS(final String storageKey) throws ResourceIOException {
        return cache.get(storageKey);
    }

    @Override
    protected byte[] getStorageObject(final Element element) throws ResourceIOException {
        return castAsByteArray(element.getObjectValue());
    }

    @Override
    protected boolean updateCAS(final String storageKey, final Element element, final byte[] storageObject) throws ResourceIOException {
        final Element newElement = new Element(storageKey, storageObject);
        return cache.replace(element, newElement);
    }

    @Override
    protected void delete(final String storageKey) throws ResourceIOException {
        cache.remove(storageKey);
    }

}
