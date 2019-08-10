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
package org.apache.hc.client5.http.impl.cache.memcached;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.cache.AbstractBinaryCacheStorage;
import org.apache.hc.client5.http.impl.cache.ByteArrayCacheEntrySerializer;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.core5.util.Args;

import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.OperationTimeoutException;

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
 * Please refer to the <a href="http://code.google.com/p/memcached/wiki/NewStart">
 * memcached documentation</a> and in particular to the documentation for
 * the <a href="http://code.google.com/p/spymemcached/">spymemcached
 * documentation</a> for details about how to set up and configure memcached
 * and the Java client used here, respectively.
 * </p>
 *
 * @since 4.1
 */
public class MemcachedHttpCacheStorage extends AbstractBinaryCacheStorage<CASValue<Object>> {

    private final MemcachedClient client;
    private final KeyHashingScheme keyHashingScheme;

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
    public MemcachedHttpCacheStorage(final MemcachedClient cache) {
        this(cache, CacheConfig.DEFAULT, ByteArrayCacheEntrySerializer.INSTANCE, SHA256KeyHashingScheme.INSTANCE);
    }

    /**
     * Create a storage backend using the given <i>memcached</i> client and
     * applying the given cache configuration, serialization, and hashing
     * mechanisms.
     * @param client how to talk to <i>memcached</i>
     * @param config apply HTTP cache-related options
     * @param serializer alternative serialization mechanism
     * @param keyHashingScheme how to map higher-level logical "storage keys"
     *   onto "cache keys" suitable for use with memcached
     */
    public MemcachedHttpCacheStorage(
            final MemcachedClient client,
            final CacheConfig config,
            final HttpCacheEntrySerializer<byte[]> serializer,
            final KeyHashingScheme keyHashingScheme) {
        super((config != null ? config : CacheConfig.DEFAULT).getMaxUpdateRetries(),
                serializer != null ? serializer : ByteArrayCacheEntrySerializer.INSTANCE);
        this.client = Args.notNull(client, "Memcached client");
        this.keyHashingScheme = keyHashingScheme;
    }

    @Override
    protected String digestToStorageKey(final String key) {
        return keyHashingScheme.hash(key);
    }

    @Override
    protected void store(final String storageKey, final byte[] storageObject) throws ResourceIOException {
        client.set(storageKey, 0, storageObject);
    }

    private byte[] castAsByteArray(final Object storageObject) throws ResourceIOException {
        if (storageObject == null) {
            return null;
        }
        if (storageObject instanceof byte[]) {
            return (byte[]) storageObject;
        }
        throw new ResourceIOException("Unexpected cache content: " + storageObject.getClass());
    }

    @Override
    protected byte[] restore(final String storageKey) throws ResourceIOException {
        try {
            return castAsByteArray(client.get(storageKey));
        } catch (final OperationTimeoutException ex) {
            throw new MemcachedOperationTimeoutException(ex);
        }
    }

    @Override
    protected CASValue<Object> getForUpdateCAS(final String storageKey) throws ResourceIOException {
        try {
            return client.gets(storageKey);
        } catch (final OperationTimeoutException ex) {
            throw new MemcachedOperationTimeoutException(ex);
        }
    }

    @Override
    protected byte[] getStorageObject(final CASValue<Object> casValue) throws ResourceIOException {
        return castAsByteArray(casValue.getValue());
    }

    @Override
    protected boolean updateCAS(
            final String storageKey, final CASValue<Object> casValue, final byte[] storageObject) throws ResourceIOException {
        final CASResponse casResult = client.cas(storageKey, casValue.getCas(), storageObject);
        return casResult == CASResponse.OK;
    }

    @Override
    protected void delete(final String storageKey) throws ResourceIOException {
        client.delete(storageKey);
    }

    @Override
    protected Map<String, byte[]> bulkRestore(final Collection<String> storageKeys) throws ResourceIOException {
        final Map<String, ?> storageObjectMap = client.getBulk(storageKeys);
        final Map<String, byte[]> resultMap = new HashMap<>(storageObjectMap.size());
        for (final Map.Entry<String, ?> resultEntry: storageObjectMap.entrySet()) {
            resultMap.put(resultEntry.getKey(), castAsByteArray(resultEntry.getValue()));
        }
        return resultMap;
    }

}
