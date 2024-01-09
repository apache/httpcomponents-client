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

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntryFactory;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.validator.ETag;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
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
    private final HttpCacheStorage storage;

    public BasicHttpCache(
            final ResourceFactory resourceFactory,
            final HttpCacheEntryFactory cacheEntryFactory,
            final HttpCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator) {
        this.resourceFactory = resourceFactory;
        this.cacheEntryFactory = cacheEntryFactory;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.storage = storage;
    }

    public BasicHttpCache(
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator) {
        this(resourceFactory, HttpCacheEntryFactory.INSTANCE, storage, cacheKeyGenerator);
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

    private void removeInternal(final String cacheKey) {
        try {
            storage.removeEntry(cacheKey);
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error removing cache entry with key {}", cacheKey);
            }
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
        if (root.hasVariants()) {
            final List<String> variantNames = CacheKeyGenerator.variantNames(root);
            final String variantKey = cacheKeyGenerator.generateVariantKey(request, variantNames);
            if (root.getVariants().contains(variantKey)) {
                final String cacheKey = variantKey + rootKey;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Get cache variant entry: {}", cacheKey);
                }
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
        final String rootKey = hit.rootKey;
        if (root != null && root.hasVariants()) {
            final List<CacheHit> variants = new ArrayList<>();
            for (final String variantKey : root.getVariants()) {
                final String variantCacheKey = variantKey + rootKey;
                final HttpCacheEntry variant = getInternal(variantCacheKey);
                if (variant != null) {
                    variants.add(new CacheHit(rootKey, variantCacheKey, variant));
                }
            }
            return variants;
        }
        return Collections.emptyList();
    }

    CacheHit store(final String rootKey, final String variantKey, final HttpCacheEntry entry) {
        if (variantKey == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Store entry in cache: {}", rootKey);
            }
            storeInternal(rootKey, entry);
            return new CacheHit(rootKey, entry);
        } else {
            final String variantCacheKey = variantKey + rootKey;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Store variant entry in cache: {}", variantCacheKey);
            }

            storeInternal(variantCacheKey, entry);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Update root entry: {}", rootKey);
            }

            updateInternal(rootKey, existing -> {
                final Set<String> variants = existing != null ? new HashSet<>(existing.getVariants()) : new HashSet<>();
                variants.add(variantKey);
                return cacheEntryFactory.createRoot(entry, variants);
            });
            return new CacheHit(rootKey, variantCacheKey, entry);
        }
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
            LOG.debug("Create cache entry: {}", rootKey);
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
                    host,
                    request,
                    originResponse,
                    content != null ? HeapResourceFactory.INSTANCE.generate(null, content.array(), 0, content.length()) : null);
            return new CacheHit(rootKey, backup);
        }
        final HttpCacheEntry entry = cacheEntryFactory.create(requestSent, responseReceived, host, request, originResponse, resource);
        final String variantKey = cacheKeyGenerator.generateVariantKey(request, entry);
        return store(rootKey,variantKey, entry);
    }

    @Override
    public CacheHit update(
            final CacheHit stale,
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update cache entry: {}", stale.getEntryKey());
        }
        final HttpCacheEntry updatedEntry = cacheEntryFactory.createUpdated(
                requestSent,
                responseReceived,
                host,
                request,
                originResponse,
                stale.entry);
        final String variantKey = cacheKeyGenerator.generateVariantKey(request, updatedEntry);
        return store(stale.rootKey, variantKey, updatedEntry);
    }

    @Override
    public CacheHit storeFromNegotiated(
            final CacheHit negotiated,
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update negotiated cache entry: {}", negotiated.getEntryKey());
        }
        final HttpCacheEntry updatedEntry = cacheEntryFactory.createUpdated(
                requestSent,
                responseReceived,
                host,
                request,
                originResponse,
               negotiated.entry);
        storeInternal(negotiated.getEntryKey(), updatedEntry);

        final String rootKey = cacheKeyGenerator.generateKey(host, request);
        final HttpCacheEntry copy = cacheEntryFactory.copy(updatedEntry);
        final String variantKey = cacheKeyGenerator.generateVariantKey(request, copy);
        return store(rootKey, variantKey, copy);
    }

    private void evictAll(final HttpCacheEntry root, final String rootKey) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Evicting root cache entry {}", rootKey);
        }
        removeInternal(rootKey);
        if (root.hasVariants()) {
            for (final String variantKey : root.getVariants()) {
                final String variantEntryKey = variantKey + rootKey;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Evicting variant cache entry {}", variantEntryKey);
                }
                removeInternal(variantEntryKey);
            }
        }
    }

    private void evict(final String rootKey) {
        final HttpCacheEntry root = getInternal(rootKey);
        if (root == null) {
            return;
        }
        evictAll(root, rootKey);
    }

    private void evict(final String rootKey, final HttpResponse response) {
        final HttpCacheEntry root = getInternal(rootKey);
        if (root == null) {
            return;
        }
        final ETag existingETag = root.getETag();
        final ETag newETag = ETag.get(response);
        if (existingETag != null && newETag != null &&
                !ETag.strongCompare(existingETag, newETag) &&
                !HttpCacheEntry.isNewer(root, response)) {
            evictAll(root, rootKey);
        }
    }

    @Override
    public void evictInvalidatedEntries(final HttpHost host, final HttpRequest request, final HttpResponse response) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Evict cache entries invalidated by exchange: {}; {} -> {}", host, new RequestLine(request), new StatusLine(response));
        }
        final int status = response.getCode();
        if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_CLIENT_ERROR &&
                !Method.isSafe(request.getMethod())) {
            final String rootKey = cacheKeyGenerator.generateKey(host, request);
            evict(rootKey);
            final URI requestUri = CacheKeyGenerator.normalize(CacheKeyGenerator.getRequestUri(host, request));
            if (requestUri != null) {
                final URI contentLocation = CacheSupport.getLocationURI(requestUri, response, HttpHeaders.CONTENT_LOCATION);
                if (contentLocation != null && CacheSupport.isSameOrigin(requestUri, contentLocation)) {
                    final String cacheKey = cacheKeyGenerator.generateKey(contentLocation);
                    evict(cacheKey, response);
                }
                final URI location = CacheSupport.getLocationURI(requestUri, response, HttpHeaders.LOCATION);
                if (location != null && CacheSupport.isSameOrigin(requestUri, location)) {
                    final String cacheKey = cacheKeyGenerator.generateKey(location);
                    evict(cacheKey, response);
                }
            }
        }
    }

}
