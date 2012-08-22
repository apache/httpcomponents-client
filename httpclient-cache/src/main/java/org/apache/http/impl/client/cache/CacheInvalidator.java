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
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.protocol.HTTP;

/**
 * Given a particular HttpRequest, flush any cache entries that this request
 * would invalidate.
 *
 * @since 4.1
 */
@ThreadSafe // so long as the cache implementation is thread-safe
class CacheInvalidator {

    private final HttpCacheStorage storage;
    private final CacheKeyGenerator cacheKeyGenerator;

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
        this.cacheKeyGenerator = uriExtractor;
        this.storage = storage;
    }

    /**
     * Remove cache entries from the cache that are no longer fresh or
     * have been invalidated in some way.
     *
     * @param host The backend host we are talking to
     * @param req The HttpRequest to that host
     */
    public void flushInvalidatedCacheEntries(HttpHost host, HttpRequest req)  {
        if (requestShouldNotBeCached(req)) {
            log.debug("Request should not be cached");

            String theUri = cacheKeyGenerator.getURI(host, req);

            HttpCacheEntry parent = getEntry(theUri);

            log.debug("parent entry: " + parent);

            if (parent != null) {
                for (String variantURI : parent.getVariantMap().values()) {
                    flushEntry(variantURI);
                }
                flushEntry(theUri);
            }
            URL reqURL = getAbsoluteURL(theUri);
            if (reqURL == null) {
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

    private void flushEntry(String uri) {
        try {
            storage.removeEntry(uri);
        } catch (IOException ioe) {
            log.warn("unable to flush cache entry", ioe);
        }
    }

    private HttpCacheEntry getEntry(String theUri) {
        try {
            return storage.getEntry(theUri);
        } catch (IOException ioe) {
            log.warn("could not retrieve entry from storage", ioe);
        }
        return null;
    }

    protected void flushUriIfSameHost(URL requestURL, URL targetURL) {
        URL canonicalTarget = getAbsoluteURL(cacheKeyGenerator.canonicalizeUri(targetURL.toString()));
        if (canonicalTarget == null) return;
        if (canonicalTarget.getAuthority().equalsIgnoreCase(requestURL.getAuthority())) {
            flushEntry(canonicalTarget.toString());
        }
    }

    protected void flushRelativeUriFromSameHost(URL reqURL, String relUri) {
        URL relURL = getRelativeURL(reqURL, relUri);
        if (relURL == null) return;
        flushUriIfSameHost(reqURL, relURL);
    }


    protected boolean flushAbsoluteUriFromSameHost(URL reqURL, String uri) {
        URL absURL = getAbsoluteURL(uri);
        if (absURL == null) return false;
        flushUriIfSameHost(reqURL,absURL);
        return true;
    }

    private URL getAbsoluteURL(String uri) {
        URL absURL = null;
        try {
            absURL = new URL(uri);
        } catch (MalformedURLException mue) {
            // nop
        }
        return absURL;
    }

    private URL getRelativeURL(URL reqURL, String relUri) {
        URL relURL = null;
        try {
            relURL = new URL(reqURL,relUri);
        } catch (MalformedURLException e) {
            // nop
        }
        return relURL;
    }

    protected boolean requestShouldNotBeCached(HttpRequest req) {
        String method = req.getRequestLine().getMethod();
        return notGetOrHeadRequest(method);
    }

    private boolean notGetOrHeadRequest(String method) {
        return !(HeaderConstants.GET_METHOD.equals(method) || HeaderConstants.HEAD_METHOD
                .equals(method));
    }

    /** Flushes entries that were invalidated by the given response
     * received for the given host/request pair.
     */
    public void flushInvalidatedCacheEntries(HttpHost host,
            HttpRequest request, HttpResponse response) {
        int status = response.getStatusLine().getStatusCode();
        if (status < 200 || status > 299) return;
        URL reqURL = getAbsoluteURL(cacheKeyGenerator.getURI(host, request));
        if (reqURL == null) return;
        URL canonURL = getContentLocationURL(reqURL, response);
        if (canonURL == null) return;
        String cacheKey = cacheKeyGenerator.canonicalizeUri(canonURL.toString());
        HttpCacheEntry entry = getEntry(cacheKey);
        if (entry == null) return;

        // do not invalidate if response is strictly older than entry
        // or if the etags match
        
        if (responseDateOlderThanEntryDate(response, entry)) return;
        if (!responseAndEntryEtagsDiffer(response, entry)) return;

        flushUriIfSameHost(reqURL, canonURL);
    }

    private URL getContentLocationURL(URL reqURL, HttpResponse response) {
        Header clHeader = response.getFirstHeader("Content-Location");
        if (clHeader == null) return null;
        String contentLocation = clHeader.getValue();
        URL canonURL = getAbsoluteURL(contentLocation);
        if (canonURL != null) return canonURL;
        return getRelativeURL(reqURL, contentLocation);
    }

    private boolean responseAndEntryEtagsDiffer(HttpResponse response,
            HttpCacheEntry entry) {
        Header entryEtag = entry.getFirstHeader(HeaderConstants.ETAG);
        Header responseEtag = response.getFirstHeader(HeaderConstants.ETAG);
        if (entryEtag == null || responseEtag == null) return false;
        return (!entryEtag.getValue().equals(responseEtag.getValue()));
    }

    private boolean responseDateOlderThanEntryDate(HttpResponse response,
            HttpCacheEntry entry) {
        Header entryDateHeader = entry.getFirstHeader(HTTP.DATE_HEADER);
        Header responseDateHeader = response.getFirstHeader(HTTP.DATE_HEADER);
        if (entryDateHeader == null || responseDateHeader == null) {
            /* be conservative; should probably flush */
            return false;
        }
        try {
            Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
            Date responseDate = DateUtils.parseDate(responseDateHeader.getValue());
            return responseDate.before(entryDate);
        } catch (DateParseException e) {
            /* flushing is always safe */
            return false;
        }
    }
}
