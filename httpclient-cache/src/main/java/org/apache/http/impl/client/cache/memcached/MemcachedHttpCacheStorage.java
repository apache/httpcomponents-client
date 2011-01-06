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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.MemcachedClientIF;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.DefaultHttpCacheEntrySerializer;

public class MemcachedHttpCacheStorage implements HttpCacheStorage {

    private MemcachedClientIF client;
    private HttpCacheEntrySerializer serializer;
    private final int maxUpdateRetries;

    public MemcachedHttpCacheStorage(InetSocketAddress address) throws IOException {
        this(new MemcachedClient(address));
    }

    public MemcachedHttpCacheStorage(MemcachedClientIF cache) {
        this(cache, new CacheConfig(), new DefaultHttpCacheEntrySerializer());
    }

    public MemcachedHttpCacheStorage(MemcachedClientIF client, CacheConfig config,
            HttpCacheEntrySerializer serializer) {
        this.client = client;
        this.maxUpdateRetries = config.getMaxUpdateRetries();
        this.serializer = serializer;
    }

    public void putEntry(String url, HttpCacheEntry entry) throws IOException  {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        serializer.writeTo(entry, bos);
        client.set(url, 0, bos.toByteArray());
    }

    public HttpCacheEntry getEntry(String url) throws IOException {
        byte[] data = (byte[]) client.get(url);
        if (null == data)
            return null;
        InputStream bis = new ByteArrayInputStream(data);
        return serializer.readFrom(bis);
    }

    public void removeEntry(String url) throws IOException {
        client.delete(url);
    }

    public void updateEntry(String url, HttpCacheUpdateCallback callback)
            throws HttpCacheUpdateException, IOException {
        int numRetries = 0;
        do{

        CASValue<Object> v = client.gets(url);
        byte[] oldBytes = (v != null) ? (byte[]) v.getValue() : null;
        HttpCacheEntry existingEntry = null;
        if (oldBytes != null) {
            ByteArrayInputStream bis = new ByteArrayInputStream(oldBytes);
            existingEntry = serializer.readFrom(bis);
        }
        HttpCacheEntry updatedEntry = callback.update(existingEntry);

        if (v == null) {
            putEntry(url, updatedEntry);
            return;

        } else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            serializer.writeTo(updatedEntry, bos);
            CASResponse casResult = client.cas(url, v.getCas(), bos.toByteArray());
            if (casResult != CASResponse.OK) {
                 numRetries++;
            }
            else return;
        }

    } while(numRetries <= maxUpdateRetries);
    throw new HttpCacheUpdateException("Failed to update");
    }
}