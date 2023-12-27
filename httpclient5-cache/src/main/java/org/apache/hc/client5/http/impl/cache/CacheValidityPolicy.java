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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CacheValidityPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(CacheValidityPolicy.class);

    private final boolean shared;
    private final boolean useHeuristicCaching;
    private final float heuristicCoefficient;
    private final TimeValue heuristicDefaultLifetime;


    /**
     * Constructs a CacheValidityPolicy with the provided CacheConfig. If the config is null, it will use
     * default heuristic coefficient and default heuristic lifetime from CacheConfig.DEFAULT.
     *
     * @param config The CacheConfig to use for this CacheValidityPolicy. If null, default values are used.
     */
    CacheValidityPolicy(final CacheConfig config) {
        super();
        this.shared = config != null ? config.isSharedCache() : CacheConfig.DEFAULT.isSharedCache();
        this.useHeuristicCaching = config != null ? config.isHeuristicCachingEnabled() : CacheConfig.DEFAULT.isHeuristicCachingEnabled();
        this.heuristicCoefficient = config != null ? config.getHeuristicCoefficient() : CacheConfig.DEFAULT.getHeuristicCoefficient();
        this.heuristicDefaultLifetime = config != null ? config.getHeuristicDefaultLifetime() : CacheConfig.DEFAULT.getHeuristicDefaultLifetime();
    }

    /**
     * Default constructor for CacheValidityPolicy. Initializes the policy with default values.
     */
    CacheValidityPolicy() {
        this(null);
    }


    public TimeValue getCurrentAge(final HttpCacheEntry entry, final Instant now) {
        return TimeValue.ofSeconds(getCorrectedInitialAge(entry).toSeconds() + getResidentTime(entry, now).toSeconds());
    }

    /**
     * Calculate the freshness lifetime of a response based on the provided cache control and cache entry.
     * <ul>
     * <li>If the cache is shared and the s-maxage response directive is present, use its value.</li>
     * <li>If the max-age response directive is present, use its value.</li>
     * <li>If the Expires response header field is present, use its value minus the value of the Date response header field.</li>
     * <li>Otherwise, a heuristic freshness lifetime might be applicable.</li>
     * </ul>
     *
     * @param responseCacheControl the cache control directives associated with the response.
     * @param entry                the cache entry associated with the response.
     * @return the calculated freshness lifetime as a {@link TimeValue}.
     */
    public TimeValue getFreshnessLifetime(final ResponseCacheControl responseCacheControl, final HttpCacheEntry entry) {
        // If the cache is shared and the s-maxage response directive is present, use its value
        if (shared) {
            final long sharedMaxAge = responseCacheControl.getSharedMaxAge();
            if (sharedMaxAge > -1) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using s-maxage directive for freshness lifetime calculation: {} seconds", sharedMaxAge);
                }
                return TimeValue.ofSeconds(sharedMaxAge);
            }
        }

        // If the max-age response directive is present, use its value
        final long maxAge = responseCacheControl.getMaxAge();
        if (maxAge > -1) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using max-age directive for freshness lifetime calculation: {} seconds", maxAge);
            }
            return TimeValue.ofSeconds(maxAge);
        }

        // If the Expires response header field is present, use its value minus the value of the Date response header field
        final Instant dateValue = entry.getInstant();
        if (dateValue != null) {
            final Instant expiry = entry.getExpires();
            if (expiry != null) {
                final Duration diff = Duration.between(dateValue, expiry);
                if (diff.isNegative()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Negative freshness lifetime detected. Content is already expired. Returning zero freshness lifetime.");
                    }
                    return TimeValue.ZERO_MILLISECONDS;
                }
                return TimeValue.ofSeconds(diff.getSeconds());
            }
        }

        if (useHeuristicCaching) {
            // No explicit expiration time is present in the response. A heuristic freshness lifetime might be applicable
            if (LOG.isDebugEnabled()) {
                LOG.debug("No explicit expiration time present in the response. Using heuristic freshness lifetime calculation.");
            }
            return getHeuristicFreshnessLifetime(entry);
        } else {
            return TimeValue.ZERO_MILLISECONDS;
        }
    }

    TimeValue getHeuristicFreshnessLifetime(final HttpCacheEntry entry) {
        final Instant dateValue = entry.getInstant();
        final Instant lastModifiedValue = entry.getLastModified();

        if (dateValue != null && lastModifiedValue != null) {
            final Duration diff = Duration.between(lastModifiedValue, dateValue);

            if (diff.isNegative()) {
                return TimeValue.ZERO_MILLISECONDS;
            }
            return TimeValue.ofSeconds((long) (heuristicCoefficient * diff.getSeconds()));
        }

        return heuristicDefaultLifetime;
    }

    TimeValue getApparentAge(final HttpCacheEntry entry) {
        final Instant dateValue = entry.getInstant();
        if (dateValue == null) {
            return CacheSupport.MAX_AGE;
        }
        final Duration diff = Duration.between(dateValue, entry.getResponseInstant());
        if (diff.isNegative()) {
            return TimeValue.ZERO_MILLISECONDS;
        }
        return TimeValue.ofSeconds(diff.getSeconds());
    }

    /**
     * Extracts and processes the Age value from an HttpCacheEntry by tokenizing the Age header value.
     * The Age header value is interpreted as a sequence of tokens, and the first token is parsed into a number
     * representing the age in delta-seconds. If the first token cannot be parsed into a number, the Age value is
     * considered as invalid and this method returns 0. If the first token represents a negative number or a number
     * that exceeds Integer.MAX_VALUE, the Age value is set to MAX_AGE (in seconds).
     * This method uses CacheSupport.parseTokens to robustly handle the Age header value.
     * <p>
     * Note: If the HttpCacheEntry contains multiple Age headers, only the first one is considered.
     *
     * @param entry The HttpCacheEntry from which to extract the Age value.
     * @return The Age value in delta-seconds, or MAX_AGE in seconds if the Age value exceeds Integer.MAX_VALUE or
     * is negative. If the Age value is invalid (cannot be parsed into a number or contains non-numeric characters),
     * this method returns 0.
     */
    long getAgeValue(final HttpCacheEntry entry) {
        final Header age = entry.getFirstHeader(HttpHeaders.AGE);
        if (age != null) {
            final AtomicReference<String> firstToken = new AtomicReference<>();
            MessageSupport.parseTokens(age, token -> firstToken.compareAndSet(null, token));
            final long delta = CacheSupport.deltaSeconds(firstToken.get());
            if (delta == -1 && LOG.isDebugEnabled()) {
                LOG.debug("Malformed Age value: {}", age);
            }
            return delta > 0 ? delta : 0;
        }
        // If we've got here, there were no valid Age headers
        return 0;
    }

    TimeValue getCorrectedAgeValue(final HttpCacheEntry entry) {
        final long ageValue = getAgeValue(entry);
        final long responseDelay = getResponseDelay(entry).toSeconds();
        return TimeValue.ofSeconds(ageValue + responseDelay);
    }

    TimeValue getResponseDelay(final HttpCacheEntry entry) {
        final Duration diff = Duration.between(entry.getRequestInstant(), entry.getResponseInstant());
        return TimeValue.ofSeconds(diff.getSeconds());
    }

    TimeValue getCorrectedInitialAge(final HttpCacheEntry entry) {
        final long apparentAge = getApparentAge(entry).toSeconds();
        final long correctedReceivedAge = getCorrectedAgeValue(entry).toSeconds();
        return TimeValue.ofSeconds(Math.max(apparentAge, correctedReceivedAge));
    }

    TimeValue getResidentTime(final HttpCacheEntry entry, final Instant now) {
        final Duration diff = Duration.between(entry.getResponseInstant(), now);
        return TimeValue.ofSeconds(diff.getSeconds());
    }

}
