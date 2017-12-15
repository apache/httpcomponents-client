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

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceIOException;

class SimpleHttpCacheStorage implements HttpCacheStorage {

    public final Map<String,HttpCacheEntry> map;

    public SimpleHttpCacheStorage() {
        map = new HashMap<>();
    }

    @Override
    public void putEntry(final String key, final HttpCacheEntry entry) throws ResourceIOException {
        map.put(key, entry);
    }

    @Override
    public HttpCacheEntry getEntry(final String key) throws ResourceIOException {
        return map.get(key);
    }

    @Override
    public void removeEntry(final String key) throws ResourceIOException {
        map.remove(key);
    }

    @Override
    public void updateEntry(
            final String key, final HttpCacheCASOperation casOperation) throws ResourceIOException {
        final HttpCacheEntry v1 = map.get(key);
        final HttpCacheEntry v2 = casOperation.execute(v1);
        map.put(key,v2);
    }

    @Override
    public Map<String, HttpCacheEntry> getEntries(final Collection<String> keys) throws ResourceIOException {
        final Map<String, HttpCacheEntry> resultMap = new HashMap<>(keys.size());
        for (final String key: keys) {
            final HttpCacheEntry entry = getEntry(key);
            if (entry != null) {
                resultMap.put(key, entry);
            }
        }
        return resultMap;
    }

}
