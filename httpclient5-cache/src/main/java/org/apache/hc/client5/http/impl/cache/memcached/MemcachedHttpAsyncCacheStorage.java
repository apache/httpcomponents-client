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
import java.util.concurrent.ExecutionException;

import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.client5.http.impl.cache.AbstractBinaryAsyncCacheStorage;
import org.apache.hc.client5.http.impl.cache.ByteArrayCacheEntrySerializer;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.util.Args;

import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.BulkGetCompletionListener;
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.internal.GetCompletionListener;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationCompletionListener;
import net.spy.memcached.internal.OperationFuture;

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
 * @since 5.0
 */
public class MemcachedHttpAsyncCacheStorage extends AbstractBinaryAsyncCacheStorage<CASValue<Object>> {

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
    public MemcachedHttpAsyncCacheStorage(final InetSocketAddress address) throws IOException {
        this(new MemcachedClient(address));
    }

    /**
     * Create a storage backend using the pre-configured given
     * <i>memcached</i> client.
     * @param cache client to use for communicating with <i>memcached</i>
     */
    public MemcachedHttpAsyncCacheStorage(final MemcachedClient cache) {
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
    public MemcachedHttpAsyncCacheStorage(
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
    protected byte[] getStorageObject(final CASValue<Object> casValue) throws ResourceIOException {
        return castAsByteArray(casValue.getValue());
    }

    private <T> Cancellable operation(final OperationFuture<T> operationFuture, final FutureCallback<T> callback) {
        operationFuture.addListener(new OperationCompletionListener() {

            @Override
            public void onComplete(final OperationFuture<?> future) throws Exception {
                try {
                    callback.completed(operationFuture.get());
                } catch (final ExecutionException ex) {
                    if (ex.getCause() instanceof Exception) {
                        callback.failed((Exception) ex.getCause());
                    } else {
                        callback.failed(ex);
                    }
                }
            }

        });
        return Operations.cancellable(operationFuture);
    }

    @Override
    protected Cancellable store(final String storageKey, final byte[] storageObject, final FutureCallback<Boolean> callback) {
        return operation(client.set(storageKey, 0, storageObject), callback);
    }

    @Override
    protected Cancellable restore(final String storageKey, final FutureCallback<byte[]> callback) {
        final GetFuture<Object> getFuture = client.asyncGet(storageKey);
        getFuture.addListener(new GetCompletionListener() {

            @Override
            public void onComplete(final GetFuture<?> future) throws Exception {
                try {
                    callback.completed(castAsByteArray(getFuture.get()));
                } catch (final ExecutionException ex) {
                    if (ex.getCause() instanceof Exception) {
                        callback.failed((Exception) ex.getCause());
                    } else {
                        callback.failed(ex);
                    }
                }
            }

        });
        return Operations.cancellable(getFuture);
    }

    @Override
    protected Cancellable getForUpdateCAS(final String storageKey, final FutureCallback<CASValue<Object>> callback) {
        return operation(client.asyncGets(storageKey), callback);
    }

    @Override
    protected Cancellable updateCAS(
            final String storageKey, final CASValue<Object> casValue, final byte[] storageObject, final FutureCallback<Boolean> callback) {
        return operation(client.asyncCAS(storageKey, casValue.getCas(), storageObject), new FutureCallback<CASResponse>() {

            @Override
            public void completed(final CASResponse result) {
                callback.completed(result == CASResponse.OK);
            }

            @Override
            public void failed(final Exception ex) {
                callback.failed(ex);
            }

            @Override
            public void cancelled() {
                callback.cancelled();
            }

        });
    }

    @Override
    protected Cancellable delete(final String storageKey, final FutureCallback<Boolean> callback) {
        return operation(client.delete(storageKey), callback);
    }

    @Override
    protected Cancellable bulkRestore(final Collection<String> storageKeys, final FutureCallback<Map<String, byte[]>> callback) {
        final BulkFuture<Map<String, Object>> future = client.asyncGetBulk(storageKeys);
        future.addListener(new BulkGetCompletionListener() {

            @Override
            public void onComplete(final BulkGetFuture<?> future) throws Exception {
                final Map<String, ?> storageObjectMap = future.get();
                final Map<String, byte[]> resultMap = new HashMap<>(storageObjectMap.size());
                for (final Map.Entry<String, ?> resultEntry: storageObjectMap.entrySet()) {
                    resultMap.put(resultEntry.getKey(), castAsByteArray(resultEntry.getValue()));
                }
                callback.completed(resultMap);
            }
        });
        return Operations.cancellable(future);
    }

}
