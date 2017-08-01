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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.http.client.cache.HttpCacheEntry;

/**
 * Default implementation of {@link MemcachedCacheEntry}. This implementation
 * simply uses Java serialization to serialize the storage key followed by
 * the {@link HttpCacheEntry} into a byte array.
 */
public class MemcachedCacheEntryImpl implements MemcachedCacheEntry {

    private String key;
    private HttpCacheEntry httpCacheEntry;

    public MemcachedCacheEntryImpl(final String key, final HttpCacheEntry httpCacheEntry) {
        this.key = key;
        this.httpCacheEntry = httpCacheEntry;
    }

    public MemcachedCacheEntryImpl() {
    }

    /* (non-Javadoc)
     * @see org.apache.http.impl.client.cache.memcached.MemcachedCacheEntry#toByteArray()
     */
    @Override
    synchronized public byte[] toByteArray() {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ObjectOutputStream oos;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(this.key);
            oos.writeObject(this.httpCacheEntry);
            oos.close();
        } catch (final IOException ioe) {
            throw new MemcachedSerializationException(ioe);
        }
        return bos.toByteArray();
    }

    /* (non-Javadoc)
     * @see org.apache.http.impl.client.cache.memcached.MemcachedCacheEntry#getKey()
     */
    @Override
    public synchronized String getStorageKey() {
        return key;
    }

    /* (non-Javadoc)
     * @see org.apache.http.impl.client.cache.memcached.MemcachedCacheEntry#getHttpCacheEntry()
     */
    @Override
    public synchronized HttpCacheEntry getHttpCacheEntry() {
        return httpCacheEntry;
    }

    /* (non-Javadoc)
     * @see org.apache.http.impl.client.cache.memcached.MemcachedCacheEntry#set(byte[])
     */
    @Override
    synchronized public void set(final byte[] bytes) {
        final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        final ObjectInputStream ois;
        final String s;
        final HttpCacheEntry entry;
        try {
            ois = new ObjectInputStream(bis);
            s = (String)ois.readObject();
            entry = (HttpCacheEntry)ois.readObject();
            ois.close();
            bis.close();
        } catch (final IOException ioe) {
            throw new MemcachedSerializationException(ioe);
        } catch (final ClassNotFoundException cnfe) {
            throw new MemcachedSerializationException(cnfe);
        }
        this.key = s;
        this.httpCacheEntry = entry;
    }

}
