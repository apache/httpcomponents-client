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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.hc.client5.http.cache.HttpAsyncCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntryFactory;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.ComplexCancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
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
    private final HttpAsyncCacheInvalidator cacheInvalidator;
    private final HttpAsyncCacheStorage storage;

    public BasicHttpAsyncCache(
            final ResourceFactory resourceFactory,
            final HttpCacheEntryFactory cacheEntryFactory,
            final HttpAsyncCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator,
            final HttpAsyncCacheInvalidator cacheInvalidator) {
        this.resourceFactory = resourceFactory;
        this.cacheEntryFactory = cacheEntryFactory;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.storage = storage;
        this.cacheInvalidator = cacheInvalidator;
    }

    public BasicHttpAsyncCache(
            final ResourceFactory resourceFactory,
            final HttpAsyncCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator) {
        this(resourceFactory, HttpCacheEntryFactory.INSTANCE, storage, cacheKeyGenerator, DefaultAsyncCacheInvalidator.INSTANCE);
    }

    public BasicHttpAsyncCache(final ResourceFactory resourceFactory, final HttpAsyncCacheStorage storage) {
        this( resourceFactory, storage, CacheKeyGenerator.INSTANCE);
    }

    @Override
    public String generateKey(final HttpHost host, final HttpRequest request, final HttpCacheEntry cacheEntry) {
        if (cacheEntry == null) {
            return cacheKeyGenerator.generateKey(host, request);
        } else {
            return cacheKeyGenerator.generateKey(host, request, cacheEntry);
        }
    }

    @Override
    public Cancellable flushCacheEntriesFor(
            final HttpHost host, final HttpRequest request, final FutureCallback<Boolean> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries: {}; {}", host, new RequestLine(request));
        }
        if (!Method.isSafe(request.getMethod())) {
            final String cacheKey = cacheKeyGenerator.generateKey(host, request);
            return storage.removeEntry(cacheKey, new FutureCallback<Boolean>() {

                @Override
                public void completed(final Boolean result) {
                    callback.completed(result);
                }

                @Override
                public void failed(final Exception ex) {
                    if (ex instanceof ResourceIOException) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("I/O error removing cache entry with key {}", cacheKey);
                        }
                        callback.completed(Boolean.TRUE);
                    } else {
                        callback.failed(ex);
                    }
                }

                @Override
                public void cancelled() {
                    callback.cancelled();
                }

            });
        }
        callback.completed(Boolean.TRUE);
        return Operations.nonCancellable();
    }

    @Override
    public Cancellable flushCacheEntriesInvalidatedByRequest(
            final HttpHost host, final HttpRequest request, final FutureCallback<Boolean> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries invalidated by request: {}; {}", host, new RequestLine(request));
        }
        return cacheInvalidator.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyGenerator, storage, callback);
    }

    @Override
    public Cancellable flushCacheEntriesInvalidatedByExchange(
            final HttpHost host, final HttpRequest request, final HttpResponse response, final FutureCallback<Boolean> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries invalidated by exchange: {}; {} -> {}", host, new RequestLine(request), new StatusLine(response));
        }
        if (!Method.isSafe(request.getMethod())) {
            return cacheInvalidator.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyGenerator, storage, callback);
        }
        callback.completed(Boolean.TRUE);
        return Operations.nonCancellable();
    }

    Cancellable storeInCache(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived,
            final String cacheKey,
            final HttpCacheEntry entry,
            final FutureCallback<Boolean> callback) {
        if (entry.hasVariants()) {
            return storeVariantEntry(host, request, originResponse, requestSent, responseReceived, cacheKey, entry, callback);
        } else {
            return storeEntry(cacheKey, entry, callback);
        }
    }

    Cancellable storeEntry(
            final String cacheKey,
            final HttpCacheEntry entry,
            final FutureCallback<Boolean> callback) {
        return storage.putEntry(cacheKey, entry, new FutureCallback<Boolean>() {

            @Override
            public void completed(final Boolean result) {
                callback.completed(result);
            }

            @Override
            public void failed(final Exception ex) {
                if (ex instanceof ResourceIOException) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("I/O error storing cache entry with key {}", cacheKey);
                    }
                    callback.completed(Boolean.TRUE);
                } else {
                    callback.failed(ex);
                }
            }

            @Override
            public void cancelled() {
                callback.cancelled();
            }

        });
    }

    Cancellable storeVariantEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived,
            final String cacheKey,
            final HttpCacheEntry entry,
            final FutureCallback<Boolean> callback) {
        final String variantKey = cacheKeyGenerator.generateVariantKey(request, entry);
        final String variantCacheKey = cacheKeyGenerator.generateKey(host, request, entry);
        return storage.putEntry(variantCacheKey, entry, new FutureCallback<Boolean>() {

            @Override
            public void completed(final Boolean result) {
                storage.updateEntry(cacheKey,
                        existing -> {
                            final Map<String,String> variantMap = existing != null ? new HashMap<>(existing.getVariantMap()) : new HashMap<>();
                            variantMap.put(variantKey, variantCacheKey);
                            return cacheEntryFactory.createRoot(requestSent, responseReceived, request, originResponse, variantMap);
                        },
                        new FutureCallback<Boolean>() {

                            @Override
                            public void completed(final Boolean result) {
                                callback.completed(result);
                            }

                            @Override
                            public void failed(final Exception ex) {
                                if (ex instanceof HttpCacheUpdateException) {
                                    if (LOG.isWarnEnabled()) {
                                        LOG.warn("Cannot update cache entry with key {}", cacheKey);
                                    }
                                } else if (ex instanceof ResourceIOException) {
                                    if (LOG.isWarnEnabled()) {
                                        LOG.warn("I/O error updating cache entry with key {}", cacheKey);
                                    }
                                } else {
                                    callback.failed(ex);
                                }
                            }

                            @Override
                            public void cancelled() {
                                callback.cancelled();
                            }

                        });
            }

            @Override
            public void failed(final Exception ex) {
                if (ex instanceof ResourceIOException) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("I/O error updating cache entry with key {}", variantCacheKey);
                    }
                    callback.completed(Boolean.TRUE);
                } else {
                    callback.failed(ex);
                }
            }

            @Override
            public void cancelled() {
                callback.cancelled();
            }

        });
    }

    @Override
    public Cancellable reuseVariantEntryFor(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final HttpCacheEntry entry,
            final Instant requestSent,
            final Instant responseReceived,
            final FutureCallback<Boolean> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Re-use variant entry: {}; {} / {}", host, new RequestLine(request), entry);
        }
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        return storeVariantEntry(host, request, originResponse, requestSent, responseReceived, cacheKey, entry, callback);
    }

    @Override
    public Cancellable updateEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpCacheEntry stale,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived,
            final FutureCallback<HttpCacheEntry> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update cache entry: {}; {}", host, new RequestLine(request));
        }
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        final HttpCacheEntry updatedEntry = cacheEntryFactory.createUpdated(
                requestSent,
                responseReceived,
                originResponse,
                stale);
        return storeInCache(
                host,
                request,
                originResponse,
                requestSent,
                responseReceived,
                cacheKey,
                updatedEntry,
                new FutureCallback<Boolean>() {

                    @Override
                    public void completed(final Boolean result) {
                        callback.completed(updatedEntry);
                    }

                    @Override
                    public void failed(final Exception ex) {
                        callback.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        callback.cancelled();
                    }

                });
    }

    @Override
    public Cancellable updateVariantEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final HttpCacheEntry entry,
            final Instant requestSent,
            final Instant responseReceived,
            final FutureCallback<HttpCacheEntry> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update variant cache entry: {}; {} / {}", host, new RequestLine(request), entry);
        }
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        final HttpCacheEntry updatedEntry = cacheEntryFactory.createUpdated(
                requestSent,
                responseReceived,
                originResponse,
                entry);
        return storeEntry(cacheKey, updatedEntry, new FutureCallback<Boolean>() {

            @Override
            public void completed(final Boolean result) {
                callback.completed(updatedEntry);
            }

            @Override
            public void failed(final Exception ex) {
                callback.failed(ex);
            }

            @Override
            public void cancelled() {
                callback.cancelled();
            }

        });
    }

    @Override
    public Cancellable createEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final ByteArrayBuffer content,
            final Instant requestSent,
            final Instant responseReceived,
            final FutureCallback<HttpCacheEntry> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Create cache entry: {}; {}", host, new RequestLine(request));
        }
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        try {
            final HttpCacheEntry entry = cacheEntryFactory.create(requestSent, responseReceived, request, originResponse,
                    content != null ? resourceFactory.generate(request.getRequestUri(), content.array(), 0, content.length()) : null);
            return storeInCache(
                    host,
                    request,
                    originResponse,
                    requestSent,
                    responseReceived,
                    cacheKey,
                    entry, new FutureCallback<Boolean>() {

                        @Override
                        public void completed(final Boolean result) {
                            callback.completed(entry);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            callback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            callback.cancelled();
                        }

                    });
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error creating cache entry with key {}", cacheKey);
            }
            callback.completed(cacheEntryFactory.create(
                    requestSent,
                    responseReceived,
                    request,
                    originResponse,
                    content != null ? HeapResourceFactory.INSTANCE.generate(null, content.array(), 0, content.length()) : null));
            return Operations.nonCancellable();
        }
    }

    @Override
    public Cancellable getCacheEntry(final HttpHost host, final HttpRequest request, final FutureCallback<HttpCacheEntry> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get cache entry: {}; {}", host, new RequestLine(request));
        }
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        complexCancellable.setDependency(storage.getEntry(cacheKey, new FutureCallback<HttpCacheEntry>() {

            @Override
            public void completed(final HttpCacheEntry root) {
                if (root != null) {
                    if (root.isVariantRoot()) {
                        final String variantKey = cacheKeyGenerator.generateVariantKey(request, root);
                        final String variantCacheKey = root.getVariantMap().get(variantKey);
                        if (variantCacheKey != null) {
                            complexCancellable.setDependency(storage.getEntry(
                                    variantCacheKey,
                                    new FutureCallback<HttpCacheEntry>() {

                                        @Override
                                        public void completed(final HttpCacheEntry result) {
                                            callback.completed(result);
                                        }

                                        @Override
                                        public void failed(final Exception ex) {
                                            if (ex instanceof ResourceIOException) {
                                                if (LOG.isWarnEnabled()) {
                                                    LOG.warn("I/O error retrieving cache entry with key {}", variantCacheKey);
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
                        }
                    }
                }
                callback.completed(root);
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
        return complexCancellable;
    }

    @Override
    public Cancellable getVariantCacheEntriesWithEtags(
            final HttpHost host, final HttpRequest request, final FutureCallback<Map<String, Variant>> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get variant cache entries: {}; {}", host, new RequestLine(request));
        }
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        final Map<String, Variant> variants = new HashMap<>();
        complexCancellable.setDependency(storage.getEntry(cacheKey, new FutureCallback<HttpCacheEntry>() {

            @Override
            public void completed(final HttpCacheEntry rootEntry) {
                if (rootEntry != null && rootEntry.isVariantRoot()) {
                    final Set<String> variantCacheKeys = rootEntry.getVariantMap().keySet();
                    complexCancellable.setDependency(storage.getEntries(
                            variantCacheKeys,
                            new FutureCallback<Map<String, HttpCacheEntry>>() {

                                @Override
                                public void completed(final Map<String, HttpCacheEntry> resultMap) {
                                    for (final Map.Entry<String, HttpCacheEntry> resultMapEntry : resultMap.entrySet()) {
                                        final String cacheKey = resultMapEntry.getKey();
                                        final HttpCacheEntry cacheEntry = resultMapEntry.getValue();
                                        final Header etagHeader = cacheEntry.getFirstHeader(HttpHeaders.ETAG);
                                        if (etagHeader != null) {
                                            variants.put(etagHeader.getValue(), new Variant(cacheKey, cacheEntry));
                                        }
                                    }
                                    callback.completed(variants);
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    if (ex instanceof ResourceIOException) {
                                        if (LOG.isWarnEnabled()) {
                                            LOG.warn("I/O error retrieving cache entry with keys {}", variantCacheKeys);
                                        }
                                        callback.completed(variants);
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
                    callback.completed(variants);
                }
            }

            @Override
            public void failed(final Exception ex) {
                if (ex instanceof ResourceIOException) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("I/O error retrieving cache entry with key {}", cacheKey);
                    }
                    callback.completed(variants);
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

}
