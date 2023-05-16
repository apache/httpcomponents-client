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
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.util.TimeValue;

class CacheValidityPolicy {

    public static final TimeValue MAX_AGE = TimeValue.ofSeconds(Integer.MAX_VALUE + 1L);

    CacheValidityPolicy() {
        super();
    }


    public TimeValue getCurrentAge(final HttpCacheEntry entry, final Instant now) {
        return TimeValue.ofSeconds(getCorrectedInitialAge(entry).toSeconds() + getResidentTime(entry, now).toSeconds());
    }

    public TimeValue getFreshnessLifetime(final HttpCacheEntry entry) {
        final long maxAge = getMaxAge(entry);
        if (maxAge > -1) {
            return TimeValue.ofSeconds(maxAge);
        }

        final Instant dateValue = entry.getInstant();
        if (dateValue == null) {
            return TimeValue.ZERO_MILLISECONDS;
        }

        final Instant expiry = DateUtils.parseStandardDate(entry, HttpHeaders.EXPIRES);
        if (expiry == null) {
            return TimeValue.ZERO_MILLISECONDS;
        }
        final Duration diff = Duration.between(dateValue, expiry);
        return TimeValue.ofSeconds(diff.getSeconds());
    }

    public boolean isResponseFresh(final HttpCacheEntry entry, final Instant now) {
        return getCurrentAge(entry, now).compareTo(getFreshnessLifetime(entry)) == -1;
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
     * @param coefficient Part of the heuristic for cache entry freshness
     * @param defaultLifetime How long can I assume a cache entry is default TTL
     * @return {@code true} if the response is fresh
     */
    public boolean isResponseHeuristicallyFresh(final HttpCacheEntry entry,
            final Instant now, final float coefficient, final TimeValue defaultLifetime) {
        return getCurrentAge(entry, now).compareTo(getHeuristicFreshnessLifetime(entry, coefficient, defaultLifetime)) == -1;
    }

    public TimeValue getHeuristicFreshnessLifetime(final HttpCacheEntry entry,
            final float coefficient, final TimeValue defaultLifetime) {
        final Instant dateValue = entry.getInstant();
        final Instant lastModifiedValue = DateUtils.parseStandardDate(entry, HttpHeaders.LAST_MODIFIED);

        if (dateValue != null && lastModifiedValue != null) {
            final Duration diff = Duration.between(lastModifiedValue, dateValue);

            if (diff.isNegative()) {
                return TimeValue.ZERO_MILLISECONDS;
            }
            return TimeValue.ofSeconds((long) (coefficient * diff.getSeconds()));
        }

        return defaultLifetime;
    }

    public boolean isRevalidatable(final HttpCacheEntry entry) {
        return entry.getFirstHeader(HttpHeaders.ETAG) != null
                || entry.getFirstHeader(HttpHeaders.LAST_MODIFIED) != null;
    }

    public boolean mustRevalidate(final HttpCacheEntry entry) {
        final ResponseCacheControl cacheControl = CacheControlHeaderParser.INSTANCE.parse(entry);
        return cacheControl.isMustRevalidate();
    }

    public boolean proxyRevalidate(final HttpCacheEntry entry) {
        final ResponseCacheControl cacheControl = CacheControlHeaderParser.INSTANCE.parse(entry);
        return cacheControl.isProxyRevalidate();
    }

    public boolean mayReturnStaleWhileRevalidating(final HttpCacheEntry entry, final Instant now) {
        final ResponseCacheControl cacheControl = CacheControlHeaderParser.INSTANCE.parse(entry);
        if (cacheControl.getStaleWhileRevalidate() >= 0) {
            final TimeValue staleness = getStaleness(entry, now);
            if (staleness.compareTo(TimeValue.ofSeconds(cacheControl.getStaleWhileRevalidate())) <= 0) {
                return true;
            }
        }
        return false;
    }

    public boolean mayReturnStaleIfError(final HttpRequest request, final HttpCacheEntry entry, final Instant now) {
        final RequestCacheControl requestCacheControl = CacheControlHeaderParser.INSTANCE.parse(request);
        final ResponseCacheControl cacheControl = CacheControlHeaderParser.INSTANCE.parse(entry);
        final TimeValue staleness = getStaleness(entry, now);
        return mayReturnStaleIfError(requestCacheControl, staleness) || mayReturnStaleIfError(cacheControl, staleness);
    }

    private boolean mayReturnStaleIfError(final CacheControl cacheControl, final TimeValue staleness) {
        return cacheControl.getStaleIfError() >= 0 && staleness.compareTo(TimeValue.ofSeconds(cacheControl.getStaleIfError())) <= 0;
    }

    /**
     * This matters for deciding whether the cache entry is valid to serve as a
     * response. If these values do not match, we might have a partial response
     *
     * @param entry The cache entry we are currently working with
     * @return boolean indicating whether actual length matches Content-Length
     */
    protected boolean contentLengthHeaderMatchesActualLength(final HttpCacheEntry entry) {
        final Header h = entry.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        if (h != null) {
            try {
                final long responseLen = Long.parseLong(h.getValue());
                final Resource resource = entry.getResource();
                if (resource == null) {
                    return false;
                }
                final long resourceLen = resource.length();
                return responseLen == resourceLen;
            } catch (final NumberFormatException ex) {
                return false;
            }
        }
        return true;
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


    protected long getMaxAge(final HttpCacheEntry entry) {
        final ResponseCacheControl cacheControl = CacheControlHeaderParser.INSTANCE.parse(entry);
        final long maxAge = cacheControl.getMaxAge();
        final long sharedMaxAge = cacheControl.getSharedMaxAge();
        if (sharedMaxAge == -1) {
            return maxAge;
        } else if (maxAge == -1) {
            return sharedMaxAge;
        } else {
            return Math.min(maxAge, sharedMaxAge);
        }
    }

    public TimeValue getStaleness(final HttpCacheEntry entry, final Instant now) {
        final TimeValue age = getCurrentAge(entry, now);
        final TimeValue freshness = getFreshnessLifetime(entry);
        if (age.compareTo(freshness) <= 0) {
            return TimeValue.ZERO_MILLISECONDS;
        }
        return TimeValue.ofSeconds(age.toSeconds() - freshness.toSeconds());
    }


}
