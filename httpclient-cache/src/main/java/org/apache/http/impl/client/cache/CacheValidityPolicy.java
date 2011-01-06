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
package org.apache.http.impl.client.cache;

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.protocol.HTTP;

/**
 * @since 4.1
 */
@Immutable
class CacheValidityPolicy {

    public static final long MAX_AGE = 2147483648L;

    CacheValidityPolicy() {
        super();
    }

    public long getCurrentAgeSecs(final HttpCacheEntry entry, Date now) {
        return getCorrectedInitialAgeSecs(entry) + getResidentTimeSecs(entry, now);
    }

    public long getFreshnessLifetimeSecs(final HttpCacheEntry entry) {
        long maxage = getMaxAge(entry);
        if (maxage > -1)
            return maxage;

        Date dateValue = getDateValue(entry);
        if (dateValue == null)
            return 0L;

        Date expiry = getExpirationDate(entry);
        if (expiry == null)
            return 0;
        long diff = expiry.getTime() - dateValue.getTime();
        return (diff / 1000);
    }

    public boolean isResponseFresh(final HttpCacheEntry entry, Date now) {
        return (getCurrentAgeSecs(entry, now) < getFreshnessLifetimeSecs(entry));
    }

    /**
     * Decides if this response is fresh enough based Last-Modified and Date, if available.
     * This entry is meant to be used when isResponseFresh returns false.  The algorithm is as follows:
     *
     * if last-modified and date are defined, freshness lifetime is coefficient*(date-lastModified),
     * else freshness lifetime is defaultLifetime
     *
     * @param entry
     * @param now
     * @param coefficient
     * @param defaultLifetime
     * @return {@code true} if the response is fresh
     */
    public boolean isResponseHeuristicallyFresh(final HttpCacheEntry entry,
            Date now, float coefficient, long defaultLifetime) {
        return (getCurrentAgeSecs(entry, now) < getHeuristicFreshnessLifetimeSecs(entry, coefficient, defaultLifetime));
    }

    public long getHeuristicFreshnessLifetimeSecs(HttpCacheEntry entry,
            float coefficient, long defaultLifetime) {
        Date dateValue = getDateValue(entry);
        Date lastModifiedValue = getLastModifiedValue(entry);

        if (dateValue != null && lastModifiedValue != null) {
            long diff = dateValue.getTime() - lastModifiedValue.getTime();
            if (diff < 0)
                return 0;
            return (long)(coefficient * (diff / 1000));
        }

        return defaultLifetime;
    }

    public boolean isRevalidatable(final HttpCacheEntry entry) {
        return entry.getFirstHeader(HeaderConstants.ETAG) != null
                || entry.getFirstHeader(HeaderConstants.LAST_MODIFIED) != null;
    }

    public boolean mustRevalidate(final HttpCacheEntry entry) {
        return hasCacheControlDirective(entry, "must-revalidate");
    }

    public boolean proxyRevalidate(final HttpCacheEntry entry) {
        return hasCacheControlDirective(entry, "proxy-revalidate");
    }
    
    public boolean mayReturnStaleWhileRevalidating(final HttpCacheEntry entry, Date now) {
        for (Header h : entry.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if ("stale-while-revalidate".equalsIgnoreCase(elt.getName())) {
                    try {
                        int allowedStalenessLifetime = Integer.parseInt(elt.getValue());
                        if (getStalenessSecs(entry, now) <= allowedStalenessLifetime) {
                            return true;
                        }
                    } catch (NumberFormatException nfe) {
                        // skip malformed directive
                    }
                }
            }
        }
        
        return false;
    }
    
    public boolean mayReturnStaleIfError(HttpRequest request,
            HttpCacheEntry entry, Date now) {
        long stalenessSecs = getStalenessSecs(entry, now);
        return mayReturnStaleIfError(request.getHeaders("Cache-Control"),
                                     stalenessSecs)
                || mayReturnStaleIfError(entry.getHeaders("Cache-Control"),
                                         stalenessSecs);
    }
    
    private boolean mayReturnStaleIfError(Header[] headers, long stalenessSecs) {
        boolean result = false;
        for(Header h : headers) {
            for(HeaderElement elt : h.getElements()) {
                if ("stale-if-error".equals(elt.getName())) {
                    try {
                        int staleIfErrorSecs = Integer.parseInt(elt.getValue());
                        if (stalenessSecs <= staleIfErrorSecs) {
                            result = true;
                            break;
                        }
                    } catch (NumberFormatException nfe) {
                        // skip malformed directive
                    }
                }
            }
        }
        return result;
    }

    protected Date getDateValue(final HttpCacheEntry entry) {
        Header dateHdr = entry.getFirstHeader(HTTP.DATE_HEADER);
        if (dateHdr == null)
            return null;
        try {
            return DateUtils.parseDate(dateHdr.getValue());
        } catch (DateParseException dpe) {
            // ignore malformed date
        }
        return null;
    }

    protected Date getLastModifiedValue(final HttpCacheEntry entry) {
        Header dateHdr = entry.getFirstHeader(HeaderConstants.LAST_MODIFIED);
        if (dateHdr == null)
            return null;
        try {
            return DateUtils.parseDate(dateHdr.getValue());
        } catch (DateParseException dpe) {
            // ignore malformed date
        }
        return null;
    }

    protected long getContentLengthValue(final HttpCacheEntry entry) {
        Header cl = entry.getFirstHeader(HTTP.CONTENT_LEN);
        if (cl == null)
            return -1;

        try {
            return Long.parseLong(cl.getValue());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * This matters for deciding whether the cache entry is valid to serve as a
     * response. If these values do not match, we might have a partial response
     *
     * @return boolean indicating whether actual length matches Content-Length
     */
    protected boolean contentLengthHeaderMatchesActualLength(final HttpCacheEntry entry) {
        return getContentLengthValue(entry) == entry.getResource().length();
    }

    protected long getApparentAgeSecs(final HttpCacheEntry entry) {
        Date dateValue = getDateValue(entry);
        if (dateValue == null)
            return MAX_AGE;
        long diff = entry.getResponseDate().getTime() - dateValue.getTime();
        if (diff < 0L)
            return 0;
        return (diff / 1000);
    }

    protected long getAgeValue(final HttpCacheEntry entry) {
        long ageValue = 0;
        for (Header hdr : entry.getHeaders(HeaderConstants.AGE)) {
            long hdrAge;
            try {
                hdrAge = Long.parseLong(hdr.getValue());
                if (hdrAge < 0) {
                    hdrAge = MAX_AGE;
                }
            } catch (NumberFormatException nfe) {
                hdrAge = MAX_AGE;
            }
            ageValue = (hdrAge > ageValue) ? hdrAge : ageValue;
        }
        return ageValue;
    }

    protected long getCorrectedReceivedAgeSecs(final HttpCacheEntry entry) {
        long apparentAge = getApparentAgeSecs(entry);
        long ageValue = getAgeValue(entry);
        return (apparentAge > ageValue) ? apparentAge : ageValue;
    }

    protected long getResponseDelaySecs(final HttpCacheEntry entry) {
        long diff = entry.getResponseDate().getTime() - entry.getRequestDate().getTime();
        return (diff / 1000L);
    }

    protected long getCorrectedInitialAgeSecs(final HttpCacheEntry entry) {
        return getCorrectedReceivedAgeSecs(entry) + getResponseDelaySecs(entry);
    }

    protected Date getCurrentDate() {
        return new Date();
    }

    protected long getResidentTimeSecs(HttpCacheEntry entry, Date now) {
        long diff = now.getTime() - entry.getResponseDate().getTime();
        return (diff / 1000L);
    }

    protected long getMaxAge(final HttpCacheEntry entry) {
        long maxage = -1;
        for (Header hdr : entry.getHeaders(HeaderConstants.CACHE_CONTROL)) {
            for (HeaderElement elt : hdr.getElements()) {
                if (HeaderConstants.CACHE_CONTROL_MAX_AGE.equals(elt.getName())
                        || "s-maxage".equals(elt.getName())) {
                    try {
                        long currMaxAge = Long.parseLong(elt.getValue());
                        if (maxage == -1 || currMaxAge < maxage) {
                            maxage = currMaxAge;
                        }
                    } catch (NumberFormatException nfe) {
                        // be conservative if can't parse
                        maxage = 0;
                    }
                }
            }
        }
        return maxage;
    }

    protected Date getExpirationDate(final HttpCacheEntry entry) {
        Header expiresHeader = entry.getFirstHeader(HeaderConstants.EXPIRES);
        if (expiresHeader == null)
            return null;
        try {
            return DateUtils.parseDate(expiresHeader.getValue());
        } catch (DateParseException dpe) {
            // malformed expires header
        }
        return null;
    }

    public boolean hasCacheControlDirective(final HttpCacheEntry entry,
            final String directive) {
        for (Header h : entry.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if (directive.equalsIgnoreCase(elt.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public long getStalenessSecs(HttpCacheEntry entry, Date now) {
        long age = getCurrentAgeSecs(entry, now);
        long freshness = getFreshnessLifetimeSecs(entry);
        if (age <= freshness) return 0L;
        return (age - freshness);
    }


}
