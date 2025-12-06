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

import java.util.concurrent.ThreadLocalRandom;

import org.apache.hc.client5.http.sse.BackoffStrategy;

/**
 * Backoff strategy that computes exponential delays with <em>full jitter</em> and
 * honors server retry hints when provided.
 *
 * <p><strong>Algorithm</strong> (when no server hint is present):</p>
 * <ol>
 *   <li>Compute the exponential cap: {@code cap = clamp(baseMs * factor^(attempt-1))}.</li>
 *   <li>Pick a uniformly distributed random value in {@code [0, cap]} (full jitter).</li>
 *   <li>Clamp the result into {@code [minMs, maxMs]}.</li>
 * </ol>
 *
 * <p><strong>Server hints</strong>:</p>
 * <ul>
 *   <li>If {@code serverRetryHintMs} is non-{@code null}, that value is used directly
 *       (clamped to {@code [minMs, maxMs]}), ignoring the exponential step.</li>
 * </ul>
 *
 * <p>This strategy is stateless and thread-safe.</p>
 *
 * @since 5.7
 */
public final class ExponentialJitterBackoff implements BackoffStrategy {

    /**
     * Base delay (milliseconds) used for the first attempt. Must be &ge; 1.
     */
    private final long baseMs;

    /**
     * Maximum delay (milliseconds). Always &ge; {@link #baseMs}.
     */
    private final long maxMs;

    /**
     * Minimum delay (milliseconds). Always &ge; 0.
     */
    private final long minMs;

    /**
     * Exponential factor. Must be &ge; 1.0.
     */
    private final double factor;

    /**
     * Creates a new exponential+jitter backoff strategy.
     *
     * @param baseMs base delay in milliseconds for attempt 1 (will be coerced to &ge; 1)
     * @param maxMs  maximum delay in milliseconds (will be coerced to &ge; baseMs)
     * @param factor exponential growth factor (will be coerced to &ge; 1.0)
     * @param minMs  minimum delay in milliseconds (will be coerced to &ge; 0)
     * @since 5.7
     */
    public ExponentialJitterBackoff(final long baseMs, final long maxMs, final double factor, final long minMs) {
        this.baseMs = Math.max(1, baseMs);
        this.maxMs = Math.max(this.baseMs, maxMs);
        this.factor = Math.max(1.0, factor);
        this.minMs = Math.max(0, minMs);
    }

    /**
     * Computes the next reconnect delay in milliseconds.
     *
     * <p>If {@code serverRetryHintMs} is non-{@code null}, that value wins (after clamping).
     * Otherwise the delay is drawn uniformly at random from {@code [0, cap]}, where
     * {@code cap = clamp(min(maxMs, round(baseMs * factor^(attempt-1))))}.</p>
     *
     * <p><strong>Notes</strong>:</p>
     * <ul>
     *   <li>{@code attempt} is 1-based. Values &lt; 1 are treated as 1.</li>
     *   <li>{@code previousDelayMs} is accepted for API symmetry but not used by this strategy.</li>
     *   <li>The returned value is always in {@code [minMs, maxMs]}.</li>
     * </ul>
     *
     * @param attempt           consecutive reconnect attempt number (1-based)
     * @param previousDelayMs   last delay used (ignored by this implementation)
     * @param serverRetryHintMs value from server {@code retry:} (ms) or HTTP {@code Retry-After}
     *                          converted to ms; {@code null} if none
     * @return delay in milliseconds (&ge; 0)
     * @since 5.7
     */
    @Override
    public long nextDelayMs(final int attempt, final long previousDelayMs, final Long serverRetryHintMs) {
        if (serverRetryHintMs != null) {
            return clamp(serverRetryHintMs);
        }
        final int a = Math.max(1, attempt);
        final double exp = Math.pow(factor, a - 1);
        final long cap = clamp(Math.min(maxMs, Math.round(baseMs * exp)));
        final long jitter = ThreadLocalRandom.current().nextLong(cap + 1L); // full jitter in [0, cap]
        return Math.max(minMs, jitter);
    }

    private long clamp(final long x) {
        return Math.max(minMs, Math.min(maxMs, x));
    }
}
