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

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;

/**
 * Determines whether a given {@link HttpCacheEntry} is suitable to be
 * used as a response for a given {@link HttpRequest}.
 *
 * @since 4.1
 */
@Immutable
class CachedResponseSuitabilityChecker {

    private final Log log = LogFactory.getLog(getClass());

    private final boolean sharedCache;
    private final boolean useHeuristicCaching;
    private final float heuristicCoefficient;
    private final long heuristicDefaultLifetime;
    private final CacheValidityPolicy validityStrategy;

    CachedResponseSuitabilityChecker(final CacheValidityPolicy validityStrategy,
            CacheConfig config) {
        super();
        this.validityStrategy = validityStrategy;
        this.sharedCache = config.isSharedCache();
        this.useHeuristicCaching = config.isHeuristicCachingEnabled();
        this.heuristicCoefficient = config.getHeuristicCoefficient();
        this.heuristicDefaultLifetime = config.getHeuristicDefaultLifetime();
    }

    CachedResponseSuitabilityChecker(CacheConfig config) {
        this(new CacheValidityPolicy(), config);
    }

    private boolean isFreshEnough(HttpCacheEntry entry, HttpRequest request, Date now) {
        if (validityStrategy.isResponseFresh(entry, now)) return true;
        if (useHeuristicCaching &&
                validityStrategy.isResponseHeuristicallyFresh(entry, now, heuristicCoefficient, heuristicDefaultLifetime))
            return true;
        if (originInsistsOnFreshness(entry)) return false;
        long maxstale = getMaxStale(request);
        if (maxstale == -1) return false;
        return (maxstale > validityStrategy.getStalenessSecs(entry, now));
    }

    private boolean originInsistsOnFreshness(HttpCacheEntry entry) {
        if (validityStrategy.mustRevalidate(entry)) return true;
        if (!sharedCache) return false;
        return validityStrategy.proxyRevalidate(entry) ||
            validityStrategy.hasCacheControlDirective(entry, "s-maxage");
    }

    private long getMaxStale(HttpRequest request) {
        long maxstale = -1;
        for(Header h : request.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if ("max-stale".equals(elt.getName())) {
                    if ((elt.getValue() == null || "".equals(elt.getValue().trim()))
                            && maxstale == -1) {
                        maxstale = Long.MAX_VALUE;
                    } else {
                        try {
                            long val = Long.parseLong(elt.getValue());
                            if (val < 0) val = 0;
                            if (maxstale == -1 || val < maxstale) {
                                maxstale = val;
                            }
                        } catch (NumberFormatException nfe) {
                            // err on the side of preserving semantic transparency
                            maxstale = 0;
                        }
                    }
                }
            }
        }
        return maxstale;
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
    public boolean canCachedResponseBeUsed(HttpHost host, HttpRequest request, HttpCacheEntry entry, Date now) {
        if (!isFreshEnough(entry, request, now)) {
            log.debug("Cache entry was not fresh enough");
            return false;
        }

        if (!validityStrategy.contentLengthHeaderMatchesActualLength(entry)) {
            log.debug("Cache entry Content-Length and header information do not match");
            return false;
        }

        if (hasUnsupportedConditionalHeaders(request)) {
            log.debug("Request contained conditional headers we don't handle");
            return false;
        }

        if (isConditional(request) && !allConditionalsMatch(request, entry, now)) {
            return false;
        }

        for (Header ccHdr : request.getHeaders(HeaderConstants.CACHE_CONTROL)) {
            for (HeaderElement elt : ccHdr.getElements()) {
                if (HeaderConstants.CACHE_CONTROL_NO_CACHE.equals(elt.getName())) {
                    log.debug("Response contained NO CACHE directive, cache was not suitable");
                    return false;
                }

                if (HeaderConstants.CACHE_CONTROL_NO_STORE.equals(elt.getName())) {
                    log.debug("Response contained NO STORE directive, cache was not suitable");
                    return false;
                }

                if (HeaderConstants.CACHE_CONTROL_MAX_AGE.equals(elt.getName())) {
                    try {
                        int maxage = Integer.parseInt(elt.getValue());
                        if (validityStrategy.getCurrentAgeSecs(entry, now) > maxage) {
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
                        long minfresh = Long.parseLong(elt.getValue());
                        if (minfresh < 0L) return false;
                        long age = validityStrategy.getCurrentAgeSecs(entry, now);
                        long freshness = validityStrategy.getFreshnessLifetimeSecs(entry);
                        if (freshness - age < minfresh) {
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

    /**
     * Is this request the type of conditional request we support?
     * @param request
     * @return {@code true} if the request is supported
     */
    public boolean isConditional(HttpRequest request) {
        return hasSupportedEtagVadlidator(request) || hasSupportedLastModifiedValidator(request);
    }

    /**
     * Check that conditionals that are part of this request match
     * @param request
     * @param entry
     * @param now
     * @return {@code true} if the request matches all conditionals
     */
    public boolean allConditionalsMatch(HttpRequest request, HttpCacheEntry entry, Date now) {
        boolean hasEtagValidator = hasSupportedEtagVadlidator(request);
        boolean hasLastModifiedValidator = hasSupportedLastModifiedValidator(request);

        boolean etagValidatorMatches = (hasEtagValidator) ? etagValidtorMatches(request, entry) : false;
        boolean lastModifiedValidatorMatches = (hasLastModifiedValidator) ? lastModifiedValidatorMatches(request, entry, now) : false;

        if ((hasEtagValidator && hasLastModifiedValidator)
            && !(etagValidatorMatches && lastModifiedValidatorMatches)) {
            return false;
        } else if (hasEtagValidator && !etagValidatorMatches) {
            return false;
        } if (hasLastModifiedValidator && !lastModifiedValidatorMatches) {
            return false;
        }
        return true;
    }

    private boolean hasUnsupportedConditionalHeaders(HttpRequest request) {
        return (request.getFirstHeader("If-Range") != null
                || request.getFirstHeader("If-Match") != null
                || hasValidDateField(request, "If-Unmodified-Since"));
    }

    private boolean hasSupportedEtagVadlidator(HttpRequest request) {
        return request.containsHeader(HeaderConstants.IF_NONE_MATCH);
    }

    private boolean hasSupportedLastModifiedValidator(HttpRequest request) {
        return hasValidDateField(request, HeaderConstants.IF_MODIFIED_SINCE);
    }

    /**
     * Check entry against If-None-Match
     * @param request
     * @param entry
     * @return
     */
    private boolean etagValidtorMatches(HttpRequest request, HttpCacheEntry entry) {
        Header etagHeader = entry.getFirstHeader(HeaderConstants.ETAG);
        String etag = (etagHeader != null) ? etagHeader.getValue() : null;
        Header[] ifNoneMatch = request.getHeaders(HeaderConstants.IF_NONE_MATCH);
        if (ifNoneMatch != null) {
            for (Header h : ifNoneMatch) {
                for (HeaderElement elt : h.getElements()) {
                    String reqEtag = elt.toString();
                    if (("*".equals(reqEtag) && etag != null)
                            || reqEtag.equals(etag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check entry against If-Modified-Since, if If-Modified-Since is in the future it is invalid as per
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
     * @param request
     * @param entry
     * @param now
     * @return
     */
    private boolean lastModifiedValidatorMatches(HttpRequest request, HttpCacheEntry entry, Date now) {
        Header lastModifiedHeader = entry.getFirstHeader(HeaderConstants.LAST_MODIFIED);
        Date lastModified = null;
        try {
            if(lastModifiedHeader != null) {
                lastModified = DateUtils.parseDate(lastModifiedHeader.getValue());
            }
        } catch (DateParseException dpe) {
            // nop
        }

        if (lastModified == null) {
            return false;
        }

        for (Header h : request.getHeaders(HeaderConstants.IF_MODIFIED_SINCE)) {
            try {
                Date ifModifiedSince = DateUtils.parseDate(h.getValue());
                if (ifModifiedSince.after(now) || lastModified.after(ifModifiedSince)) {
                    return false;
                }
            } catch (DateParseException dpe) {
                // nop
            }
        }
        return true;
    }

    private boolean hasValidDateField(HttpRequest request, String headerName) {
        for(Header h : request.getHeaders(headerName)) {
            try {
                DateUtils.parseDate(h.getValue());
                return true;
            } catch (DateParseException dpe) {
                // ignore malformed dates
            }
        }
        return false;
    }
}
