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
package org.apache.hc.client5.http.cache;

import java.util.Collection;
import java.util.Map;

import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.util.Args;

/**
 * {@link HttpAsyncCacheStorage} implementation that emulates asynchronous
 * behavior using an instance of classic {@link HttpCacheStorage}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public final class HttpAsyncCacheStorageAdaptor implements HttpAsyncCacheStorage {

    private final HttpCacheStorage cacheStorage;

    public HttpAsyncCacheStorageAdaptor(final HttpCacheStorage cacheStorage) {
        this.cacheStorage = Args.notNull(cacheStorage, "Cache strorage");
    }

    @Override
    public Cancellable putEntry(final String key, final HttpCacheEntry entry, final FutureCallback<Boolean> callback) {
        Args.notEmpty(key, "Key");
        Args.notNull(entry, "Cache ehtry");
        Args.notNull(callback, "Callback");
        try {
            cacheStorage.putEntry(key, entry);
            callback.completed(Boolean.TRUE);
        } catch (final Exception ex) {
            callback.failed(ex);
        }
        return Operations.nonCancellable();
    }

    @Override
    public Cancellable getEntry(final String key, final FutureCallback<HttpCacheEntry> callback) {
        Args.notEmpty(key, "Key");
        Args.notNull(callback, "Callback");
        try {
            final HttpCacheEntry entry = cacheStorage.getEntry(key);
            callback.completed(entry);
        } catch (final Exception ex) {
            callback.failed(ex);
        }
        return Operations.nonCancellable();
    }

    @Override
    public Cancellable removeEntry(final String key, final FutureCallback<Boolean> callback) {
        Args.notEmpty(key, "Key");
        Args.notNull(callback, "Callback");
        try {
            cacheStorage.removeEntry(key);
            callback.completed(Boolean.TRUE);
        } catch (final Exception ex) {
            callback.failed(ex);
        }
        return Operations.nonCancellable();
    }

    @Override
    public Cancellable updateEntry(
            final String key, final HttpCacheCASOperation casOperation, final FutureCallback<Boolean> callback) {
        Args.notEmpty(key, "Key");
        Args.notNull(casOperation, "CAS operation");
        Args.notNull(callback, "Callback");
        try {
            cacheStorage.updateEntry(key, casOperation);
            callback.completed(Boolean.TRUE);
        } catch (final Exception ex) {
            callback.failed(ex);
        }
        return Operations.nonCancellable();
    }

    @Override
    public Cancellable getEntries(final Collection<String> keys, final FutureCallback<Map<String, HttpCacheEntry>> callback) {
        Args.notNull(keys, "Key");
        Args.notNull(callback, "Callback");
        try {
            callback.completed(cacheStorage.getEntries(keys));
        } catch (final Exception ex) {
            callback.failed(ex);
        }
        return Operations.nonCancellable();
    }

}
