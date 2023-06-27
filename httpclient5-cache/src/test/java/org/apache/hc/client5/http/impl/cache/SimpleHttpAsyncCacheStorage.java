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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;

class SimpleHttpAsyncCacheStorage implements HttpAsyncCacheStorage {

    public final Map<String,HttpCacheEntry> map;

    public SimpleHttpAsyncCacheStorage() {
        map = new HashMap<>();
    }

    @Override
    public Cancellable putEntry(final String key, final HttpCacheEntry entry, final FutureCallback<Boolean> callback) {
        map.put(key, entry);
        if (callback != null) {
            callback.completed(true);
        }
        return Operations.nonCancellable();
    }

    public void putEntry(final String key, final HttpCacheEntry entry) {
        map.put(key, entry);
    }

    @Override
    public Cancellable getEntry(final String key, final FutureCallback<HttpCacheEntry> callback) {
        final HttpCacheEntry entry = map.get(key);
        if (callback != null) {
            callback.completed(entry);
        }
        return Operations.nonCancellable();
    }

    public HttpCacheEntry getEntry(final String key) {
        return map.get(key);
    }

    @Override
    public Cancellable removeEntry(final String key, final FutureCallback<Boolean> callback) {
        final HttpCacheEntry removed = map.remove(key);
        if (callback != null) {
            callback.completed(removed != null);
        }
        return Operations.nonCancellable();
    }

    @Override
    public Cancellable updateEntry(final String key, final HttpCacheCASOperation casOperation, final FutureCallback<Boolean> callback) {
        final HttpCacheEntry v1 = map.get(key);
        try {
            final HttpCacheEntry v2 = casOperation.execute(v1);
            map.put(key,v2);
            if (callback != null) {
                callback.completed(true);
            }
        } catch (final ResourceIOException ex) {
            if (callback != null) {
                callback.failed(ex);
            }
        }
        return Operations.nonCancellable();
    }

    @Override
    public Cancellable getEntries(final Collection<String> keys, final FutureCallback<Map<String, HttpCacheEntry>> callback) {
        final Map<String, HttpCacheEntry> resultMap = new HashMap<>(keys.size());
        for (final String key: keys) {
            final HttpCacheEntry entry = map.get(key);
            if (entry != null) {
                resultMap.put(key, entry);
            }
        }
        callback.completed(resultMap);
        return Operations.nonCancellable();
    }

}
