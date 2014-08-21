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

import java.io.IOException;
import java.net.InetSocketAddress;

import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.MemcachedClientIF;
import net.spy.memcached.OperationTimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.impl.client.cache.CacheConfig;

/**
 * <p>
 * This class is a storage backend that uses an external <i>memcached</i>
 * for storing cached origin responses. This storage option provides a
 * couple of interesting advantages over the default in-memory storage
 * backend:
 * </p>
 * <ol>
 * <li>in-memory cached objects can survive an application restart since
 * they are held in a separate process</li>
 * <li>it becomes possible for several cooperating applications to share
 * a large <i>memcached</i> farm together</li>
 * </ol>
 * <p>
 * Note that in a shared memcached pool setting you may wish to make use
 * of the Ketama consistent hashing algorithm to reduce the number of
 * cache misses that might result if one of the memcached cluster members
 * fails (see the <a href="http://dustin.github.com/java-memcached-client/apidocs/net/spy/memcached/KetamaConnectionFactory.html">
 * KetamaConnectionFactory</a>).
 * </p>
 * <p>
 * Because memcached places limits on the size of its keys, we need to
 * introduce a key hashing scheme to map the annotated URLs the higher-level
 * caching HTTP client wants to use as keys onto ones that are suitable
 * for use with memcached. Please see {@link KeyHashingScheme} if you would
 * like to use something other than the provided {@link SHA256KeyHashingScheme}.
 * </p>
 *
 * <p>
 * Because this hashing scheme can potentially result in key collisions (though
 * highly unlikely), we need to store the higher-level logical storage key along
 * with the {@link HttpCacheEntry} so that we can re-check it on retrieval. There
 * is a default serialization scheme provided for this, although you can provide
 * your own implementations of {@link MemcachedCacheEntry} and
 * {@link MemcachedCacheEntryFactory} to customize this serialization.
 * </p>
 *
 * <p>
 * Please refer to the <a href="http://code.google.com/p/memcached/wiki/NewStart">
 * memcached documentation</a> and in particular to the documentation for
 * the <a href="http://code.google.com/p/spymemcached/">spymemcached
 * documentation</a> for details about how to set up and configure memcached
 * and the Java client used here, respectively.
 * </p>
 *
 * @since 4.1
 */
public class MemcachedHttpCacheStorage implements HttpCacheStorage {

    private static final Log log = LogFactory.getLog(MemcachedHttpCacheStorage.class);

    private final MemcachedClientIF client;
    private final KeyHashingScheme keyHashingScheme;
    private final MemcachedCacheEntryFactory memcachedCacheEntryFactory;
    private final int maxUpdateRetries;

    /**
     * Create a storage backend talking to a <i>memcached</i> instance
     * listening on the specified host and port. This is useful if you
     * just have a single local memcached instance running on the same
     * machine as your application, for example.
     * @param address where the <i>memcached</i> daemon is running
     * @throws IOException in case of an error
     */
    public MemcachedHttpCacheStorage(final InetSocketAddress address) throws IOException {
        this(new MemcachedClient(address));
    }

    /**
     * Create a storage backend using the pre-configured given
     * <i>memcached</i> client.
     * @param cache client to use for communicating with <i>memcached</i>
     */
    public MemcachedHttpCacheStorage(final MemcachedClientIF cache) {
        this(cache, CacheConfig.DEFAULT, new MemcachedCacheEntryFactoryImpl(),
                new SHA256KeyHashingScheme());
    }

    /**
     * Create a storage backend using the given <i>memcached</i> client and
     * applying the given cache configuration and cache entry serialization
     * mechanism. <b>Deprecation note:</b> In the process of fixing a bug
     * based on the need to hash logical storage keys onto memcached cache
     * keys, the serialization process was revamped. This constructor still
     * works, but the serializer argument will be ignored and default
     * implementations of the new framework will be used. You can still
     * provide custom serialization by using the
     * {@link #MemcachedHttpCacheStorage(MemcachedClientIF, CacheConfig,
     * MemcachedCacheEntryFactory, KeyHashingScheme)} constructor.
     * @param client how to talk to <i>memcached</i>
     * @param config apply HTTP cache-related options
     * @param serializer <b>ignored</b>
     *
     * @deprecated (4.2) do not use
     */
    @Deprecated
    public MemcachedHttpCacheStorage(final MemcachedClientIF client, final CacheConfig config,
            final HttpCacheEntrySerializer serializer) {
        this(client, config, new MemcachedCacheEntryFactoryImpl(),
                new SHA256KeyHashingScheme());
    }

    /**
     * Create a storage backend using the given <i>memcached</i> client and
     * applying the given cache configuration, serialization, and hashing
     * mechanisms.
     * @param client how to talk to <i>memcached</i>
     * @param config apply HTTP cache-related options
     * @param memcachedCacheEntryFactory Factory pattern used for obtaining
     *   instances of alternative cache entry serialization mechanisms
     * @param keyHashingScheme how to map higher-level logical "storage keys"
     *   onto "cache keys" suitable for use with memcached
     */
    public MemcachedHttpCacheStorage(final MemcachedClientIF client, final CacheConfig config,
            final MemcachedCacheEntryFactory memcachedCacheEntryFactory,
            final KeyHashingScheme keyHashingScheme) {
        this.client = client;
        this.maxUpdateRetries = config.getMaxUpdateRetries();
        this.memcachedCacheEntryFactory = memcachedCacheEntryFactory;
        this.keyHashingScheme = keyHashingScheme;
    }

    @Override
    public void putEntry(final String url, final HttpCacheEntry entry) throws IOException  {
        final byte[] bytes = serializeEntry(url, entry);
        final String key = getCacheKey(url);
        if (key == null) {
            return;
        }
        try {
            client.set(key, 0, bytes);
        } catch (final OperationTimeoutException ex) {
            throw new MemcachedOperationTimeoutException(ex);
        }
    }

    private String getCacheKey(final String url) {
        try {
            return keyHashingScheme.hash(url);
        } catch (final MemcachedKeyHashingException mkhe) {
            return null;
        }
    }

    private byte[] serializeEntry(final String url, final HttpCacheEntry hce) throws IOException {
        final MemcachedCacheEntry mce = memcachedCacheEntryFactory.getMemcachedCacheEntry(url, hce);
        try {
            return mce.toByteArray();
        } catch (final MemcachedSerializationException mse) {
            final IOException ioe = new IOException();
            ioe.initCause(mse);
            throw ioe;
        }
    }

    private byte[] convertToByteArray(final Object o) {
        if (o == null) {
            return null;
        }
        if (!(o instanceof byte[])) {
            log.warn("got a non-bytearray back from memcached: " + o);
            return null;
        }
        return (byte[])o;
    }

    private MemcachedCacheEntry reconstituteEntry(final Object o) {
        final byte[] bytes = convertToByteArray(o);
        if (bytes == null) {
            return null;
        }
        final MemcachedCacheEntry mce = memcachedCacheEntryFactory.getUnsetCacheEntry();
        try {
            mce.set(bytes);
        } catch (final MemcachedSerializationException mse) {
            return null;
        }
        return mce;
    }

    @Override
    public HttpCacheEntry getEntry(final String url) throws IOException {
        final String key = getCacheKey(url);
        if (key == null) {
            return null;
        }
        try {
            final MemcachedCacheEntry mce = reconstituteEntry(client.get(key));
            if (mce == null || !url.equals(mce.getStorageKey())) {
                return null;
            }
            return mce.getHttpCacheEntry();
        } catch (final OperationTimeoutException ex) {
            throw new MemcachedOperationTimeoutException(ex);
        }
    }

    @Override
    public void removeEntry(final String url) throws IOException {
        final String key = getCacheKey(url);
        if (key == null) {
            return;
        }
        try {
            client.delete(key);
        } catch (final OperationTimeoutException ex) {
            throw new MemcachedOperationTimeoutException(ex);
        }
    }

    @Override
    public void updateEntry(final String url, final HttpCacheUpdateCallback callback)
            throws HttpCacheUpdateException, IOException {
        int numRetries = 0;
        final String key = getCacheKey(url);
        if (key == null) {
            throw new HttpCacheUpdateException("couldn't generate cache key");
        }
        do {
            try {
                final CASValue<Object> v = client.gets(key);
                MemcachedCacheEntry mce = (v == null) ? null
                        : reconstituteEntry(v.getValue());
                if (mce != null && (!url.equals(mce.getStorageKey()))) {
                    mce = null;
                }
                final HttpCacheEntry existingEntry = (mce == null) ? null
                        : mce.getHttpCacheEntry();
                final HttpCacheEntry updatedEntry = callback.update(existingEntry);

                if (existingEntry == null) {
                    putEntry(url, updatedEntry);
                    return;

                } else {
                    final byte[] updatedBytes = serializeEntry(url, updatedEntry);
                    final CASResponse casResult = client.cas(key, v.getCas(),
                            updatedBytes);
                    if (casResult != CASResponse.OK) {
                        numRetries++;
                    } else {
                        return;
                    }
                }
            } catch (final OperationTimeoutException ex) {
                throw new MemcachedOperationTimeoutException(ex);
            }
        } while (numRetries <= maxUpdateRetries);

        throw new HttpCacheUpdateException("Failed to update");
    }
}
