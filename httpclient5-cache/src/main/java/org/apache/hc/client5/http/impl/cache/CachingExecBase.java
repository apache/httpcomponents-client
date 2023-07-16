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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingExecBase {

    final static boolean SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS = false;

    final AtomicLong cacheHits = new AtomicLong();
    final AtomicLong cacheMisses = new AtomicLong();
    final AtomicLong cacheUpdates = new AtomicLong();

    final Map<ProtocolVersion, String> viaHeaders = new ConcurrentHashMap<>(4);
    final ResponseCachingPolicy responseCachingPolicy;
    final CacheValidityPolicy validityPolicy;
    final CachedHttpResponseGenerator responseGenerator;
    final CacheableRequestPolicy cacheableRequestPolicy;
    final CachedResponseSuitabilityChecker suitabilityChecker;
    final ResponseProtocolCompliance responseCompliance;
    final RequestProtocolCompliance requestCompliance;
    final CacheConfig cacheConfig;

    private static final Logger LOG = LoggerFactory.getLogger(CachingExecBase.class);

    private static final TimeValue ONE_DAY = TimeValue.ofHours(24);

    CachingExecBase(
            final CacheValidityPolicy validityPolicy,
            final ResponseCachingPolicy responseCachingPolicy,
            final CachedHttpResponseGenerator responseGenerator,
            final CacheableRequestPolicy cacheableRequestPolicy,
            final CachedResponseSuitabilityChecker suitabilityChecker,
            final ResponseProtocolCompliance responseCompliance,
            final RequestProtocolCompliance requestCompliance,
            final CacheConfig config) {
        this.responseCachingPolicy = responseCachingPolicy;
        this.validityPolicy = validityPolicy;
        this.responseGenerator = responseGenerator;
        this.cacheableRequestPolicy = cacheableRequestPolicy;
        this.suitabilityChecker = suitabilityChecker;
        this.requestCompliance = requestCompliance;
        this.responseCompliance = responseCompliance;
        this.cacheConfig = config != null ? config : CacheConfig.DEFAULT;
    }

    CachingExecBase(final CacheConfig config) {
        super();
        this.cacheConfig = config != null ? config : CacheConfig.DEFAULT;
        this.validityPolicy = new CacheValidityPolicy(config);
        this.responseGenerator = new CachedHttpResponseGenerator(this.validityPolicy);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker(this.validityPolicy, this.cacheConfig);
        this.responseCompliance = new ResponseProtocolCompliance();
        this.requestCompliance = new RequestProtocolCompliance(this.cacheConfig.isWeakETagOnPutDeleteAllowed());
        this.responseCachingPolicy = new ResponseCachingPolicy(
                this.cacheConfig.getMaxObjectSize(),
                this.cacheConfig.isSharedCache(),
                this.cacheConfig.isNeverCacheHTTP10ResponsesWithQuery(),
                this.cacheConfig.is303CachingEnabled(),
                this.cacheConfig.isNeverCacheHTTP11ResponsesWithQuery(),
                this.cacheConfig.isStaleIfErrorEnabled());
    }

    /**
     * Reports the number of times that the cache successfully responded
     * to an {@link HttpRequest} without contacting the origin server.
     * @return the number of cache hits
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Reports the number of times that the cache contacted the origin
     * server because it had no appropriate response cached.
     * @return the number of cache misses
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Reports the number of times that the cache was able to satisfy
     * a response by revalidating an existing but stale cache entry.
     * @return the number of cache revalidations
     */
    public long getCacheUpdates() {
        return cacheUpdates.get();
    }

    /**
     * @since 5.2
     */
    SimpleHttpResponse getFatallyNonCompliantResponse(
            final HttpRequest request,
            final HttpContext context,
            final boolean resourceExists) {
        final List<RequestProtocolError> fatalError = requestCompliance.requestIsFatallyNonCompliant(request, resourceExists);
        if (fatalError != null && !fatalError.isEmpty()) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            return responseGenerator.getErrorForRequest(fatalError.get(0));
        }
        return null;
    }

    void recordCacheMiss(final HttpHost target, final HttpRequest request) {
        cacheMisses.getAndIncrement();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Cache miss [host: {}; uri: {}]", target, request.getRequestUri());
        }
    }

    void recordCacheHit(final HttpHost target, final HttpRequest request) {
        cacheHits.getAndIncrement();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Cache hit [host: {}; uri: {}]", target, request.getRequestUri());
        }
    }

    void recordCacheFailure(final HttpHost target, final HttpRequest request) {
        cacheMisses.getAndIncrement();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Cache failure [host: {}; uri: {}]", target, request.getRequestUri());
        }
    }

    void recordCacheUpdate(final HttpContext context) {
        cacheUpdates.getAndIncrement();
        setResponseStatus(context, CacheResponseStatus.VALIDATED);
    }

    SimpleHttpResponse generateCachedResponse(
            final ResponseCacheControl responseCacheControl,
            final HttpCacheEntry entry,
            final HttpRequest request,
            final HttpContext context) throws ResourceIOException {
        final SimpleHttpResponse cachedResponse;
        if (request.containsHeader(HttpHeaders.IF_NONE_MATCH)
                || request.containsHeader(HttpHeaders.IF_MODIFIED_SINCE)) {
            cachedResponse = responseGenerator.generateNotModifiedResponse(entry);
        } else {
            cachedResponse = responseGenerator.generateResponse(request, entry);
        }
        setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
        return cachedResponse;
    }

    SimpleHttpResponse handleRevalidationFailure(
            final RequestCacheControl requestCacheControl,
            final ResponseCacheControl responseCacheControl,
            final HttpCacheEntry entry,
            final HttpRequest request,
            final HttpContext context,
            final Instant now) throws IOException {
        if (staleResponseNotAllowed(requestCacheControl, responseCacheControl, entry, now)) {
            return generateGatewayTimeout(context);
        } else {
            return unvalidatedCacheHit(request, context, entry);
        }
    }

    SimpleHttpResponse generateGatewayTimeout(
            final HttpContext context) {
        setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
        return SimpleHttpResponse.create(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
    }

    SimpleHttpResponse unvalidatedCacheHit(
            final HttpRequest request,
            final HttpContext context,
            final HttpCacheEntry entry) throws IOException {
        final SimpleHttpResponse cachedResponse = responseGenerator.generateResponse(request, entry);
        setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
        return cachedResponse;
    }

    boolean staleResponseNotAllowed(final RequestCacheControl requestCacheControl,
                                    final ResponseCacheControl responseCacheControl,
                                    final HttpCacheEntry entry,
                                    final Instant now) {
        return responseCacheControl.isMustRevalidate()
            || (cacheConfig.isSharedCache() && responseCacheControl.isProxyRevalidate())
            || explicitFreshnessRequest(requestCacheControl, responseCacheControl, entry, now);
    }

    boolean mayCallBackend(final RequestCacheControl requestCacheControl) {
        if (requestCacheControl.isOnlyIfCached()) {
            LOG.debug("Request marked only-if-cached");
            return false;
        }
        return true;
    }

    boolean explicitFreshnessRequest(final RequestCacheControl requestCacheControl,
                                     final ResponseCacheControl responseCacheControl,
                                     final HttpCacheEntry entry,
                                     final Instant now) {
        if (requestCacheControl.getMaxStale() >= 0) {
            final TimeValue age = validityPolicy.getCurrentAge(entry, now);
            final TimeValue lifetime = validityPolicy.getFreshnessLifetime(responseCacheControl, entry);
            if (age.toSeconds() - lifetime.toSeconds() > requestCacheControl.getMaxStale()) {
                return true;
            }
        } else if (requestCacheControl.getMinFresh() >= 0 || requestCacheControl.getMaxAge() >= 0) {
            return true;
        }
        return false;
    }

    String generateViaHeader(final HttpMessage msg) {

        if (msg.getVersion() == null) {
            msg.setVersion(HttpVersion.DEFAULT);
        }
        final ProtocolVersion pv = msg.getVersion();
        final String existingEntry = viaHeaders.get(msg.getVersion());
        if (existingEntry != null) {
            return existingEntry;
        }

        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.hc.client5", getClass().getClassLoader());
        final String release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;

        final String value;
        final int major = pv.getMajor();
        final int minor = pv.getMinor();
        if (URIScheme.HTTP.same(pv.getProtocol())) {
            value = String.format("%d.%d localhost (Apache-HttpClient/%s (cache))", major, minor,
                    release);
        } else {
            value = String.format("%s/%d.%d localhost (Apache-HttpClient/%s (cache))", pv.getProtocol(), major,
                    minor, release);
        }
        viaHeaders.put(pv, value);

        return value;
    }

    void setResponseStatus(final HttpContext context, final CacheResponseStatus value) {
        if (context != null) {
            context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, value);
        }
    }

    /**
     * Reports whether this {@code CachingHttpClient} implementation
     * supports byte-range requests as specified by the {@code Range}
     * and {@code Content-Range} headers.
     * @return {@code true} if byte-range requests are supported
     */
    boolean supportsRangeAndContentRangeHeaders() {
        return SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS;
    }

    Instant getCurrentDate() {
        return Instant.now();
    }

    boolean clientRequestsOurOptions(final HttpRequest request) {
        if (!Method.OPTIONS.isSame(request.getMethod())) {
            return false;
        }

        if (!"*".equals(request.getRequestUri())) {
            return false;
        }

        final Header h = request.getFirstHeader(HttpHeaders.MAX_FORWARDS);
        return "0".equals(h != null ? h.getValue() : null);
    }

    boolean revalidationResponseIsTooOld(final HttpResponse backendResponse, final HttpCacheEntry cacheEntry) {
        // either backend response or cached entry did not have a valid
        // Date header, so we can't tell if they are out of order
        // according to the origin clock; thus we can skip the
        // unconditional retry recommended in 13.2.6 of RFC 2616.
        return DateSupport.isBefore(backendResponse, cacheEntry, HttpHeaders.DATE);
    }

    boolean shouldSendNotModifiedResponse(final HttpRequest request, final HttpCacheEntry responseEntry) {
        return (suitabilityChecker.isConditional(request)
                && suitabilityChecker.allConditionalsMatch(request, responseEntry, Instant.now()));
    }

    boolean staleIfErrorAppliesTo(final int statusCode) {
        return statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR
                || statusCode == HttpStatus.SC_BAD_GATEWAY
                || statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE
                || statusCode == HttpStatus.SC_GATEWAY_TIMEOUT;
    }

    /**
     * For 304 Not modified responses, adds a "Last-Modified" header with the
     * value of the "If-Modified-Since" header passed in the request. This
     * header is required to be able to reuse match the cache entry for
     * subsequent requests but as defined in http specifications it is not
     * included in 304 responses by backend servers. This header will not be
     * included in the resulting response.
     */
    void storeRequestIfModifiedSinceFor304Response(final HttpRequest request, final HttpResponse backendResponse) {
        if (backendResponse.getCode() == HttpStatus.SC_NOT_MODIFIED) {
            final Header h = request.getFirstHeader(HttpHeaders.IF_MODIFIED_SINCE);
            if (h != null) {
                backendResponse.addHeader(HttpHeaders.LAST_MODIFIED, h.getValue());
            }
        }
    }

}
