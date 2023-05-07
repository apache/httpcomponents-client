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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;
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

    private final static Set<Integer> CACHEABLE_STATUS_CODES =
            new HashSet<>(Arrays.asList(HttpStatus.SC_OK,
                    HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION,
                    HttpStatus.SC_MULTIPLE_CHOICES,
                    HttpStatus.SC_MOVED_PERMANENTLY,
                    HttpStatus.SC_GONE));

    private static final Logger LOG = LoggerFactory.getLogger(ResponseCachingPolicy.class);

    private final long maxObjectSizeBytes;
    private final boolean sharedCache;
    private final boolean neverCache1_0ResponsesWithQueryString;
    private final boolean neverCache1_1ResponsesWithQueryString;
    private final Set<Integer> uncacheableStatusCodes;

    /**
     * A flag indicating whether serving stale cache entries is allowed when an error occurs
     * while fetching a fresh response from the origin server.
     * If {@code true}, stale cache entries may be served in case of errors.
     * If {@code false}, stale cache entries will not be served in case of errors.
     */
    private final boolean staleIfErrorEnabled;

    /**
     * Define a cache policy that limits the size of things that should be stored
     * in the cache to a maximum of {@link HttpResponse} bytes in size.
     *
     * @param maxObjectSizeBytes the size to limit items into the cache
     * @param sharedCache whether to behave as a shared cache (true) or a
     * non-shared/private cache (false)
     * @param neverCache1_0ResponsesWithQueryString true to never cache HTTP 1.0 responses with a query string, false
     * to cache if explicit cache headers are found.
     * @param allow303Caching if this policy is permitted to cache 303 response
     * @param neverCache1_1ResponsesWithQueryString {@code true} to never cache HTTP 1.1 responses with a query string,
     * {@code false} to cache if explicit cache headers are found.
     */
    public ResponseCachingPolicy(final long maxObjectSizeBytes,
            final boolean sharedCache,
            final boolean neverCache1_0ResponsesWithQueryString,
            final boolean allow303Caching,
            final boolean neverCache1_1ResponsesWithQueryString) {
        this(maxObjectSizeBytes,
                sharedCache,
                neverCache1_0ResponsesWithQueryString,
                allow303Caching,
                neverCache1_1ResponsesWithQueryString,
                false);
    }

    /**
     * Constructs a new ResponseCachingPolicy with the specified cache policy settings and stale-if-error support.
     *
     * @param maxObjectSizeBytes                    the maximum size of objects, in bytes, that should be stored
     *                                              in the cache
     * @param sharedCache                           whether to behave as a shared cache (true) or a
     *                                              non-shared/private cache (false)
     * @param neverCache1_0ResponsesWithQueryString {@code true} to never cache HTTP 1.0 responses with a query string,
     *                                              {@code false} to cache if explicit cache headers are found.
     * @param allow303Caching                       {@code true} if this policy is permitted to cache 303 responses,
     *                                              {@code false} otherwise
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
             final boolean allow303Caching,
             final boolean neverCache1_1ResponsesWithQueryString,
             final boolean staleIfErrorEnabled) {
        this.maxObjectSizeBytes = maxObjectSizeBytes;
        this.sharedCache = sharedCache;
        this.neverCache1_0ResponsesWithQueryString = neverCache1_0ResponsesWithQueryString;
        this.neverCache1_1ResponsesWithQueryString = neverCache1_1ResponsesWithQueryString;
        if (allow303Caching) {
            uncacheableStatusCodes = new HashSet<>(Collections.singletonList(HttpStatus.SC_PARTIAL_CONTENT));
        } else {
            uncacheableStatusCodes = new HashSet<>(Arrays.asList(HttpStatus.SC_PARTIAL_CONTENT, HttpStatus.SC_SEE_OTHER));
        }
        this.staleIfErrorEnabled = staleIfErrorEnabled;
    }

    /**
     * Determines if an HttpResponse can be cached.
     *
     * @param httpMethod What type of request was this, a GET, PUT, other?
     * @param response The origin response
     * @return {@code true} if response is cacheable
     */
    public boolean isResponseCacheable(final String httpMethod, final HttpResponse response, final CacheControl cacheControl) {
        boolean cacheable = false;

        if (!HeaderConstants.GET_METHOD.equals(httpMethod) && !HeaderConstants.HEAD_METHOD.equals(httpMethod)
                && !HeaderConstants.POST_METHOD.equals(httpMethod)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} method response is not cacheable", httpMethod);
            }
            return false;
        }

        final int status = response.getCode();
        if (CACHEABLE_STATUS_CODES.contains(status)) {
            // these response codes MAY be cached
            cacheable = true;
        } else if (uncacheableStatusCodes.contains(status)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} response is not cacheable", status);
            }
            return false;
        } else if (unknownStatusCode(status)) {
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

        if (response.countHeaders(HeaderConstants.AGE) > 1) {
            LOG.debug("Multiple Age headers");
            return false;
        }

        if (response.countHeaders(HeaderConstants.EXPIRES) > 1) {
            LOG.debug("Multiple Expires headers");
            return false;
        }

        if (response.countHeaders(HttpHeaders.DATE) > 1) {
            LOG.debug("Multiple Date headers");
            return false;
        }

        final Instant date = DateUtils.parseStandardDate(response, HttpHeaders.DATE);
        if (date == null) {
            LOG.debug("Invalid / missing Date header");
            return false;
        }

        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderConstants.VARY);
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

        // calculate freshness lifetime
        final Duration freshnessLifetime = calculateFreshnessLifetime(response, cacheControl);
        if (freshnessLifetime.isNegative() || freshnessLifetime.isZero()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Freshness lifetime is invalid");
            }
            return false;
        }

        return cacheable || isExplicitlyCacheable(response, cacheControl);
    }

    private boolean unknownStatusCode(final int status) {
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
    protected boolean isExplicitlyNonCacheable(final CacheControl cacheControl) {
        if (cacheControl == null) {
            return false;
        } else {
            // The response is considered explicitly non-cacheable if it contains
            // "no-store" or (if sharedCache is true) "private" directives.
            // Note that "no-cache" is considered cacheable but requires validation before use.
            return cacheControl.isNoStore() || (sharedCache && cacheControl.isCachePrivate());
        }
    }
    /**
     * @deprecated As of version 5.0, use {@link ResponseCachingPolicy#parseCacheControlHeader(MessageHeaders)} instead.
     */
    @Deprecated
    protected boolean hasCacheControlParameterFrom(final HttpMessage msg, final String[] params) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(msg, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elem = it.next();
            for (final String param : params) {
                if (param.equalsIgnoreCase(elem.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isExplicitlyCacheable(final HttpResponse response, final CacheControl cacheControl ) {
        if (response.getFirstHeader(HeaderConstants.EXPIRES) != null) {
            return true;
        }
        if (cacheControl == null) {
            return false;
        }else {
            return cacheControl.getMaxAge() > 0 || cacheControl.getSharedMaxAge()>0 ||
                    cacheControl.isMustRevalidate() || cacheControl.isProxyRevalidate() || (cacheControl.isPublic());
        }
    }

    /**
     * Determine if the {@link HttpResponse} gotten from the origin is a
     * cacheable response.
     *
     * @param request  the {@link HttpRequest} that generated an origin hit. Can't be {@code null}.
     * @param response the {@link HttpResponse} from the origin. Can't be {@code null}.
     * @return {@code true} if response is cacheable
     * @since 5.3
     */
    public boolean isResponseCacheable(final HttpRequest request, final HttpResponse response) {
        Args.notNull(request, "Request");
        Args.notNull(response, "Response");
        return isResponseCacheable(request, response, parseCacheControlHeader(response));
    }

    /**
     * Determines if an HttpResponse can be cached.
     *
     * @param httpMethod What type of request was this, a GET, PUT, other?. Can't be {@code null}.
     * @param response   The origin response. Can't be {@code null}.
     * @return {@code true} if response is cacheable
     * @since 5.3
     */
    public boolean isResponseCacheable(final String httpMethod, final HttpResponse response) {
        Args.notEmpty(httpMethod, "httpMethod");
        Args.notNull(response, "Response");
        return isResponseCacheable(httpMethod, response, parseCacheControlHeader(response));
    }


    /**
     * Determine if the {@link HttpResponse} gotten from the origin is a
     * cacheable response.
     *
     * @param request the {@link HttpRequest} that generated an origin hit
     * @param response the {@link HttpResponse} from the origin
     * @return {@code true} if response is cacheable
     */
    public boolean isResponseCacheable(final HttpRequest request, final HttpResponse response, final CacheControl cacheControl) {
        final ProtocolVersion version = request.getVersion() != null ? request.getVersion() : HttpVersion.DEFAULT;
        if (version.compareToVersion(HttpVersion.HTTP_1_1) > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Protocol version {} is non-cacheable", version);
            }
            return false;
        }
        if (cacheControl != null && cacheControl.isNoStore()) {
            LOG.debug("Response is explicitly non-cacheable per cache control directive");
            return false;
        }

        if (request.getRequestUri().contains("?")) {
            if (neverCache1_0ResponsesWithQueryString && from1_0Origin(response)) {
                LOG.debug("Response is not cacheable as it had a query string");
                return false;
            } else if (!neverCache1_1ResponsesWithQueryString && !isExplicitlyCacheable(response, cacheControl)) {
                LOG.debug("Response is not cacheable as it is missing explicit caching headers");
                return false;
            }
        }

        if (expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(response, cacheControl)) {
            LOG.debug("Expires header less or equal to Date header and no cache control directives");
            return false;
        }

        if (sharedCache) {
            if (request.countHeaders(HeaderConstants.AUTHORIZATION) > 0
                    && cacheControl != null && !(cacheControl.getSharedMaxAge() > -1 || cacheControl.isMustRevalidate() || cacheControl.isPublic())) {
                LOG.debug("Request contains private credentials");
                return false;
            }
        }

        final String method = request.getMethod();
        return isResponseCacheable(method, response, cacheControl);
    }

    private boolean expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(final HttpResponse response, final CacheControl cacheControl) {
        if (cacheControl != null) {
            return false;
        }
        final Header expiresHdr = response.getFirstHeader(HeaderConstants.EXPIRES);
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
        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderConstants.VIA);
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
    private Duration calculateFreshnessLifetime(final HttpResponse response, final CacheControl cacheControl) {

        if (cacheControl == null) {
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
     * @param entry the cached HTTP message entry to check
     * @return {@code true} if a stale response can be served in case of an error status code, {@code false} otherwise
     */
    boolean isStaleIfErrorEnabled(final HttpCacheEntry entry) {
        // Check if the stale-while-revalidate extension is enabled
        if (staleIfErrorEnabled) {
            // Check if the cached response has an error status code
            final int statusCode = entry.getStatus();
            if (statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR && statusCode <= HttpStatus.SC_GATEWAY_TIMEOUT) {
                // Check if the cached response has a stale-while-revalidate directive
                final CacheControl cacheControl = parseCacheControlHeader(entry);
                if (cacheControl == null) {
                    return false;
                } else {
                    return cacheControl.getStaleWhileRevalidate() > 0;
                }
            }
        }
        return false;
    }

    /**
     * Parses the Cache-Control header from the given HTTP messageHeaders and returns the corresponding CacheControl instance.
     * If the header is not present, returns a CacheControl instance with default values for all directives.
     *
     * @param messageHeaders the HTTP message to parse the header from
     * @return a CacheControl instance with the parsed directives or default values if the header is not present
     */
    private CacheControl parseCacheControlHeader(final MessageHeaders messageHeaders) {
        final Header cacheControlHeader = messageHeaders.getFirstHeader(HttpHeaders.CACHE_CONTROL);
        if (cacheControlHeader == null) {
            return null;
        } else {
            return CacheControlHeaderParser.INSTANCE.parse(cacheControlHeader);
        }
    }

}
