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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;

public class CachingExecBase {

    final AtomicLong cacheHits = new AtomicLong();
    final AtomicLong cacheMisses = new AtomicLong();
    final AtomicLong cacheUpdates = new AtomicLong();

    final ResponseCachingPolicy responseCachingPolicy;
    final CacheValidityPolicy validityPolicy;
    final CachedHttpResponseGenerator responseGenerator;
    final CacheableRequestPolicy cacheableRequestPolicy;
    final CachedResponseSuitabilityChecker suitabilityChecker;
    final CacheConfig cacheConfig;

    CachingExecBase(
            final CacheValidityPolicy validityPolicy,
            final ResponseCachingPolicy responseCachingPolicy,
            final CachedHttpResponseGenerator responseGenerator,
            final CacheableRequestPolicy cacheableRequestPolicy,
            final CachedResponseSuitabilityChecker suitabilityChecker,
            final CacheConfig config) {
        this.responseCachingPolicy = responseCachingPolicy;
        this.validityPolicy = validityPolicy;
        this.responseGenerator = responseGenerator;
        this.cacheableRequestPolicy = cacheableRequestPolicy;
        this.suitabilityChecker = suitabilityChecker;
        this.cacheConfig = config != null ? config : CacheConfig.DEFAULT;
    }

    CachingExecBase(final CacheConfig config) {
        super();
        this.cacheConfig = config != null ? config : CacheConfig.DEFAULT;
        this.validityPolicy = new CacheValidityPolicy(config);
        this.responseGenerator = new CachedHttpResponseGenerator(this.validityPolicy);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker(this.validityPolicy, this.cacheConfig);
        this.responseCachingPolicy = new ResponseCachingPolicy(
                this.cacheConfig.isSharedCache(),
                this.cacheConfig.isNeverCacheHTTP10ResponsesWithQuery(),
                this.cacheConfig.isNeverCacheHTTP11ResponsesWithQuery());
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

    SimpleHttpResponse generateCachedResponse(
            final HttpRequest request,
            final HttpCacheEntry entry,
            final Instant now) throws ResourceIOException {
        if (shouldSendNotModifiedResponse(request, entry, now)) {
            return responseGenerator.generateNotModifiedResponse(entry);
        } else {
            return responseGenerator.generateResponse(request, entry);
        }
    }

    SimpleHttpResponse generateGatewayTimeout() {
        return SimpleHttpResponse.create(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
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

    boolean shouldSendNotModifiedResponse(final HttpRequest request, final HttpCacheEntry responseEntry, final Instant now) {
        return suitabilityChecker.isConditional(request)
                && suitabilityChecker.allConditionalsMatch(request, responseEntry, now);
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

    boolean isResponseTooBig(final EntityDetails entityDetails) {
        if (entityDetails == null) {
            return false;
        }
        final long contentLength = entityDetails.getContentLength();
        if (contentLength == -1) {
            return false;
        }
        final long maxObjectSize = cacheConfig.getMaxObjectSize();
        return contentLength > maxObjectSize;
    }

}
