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
package org.apache.hc.client5.http.impl.schedule;

import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * An implementation that backs off exponentially based on the number of
 * consecutive failed attempts. It uses the following defaults:
 * <pre>
 *      no delay in case it was never tried or didn't fail so far
 *     6 s delay for one failed attempt (= {@link #getInitialExpiry()})
 *    60 s delay for two failed attempts
 *  10 min delay for three failed attempts
 * 100 min delay for four failed attempts
 *   ~16 h delay for five failed attempts
 *    24 h delay for six or more failed attempts (= {@link #getMaxExpiry()})
 * </pre>
 *
 * The following equation is used to calculate the delay for a specific pending operation:
 * <pre>
 *     delay = {@link #getInitialExpiry()} * Math.pow({@link #getBackOffRate()},
 *     {@code consecutiveFailedAttempts} - 1))
 * </pre>
 * The resulting delay won't exceed {@link #getMaxExpiry()}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class ExponentialBackOffSchedulingStrategy implements SchedulingStrategy {

    public static final long DEFAULT_BACK_OFF_RATE = 10;
    public static final TimeValue DEFAULT_INITIAL_EXPIRY = TimeValue.ofSeconds(6);
    public static final TimeValue DEFAULT_MAX_EXPIRY = TimeValue.ofSeconds(86400);

    private static final ExponentialBackOffSchedulingStrategy INSTANCE = new ExponentialBackOffSchedulingStrategy(
            DEFAULT_BACK_OFF_RATE, DEFAULT_INITIAL_EXPIRY, DEFAULT_MAX_EXPIRY);

    private final long backOffRate;
    private final TimeValue initialExpiry;
    private final TimeValue maxExpiry;

    public ExponentialBackOffSchedulingStrategy(
            final long backOffRate,
            final TimeValue initialExpiry,
            final TimeValue maxExpiry) {
        this.backOffRate = Args.notNegative(backOffRate, "BackOff rate");
        this.initialExpiry = Args.notNull(initialExpiry, "Initial expiry");
        this.maxExpiry = Args.notNull(maxExpiry, "Max expiry");
    }

    public ExponentialBackOffSchedulingStrategy(final long backOffRate, final TimeValue initialExpiry) {
        this(backOffRate, initialExpiry, DEFAULT_MAX_EXPIRY);
    }

    public ExponentialBackOffSchedulingStrategy(final long backOffRate) {
        this(backOffRate, DEFAULT_INITIAL_EXPIRY, DEFAULT_MAX_EXPIRY);
    }

    public ExponentialBackOffSchedulingStrategy() {
        this(DEFAULT_BACK_OFF_RATE, DEFAULT_INITIAL_EXPIRY, DEFAULT_MAX_EXPIRY);
    }

    @Override
    public TimeValue schedule(final int attemptNumber) {
        return calculateDelay(attemptNumber);
    }

    public long getBackOffRate() {
        return backOffRate;
    }

    public TimeValue getInitialExpiry() {
        return initialExpiry;
    }

    public TimeValue getMaxExpiry() {
        return maxExpiry;
    }

    protected TimeValue calculateDelay(final int consecutiveFailedAttempts) {
        if (consecutiveFailedAttempts > 0) {
            final long delay = (long) (initialExpiry.toMilliseconds() * Math.pow(backOffRate, consecutiveFailedAttempts - 1));
            return TimeValue.ofMilliseconds(Math.min(delay, maxExpiry.toMilliseconds()));
        }
        return TimeValue.ZERO_MILLISECONDS;
    }

}
