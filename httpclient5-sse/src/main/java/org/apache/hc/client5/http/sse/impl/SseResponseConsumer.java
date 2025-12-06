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
package org.apache.hc.client5.http.sse.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.LongConsumer;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Internal response consumer that bridges an HTTP response to an SSE entity consumer.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Validate that the status is {@code 200 OK}; otherwise propagate a failure.</li>
 *   <li>Extract and pass a {@code Retry-After} hint (seconds or RFC-1123 date) to the caller
 *       via the provided {@link LongConsumer}.</li>
 *   <li>Treat {@code 204 No Content} as a terminal close (no reconnect), signaled with
 *       {@link StopReconnectException}.</li>
 * </ul>
 *
 * <p>This class is used internally by {@code DefaultEventSource}.</p>
 *
 * @since 5.7
 */
@Internal
public final class SseResponseConsumer implements AsyncResponseConsumer<Void> {

    private final AsyncEntityConsumer<Void> entity;
    private final LongConsumer retryHintSink; // may be null

    /**
     * Signals that the server requested a terminal close (no reconnect).
     */
    static final class StopReconnectException extends HttpException {
        StopReconnectException(final String msg) {
            super(msg);
        }
    }

    public SseResponseConsumer(final AsyncEntityConsumer<Void> entity, final LongConsumer retryHintSink) {
        this.entity = entity;
        this.retryHintSink = retryHintSink;
    }

    @Override
    public void consumeResponse(final HttpResponse rsp, final EntityDetails ed, final HttpContext ctx,
                                final FutureCallback<Void> cb)
            throws HttpException, IOException {
        final int code = rsp.getCode();
        if (code != HttpStatus.SC_OK) {
            final Header h = rsp.getFirstHeader(HttpHeaders.RETRY_AFTER);
            if (h != null && retryHintSink != null) {
                final long ms = parseRetryAfterMillis(h.getValue());
                if (ms >= 0) {
                    retryHintSink.accept(ms);
                }
            }
            if (code == HttpStatus.SC_NO_CONTENT) { // 204 => do not reconnect
                throw new StopReconnectException("Server closed stream (204)");
            }
            throw new HttpException("Unexpected status: " + code);
        }
        entity.streamStart(ed, cb);
    }

    @Override
    public void informationResponse(final HttpResponse response, final HttpContext context) {
        // no-op
    }

    @Override
    public void updateCapacity(final CapacityChannel channel) throws IOException {
        entity.updateCapacity(channel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        entity.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        entity.streamEnd(trailers);
    }

    @Override
    public void failed(final Exception cause) {
        entity.failed(cause);
    }

    @Override
    public void releaseResources() {
        entity.releaseResources();
    }

    /**
     * Parses an HTTP {@code Retry-After} header value into milliseconds.
     * Accepts either a positive integer (seconds) or an RFC-1123 date.
     *
     * @return milliseconds to wait, or {@code -1} if unparseable.
     */
    private static long parseRetryAfterMillis(final String v) {
        final String s = v != null ? v.trim() : "";
        try {
            final long sec = Long.parseLong(s);
            return sec >= 0 ? sec * 1000L : -1L;
        } catch (final NumberFormatException ignore) {
            try {
                final ZonedDateTime t = ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME);
                final long ms = Duration.between(ZonedDateTime.now(ZoneOffset.UTC), t).toMillis();
                return Math.max(0L, ms);
            } catch (final Exception ignore2) {
                return -1L;
            }
        }
    }
}
