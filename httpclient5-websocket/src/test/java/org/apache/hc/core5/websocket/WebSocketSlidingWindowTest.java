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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class WebSocketSlidingWindowTest {

    @Test
    void newWindowIsEmpty() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(8);
        assertTrue(w.isEmpty());
        assertArrayEquals(new byte[0], w.snapshot());
    }

    @Test
    void constructorRejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new WebSocketSlidingWindow(0));
    }

    @Test
    void appendBelowCapacity_snapshotIsExactlyWhatWasAppended() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(8);
        w.append(new byte[]{1, 2, 3}, 3);
        assertArrayEquals(new byte[]{1, 2, 3}, w.snapshot());
    }

    @Test
    void appendAtOrAboveCapacityInOneChunk_keepsOnlyLastCapacityBytesOfThatChunk() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(4);
        final byte[] chunk = {1, 2, 3, 4, 5, 6};
        w.append(chunk, chunk.length);
        // Must be the last 4 bytes of THIS chunk (3,4,5,6), not stale bytes from before it.
        assertArrayEquals(new byte[]{3, 4, 5, 6}, w.snapshot());
    }

    @Test
    void appendAtOrAboveCapacity_afterPriorAppend_dropsStaleBytes() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(4);
        w.append(new byte[]{9, 9}, 2);
        // next=2, count=2 before this chunk lands; it must still fully replace the ring.
        final byte[] chunk = {1, 2, 3, 4, 5, 6};
        w.append(chunk, chunk.length);
        assertArrayEquals(new byte[]{3, 4, 5, 6}, w.snapshot(), "stale bytes from before the big chunk must not survive");
    }

    @Test
    void appendExactlyAtCapacity_snapshotIsExactlyThatChunk() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(4);
        w.append(new byte[]{1, 2, 3, 4}, 4);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, w.snapshot());
    }

    @Test
    void appendSequenceThatWraps_snapshotStaysOldestFirst() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(4);
        w.append(new byte[]{1, 2, 3}, 3);
        // next=3, count=3; this append wraps past the end of the ring (index 3, then 0).
        w.append(new byte[]{4, 5}, 2);
        // Ring now holds, oldest to newest: 2, 3, 4, 5 (1 fell off first).
        assertArrayEquals(new byte[]{2, 3, 4, 5}, w.snapshot());
    }

    @Test
    void snapshotWithRingFullyWrappedMultipleTimes_isOldestFirst() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(4);
        for (int i = 1; i <= 10; i++) {
            w.append(new byte[]{(byte) i}, 1);
        }
        // Only the last 4 of 1..10 survive: 7, 8, 9, 10, oldest first.
        assertArrayEquals(new byte[]{7, 8, 9, 10}, w.snapshot());
    }

    @Test
    void appendMoreThanCapacityAcrossSeveralChunks_keepsLastCapacityBytesOverall() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(32768);
        final byte[] first = new byte[20000];
        Arrays.fill(first, (byte) 'A');
        final byte[] second = new byte[20000];
        Arrays.fill(second, (byte) 'B');
        w.append(first, first.length);
        w.append(second, second.length);
        final byte[] snapshot = w.snapshot();
        assertTrue(snapshot.length == 32768, "snapshot must be capped at capacity, was " + snapshot.length);
        // Last 12768 bytes of "first" survive, followed by all 20000 bytes of "second".
        final byte[] expected = new byte[32768];
        Arrays.fill(expected, 0, 12768, (byte) 'A');
        Arrays.fill(expected, 12768, 32768, (byte) 'B');
        assertArrayEquals(expected, snapshot);
    }

    @Test
    void clearResetsToFreshState() {
        final WebSocketSlidingWindow w = new WebSocketSlidingWindow(4);
        w.append(new byte[]{1, 2, 3, 4, 5}, 5);
        w.clear();
        assertTrue(w.isEmpty());
        assertArrayEquals(new byte[0], w.snapshot());
        // Reusable after clear(), not left in a wrapped state that corrupts future appends.
        w.append(new byte[]{9}, 1);
        assertArrayEquals(new byte[]{9}, w.snapshot());
    }
}
