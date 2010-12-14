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
package org.apache.http.impl.client.cache;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;

/**
 * Given a particular HttpRequest, flush any cache entries that this request
 * would invalidate.
 *
 * @since 4.1
 */
@ThreadSafe // so long as the cache implementation is thread-safe
class CacheInvalidator {

    private final HttpCacheStorage storage;
    private final CacheKeyGenerator uriExtractor;

    private final Log log = LogFactory.getLog(getClass());

    /**
     * Create a new {@link CacheInvalidator} for a given {@link HttpCache} and
     * {@link CacheKeyGenerator}.
     *
     * @param uriExtractor Provides identifiers for the keys to store cache entries
     * @param storage the cache to store items away in
     */
    public CacheInvalidator(
            final CacheKeyGenerator uriExtractor,
            final HttpCacheStorage storage) {
        this.uriExtractor = uriExtractor;
        this.storage = storage;
    }

    /**
     * Remove cache entries from the cache that are no longer fresh or
     * have been invalidated in some way.
     *
     * @param host The backend host we are talking to
     * @param req The HttpRequest to that host
     */
    public void flushInvalidatedCacheEntries(HttpHost host, HttpRequest req) throws IOException {
        if (requestShouldNotBeCached(req)) {
            log.debug("Request should not be cached");

            String theUri = uriExtractor.getURI(host, req);

            HttpCacheEntry parent = storage.getEntry(theUri);

            log.debug("parent entry: " + parent);

            if (parent != null) {
                for (String variantURI : parent.getVariantMap().values()) {
                    storage.removeEntry(variantURI);
                }
                storage.removeEntry(theUri);
            }
            URL reqURL;
            try {
                reqURL = new URL(theUri);
            } catch (MalformedURLException mue) {
                log.error("Couldn't transform request into valid URL");
                return;
            }
            Header clHdr = req.getFirstHeader("Content-Location");
            if (clHdr != null) {
                String contentLocation = clHdr.getValue();
                if (!flushAbsoluteUriFromSameHost(reqURL, contentLocation)) {
                    flushRelativeUriFromSameHost(reqURL, contentLocation);
                }
            }
            Header lHdr = req.getFirstHeader("Location");
            if (lHdr != null) {
                flushAbsoluteUriFromSameHost(reqURL, lHdr.getValue());
            }
        }
    }

    protected void flushUriIfSameHost(URL requestURL, URL targetURL) throws IOException {
        URL canonicalTarget = new URL(uriExtractor.canonicalizeUri(targetURL.toString()));
        if (canonicalTarget.getAuthority().equalsIgnoreCase(requestURL.getAuthority())) {
            storage.removeEntry(canonicalTarget.toString());
        }
    }

    protected void flushRelativeUriFromSameHost(URL reqURL, String relUri) throws IOException {
        URL relURL;
        try {
            relURL = new URL(reqURL,relUri);
        } catch (MalformedURLException e) {
            log.debug("Invalid relative URI",e);
            return;
        }
        flushUriIfSameHost(reqURL, relURL);
    }

    protected boolean flushAbsoluteUriFromSameHost(URL reqURL, String uri) throws IOException {
        URL absURL;
        try {
            absURL = new URL(uri);
        } catch (MalformedURLException mue) {
            return false;
        }
        flushUriIfSameHost(reqURL,absURL);
        return true;
    }

    protected boolean requestShouldNotBeCached(HttpRequest req) {
        String method = req.getRequestLine().getMethod();
        return notGetOrHeadRequest(method);
    }

    private boolean notGetOrHeadRequest(String method) {
        return !(HeaderConstants.GET_METHOD.equals(method) || HeaderConstants.HEAD_METHOD
                .equals(method));
    }
}
