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

import java.util.Objects;

import org.apache.hc.client5.http.sse.impl.ExponentialJitterBackoff;

/**
 * Immutable configuration for {@link EventSource} behavior, primarily covering
 * reconnect policy and limits.
 *
 * <p>Use {@link #builder()} to create instances. If you do not provide a config,
 * implementations will typically use {@link #DEFAULT}.</p>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@link #backoff}: strategy that decides if/when to reconnect, and the delay between attempts.
 *       The strategy can also honor server hints (SSE {@code retry:} field or HTTP {@code Retry-After}).</li>
 *   <li>{@link #maxReconnects}: maximum number of reconnect attempts before giving up.
 *       A value of {@code -1} means unlimited attempts.</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * <p>Instances are immutable and thread-safe.</p>
 *
 * @since 5.7
 */
public final class EventSourceConfig {

    /**
     * Reconnect decision and delay computation.
     *
     * <p>See {@link BackoffStrategy} for the contract. The default is
     * {@link ExponentialJitterBackoff} with base=1000 ms, max=30000 ms, factor=2.0, min=250 ms.</p>
     *
     * @since 5.7
     */
    public final BackoffStrategy backoff;

    /**
     * Maximum number of reconnect attempts.
     * <ul>
     *   <li>{@code -1}: unlimited reconnects.</li>
     *   <li>{@code 0}: never reconnect.</li>
     *   <li>{@code >0}: number of attempts after the initial connect.</li>
     * </ul>
     *
     * @since 5.7
     */
    public final int maxReconnects;

    /**
     * Default configuration:
     * <ul>
     *   <li>{@link #backoff} = {@code new ExponentialJitterBackoff(1000, 30000, 2.0, 250)}</li>
     *   <li>{@link #maxReconnects} = {@code -1} (unlimited)</li>
     * </ul>
     *
     * @since 5.7
     */
    public static final EventSourceConfig DEFAULT = builder().build();

    private EventSourceConfig(final BackoffStrategy backoff, final int maxReconnects) {
        this.backoff = backoff;
        this.maxReconnects = maxReconnects;
    }

    /**
     * Creates a new builder initialized with sensible defaults.
     *
     * @return a new {@link Builder}
     * @since 5.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EventSourceConfig}.
     *
     * <p>Not thread-safe.</p>
     *
     * @since 5.7
     */
    public static final class Builder {
        private BackoffStrategy backoff =
                new ExponentialJitterBackoff(1_000L, 30_000L, 2.0, 250L);
        private int maxReconnects = -1;

        /**
         * Sets the reconnect/backoff strategy.
         *
         * @param backoff non-null strategy implementation (e.g., {@link ExponentialJitterBackoff}
         *          or a custom {@link BackoffStrategy})
         * @return this builder
         * @since 5.7
         */
        public Builder backoff(final BackoffStrategy backoff) {
            this.backoff = Objects.requireNonNull(backoff, "backoff");
            return this;
        }

        /**
         * Sets the maximum number of reconnect attempts.
         *
         * <p>Use {@code -1} for unlimited; {@code 0} to disable reconnects.</p>
         *
         * @param nmaxReconnects max attempts
         * @return this builder
         * @since 5.7
         */
        public Builder maxReconnects(final int nmaxReconnects) {
            this.maxReconnects = nmaxReconnects;
            return this;
        }

        /**
         * Builds an immutable {@link EventSourceConfig}.
         *
         * @return the config
         * @since 5.7
         */
        public EventSourceConfig build() {
            return new EventSourceConfig(backoff, maxReconnects);
        }
    }
}
