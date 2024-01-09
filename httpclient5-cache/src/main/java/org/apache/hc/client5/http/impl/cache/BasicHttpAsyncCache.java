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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntryFactory;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.client5.http.validator.ETag;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.ComplexCancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
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

class BasicHttpAsyncCache implements HttpAsyncCache {

    private static final Logger LOG = LoggerFactory.getLogger(BasicHttpAsyncCache.class);

    private final ResourceFactory resourceFactory;
    private final HttpCacheEntryFactory cacheEntryFactory;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final HttpAsyncCacheStorage storage;

    public BasicHttpAsyncCache(
            final ResourceFactory resourceFactory,
            final HttpCacheEntryFactory cacheEntryFactory,
            final HttpAsyncCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator) {
        this.resourceFactory = resourceFactory;
        this.cacheEntryFactory = cacheEntryFactory;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.storage = storage;
    }

    public BasicHttpAsyncCache(
            final ResourceFactory resourceFactory,
            final HttpAsyncCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator) {
        this(resourceFactory, HttpCacheEntryFactory.INSTANCE, storage, cacheKeyGenerator);
    }

    public BasicHttpAsyncCache(final ResourceFactory resourceFactory, final HttpAsyncCacheStorage storage) {
        this( resourceFactory, storage, CacheKeyGenerator.INSTANCE);
    }

    @Override
    public Cancellable match(final HttpHost host, final HttpRequest request, final FutureCallback<CacheMatch> callback) {
        final String rootKey = cacheKeyGenerator.generateKey(host, request);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get cache entry: {}", rootKey);
        }
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        complexCancellable.setDependency(storage.getEntry(rootKey, new FutureCallback<HttpCacheEntry>() {

            @Override
            public void completed(final HttpCacheEntry root) {
                if (root != null) {
                    if (root.hasVariants()) {
                        final List<String> variantNames = CacheKeyGenerator.variantNames(root);
                        final String variantKey = cacheKeyGenerator.generateVariantKey(request, variantNames);
                        if (root.getVariants().contains(variantKey)) {
                            final String cacheKey = variantKey + rootKey;
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Get cache variant entry: {}", cacheKey);
                            }
                            complexCancellable.setDependency(storage.getEntry(
                                    cacheKey,
                                    new FutureCallback<HttpCacheEntry>() {

                                        @Override
                                        public void completed(final HttpCacheEntry entry) {
                                            callback.completed(new CacheMatch(
                                                    entry != null ? new CacheHit(rootKey, cacheKey, entry) : null,
                                                    new CacheHit(rootKey, root)));
                                        }

                                        @Override
                                        public void failed(final Exception ex) {
                                            if (ex instanceof ResourceIOException) {
                                                if (LOG.isWarnEnabled()) {
                                                    LOG.warn("I/O error retrieving cache entry with key {}", cacheKey);
                                                }
                                                callback.completed(null);
                                            } else {
                                                callback.failed(ex);
                                            }
                                        }

                                        @Override
                                        public void cancelled() {
                                            callback.cancelled();
                                        }

                                    }));
                            return;
                        } else {
                            callback.completed(new CacheMatch(null, new CacheHit(rootKey, root)));
                        }
                    } else {
                        callback.completed(new CacheMatch(new CacheHit(rootKey, root), null));
                    }
                } else {
                    callback.completed(null);
                }
            }

            @Override
            public void failed(final Exception ex) {
                if (ex instanceof ResourceIOException) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("I/O error retrieving cache entry with key {}", rootKey);
                    }
                    callback.completed(null);
                } else {
                    callback.failed(ex);
                }
            }

            @Override
            public void cancelled() {
                callback.cancelled();
            }

        }));
        return complexCancellable;
    }

    @Override
    public Cancellable getVariants(
            final CacheHit hit, final FutureCallback<Collection<CacheHit>> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get variant cache entries: {}", hit.rootKey);
        }
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        final HttpCacheEntry root = hit.entry;
        final String rootKey = hit.rootKey;
        if (root != null && root.hasVariants()) {
            final List<String> variantCacheKeys = root.getVariants().stream()
                    .map(e -> e + rootKey)
                    .collect(Collectors.toList());
            complexCancellable.setDependency(storage.getEntries(
                    variantCacheKeys,
                    new FutureCallback<Map<String, HttpCacheEntry>>() {

                        @Override
                        public void completed(final Map<String, HttpCacheEntry> resultMap) {
                            final List<CacheHit> cacheHits = resultMap.entrySet().stream()
                                    .map(e -> new CacheHit(hit.rootKey, e.getKey(), e.getValue()))
                                    .collect(Collectors.toList());
                            callback.completed(cacheHits);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            if (ex instanceof ResourceIOException) {
                                if (LOG.isWarnEnabled()) {
                                    LOG.warn("I/O error retrieving cache entry with keys {}", variantCacheKeys);
                                }
                                callback.completed(Collections.emptyList());
                            } else {
                                callback.failed(ex);
                            }
                        }

                        @Override
                        public void cancelled() {
                            callback.cancelled();
                        }

                    }));
        } else {
            callback.completed(Collections.emptyList());
        }
        return complexCancellable;
    }

    Cancellable storeInternal(final String cacheKey, final HttpCacheEntry entry, final FutureCallback<Boolean> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Store entry in cache: {}", cacheKey);
        }

        return storage.putEntry(cacheKey, entry, new FutureCallback<Boolean>() {

            @Override
            public void completed(final Boolean result) {
                if (callback != null) {
                    callback.completed(result);
                }
            }

            @Override
            public void failed(final Exception ex) {
                if (ex instanceof ResourceIOException) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("I/O error storing cache entry with key {}", cacheKey);
                    }
                    if (callback != null) {
                        callback.completed(false);
                    }
                } else {
                    if (callback != null) {
                        callback.failed(ex);
                    }
                }
            }

            @Override
            public void cancelled() {
                if (callback != null) {
                    callback.cancelled();
                }
            }

        });
    }

    Cancellable updateInternal(final String cacheKey, final HttpCacheCASOperation casOperation, final FutureCallback<Boolean> callback) {
        return storage.updateEntry(cacheKey, casOperation, new FutureCallback<Boolean>() {

            @Override
            public void completed(final Boolean result) {
                if (callback != null) {
                    callback.completed(result);
                }
            }

            @Override
            public void failed(final Exception ex) {
                if (ex instanceof HttpCacheUpdateException) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Cannot update cache entry with key {}", cacheKey);
                    }
                    if (callback != null) {
                        callback.completed(false);
                    }
                } else if (ex instanceof ResourceIOException) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("I/O error updating cache entry with key {}", cacheKey);
                    }
                    if (callback != null) {
                        callback.completed(false);
                    }
                } else {
                    if (callback != null) {
                        callback.failed(ex);
                    }
                }
            }

            @Override
            public void cancelled() {
                if (callback != null) {
                    callback.cancelled();
                }
            }

        });
    }

    private void removeInternal(final String cacheKey) {
        storage.removeEntry(cacheKey, new FutureCallback<Boolean>() {

            @Override
            public void completed(final Boolean result) {
            }

            @Override
            public void failed(final Exception ex) {
                if (LOG.isWarnEnabled()) {
                    if (ex instanceof ResourceIOException) {
                        LOG.warn("I/O error removing cache entry with key {}", cacheKey);
                    } else {
                        LOG.warn("Unexpected error removing cache entry with key {}", cacheKey, ex);
                    }
                }
            }

            @Override
            public void cancelled() {
            }

        });
    }

    Cancellable store(
            final String rootKey,
            final String variantKey,
            final HttpCacheEntry entry,
            final FutureCallback<CacheHit> callback) {
        if (variantKey == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Store entry in cache: {}", rootKey);
            }
            return storeInternal(rootKey, entry, new CallbackContribution<Boolean>(callback) {

                @Override
                public void completed(final Boolean result) {
                    callback.completed(new CacheHit(rootKey, entry));
                }

            });
        } else {
            final String variantCacheKey = variantKey + rootKey;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Store variant entry in cache: {}", variantCacheKey);
            }

            return storeInternal(variantCacheKey, entry, new CallbackContribution<Boolean>(callback) {

                @Override
                public void completed(final Boolean result) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Update root entry: {}", rootKey);
                    }

                    updateInternal(rootKey,
                            existing -> {
                                final Set<String> variantMap = existing != null ? new HashSet<>(existing.getVariants()) : new HashSet<>();
                                variantMap.add(variantKey);
                                return cacheEntryFactory.createRoot(entry, variantMap);
                            },
                            new CallbackContribution<Boolean>(callback) {

                                @Override
                                public void completed(final Boolean result) {
                                    callback.completed(new CacheHit(rootKey, variantCacheKey, entry));
                                }

                            });
                }

            });
        }
    }

    @Override
    public Cancellable store(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final ByteArrayBuffer content,
            final Instant requestSent,
            final Instant responseReceived,
            final FutureCallback<CacheHit> callback) {
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
            callback.completed(new CacheHit(rootKey, backup));
            return Operations.nonCancellable();
        }
        final HttpCacheEntry entry = cacheEntryFactory.create(requestSent, responseReceived, host, request, originResponse, resource);
        final String variantKey = cacheKeyGenerator.generateVariantKey(request, entry);
        return store(rootKey,variantKey, entry, callback);
    }

    @Override
    public Cancellable update(
            final CacheHit stale,
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived,
            final FutureCallback<CacheHit> callback) {
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
        return store(stale.rootKey, variantKey, updatedEntry, callback);
    }

    @Override
    public Cancellable storeFromNegotiated(
            final CacheHit negotiated,
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived,
            final FutureCallback<CacheHit> callback) {
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

        storeInternal(negotiated.getEntryKey(), updatedEntry, null);

        final String rootKey = cacheKeyGenerator.generateKey(host, request);
        final HttpCacheEntry copy = cacheEntryFactory.copy(updatedEntry);
        final String variantKey = cacheKeyGenerator.generateVariantKey(request, copy);
        return store(rootKey, variantKey, copy, callback);
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

    private Cancellable evict(final String rootKey) {
        return storage.getEntry(rootKey, new FutureCallback<HttpCacheEntry>() {

            @Override
            public void completed(final HttpCacheEntry root) {
                if (root != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Evicting root cache entry {}", rootKey);
                    }
                    evictAll(root, rootKey);
                }
            }

            @Override
            public void failed(final Exception ex) {
            }

            @Override
            public void cancelled() {
            }

        });
    }

    private Cancellable evict(final String rootKey, final HttpResponse response) {
        return storage.getEntry(rootKey, new FutureCallback<HttpCacheEntry>() {

            @Override
            public void completed(final HttpCacheEntry root) {
                if (root != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Evicting root cache entry {}", rootKey);
                    }
                    final ETag existingETag = root.getETag();
                    final ETag newETag = ETag.get(response);
                    if (existingETag != null && newETag != null &&
                            !ETag.strongCompare(existingETag, newETag) &&
                            !HttpCacheEntry.isNewer(root, response)) {
                        evictAll(root, rootKey);
                    }
                }
            }

            @Override
            public void failed(final Exception ex) {
            }

            @Override
            public void cancelled() {
            }

        });
    }

    @Override
    public Cancellable evictInvalidatedEntries(
            final HttpHost host, final HttpRequest request, final HttpResponse response, final FutureCallback<Boolean> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries invalidated by exchange: {}; {} -> {}", host, new RequestLine(request), new StatusLine(response));
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
        callback.completed(Boolean.TRUE);
        return Operations.nonCancellable();
    }

}
