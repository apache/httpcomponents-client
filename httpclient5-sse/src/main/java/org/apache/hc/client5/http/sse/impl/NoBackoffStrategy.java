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

/**
 * Backoff policy that <strong>never reconnects</strong>.
 *
 * <p>Use this when you want a single-shot SSE connection: after the stream ends
 * (or fails), no reconnect attempts will be made.</p>
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li>{@link #shouldReconnect(int, long, Long)} always returns {@code false}.</li>
 *   <li>{@link #nextDelayMs(int, long, Long)} returns {@code 0} but is ignored
 *       by callers because {@code shouldReconnect(..)} is {@code false}.</li>
 * </ul>
 *
 * <p>Stateless and thread-safe.</p>
 *
 * @since 5.7
 */
public final class NoBackoffStrategy implements BackoffStrategy {

    /**
     * Always returns {@code 0}. This value is ignored because
     * {@link #shouldReconnect(int, long, Long)} returns {@code false}.
     *
     * @param attempt           consecutive reconnect attempt number (unused)
     * @param previousDelayMs   last delay used (unused)
     * @param serverRetryHintMs server-provided retry hint (unused)
     * @return {@code 0}
     * @since 5.7
     */
    @Override
    public long nextDelayMs(final int attempt, final long previousDelayMs, final Long serverRetryHintMs) {
        return 0L; // ignored since shouldReconnect(..) is false
    }

    /**
     * Always returns {@code false}: no reconnects will be attempted.
     *
     * @param attempt           consecutive reconnect attempt number (unused)
     * @param previousDelayMs   last delay used (unused)
     * @param serverRetryHintMs server-provided retry hint (unused)
     * @return {@code false}
     * @since 5.7
     */
    @Override
    public boolean shouldReconnect(final int attempt, final long previousDelayMs, final Long serverRetryHintMs) {
        return false;
    }
}
