package org.apache.hc.client5.http.impl.async;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;

final class CompressionDictionaryFreshness {

    private CompressionDictionaryFreshness() {
    }

    static Instant determineValidUntil(
            final HttpResponse response,
            final Instant requestTime,
            final Instant responseTime) {

        final CacheControl cacheControl =
                parseCacheControl(
                        response.getFirstHeader(HttpHeaders.CACHE_CONTROL));

        if (cacheControl.noStore || cacheControl.noCache) {
            return null;
        }

        final long freshnessLifetime;

        if (cacheControl.maxAge >= 0) {
            freshnessLifetime = cacheControl.maxAge;
        } else {
            final Instant expires =
                    parseHttpDate(response.getFirstHeader(HttpHeaders.EXPIRES));
            final Instant date =
                    parseHttpDate(response.getFirstHeader(HttpHeaders.DATE));

            if (expires == null || date == null) {
                return null;
            }

            freshnessLifetime =
                    Math.max(0, Duration.between(date, expires).getSeconds());
        }

        final Instant date =
                parseHttpDate(response.getFirstHeader(HttpHeaders.DATE));

        final long apparentAge =
                date != null
                        ? Math.max(
                        0,
                        Duration.between(date, responseTime)
                                .getSeconds())
                        : 0;

        final long ageValue =
                parseAge(response.getFirstHeader(HttpHeaders.AGE));

        final long responseDelay =
                Math.max(
                        0,
                        Duration.between(requestTime, responseTime)
                                .getSeconds());

        final long correctedAgeValue =
                saturatedAdd(ageValue, responseDelay);

        final long correctedInitialAge =
                Math.max(apparentAge, correctedAgeValue);

        final long remaining =
                freshnessLifetime - correctedInitialAge;

        if (remaining <= 0) {
            return null;
        }

        return responseTime.plusSeconds(remaining);
    }

    private static long parseAge(final Header header) {
        if (header == null) {
            return 0;
        }

        try {
            return Math.max(0, Long.parseLong(header.getValue().trim()));
        } catch (final NumberFormatException ex) {
            return 0;
        }
    }

    private static Instant parseHttpDate(final Header header) {
        if (header == null) {
            return null;
        }

        try {
            return ZonedDateTime.parse(
                            header.getValue(),
                            DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    private static CacheControl parseCacheControl(final Header header) {
        final CacheControl result = new CacheControl();

        if (header == null) {
            return result;
        }

        for (final String element : header.getValue().split(",")) {
            final String directive = element.trim();

            if ("no-store".equalsIgnoreCase(directive)) {
                result.noStore = true;
            } else if ("no-cache".equalsIgnoreCase(directive)) {
                result.noCache = true;
            } else if (directive.regionMatches(
                    true, 0, "max-age=", 0, 8)) {
                try {
                    result.maxAge =
                            Math.max(
                                    0,
                                    Long.parseLong(
                                            directive.substring(8)
                                                    .trim()));
                } catch (final NumberFormatException ignore) {
                    result.maxAge = -1;
                }
            }
        }

        return result;
    }

    private static long saturatedAdd(
            final long first,
            final long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static final class CacheControl {

        private boolean noStore;
        private boolean noCache;
        private long maxAge = -1;
    }
}