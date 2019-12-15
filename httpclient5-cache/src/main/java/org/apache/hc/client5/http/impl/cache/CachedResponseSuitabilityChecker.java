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
import java.util.Iterator;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Determines whether a given {@link HttpCacheEntry} is suitable to be
 * used as a response for a given {@link HttpRequest}.
 */
class CachedResponseSuitabilityChecker {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final boolean sharedCache;
    private final boolean useHeuristicCaching;
    private final float heuristicCoefficient;
    private final TimeValue heuristicDefaultLifetime;
    private final CacheValidityPolicy validityStrategy;

    CachedResponseSuitabilityChecker(final CacheValidityPolicy validityStrategy,
            final CacheConfig config) {
        super();
        this.validityStrategy = validityStrategy;
        this.sharedCache = config.isSharedCache();
        this.useHeuristicCaching = config.isHeuristicCachingEnabled();
        this.heuristicCoefficient = config.getHeuristicCoefficient();
        this.heuristicDefaultLifetime = config.getHeuristicDefaultLifetime();
    }

    CachedResponseSuitabilityChecker(final CacheConfig config) {
        this(new CacheValidityPolicy(), config);
    }

    private boolean isFreshEnough(final HttpCacheEntry entry, final HttpRequest request, final Date now) {
        if (validityStrategy.isResponseFresh(entry, now)) {
            return true;
        }
        if (useHeuristicCaching &&
                validityStrategy.isResponseHeuristicallyFresh(entry, now, heuristicCoefficient, heuristicDefaultLifetime)) {
            return true;
        }
        if (originInsistsOnFreshness(entry)) {
            return false;
        }
        final long maxStale = getMaxStale(request);
        if (maxStale == -1) {
            return false;
        }
        return (maxStale > validityStrategy.getStaleness(entry, now).toSeconds());
    }

    private boolean originInsistsOnFreshness(final HttpCacheEntry entry) {
        if (validityStrategy.mustRevalidate(entry)) {
            return true;
        }
        if (!sharedCache) {
            return false;
        }
        return validityStrategy.proxyRevalidate(entry) ||
            validityStrategy.hasCacheControlDirective(entry, "s-maxage");
    }

    private long getMaxStale(final HttpRequest request) {
        // This is a header value, we leave as-is
        long maxStale = -1;
        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (HeaderConstants.CACHE_CONTROL_MAX_STALE.equals(elt.getName())) {
                if ((elt.getValue() == null || elt.getValue().trim().isEmpty()) && maxStale == -1) {
                    maxStale = Long.MAX_VALUE;
                } else {
                    try {
                        long val = Long.parseLong(elt.getValue());
                        if (val < 0) {
                            val = 0;
                        }
                        if (maxStale == -1 || val < maxStale) {
                            maxStale = val;
                        }
                    } catch (final NumberFormatException nfe) {
                        // err on the side of preserving semantic transparency
                        maxStale = 0;
                    }
                }
            }
        }
        return maxStale;
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
     * @param now
     *            Right now in time
     * @return boolean yes/no answer
     */
    public boolean canCachedResponseBeUsed(final HttpHost host, final HttpRequest request, final HttpCacheEntry entry, final Date now) {
        if (!isFreshEnough(entry, request, now)) {
            log.debug("Cache entry is not fresh enough");
            return false;
        }

        if (isGet(request) && !validityStrategy.contentLengthHeaderMatchesActualLength(entry)) {
            log.debug("Cache entry Content-Length and header information do not match");
            return false;
        }

        if (hasUnsupportedConditionalHeaders(request)) {
            log.debug("Request contains unsupported conditional headers");
            return false;
        }

        if (!isConditional(request) && entry.getStatus() == HttpStatus.SC_NOT_MODIFIED) {
            log.debug("Unconditional request and non-modified cached response");
            return false;
        }

        if (isConditional(request) && !allConditionalsMatch(request, entry, now)) {
            log.debug("Conditional request and with mismatched conditions");
            return false;
        }

        if (hasUnsupportedCacheEntryForGet(request, entry)) {
            log.debug("HEAD response caching enabled but the cache entry does not contain a " +
                      "request method, entity or a 204 response");
            return false;
        }
        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
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
                    // in seconds
                    final int maxAge = Integer.parseInt(elt.getValue());
                    if (validityStrategy.getCurrentAge(entry, now).toSeconds() > maxAge) {
                        log.debug("Response from cache was not suitable due to max age");
                        return false;
                    }
                } catch (final NumberFormatException ex) {
                    // err conservatively
                    log.debug("Response from cache was malformed" + ex.getMessage());
                    return false;
                }
            }

            if (HeaderConstants.CACHE_CONTROL_MAX_STALE.equals(elt.getName())) {
                try {
                    // in seconds
                    final int maxStale = Integer.parseInt(elt.getValue());
                    if (validityStrategy.getFreshnessLifetime(entry).toSeconds() > maxStale) {
                        log.debug("Response from cache was not suitable due to max stale freshness");
                        return false;
                    }
                } catch (final NumberFormatException ex) {
                    // err conservatively
                    log.debug("Response from cache was malformed: " + ex.getMessage());
                    return false;
                }
            }

            if (HeaderConstants.CACHE_CONTROL_MIN_FRESH.equals(elt.getName())) {
                try {
                    // in seconds
                    final long minFresh = Long.parseLong(elt.getValue());
                    if (minFresh < 0L) {
                        return false;
                    }
                    final TimeValue age = validityStrategy.getCurrentAge(entry, now);
                    final TimeValue freshness = validityStrategy.getFreshnessLifetime(entry);
                    if (freshness.toSeconds() - age.toSeconds() < minFresh) {
                        log.debug("Response from cache was not suitable due to min fresh " +
                                "freshness requirement");
                        return false;
                    }
                } catch (final NumberFormatException ex) {
                    // err conservatively
                    log.debug("Response from cache was malformed: " + ex.getMessage());
                    return false;
                }
            }
        }

        log.debug("Response from cache was suitable");
        return true;
    }

    private boolean isGet(final HttpRequest request) {
        return request.getMethod().equals(HeaderConstants.GET_METHOD);
    }

    private boolean entryIsNotA204Response(final HttpCacheEntry entry) {
        return entry.getStatus() != HttpStatus.SC_NO_CONTENT;
    }

    private boolean cacheEntryDoesNotContainMethodAndEntity(final HttpCacheEntry entry) {
        return entry.getRequestMethod() == null && entry.getResource() == null;
    }

    private boolean hasUnsupportedCacheEntryForGet(final HttpRequest request, final HttpCacheEntry entry) {
        return isGet(request) && cacheEntryDoesNotContainMethodAndEntity(entry) && entryIsNotA204Response(entry);
    }

    /**
     * Is this request the type of conditional request we support?
     * @param request The current httpRequest being made
     * @return {@code true} if the request is supported
     */
    public boolean isConditional(final HttpRequest request) {
        return hasSupportedEtagValidator(request) || hasSupportedLastModifiedValidator(request);
    }

    /**
     * Check that conditionals that are part of this request match
     * @param request The current httpRequest being made
     * @param entry the cache entry
     * @param now right NOW in time
     * @return {@code true} if the request matches all conditionals
     */
    public boolean allConditionalsMatch(final HttpRequest request, final HttpCacheEntry entry, final Date now) {
        final boolean hasEtagValidator = hasSupportedEtagValidator(request);
        final boolean hasLastModifiedValidator = hasSupportedLastModifiedValidator(request);

        final boolean etagValidatorMatches = (hasEtagValidator) && etagValidatorMatches(request, entry);
        final boolean lastModifiedValidatorMatches = (hasLastModifiedValidator) && lastModifiedValidatorMatches(request, entry, now);

        if ((hasEtagValidator && hasLastModifiedValidator)
            && !(etagValidatorMatches && lastModifiedValidatorMatches)) {
            return false;
        } else if (hasEtagValidator && !etagValidatorMatches) {
            return false;
        }

        if (hasLastModifiedValidator && !lastModifiedValidatorMatches) {
            return false;
        }
        return true;
    }

    private boolean hasUnsupportedConditionalHeaders(final HttpRequest request) {
        return (request.getFirstHeader(HeaderConstants.IF_RANGE) != null
                || request.getFirstHeader(HeaderConstants.IF_MATCH) != null
                || hasValidDateField(request, HeaderConstants.IF_UNMODIFIED_SINCE));
    }

    private boolean hasSupportedEtagValidator(final HttpRequest request) {
        return request.containsHeader(HeaderConstants.IF_NONE_MATCH);
    }

    private boolean hasSupportedLastModifiedValidator(final HttpRequest request) {
        return hasValidDateField(request, HeaderConstants.IF_MODIFIED_SINCE);
    }

    /**
     * Check entry against If-None-Match
     * @param request The current httpRequest being made
     * @param entry the cache entry
     * @return boolean does the etag validator match
     */
    private boolean etagValidatorMatches(final HttpRequest request, final HttpCacheEntry entry) {
        final Header etagHeader = entry.getFirstHeader(HeaderConstants.ETAG);
        final String etag = (etagHeader != null) ? etagHeader.getValue() : null;
        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HeaderConstants.IF_NONE_MATCH);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            final String reqEtag = elt.toString();
            if (("*".equals(reqEtag) && etag != null) || reqEtag.equals(etag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check entry against If-Modified-Since, if If-Modified-Since is in the future it is invalid as per
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
     * @param request The current httpRequest being made
     * @param entry the cache entry
     * @param now right NOW in time
     * @return  boolean Does the last modified header match
     */
    private boolean lastModifiedValidatorMatches(final HttpRequest request, final HttpCacheEntry entry, final Date now) {
        final Date lastModified = DateUtils.parseDate(entry, HeaderConstants.LAST_MODIFIED);
        if (lastModified == null) {
            return false;
        }

        for (final Header h : request.getHeaders(HeaderConstants.IF_MODIFIED_SINCE)) {
            final Date ifModifiedSince = DateUtils.parseDate(h.getValue());
            if (ifModifiedSince != null) {
                if (ifModifiedSince.after(now) || lastModified.after(ifModifiedSince)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasValidDateField(final HttpRequest request, final String headerName) {
        for(final Header h : request.getHeaders(headerName)) {
            final Date date = DateUtils.parseDate(h.getValue());
            return date != null;
        }
        return false;
    }
}
