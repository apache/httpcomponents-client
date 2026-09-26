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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
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

    /**
     * Not to be instantiated.
     */
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
     * @param request      the dictionary request whose {@code no-store} directive is inspected,
     *                     or {@code null} when request metadata is unavailable.
     * @param response     the dictionary response whose caching headers are inspected.
     * @param requestTime  the instant the request was sent; combined with {@code responseTime} it
     *                     accounts for the transmission delay when correcting the reported age.
     * @param responseTime the instant the response was received; the freshness deadline is expressed
     *                     relative to it.
     * @return the instant until which the dictionary remains fresh, or {@code null} when the response
     * is non-storable ({@code no-store}), immediately non-fresh ({@code no-cache} or a
     * malformed {@code Cache-Control}), has no usable freshness lifetime, carries a malformed
     * {@code Age}, is already stale, or when the deadline would overflow the representable
     * range.
     */
    static Instant determineValidUntil(
            final HttpRequest request,
            final HttpResponse response,
            final Instant requestTime,
            final Instant responseTime) {

        if (request != null) {
            final CacheControl requestCacheControl = parseCacheControl(
                    request.getHeaders(HttpHeaders.CACHE_CONTROL));
            if (requestCacheControl.noStore) {
                return null;
            }
        }

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

    static Instant determineValidUntil(
            final HttpResponse response,
            final Instant requestTime,
            final Instant responseTime) {
        return determineValidUntil(null, response, requestTime, responseTime);
    }

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

    private static CacheControl parseCacheControl(final Header[] headers) {
        final CacheControl result = new CacheControl();
        if (headers == null) {
            return result;
        }
        for (final Header header : headers) {
            final List<String> elements = splitDirectives(header.getValue());
            if (elements == null) {
                return CacheControl.invalid();
            }
            for (final String element : elements) {
                final String directive = element.trim();
                if (directive.isEmpty()) {
                    continue;
                }
                final int equals = directive.indexOf('=');
                final String name = (equals >= 0
                        ? directive.substring(0, equals)
                        : directive).trim();
                if (!isToken(name)) {
                    return CacheControl.invalid();
                }
                if ("no-store".equalsIgnoreCase(name)) {
                    result.noStore = true;
                } else if ("no-cache".equalsIgnoreCase(name)) {
                    result.noCache = true;
                } else if ("max-age".equalsIgnoreCase(name)) {
                    if (equals < 0 || result.maxAgeSeen) {
                        return CacheControl.invalid();
                    }
                    result.maxAgeSeen = true;
                    final long maxAge = parseDeltaSeconds(directive.substring(equals + 1));
                    if (maxAge < 0) {
                        return CacheControl.invalid();
                    }
                    result.maxAge = maxAge;
                }
            }
        }
        return result;
    }

    private static List<String> splitDirectives(final String value) {
        if (value == null) {
            return null;
        }
        final List<String> result = new ArrayList<>();
        int start = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (quoted && ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                result.add(value.substring(start, i));
                start = i + 1;
            }
        }
        if (quoted || escaped) {
            return null;
        }
        result.add(value.substring(start));
        return result;
    }

    private static long parseDeltaSeconds(final String input) {
        String value = input.trim();
        if (value.length() >= 2 && value.charAt(0) == '"'
                && value.charAt(value.length() - 1) == '"') {
            value = unescapeQuotedString(value.substring(1, value.length() - 1));
            if (value == null) {
                return -1;
            }
        } else if (value.indexOf('"') >= 0) {
            return -1;
        }
        if (value.isEmpty()) {
            return -1;
        }
        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch < '0' || ch > '9') {
                return -1;
            }
            final int digit = ch - '0';
            if (result > (Long.MAX_VALUE - digit) / 10) {
                return Long.MAX_VALUE;
            }
            result = result * 10 + digit;
        }
        return result;
    }

    private static String unescapeQuotedString(final String value) {
        final StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (escaped) {
                result.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return null;
            } else {
                result.append(ch);
            }
        }
        return escaped ? null : result.toString();
    }

    private static boolean isToken(final String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (!(ch >= 'a' && ch <= 'z'
                    || ch >= 'A' && ch <= 'Z'
                    || ch >= '0' && ch <= '9'
                    || ch == '!' || ch == '#' || ch == '$' || ch == '%'
                    || ch == '&' || ch == '\'' || ch == '*' || ch == '+'
                    || ch == '-' || ch == '.' || ch == '^' || ch == '_'
                    || ch == '`' || ch == '|' || ch == '~')) {
                return false;
            }
        }
        return true;
    }

    private static long saturatedAdd(final long first, final long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static final class CacheControl {
        private boolean noStore;
        private boolean noCache;
        private boolean maxAgeSeen;
        private long maxAge = -1;

        static CacheControl invalid() {
            final CacheControl value = new CacheControl();
            value.noCache = true;
            return value;
        }
    }
}
