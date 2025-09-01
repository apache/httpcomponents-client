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
package org.apache.hc.client5.http.sse.example.performance;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free-ish log2 histogram in nanoseconds. 0..~2^63 range, 64 buckets.
 */
final class LogHistogram {
    private final LongAdder[] buckets = new LongAdder[64];

    LogHistogram() {
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new LongAdder();
        }
    }

    /**
     * Record a non-negative value in nanoseconds (negative values ignored).
     */
    void recordNanos(final long v) {
        if (v <= 0) {
            buckets[0].increment();
            return;
        }
        final int idx = 63 - Long.numberOfLeadingZeros(v);
        buckets[Math.min(idx, 63)].increment();
    }

    /**
     * Snapshot percentiles in nanoseconds.
     */
    Snapshot snapshot() {
        final long[] c = new long[64];
        long total = 0;
        for (int i = 0; i < 64; i++) {
            c[i] = buckets[i].sum();
            total += c[i];
        }
        return new Snapshot(c, total);
    }

    static final class Snapshot {
        final long[] counts;
        final long total;

        Snapshot(final long[] counts, final long total) {
            this.counts = counts;
            this.total = total;
        }

        long percentile(final double p) { // p in [0,100]
            if (total == 0) {
                return 0;
            }
            long rank = (long) Math.ceil((p / 100.0) * total);
            if (rank <= 0) {
                rank = 1;
            }
            long cum = 0;
            for (int i = 0; i < 64; i++) {
                cum += counts[i];
                if (cum >= rank) {
                    // return upper bound of bucket (approx)
                    return (i == 63) ? Long.MAX_VALUE : ((1L << (i + 1)) - 1);
                }
            }
            return (1L << 63) - 1;
        }
    }
}
