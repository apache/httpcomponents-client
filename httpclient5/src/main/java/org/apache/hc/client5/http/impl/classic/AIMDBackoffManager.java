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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.BackoffManager;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>The {@code AIMDBackoffManager} applies an additive increase,
 * multiplicative decrease (AIMD) to managing a dynamic limit to
 * the number of connections allowed to a given host. You may want
 * to experiment with the settings for the cooldown periods and the
 * backoff factor to get the adaptive behavior you want.</p>
 *
 * <p>Generally speaking, shorter cooldowns will lead to more steady-state
 * variability but faster reaction times, while longer cooldowns
 * will lead to more stable equilibrium behavior but slower reaction
 * times.</p>
 *
 * <p>Similarly, higher backoff factors promote greater
 * utilization of available capacity at the expense of fairness
 * among clients. Lower backoff factors allow equal distribution of
 * capacity among clients (fairness) to happen faster, at the
 * expense of having more server capacity unused in the short term.</p>
 *
 * @since 4.2
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class AIMDBackoffManager implements BackoffManager {

    private static final Logger LOG = LoggerFactory.getLogger(AIMDBackoffManager.class);


    /**
     * Represents the connection pool control object for managing
     * the maximum number of connections allowed per HTTP route.
     */
    private final ConnPoolControl<HttpRoute> connPerRoute;

    /**
     * A map that keeps track of the last time a successful
     * connection was made for each HTTP route. Used for
     * adjusting the pool size when probing.
     */
    private final Map<HttpRoute, Instant> lastRouteProbes;

    /**
     * A map that keeps track of the last time a connection
     * failure occurred for each HTTP route. Used for
     * adjusting the pool size when backing off.
     */
    private final Map<HttpRoute, Instant> lastRouteBackoffs;

    /**
     * The cool-down time between adjustments in pool sizes for a given host.
     * This time value allows enough time for the adjustments to take effect.
     * Defaults to 5 seconds.
     */
    private final AtomicReference<TimeValue> coolDown = new AtomicReference<>(TimeValue.ofSeconds(5L));

    /**
     * The factor to use when backing off; the new per-host limit will be
     * roughly the current max times this factor. {@code Math.floor} is
     * applied in the case of non-integer outcomes to ensure we actually
     * decrease the pool size. Pool sizes are never decreased below 1, however.
     * Defaults to 0.5.
     */
    private final AtomicReference<Double> backoffFactor = new AtomicReference<>(0.5);

    /**
     * The absolute maximum per-host connection pool size to probe up to.
     * Defaults to 2 (the default per-host max as per RFC 2616 section 8.1.4).
     */
    private final AtomicInteger cap = new AtomicInteger(2); // Per RFC 2616 sec 8.1.4


    /**
     * Constructs an {@code AIMDBackoffManager} with the specified
     * {@link ConnPoolControl} and {@link Clock}.
     * <p>
     * This constructor is primarily used for testing purposes, allowing the
     * injection of a custom {@link Clock} implementation.
     *
     * @param connPerRoute the {@link ConnPoolControl} that manages
     *                     per-host routing maximums
     */
    public AIMDBackoffManager(final ConnPoolControl<HttpRoute> connPerRoute) {
        this.connPerRoute = Args.notNull(connPerRoute, "Connection pool control");
        this.lastRouteProbes = new ConcurrentHashMap<>();
        this.lastRouteBackoffs = new ConcurrentHashMap<>();
    }

    @Override
    public void backOff(final HttpRoute route) {
        final int curr = connPerRoute.getMaxPerRoute(route);
        final Instant now = Instant.now();

        lastRouteBackoffs.compute(route, (r, lastUpdate) -> {
            if (lastUpdate == null || now.isAfter(lastUpdate.plus(coolDown.get().toMilliseconds(), ChronoUnit.MILLIS))) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Backoff applied for route: {}, new max connections: {}", route, connPerRoute.getMaxPerRoute(route));
                }
                connPerRoute.setMaxPerRoute(route, getBackedOffPoolSize(curr));
                return now;
            }
            return lastUpdate;
        });
    }


    /**
     * Returns the backed-off pool size based on the current pool size.
     * The new pool size is calculated as the floor of (backoffFactor * curr).
     *
     * @param curr the current pool size
     * @return the backed-off pool size, with a minimum value of 1
     */
    private int getBackedOffPoolSize(final int curr) {
        if (curr <= 1) {
            return 1;
        }
        return (int) (Math.floor(backoffFactor.get() * curr));
    }


    @Override
    public void probe(final HttpRoute route) {
        final int curr = connPerRoute.getMaxPerRoute(route);
        final int max = (curr >= cap.get()) ? cap.get() : curr + 1;
        final Instant now = Instant.now();

        lastRouteProbes.compute(route, (r, lastProbe) -> {
            if (lastProbe == null || now.isAfter(lastProbe.plus(coolDown.get().toMilliseconds(), ChronoUnit.MILLIS))) {
                final Instant lastBackoff = lastRouteBackoffs.get(r);
                if (lastBackoff == null || now.isAfter(lastBackoff.plus(coolDown.get().toMilliseconds(), ChronoUnit.MILLIS))) {
                    connPerRoute.setMaxPerRoute(route, max);
                    if (LOG.isDebugEnabled()){
                        LOG.info("Probe applied for route: {}, new max connections: {}", route, connPerRoute.getMaxPerRoute(route));
                    }
                    return now;
                }
            }
            return lastProbe;
        });
    }

    /**
     * Returns the last update time of a specific route from the provided updates map.
     *
     * @param updates the map containing the last update times
     * @param route the HttpRoute whose last update time is required
     * @return the last update time or 0L if the route is not present in the map
     */
    private long getLastUpdate(final Map<HttpRoute, Long> updates, final HttpRoute route) {
        return updates.getOrDefault(route, 0L);
    }

    /**
     * Sets the factor to use when backing off; the new
     * per-host limit will be roughly the current max times
     * this factor. {@code Math.floor} is applied in the
     * case of non-integer outcomes to ensure we actually
     * decrease the pool size. Pool sizes are never decreased
     * below 1, however. Defaults to 0.5.
     * @param d must be between 0.0 and 1.0, exclusive.
     */
    public void setBackoffFactor(final double d) {
        Args.check(d > 0.0 && d < 1.0, "Backoff factor must be 0.0 < f < 1.0");
        backoffFactor.set(d);
    }

    /**
     * Sets the amount of time to wait between adjustments in
     * pool sizes for a given host, to allow enough time for
     * the adjustments to take effect. Defaults to 5 seconds.
     * @param coolDown must be positive
     */
    public void setCoolDown(final TimeValue coolDown) {
        Args.notNull(coolDown, "Cool down time value cannot be null");
        Args.positive(coolDown.getDuration(), "coolDown");
        this.coolDown.set(coolDown);
    }

    /**
     * Sets the absolute maximum per-host connection pool size to
     * probe up to; defaults to 2 (the default per-host max).
     * @param cap must be &gt;= 1
     */
    public void setPerHostConnectionCap(final int cap) {
        Args.positive(cap, "Per host connection cap");
        this.cap.set(cap);
    }
}
