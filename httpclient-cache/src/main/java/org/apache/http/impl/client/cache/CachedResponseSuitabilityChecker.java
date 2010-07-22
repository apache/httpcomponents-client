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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;

/**
 * Determines whether a given {@link HttpCacheEntry} is suitable to be
 * used as a response for a given {@link HttpRequest}.
 *
 * @since 4.1
 */
@Immutable
class CachedResponseSuitabilityChecker {

    private final Log log = LogFactory.getLog(getClass());

    private final CacheValidityPolicy validityStrategy;

    CachedResponseSuitabilityChecker(final CacheValidityPolicy validityStrategy) {
        super();
        this.validityStrategy = validityStrategy;
    }

    CachedResponseSuitabilityChecker() {
        this(new CacheValidityPolicy());
    }

    /**
     * Determine if I can utilize a {@link HttpCacheEntry} to respond to the given
     * {@link HttpRequest}
     *
     * @param host
     *            {@link HttpHost}
     * @param request
     *            {@link HttpRequest}
     * @param entry
     *            {@link HttpCacheEntry}
     * @return boolean yes/no answer
     */
    public boolean canCachedResponseBeUsed(HttpHost host, HttpRequest request, HttpCacheEntry entry) {
        if (!validityStrategy.isResponseFresh(entry)) {
            log.debug("Cache entry was not fresh enough");
            return false;
        }

        if (!validityStrategy.contentLengthHeaderMatchesActualLength(entry)) {
            log.debug("Cache entry Content-Length and header information do not match");
            return false;
        }

        if (validityStrategy.modifiedSince(entry, request)) {
            log.debug("Cache entry modified times didn't line up. Cache Entry should not be used");
            return false;
        }

        for (Header ccHdr : request.getHeaders(HeaderConstants.CACHE_CONTROL)) {
            for (HeaderElement elt : ccHdr.getElements()) {
                if (HeaderConstants.CACHE_CONTROL_NO_CACHE.equals(elt.getName())) {
                    log.debug("Response contained NO CACHE directive, cache was not suitable");
                    return false;
                }

                if (HeaderConstants.CACHE_CONTROL_NO_STORE.equals(elt.getName())) {
                    log.debug("Response contained NO SORE directive, cache was not suitable");
                    return false;
                }

                if (HeaderConstants.CACHE_CONTROL_MAX_AGE.equals(elt.getName())) {
                    try {
                        int maxage = Integer.parseInt(elt.getValue());
                        if (validityStrategy.getCurrentAgeSecs(entry) > maxage) {
                            log.debug("Response from cache was NOT suitable due to max age");
                            return false;
                        }
                    } catch (NumberFormatException ex) {
                        // err conservatively
                        log.debug("Response from cache was malformed: " + ex.getMessage());
                        return false;
                    }
                }

                if (HeaderConstants.CACHE_CONTROL_MAX_STALE.equals(elt.getName())) {
                    try {
                        int maxstale = Integer.parseInt(elt.getValue());
                        if (validityStrategy.getFreshnessLifetimeSecs(entry) > maxstale) {
                            log.debug("Response from cache was not suitable due to Max stale freshness");
                            return false;
                        }
                    } catch (NumberFormatException ex) {
                        // err conservatively
                        log.debug("Response from cache was malformed: " + ex.getMessage());
                        return false;
                    }
                }

                if (HeaderConstants.CACHE_CONTROL_MIN_FRESH.equals(elt.getName())) {
                    try {
                        int minfresh = Integer.parseInt(elt.getValue());
                        if (validityStrategy.getFreshnessLifetimeSecs(entry) < minfresh) {
                            log.debug("Response from cache was not suitable due to min fresh " +
                                    "freshness requirement");
                            return false;
                        }
                    } catch (NumberFormatException ex) {
                        // err conservatively
                        log.debug("Response from cache was malformed: " + ex.getMessage());
                        return false;
                    }
                }
            }
        }

        log.debug("Response from cache was suitable");
        return true;
    }
}
