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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.HttpCacheOperationException;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.impl.client.cache.DefaultHttpCacheEntrySerializer;

public class EhcacheHttpCacheStorage implements HttpCacheStorage {

    private final Ehcache cache;
    private final HttpCacheEntrySerializer serializer;

    public EhcacheHttpCacheStorage(Ehcache cache) {
        this(cache, new DefaultHttpCacheEntrySerializer());
    }

    public EhcacheHttpCacheStorage(Ehcache cache, HttpCacheEntrySerializer serializer){
        this.cache = cache;
        this.serializer = serializer;
    }

    public synchronized void putEntry(String key, HttpCacheEntry entry) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        serializer.writeTo(entry, bos);
        cache.put(new Element(key, bos.toByteArray()));
    }

    public synchronized HttpCacheEntry getEntry(String key) throws IOException {
        Element e = cache.get(key);
        if(e == null){
            return null;
        }

        byte[] data = (byte[])e.getValue();
        return serializer.readFrom(new ByteArrayInputStream(data));
    }

    public synchronized void removeEntry(String key) {
        cache.remove(key);
    }

    public synchronized void updateEntry(String key, HttpCacheUpdateCallback callback)
            throws IOException {
        Element oldElement = cache.get(key);

        HttpCacheEntry existingEntry = null;
        if(oldElement != null){
            byte[] data = (byte[])oldElement.getValue();
            existingEntry = serializer.readFrom(new ByteArrayInputStream(data));
        }

        HttpCacheEntry updatedEntry = callback.update(existingEntry);

        if (existingEntry == null) {
            putEntry(key, updatedEntry);
        } else {
            // Attempt to do a CAS replace, if we fail throw an IOException for now
            // While this operation should work fine within this instance, multiple instances
            //  could trample each others' data
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            serializer.writeTo(updatedEntry, bos);
            Element newElement = new Element(key, bos.toByteArray());
            if (!cache.replace(oldElement, newElement)) {
                throw new HttpCacheOperationException("Replace operation failed");
            }
        }
    }
}