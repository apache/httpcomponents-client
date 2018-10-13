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

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a particular HTTP request / response pair, flush any cache entries
 * that this exchange would invalidate.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public class DefaultCacheInvalidator extends CacheInvalidatorBase implements HttpCacheInvalidator {

    public static final DefaultCacheInvalidator INSTANCE = new DefaultCacheInvalidator();

    private final Logger log = LoggerFactory.getLogger(getClass());

    private HttpCacheEntry getEntry(final HttpCacheStorage storage, final String cacheKey) {
        try {
            return storage.getEntry(cacheKey);
        } catch (final ResourceIOException ex) {
            if (log.isWarnEnabled()) {
                log.warn("Unable to get cache entry with key " + cacheKey, ex);
            }
            return null;
        }
    }

    private void removeEntry(final HttpCacheStorage storage, final String cacheKey) {
        try {
            storage.removeEntry(cacheKey);
        } catch (final ResourceIOException ex) {
            if (log.isWarnEnabled()) {
                log.warn("Unable to flush cache entry with key " + cacheKey, ex);
            }
        }
    }

    @Override
    public void flushCacheEntriesInvalidatedByRequest(
            final HttpHost host,
            final HttpRequest request,
            final Resolver<URI, String> cacheKeyResolver,
            final HttpCacheStorage storage) {
        final String s = HttpCacheSupport.getRequestUri(request, host);
        final URI uri = HttpCacheSupport.normalizeQuetly(s);
        final String cacheKey = uri != null ? cacheKeyResolver.resolve(uri) : s;
        final HttpCacheEntry parent = getEntry(storage, cacheKey);

        if (requestShouldNotBeCached(request) || shouldInvalidateHeadCacheEntry(request, parent)) {
            if (parent != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Invalidating parent cache entry with key " + cacheKey);
                }
                for (final String variantURI : parent.getVariantMap().values()) {
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
    }

    private void flushRelativeUriFromSameHost(
            final URI requestUri,
            final URI uri,
            final Resolver<URI, String> cacheKeyResolver,
            final HttpCacheStorage storage) {
        final URI resolvedUri = uri != null ? URIUtils.resolve(requestUri, uri) : null;
        if (resolvedUri != null && isSameHost(requestUri, resolvedUri)) {
            removeEntry(storage, cacheKeyResolver.resolve(resolvedUri));
        }
    }

    private boolean flushAbsoluteUriFromSameHost(
            final URI requestUri,
            final URI uri,
            final Resolver<URI, String> cacheKeyResolver,
            final HttpCacheStorage storage) {
        if (uri != null && isSameHost(requestUri, uri)) {
            removeEntry(storage, cacheKeyResolver.resolve(uri));
            return true;
        }
        return false;
    }

    @Override
    public void flushCacheEntriesInvalidatedByExchange(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse response,
            final Resolver<URI, String> cacheKeyResolver,
            final HttpCacheStorage storage) {
        final int status = response.getCode();
        if (status < 200 || status > 299) {
            return;
        }
        final String s = HttpCacheSupport.getRequestUri(request, host);
        final URI uri = HttpCacheSupport.normalizeQuetly(s);
        if (uri == null) {
            return;
        }
        final URI contentLocation = getContentLocationURI(uri, response);
        if (contentLocation != null && isSameHost(uri, contentLocation)) {
            flushLocationCacheEntry(response, contentLocation, storage, cacheKeyResolver);
        }
        final URI location = getLocationURI(uri, response);
        if (location != null && isSameHost(uri, location)) {
            flushLocationCacheEntry(response, location, storage, cacheKeyResolver);
        }
    }

    private void flushLocationCacheEntry(
            final HttpResponse response,
            final URI location,
            final HttpCacheStorage storage,
            final Resolver<URI, String> cacheKeyResolver) {
        final String cacheKey = cacheKeyResolver.resolve(location);
        final HttpCacheEntry entry = getEntry(storage, cacheKey);
        if (entry != null) {
            // do not invalidate if response is strictly older than entry
            // or if the etags match

            if (!responseDateOlderThanEntryDate(response, entry) && responseAndEntryEtagsDiffer(response, entry)) {
                removeEntry(storage, cacheKey);
            }
        }
    }

}
