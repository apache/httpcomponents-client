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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;

/**
 * {@literal HttpAsyncCacheStorage} represents an abstract HTTP cache
 * storage backend that can then be plugged into the  asynchronous
 * (non-blocking ) request execution
 * pipeline.
 * <p>
 * Implementations of this interface are expected to be threading-safe.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public interface HttpAsyncCacheStorage {

    /**
     * Store a given cache entry under the given key.
     * @param key where in the cache to store the entry
     * @param entry cached response to store
     * @param callback result callback
     */
    Cancellable putEntry(
            String key, HttpCacheEntry entry, FutureCallback<Boolean> callback);

    /**
     * Retrieves the cache entry stored under the given key
     * or null if no entry exists under that key.
     * @param key cache key
     * @param callback result callback
     * @return an {@link HttpCacheEntry} or {@code null} if no
     *   entry exists
     */
    Cancellable getEntry(
            String key, FutureCallback<HttpCacheEntry> callback);

    /**
     * Deletes/invalidates/removes any cache entries currently
     * stored under the given key.
     * @param key
     * @param callback result callback
     */
    Cancellable removeEntry(
            String key, FutureCallback<Boolean> callback);

    /**
     * Atomically applies the given callback to processChallenge an existing cache
     * entry under a given key.
     * @param key indicates which entry to modify
     * @param casOperation the CAS operation to perform.
     * @param callback result callback
     */
    Cancellable updateEntry(
            String key, HttpCacheCASOperation casOperation, FutureCallback<Boolean> callback);


    /**
     * Retrieves multiple cache entries stored under the given keys. Some implementations
     * may use a single bulk operation to do the retrieval.
     *
     * @param keys cache keys
     * @param callback result callback
     */
    Cancellable getEntries(Collection<String> keys, FutureCallback<Map<String, HttpCacheEntry>> callback);

}
