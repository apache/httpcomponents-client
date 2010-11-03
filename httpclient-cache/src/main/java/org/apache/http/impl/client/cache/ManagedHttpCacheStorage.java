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
package org.apache.http.impl.client.cache;

import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.Resource;

/**
 * {@link HttpCacheStorage} implementation capable of deallocating resources associated with
 * the cache entries. This cache keeps track of cache entries using {@link PhantomReference}
 * and maintains a collection of all resources that are no longer in use. The cache, however,
 * does not automatically deallocates associated resources by invoking {@link Resource#dispose()}
 * method. The consumer MUST periodically call {@link #cleanResources()} method to trigger
 * resource deallocation. The cache can be permanently shut down using {@link #shutdown()}
 * method. All resources associated with the entries used by the cache will be deallocated.
 *
 * This {@link HttpCacheStorage} implementation is intended for use with {@link FileResource}
 * and similar.
 *
 * @since 4.1
 */
@ThreadSafe
public class ManagedHttpCacheStorage implements HttpCacheStorage {

    private final CacheMap entries;
    private final ReferenceQueue<HttpCacheEntry> morque;
    private final Set<ResourceReference> resources;

    private volatile boolean shutdown;

    public ManagedHttpCacheStorage(final CacheConfig config) {
        super();
        this.entries = new CacheMap(config.getMaxCacheEntries());
        this.morque = new ReferenceQueue<HttpCacheEntry>();
        this.resources = new HashSet<ResourceReference>();
    }

    private void ensureValidState() throws IllegalStateException {
        if (this.shutdown) {
            throw new IllegalStateException("Cache has been shut down");
        }
    }

    private void keepResourceReference(final HttpCacheEntry entry) {
        Resource resource = entry.getResource();
        if (resource != null) {
            // Must deallocate the resource when the entry is no longer in used
            ResourceReference ref = new ResourceReference(entry, this.morque);
            this.resources.add(ref);
        }
    }

    public void putEntry(final String url, final HttpCacheEntry entry) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL may not be null");
        }
        if (entry == null) {
            throw new IllegalArgumentException("Cache entry may not be null");
        }
        ensureValidState();
        synchronized (this) {
            this.entries.put(url, entry);
            keepResourceReference(entry);
        }
    }

    public HttpCacheEntry getEntry(final String url) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL may not be null");
        }
        ensureValidState();
        synchronized (this) {
            return this.entries.get(url);
        }
    }

    public void removeEntry(String url) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL may not be null");
        }
        ensureValidState();
        synchronized (this) {
            // Cannot deallocate the associated resources immediately as the
            // cache entry may still be in use
            this.entries.remove(url);
        }
    }

    public void updateEntry(
            final String url,
            final HttpCacheUpdateCallback callback) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL may not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback may not be null");
        }
        ensureValidState();
        synchronized (this) {
            HttpCacheEntry existing = this.entries.get(url);
            HttpCacheEntry updated = callback.update(existing);
            this.entries.put(url, updated);
            if (existing != updated) {
                keepResourceReference(updated);
            }
        }
    }

    public void cleanResources() {
        if (this.shutdown) {
            return;
        }
        ResourceReference ref;
        while ((ref = (ResourceReference) this.morque.poll()) != null) {
            synchronized (this) {
                this.resources.remove(ref);
            }
            ref.getResource().dispose();
        }
    }

    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        synchronized (this) {
            this.entries.clear();
            for (ResourceReference ref: this.resources) {
                ref.getResource().dispose();
            }
            this.resources.clear();
            while (this.morque.poll() != null) {
            }
        }
    }

}
