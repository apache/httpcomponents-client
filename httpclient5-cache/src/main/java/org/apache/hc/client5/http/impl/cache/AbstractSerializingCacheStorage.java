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

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.util.Args;

/**
 * Abstract cache backend for serialized objects capable of CAS (compare-and-swap) updates.
 *
 * @since 5.0
 */
public abstract class AbstractSerializingCacheStorage<T, CAS> implements HttpCacheStorage {

    private final int maxUpdateRetries;
    private final HttpCacheEntrySerializer<T> serializer;

    public AbstractSerializingCacheStorage(final int maxUpdateRetries, final HttpCacheEntrySerializer<T> serializer) {
        this.maxUpdateRetries = Args.notNegative(maxUpdateRetries, "Max retries");
        this.serializer = Args.notNull(serializer, "Cache entry serializer");
    }

    protected abstract String digestToStorageKey(String key);

    protected abstract void store(String storageKey, T storageObject) throws ResourceIOException;

    protected abstract T restore(String storageKey) throws ResourceIOException;

    protected abstract CAS getForUpdateCAS(String storageKey) throws ResourceIOException;

    protected abstract T getStorageObject(CAS cas) throws ResourceIOException;

    protected abstract boolean updateCAS(String storageKey, CAS cas, T storageObject) throws ResourceIOException;

    protected abstract void delete(String storageKey) throws ResourceIOException;

    protected abstract Map<String, T> bulkRestore(Collection<String> storageKeys) throws ResourceIOException;

    @Override
    public final void putEntry(final String key, final HttpCacheEntry entry) throws ResourceIOException {
        final String storageKey = digestToStorageKey(key);
        final T storageObject = serializer.serialize(new HttpCacheStorageEntry(key, entry));
        store(storageKey, storageObject);
    }

    @Override
    public final HttpCacheEntry getEntry(final String key) throws ResourceIOException {
        final String storageKey = digestToStorageKey(key);
        final T storageObject = restore(storageKey);
        if (storageObject == null) {
            return null;
        }
        final HttpCacheStorageEntry entry = serializer.deserialize(storageObject);
        if (key.equals(entry.getKey())) {
            return entry.getContent();
        } else {
            return null;
        }
    }

    @Override
    public final void removeEntry(final String key) throws ResourceIOException {
        final String storageKey = digestToStorageKey(key);
        delete(storageKey);
    }

    @Override
    public final void updateEntry(
            final String key,
            final HttpCacheCASOperation casOperation) throws HttpCacheUpdateException, ResourceIOException {
        int numRetries = 0;
        final String storageKey = digestToStorageKey(key);
        for (;;) {
            final CAS cas = getForUpdateCAS(storageKey);
            HttpCacheStorageEntry storageEntry = cas != null ? serializer.deserialize(getStorageObject(cas)) : null;
            if (storageEntry != null && !key.equals(storageEntry.getKey())) {
                storageEntry = null;
            }
            final HttpCacheEntry existingEntry = storageEntry != null ? storageEntry.getContent() : null;
            final HttpCacheEntry updatedEntry = casOperation.execute(existingEntry);

            if (existingEntry == null) {
                putEntry(key, updatedEntry);
                return;

            }
            final T storageObject = serializer.serialize(new HttpCacheStorageEntry(key, updatedEntry));
            if (!updateCAS(storageKey, cas, storageObject)) {
                numRetries++;
                if (numRetries >= maxUpdateRetries) {
                    throw new HttpCacheUpdateException("Cache update failed after " + numRetries + " retries");
                }
            } else {
                return;
            }
        }
    }

    @Override
    public final Map<String, HttpCacheEntry> getEntries(final Collection<String> keys) throws ResourceIOException {
        Args.notNull(keys, "Storage keys");
        final List<String> storageKeys = new ArrayList<>(keys.size());
        for (final String key: keys) {
            storageKeys.add(digestToStorageKey(key));
        }
        final Map<String, T> storageObjectMap = bulkRestore(storageKeys);
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
        return resultMap;
    }

}
