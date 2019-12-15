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

import java.util.Date;
import java.util.Iterator;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.TimeValue;

class CacheValidityPolicy {

    public static final TimeValue MAX_AGE = TimeValue.ofSeconds(Integer.MAX_VALUE + 1L);

    CacheValidityPolicy() {
        super();
    }

    public TimeValue getCurrentAge(final HttpCacheEntry entry, final Date now) {
        return TimeValue.ofSeconds(getCorrectedInitialAge(entry).toSeconds() + getResidentTime(entry, now).toSeconds());
    }

    public TimeValue getFreshnessLifetime(final HttpCacheEntry entry) {
        final long maxAge = getMaxAge(entry);
        if (maxAge > -1) {
            return TimeValue.ofSeconds(maxAge);
        }

        final Date dateValue = entry.getDate();
        if (dateValue == null) {
            return TimeValue.ZERO_MILLISECONDS;
        }

        final Date expiry = DateUtils.parseDate(entry, HeaderConstants.EXPIRES);
        if (expiry == null) {
            return TimeValue.ZERO_MILLISECONDS;
        }
        final long diff = expiry.getTime() - dateValue.getTime();
        return TimeValue.ofSeconds(diff / 1000);
    }

    public boolean isResponseFresh(final HttpCacheEntry entry, final Date now) {
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
            final Date now, final float coefficient, final TimeValue defaultLifetime) {
        return getCurrentAge(entry, now).compareTo(getHeuristicFreshnessLifetime(entry, coefficient, defaultLifetime)) == -1;
    }

    public TimeValue getHeuristicFreshnessLifetime(final HttpCacheEntry entry,
            final float coefficient, final TimeValue defaultLifetime) {
        final Date dateValue = entry.getDate();
        final Date lastModifiedValue = DateUtils.parseDate(entry, HeaderConstants.LAST_MODIFIED);

        if (dateValue != null && lastModifiedValue != null) {
            final long diff = dateValue.getTime() - lastModifiedValue.getTime();
            if (diff < 0) {
                return TimeValue.ZERO_MILLISECONDS;
            }
            return TimeValue.ofSeconds((long) (coefficient * diff / 1000));
        }

        return defaultLifetime;
    }

    public boolean isRevalidatable(final HttpCacheEntry entry) {
        return entry.getFirstHeader(HeaderConstants.ETAG) != null
                || entry.getFirstHeader(HeaderConstants.LAST_MODIFIED) != null;
    }

    public boolean mustRevalidate(final HttpCacheEntry entry) {
        return hasCacheControlDirective(entry, HeaderConstants.CACHE_CONTROL_MUST_REVALIDATE);
    }

    public boolean proxyRevalidate(final HttpCacheEntry entry) {
        return hasCacheControlDirective(entry, HeaderConstants.CACHE_CONTROL_PROXY_REVALIDATE);
    }

    public boolean mayReturnStaleWhileRevalidating(final HttpCacheEntry entry, final Date now) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(entry, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (HeaderConstants.STALE_WHILE_REVALIDATE.equalsIgnoreCase(elt.getName())) {
                try {
                    // in seconds
                    final int allowedStalenessLifetime = Integer.parseInt(elt.getValue());
                    if (getStaleness(entry, now).compareTo(TimeValue.ofSeconds(allowedStalenessLifetime)) <= 0) {
                        return true;
                    }
                } catch (final NumberFormatException nfe) {
                    // skip malformed directive
                }
            }
        }

        return false;
    }

    public boolean mayReturnStaleIfError(final HttpRequest request, final HttpCacheEntry entry, final Date now) {
        final TimeValue staleness = getStaleness(entry, now);
        return mayReturnStaleIfError(request, HeaderConstants.CACHE_CONTROL, staleness)
                || mayReturnStaleIfError(entry, HeaderConstants.CACHE_CONTROL, staleness);
    }

    private boolean mayReturnStaleIfError(final MessageHeaders headers, final String name, final TimeValue staleness) {
        boolean result = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(headers, name);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (HeaderConstants.STALE_IF_ERROR.equals(elt.getName())) {
                try {
                    // in seconds
                    final int staleIfError = Integer.parseInt(elt.getValue());
                    if (staleness.compareTo(TimeValue.ofSeconds(staleIfError)) <= 0) {
                        result = true;
                        break;
                    }
                } catch (final NumberFormatException nfe) {
                    // skip malformed directive
                }
            }
        }
        return result;
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
        final Date dateValue = entry.getDate();
        if (dateValue == null) {
            return MAX_AGE;
        }
        final long diff = entry.getResponseDate().getTime() - dateValue.getTime();
        if (diff < 0L) {
            return TimeValue.ZERO_MILLISECONDS;
        }
        return TimeValue.ofSeconds(diff / 1000);
    }

    protected long getAgeValue(final HttpCacheEntry entry) {
        // This is a header value, we leave as-is
        long ageValue = 0;
        for (final Header hdr : entry.getHeaders(HeaderConstants.AGE)) {
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
        final long diff = entry.getResponseDate().getTime() - entry.getRequestDate().getTime();
        return TimeValue.ofSeconds(diff / 1000);
    }

    protected TimeValue getCorrectedInitialAge(final HttpCacheEntry entry) {
        return TimeValue.ofSeconds(getCorrectedReceivedAge(entry).toSeconds() + getResponseDelay(entry).toSeconds());
    }

    protected TimeValue getResidentTime(final HttpCacheEntry entry, final Date now) {
        final long diff = now.getTime() - entry.getResponseDate().getTime();
        return TimeValue.ofSeconds(diff / 1000);
    }

    protected long getMaxAge(final HttpCacheEntry entry) {
        // This is a header value, we leave as-is
        long maxAge = -1;
        final Iterator<HeaderElement> it = MessageSupport.iterate(entry, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (HeaderConstants.CACHE_CONTROL_MAX_AGE.equals(elt.getName()) || "s-maxage".equals(elt.getName())) {
                try {
                    final long currMaxAge = Long.parseLong(elt.getValue());
                    if (maxAge == -1 || currMaxAge < maxAge) {
                        maxAge = currMaxAge;
                    }
                } catch (final NumberFormatException nfe) {
                    // be conservative if can't parse
                    maxAge = 0;
                }
            }
        }
        return maxAge;
    }

    public boolean hasCacheControlDirective(final HttpCacheEntry entry, final String directive) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(entry, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (directive.equalsIgnoreCase(elt.getName())) {
                return true;
            }
        }
        return false;
    }

    public TimeValue getStaleness(final HttpCacheEntry entry, final Date now) {
        final TimeValue age = getCurrentAge(entry, now);
        final TimeValue freshness = getFreshnessLifetime(entry);
        if (age.compareTo(freshness) <= 0) {
            return TimeValue.ZERO_MILLISECONDS;
        }
        return TimeValue.ofSeconds(age.toSeconds() - freshness.toSeconds());
    }


}
