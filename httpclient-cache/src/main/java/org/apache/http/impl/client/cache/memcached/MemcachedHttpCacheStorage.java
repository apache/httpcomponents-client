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
package org.apache.http.impl.client.cache.memcached;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.MemcachedClientIF;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.DefaultHttpCacheEntrySerializer;

/**
 * <p>This class is a storage backend that uses an external <i>memcached</i>
 * for storing cached origin responses. This storage option provides a
 * couple of interesting advantages over the default in-memory storage
 * backend:
 * <ol>
 * <li>in-memory cached objects can survive an application restart since
 * they are held in a separate process</li>
 * <li>it becomes possible for several cooperating applications to share
 * a large <i>memcached</i> farm together, effectively providing cache
 * peering of a sort</li>
 * </ol>
 * Note that in a shared memcached pool setting you may wish to make use
 * of the Ketama consistent hashing algorithm to reduce the number of 
 * cache misses that might result if one of the memcached cluster members
 * fails (see the <a href="http://dustin.github.com/java-memcached-client/apidocs/net/spy/memcached/KetamaConnectionFactory.html">
 * KetamaConnectionFactory</a>).
 * </p>
 * 
 * <p>Please refer to the <a href="http://code.google.com/p/memcached/wiki/NewStart">
 * memcached documentation</a> and in particular to the documentation for
 * the <a href="http://code.google.com/p/spymemcached/">spymemcached
 * documentation</a> for details about how to set up and configure memcached
 * and the Java client used here, respectively.</p>
 * 
 * @since 4.1
 */
public class MemcachedHttpCacheStorage implements HttpCacheStorage {

    private final MemcachedClientIF client;
    private final HttpCacheEntrySerializer serializer;
    private final int maxUpdateRetries;

    /**
     * Create a storage backend talking to a <i>memcached</i> instance
     * listening on the specified host and port. This is useful if you
     * just have a single local memcached instance running on the same
     * machine as your application, for example.
     * @param address where the <i>memcached</i> daemon is running
     * @throws IOException
     */
    public MemcachedHttpCacheStorage(InetSocketAddress address) throws IOException {
        this(new MemcachedClient(address));
    }

    /**
     * Create a storage backend using the pre-configured given
     * <i>memcached</i> client.
     * @param cache client to use for communicating with <i>memcached</i>
     */
    public MemcachedHttpCacheStorage(MemcachedClientIF cache) {
        this(cache, new CacheConfig(), new DefaultHttpCacheEntrySerializer());
    }

    /**
     * Create a storage backend using the given <i>memcached</i> client and
     * applying the given cache configuration and cache entry serialization
     * mechanism.
     * @param client how to talk to <i>memcached</i>
     * @param config apply HTTP cache-related options
     * @param serializer how to serialize the cache entries before writing
     *   them out to <i>memcached</i>. The provided {@link
     *   DefaultHttpCacheEntrySerializer} is a fine serialization mechanism
     *   to use here.
     */
    public MemcachedHttpCacheStorage(MemcachedClientIF client, CacheConfig config,
            HttpCacheEntrySerializer serializer) {
        this.client = client;
        this.maxUpdateRetries = config.getMaxUpdateRetries();
        this.serializer = serializer;
    }

    public void putEntry(String url, HttpCacheEntry entry) throws IOException  {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        serializer.writeTo(entry, bos);
        client.set(url, 0, bos.toByteArray());
    }

    public HttpCacheEntry getEntry(String url) throws IOException {
        byte[] data = (byte[]) client.get(url);
        if (null == data)
            return null;
        InputStream bis = new ByteArrayInputStream(data);
        return serializer.readFrom(bis);
    }

    public void removeEntry(String url) throws IOException {
        client.delete(url);
    }

    public void updateEntry(String url, HttpCacheUpdateCallback callback)
            throws HttpCacheUpdateException, IOException {
        int numRetries = 0;
        do{

        CASValue<Object> v = client.gets(url);
        byte[] oldBytes = (v != null) ? (byte[]) v.getValue() : null;
        HttpCacheEntry existingEntry = null;
        if (oldBytes != null) {
            ByteArrayInputStream bis = new ByteArrayInputStream(oldBytes);
            existingEntry = serializer.readFrom(bis);
        }
        HttpCacheEntry updatedEntry = callback.update(existingEntry);

        if (v == null) {
            putEntry(url, updatedEntry);
            return;

        } else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            serializer.writeTo(updatedEntry, bos);
            CASResponse casResult = client.cas(url, v.getCas(), bos.toByteArray());
            if (casResult != CASResponse.OK) {
                 numRetries++;
            }
            else return;
        }

    } while(numRetries <= maxUpdateRetries);
    throw new HttpCacheUpdateException("Failed to update");
    }
}