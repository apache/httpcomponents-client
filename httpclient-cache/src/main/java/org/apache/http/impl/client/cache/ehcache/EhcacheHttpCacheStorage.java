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
package org.apache.http.impl.client.cache.ehcache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.DefaultHttpCacheEntrySerializer;

/**
 * <p>This class is a storage backend for cache entries that uses the
 * popular <a href="http://ehcache.org">Ehcache</a> cache implementation.
 * In particular, this backend allows for spillover to disk, where the
 * cache can be effectively larger than memory, and cached responses are
 * paged into and out of memory from disk as needed.</p>
 * 
 * <p><b>N.B.</b> Since the Ehcache is configured ahead of time with a
 * maximum number of cache entries, this effectively ignores the
 * {@link CacheConfig#setMaxCacheEntries(int) maximum cache entries}
 * specified by a provided {@link CacheConfig}.</p>
 * 
 * <p>Please refer to the <a href="http://ehcache.org/documentation/index.html">
 * Ehcache documentation</a> for details on how to configure the Ehcache
 * itself.</p>
 * @since 4.1
 */
public class EhcacheHttpCacheStorage implements HttpCacheStorage {

    private final Ehcache cache;
    private final HttpCacheEntrySerializer serializer;
    private final int maxUpdateRetries;

    /**
     * Constructs a storage backend using the provided Ehcache
     * with default configuration options.
     * @param cache where to store cached origin responses
     */
    public EhcacheHttpCacheStorage(Ehcache cache) {
        this(cache, new CacheConfig(), new DefaultHttpCacheEntrySerializer());
    }

    /**
     * Constructs a storage backend using the provided Ehcache
     * with the given configuration options.
     * @param cache where to store cached origin responses
     * @param config cache storage configuration options - note that
     *   the setting for max object size <b>will be ignored</b> and
     *   should be configured in the Ehcache instead.
     */
    public EhcacheHttpCacheStorage(Ehcache cache, CacheConfig config){
        this(cache, config, new DefaultHttpCacheEntrySerializer());
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
    public EhcacheHttpCacheStorage(Ehcache cache, CacheConfig config, HttpCacheEntrySerializer serializer){
        this.cache = cache;
        this.maxUpdateRetries = config.getMaxUpdateRetries();
        this.serializer = serializer;
    }

    public synchronized void putEntry(String key, HttpCacheEntry entry) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        serializer.writeTo(entry, bos);
        cache.put(new Element(key, bos.toByteArray()));
    }

    public synchronized HttpCacheEntry getEntry(String key) throws IOException {
        Element e = cache.get(key);
        if(e == null){
            return null;
        }

        byte[] data = (byte[])e.getValue();
        return serializer.readFrom(new ByteArrayInputStream(data));
    }

    public synchronized void removeEntry(String key) {
        cache.remove(key);
    }

    public synchronized void updateEntry(String key, HttpCacheUpdateCallback callback)
            throws IOException, HttpCacheUpdateException {
        int numRetries = 0;
        do{
            Element oldElement = cache.get(key);

            HttpCacheEntry existingEntry = null;
            if(oldElement != null){
                byte[] data = (byte[])oldElement.getValue();
                existingEntry = serializer.readFrom(new ByteArrayInputStream(data));
            }

            HttpCacheEntry updatedEntry = callback.update(existingEntry);

            if (existingEntry == null) {
                putEntry(key, updatedEntry);
                return;
            } else {
                // Attempt to do a CAS replace, if we fail then retry
                // While this operation should work fine within this instance, multiple instances
                //  could trample each others' data
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                serializer.writeTo(updatedEntry, bos);
                Element newElement = new Element(key, bos.toByteArray());
                if (cache.replace(oldElement, newElement)) {
                    return;
                }else{
                    numRetries++;
                }
            }
        }while(numRetries <= maxUpdateRetries);
        throw new HttpCacheUpdateException("Failed to update");
    }
}