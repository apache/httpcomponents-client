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

import java.time.Instant;
import java.util.Iterator;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
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

    private static final Logger LOG = LoggerFactory.getLogger(CachedResponseSuitabilityChecker.class);

    private final boolean sharedCache;
    private final boolean useHeuristicCaching;
    private final CacheValidityPolicy validityStrategy;

    CachedResponseSuitabilityChecker(final CacheValidityPolicy validityStrategy,
                                     final CacheConfig config) {
        super();
        this.validityStrategy = validityStrategy;
        this.sharedCache = config.isSharedCache();
        this.useHeuristicCaching = config.isHeuristicCachingEnabled();
    }

    CachedResponseSuitabilityChecker(final CacheConfig config) {
        this(new CacheValidityPolicy(config), config);
    }

    private boolean isFreshEnough(final RequestCacheControl requestCacheControl,
                                  final ResponseCacheControl responseCacheControl, final HttpCacheEntry entry,
                                  final Instant now) {
        if (validityStrategy.isResponseFresh(responseCacheControl, entry, now)) {
            return true;
        }
        if (useHeuristicCaching &&
                validityStrategy.isResponseHeuristicallyFresh(entry, now)) {
            return true;
        }
        if (originInsistsOnFreshness(responseCacheControl)) {
            return false;
        }
        if (requestCacheControl.getMaxStale() == -1) {
            return false;
        }
        return (requestCacheControl.getMaxStale() > validityStrategy.getStaleness(responseCacheControl, entry, now).toSeconds());
    }

    private boolean originInsistsOnFreshness(final ResponseCacheControl responseCacheControl) {
        if (responseCacheControl.isMustRevalidate()) {
            return true;
        }
        if (!sharedCache) {
            return false;
        }
        return responseCacheControl.isProxyRevalidate() || responseCacheControl.getSharedMaxAge() >= 0;
    }

    /**
     * Determine if I can utilize a {@link HttpCacheEntry} to respond to the given
     * {@link HttpRequest}
     * @since 5.3
     */
    public boolean canCachedResponseBeUsed(final RequestCacheControl requestCacheControl,
                                           final ResponseCacheControl responseCacheControl, final HttpRequest request,
                                           final HttpCacheEntry entry, final Instant now) {

        if (isGetRequestWithHeadCacheEntry(request, entry)) {
            LOG.debug("Cache entry created by HEAD request cannot be used to serve GET request");
            return false;
        }

        if (!isFreshEnough(requestCacheControl, responseCacheControl, entry, now)) {
            LOG.debug("Cache entry is not fresh enough");
            return false;
        }

        if (hasUnsupportedConditionalHeaders(request)) {
            LOG.debug("Request contains unsupported conditional headers");
            return false;
        }

        if (!isConditional(request) && entry.getStatus() == HttpStatus.SC_NOT_MODIFIED) {
            LOG.debug("Unconditional request and non-modified cached response");
            return false;
        }

        if (isConditional(request) && !allConditionalsMatch(request, entry, now)) {
            LOG.debug("Conditional request and with mismatched conditions");
            return false;
        }

        if (hasUnsupportedCacheEntryForGet(request, entry)) {
            LOG.debug("HEAD response caching enabled but the cache entry does not contain a " +
                    "request method, entity or a 204 response");
            return false;
        }
        if (requestCacheControl.isNoCache()) {
            LOG.debug("Response contained NO CACHE directive, cache was not suitable");
            return false;
        }

        if (requestCacheControl.isNoStore()) {
            LOG.debug("Response contained NO STORE directive, cache was not suitable");
            return false;
        }

        if (requestCacheControl.getMaxAge() >= 0) {
            if (validityStrategy.getCurrentAge(entry, now).toSeconds() > requestCacheControl.getMaxAge()) {
                LOG.debug("Response from cache was not suitable due to max age");
                return false;
            }
        }

        if (requestCacheControl.getMaxStale() >= 0) {
            if (validityStrategy.getFreshnessLifetime(responseCacheControl, entry).toSeconds() > requestCacheControl.getMaxStale()) {
                LOG.debug("Response from cache was not suitable due to max stale freshness");
                return false;
            }
        }

        if (requestCacheControl.getMinFresh() >= 0) {
            if (requestCacheControl.getMinFresh() == 0) {
                return false;
            }
            final TimeValue age = validityStrategy.getCurrentAge(entry, now);
            final TimeValue freshness = validityStrategy.getFreshnessLifetime(responseCacheControl, entry);
            if (freshness.toSeconds() - age.toSeconds() < requestCacheControl.getMinFresh()) {
                LOG.debug("Response from cache was not suitable due to min fresh " +
                        "freshness requirement");
                return false;
            }
        }

        LOG.debug("Response from cache was suitable");
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
     * Determines whether the given request is a {@link HeaderConstants#GET_METHOD} request and the associated cache entry was created by a
     * {@link HeaderConstants#HEAD_METHOD} request.
     *
     * @param request The {@link HttpRequest} to check if it is a {@link HeaderConstants#GET_METHOD} request.
     * @param entry   The {@link HttpCacheEntry} to check if it was created by a {@link HeaderConstants#HEAD_METHOD} request.
     * @return true if the request is a {@link HeaderConstants#GET_METHOD} request and the cache entry was created by a
     * {@link HeaderConstants#HEAD_METHOD} request, otherwise {@code false}.
     * @since 5.3
     */
    public boolean isGetRequestWithHeadCacheEntry(final HttpRequest request, final HttpCacheEntry entry) {
        return isGet(request) && HeaderConstants.HEAD_METHOD.equalsIgnoreCase(entry.getRequestMethod());
    }


    /**
     * Check that conditionals that are part of this request match
     * @param request The current httpRequest being made
     * @param entry the cache entry
     * @param now right NOW in time
     * @return {@code true} if the request matches all conditionals
     */
    public boolean allConditionalsMatch(final HttpRequest request, final HttpCacheEntry entry, final Instant now) {
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

        return !hasLastModifiedValidator || lastModifiedValidatorMatches;
    }

    private boolean hasUnsupportedConditionalHeaders(final HttpRequest request) {
        return (request.getFirstHeader(HttpHeaders.IF_RANGE) != null
                || request.getFirstHeader(HttpHeaders.IF_MATCH) != null
                || hasValidDateField(request, HttpHeaders.IF_UNMODIFIED_SINCE));
    }

    private boolean hasSupportedEtagValidator(final HttpRequest request) {
        return request.containsHeader(HttpHeaders.IF_NONE_MATCH);
    }

    private boolean hasSupportedLastModifiedValidator(final HttpRequest request) {
        return hasValidDateField(request, HttpHeaders.IF_MODIFIED_SINCE);
    }

    /**
     * Check entry against If-None-Match
     * @param request The current httpRequest being made
     * @param entry the cache entry
     * @return boolean does the etag validator match
     */
    private boolean etagValidatorMatches(final HttpRequest request, final HttpCacheEntry entry) {
        final Header etagHeader = entry.getFirstHeader(HttpHeaders.ETAG);
        final String etag = (etagHeader != null) ? etagHeader.getValue() : null;
        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HttpHeaders.IF_NONE_MATCH);
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
     * Check entry against If-Modified-Since, if If-Modified-Since is in the future it is invalid
     * @param request The current httpRequest being made
     * @param entry the cache entry
     * @param now right NOW in time
     * @return  boolean Does the last modified header match
     */
    private boolean lastModifiedValidatorMatches(final HttpRequest request, final HttpCacheEntry entry, final Instant now) {
        final Instant lastModified = DateUtils.parseStandardDate(entry, HttpHeaders.LAST_MODIFIED);
        if (lastModified == null) {
            return false;
        }

        for (final Header h : request.getHeaders(HttpHeaders.IF_MODIFIED_SINCE)) {
            final Instant ifModifiedSince = DateUtils.parseStandardDate(h.getValue());
            if (ifModifiedSince != null) {
                if (ifModifiedSince.isAfter(now) || lastModified.isAfter(ifModifiedSince)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasValidDateField(final HttpRequest request, final String headerName) {
        for(final Header h : request.getHeaders(headerName)) {
            final Instant instant = DateUtils.parseStandardDate(h.getValue());
            return instant != null;
        }
        return false;
    }
}