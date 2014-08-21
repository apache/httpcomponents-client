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

import org.apache.http.client.cache.HttpCacheEntry;

/**
 * Creates {@link MemcachedCacheEntry} instances that can be used for
 * serializing and deserializing {@link HttpCacheEntry} instances for
 * storage in memcached.
 */
public interface MemcachedCacheEntryFactory {

    /**
     * Creates a new {@link MemcachedCacheEntry} for storing the
     * given {@link HttpCacheEntry} under the given storage key. Since
     * we are hashing storage keys into cache keys to accommodate
     * limitations in memcached's key space, it is possible to have
     * cache collisions. Therefore, we store the storage key along
     * with the {@code HttpCacheEntry} so it can be compared
     * on retrieval and thus detect collisions.
     * @param storageKey storage key under which the entry will
     *   be logically stored
     * @param entry the cache entry to store
     * @return a {@link MemcachedCacheEntry} ready to provide
     *   a serialized representation
     */
    MemcachedCacheEntry getMemcachedCacheEntry(String storageKey, HttpCacheEntry entry);

    /**
     * Creates an "unset" {@link MemcachedCacheEntry} ready to accept
     * a serialized representation via {@link MemcachedCacheEntry#set(byte[])}
     * and deserialize it into a storage key and a {@link HttpCacheEntry}.
     * @return {@code MemcachedCacheEntry}
     */
    MemcachedCacheEntry getUnsetCacheEntry();

}
