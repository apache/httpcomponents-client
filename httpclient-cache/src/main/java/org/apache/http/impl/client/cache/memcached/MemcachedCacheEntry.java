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
 * Provides for serialization and deserialization of higher-level
 * {@link HttpCacheEntry} objects into byte arrays suitable for
 * storage in memcached. Clients wishing to change the serialization
 * mechanism from the provided defaults should implement this
 * interface as well as {@link MemcachedCacheEntryFactory}.
 */
public interface MemcachedCacheEntry {

    /**
     * Returns a serialized representation of the current cache entry.
     */
    byte[] toByteArray();

    /**
     * Returns the storage key associated with this entry. May return
     * {@code null} if this is an "unset" instance waiting to be
     * {@link #set(byte[])} with a serialized representation.
     */
    String getStorageKey();

    /**
     * Returns the {@link HttpCacheEntry} associated with this entry.
     * May return {@code null} if this is an "unset" instance
     * waiting to be {@link #set(byte[])} with a serialized
     * representation.
     */
    HttpCacheEntry getHttpCacheEntry();

    /**
     * Given a serialized representation of a {@link MemcachedCacheEntry},
     * attempt to reconstitute the storage key and {@link HttpCacheEntry}
     * represented therein. After a successful call to this method, this
     * object should return updated (as appropriate) values for
     * {@link #getStorageKey()} and {@link #getHttpCacheEntry()}. This
     * should be viewed as an atomic operation on the
     * {@code MemcachedCacheEntry}.
     *
     * @param bytes serialized representation
     * @throws MemcachedSerializationException if deserialization
     *   fails. In this case, the prior values for {{@link #getStorageKey()}
     *   and {@link #getHttpCacheEntry()} should remain unchanged.
     */
    void set(byte[] bytes);

}
