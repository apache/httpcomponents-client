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

package org.apache.hc.client5.http.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.util.Args;

/**
 * A utility class for parsing and formatting HTTP dates as used in cookies and
 * other headers.
 *
 * @since 4.3
 */
public final class DateUtils {

    /**
     * @deprecated use {@link #INTERNET_MESSAGE_FORMAT}
     */
    @Deprecated
    public static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String INTERNET_MESSAGE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    /**
     * Date formatter used to parse HTTP date headers in the Internet Message Format
     * specified by the HTTP protocol.
     *
     * @since 5.2
     */
    public static final DateTimeFormatter FORMATTER_RFC1123 = new DateTimeFormatterBuilder()
            .parseLenient()
            .parseCaseInsensitive()
            .appendPattern(INTERNET_MESSAGE_FORMAT)
            .toFormatter(Locale.ENGLISH);

    /**
     * Date format pattern used to parse HTTP date headers in RFC 1036 format.
     */
    public static final String PATTERN_RFC1036 = "EEE, dd-MMM-yy HH:mm:ss zzz";

    /**
     * Date formatter used to parse HTTP date headers in RFC 1036 format.
     *
     * @since 5.2
     */
    public static final DateTimeFormatter FORMATTER_RFC1036 = new DateTimeFormatterBuilder()
            .parseLenient()
            .parseCaseInsensitive()
            .appendPattern(PATTERN_RFC1036)
            .toFormatter(Locale.ENGLISH);

    /**
     * Date format pattern used to parse HTTP date headers in ANSI C
     * {@code asctime()} format.
     */
    public static final String PATTERN_ASCTIME = "EEE MMM d HH:mm:ss yyyy";

    /**
     * Date formatter used to parse HTTP date headers in in ANSI C {@code asctime()} format.
     *
     * @since 5.2
     */
    public static final DateTimeFormatter FORMATTER_ASCTIME = new DateTimeFormatterBuilder()
            .parseLenient()
            .parseCaseInsensitive()
            .appendPattern(PATTERN_ASCTIME)
            .toFormatter(Locale.ENGLISH);

    /**
     * Standard date formatters: {@link #FORMATTER_RFC1123}, {@link #FORMATTER_RFC1036}, {@link #FORMATTER_ASCTIME}.
     *
     * @since 5.2
     */
    public static final DateTimeFormatter[] STANDARD_PATTERNS = new DateTimeFormatter[] {
            FORMATTER_RFC1123,
            FORMATTER_RFC1036,
            FORMATTER_ASCTIME
    };

    static final ZoneId GMT_ID = ZoneId.of("GMT");

    /**
     * @since 5.2
     */
    public static Date toDate(final Instant instant) {
        return instant != null ? new Date(instant.toEpochMilli()) : null;
    }

    /**
     * @since 5.2
     */
    public static Instant toInstant(final Date date) {
        return date != null ? Instant.ofEpochMilli(date.getTime()) : null;
    }

    /**
     * @since 5.2
     */
    public static LocalDateTime toUTC(final Instant instant) {
        return instant != null ? instant.atZone(ZoneOffset.UTC).toLocalDateTime() : null;
    }

    /**
     * @since 5.2
     */
    public static LocalDateTime toUTC(final Date date) {
        return toUTC(toInstant(date));
    }

    /**
     * Parses the date value using the given date/time formats.
     *
     * @param dateValue the instant value to parse
     * @param dateFormatters the date/time formats to use
     *
     * @return the parsed instant or null if input could not be parsed
     *
     * @since 5.2
     */
    public static Instant parseDate(final String dateValue, final DateTimeFormatter... dateFormatters) {
        Args.notNull(dateValue, "Date value");
        String v = dateValue;
        // trim single quotes around date if present
        // see issue #5279
        if (v.length() > 1 && v.startsWith("'") && v.endsWith("'")) {
            v = v.substring (1, v.length() - 1);
        }

        for (final DateTimeFormatter dateFormatter : dateFormatters) {
            try {
                return Instant.from(dateFormatter.parse(v));
            } catch (final DateTimeParseException ignore) {
            }
        }
        return null;
    }

    /**
     * Parses the instant value using the standard date/time formats ({@link #PATTERN_RFC1123},
     * {@link #PATTERN_RFC1036}, {@link #PATTERN_ASCTIME}).
     *
     * @param dateValue the instant value to parse
     *
     * @return the parsed instant or null if input could not be parsed
     *
     * @since 5.2
     */
    public static Instant parseStandardDate(final String dateValue) {
        return parseDate(dateValue, STANDARD_PATTERNS);
    }

    /**
     * Parses an instant value from a header with the given name.
     *
     * @param headers message headers
     * @param headerName header name
     *
     * @return the parsed instant or null if input could not be parsed
     *
     * @since 5.2
     */
    public static Instant parseStandardDate(final MessageHeaders headers, final String headerName) {
        if (headers == null) {
            return null;
        }
        final Header header = headers.getFirstHeader(headerName);
        if (header == null) {
            return null;
        }
        return parseStandardDate(header.getValue());
    }

    /**
     * Formats the given instant according to the RFC 1123 pattern.
     *
     * @param instant Instant to format.
     * @return An RFC 1123 formatted instant string.
     *
     * @see #PATTERN_RFC1123
     *
     * @since 5.2
     */
    public static String formatStandardDate(final Instant instant) {
        return formatDate(instant, FORMATTER_RFC1123);
    }

    /**
     * Formats the given date according to the specified pattern.
     *
     * @param instant Instant to format.
     * @param dateTimeFormatter The pattern to use for formatting the instant.
     * @return A formatted instant string.
     *
     * @throws IllegalArgumentException If the given date pattern is invalid.
     *
     * @since 5.2
     */
    public static String formatDate(final Instant instant, final DateTimeFormatter dateTimeFormatter) {
        Args.notNull(instant, "Instant");
        Args.notNull(dateTimeFormatter, "DateTimeFormatter");
        return dateTimeFormatter.format(instant.atZone(GMT_ID));
    }

    /**
     * @deprecated This attribute is no longer supported as a part of the public API.
     */
    @Deprecated
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    /**
     * Parses a date value.  The formats used for parsing the date value are retrieved from
     * the default http params.
     *
     * @param dateValue the date value to parse
     *
     * @return the parsed date or null if input could not be parsed
     *
     * @deprecated Use {@link #parseStandardDate(String)}
     */
    @Deprecated
    public static Date parseDate(final String dateValue) {
        return parseDate(dateValue, null, null);
    }

    /**
     * Parses a date value from a header with the given name.
     *
     * @param headers message headers
     * @param headerName header name
     *
     * @return the parsed date or null if input could not be parsed
     *
     * @since 5.0
     *
     * @deprecated Use {@link #parseStandardDate(MessageHeaders, String)}
     */
    @Deprecated
    public static Date parseDate(final MessageHeaders headers, final String headerName) {
        return toDate(parseStandardDate(headers, headerName));
    }

    /**
     * Tests if the first message is after (newer) than second one
     * using the given message header for comparison.
     *
     * @param message1 the first message
     * @param message2 the second message
     * @param headerName header name
     *
     * @return {@code true} if both messages contain a header with the given name
     *  and the value of the header from the first message is newer that of
     *  the second message.
     *
     * @since 5.0
     *
     * @deprecated This method is no longer supported as a part of the public API.
     */
    @Deprecated
    public static boolean isAfter(
            final MessageHeaders message1,
            final MessageHeaders message2,
            final String headerName) {
        if (message1 != null && message2 != null) {
            final Header dateHeader1 = message1.getFirstHeader(headerName);
            if (dateHeader1 != null) {
                final Header dateHeader2 = message2.getFirstHeader(headerName);
                if (dateHeader2 != null) {
                    final Date date1 = parseDate(dateHeader1.getValue());
                    if (date1 != null) {
                        final Date date2 = parseDate(dateHeader2.getValue());
                        if (date2 != null) {
                            return date1.after(date2);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Tests if the first message is before (older) than the second one
     * using the given message header for comparison.
     *
     * @param message1 the first message
     * @param message2 the second message
     * @param headerName header name
     *
     * @return {@code true} if both messages contain a header with the given name
     *  and the value of the header from the first message is older that of
     *  the second message.
     *
     * @since 5.0
     *
     * @deprecated This method is no longer supported as a part of the public API.
     */
    @Deprecated
    public static boolean isBefore(
            final MessageHeaders message1,
            final MessageHeaders message2,
            final String headerName) {
        if (message1 != null && message2 != null) {
            final Header dateHeader1 = message1.getFirstHeader(headerName);
            if (dateHeader1 != null) {
                final Header dateHeader2 = message2.getFirstHeader(headerName);
                if (dateHeader2 != null) {
                    final Date date1 = parseDate(dateHeader1.getValue());
                    if (date1 != null) {
                        final Date date2 = parseDate(dateHeader2.getValue());
                        if (date2 != null) {
                            return date1.before(date2);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Parses the date value using the given date/time formats.
     *
     * @param dateValue the date value to parse
     * @param dateFormats the date/time formats to use
     *
     * @return the parsed date or null if input could not be parsed
     *
     * @deprecated Use {@link #parseDate(String, DateTimeFormatter...)}
     */
    @Deprecated
    public static Date parseDate(final String dateValue, final String[] dateFormats) {
        return parseDate(dateValue, dateFormats, null);
    }

    /**
     * Parses the date value using the given date/time formats.
     *
     * @param dateValue the date value to parse
     * @param dateFormats the date/time formats to use
     * @param startDate During parsing, two digit years will be placed in the range
     * {@code startDate} to {@code startDate + 100 years}. This value may
     * be {@code null}. When {@code null} is given as a parameter, year
     * {@code 2000} will be used.
     *
     * @return the parsed date or null if input could not be parsed
     *
     * @deprecated Use {@link #parseDate(String, DateTimeFormatter...)}
     */
    @Deprecated
    public static Date parseDate(
            final String dateValue,
            final String[] dateFormats,
            final Date startDate) {
        final DateTimeFormatter[] dateTimeFormatters;
        if (dateFormats != null) {
            dateTimeFormatters = new DateTimeFormatter[dateFormats.length];
            for (int i = 0; i < dateFormats.length; i++) {
                dateTimeFormatters[i] = new DateTimeFormatterBuilder()
                        .parseLenient()
                        .parseCaseInsensitive()
                        .appendPattern(dateFormats[i])
                        .toFormatter();
            }
        } else {
            dateTimeFormatters = STANDARD_PATTERNS;
        }
        return toDate(parseDate(dateValue, dateTimeFormatters));
    }

    /**
     * Formats the given date according to the RFC 1123 pattern.
     *
     * @param date The date to format.
     * @return An RFC 1123 formatted date string.
     *
     * @see #PATTERN_RFC1123
     *
     * @deprecated Use {@link #formatStandardDate(Instant)}
     */
    @Deprecated
    public static String formatDate(final Date date) {
        return formatStandardDate(toInstant(date));
    }

    /**
     * Formats the given date according to the specified pattern.
     *
     * @param date The date to format.
     * @param pattern The pattern to use for formatting the date.
     * @return A formatted date string.
     *
     * @throws IllegalArgumentException If the given date pattern is invalid.
     *
     * @deprecated Use {@link #formatDate(Instant, DateTimeFormatter)}
     */
    @Deprecated
    public static String formatDate(final Date date, final String pattern) {
        Args.notNull(date, "Date");
        Args.notNull(pattern, "Pattern");
        return DateTimeFormatter.ofPattern(pattern).format(toInstant(date).atZone(GMT_ID));
    }

    /**
     * Clears thread-local variable containing {@link java.text.DateFormat} cache.
     *
     * @since 4.3
     *
     * @deprecated Noop method. Do not use.
     */
    @Deprecated
    public static void clearThreadLocal() {
    }

    /** This class should not be instantiated. */
    private DateUtils() {
    }

}
