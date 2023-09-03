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
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Set;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
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

    /**
     * This {@link DateTimeFormatter} is used to format and parse date-time objects in a specific format commonly
     * used in HTTP protocol messages. The format includes the day of the week, day of the month, month, year, and time
     * of day, all represented in GMT time. An example of a date-time string in this format is "Tue, 15 Nov 1994 08:12:31 GMT".
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;

    private static final Logger LOG = LoggerFactory.getLogger(ResponseCachingPolicy.class);

    private final long maxObjectSizeBytes;
    private final boolean sharedCache;
    private final boolean neverCache1_0ResponsesWithQueryString;
    private final boolean neverCache1_1ResponsesWithQueryString;

    /**
     * A flag indicating whether serving stale cache entries is allowed when an error occurs
     * while fetching a fresh response from the origin server.
     * If {@code true}, stale cache entries may be served in case of errors.
     * If {@code false}, stale cache entries will not be served in case of errors.
     */
    private final boolean staleIfErrorEnabled;

    /**
     * Constructs a new ResponseCachingPolicy with the specified cache policy settings and stale-if-error support.
     *
     * @param maxObjectSizeBytes                    the maximum size of objects, in bytes, that should be stored
     *                                              in the cache
     * @param sharedCache                           whether to behave as a shared cache (true) or a
     *                                              non-shared/private cache (false)
     * @param neverCache1_0ResponsesWithQueryString {@code true} to never cache HTTP 1.0 responses with a query string,
     *                                              {@code false} to cache if explicit cache headers are found.
     * @param neverCache1_1ResponsesWithQueryString {@code true} to never cache HTTP 1.1 responses with a query string,
     *                                              {@code false} to cache if explicit cache headers are found.
     * @param staleIfErrorEnabled                   {@code true} to enable the stale-if-error cache directive, which
     *                                              allows clients to receive a stale cache entry when a request
     *                                              results in an error, {@code false} to disable this feature.
     * @since 5.3
     */
    public ResponseCachingPolicy(final long maxObjectSizeBytes,
             final boolean sharedCache,
             final boolean neverCache1_0ResponsesWithQueryString,
             final boolean neverCache1_1ResponsesWithQueryString,
             final boolean staleIfErrorEnabled) {
        this.maxObjectSizeBytes = maxObjectSizeBytes;
        this.sharedCache = sharedCache;
        this.neverCache1_0ResponsesWithQueryString = neverCache1_0ResponsesWithQueryString;
        this.neverCache1_1ResponsesWithQueryString = neverCache1_1ResponsesWithQueryString;
        this.staleIfErrorEnabled = staleIfErrorEnabled;
    }

    /**
     * Determines if an HttpResponse can be cached.
     *
     * @return {@code true} if response is cacheable
     */
    public boolean isResponseCacheable(final ResponseCacheControl cacheControl, final String httpMethod, final HttpResponse response) {
        boolean cacheable = false;

        if (!Method.GET.isSame(httpMethod) && !Method.HEAD.isSame(httpMethod) && !Method.POST.isSame((httpMethod))) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} method response is not cacheable", httpMethod);
            }
            return false;
        }

        final int status = response.getCode();
        if (isKnownCacheableStatusCode(status)) {
            // these response codes MAY be cached
            cacheable = true;
        } else if (isKnownNonCacheableStatusCode(status)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} response is not cacheable", status);
            }
            return false;
        } else if (isUnknownStatusCode(status)) {
            // a response with an unknown status code MUST NOT be
            // cached
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} response is unknown", status);
            }
            return false;
        }

        final Header contentLength = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null) {
            final long contentLengthValue = Long.parseLong(contentLength.getValue());
            if (contentLengthValue > this.maxObjectSizeBytes) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Response content length exceeds {}", this.maxObjectSizeBytes);
                }
                return false;
            }
        }

        if (response.countHeaders(HttpHeaders.EXPIRES) > 1) {
            LOG.debug("Multiple Expires headers");
            return false;
        }

        if (response.countHeaders(HttpHeaders.DATE) > 1) {
            LOG.debug("Multiple Date headers");
            return false;
        }

        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HttpHeaders.VARY);
        while (it.hasNext()) {
            final HeaderElement elem = it.next();
            if ("*".equals(elem.getName())) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Vary * found");
                }
                return false;
            }
        }
        if (isExplicitlyNonCacheable(cacheControl)) {
            LOG.debug("Response is explicitly non-cacheable");
            return false;
        }

        final Duration freshnessLifetime = calculateFreshnessLifetime(cacheControl, response);

        // If the 'immutable' directive is present and the response is still fresh,
        // then the response is considered cacheable without further validation
        if (cacheControl.isImmutable() && responseIsStillFresh(response, freshnessLifetime)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Response is immutable and fresh, considered cacheable without further validation");
            }
            return true;
        }

        // calculate freshness lifetime
        if (freshnessLifetime.isNegative() || freshnessLifetime.isZero()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Freshness lifetime is invalid");
            }
            return false;
        }

        return cacheable || isExplicitlyCacheable(cacheControl, response);
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
        if (response.getFirstHeader(HttpHeaders.EXPIRES) != null) {
            return true;
        }
        return cacheControl.getMaxAge() > 0 || cacheControl.getSharedMaxAge()>0 ||
                cacheControl.isMustRevalidate() || cacheControl.isProxyRevalidate() || (cacheControl.isPublic());
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

        if (cacheControl.isMustUnderstand() && cacheControl.isNoStore() && !understoodStatusCode(response.getCode())) {
            // must-understand cache directive overrides no-store
            LOG.debug("Response contains a status code that the cache does not understand, so it's not cacheable");
            return false;
        }

        if (!cacheControl.isMustUnderstand() && cacheControl.isNoStore()) {
            LOG.debug("Response is explicitly non-cacheable per cache control directive");
            return false;
        }


        if (request.getRequestUri().contains("?")) {
            if (neverCache1_0ResponsesWithQueryString && from1_0Origin(response)) {
                LOG.debug("Response is not cacheable as it had a query string");
                return false;
            } else if (!neverCache1_1ResponsesWithQueryString && !isExplicitlyCacheable(cacheControl, response)) {
                LOG.debug("Response is not cacheable as it is missing explicit caching headers");
                return false;
            }
        }

        if (expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(cacheControl, response)) {
            LOG.debug("Expires header less or equal to Date header and no cache control directives");
            return false;
        }

        if (sharedCache) {
            if (request.countHeaders(HttpHeaders.AUTHORIZATION) > 0
                    && !(cacheControl.getSharedMaxAge() > -1 || cacheControl.isMustRevalidate() || cacheControl.isPublic())) {
                LOG.debug("Request contains private credentials");
                return false;
            }
        }

        final String method = request.getMethod();
        return isResponseCacheable(cacheControl, method, response);
    }

    private boolean expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(final ResponseCacheControl cacheControl, final HttpResponse response) {
        if (!cacheControl.isUndefined()) {
            return false;
        }
        final Header expiresHdr = response.getFirstHeader(HttpHeaders.EXPIRES);
        final Header dateHdr = response.getFirstHeader(HttpHeaders.DATE);
        if (expiresHdr == null || dateHdr == null) {
            return false;
        }
        final Instant expires = DateUtils.parseStandardDate(expiresHdr.getValue());
        final Instant date = DateUtils.parseStandardDate(dateHdr.getValue());
        if (expires == null || date == null) {
            return false;
        }
        return expires.equals(date) || expires.isBefore(date);
    }

    private boolean from1_0Origin(final HttpResponse response) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HttpHeaders.VIA);
        if (it.hasNext()) {
            final HeaderElement elt = it.next();
            final String proto = elt.toString().split("\\s")[0];
            if (proto.contains("/")) {
                return proto.equals("HTTP/1.0");
            } else {
                return proto.equals("1.0");
            }
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
     *
     * @param response the HTTP response for which to calculate the freshness lifetime
     * @return the freshness lifetime of the response, in seconds
     */
    private Duration calculateFreshnessLifetime(final ResponseCacheControl cacheControl, final HttpResponse response) {

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

        // Check if Expires is present and use its value minus the value of the Date header
        Instant expiresInstant = null;
        Instant dateInstant = null;
        final Header expire = response.getFirstHeader(HttpHeaders.EXPIRES);
        if (expire != null) {
            final String expiresHeaderValue = expire.getValue();
            expiresInstant = FORMATTER.parse(expiresHeaderValue, Instant::from);
        }
        final Header date = response.getFirstHeader(HttpHeaders.DATE);
        if (date != null) {
            final String dateHeaderValue = date.getValue();
            dateInstant = FORMATTER.parse(dateHeaderValue, Instant::from);
        }
        if (expiresInstant != null && dateInstant != null) {
            return Duration.ofSeconds(expiresInstant.getEpochSecond() - dateInstant.getEpochSecond());
        }

        // If none of the above conditions are met, a heuristic freshness lifetime might be applicable
        return DEFAULT_FRESHNESS_DURATION; // 5 minutes
    }

    /**
     * Determines whether a stale response should be served in case of an error status code in the cached response.
     * This method first checks if the {@code stale-if-error} extension is enabled in the cache configuration. If it is, it
     * then checks if the cached response has an error status code (500-504). If it does, it checks if the response has a
     * {@code stale-while-revalidate} directive in its Cache-Control header. If it does, this method returns {@code true},
     * indicating that a stale response can be served. If not, it returns {@code false}.
     *
     * @return {@code true} if a stale response can be served in case of an error status code, {@code false} otherwise
     */
    boolean isStaleIfErrorEnabled(final ResponseCacheControl cacheControl, final HttpCacheEntry entry) {
        // Check if the stale-while-revalidate extension is enabled
        if (staleIfErrorEnabled) {
            // Check if the cached response has an error status code
            final int statusCode = entry.getStatus();
            if (statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR && statusCode <= HttpStatus.SC_GATEWAY_TIMEOUT) {
                // Check if the cached response has a stale-while-revalidate directive
                return cacheControl.getStaleWhileRevalidate() > 0;
            }
        }
        return false;
    }

    /**
     * Determines if the given {@link HttpCacheEntry} requires revalidation based on the presence of the {@code no-cache} directive
     * in the Cache-Control header.
     * <p>
     * The method returns true in the following cases:
     * - If the {@code no-cache} directive is present without any field names.
     * - If the {@code no-cache} directive is present with field names, and at least one of these field names is present
     * in the headers of the {@link HttpCacheEntry}.
     * <p>
     * If the {@code no-cache} directive is not present in the Cache-Control header, the method returns {@code false}.
     *
     * @param entry the  {@link HttpCacheEntry} containing the headers to check for the {@code no-cache} directive.
     * @return true if revalidation is required based on the {@code no-cache} directive, {@code false} otherwise.
     */
    boolean responseContainsNoCacheDirective(final ResponseCacheControl responseCacheControl, final HttpCacheEntry entry) {
        final Set<String> noCacheFields = responseCacheControl.getNoCacheFields();

        // If no-cache directive is present and has no field names
        if (responseCacheControl.isNoCache() && noCacheFields.isEmpty()) {
            LOG.debug("No-cache directive present without field names. Revalidation required.");
            return true;
        }

        // If no-cache directive is present with field names
        if (responseCacheControl.isNoCache()) {
            for (final String field : noCacheFields) {
                if (entry.getFirstHeader(field) != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("No-cache directive field '{}' found in response headers. Revalidation required.", field);
                    }
                    return true;
                }
            }
        }
        return false;
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
     * @param response          The HttpResponse whose freshness is being checked.
     * @param freshnessLifetime The calculated freshness lifetime of the HttpResponse.
     * @return {@code true} if the response age is less than its freshness lifetime, {@code false} otherwise.
     */
    private boolean responseIsStillFresh(final HttpResponse response, final Duration freshnessLifetime) {
        final Instant date = DateUtils.parseStandardDate(response, HttpHeaders.DATE);
        if (date == null) {
            // The Date header is missing or invalid. Assuming the response is not fresh.
            return false;
        }
        final Duration age = Duration.between(date, Instant.now());
        return age.compareTo(freshnessLifetime) < 0;
    }

}
