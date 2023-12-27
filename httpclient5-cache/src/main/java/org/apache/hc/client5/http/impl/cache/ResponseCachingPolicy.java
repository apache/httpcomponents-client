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

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;

import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.MessageSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResponseCachingPolicy {

    /**
     * The default freshness duration for a cached object, in seconds.
     *
     * <p>This constant is used to set the default value for the freshness lifetime of a cached object.
     * When a new object is added to the cache, it will be assigned this duration if no other duration
     * is specified.</p>
     *
     * <p>By default, this value is set to 300 seconds (5 minutes). Applications can customize this
     * value as needed.</p>
     */
     private static final Duration DEFAULT_FRESHNESS_DURATION = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(ResponseCachingPolicy.class);

    private final boolean sharedCache;
    private final boolean neverCache1_0ResponsesWithQueryString;
    private final boolean neverCache1_1ResponsesWithQueryString;

    /**
     * Constructs a new ResponseCachingPolicy with the specified cache policy settings and stale-if-error support.
     *
     * @param sharedCache                           whether to behave as a shared cache (true) or a
     *                                              non-shared/private cache (false)
     * @param neverCache1_0ResponsesWithQueryString {@code true} to never cache HTTP 1.0 responses with a query string,
     *                                              {@code false} to cache if explicit cache headers are found.
     * @param neverCache1_1ResponsesWithQueryString {@code true} to never cache HTTP 1.1 responses with a query string,
     *                                              {@code false} to cache if explicit cache headers are found.
     * @since 5.4
     */
    public ResponseCachingPolicy(
             final boolean sharedCache,
             final boolean neverCache1_0ResponsesWithQueryString,
             final boolean neverCache1_1ResponsesWithQueryString) {
        this.sharedCache = sharedCache;
        this.neverCache1_0ResponsesWithQueryString = neverCache1_0ResponsesWithQueryString;
        this.neverCache1_1ResponsesWithQueryString = neverCache1_1ResponsesWithQueryString;
    }

    /**
     * Determine if the {@link HttpResponse} gotten from the origin is a
     * cacheable response.
     *
     * @return {@code true} if response is cacheable
     */
    public boolean isResponseCacheable(final ResponseCacheControl cacheControl, final HttpRequest request, final HttpResponse response) {
        final ProtocolVersion version = request.getVersion() != null ? request.getVersion() : HttpVersion.DEFAULT;
        if (version.compareToVersion(HttpVersion.HTTP_1_1) > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Protocol version {} is non-cacheable", version);
            }
            return false;
        }

        // Presently only GET and HEAD methods are supported
        final String httpMethod = request.getMethod();
        if (!Method.GET.isSame(httpMethod) && !Method.HEAD.isSame(httpMethod)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} method response is not cacheable", httpMethod);
            }
            return false;
        }

        final int code = response.getCode();

        // Should never happen but better be defensive
        if (code <= HttpStatus.SC_INFORMATIONAL) {
            return false;
        }

        if (isKnownNonCacheableStatusCode(code)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} response is not cacheable", code);
            }
            return false;
        }

        if (request.getPath().contains("?")) {
            if (neverCache1_0ResponsesWithQueryString && from1_0Origin(response)) {
                LOG.debug("Response is not cacheable as it had a query string");
                return false;
            } else if (!neverCache1_1ResponsesWithQueryString && !isExplicitlyCacheable(cacheControl, response)) {
                LOG.debug("Response is not cacheable as it is missing explicit caching headers");
                return false;
            }
        }

        if (cacheControl.isMustUnderstand() && !understoodStatusCode(code)) {
            // must-understand cache directive overrides no-store
            LOG.debug("Response contains a status code that the cache does not understand, so it's not cacheable");
            return false;
        }

        if (isExplicitlyNonCacheable(cacheControl)) {
            LOG.debug("Response is explicitly non-cacheable per cache control directive");
            return false;
        }

        if (sharedCache) {
            if (request.containsHeader(HttpHeaders.AUTHORIZATION) &&
                    cacheControl.getSharedMaxAge() == -1 &&
                    !cacheControl.isPublic()) {
                LOG.debug("Request contains private credentials");
                return false;
            }
        }

        // See if the response is tainted
        if (response.countHeaders(HttpHeaders.EXPIRES) > 1) {
            LOG.debug("Multiple Expires headers");
            return false;
        }

        if (response.countHeaders(HttpHeaders.DATE) > 1) {
            LOG.debug("Multiple Date headers");
            return false;
        }

        final Instant responseDate = DateUtils.parseStandardDate(response, HttpHeaders.DATE);
        final Instant responseExpires = DateUtils.parseStandardDate(response, HttpHeaders.EXPIRES);

        if (expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(cacheControl, responseDate, responseExpires)) {
            LOG.debug("Expires header less or equal to Date header and no cache control directives");
            return false;
        }

        // Treat responses with `Vary: *` as essentially non-cacheable.
        final Iterator<String> it = MessageSupport.iterateTokens(response, HttpHeaders.VARY);
        while (it.hasNext()) {
            final String token = it.next();
            if ("*".equals(token)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Vary: * found");
                }
                return false;
            }
        }

        return isExplicitlyCacheable(cacheControl, response) || isHeuristicallyCacheable(cacheControl, code, responseDate, responseExpires);
    }

    private static boolean isKnownCacheableStatusCode(final int status) {
        return status == HttpStatus.SC_OK ||
                status == HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION ||
                status == HttpStatus.SC_MULTIPLE_CHOICES ||
                status == HttpStatus.SC_MOVED_PERMANENTLY ||
                status == HttpStatus.SC_GONE;
    }

    private static boolean isKnownNonCacheableStatusCode(final int status) {
        return status == HttpStatus.SC_PARTIAL_CONTENT;
    }

    private static boolean isUnknownStatusCode(final int status) {
        if (status >= 100 && status <= 101) {
            return false;
        }
        if (status >= 200 && status <= 206) {
            return false;
        }
        if (status >= 300 && status <= 307) {
            return false;
        }
        if (status >= 400 && status <= 417) {
            return false;
        }
        return status < 500 || status > 505;
    }

    /**
     * Determines whether the given CacheControl object indicates that the response is explicitly non-cacheable.
     *
     * @param cacheControl the CacheControl object representing the cache-control directive(s) from the HTTP response.
     * @return true if the response is explicitly non-cacheable according to the cache-control directive(s),
     * false otherwise.
     * <p>
     * When cacheControl is non-null:
     * - Returns true if the response contains "no-store" or (if sharedCache is true) "private" cache-control directives.
     * - If the response contains the "no-cache" directive, it is considered cacheable, but requires validation against
     * the origin server before use. In this case, the method returns false.
     * - Returns false for other cache-control directives, implying the response is cacheable.
     * <p>
     * When cacheControl is null, returns false, implying the response is cacheable.
     */
    protected boolean isExplicitlyNonCacheable(final ResponseCacheControl cacheControl) {
        if (cacheControl == null) {
            return false;
        } else {
            // The response is considered explicitly non-cacheable if it contains
            // "no-store" or (if sharedCache is true) "private" directives.
            // Note that "no-cache" is considered cacheable but requires validation before use.
            return cacheControl.isNoStore() || (sharedCache && cacheControl.isCachePrivate());
        }
    }

    protected boolean isExplicitlyCacheable(final ResponseCacheControl cacheControl, final HttpResponse response) {
        if (cacheControl.isPublic()) {
            return true;
        }
        if (!sharedCache && cacheControl.isCachePrivate()) {
            return true;
        }
        if (response.containsHeader(HttpHeaders.EXPIRES)) {
            return true;
        }
        if (cacheControl.getMaxAge() > 0) {
            return true;
        }
        if (sharedCache && cacheControl.getSharedMaxAge() > 0) {
            return true;
        }
        return false;
    }

    protected boolean isHeuristicallyCacheable(final ResponseCacheControl cacheControl,
                                               final int status,
                                               final Instant responseDate,
                                               final Instant responseExpires) {
        if (isKnownCacheableStatusCode(status)) {
            final Duration freshnessLifetime = calculateFreshnessLifetime(cacheControl, responseDate, responseExpires);
            // calculate freshness lifetime
            if (freshnessLifetime.isNegative()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Freshness lifetime is invalid");
                }
                return false;
            }
            // If the 'immutable' directive is present and the response is still fresh,
            // then the response is considered cacheable without further validation
            if (cacheControl.isImmutable() && responseIsStillFresh(responseDate, freshnessLifetime)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Response is immutable and fresh, considered cacheable without further validation");
                }
                return true;
            }
            if (freshnessLifetime.compareTo(Duration.ZERO) > 0) {
                return true;
            }
        } else if (isUnknownStatusCode(status)) {
            // a response with an unknown status code MUST NOT be
            // cached
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} response is unknown", status);
            }
            return false;
        }
        return false;
    }

    private boolean expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(final ResponseCacheControl cacheControl, final Instant responseDate, final Instant expires) {
        if (!cacheControl.isUndefined()) {
            return false;
        }
        if (expires == null || responseDate == null) {
            return false;
        }
        return expires.compareTo(responseDate) <= 0;
    }

    private boolean from1_0Origin(final HttpResponse response) {
        final Iterator<String> it = MessageSupport.iterateTokens(response, HttpHeaders.VIA);
        if (it.hasNext()) {
            final String token = it.next();
            return token.startsWith("1.0 ") || token.startsWith("HTTP/1.0 ");
        }
        final ProtocolVersion version = response.getVersion() != null ? response.getVersion() : HttpVersion.DEFAULT;
        return HttpVersion.HTTP_1_0.equals(version);
    }

    /**
     * Calculates the freshness lifetime of a response, based on the headers in the response.
     * <p>
     * This method follows the algorithm for calculating the freshness lifetime.
     * The freshness lifetime represents the time interval in seconds during which the response can be served without
     * being considered stale. The freshness lifetime calculation takes into account the s-maxage, max-age, Expires, and
     * Date headers as follows:
     * <ul>
     * <li>If the s-maxage directive is present in the Cache-Control header of the response, its value is used as the
     * freshness lifetime for shared caches, which typically serve multiple users or clients.</li>
     * <li>If the max-age directive is present in the Cache-Control header of the response, its value is used as the
     * freshness lifetime for private caches, which serve a single user or client.</li>
     * <li>If the Expires header is present in the response, its value is used as the expiration time of the response.
     * The freshness lifetime is calculated as the difference between the expiration time and the time specified in the
     * Date header of the response.</li>
     * <li>If none of the above headers are present or if the calculated freshness lifetime is invalid, a default value of
     * 5 minutes is returned.</li>
     * </ul>
     *
     * <p>
     * Note that caching is a complex topic and cache control directives may interact with each other in non-trivial ways.
     * This method provides a basic implementation of the freshness lifetime calculation algorithm and may not be suitable
     * for all use cases. Developers should consult the HTTP caching specifications for more information and consider
     * implementing additional caching mechanisms as needed.
     * </p>
     */
    private Duration calculateFreshnessLifetime(final ResponseCacheControl cacheControl, final Instant responseDate, final Instant responseExpires) {

        if (cacheControl.isUndefined()) {
            // If no cache-control header is present, assume no caching directives and return a default value
            return DEFAULT_FRESHNESS_DURATION; // 5 minutes
        }

        // Check if s-maxage is present and use its value if it is
        if (cacheControl.getSharedMaxAge() != -1) {
            return Duration.ofSeconds(cacheControl.getSharedMaxAge());
        } else if (cacheControl.getMaxAge() != -1) {
            return Duration.ofSeconds(cacheControl.getMaxAge());
        }

        if (responseDate != null && responseExpires != null) {
            return Duration.ofSeconds(responseExpires.getEpochSecond() - responseDate.getEpochSecond());
        }

        // If none of the above conditions are met, a heuristic freshness lifetime might be applicable
        return DEFAULT_FRESHNESS_DURATION; // 5 minutes
    }

    /**
     * Understood status codes include:
     * - All 2xx (Successful) status codes (200-299)
     * - All 3xx (Redirection) status codes (300-399)
     * - All 4xx (Client Error) status codes up to 417 and 421
     * - All 5xx (Server Error) status codes up to 505
     *
     * @param status The HTTP status code to be checked.
     * @return true if the HTTP status code is understood, false otherwise.
     */
    private boolean understoodStatusCode(final int status) {
        return (status >= 200 && status <= 206)    ||
                (status >= 300 && status <= 399)   ||
                (status >= 400 && status <= 417)   ||
                (status == 421)                    ||
                (status >= 500 && status <= 505);
    }

    /**
     * Determines if an HttpResponse is still fresh based on its Date header and calculated freshness lifetime.
     *
     * <p>
     * This method calculates the age of the response from its Date header and compares it with the provided freshness
     * lifetime. If the age is less than the freshness lifetime, the response is considered fresh.
     * </p>
     *
     * <p>
     * Note: If the Date header is missing or invalid, this method assumes the response is not fresh.
     * </p>
     *
     * @param responseDate  The response date.
     * @param freshnessLifetime The calculated freshness lifetime of the HttpResponse.
     * @return {@code true} if the response age is less than its freshness lifetime, {@code false} otherwise.
     */
    private boolean responseIsStillFresh(final Instant responseDate, final Duration freshnessLifetime) {
        if (responseDate == null) {
            // The Date header is missing or invalid. Assuming the response is not fresh.
            return false;
        }
        final Duration age = Duration.between(responseDate, Instant.now());
        return age.compareTo(freshnessLifetime) < 0;
    }

}
