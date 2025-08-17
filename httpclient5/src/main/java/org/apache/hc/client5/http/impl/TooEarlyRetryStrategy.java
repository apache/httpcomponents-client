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
package org.apache.hc.client5.http.impl;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * Retry policy for RFC 8470 (<em>Using Early Data in HTTP</em>).
 * <p>
 * This strategy allows a single automatic retry on {@code 425 Too Early}
 * (and, optionally, on {@code 429} and {@code 503} honoring {@code Retry-After})
 * for requests that are considered replay-safe:
 * idempotent methods and, when present, repeatable entities.
 * </p>
 *
 * <p><strong>Notes</strong></p>
 * <ul>
 *   <li>On {@code 425}, the context attribute
 *   {@link #DISABLE_EARLY_DATA_ATTR} is set to {@code Boolean.TRUE}
 *   so a TLS layer with 0-RTT support can avoid early data on the retry.</li>
 *   <li>This class is thread-safe and can be reused across clients.</li>
 * </ul>
 *
 * @since 5.7
 */
public final class TooEarlyRetryStrategy implements HttpRequestRetryStrategy {

    /**
     * Context attribute key used to signal the transport/TLS layer that the next attempt
     * must not use TLS 0-RTT early data. Implementations that support early data may
     * check this flag and force a full handshake on retry.
     */
    public static final String DISABLE_EARLY_DATA_ATTR = "http.client.tls.early_data.disable";

    private final int maxRetries;
    private final boolean include429and503;
    private final HttpRequestRetryStrategy delegateForExceptions; // optional, may be null

    /**
     * Creates a strategy that retries once on {@code 425 Too Early}.
     * <p>
     * When {@code include429and503} is {@code true}, the same rules are also
     * applied to {@code 429 Too Many Requests} and {@code 503 Service Unavailable},
     * honoring {@code Retry-After} when present.
     * </p>
     *
     * @param include429and503 whether to also retry 429/503
     * @since 5.7
     */
    public TooEarlyRetryStrategy(final boolean include429and503) {
        this(1, include429and503, null);
    }

    /**
     * Creates a strategy with custom limits and optional delegation for I/O exception retries.
     *
     * @param maxRetries            maximum retry attempts for eligible status codes (recommended: {@code 1})
     * @param include429and503      whether to also retry 429/503
     * @param delegateForExceptions optional delegate to handle I/O exception retries; may be {@code null}
     * @since 5.7
     */
    public TooEarlyRetryStrategy(
            final int maxRetries,
            final boolean include429and503,
            final HttpRequestRetryStrategy delegateForExceptions) {
        this.maxRetries = Args.positive(maxRetries, "maxRetries");
        this.include429and503 = include429and503;
        this.delegateForExceptions = delegateForExceptions;
    }

    /**
     * Delegates I/O exception retry decisions to {@code delegateForExceptions} if provided;
     * otherwise returns {@code false}.
     *
     * @param request   the original request
     * @param exception I/O exception that occurred
     * @param execCount execution count (including the initial attempt)
     * @param context   HTTP context
     * @return {@code true} to retry, {@code false} otherwise
     * @since 5.7
     */
    @Override
    public boolean retryRequest(
            final HttpRequest request,
            final IOException exception,
            final int execCount,
            final HttpContext context) {
        return delegateForExceptions != null
                && delegateForExceptions.retryRequest(request, exception, execCount, context);
    }

    /**
     * Decides status-based retries for {@code 425} (and optionally {@code 429/503}).
     * <p>
     * Retries only when:
     * </p>
     * <ul>
     *   <li>{@code execCount} ≤ {@code maxRetries},</li>
     *   <li>the original method is idempotent, and</li>
     *   <li>any request entity is {@linkplain HttpEntity#isRepeatable() repeatable}.</li>
     * </ul>
     * <p>
     * On {@code 425}, sets {@link #DISABLE_EARLY_DATA_ATTR} to {@code Boolean.TRUE}
     * in the provided {@link HttpContext}.
     * </p>
     *
     * @param response  the response received
     * @param execCount execution count (including the initial attempt)
     * @param context   HTTP context (used to obtain the original request)
     * @return {@code true} if the request should be retried, {@code false} otherwise
     * @since 5.7
     */
    @Override
    public boolean retryRequest(
            final HttpResponse response,
            final int execCount,
            final HttpContext context) {

        final int code = response.getCode();
        final boolean eligible =
                code == HttpStatus.SC_TOO_EARLY || include429and503 && (code == HttpStatus.SC_TOO_MANY_REQUESTS
                        || code == HttpStatus.SC_SERVICE_UNAVAILABLE);

        if (!eligible || execCount > maxRetries) {
            return false;
        }

        final HttpRequest original = HttpCoreContext.cast(context).getRequest();
        if (original == null) {
            return false;
        }

        if (!Method.normalizedValueOf(original.getMethod()).isIdempotent()) {
            return false;
        }

        // Require repeatable entity when present (classic requests expose it via HttpEntityContainer).
        if (original instanceof HttpEntityContainer) {
            final HttpEntity entity = ((HttpEntityContainer) original).getEntity();
            if (entity != null && !entity.isRepeatable()) {
                return false;
            }
        }

        if (code == HttpStatus.SC_TOO_EARLY) {
            context.setAttribute(DISABLE_EARLY_DATA_ATTR, Boolean.TRUE);
        }

        return true;
    }

    /**
     * Computes the back-off interval from {@code Retry-After}, when present, for
     * eligible status codes.
     * <p>
     * Supports both delta-seconds and HTTP-date (RFC&nbsp;1123) formats.
     * Unparseable values and past dates yield {@link TimeValue#ZERO_MILLISECONDS}.
     * </p>
     *
     * @param response  the response
     * @param execCount execution count (including the initial attempt)
     * @param context   HTTP context (unused)
     * @return a {@link TimeValue} to wait before retrying; {@code ZERO_MILLISECONDS} if none
     * @since 5.7
     */
    @Override
    public TimeValue getRetryInterval(
            final HttpResponse response,
            final int execCount,
            final HttpContext context) {

        final int code = response.getCode();
        final boolean eligible =
                code == HttpStatus.SC_TOO_EARLY || include429and503 && (code == HttpStatus.SC_TOO_MANY_REQUESTS
                        || code == HttpStatus.SC_SERVICE_UNAVAILABLE);

        if (!eligible) {
            return TimeValue.ZERO_MILLISECONDS;
        }

        final Header h = response.getFirstHeader("Retry-After");
        if (h == null) {
            return TimeValue.ZERO_MILLISECONDS;
        }

        final String v = h.getValue().trim();

        // 1) delta-seconds
        try {
            final long seconds = Long.parseLong(v);
            if (seconds >= 0L) {
                return TimeValue.ofSeconds(seconds);
            }
        } catch (final NumberFormatException ignore) {
            // fall through to HTTP-date
        }

        // 2) HTTP-date (RFC 1123)
        try {
            final ZonedDateTime when = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
            final long millis = when.toInstant().toEpochMilli() - Instant.now().toEpochMilli();
            return millis > 0L ? TimeValue.ofMilliseconds(millis) : TimeValue.ZERO_MILLISECONDS;
        } catch (final DateTimeParseException ignore) {
            return TimeValue.ZERO_MILLISECONDS;
        }
    }

    @Override
    public String toString() {
        return "TooEarlyRetryStrategy(maxRetries=" + maxRetries +
                ", include429and503=" + include429and503 + ')';
    }
}
