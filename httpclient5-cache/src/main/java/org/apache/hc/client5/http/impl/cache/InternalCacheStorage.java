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

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.core5.annotation.Internal;

@Internal
final public class InternalCacheStorage {

    private final Map<String, HttpCacheEntry> map;
    private final Queue<HttpCacheStorageEntry> evictionQueue;
    private final Consumer<HttpCacheStorageEntry> evictionCallback;

    public InternalCacheStorage(final int maxEntries, final Consumer<HttpCacheStorageEntry> evictionCallback) {
        this.evictionCallback = evictionCallback;
        this.map = new LinkedHashMap<String, HttpCacheEntry>(20, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, HttpCacheEntry> eldest) {
                if (size() > maxEntries) {
                    if (evictionCallback != null) {
                        evictionQueue.add(new HttpCacheStorageEntry(eldest.getKey(), eldest.getValue()));
                    }
                    return true;
                } else {
                    return false;
                }
            }

        };
        this.evictionQueue = new LinkedList<>();
    }

    public InternalCacheStorage(final int maxEntries) {
        this(maxEntries, null);
    }

    public InternalCacheStorage() {
        this(Integer.MAX_VALUE, null);
    }

    public void put(final String key, final HttpCacheEntry entry) {
        map.put(key, entry);
        HttpCacheStorageEntry evicted;
        while ((evicted = evictionQueue.poll()) != null) {
            if (evictionCallback != null) {
                evictionCallback.accept(evicted);
            }
        }
    }

    public HttpCacheEntry get(final String key) {
        return map.get(key);
    }

    public HttpCacheEntry remove(final String key) {
        return map.remove(key);
    }

    public void clear() {
        map.clear();
    }

}
