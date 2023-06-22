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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntryFactory;
import org.apache.hc.client5.http.cache.HttpCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BasicHttpCache implements HttpCache {

    private static final Logger LOG = LoggerFactory.getLogger(BasicHttpCache.class);

    private final ResourceFactory resourceFactory;
    private final HttpCacheEntryFactory cacheEntryFactory;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final HttpCacheInvalidator cacheInvalidator;
    private final HttpCacheStorage storage;

    public BasicHttpCache(
            final ResourceFactory resourceFactory,
            final HttpCacheEntryFactory cacheEntryFactory,
            final HttpCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator,
            final HttpCacheInvalidator cacheInvalidator) {
        this.resourceFactory = resourceFactory;
        this.cacheEntryFactory = cacheEntryFactory;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.storage = storage;
        this.cacheInvalidator = cacheInvalidator;
    }

    public BasicHttpCache(
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator) {
        this(resourceFactory, HttpCacheEntryFactory.INSTANCE, storage, cacheKeyGenerator, new DefaultCacheInvalidator());
    }

    public BasicHttpCache(final ResourceFactory resourceFactory, final HttpCacheStorage storage) {
        this( resourceFactory, storage, new CacheKeyGenerator());
    }

    public BasicHttpCache(final CacheConfig config) {
        this(new HeapResourceFactory(), new BasicHttpCacheStorage(config));
    }

    public BasicHttpCache() {
        this(CacheConfig.DEFAULT);
    }

    void storeInternal(final String cacheKey, final HttpCacheEntry entry) {
        try {
            storage.putEntry(cacheKey, entry);
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error storing cache entry with key {}", cacheKey);
            }
        }
    }

    void updateInternal(final String cacheKey, final HttpCacheCASOperation casOperation) {
        try {
            storage.updateEntry(cacheKey, casOperation);
        } catch (final HttpCacheUpdateException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Cannot update cache entry with key {}", cacheKey);
            }
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error updating cache entry with key {}", cacheKey);
            }
        }
    }

    HttpCacheEntry getInternal(final String cacheKey) {
        try {
            return storage.getEntry(cacheKey);
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error retrieving cache entry with key {}", cacheKey);
            }
            return null;
        }
    }

    @Override
    public CacheMatch match(final HttpHost host, final HttpRequest request) {
        final String rootKey = cacheKeyGenerator.generateKey(host, request);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get cache root entry: {}", rootKey);
        }
        final HttpCacheEntry root = getInternal(rootKey);
        if (root == null) {
            return null;
        }
        if (root.isVariantRoot()) {
            final List<String> variantNames = CacheKeyGenerator.variantNames(root);
            final String variantKey = cacheKeyGenerator.generateVariantKey(request, variantNames);
            final String cacheKey = root.getVariantMap().get(variantKey);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Get cache variant entry: {}", cacheKey);
            }
            if (cacheKey != null) {
                final HttpCacheEntry entry = getInternal(cacheKey);
                if (entry != null) {
                    return new CacheMatch(new CacheHit(rootKey, cacheKey, entry), new CacheHit(rootKey, root));
                }
            }
            return new CacheMatch(null, new CacheHit(rootKey, root));
        } else {
            return new CacheMatch(new CacheHit(rootKey, root), null);
        }
    }

    @Override
    public List<CacheHit> getVariants(final CacheHit hit) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get variant cache entries: {}", hit.rootKey);
        }
        final HttpCacheEntry root = hit.entry;
        if (root != null && root.isVariantRoot()) {
            final List<CacheHit> variants = new ArrayList<>();
            for (final String variantKey : root.getVariantMap().values()) {
                final HttpCacheEntry variant = getInternal(variantKey);
                if (variant != null) {
                    variants.add(new CacheHit(hit.rootKey, variantKey, variant));
                }
            }
            return variants;
        }
        return Collections.emptyList();
    }

    CacheHit store(
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived,
            final String rootKey,
            final HttpCacheEntry entry) {
        if (entry.hasVariants()) {
            return storeVariant(request, originResponse, requestSent, responseReceived, rootKey, entry);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Put entry in cache: {}", rootKey);
            }
            storeInternal(rootKey, entry);
            return new CacheHit(rootKey, entry);
        }
    }

    CacheHit storeVariant(
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived,
            final String rootKey,
            final HttpCacheEntry entry) {
        final List<String> variantNames = CacheKeyGenerator.variantNames(entry);
        final String variantKey = cacheKeyGenerator.generateVariantKey(request, variantNames);
        final String variantCacheKey = variantKey + rootKey;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Put variant entry in cache: {}", variantCacheKey);
        }

        storeInternal(variantCacheKey, entry);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Update root entry: {}", rootKey);
        }

        updateInternal(rootKey, existing -> {
            final Map<String, String> variantMap = existing != null ? new HashMap<>(existing.getVariantMap()) : new HashMap<>();
            variantMap.put(variantKey, variantCacheKey);
            return cacheEntryFactory.createRoot(requestSent, responseReceived, request, originResponse, variantMap);
        });
        return new CacheHit(rootKey, variantCacheKey, entry);
    }

    @Override
    public CacheHit store(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final ByteArrayBuffer content,
            final Instant requestSent,
            final Instant responseReceived) {
        final String rootKey = cacheKeyGenerator.generateKey(host, request);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Store cache entry: {}", rootKey);
        }
        final Resource resource;
        try {
            resource = content != null ? resourceFactory.generate(request.getRequestUri(), content.array(), 0, content.length()) : null;
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error creating cache entry with key {}", rootKey);
            }
            final HttpCacheEntry backup = cacheEntryFactory.create(
                    requestSent,
                    responseReceived,
                    request,
                    originResponse,
                    content != null ? HeapResourceFactory.INSTANCE.generate(null, content.array(), 0, content.length()) : null);
            return new CacheHit(rootKey, backup);
        }
        final HttpCacheEntry entry = cacheEntryFactory.create(requestSent, responseReceived, request, originResponse, resource);
        return store(request, originResponse, requestSent, responseReceived, rootKey, entry);
    }

    @Override
    public CacheHit update(
            final CacheHit stale,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived) {
        final String entryKey = stale.getEntryKey();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update cache entry: {}", entryKey);
        }
        final HttpCacheEntry updatedEntry = cacheEntryFactory.createUpdated(
                requestSent,
                responseReceived,
                originResponse,
                stale.entry);
        return store(request, originResponse, requestSent, responseReceived, entryKey, updatedEntry);
    }

    @Override
    public CacheHit update(
            final CacheHit stale,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived) {
        final String entryKey = stale.getEntryKey();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update cache entry (no root)): {}", entryKey);
        }
        final HttpCacheEntry updatedEntry = cacheEntryFactory.createUpdated(
                requestSent,
                responseReceived,
                originResponse,
                stale.entry);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Put entry in cache: {}", entryKey);
        }

        storeInternal(entryKey, updatedEntry);
        return new CacheHit(stale.rootKey, stale.variantKey, updatedEntry);
    }

    @Override
    public CacheHit storeReusing(
            final CacheHit hit,
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived) {
        final String rootKey = cacheKeyGenerator.generateKey(host, request);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Store cache entry using existing entry: {} -> {}", rootKey, hit.rootKey);
        }
        return store(request, originResponse, requestSent, responseReceived, rootKey, hit.entry);
    }

    @Override
    public void flushCacheEntriesFor(final HttpHost host, final HttpRequest request) {
        final String rootKey = cacheKeyGenerator.generateKey(host, request);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries: {}", rootKey);
        }
        try {
            storage.removeEntry(rootKey);
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error removing cache entry with key {}", rootKey);
            }
        }
    }

    @Override
    public void flushCacheEntriesInvalidatedByRequest(final HttpHost host, final HttpRequest request) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries invalidated by request: {}; {}", host, new RequestLine(request));
        }
        cacheInvalidator.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyGenerator, storage);
    }

    @Override
    public void flushCacheEntriesInvalidatedByExchange(final HttpHost host, final HttpRequest request, final HttpResponse response) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries invalidated by exchange: {}; {} -> {}", host, new RequestLine(request), new StatusLine(response));
        }
        if (!Method.isSafe(request.getMethod())) {
            cacheInvalidator.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyGenerator, storage);
        }
    }

}
