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
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;

/**
 * Low-allocation binary consumer for Server-Sent Events (SSE).
 *
 * <p>This consumer parses {@code text/event-stream} responses directly from a
 * {@link ByteBuffer} without intermediate {@code char[]} conversion. It performs
 * ASCII field matching in-place, accumulates lines until a blank line is reached,
 * then emits one logical SSE event via the supplied {@link SseCallbacks}.</p>
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li>Validates {@code Content-Type} equals {@code text/event-stream}
 *       in {@link #streamStart(ContentType)}; otherwise throws {@link HttpException}.</li>
 *   <li>Strips a UTF-8 BOM if present in the first chunk.</li>
 *   <li>Accepts LF and CRLF line endings; tolerates CRLF split across buffers.</li>
 *   <li>Implements WHATWG SSE fields: {@code data}, {@code id}, {@code event}, {@code retry}.
 *       Unknown fields and malformed {@code retry} values are ignored.</li>
 *   <li>At end of stream, flushes any partially accumulated line and forces a final
 *       dispatch of the current event if it has data.</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * <p>Instances are not thread-safe and are intended to be used by a single I/O thread
 * per HTTP message, as per {@link AbstractBinAsyncEntityConsumer} contract.</p>
 *
 * <p><strong>Internal:</strong> this type is not part of the public API and may change
 * without notice.</p>
 *
 */
@Internal
public final class ByteSseEntityConsumer extends AbstractBinAsyncEntityConsumer<Void> {

    private static final byte LF = (byte) '\n';
    private static final byte CR = (byte) '\r';
    private static final byte COLON = (byte) ':';
    private static final byte SPACE = (byte) ' ';

    private final SseCallbacks cb;

    // line accumulator
    private byte[] lineBuf = new byte[256];
    private int lineLen = 0;

    // event accumulator
    private final StringBuilder data = new StringBuilder(256);
    private String id;
    private String type; // defaults to "message"

    // Robust BOM skipper (works across multiple chunks)
    // Matches 0xEF 0xBB 0xBF at the very beginning of the stream
    private int bomMatched = 0;      // 0..3 bytes matched so far
    private boolean bomDone = false; // once true, no further BOM detection

    public ByteSseEntityConsumer(final SseCallbacks callbacks) {
        this.cb = callbacks;
    }

    @Override
    public void streamStart(final ContentType contentType) throws HttpException, IOException {
        final String mt = contentType != null ? contentType.getMimeType() : null;
        if (!"text/event-stream".equalsIgnoreCase(mt)) {
            throw new HttpException("Unexpected Content-Type: " + mt);
        }
        cb.onOpen();
    }

    @Override
    protected void data(final ByteBuffer src, final boolean endOfStream) {
        if (!bomDone) {
            while (src.hasRemaining() && bomMatched < 3) {
                final int expected = (bomMatched == 0) ? 0xEF : (bomMatched == 1 ? 0xBB : 0xBF);
                final int b = src.get() & 0xFF;
                if (b == expected) {
                    bomMatched++;
                    if (bomMatched == 3) {
                        // Full BOM consumed, mark as done and proceed
                        bomDone = true;
                    }
                    continue;
                }
                if (bomMatched > 0) {
                    appendByte((byte) 0xEF);
                    if (bomMatched >= 2) {
                        appendByte((byte) 0xBB);
                    }
                }
                appendByte((byte) b);
                bomMatched = 0;
                bomDone = true;
                break; // drop into normal loop below for the rest of 'src'
            }
            if (!bomDone && !src.hasRemaining()) {
                if (endOfStream) {
                    flushEndOfStream();
                }
                return;
            }
        }

        while (src.hasRemaining()) {
            final byte b = src.get();
            if (b == LF) {
                int len = lineLen;
                if (len > 0 && lineBuf[len - 1] == CR) {
                    len--;
                }
                handleLine(lineBuf, len);
                lineLen = 0;
            } else {
                appendByte(b);
            }
        }

        if (endOfStream) {
            flushEndOfStream();
        }
    }

    private void flushEndOfStream() {
        if (lineLen > 0) {
            int len = lineLen;
            if (lineBuf[len - 1] == CR) {
                len--;
            }
            handleLine(lineBuf, len);
            lineLen = 0;
        }
        handleLine(lineBuf, 0);
    }

    private void appendByte(final byte b) {
        ensureCapacity(lineLen + 1);
        lineBuf[lineLen++] = b;
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
        lineBuf = new byte[0];
        data.setLength(0);
        id = null;
        type = null;
        bomMatched = 0;
        bomDone = false;
    }

    private void handleLine(final byte[] buf, final int len) {
        if (len == 0) {
            dispatch();
            return;
        }
        if (buf[0] == (byte) ':') {
            // comment -> ignore
            return;
        }
        int colon = -1;
        for (int i = 0; i < len; i++) {
            if (buf[i] == COLON) {
                colon = i;
                break;
            }
        }
        final int fEnd = colon >= 0 ? colon : len;
        int vStart = colon >= 0 ? colon + 1 : len;
        if (vStart < len && buf[vStart] == SPACE) {
            vStart++;
        }

        final int fLen = fEnd; // since field starts at 0

        // Compare ASCII field name without allocations
        if (fLen == 4 && buf[0] == 'd' && buf[1] == 'a' && buf[2] == 't' && buf[3] == 'a') {
            final String v = new String(buf, vStart, len - vStart, StandardCharsets.UTF_8);
            data.append(v).append('\n');
        } else if (fLen == 5 && buf[0] == 'e' && buf[1] == 'v' && buf[2] == 'e' && buf[3] == 'n' && buf[4] == 't') {
            type = new String(buf, vStart, len - vStart, StandardCharsets.UTF_8);
        } else if (fLen == 2 && buf[0] == 'i' && buf[1] == 'd') {
            // ignore if contains NUL per spec
            boolean hasNul = false;
            for (int i = vStart; i < len; i++) {
                if (buf[i] == 0) {
                    hasNul = true;
                    break;
                }
            }
            if (!hasNul) {
                id = new String(buf, vStart, len - vStart, StandardCharsets.UTF_8);
            }
        } else if (fLen == 5 && buf[0] == 'r' && buf[1] == 'e' && buf[2] == 't' && buf[3] == 'r' && buf[4] == 'y') {
            final long retry = parseLongAscii(buf, vStart, len - vStart);
            if (retry >= 0) {
                cb.onRetry(retry);
            }
        }
    }

    private void dispatch() {
        if (data.length() == 0) {
            type = null;
            return;
        }
        final int n = data.length();
        if (n > 0 && data.charAt(n - 1) == '\n') {
            data.setLength(n - 1);
        }
        cb.onEvent(id, type != null ? type : "message", data.toString());
        data.setLength(0);
        type = null; // id persists
    }

    private void ensureCapacity(final int cap) {
        if (cap <= lineBuf.length) {
            return;
        }
        int n = lineBuf.length << 1;
        if (n < cap) {
            n = cap;
        }
        final byte[] nb = new byte[n];
        System.arraycopy(lineBuf, 0, nb, 0, lineLen);
        lineBuf = nb;
    }

    private static long parseLongAscii(final byte[] arr, final int off, final int len) {
        if (len <= 0) {
            return -1L;
        }
        long v = 0L;
        for (int i = 0; i < len; i++) {
            final int d = arr[off + i] - '0';
            if (d < 0 || d > 9) {
                return -1L;
            }
            v = v * 10L + d;
            if (v < 0) {
                return -1L;
            }
        }
        return v;
    }
}
