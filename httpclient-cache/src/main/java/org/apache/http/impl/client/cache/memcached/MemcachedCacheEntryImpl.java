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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.client.cache.FileResource;
import org.apache.http.impl.client.cache.HeapResource;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.message.BufferedHeader;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.CharArrayBuffer;

/**
 * Default implementation of {@link MemcachedCacheEntry}. This implementation
 * simply uses Java serialization to serialize the storage key followed by
 * the {@link HttpCacheEntry} into a byte array.
 * <p>
 * Deserialization accepts only the standard cache entry classes, including
 * {@link BasicHeader}, {@link BufferedHeader}, {@link BasicStatusLine},
 * {@link ProtocolVersion}, {@link HttpVersion}, {@link Date},
 * {@link HeapResource}, and {@link FileResource}, and their supporting classes.
 * Applications using custom entry, header, status line, protocol version, date,
 * or resource subclasses must provide their own {@link MemcachedCacheEntryFactory}
 * and {@link MemcachedCacheEntry} implementation.
 * </p>
 * <p>
 * Class filtering is a hardening measure; the cache backend must still be trusted.
 * It does not impose object graph complexity limits or control deserialization
 * performed by the memcached client's transcoder.
 * </p>
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
        final String s;
        final HttpCacheEntry entry;
        try {
            if (bytes == null) {
                throw new InvalidObjectException("Cache entry bytes must not be null");
            }
            final ObjectInputStream ois = new RestrictedObjectInputStream(new ByteArrayInputStream(bytes));
            try {
                final Object storageKey = ois.readObject();
                if (storageKey != null && !(storageKey instanceof String)) {
                    throw new InvalidObjectException("Expected a String storage key");
                }
                s = (String) storageKey;
                final Object cacheEntry = ois.readObject();
                if (cacheEntry != null && !(cacheEntry instanceof HttpCacheEntry)) {
                    throw new InvalidObjectException("Expected an HttpCacheEntry");
                }
                entry = (HttpCacheEntry) cacheEntry;
            } finally {
                ois.close();
            }
        } catch (final IOException ex) {
            throw new MemcachedSerializationException(ex);
        } catch (final ClassNotFoundException ex) {
            throw new MemcachedSerializationException(ex);
        } catch (final RuntimeException ex) {
            // JDK deserialization can report malformed data with unchecked exceptions.
            throw new MemcachedSerializationException(ex);
        }
        this.key = s;
        this.httpCacheEntry = entry;
    }

    private static class RestrictedObjectInputStream extends ObjectInputStream {

        // Exact classes needed by the standard HttpCacheEntry graph, not package prefixes.
        // @formatter:off
        private static final Set<String> ALLOWED_CLASSES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                String.class.getName(),
                Date.class.getName(),
                ArrayList.class.getName(),
                HashMap.class.getName(),
                File.class.getName(),
                HttpCacheEntry.class.getName(),
                HttpVersion.class.getName(),
                ProtocolVersion.class.getName(),
                BasicHeader.class.getName(),
                BufferedHeader.class.getName(),
                BasicStatusLine.class.getName(),
                HeaderGroup.class.getName(),
                Header[].class.getName(), // Older HttpCore versions serialized HeaderGroup.EMPTY.
                CharArrayBuffer.class.getName(),
                HeapResource.class.getName(),
                FileResource.class.getName(),
                byte[].class.getName(),
                char[].class.getName()
        )));
        // @formatter:on

        RestrictedObjectInputStream(final InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(final ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
            if (!ALLOWED_CLASSES.contains(descriptor.getName())) {
                throw new InvalidClassException(descriptor.getName(), "Class is not allowed for deserialization");
            }
            return super.resolveClass(descriptor);
        }

        @Override
        protected Class<?> resolveProxyClass(final String[] interfaces) throws IOException, ClassNotFoundException {
            throw new InvalidClassException("Dynamic proxy classes are not allowed for deserialization");
        }
    }

}
