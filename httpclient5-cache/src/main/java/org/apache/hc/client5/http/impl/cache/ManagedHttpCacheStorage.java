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

import java.io.Closeable;
import java.lang.ref.ReferenceQueue;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * <p>
 * {@link HttpCacheStorage} implementation capable of deallocating resources associated with
 * the cache entries.
 * <p>
 * This cache keeps track of cache entries using
 * {@link java.lang.ref.PhantomReference} and maintains a collection of all resources that
 * are no longer in use. The cache, however, does not automatically deallocates associated
 * resources by invoking {@link Resource#dispose()} method. The consumer MUST periodically
 * call {@link #cleanResources()} method to trigger resource deallocation. The cache can be
 * permanently shut down using {@link #shutdown()} method. All resources associated with
 * the entries used by the cache will be deallocated.
 * </p>
 * <p>
 * This {@link HttpCacheStorage} implementation is intended for use with {@link FileResource}
 * and similar.
 * </p>
 * <p>
 * Compatibility note. Prior to version 4.4 this storage implementation used to dispose of
 * all resource entries upon {@link #close()}. As of version 4.4 the {@link #close()} method
 * disposes only of those resources that have been explicitly removed from the cache with
 * {@link #removeEntry(String)} method.
 * </p>
 * <p>
 * The {@link #shutdown()} ()} method can still be used to shut down the storage and dispose of
 * all resources currently managed by it.
 * </p>
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class ManagedHttpCacheStorage implements HttpCacheStorage, Closeable {

    private final CacheMap entries;
    private final ReferenceQueue<HttpCacheEntry> morque;
    private final Set<ResourceReference> resources;
    private final AtomicBoolean active;

    public ManagedHttpCacheStorage(final CacheConfig config) {
        super();
        this.entries = new CacheMap(config.getMaxCacheEntries());
        this.morque = new ReferenceQueue<>();
        this.resources = new HashSet<>();
        this.active = new AtomicBoolean(true);
    }

    private void ensureValidState() {
        if (!isActive()) {
            throw new IllegalStateException("Cache has been shut down");
        }
    }

    private void keepResourceReference(final HttpCacheEntry entry) {
        final Resource resource = entry.getResource();
        if (resource != null) {
            // Must deallocate the resource when the entry is no longer in used
            final ResourceReference ref = new ResourceReference(entry, this.morque);
            this.resources.add(ref);
        }
    }

    @Override
    public void putEntry(final String url, final HttpCacheEntry entry) throws ResourceIOException {
        Args.notNull(url, "URL");
        Args.notNull(entry, "Cache entry");
        ensureValidState();
        synchronized (this) {
            this.entries.put(url, entry);
            keepResourceReference(entry);
        }
    }

    @Override
    public HttpCacheEntry getEntry(final String url) throws ResourceIOException {
        Args.notNull(url, "URL");
        ensureValidState();
        synchronized (this) {
            return this.entries.get(url);
        }
    }

    @Override
    public void removeEntry(final String url) throws ResourceIOException {
        Args.notNull(url, "URL");
        ensureValidState();
        synchronized (this) {
            // Cannot deallocate the associated resources immediately as the
            // cache entry may still be in use
            this.entries.remove(url);
        }
    }

    @Override
    public void updateEntry(
            final String url,
            final HttpCacheCASOperation casOperation) throws ResourceIOException {
        Args.notNull(url, "URL");
        Args.notNull(casOperation, "CAS operation");
        ensureValidState();
        synchronized (this) {
            final HttpCacheEntry existing = this.entries.get(url);
            final HttpCacheEntry updated = casOperation.execute(existing);
            this.entries.put(url, updated);
            if (existing != updated) {
                keepResourceReference(updated);
            }
        }
    }

    @Override
    public Map<String, HttpCacheEntry> getEntries(final Collection<String> keys) throws ResourceIOException {
        Args.notNull(keys, "Key");
        final Map<String, HttpCacheEntry> resultMap = new HashMap<>(keys.size());
        for (final String key: keys) {
            final HttpCacheEntry entry = getEntry(key);
            if (entry != null) {
                resultMap.put(key, entry);
            }
        }
        return resultMap;
    }

    public void cleanResources() {
        if (isActive()) {
            ResourceReference ref;
            while ((ref = (ResourceReference) this.morque.poll()) != null) {
                synchronized (this) {
                    this.resources.remove(ref);
                }
                ref.getResource().dispose();
            }
        }
    }

    public void shutdown() {
        if (compareAndSet()) {
            synchronized (this) {
                this.entries.clear();
                for (final ResourceReference ref: this.resources) {
                    ref.getResource().dispose();
                }
                this.resources.clear();
                while (this.morque.poll() != null) {
                }
            }
        }
    }

    @Override
    public void close() {
        if (compareAndSet()) {
            synchronized (this) {
                ResourceReference ref;
                while ((ref = (ResourceReference) this.morque.poll()) != null) {
                    this.resources.remove(ref);
                    ref.getResource().dispose();
                }
            }
        }
    }

    /**
     * Check if the cache is still active and has not shut down.
     *
     * @return {@code true} if the cache is active, otherwise return {@code false}.
     * @since 5.2
     */
    public boolean isActive() {
        return active.get();
    }

    private boolean compareAndSet(){
        return this.active.compareAndSet(true, false);
    }
}
