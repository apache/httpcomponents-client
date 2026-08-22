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
package org.apache.hc.client5.http.impl.async;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;

/**
 * Derives, from the caching metadata of a dictionary response, the instant up to which the
 * dictionary may still be treated as a match on a later request. The calculation follows the
 * shared-cache freshness model: a freshness lifetime taken from {@code Cache-Control: max-age}
 * or, in its absence, from {@code Expires} measured against {@code Date}, reduced by the response
 * age derived from {@code Date}, {@code Age} and the request/response round trip. A
 * {@code no-store} directive makes the response non-storable and a {@code no-cache} directive
 * makes it immediately non-fresh; either directive, along with any missing or malformed input the
 * calculation relies on, yields {@code null}.
 * <p>
 * Used by the Compression Dictionary Transport machinery to decide whether a previously fetched
 * dictionary is still eligible to be offered through {@code Available-Dictionary}.
 */
final class CompressionDictionaryFreshness {

    private static final DateTimeFormatter ASCTIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseLenient()
            .parseCaseInsensitive()
            .appendPattern(DateUtils.PATTERN_ASCTIME)
            .toFormatter(Locale.ENGLISH);

    /** Not to be instantiated. */
    private CompressionDictionaryFreshness() {
    }

    /**
     * Computes the instant up to which the dictionary carried by the given response stays fresh and
     * may therefore be advertised as a match. The freshness lifetime is taken from
     * {@code Cache-Control: max-age} when present, otherwise from {@code Expires} measured against
     * {@code Date} (or against {@code responseTime} when {@code Date} is absent). A corrected initial
     * age is derived from {@code Date}, {@code Age} and the transmission delay and subtracted from the
     * freshness lifetime; the remaining seconds are added to {@code responseTime} to yield the result.
     *
     * @param response     the dictionary response whose caching headers are inspected.
     * @param requestTime  the instant the request was sent; combined with {@code responseTime} it
     *                     accounts for the transmission delay when correcting the reported age.
     * @param responseTime the instant the response was received; the freshness deadline is expressed
     *                     relative to it.
     * @return the instant until which the dictionary remains fresh, or {@code null} when the response
     *         is non-storable ({@code no-store}), immediately non-fresh ({@code no-cache} or a
     *         malformed {@code Cache-Control}), has no usable freshness lifetime, carries a malformed
     *         {@code Age}, is already stale, or when the deadline would overflow the representable
     *         range.
     */
    static Instant determineValidUntil(
            final HttpResponse response,
            final Instant requestTime,
            final Instant responseTime) {

        final CacheControl cacheControl = parseCacheControl(response.getHeaders(HttpHeaders.CACHE_CONTROL));
        if (cacheControl.noStore || cacheControl.noCache) {
            return null;
        }

        final Instant date = parseHttpDate(response.getFirstHeader(HttpHeaders.DATE));
        final long freshnessLifetime;
        if (cacheControl.maxAge >= 0) {
            freshnessLifetime = cacheControl.maxAge;
        } else {
            final Instant expires = parseHttpDate(response.getFirstHeader(HttpHeaders.EXPIRES));
            if (expires == null) {
                return null;
            }
            final Instant reference = date != null ? date : responseTime;
            freshnessLifetime = Math.max(0, Duration.between(reference, expires).getSeconds());
        }

        final long ageValue = parseAge(response.getFirstHeader(HttpHeaders.AGE));
        if (ageValue < 0) {
            return null;
        }

        final long apparentAge = date != null
                ? Math.max(0, Duration.between(date, responseTime).getSeconds())
                : 0;
        final long responseDelay = Math.max(0, Duration.between(requestTime, responseTime).getSeconds());
        final long correctedAgeValue = saturatedAdd(ageValue, responseDelay);
        final long correctedInitialAge = Math.max(apparentAge, correctedAgeValue);
        final long remaining = freshnessLifetime - correctedInitialAge;
        if (remaining <= 0) {
            return null;
        }
        try {
            return responseTime.plusSeconds(remaining);
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    /**
     * Parses the {@code Age} header. A missing header is treated as an age of zero, a well-formed
     * non-negative value is returned as seconds, and a negative or unparseable value returns
     * {@code -1} to signal that the response must be treated as non-fresh.
     *
     * @param header the {@code Age} header, or {@code null} if absent.
     * @return the age in seconds, {@code 0} when the header is absent, or {@code -1} when the value is
     *         invalid.
     */
    private static long parseAge(final Header header) {
        if (header == null) {
            return 0;
        }
        try {
            final long age = Long.parseLong(header.getValue().trim());
            return age >= 0 ? age : -1;
        } catch (final NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * Parses a date-valued header in any HTTP-date format accepted by HTTP semantics.
     *
     * @param header the date-valued header ({@code Date} or {@code Expires}), or {@code null} if absent.
     * @return the parsed instant, or {@code null} when the header is absent or its value cannot be
     *         parsed.
     */
    private static Instant parseHttpDate(final Header header) {
        if (header == null) {
            return null;
        }
        final Instant standardDate = DateUtils.parseStandardDate(header.getValue());
        if (standardDate != null) {
            return standardDate;
        }
        try {
            return LocalDateTime.parse(header.getValue(), ASCTIME_FORMATTER)
                    .toInstant(ZoneOffset.UTC);
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    /**
     * Extracts the {@code no-store}, {@code no-cache} and {@code max-age} directives from the given
     * {@code Cache-Control} headers, folding all header lines and comma-separated elements together.
     * A quoted {@code max-age} value is unquoted before parsing. A negative, unparseable or
     * self-contradicting {@code max-age} (the directive repeated with conflicting values) degrades the
     * result to an {@linkplain CacheControl#invalid() invalid} control block flagged {@code no-cache},
     * so a broken directive fails closed as non-fresh rather than being silently ignored.
     *
     * @param headers the {@code Cache-Control} headers, or {@code null} if none are present.
     * @return the parsed directives; never {@code null}.
     */
    private static CacheControl parseCacheControl(final Header[] headers) {
        final CacheControl result = new CacheControl();
        if (headers == null) {
            return result;
        }
        for (final Header header : headers) {
            final String[] elements = header.getValue().split(",");
            for (final String element : elements) {
                final String directive = element.trim();
                if ("no-store".equalsIgnoreCase(directive)) {
                    result.noStore = true;
                } else if ("no-cache".equalsIgnoreCase(directive)) {
                    result.noCache = true;
                } else if (directive.regionMatches(true, 0, "max-age=", 0, 8)) {
                    String value = directive.substring(8).trim();
                    if (value.length() > 1 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                        value = value.substring(1, value.length() - 1);
                    }
                    try {
                        final long maxAge = Long.parseLong(value);
                        if (maxAge < 0) {
                            return CacheControl.invalid();
                        }
                        if (result.maxAge >= 0 && result.maxAge != maxAge) {
                            return CacheControl.invalid();
                        }
                        result.maxAge = maxAge;
                    } catch (final NumberFormatException ex) {
                        return CacheControl.invalid();
                    }
                }
            }
        }
        return result;
    }

    /**
     * Adds two non-negative second counts, clamping to {@link Long#MAX_VALUE} instead of overflowing,
     * so that an extreme reported age combined with the transmission delay cannot wrap around into a
     * misleadingly small corrected age.
     *
     * @param first  the first summand.
     * @param second the second summand.
     * @return the sum, or {@link Long#MAX_VALUE} if the true sum would overflow.
     */
    private static long saturatedAdd(final long first, final long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    /**
     * Mutable holder for the subset of {@code Cache-Control} directives that bear on dictionary
     * freshness. A {@code maxAge} of {@code -1} means the directive was absent.
     */
    private static final class CacheControl {
        private boolean noStore;
        private boolean noCache;
        private long maxAge = -1;

        /**
         * Returns a control block that fails closed by flagging {@code no-cache}, used when a
         * {@code max-age} directive is malformed or contradictory.
         *
         * @return a {@code no-cache} control block.
         */
        static CacheControl invalid() {
            final CacheControl value = new CacheControl();
            value.noCache = true;
            return value;
        }
    }
}
