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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpAsyncCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a particular HTTP request / response pair, flush any cache entries
 * that this exchange would invalidate.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public class DefaultAsyncCacheInvalidator extends CacheInvalidatorBase implements HttpAsyncCacheInvalidator {

    public static final DefaultAsyncCacheInvalidator INSTANCE = new DefaultAsyncCacheInvalidator();

    private final Logger log = LoggerFactory.getLogger(getClass());

    private void removeEntry(final HttpAsyncCacheStorage storage, final String cacheKey) {
        storage.removeEntry(cacheKey, new FutureCallback<Boolean>() {

            @Override
            public void completed(final Boolean result) {
                if (log.isDebugEnabled()) {
                    if (result) {
                        log.debug("Cache entry with key " + cacheKey + " successfully flushed");
                    } else {
                        log.debug("Cache entry with key " + cacheKey + " could not be flushed");
                    }
                }
            }

            @Override
            public void failed(final Exception ex) {
                if (log.isWarnEnabled()) {
                    log.warn("Unable to flush cache entry with key " + cacheKey, ex);
                }
            }

            @Override
            public void cancelled() {
            }

        });
    }

    @Override
    public Cancellable flushCacheEntriesInvalidatedByRequest(
            final HttpHost host,
            final HttpRequest request,
            final Resolver<URI, String> cacheKeyResolver,
            final HttpAsyncCacheStorage storage,
            final FutureCallback<Boolean> callback) {
        final String s = HttpCacheSupport.getRequestUri(request, host);
        final URI uri = HttpCacheSupport.normalizeQuetly(s);
        final String cacheKey = uri != null ? cacheKeyResolver.resolve(uri) : s;
        return storage.getEntry(cacheKey, new FutureCallback<HttpCacheEntry>() {

            @Override
            public void completed(final HttpCacheEntry parentEntry) {
                if (requestShouldNotBeCached(request) || shouldInvalidateHeadCacheEntry(request, parentEntry)) {
                    if (parentEntry != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Invalidating parentEntry cache entry with key " + cacheKey);
                        }
                        for (final String variantURI : parentEntry.getVariantMap().values()) {
                            removeEntry(storage, variantURI);
                        }
                        removeEntry(storage, cacheKey);
                    }
                    if (uri != null) {
                        if (log.isWarnEnabled()) {
                            log.warn(s + " is not a valid URI");
                        }
                        final Header clHdr = request.getFirstHeader("Content-Location");
                        if (clHdr != null) {
                            final URI contentLocation = HttpCacheSupport.normalizeQuetly(clHdr.getValue());
                            if (contentLocation != null) {
                                if (!flushAbsoluteUriFromSameHost(uri, contentLocation, cacheKeyResolver, storage)) {
                                    flushRelativeUriFromSameHost(uri, contentLocation, cacheKeyResolver, storage);
                                }
                            }
                        }
                        final Header lHdr = request.getFirstHeader("Location");
                        if (lHdr != null) {
                            final URI location = HttpCacheSupport.normalizeQuetly(lHdr.getValue());
                            if (location != null) {
                                flushAbsoluteUriFromSameHost(uri, location, cacheKeyResolver, storage);
                            }
                        }
                    }
                }
                callback.completed(Boolean.TRUE);
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

    private void flushRelativeUriFromSameHost(
            final URI requestUri,
            final URI uri,
            final Resolver<URI, String> cacheKeyResolver,
            final HttpAsyncCacheStorage storage) {
        final URI resolvedUri = uri != null ? URIUtils.resolve(requestUri, uri) : null;
        if (resolvedUri != null && isSameHost(requestUri, resolvedUri)) {
            removeEntry(storage, cacheKeyResolver.resolve(resolvedUri));
        }
    }

    private boolean flushAbsoluteUriFromSameHost(
            final URI requestUri,
            final URI uri,
            final Resolver<URI, String> cacheKeyResolver,
            final HttpAsyncCacheStorage storage) {
        if (uri != null && isSameHost(requestUri, uri)) {
            removeEntry(storage, cacheKeyResolver.resolve(uri));
            return true;
        }
        return false;
    }

    @Override
    public Cancellable flushCacheEntriesInvalidatedByExchange(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse response,
            final Resolver<URI, String> cacheKeyResolver,
            final HttpAsyncCacheStorage storage,
            final FutureCallback<Boolean> callback) {
        final int status = response.getCode();
        if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
            final String s = HttpCacheSupport.getRequestUri(request, host);
            final URI requestUri = HttpCacheSupport.normalizeQuetly(s);
            if (requestUri != null) {
                final List<String> cacheKeys = new ArrayList<>(2);
                final URI contentLocation = getContentLocationURI(requestUri, response);
                if (contentLocation != null && isSameHost(requestUri, contentLocation)) {
                    cacheKeys.add(cacheKeyResolver.resolve(contentLocation));
                }
                final URI location = getLocationURI(requestUri, response);
                if (location != null && isSameHost(requestUri, location)) {
                    cacheKeys.add(cacheKeyResolver.resolve(location));
                }
                if (cacheKeys.size() == 1) {
                    final String key = cacheKeys.get(0);
                    storage.getEntry(key, new FutureCallback<HttpCacheEntry>() {

                        @Override
                        public void completed(final HttpCacheEntry entry) {
                            if (entry != null) {
                                // do not invalidate if response is strictly older than entry
                                // or if the etags match
                                if (!responseDateOlderThanEntryDate(response, entry) && responseAndEntryEtagsDiffer(response, entry)) {
                                    removeEntry(storage, key);
                                }
                            }
                            callback.completed(Boolean.TRUE);
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
                } else if (cacheKeys.size() > 1) {
                    storage.getEntries(cacheKeys, new FutureCallback<Map<String, HttpCacheEntry>>() {

                        @Override
                        public void completed(final Map<String, HttpCacheEntry> resultMap) {
                            for (final Map.Entry<String, HttpCacheEntry> resultEntry: resultMap.entrySet()) {
                                // do not invalidate if response is strictly older than entry
                                // or if the etags match
                                final String key = resultEntry.getKey();
                                final HttpCacheEntry entry = resultEntry.getValue();
                                if (!responseDateOlderThanEntryDate(response, entry) && responseAndEntryEtagsDiffer(response, entry)) {
                                    removeEntry(storage, key);
                                }
                            }
                            callback.completed(Boolean.TRUE);
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
            }
        }
        callback.completed(Boolean.TRUE);
        return Operations.nonCancellable();
    }

}
