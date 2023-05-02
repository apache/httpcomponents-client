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
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A backoff manager implementation that uses an exponential backoff algorithm to adjust the maximum
 * number of connections per HTTP route. The algorithm reduces the number of connections in response
 * to adverse events, such as connection failures, and gradually increases the number of connections
 * when the route is operating without issues.
 *
 * <p>This implementation is specifically designed for managing connections in an HTTP route context
 * and provides methods for probing and backing off connections based on the performance of the route.
 *
 * <p>The exponential backoff algorithm is primarily implemented in the {@code getBackedOffPoolSize}
 * method, which calculates the new connection pool size based on the current pool size, growth rate,
 * and the number of time intervals.
 *
 * @since 5.3
 */
public class ExponentialBackoffManager implements BackoffManager {

    private static final Logger LOG = LoggerFactory.getLogger(ExponentialBackoffManager.class);

    /**
     * Connection pool control responsible for managing the maximum number of connections per HTTP route.
     */
    private final ConnPoolControl<HttpRoute> connPerRoute;

    /**
     * A map that stores the last probe timestamp for each HTTP route.
     */
    private final Map<HttpRoute, Instant> lastRouteProbes;

    /**
     * A map that stores the last backoff timestamp for each HTTP route.
     */
    private final Map<HttpRoute, Instant> lastRouteBackoffs;

    /**
     * The cool-down period after which the backoff or probe process can be performed again.
     */
    private final AtomicReference<TimeValue> coolDown = new AtomicReference<>(TimeValue.ofSeconds(5L));

    /**
     * The growth rate used in the exponential backoff algorithm.
     */
    private final AtomicReference<Double> growthRate = new AtomicReference<>(0.5);

    /**
     * The per-host connection cap, as defined in RFC 2616 sec 8.1.4.
     */
    private final AtomicInteger cap = new AtomicInteger(2);

    /**
     * The number of time intervals used in the exponential backoff algorithm.
     */
    private final AtomicInteger timeInterval = new AtomicInteger(0);

    /**
     * Constructs a new ExponentialBackoffManager with the specified connection pool control.
     *
     * @param connPerRoute the connection pool control to be used for managing connections
     * @throws IllegalArgumentException if connPerRoute is null
     */
    public ExponentialBackoffManager(final ConnPoolControl<HttpRoute> connPerRoute) {
        this.connPerRoute = Args.notNull(connPerRoute, "Connection pool control");
        this.lastRouteProbes = new ConcurrentHashMap<>();
        this.lastRouteBackoffs = new ConcurrentHashMap<>();
    }

    /**
     * Reduces the number of maximum allowed connections for the specified route based on the exponential backoff algorithm.
     *
     * @param route the HttpRoute for which the backoff needs to be applied
     */
    @Override
    public void backOff(final HttpRoute route) {
        final int curr = connPerRoute.getMaxPerRoute(route);
        final Instant now = Instant.now();

        lastRouteBackoffs.compute(route, (r, lastUpdate) -> {
            if (lastUpdate == null || now.isAfter(lastUpdate.plus(coolDown.get().toMilliseconds(), ChronoUnit.MILLIS))) {
                final int backedOffPoolSize = getBackedOffPoolSize(curr); // Exponential backoff
                connPerRoute.setMaxPerRoute(route, backedOffPoolSize);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Backoff applied for route: {}, new max connections: {}", route, connPerRoute.getMaxPerRoute(route));
                }
                return now;
            }
            return lastUpdate;
        });
    }


    /**
     * Calculates the new pool size after applying the exponential backoff algorithm.
     * The new pool size is calculated using the formula: floor(curr / (1 + growthRate) ^ t),
     * where curr is the current pool size, growthRate is the exponential growth rate, and t is the time interval.
     *
     * @param curr the current pool size
     * @return the new pool size after applying the backoff
     */
    private int getBackedOffPoolSize(final int curr) {
        if (curr <= 1) {
            return 1;
        }
        final int t = timeInterval.incrementAndGet();
        final int result = Math.max(1, (int) Math.floor(curr / Math.pow(1 + growthRate.get(), t)));

        if (LOG.isDebugEnabled()) {
            LOG.debug("curr={}, t={}, growthRate={}, result={}", curr, t, growthRate.get(), result);
        }
        return result;
    }


    /**
     * Increases the number of maximum allowed connections for the specified route after a successful connection has been established.
     *
     * @param route the HttpRoute for which the probe needs to be applied
     */
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
                    if (LOG.isDebugEnabled()) {
                        LOG.info("Probe applied for route: {}, new max connections: {}", route, connPerRoute.getMaxPerRoute(route));
                    }
                    timeInterval.set(0); // Reset the time interval
                    return now;
                }
            }
            return lastProbe;
        });
    }

    /**
     * Retrieves the last update timestamp for the specified route from the provided updates map.
     *
     * @param updates the map containing update timestamps for HttpRoutes
     * @param route   the HttpRoute for which the last update timestamp is needed
     * @return the last update timestamp for the specified route or 0L if not present in the map
     */
    private long getLastUpdate(final Map<HttpRoute, Long> updates, final HttpRoute route) {
        return updates.getOrDefault(route, 0L);
    }


    /**
     * Sets the growth rate to be used in the exponential backoff algorithm.
     *
     * @param rate the growth rate to be set
     * @throws IllegalArgumentException if the growth rate is not greater than 0.0
     */
    public void setGrowthRate(final double rate) {
        Args.check(rate > 0.0, "Growth rate must be greater than 0.0");
        this.growthRate.set(rate);
    }


    /**
     * Sets the cool-down period after which the backoff or probe process can be performed again.
     *
     * @param coolDown the TimeValue representing the cool-down period
     * @throws IllegalArgumentException if coolDown is null or the duration is not positive
     */
    public void setCoolDown(final TimeValue coolDown) {
        Args.notNull(coolDown, "Cool down time value cannot be null");
        Args.positive(coolDown.getDuration(), "coolDown");
        this.coolDown.set(coolDown);
    }

    /**
     * Sets the per-host connection cap.
     *
     * @param cap the per-host connection cap to be set
     * @throws IllegalArgumentException if the cap is not positive
     */
    public void setPerHostConnectionCap(final int cap) {
        Args.positive(cap, "Per host connection cap");
        this.cap.set(cap);
    }
}
