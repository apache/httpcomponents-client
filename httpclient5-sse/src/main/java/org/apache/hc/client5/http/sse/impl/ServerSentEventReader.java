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

import org.apache.hc.core5.annotation.Internal;

/**
 * Minimal-allocation SSE line parser.
 *
 * <p>Notes:
 * <ul>
 *   <li>{@code line} is {@code final}; we use indices instead of reassigning.</li>
 *   <li>Field dispatch is done by length+char checks to avoid a temporary "field" string.</li>
 *   <li>{@code retry} is parsed without creating a substring; only {@code data/event/id} values
 *       create substrings when needed.</li>
 * </ul>
 */
@Internal
public final class ServerSentEventReader {

    public interface Callback {
        void onEvent(String id, String type, String data);

        void onComment(String comment);

        void onRetryChange(long retryMs);
    }

    private final Callback cb;
    private final StringBuilder data = new StringBuilder(128);
    private String type; // defaults to "message"
    private String id;

    public ServerSentEventReader(final Callback cb) {
        this.cb = cb;
    }

    public void line(final String line) {
        // Trim possible trailing CR without reallocating
        final int L0 = line.length();
        int end = L0;
        if (end > 0 && line.charAt(end - 1) == '\r') {
            end--;
        }

        if (end == 0) {
            // blank line -> dispatch accumulated event
            dispatch();
            return;
        }

        // Comment line: ":" [ " " ] comment
        if (line.charAt(0) == ':') {
            final int cStart = end > 1 && line.charAt(1) == ' ' ? 2 : 1;
            if (cStart < end) {
                cb.onComment(line.substring(cStart, end));
            } else {
                cb.onComment("");
            }
            return;
        }

        // Find colon (if any) up to 'end'
        int colon = -1;
        for (int i = 0; i < end; i++) {
            if (line.charAt(i) == ':') {
                colon = i;
                break;
            }
        }

        final int fStart = 0;
        final int fEnd = colon >= 0 ? colon : end;
        int vStart = colon >= 0 ? colon + 1 : end;
        if (vStart < end && line.charAt(vStart) == ' ') {
            vStart++;
        }

        final int fLen = fEnd - fStart;

        // Fast ASCII field dispatch (lowercase per spec)
        if (fLen == 4 &&
                line.charAt(0) == 'd' &&
                line.charAt(1) == 'a' &&
                line.charAt(2) == 't' &&
                line.charAt(3) == 'a') {

            // data: <value> (append newline; removed on dispatch)
            if (vStart <= end) {
                data.append(line, vStart, end).append('\n');
            }

        } else if (fLen == 5 &&
                line.charAt(0) == 'e' &&
                line.charAt(1) == 'v' &&
                line.charAt(2) == 'e' &&
                line.charAt(3) == 'n' &&
                line.charAt(4) == 't') {

            // event: <value>
            type = (vStart <= end) ? line.substring(vStart, end) : "";

        } else if (fLen == 2 &&
                line.charAt(0) == 'i' &&
                line.charAt(1) == 'd') {

            // id: <value>  (ignore if contains NUL per spec)
            boolean hasNul = false;
            for (int i = vStart; i < end; i++) {
                if (line.charAt(i) == '\u0000') {
                    hasNul = true;
                    break;
                }
            }
            if (!hasNul) {
                id = vStart <= end ? line.substring(vStart, end) : "";
            }

        } else if (fLen == 5 &&
                line.charAt(0) == 'r' &&
                line.charAt(1) == 'e' &&
                line.charAt(2) == 't' &&
                line.charAt(3) == 'r' &&
                line.charAt(4) == 'y') {

            // retry: <millis> (non-negative integer), parse without substring
            final long retry = parseLongAscii(line, vStart, end);
            if (retry >= 0) {
                cb.onRetryChange(retry);
            }

        } else {
            // Unknown field -> ignore
        }
    }

    private void dispatch() {
        if (data.length() == 0) {
            // spec: a blank line with no "data:" accumulates nothing -> just clear type
            type = null;
            return;
        }
        final int n = data.length();
        if (n > 0 && data.charAt(n - 1) == '\n') {
            data.setLength(n - 1);
        }
        cb.onEvent(id, type != null ? type : "message", data.toString());
        data.setLength(0);
        // id persists across events; type resets per spec
        type = null;
    }

    private static long parseLongAscii(final String s, final int start, final int end) {
        if (start >= end) {
            return -1L;
        }
        long v = 0L;
        for (int i = start; i < end; i++) {
            final char ch = s.charAt(i);
            if (ch < '0' || ch > '9') {
                return -1L;
            }
            v = v * 10L + (ch - '0');
            if (v < 0L) {
                return -1L; // overflow guard
            }
        }
        return v;
    }
}
