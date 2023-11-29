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
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ExponentialBackoffManager extends AbstractBackoff {

    private static final Logger LOG = LoggerFactory.getLogger(ExponentialBackoffManager.class);


    /**
     * Constructs a new ExponentialBackoffManager with the specified connection pool control.
     *
     * @param connPerRoute the connection pool control to be used for managing connections
     * @throws IllegalArgumentException if connPerRoute is null
     */
    public ExponentialBackoffManager(final ConnPoolControl<HttpRoute> connPerRoute) {
        super(connPerRoute);

    }

    /**
     * Calculates the new pool size after applying the exponential backoff algorithm.
     * The new pool size is calculated using the formula: floor(curr / (1 + growthRate) ^ t),
     * where curr is the current pool size, growthRate is the exponential growth rate, and t is the time interval.
     *
     * @param curr the current pool size
     * @return the new pool size after applying the backoff
     */
    protected int getBackedOffPoolSize(final int curr) {
        if (curr <= 1) {
            return 1;
        }
        final int t = getTimeInterval().incrementAndGet();
        final int result = Math.max(1, (int) Math.floor(curr / Math.pow(1 + getBackoffFactor().get(), t)));

        if (LOG.isDebugEnabled()) {
            LOG.debug("curr={}, t={}, growthRate={}, result={}", curr, t, getBackoffFactor().get(), result);
        }
        return result;
    }

    public void setBackoffFactor(final double rate) {
        Args.check(rate > 0.0, "Growth rate must be greater than 0.0");
        this.getBackoffFactor().set(rate);
    }

}
