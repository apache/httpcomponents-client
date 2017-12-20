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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.StandardMethods;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpAsyncCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.ByteArrayBuffer;

class BasicHttpAsyncCache implements HttpAsyncCache {

    private final CacheUpdateHandler cacheUpdateHandler;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final HttpAsyncCacheInvalidator cacheInvalidator;
    private final HttpAsyncCacheStorage storage;

    public BasicHttpAsyncCache(
            final ResourceFactory resourceFactory,
            final HttpAsyncCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator,
            final HttpAsyncCacheInvalidator cacheInvalidator) {
        this.cacheUpdateHandler = new CacheUpdateHandler(resourceFactory);
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.storage = storage;
        this.cacheInvalidator = cacheInvalidator;
    }

    public BasicHttpAsyncCache(
            final ResourceFactory resourceFactory,
            final HttpAsyncCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator) {
        this(resourceFactory, storage, cacheKeyGenerator, DefaultAsyncCacheInvalidator.INSTANCE);
    }

    public BasicHttpAsyncCache(final ResourceFactory resourceFactory, final HttpAsyncCacheStorage storage) {
        this( resourceFactory, storage, CacheKeyGenerator.INSTANCE);
    }

    @Override
    public Cancellable flushCacheEntriesFor(
            final HttpHost host, final HttpRequest request, final FutureCallback<Boolean> callback) {
        if (!StandardMethods.isSafe(request.getMethod())) {
            final String uri = cacheKeyGenerator.generateKey(host, request);
            return storage.removeEntry(uri, callback);
        } else {
            callback.completed(Boolean.TRUE);
            return Operations.nonCancellable();
        }
    }

    @Override
    public Cancellable flushInvalidatedCacheEntriesFor(
            final HttpHost host, final HttpRequest request, final HttpResponse response, final FutureCallback<Boolean> callback) {
        if (!StandardMethods.isSafe(request.getMethod())) {
            return cacheInvalidator.flushInvalidatedCacheEntries(host, request, response, cacheKeyGenerator, storage, callback);
        } else {
            callback.completed(Boolean.TRUE);
            return Operations.nonCancellable();
        }
    }

    @Override
    public Cancellable flushInvalidatedCacheEntriesFor(
            final HttpHost host, final HttpRequest request, final FutureCallback<Boolean> callback) {
        return cacheInvalidator.flushInvalidatedCacheEntries(host, request, cacheKeyGenerator, storage, callback);
    }

    Cancellable storeInCache(
            final HttpHost target, final HttpRequest request, final HttpCacheEntry entry, final FutureCallback<Boolean> callback) {
        if (entry.hasVariants()) {
            return storeVariantEntry(target, request, entry, callback);
        } else {
            return storeNonVariantEntry(target, request, entry, callback);
        }
    }

    Cancellable storeNonVariantEntry(
            final HttpHost target,
            final HttpRequest req,
            final HttpCacheEntry entry,
            final FutureCallback<Boolean> callback) {
        final String uri = cacheKeyGenerator.generateKey(target, req);
        return storage.putEntry(uri, entry, callback);
    }

    Cancellable storeVariantEntry(
            final HttpHost target,
            final HttpRequest req,
            final HttpCacheEntry entry,
            final FutureCallback<Boolean> callback) {
        final String parentCacheKey = cacheKeyGenerator.generateKey(target, req);
        final String variantKey = cacheKeyGenerator.generateVariantKey(req, entry);
        final String variantURI = cacheKeyGenerator.generateVariantURI(target, req, entry);
        return storage.putEntry(variantURI, entry, new FutureCallback<Boolean>() {

            @Override
            public void completed(final Boolean result) {
                storage.updateEntry(parentCacheKey, new HttpCacheCASOperation() {

                    @Override
                    public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                        return cacheUpdateHandler.updateParentCacheEntry(req.getRequestUri(), existing, entry, variantKey, variantURI);
                    }

                }, callback);
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
    public Cancellable reuseVariantEntryFor(
            final HttpHost target, final HttpRequest req, final Variant variant, final FutureCallback<Boolean> callback) {
        final String parentCacheKey = cacheKeyGenerator.generateKey(target, req);
        final HttpCacheEntry entry = variant.getEntry();
        final String variantKey = cacheKeyGenerator.generateVariantKey(req, entry);
        final String variantCacheKey = variant.getCacheKey();
        return storage.updateEntry(parentCacheKey, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                return cacheUpdateHandler.updateParentCacheEntry(req.getRequestUri(), existing, entry, variantKey, variantCacheKey);
            }

        }, callback);
    }

    @Override
    public Cancellable updateCacheEntry(
            final HttpHost target,
            final HttpRequest request,
            final HttpCacheEntry stale,
            final HttpResponse originResponse,
            final Date requestSent,
            final Date responseReceived,
            final FutureCallback<HttpCacheEntry> callback) {
        try {
            final HttpCacheEntry updatedEntry = cacheUpdateHandler.updateCacheEntry(
                    request.getRequestUri(),
                    stale,
                    requestSent,
                    responseReceived,
                    originResponse);
            return storeInCache(target, request, updatedEntry, new FutureCallback<Boolean>() {

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
        } catch (final ResourceIOException ex) {
            callback.failed(ex);
            return Operations.nonCancellable();
        }
    }

    @Override
    public Cancellable updateVariantCacheEntry(
            final HttpHost target,
            final HttpRequest request,
            final HttpCacheEntry stale,
            final HttpResponse originResponse,
            final Date requestSent,
            final Date responseReceived,
            final String cacheKey,
            final FutureCallback<HttpCacheEntry> callback) {
        try {
            final HttpCacheEntry updatedEntry = cacheUpdateHandler.updateCacheEntry(
                    request.getRequestUri(),
                    stale,
                    requestSent,
                    responseReceived,
                    originResponse);
            return storage.putEntry(cacheKey, updatedEntry, new FutureCallback<Boolean>() {

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
        } catch (final ResourceIOException ex) {
            callback.failed(ex);
            return Operations.nonCancellable();
        }
    }

    @Override
    public Cancellable createCacheEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final ByteArrayBuffer content,
            final Date requestSent,
            final Date responseReceived,
            final FutureCallback<HttpCacheEntry> callback) {
        try {
            final HttpCacheEntry entry = cacheUpdateHandler.createtCacheEntry(request, originResponse, content, requestSent, responseReceived);
            return storeInCache(host, request, entry, new FutureCallback<Boolean>() {

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
            callback.failed(ex);
            return Operations.nonCancellable();
        }
    }

    @Override
    public Cancellable getCacheEntry(final HttpHost host, final HttpRequest request, final FutureCallback<HttpCacheEntry> callback) {
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        complexCancellable.setDependency(storage.getEntry(cacheKey, new FutureCallback<HttpCacheEntry>() {

            @Override
            public void completed(final HttpCacheEntry root) {
                if (root != null) {
                    if (root.hasVariants()) {
                        final String variantCacheKey = root.getVariantMap().get(cacheKeyGenerator.generateVariantKey(request, root));
                        if (variantCacheKey != null) {
                            complexCancellable.setDependency(storage.getEntry(variantCacheKey, callback));
                            return;
                        }
                    }
                }
                callback.completed(root);
            }

            @Override
            public void failed(final Exception ex) {
                callback.failed(ex);
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
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        complexCancellable.setDependency(storage.getEntry(cacheKey, new FutureCallback<HttpCacheEntry>() {

            @Override
            public void completed(final HttpCacheEntry rootEntry) {
                final Map<String, Variant> variants = new HashMap<>();
                if (rootEntry != null && rootEntry.hasVariants()) {
                    complexCancellable.setDependency(storage.getEntries(
                            rootEntry.getVariantMap().keySet(),
                            new FutureCallback<Map<String, HttpCacheEntry>>() {

                                @Override
                                public void completed(final Map<String, HttpCacheEntry> resultMap) {
                                    for (final Map.Entry<String, HttpCacheEntry> resultMapEntry : resultMap.entrySet()) {
                                        final String cacheKey = resultMapEntry.getKey();
                                        final HttpCacheEntry cacheEntry = resultMapEntry.getValue();
                                        final Header etagHeader = cacheEntry.getFirstHeader(HeaderConstants.ETAG);
                                        if (etagHeader != null) {
                                            variants.put(etagHeader.getValue(), new Variant(cacheKey, cacheEntry));
                                        }
                                    }
                                    callback.completed(variants);
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    callback.failed(ex);
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
                callback.failed(ex);
            }

            @Override
            public void cancelled() {
                callback.cancelled();
            }

        }));
        return complexCancellable;
    }

}
