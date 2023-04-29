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
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
public class LinearBackoffManager implements BackoffManager {

    private static final Logger LOG = LoggerFactory.getLogger(org.slf4j.LoggerFactory.class);

    /**
     * A ConcurrentHashMap that stores the timestamp of the last successful probe for each HttpRoute.
     * The key is the HttpRoute and the value is an Instant representing the timestamp.
     */
    private final ConcurrentHashMap<HttpRoute, Instant> lastRouteProbes;

    /**
     * A ConcurrentHashMap that stores the timestamp of the last backoff for each HttpRoute.
     * The key is the HttpRoute and the value is an Instant representing the timestamp.
     */
    private final ConcurrentHashMap<HttpRoute, Instant> lastRouteBackoffs;

    /**
     * A ConcurrentHashMap that stores the number of backoff attempts for each HttpRoute.
     * The key is the HttpRoute and the value is an AtomicInteger representing the number of attempts.
     */
    private final ConcurrentHashMap<HttpRoute, AtomicInteger> routeAttempts;

    /**
     * The cool-down time between adjustments in pool sizes for a given host.
     * This time value allows enough time for the adjustments to take effect.
     * Defaults to 5 seconds.
     */
    private final AtomicReference<TimeValue> coolDown = new AtomicReference<>(TimeValue.ofSeconds(5L));

    /**
     * The backoff increment used when adjusting connection pool sizes.
     * The pool size will be increased or decreased by this value during the backoff process.
     * The increment must be positive.
     */
    private final int increment;

    /**
     * The connection pool control used by this LinearBackoffManager to adjust
     * the maximum number of connections for each HttpRoute.
     * The control is responsible for managing the actual connection pool and its settings.
     */
    private final ConnPoolControl<HttpRoute> connPoolControl;

    /**
     * The absolute maximum per-host connection pool size to probe up to.
     * Defaults to 2 (the default per-host max as per RFC 2616 section 8.1.4).
     */
    private final AtomicInteger cap = new AtomicInteger(2); // Per RFC 2616 sec 8.1.4


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
        this.connPoolControl = Args.notNull(connPoolControl, "Connection pool control");
        this.increment = Args.positive(increment, "Increment");
        this.lastRouteProbes = new ConcurrentHashMap<>();
        this.lastRouteBackoffs = new ConcurrentHashMap<>();
        this.routeAttempts = new ConcurrentHashMap<>();
    }

    /**
     * Adjusts the maximum number of connections for the specified route, increasing it by the increment value.
     * The method ensures that adjustments only happen after the cool-down period has passed since the last adjustment.
     *
     * @param route the HttpRoute for which the maximum number of connections will be increased
     */
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

        lastRouteBackoffs.put(route, now);

        final int currentMax = connPoolControl.getMaxPerRoute(route);
        connPoolControl.setMaxPerRoute(route, currentMax + increment); // Increment max per route by the increment value

        attempt.incrementAndGet();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Backoff applied for route: {}, new max connections: {}", route, connPoolControl.getMaxPerRoute(route));
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

        lastRouteProbes.put(route, now);

        final int currentMax = connPoolControl.getMaxPerRoute(route);
        final int newMax = Math.max(currentMax - increment, cap.get()); // Ensure the new max does not go below the cap

        connPoolControl.setMaxPerRoute(route, newMax);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Probe applied for route: {}, new max connections: {}", route, connPoolControl.getMaxPerRoute(route));
        }
    }

    /**
     * Sets the cool-down time value for adjustments in pool sizes for a given host. This time value
     * allows enough time for the adjustments to take effect before further adjustments are made.
     * The cool-down time value must be positive and not null.
     *
     * @param coolDown the TimeValue representing the cool-down period between adjustments
     * @throws IllegalArgumentException if the provided cool-down time value is null or non-positive
     */
    public void setCoolDown(final TimeValue coolDown) {
        Args.notNull(coolDown, "Cool down time value cannot be null");
        Args.positive(coolDown.getDuration(), "coolDown");
        this.coolDown.set(coolDown);
    }

    /**
     * Sets the maximum number of connections allowed per host (route) to the specified value.
     * This cap acts as an upper bound on the number of connections that can be created per host
     * during the linear backoff process. The provided value must be positive.
     *
     * @param cap the maximum number of connections allowed per host (route)
     * @throws IllegalArgumentException if the provided cap value is not positive
     */
    public void setPerHostConnectionCap(final int cap) {
        Args.positive(cap, "Per host connection cap");
        this.cap.set(cap);
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
        final Instant lastProbe = lastRouteProbes.getOrDefault(route, Instant.EPOCH);
        final Instant lastBackoff = lastRouteBackoffs.getOrDefault(route, Instant.EPOCH);

        return Duration.between(lastProbe, now).compareTo(coolDown.get().toDuration()) < 0 ||
                Duration.between(lastBackoff, now).compareTo(coolDown.get().toDuration()) < 0;
    }

}