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
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.client5.http.validator.ETag;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
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

    private final CacheValidityPolicy validityStrategy;
    private final boolean sharedCache;
    private final boolean staleifError;

    CachedResponseSuitabilityChecker(final CacheValidityPolicy validityStrategy,
                                     final CacheConfig config) {
        super();
        this.validityStrategy = validityStrategy;
        this.sharedCache = config.isSharedCache();
        this.staleifError = config.isStaleIfErrorEnabled();
    }

    CachedResponseSuitabilityChecker(final CacheConfig config) {
        this(new CacheValidityPolicy(config), config);
    }

    /**
     * Determine if I can utilize the given {@link HttpCacheEntry} to respond to the given
     * {@link HttpRequest}.
     *
     * @since 5.4
     */
    public CacheSuitability assessSuitability(final RequestCacheControl requestCacheControl,
                                              final ResponseCacheControl responseCacheControl,
                                              final HttpRequest request,
                                              final HttpCacheEntry entry,
                                              final Instant now) {
        if (!requestMethodMatch(request, entry)) {
            LOG.debug("Request method and the cache entry method do not match");
            return CacheSuitability.MISMATCH;
        }

        if (!requestUriMatch(request, entry)) {
            LOG.debug("Target request URI and the cache entry request URI do not match");
            return CacheSuitability.MISMATCH;
        }

        if (!requestHeadersMatch(request, entry)) {
            LOG.debug("Request headers nominated by the cached response do not match those of the request associated with the cache entry");
            return CacheSuitability.MISMATCH;
        }

        if (!requestHeadersMatch(request, entry)) {
            LOG.debug("Request headers nominated by the cached response do not match those of the request associated with the cache entry");
            return CacheSuitability.MISMATCH;
        }

        if (requestCacheControl.isNoCache()) {
            LOG.debug("Request contained no-cache directive; the cache entry must be re-validated");
            return CacheSuitability.REVALIDATION_REQUIRED;
        }

        if (isResponseNoCache(responseCacheControl, entry)) {
            LOG.debug("Response contained no-cache directive; the cache entry must be re-validated");
            return CacheSuitability.REVALIDATION_REQUIRED;
        }

        if (hasUnsupportedConditionalHeaders(request)) {
            LOG.debug("Response from cache is not suitable due to the request containing unsupported conditional headers");
            return CacheSuitability.REVALIDATION_REQUIRED;
        }

        if (!isConditional(request) && entry.getStatus() == HttpStatus.SC_NOT_MODIFIED) {
            LOG.debug("Unconditional request and non-modified cached response");
            return CacheSuitability.REVALIDATION_REQUIRED;
        }

        if (!allConditionalsMatch(request, entry, now)) {
            LOG.debug("Response from cache is not suitable due to the conditional request and with mismatched conditions");
            return CacheSuitability.REVALIDATION_REQUIRED;
        }

        final TimeValue currentAge = validityStrategy.getCurrentAge(entry, now);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Cache entry current age: {}", currentAge);
        }
        final TimeValue freshnessLifetime = validityStrategy.getFreshnessLifetime(responseCacheControl, entry);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Cache entry freshness lifetime: {}", freshnessLifetime);
        }

        final boolean fresh = currentAge.compareTo(freshnessLifetime) < 0;

        if (!fresh && responseCacheControl.isMustRevalidate()) {
            LOG.debug("Response from cache is not suitable due to the response must-revalidate requirement");
            return CacheSuitability.REVALIDATION_REQUIRED;
        }

        if (!fresh && sharedCache && responseCacheControl.isProxyRevalidate()) {
            LOG.debug("Response from cache is not suitable due to the response proxy-revalidate requirement");
            return CacheSuitability.REVALIDATION_REQUIRED;
        }

        if (fresh && requestCacheControl.getMaxAge() >= 0) {
            if (currentAge.toSeconds() > requestCacheControl.getMaxAge() && requestCacheControl.getMaxStale() == -1) {
                LOG.debug("Response from cache is not suitable due to the request max-age requirement");
                return CacheSuitability.REVALIDATION_REQUIRED;
            }
        }

        if (fresh && requestCacheControl.getMinFresh() >= 0) {
            if (requestCacheControl.getMinFresh() == 0 ||
                    freshnessLifetime.toSeconds() - currentAge.toSeconds() < requestCacheControl.getMinFresh()) {
                LOG.debug("Response from cache is not suitable due to the request min-fresh requirement");
                return CacheSuitability.REVALIDATION_REQUIRED;
            }
        }

        if (requestCacheControl.getMaxStale() >= 0) {
            final long stale = currentAge.compareTo(freshnessLifetime) > 0 ? currentAge.toSeconds() - freshnessLifetime.toSeconds() : 0;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cache entry staleness: {} SECONDS", stale);
            }
            if (stale >= requestCacheControl.getMaxStale()) {
                LOG.debug("Response from cache is not suitable due to the request max-stale requirement");
                return CacheSuitability.REVALIDATION_REQUIRED;
            } else {
                LOG.debug("The cache entry is fresh enough");
                return CacheSuitability.FRESH_ENOUGH;
            }
        }

        if (fresh) {
            LOG.debug("The cache entry is fresh");
            return CacheSuitability.FRESH;
        } else {
            if (responseCacheControl.getStaleWhileRevalidate() > 0) {
                final long stale = currentAge.compareTo(freshnessLifetime) > 0 ? currentAge.toSeconds() - freshnessLifetime.toSeconds() : 0;
                if (stale < responseCacheControl.getStaleWhileRevalidate()) {
                    LOG.debug("The cache entry is stale but suitable while being revalidated");
                    return CacheSuitability.STALE_WHILE_REVALIDATED;
                }
            }
            LOG.debug("The cache entry is stale");
            return CacheSuitability.STALE;
        }
    }

    boolean requestMethodMatch(final HttpRequest request, final HttpCacheEntry entry) {
        return request.getMethod().equalsIgnoreCase(entry.getRequestMethod()) ||
                (Method.HEAD.isSame(request.getMethod()) && Method.GET.isSame(entry.getRequestMethod()));
    }

    boolean requestUriMatch(final HttpRequest request, final HttpCacheEntry entry) {
        try {
            final URI requestURI = CacheKeyGenerator.normalize(request.getUri());
            final URI cacheURI = new URI(entry.getRequestURI());
            if (requestURI.isAbsolute()) {
                return Objects.equals(requestURI, cacheURI);
            } else {
                return Objects.equals(requestURI.getPath(), cacheURI.getPath()) && Objects.equals(requestURI.getQuery(), cacheURI.getQuery());
            }
        } catch (final URISyntaxException ex) {
            return false;
        }
    }

    boolean requestHeadersMatch(final HttpRequest request, final HttpCacheEntry entry) {
        final Iterator<Header> it = entry.headerIterator(HttpHeaders.VARY);
        if (it.hasNext()) {
            final Set<String> headerNames = new HashSet<>();
            while (it.hasNext()) {
                final Header header = it.next();
                MessageSupport.parseTokens(header, e -> {
                    headerNames.add(e.toLowerCase(Locale.ROOT));
                });
            }
            final List<String> tokensInRequest = new ArrayList<>();
            final List<String> tokensInCache = new ArrayList<>();
            for (final String headerName: headerNames) {
                if (headerName.equalsIgnoreCase("*")) {
                    return false;
                }
                CacheKeyGenerator.normalizeElements(request, headerName, tokensInRequest::add);
                CacheKeyGenerator.normalizeElements(entry.requestHeaders(), headerName, tokensInCache::add);
                if (!Objects.equals(tokensInRequest, tokensInCache)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Determines if the given {@link HttpCacheEntry} requires revalidation based on the presence of the {@code no-cache} directive
     * in the Cache-Control header.
     * <p>
     * The method returns true in the following cases:
     * - If the {@code no-cache} directive is present without any field names (unqualified).
     * - If the {@code no-cache} directive is present with field names, and at least one of these field names is present
     * in the headers of the {@link HttpCacheEntry}.
     * <p>
     * If the {@code no-cache} directive is not present in the Cache-Control header, the method returns {@code false}.
     */
    boolean isResponseNoCache(final ResponseCacheControl responseCacheControl, final HttpCacheEntry entry) {
        // If no-cache directive is present and has no field names
        if (responseCacheControl.isNoCache()) {
            final Set<String> noCacheFields = responseCacheControl.getNoCacheFields();
            if (noCacheFields.isEmpty()) {
                LOG.debug("Revalidation required due to unqualified no-cache directive");
                return true;
            }
            for (final String field : noCacheFields) {
                if (entry.containsHeader(field)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Revalidation required due to no-cache directive with field {}", field);
                    }
                    return true;
                }
            }
        }
        return false;
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
    public boolean allConditionalsMatch(final HttpRequest request, final HttpCacheEntry entry, final Instant now) {
        final boolean hasEtagValidator = hasSupportedEtagValidator(request);
        final boolean hasLastModifiedValidator = hasSupportedLastModifiedValidator(request);

        if (!hasEtagValidator && !hasLastModifiedValidator) {
            return true;
        }

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

    boolean hasUnsupportedConditionalHeaders(final HttpRequest request) {
        return (request.containsHeader(HttpHeaders.IF_RANGE)
                || request.containsHeader(HttpHeaders.IF_MATCH)
                || request.containsHeader(HttpHeaders.IF_UNMODIFIED_SINCE));
    }

    boolean hasSupportedEtagValidator(final HttpRequest request) {
        return request.containsHeader(HttpHeaders.IF_NONE_MATCH);
    }

    boolean hasSupportedLastModifiedValidator(final HttpRequest request) {
        return request.containsHeader(HttpHeaders.IF_MODIFIED_SINCE);
    }

    /**
     * Check entry against If-None-Match
     * @param request The current httpRequest being made
     * @param entry the cache entry
     * @return boolean does the etag validator match
     */
    boolean etagValidatorMatches(final HttpRequest request, final HttpCacheEntry entry) {
        final ETag etag = entry.getETag();
        if (etag == null) {
            return false;
        }
        final Iterator<String> it = MessageSupport.iterateTokens(request, HttpHeaders.IF_NONE_MATCH);
        while (it.hasNext()) {
            final String token = it.next();
            if ("*".equals(token) || ETag.weakCompare(etag, ETag.parse(token))) {
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
    boolean lastModifiedValidatorMatches(final HttpRequest request, final HttpCacheEntry entry, final Instant now) {
        final Instant lastModified = entry.getLastModified();
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

    public boolean isSuitableIfError(final RequestCacheControl requestCacheControl,
                                     final ResponseCacheControl responseCacheControl,
                                     final HttpCacheEntry entry,
                                     final Instant now) {
        // Explicit cache control
        if (requestCacheControl.getStaleIfError() > 0 || responseCacheControl.getStaleIfError() > 0) {
            final TimeValue currentAge = validityStrategy.getCurrentAge(entry, now);
            final TimeValue freshnessLifetime = validityStrategy.getFreshnessLifetime(responseCacheControl, entry);
            if (requestCacheControl.getMinFresh() > 0 && requestCacheControl.getMinFresh() < freshnessLifetime.toSeconds()) {
                return false;
            }
            final long stale = currentAge.compareTo(freshnessLifetime) > 0 ? currentAge.toSeconds() - freshnessLifetime.toSeconds() : 0;
            if (requestCacheControl.getStaleIfError() > 0 && stale < requestCacheControl.getStaleIfError()) {
                return true;
            }
            if (responseCacheControl.getStaleIfError() > 0 && stale < responseCacheControl.getStaleIfError()) {
                return true;
            }
        }
        // Global override
        if (staleifError && requestCacheControl.getStaleIfError() == -1 && responseCacheControl.getStaleIfError() == -1) {
            return true;
        }
        return false;
    }

}