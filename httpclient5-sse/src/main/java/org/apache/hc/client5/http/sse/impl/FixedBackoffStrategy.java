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

import org.apache.hc.client5.http.sse.BackoffStrategy;
import org.apache.hc.client5.http.sse.EventSourceConfig;

/**
 * Backoff strategy that uses a fixed delay between reconnect attempts and
 * honors server-provided retry hints when present.
 *
 * <p>If the server supplies a hint (via the SSE {@code retry:} field or an HTTP
 * {@code Retry-After} header converted to milliseconds), that value is used
 * for the next delay. Otherwise, the constant delay configured at construction
 * time is returned.</p>
 *
 * <h3>Characteristics</h3>
 * <ul>
 *   <li>Immutable and thread-safe.</li>
 *   <li>Ignores {@code attempt} and {@code previousDelayMs} (kept for API symmetry).</li>
 *   <li>Negative inputs are coerced to {@code 0} ms.</li>
 * </ul>
 *
 * @see BackoffStrategy
 * @see EventSourceConfig
 * @since 5.7
 */
public final class FixedBackoffStrategy implements BackoffStrategy {

    /**
     * Constant delay (milliseconds) to use when no server hint is present.
     */
    private final long delayMs;

    /**
     * Creates a fixed-delay backoff strategy.
     *
     * @param delayMs constant delay in milliseconds (negative values are coerced to {@code 0})
     * @since 5.7
     */
    public FixedBackoffStrategy(final long delayMs) {
        this.delayMs = Math.max(0L, delayMs);
    }

    /**
     * Returns the next delay.
     *
     * <p>If {@code serverRetryHintMs} is non-{@code null}, that value is used
     * (coerced to {@code >= 0}). Otherwise, returns the configured constant delay.</p>
     *
     * @param attempt           consecutive reconnect attempt number (ignored)
     * @param previousDelayMs   last delay used (ignored)
     * @param serverRetryHintMs server-provided retry delay in ms, or {@code null}
     * @return delay in milliseconds (always {@code >= 0})
     * @since 5.7
     */
    @Override
    public long nextDelayMs(final int attempt, final long previousDelayMs, final Long serverRetryHintMs) {
        if (serverRetryHintMs != null) {
            return Math.max(0L, serverRetryHintMs);
        }
        return delayMs;
    }
}
