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
package org.apache.hc.client5.http.sse;

/**
 * Computes the next reconnect delay for SSE (in milliseconds).
 * <p>
 * Implementations may also override {@link #shouldReconnect(int, long, Long)}
 * to decline reconnects entirely (e.g., a "no strategy" that never reconnects).
 */
public interface BackoffStrategy {

    /**
     * @param attempt           consecutive reconnect attempt number (1-based)
     * @param previousDelayMs   last delay used (0 for first attempt)
     * @param serverRetryHintMs value from server 'retry:' (ms) or HTTP Retry-After, or null if none
     * @return delay in milliseconds, greater than or equal to {@code 0}
     */
    long nextDelayMs(int attempt, long previousDelayMs, Long serverRetryHintMs);

    /**
     * Whether a reconnect should be attempted at all.
     * Default is {@code true} for backward compatibility.
     *
     * @param attempt           consecutive reconnect attempt number (1-based)
     * @param previousDelayMs   last delay used (0 for first attempt)
     * @param serverRetryHintMs value from server 'retry:' (ms) or HTTP Retry-After, or null if none
     * @return {@code true} to reconnect, {@code false} to stop
     */
    default boolean shouldReconnect(final int attempt, final long previousDelayMs, final Long serverRetryHintMs) {
        return true;
    }
}
