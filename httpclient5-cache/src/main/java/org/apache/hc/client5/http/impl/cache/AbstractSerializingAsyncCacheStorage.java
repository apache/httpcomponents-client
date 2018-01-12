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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.ComplexCancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.util.Args;

/**
 * Abstract cache backend for serialized objects capable of CAS (compare-and-swap) updates.
 *
 * @since 5.0
 */
public abstract class AbstractSerializingAsyncCacheStorage<T, CAS> implements HttpAsyncCacheStorage {

    private final int maxUpdateRetries;
    private final HttpCacheEntrySerializer<T> serializer;

    public AbstractSerializingAsyncCacheStorage(final int maxUpdateRetries, final HttpCacheEntrySerializer<T> serializer) {
        this.maxUpdateRetries = Args.notNegative(maxUpdateRetries, "Max retries");
        this.serializer = Args.notNull(serializer, "Cache entry serializer");
    }

    protected abstract String digestToStorageKey(String key);

    protected abstract T getStorageObject(CAS cas) throws ResourceIOException;

    protected abstract Cancellable store(String storageKey, T storageObject, FutureCallback<Boolean> callback);

    protected abstract Cancellable restore(String storageKey, FutureCallback<T> callback);

    protected abstract Cancellable getForUpdateCAS(String storageKey, FutureCallback<CAS> callback);

    protected abstract Cancellable updateCAS(String storageKey, CAS cas, T storageObject, FutureCallback<Boolean> callback);

    protected abstract Cancellable delete(String storageKey, FutureCallback<Boolean> callback);

    protected abstract Cancellable bulkRestore(Collection<String> storageKeys, FutureCallback<Map<String, T>> callback);

    @Override
    public final Cancellable putEntry(
            final String key, final HttpCacheEntry entry, final FutureCallback<Boolean> callback) {
        Args.notNull(key, "Storage key");
        Args.notNull(callback, "Callback");
        try {
            final String storageKey = digestToStorageKey(key);
            final T storageObject = serializer.serialize(new HttpCacheStorageEntry(key, entry));
            return store(storageKey, storageObject, callback);
        } catch (final Exception ex) {
            callback.failed(ex);
            return Operations.nonCancellable();
        }
    }

    @Override
    public final Cancellable getEntry(final String key, final FutureCallback<HttpCacheEntry> callback) {
        Args.notNull(key, "Storage key");
        Args.notNull(callback, "Callback");
        try {
            final String storageKey = digestToStorageKey(key);
            return restore(storageKey, new FutureCallback<T>() {

                @Override
                public void completed(final T storageObject) {
                    try {
                        if (storageObject != null) {
                            final HttpCacheStorageEntry entry = serializer.deserialize(storageObject);
                            if (key.equals(entry.getKey())) {
                                callback.completed(entry.getContent());
                            } else {
                                callback.completed(null);
                            }
                        } else {
                            callback.completed(null);
                        }
                    } catch (final Exception ex) {
                        callback.failed(ex);
                    }
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
        } catch (final Exception ex) {
            callback.failed(ex);
            return Operations.nonCancellable();
        }
    }

    @Override
    public final Cancellable removeEntry(final String key, final FutureCallback<Boolean> callback) {
        Args.notNull(key, "Storage key");
        Args.notNull(callback, "Callback");
        try {
            final String storageKey = digestToStorageKey(key);
            return delete(storageKey, callback);
        } catch (final Exception ex) {
            callback.failed(ex);
            return Operations.nonCancellable();
        }
    }

    @Override
    public final Cancellable updateEntry(
            final String key, final HttpCacheCASOperation casOperation, final FutureCallback<Boolean> callback) {
        Args.notNull(key, "Storage key");
        Args.notNull(casOperation, "CAS operation");
        Args.notNull(callback, "Callback");
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        final AtomicInteger count = new AtomicInteger(0);
        atemmptUpdateEntry(key, casOperation, complexCancellable, count, callback);
        return complexCancellable;
    }

    private void atemmptUpdateEntry(
            final String key,
            final HttpCacheCASOperation casOperation,
            final ComplexCancellable complexCancellable,
            final AtomicInteger count,
            final FutureCallback<Boolean> callback) {
        try {
            final String storageKey = digestToStorageKey(key);
            complexCancellable.setDependency(getForUpdateCAS(storageKey, new FutureCallback<CAS>() {

                @Override
                public void completed(final CAS cas) {
                    try {
                        HttpCacheStorageEntry storageEntry = cas != null ? serializer.deserialize(getStorageObject(cas)) : null;
                        if (storageEntry != null && !key.equals(storageEntry.getKey())) {
                            storageEntry = null;
                        }
                        final HttpCacheEntry existingEntry = storageEntry != null ? storageEntry.getContent() : null;
                        final HttpCacheEntry updatedEntry = casOperation.execute(existingEntry);
                        if (existingEntry == null) {
                            putEntry(key, updatedEntry, callback);
                        } else {
                            final T storageObject = serializer.serialize(new HttpCacheStorageEntry(key, updatedEntry));
                            complexCancellable.setDependency(updateCAS(storageKey, cas, storageObject, new FutureCallback<Boolean>() {

                                @Override
                                public void completed(final Boolean result) {
                                    if (result) {
                                        callback.completed(result);
                                    } else {
                                        if (!complexCancellable.isCancelled()) {
                                            final int numRetries = count.incrementAndGet();
                                            if (numRetries >= maxUpdateRetries) {
                                                callback.failed(new HttpCacheUpdateException("Cache update failed after " + numRetries + " retries"));
                                            } else {
                                                atemmptUpdateEntry(key, casOperation, complexCancellable, count, callback);
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    callback.failed(ex);
                                }

                                @Override
                                public void cancelled() {
                                    callback.cancelled();
                                }

                            }));
                        }
                    } catch (final Exception ex) {
                        callback.failed(ex);
                    }
                }

                @Override
                public void failed(final Exception ex) {
                    callback.failed(ex);
                }

                @Override
                public void cancelled() {
                    callback.cancelled();
                }

            }));
        } catch (final Exception ex) {
            callback.failed(ex);
        }
    }

    @Override
    public final Cancellable getEntries(final Collection<String> keys, final FutureCallback<Map<String, HttpCacheEntry>> callback) {
        Args.notNull(keys, "Storage keys");
        Args.notNull(callback, "Callback");
        try {
            final List<String> storageKeys = new ArrayList<>(keys.size());
            for (final String key: keys) {
                storageKeys.add(digestToStorageKey(key));
            }
            return bulkRestore(storageKeys, new FutureCallback<Map<String, T>>() {

                @Override
                public void completed(final Map<String, T> storageObjectMap) {
                    try {
                        final Map<String, HttpCacheEntry> resultMap = new HashMap<>();
                        for (final String key: keys) {
                            final String storageKey = digestToStorageKey(key);
                            final T storageObject = storageObjectMap.get(storageKey);
                            if (storageObject != null) {
                                final HttpCacheStorageEntry entry = serializer.deserialize(storageObject);
                                if (key.equals(entry.getKey())) {
                                    resultMap.put(key, entry.getContent());
                                }
                            }
                        }
                        callback.completed(resultMap);
                    } catch (final Exception ex) {
                        callback.failed(ex);
                    }
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
        } catch (final Exception ex) {
            callback.failed(ex);
            return Operations.nonCancellable();
        }
    }

}
