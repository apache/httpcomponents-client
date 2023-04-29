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
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;

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
public class AIMDBackoffManager extends AbstractBackoff {

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
        super(connPerRoute);
    }

    /**
     * Returns the backed-off pool size based on the current pool size.
     * The new pool size is calculated as the floor of (backoffFactor * curr).
     *
     * @param curr the current pool size
     * @return the backed-off pool size, with a minimum value of 1
     */
    protected int getBackedOffPoolSize(final int curr) {
        if (curr <= 1) {
            return 1;
        }
        return (int) (Math.floor(getBackoffFactor().get() * curr));
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
    @Override
    public void setBackoffFactor(final double d) {
        Args.check(d > 0.0 && d < 1.0, "Backoff factor must be 0.0 < f < 1.0");
        getBackoffFactor().set(d);
    }

}
