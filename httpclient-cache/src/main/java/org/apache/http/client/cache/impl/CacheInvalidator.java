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
package org.apache.http.client.cache.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.cache.HttpCacheOperationException;

/**
 * Given a particular HttpRequest, flush any cache entries that this request
 * would invalidate.
 *
 * @since 4.1
 */
@Immutable
public class CacheInvalidator {

    private final HttpCache<CacheEntry> cache;
    private final URIExtractor uriExtractor;

    private final Log LOG = LogFactory.getLog(CacheInvalidator.class);

    /**
     *
     * @param uriExtractor
     * @param cache
     */
    public CacheInvalidator(URIExtractor uriExtractor, HttpCache<CacheEntry> cache) {
        this.uriExtractor = uriExtractor;
        this.cache = cache;
    }

    /**
     * Remove cache entries from the cache that are no longer fresh or
     * have been invalidated in some way.
     *
     * @param host The backend host we are talking to
     * @param req The HttpRequest to that host
     */
    public void flushInvalidatedCacheEntries(HttpHost host, HttpRequest req) {
        LOG.debug("CacheInvalidator: flushInvalidatedCacheEntries, BEGIN");

        if (requestShouldNotBeCached(req)) {
            LOG.debug("CacheInvalidator: flushInvalidatedCacheEntries, Request should not be cached");

            try {
                String theUri = uriExtractor.getURI(host, req);

                CacheEntry parent = cache.getEntry(theUri);

                LOG.debug("CacheInvalidator: flushInvalidatedCacheEntries: " + parent);

                if (parent != null) {
                    for (String variantURI : parent.getVariantURIs()) {
                        cache.removeEntry(variantURI);
                    }
                    cache.removeEntry(theUri);
                }
            } catch (HttpCacheOperationException coe) {
                LOG.warn("Cache: Was unable to REMOVE an entry from the cache based on the uri provided.", coe);
                // TODO: track failed state
            }
        }
    }

    protected boolean requestShouldNotBeCached(HttpRequest req) {
        String method = req.getRequestLine().getMethod();
        return notGetOrHeadRequest(method) || containsCacheControlHeader(req)
                || containsPragmaHeader(req);
    }

    private boolean notGetOrHeadRequest(String method) {
        return !(HeaderConstants.GET_METHOD.equals(method) || HeaderConstants.HEAD_METHOD
                .equals(method));
    }

    private boolean containsPragmaHeader(HttpRequest req) {
        return req.getFirstHeader(HeaderConstants.PRAGMA) != null;
    }

    private boolean containsCacheControlHeader(HttpRequest request) {
        Header[] cacheControlHeaders = request.getHeaders(HeaderConstants.CACHE_CONTROL);

        if (cacheControlHeaders == null) {
            return false;
        }

        for (Header cacheControl : cacheControlHeaders) {
            HeaderElement[] cacheControlElements = cacheControl.getElements();
            if (cacheControlElements == null) {
                return false;
            }

            for (HeaderElement cacheControlElement : cacheControlElements) {
                if (HeaderConstants.CACHE_CONTROL_NO_CACHE.equalsIgnoreCase(cacheControlElement
                        .getName())) {
                    return true;
                }

                if (HeaderConstants.CACHE_CONTROL_NO_STORE.equalsIgnoreCase(cacheControlElement
                        .getName())) {
                    return true;
                }
            }
        }

        return false;
    }
}
