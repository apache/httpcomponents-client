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

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CacheValidityPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(CacheValidityPolicy.class);


    public static final TimeValue MAX_AGE = TimeValue.ofSeconds(Integer.MAX_VALUE + 1L);


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
        final long sharedMaxAge = responseCacheControl.getSharedMaxAge();
        if (sharedMaxAge > -1) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using s-maxage directive for freshness lifetime calculation: {} seconds", sharedMaxAge);
            }
            return TimeValue.ofSeconds(sharedMaxAge);
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
            final Instant expiry = DateUtils.parseStandardDate(entry, HttpHeaders.EXPIRES);
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

        // No explicit expiration time is present in the response. A heuristic freshness lifetime might be applicable
        if (LOG.isDebugEnabled()) {
            LOG.debug("No explicit expiration time present in the response. Using heuristic freshness lifetime calculation.");
        }
        return getHeuristicFreshnessLifetime(entry);
    }

    public boolean isResponseFresh(final ResponseCacheControl responseCacheControl, final HttpCacheEntry entry,
                                   final Instant now) {
        return getCurrentAge(entry, now).compareTo(getFreshnessLifetime(responseCacheControl, entry)) == -1;
    }

    /**
     * Decides if this response is fresh enough based Last-Modified and Date, if available.
     * This entry is meant to be used when isResponseFresh returns false.
     *
     * The algorithm is as follows:
     * if last-modified and date are defined, freshness lifetime is coefficient*(date-lastModified),
     * else freshness lifetime is defaultLifetime
     *
     * @param entry the cache entry
     * @param now what time is it currently (When is right NOW)
     * @return {@code true} if the response is fresh
     */
    public boolean isResponseHeuristicallyFresh(final HttpCacheEntry entry, final Instant now) {
        return getCurrentAge(entry, now).compareTo(getHeuristicFreshnessLifetime(entry)) == -1;
    }

    public TimeValue getHeuristicFreshnessLifetime(final HttpCacheEntry entry) {
        final Instant dateValue = entry.getInstant();
        final Instant lastModifiedValue = DateUtils.parseStandardDate(entry, HttpHeaders.LAST_MODIFIED);

        if (dateValue != null && lastModifiedValue != null) {
            final Duration diff = Duration.between(lastModifiedValue, dateValue);

            if (diff.isNegative()) {
                return TimeValue.ZERO_MILLISECONDS;
            }
            return TimeValue.ofSeconds((long) (heuristicCoefficient * diff.getSeconds()));
        }

        return heuristicDefaultLifetime;
    }

    public boolean isRevalidatable(final HttpCacheEntry entry) {
        return entry.getFirstHeader(HttpHeaders.ETAG) != null
                || entry.getFirstHeader(HttpHeaders.LAST_MODIFIED) != null;
    }

    public boolean mayReturnStaleWhileRevalidating(final ResponseCacheControl responseCacheControl,
                                                   final HttpCacheEntry entry, final Instant now) {
        if (responseCacheControl.getStaleWhileRevalidate() >= 0) {
            final TimeValue staleness = getStaleness(responseCacheControl, entry, now);
            if (staleness.compareTo(TimeValue.ofSeconds(responseCacheControl.getStaleWhileRevalidate())) <= 0) {
                return true;
            }
        }
        return false;
    }

    public boolean mayReturnStaleIfError(final RequestCacheControl requestCacheControl,
                                         final ResponseCacheControl responseCacheControl, final HttpCacheEntry entry,
                                         final Instant now) {
        final TimeValue staleness = getStaleness(responseCacheControl, entry, now);
        return mayReturnStaleIfError(requestCacheControl, staleness) ||
                mayReturnStaleIfError(responseCacheControl, staleness);
    }

    private boolean mayReturnStaleIfError(final CacheControl responseCacheControl, final TimeValue staleness) {
        return responseCacheControl.getStaleIfError() >= 0 &&
                staleness.compareTo(TimeValue.ofSeconds(responseCacheControl.getStaleIfError())) <= 0;
    }

    protected TimeValue getApparentAge(final HttpCacheEntry entry) {
        final Instant dateValue = entry.getInstant();
        if (dateValue == null) {
            return MAX_AGE;
        }
        final Duration diff = Duration.between(dateValue, entry.getResponseInstant());
        if (diff.isNegative()) {
            return TimeValue.ZERO_MILLISECONDS;
        }
        return TimeValue.ofSeconds(diff.getSeconds());
    }

    protected long getAgeValue(final HttpCacheEntry entry) {
        // This is a header value, we leave as-is
        long ageValue = 0;
        for (final Header hdr : entry.getHeaders(HttpHeaders.AGE)) {
            long hdrAge;
            try {
                hdrAge = Long.parseLong(hdr.getValue());
                if (hdrAge < 0) {
                    hdrAge = MAX_AGE.toSeconds();
                }
            } catch (final NumberFormatException nfe) {
                hdrAge = MAX_AGE.toSeconds();
            }
            ageValue = (hdrAge > ageValue) ? hdrAge : ageValue;
        }
        return ageValue;
    }

    protected TimeValue getCorrectedReceivedAge(final HttpCacheEntry entry) {
        final TimeValue apparentAge = getApparentAge(entry);
        final long ageValue = getAgeValue(entry);
        return (apparentAge.toSeconds() > ageValue) ? apparentAge : TimeValue.ofSeconds(ageValue);
    }

    protected TimeValue getResponseDelay(final HttpCacheEntry entry) {
        final Duration diff = Duration.between(entry.getRequestInstant(), entry.getResponseInstant());
        return TimeValue.ofSeconds(diff.getSeconds());
    }

    protected TimeValue getCorrectedInitialAge(final HttpCacheEntry entry) {
        return TimeValue.ofSeconds(getCorrectedReceivedAge(entry).toSeconds() + getResponseDelay(entry).toSeconds());
    }

    protected TimeValue getResidentTime(final HttpCacheEntry entry, final Instant now) {
        final Duration diff = Duration.between(entry.getResponseInstant(), now);
        return TimeValue.ofSeconds(diff.getSeconds());
    }


    protected long getMaxAge(final ResponseCacheControl responseCacheControl) {
        final long maxAge = responseCacheControl.getMaxAge();
        final long sharedMaxAge = responseCacheControl.getSharedMaxAge();
        if (sharedMaxAge == -1) {
            return maxAge;
        } else if (maxAge == -1) {
            return sharedMaxAge;
        } else {
            return Math.min(maxAge, sharedMaxAge);
        }
    }

    public TimeValue getStaleness(final ResponseCacheControl responseCacheControl, final HttpCacheEntry entry, final Instant now) {
        final TimeValue age = getCurrentAge(entry, now);
        final TimeValue freshness = getFreshnessLifetime(responseCacheControl, entry);
        if (age.compareTo(freshness) <= 0) {
            return TimeValue.ZERO_MILLISECONDS;
        }
        return TimeValue.ofSeconds(age.toSeconds() - freshness.toSeconds());
    }


}
