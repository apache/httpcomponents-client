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
package org.apache.hc.client5.http.impl.classic;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.BackoffManager;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of {@link BackoffManager} that uses a linear backoff strategy to adjust the maximum number
 * of connections per route in an {@link org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager}.
 * This class is designed to be thread-safe and can be used in multi-threaded environments.
 * <p>
 * The linear backoff strategy increases or decreases the maximum number of connections per route by a fixed increment
 * when backing off or probing, respectively. The adjustments are made based on a cool-down period, during which no
 * further adjustments will be made.
 * <p>
 * The {@code LinearBackoffManager} is intended to be used with a {@link org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager},
 * which provides the {@link ConnPoolControl} interface. This class interacts with the {@code PoolingHttpClientConnectionManager}
 * to adjust the maximum number of connections per route.
 * <p>
 * Example usage:
 * <pre>
 * PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
 * LinearBackoffManager backoffManager = new LinearBackoffManager(connectionManager, 1);
 * // Use the backoffManager with the connectionManager in your application
 * </pre>
 *
 * @see BackoffManager
 * @see ConnPoolControl
 * @see org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class LinearBackoffManager extends AbstractBackoff {


    private static final Logger LOG = LoggerFactory.getLogger(org.slf4j.LoggerFactory.class);

    /**
     * The backoff increment used when adjusting connection pool sizes.
     * The pool size will be increased or decreased by this value during the backoff process.
     * The increment must be positive.
     */
    private final int increment;

    private final ConcurrentHashMap<HttpRoute, AtomicInteger> routeAttempts;

    /**
     * Constructs a new LinearBackoffManager with the specified connection pool control.
     * The backoff increment is set to {@code 1} by default.
     *
     * @param connPoolControl the connection pool control to be used by this LinearBackoffManager
     */
    public LinearBackoffManager(final ConnPoolControl<HttpRoute> connPoolControl) {
        this(connPoolControl, 1);
    }

    /**
     * Constructs a new LinearBackoffManager with the specified connection pool control and backoff increment.
     *
     * @param connPoolControl the connection pool control to be used by this LinearBackoffManager
     * @param increment       the backoff increment to be used when adjusting connection pool sizes
     * @throws IllegalArgumentException if connPoolControl is {@code null} or increment is not positive
     */
    public LinearBackoffManager(final ConnPoolControl<HttpRoute> connPoolControl, final int increment) {
        super(connPoolControl);
        this.increment = Args.positive(increment, "Increment");
        routeAttempts = new ConcurrentHashMap<>();
    }


    @Override
    public void backOff(final HttpRoute route) {
        final Instant now = Instant.now();

        if (shouldSkip(route, now)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("BackOff not applied for route: {}, cool-down period not elapsed", route);
            }
            return;
        }

        final AtomicInteger attempt = routeAttempts.compute(route, (r, oldValue) -> {
            if (oldValue == null) {
                return new AtomicInteger(1);
            }
            oldValue.incrementAndGet();
            return oldValue;
        });

        getLastRouteBackoffs().put(route, now);

        final int currentMax = getConnPerRoute().getMaxPerRoute(route);
        getConnPerRoute().setMaxPerRoute(route, getBackedOffPoolSize(currentMax));

        attempt.incrementAndGet();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Backoff applied for route: {}, new max connections: {}", route, getConnPerRoute().getMaxPerRoute(route));
        }
    }

    /**
     * Adjusts the maximum number of connections for the specified route, decreasing it by the increment value.
     * The method ensures that adjustments only happen after the cool-down period has passed since the last adjustment.
     *
     * @param route the HttpRoute for which the maximum number of connections will be decreased
     */
    @Override
    public void probe(final HttpRoute route) {
        final Instant now = Instant.now();

        if (shouldSkip(route, now)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Probe not applied for route: {}, cool-down period not elapsed", route);
            }
            return;
        }

        routeAttempts.compute(route, (r, oldValue) -> {
            if (oldValue == null || oldValue.get() <= 1) {
                return null;
            }
            oldValue.decrementAndGet();
            return oldValue;
        });

        getLastRouteProbes().put(route, now);

        final int currentMax = getConnPerRoute().getMaxPerRoute(route);
        final int newMax = Math.max(currentMax - increment, getCap().get()); // Ensure the new max does not go below the cap

        getConnPerRoute().setMaxPerRoute(route, newMax);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Probe applied for route: {}, new max connections: {}", route, getConnPerRoute().getMaxPerRoute(route));
        }
    }

    /**
     * Determines whether an adjustment action (backoff or probe) should be skipped for the given HttpRoute based on the cool-down period.
     * If the time elapsed since the last successful probe or backoff for the given route is less than the cool-down
     * period, the method returns true. Otherwise, it returns false.
     * <p>
     * This method is used by both backOff() and probe() methods to enforce the cool-down period before making adjustments
     * to the connection pool size.
     *
     * @param route the {@link HttpRoute} to check
     * @param now   the current {@link Instant} used to calculate the time since the last probe or backoff
     * @return true if the cool-down period has not elapsed since the last probe or backoff, false otherwise
     */
    private boolean shouldSkip(final HttpRoute route, final Instant now) {
        final Instant lastProbe = getLastRouteProbes().getOrDefault(route, Instant.EPOCH);
        final Instant lastBackoff = getLastRouteBackoffs().getOrDefault(route, Instant.EPOCH);

        return Duration.between(lastProbe, now).compareTo(getCoolDown().get().toDuration()) < 0 ||
                Duration.between(lastBackoff, now).compareTo(getCoolDown().get().toDuration()) < 0;
    }


    /**
     * Returns the new pool size after applying the linear backoff algorithm.
     * The new pool size is calculated by adding the increment value to the current pool size.
     *
     * @param curr the current pool size
     * @return the new pool size after applying the linear backoff
     */
    @Override
    protected int getBackedOffPoolSize(final int curr) {
        return curr + increment;
    }


    /**
     * This method is not used in LinearBackoffManager's implementation.
     * It is provided to fulfill the interface requirement and for potential future extensions or modifications
     * of LinearBackoffManager that may use the backoff factor.
     *
     * @param d the backoff factor, not used in the current implementation
     */
    @Override
    public void setBackoffFactor(final double d) {
        // Intentionally empty, as the backoff factor is not used in LinearBackoffManager
    }

}