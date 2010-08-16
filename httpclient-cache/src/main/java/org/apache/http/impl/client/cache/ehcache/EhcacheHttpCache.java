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
package org.apache.http.impl.client.cache.ehcache;

import java.io.IOException;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheUpdateCallback;

public class EhcacheHttpCache implements HttpCache {

    private final Ehcache cache;

    public EhcacheHttpCache(Ehcache cache) {
        this.cache = cache;
    }

    public synchronized void putEntry(String key, HttpCacheEntry entry) throws IOException {
        cache.put(new Element(key, entry));
    }

    public synchronized HttpCacheEntry getEntry(String url) {
        Element e = cache.get(url);
        return (e != null) ? (HttpCacheEntry)e.getValue() : null;
    }

    public synchronized void removeEntry(String url) {
        cache.remove(url);
    }

    public synchronized void updateEntry(String key, HttpCacheUpdateCallback callback)
            throws IOException {
        Element e = cache.get(key);
        HttpCacheEntry existingEntry = (e != null) ? (HttpCacheEntry)e.getValue() : null;
        HttpCacheEntry updatedEntry = callback.update(existingEntry);

        if (e == null) {
            putEntry(key, updatedEntry);
        } else {
            // Attempt to do a CAS replace, if we fail throw an IOException for now
            // While this operation should work fine within this instance, multiple instances
            //  could trample each others' data
            if (!cache.replace(e, new Element(key, updatedEntry))) {
                throw new IOException();
            }
        }
    }
}