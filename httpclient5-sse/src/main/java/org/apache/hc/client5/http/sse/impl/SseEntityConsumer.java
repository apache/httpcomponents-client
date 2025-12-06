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

import static org.apache.hc.core5.http.ContentType.TEXT_EVENT_STREAM;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.entity.AbstractCharAsyncEntityConsumer;

/**
 * Internal char-level SSE consumer that converts decoded UTF-8 characters
 * into Server-Sent Events using {@link ServerSentEventReader} and forwards
 * them via {@link SseCallbacks}.
 *
 * <p><strong>Responsibilities</strong></p>
 * <ul>
 *   <li>Validates {@code Content-Type == text/event-stream}.</li>
 *   <li>Ensures UTF-8 decoding and strips optional UTF-8 BOM.</li>
 *   <li>Accumulates CR/LF-delimited lines and feeds them into
 *       {@link ServerSentEventReader}, which applies the SSE field rules
 *       (data/event/id/retry).</li>
 *   <li>Emits lifecycle and parsed events to the owning {@code EventSource}
 *       through {@link SseCallbacks}.</li>
 * </ul>
 *
 * <p><strong>Thread-safety:</strong> Not thread-safe. One instance is expected to be
 * used by a single decoding flow on an I/O thread.</p>
 *
 * @since 5.7
 */
@Internal
public final class SseEntityConsumer extends AbstractCharAsyncEntityConsumer<Void>
        implements ServerSentEventReader.Callback {

    private final SseCallbacks cb;
    private final StringBuilder partial = new StringBuilder(256);
    private ServerSentEventReader reader;
    private boolean firstChunk = true;

    public SseEntityConsumer(final SseCallbacks callbacks) {
        this.cb = callbacks;
    }

    @Override
    public void streamStart(final ContentType contentType) throws HttpException, IOException {
        final String mt = contentType != null ? contentType.getMimeType() : null;
        if (!TEXT_EVENT_STREAM.getMimeType().equalsIgnoreCase(mt)) {
            throw new HttpException("Unexpected Content-Type: " + mt);
        }
        setCharset(StandardCharsets.UTF_8);
        reader = new ServerSentEventReader(this);
        cb.onOpen();
    }

    @Override
    public void data(final CharBuffer src, final boolean endOfStream) {
        if (firstChunk) {
            firstChunk = false;
            // Strip UTF-8 BOM if present.
            if (src.remaining() >= 1 && src.get(src.position()) == '\uFEFF') {
                src.position(src.position() + 1);
            }
        }
        while (src.hasRemaining()) {
            final char c = src.get();
            if (c == '\n') {
                final int len = partial.length();
                if (len > 0 && partial.charAt(len - 1) == '\r') {
                    partial.setLength(len - 1);
                }
                reader.line(partial.toString());
                partial.setLength(0);
            } else {
                partial.append(c);
            }
        }
        if (endOfStream) {
            if (partial.length() > 0) {
                reader.line(partial.toString());
                partial.setLength(0);
            }
            // Flush any accumulated fields into a final event.
            reader.line("");
        }
    }

    @Override
    protected int capacityIncrement() {
        return 8192;
    }

    @Override
    protected Void generateContent() {
        return null;
    }

    @Override
    public void releaseResources() {
        partial.setLength(0);
        reader = null;
    }

    // ServerSentEventReader.Callback

    @Override
    public void onEvent(final String id, final String type, final String data) {
        cb.onEvent(id, type, data);
    }

    @Override
    public void onComment(final String comment) {
        // ignored
    }

    @Override
    public void onRetryChange(final long retryMs) {
        cb.onRetry(retryMs);
    }
}
