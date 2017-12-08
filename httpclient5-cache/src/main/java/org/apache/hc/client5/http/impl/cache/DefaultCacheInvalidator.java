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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Given a particular HttpRequest, flush any cache entries that this request
 * would invalidate.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class DefaultCacheInvalidator implements HttpCacheInvalidator {

    private final HttpCacheStorage storage;
    private final CacheKeyGenerator cacheKeyGenerator;

    private final Logger log = LogManager.getLogger(getClass());

    /**
     * Create a new {@link DefaultCacheInvalidator} for a given {@link HttpCache} and
     * {@link CacheKeyGenerator}.
     *
     * @param cacheKeyGenerator Provides identifiers for the keys to store cache entries
     * @param storage the cache to store items away in
     */
    public DefaultCacheInvalidator(
            final CacheKeyGenerator cacheKeyGenerator,
            final HttpCacheStorage storage) {
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.storage = storage;
    }

    private static URI parse(final String uri) {
        if (uri == null) {
            return null;
        }
        try {
            return new URI(uri);
        } catch (final URISyntaxException ex) {
            return null;
        }
    }

    /**
     * Remove cache entries from the cache that are no longer fresh or
     * have been invalidated in some way.
     *
     * @param host The backend host we are talking to
     * @param req The HttpRequest to that host
     */
    @Override
    public void flushInvalidatedCacheEntries(final HttpHost host, final HttpRequest req)  {
        final String key = cacheKeyGenerator.generateKey(host, req);
        final HttpCacheEntry parent = getEntry(key);

        if (requestShouldNotBeCached(req) || shouldInvalidateHeadCacheEntry(req, parent)) {
            if (log.isDebugEnabled()) {
                log.debug("Invalidating parent cache entry: " + parent);
            }
            if (parent != null) {
                for (final String variantURI : parent.getVariantMap().values()) {
                    flushEntry(variantURI);
                }
                flushEntry(key);
            }
            final URI uri = parse(key);
            if (uri == null) {
                log.error("Couldn't transform request into valid URI");
                return;
            }
            final Header clHdr = req.getFirstHeader("Content-Location");
            if (clHdr != null) {
                final URI contentLocation = parse(clHdr.getValue());
                if (contentLocation != null) {
                    if (!flushAbsoluteUriFromSameHost(uri, contentLocation)) {
                        flushRelativeUriFromSameHost(uri, contentLocation);
                    }
                }
            }
            final Header lHdr = req.getFirstHeader("Location");
            if (lHdr != null) {
                flushAbsoluteUriFromSameHost(uri, parse(lHdr.getValue()));
            }
        }
    }

    private boolean shouldInvalidateHeadCacheEntry(final HttpRequest req, final HttpCacheEntry parentCacheEntry) {
        return requestIsGet(req) && isAHeadCacheEntry(parentCacheEntry);
    }

    private boolean requestIsGet(final HttpRequest req) {
        return req.getMethod().equals((HeaderConstants.GET_METHOD));
    }

    private boolean isAHeadCacheEntry(final HttpCacheEntry parentCacheEntry) {
        return parentCacheEntry != null && parentCacheEntry.getRequestMethod().equals(HeaderConstants.HEAD_METHOD);
    }

    private void flushEntry(final String uri) {
        try {
            storage.removeEntry(uri);
        } catch (final IOException ioe) {
            log.warn("unable to flush cache entry", ioe);
        }
    }

    private HttpCacheEntry getEntry(final String theUri) {
        try {
            return storage.getEntry(theUri);
        } catch (final IOException ioe) {
            log.warn("could not retrieve entry from storage", ioe);
        }
        return null;
    }

    protected void flushUriIfSameHost(final URI requestURI, final URI targetURI) {
        try {
            final URI canonicalTarget = HttpCacheSupport.normalize(targetURI);
            if (canonicalTarget.isAbsolute()
                    && canonicalTarget.getAuthority().equalsIgnoreCase(requestURI.getAuthority())) {
                flushEntry(canonicalTarget.toString());
            }
        } catch (final URISyntaxException ignore) {
        }
    }

    protected void flushRelativeUriFromSameHost(final URI requestUri, final URI uri) {
        final URI resolvedUri = uri != null ? URIUtils.resolve(requestUri, uri) : null;
        if (resolvedUri != null) {
            flushUriIfSameHost(requestUri, resolvedUri);
        }
    }


    protected boolean flushAbsoluteUriFromSameHost(final URI requestUri, final URI uri) {
        if (uri != null && uri.isAbsolute()) {
            flushUriIfSameHost(requestUri, uri);
            return true;
        } else {
            return false;
        }
    }

    protected boolean requestShouldNotBeCached(final HttpRequest req) {
        final String method = req.getMethod();
        return notGetOrHeadRequest(method);
    }

    private boolean notGetOrHeadRequest(final String method) {
        return !(HeaderConstants.GET_METHOD.equals(method) || HeaderConstants.HEAD_METHOD
                .equals(method));
    }

    /** Flushes entries that were invalidated by the given response
     * received for the given host/request pair.
     */
    @Override
    public void flushInvalidatedCacheEntries(final HttpHost host, final HttpRequest request, final HttpResponse response) {
        final int status = response.getCode();
        if (status < 200 || status > 299) {
            return;
        }
        final URI uri = parse(cacheKeyGenerator.generateKey(host, request));
        if (uri == null) {
            return;
        }
        final URI contentLocation = getContentLocationURI(uri, response);
        if (contentLocation != null) {
            flushLocationCacheEntry(uri, response, contentLocation);
        }
        final URI location = getLocationURI(uri, response);
        if (location != null) {
            flushLocationCacheEntry(uri, response, location);
        }
    }

    private void flushLocationCacheEntry(final URI requestUri, final HttpResponse response, final URI location) {
        final String cacheKey = cacheKeyGenerator.generateKey(location);
        final HttpCacheEntry entry = getEntry(cacheKey);
        if (entry == null) {
            return;
        }

        // do not invalidate if response is strictly older than entry
        // or if the etags match

        if (responseDateOlderThanEntryDate(response, entry)) {
            return;
        }
        if (!responseAndEntryEtagsDiffer(response, entry)) {
            return;
        }

        flushUriIfSameHost(requestUri, location);
    }

    private static URI getLocationURI(final URI requestUri, final HttpResponse response, final String headerName) {
        final Header h = response.getFirstHeader(headerName);
        if (h == null) {
            return null;
        }
        final URI locationUri = parse(h.getValue());
        if (locationUri == null) {
            return requestUri;
        }
        if (locationUri.isAbsolute()) {
            return locationUri;
        } else {
            return URIUtils.resolve(requestUri, locationUri);
        }
    }

    private URI getContentLocationURI(final URI requestUri, final HttpResponse response) {
        return getLocationURI(requestUri, response, HttpHeaders.CONTENT_LOCATION);
    }

    private URI getLocationURI(final URI requestUri, final HttpResponse response) {
        return getLocationURI(requestUri, response, HttpHeaders.LOCATION);
    }

    private boolean responseAndEntryEtagsDiffer(final HttpResponse response,
            final HttpCacheEntry entry) {
        final Header entryEtag = entry.getFirstHeader(HeaderConstants.ETAG);
        final Header responseEtag = response.getFirstHeader(HeaderConstants.ETAG);
        if (entryEtag == null || responseEtag == null) {
            return false;
        }
        return (!entryEtag.getValue().equals(responseEtag.getValue()));
    }

    private boolean responseDateOlderThanEntryDate(final HttpResponse response,
            final HttpCacheEntry entry) {
        final Header entryDateHeader = entry.getFirstHeader(HttpHeaders.DATE);
        final Header responseDateHeader = response.getFirstHeader(HttpHeaders.DATE);
        if (entryDateHeader == null || responseDateHeader == null) {
            /* be conservative; should probably flush */
            return false;
        }
        final Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
        final Date responseDate = DateUtils.parseDate(responseDateHeader.getValue());
        if (entryDate == null || responseDate == null) {
            return false;
        }
        return responseDate.before(entryDate);
    }
}
