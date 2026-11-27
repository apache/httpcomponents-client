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
package org.apache.hc.core5.websocket;

import java.util.Arrays;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.Args;

/**
 * Ring buffer retaining up to the last {@code capacity} bytes appended to it. Used to
 * re-prime a permessage-deflate {@link java.util.zip.Inflater} dictionary across a
 * mid-stream reset without re-copying everything the stream has ever decoded: appending
 * costs O(length), independent of how much history the window already holds, and only
 * {@link #snapshot()} pays the O(capacity) cost of linearizing the ring.
 *
 * @since 5.7
 */
@Internal
public final class WebSocketSlidingWindow {

    /** The maximum LZ77 window size DEFLATE supports (RFC 1951), and the cap this ring buffer enforces. */
    public static final int MAX_WINDOW_SIZE = 1 << 15;

    private final byte[] ring;
    private int next;
    private int count;

    /** Creates a ring buffer that retains at most the last {@code capacity} bytes appended to it. */
    public WebSocketSlidingWindow(final int capacity) {
        this.ring = new byte[Args.positive(capacity, "capacity")];
    }

    /** Appends the first {@code length} bytes of {@code data}, discarding older bytes past capacity. */
    public void append(final byte[] data, final int length) {
        if (length <= 0) {
            return;
        }
        if (length >= ring.length) {
            System.arraycopy(data, length - ring.length, ring, 0, ring.length);
            next = 0;
            count = ring.length;
            return;
        }
        final int firstPart = Math.min(length, ring.length - next);
        System.arraycopy(data, 0, ring, next, firstPart);
        if (firstPart < length) {
            System.arraycopy(data, firstPart, ring, 0, length - firstPart);
        }
        next = (next + length) % ring.length;
        count = Math.min(ring.length, count + length);
    }

    /** Returns the retained bytes in the order they were appended, oldest first. */
    public byte[] snapshot() {
        if (count < ring.length) {
            return Arrays.copyOf(ring, count);
        }
        final byte[] out = new byte[count];
        final int tail = ring.length - next;
        System.arraycopy(ring, next, out, 0, tail);
        System.arraycopy(ring, 0, out, tail, next);
        return out;
    }

    /** Returns {@code true} if nothing has been appended since construction or the last {@link #clear()}. */
    public boolean isEmpty() {
        return count == 0;
    }

    /** Discards all retained bytes, as if newly constructed. */
    public void clear() {
        next = 0;
        count = 0;
    }
}
