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
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExponentialBackoffManager {

    private ExponentialBackoffManager impl;
    private MockConnPoolControl connPerRoute;
    private HttpRoute route;
    private static final long DEFAULT_COOL_DOWN_MS = 5000; // Adjust this value to match the default cooldown period in ExponentialBackoffManager

    @BeforeEach
    public void setUp() {
        connPerRoute = new MockConnPoolControl();
        route = new HttpRoute(new HttpHost("localhost", 80));
        impl = new ExponentialBackoffManager(connPerRoute);
        impl.setPerHostConnectionCap(10);
        impl.setCoolDown(TimeValue.ofMilliseconds(DEFAULT_COOL_DOWN_MS));
        impl.setBackoffFactor(1.75); // Adjust this value to match the default growth rate in ExponentialBackoffManager
    }

    @Test
    public void exponentialBackoffApplied() {
        connPerRoute.setMaxPerRoute(route, 4);
        impl.setBackoffFactor(2); // Sets the growth rate to 2 for this test
        impl.backOff(route);
        assertEquals(1, connPerRoute.getMaxPerRoute(route)); // Corrected expected value
    }

    @Test
    public void exponentialGrowthRateIsConfigurable() throws InterruptedException {
        final int customCoolDownMs = 500;
        connPerRoute.setMaxPerRoute(route, 4);
        impl.setBackoffFactor(0.5);
        impl.setCoolDown(TimeValue.ofMilliseconds(customCoolDownMs));
        impl.backOff(route);
        assertEquals(2, connPerRoute.getMaxPerRoute(route));
        Thread.sleep(customCoolDownMs + 100); // Sleep for a slightly longer than the custom cooldown period
        impl.backOff(route);
        assertEquals(1, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void doesNotIncreaseBeyondPerHostMaxOnProbe() {
        connPerRoute.setDefaultMaxPerRoute(5);
        connPerRoute.setMaxPerRoute(route, 5);
        impl.setPerHostConnectionCap(5);
        impl.probe(route);
        assertEquals(5, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void backoffDoesNotAdjustDuringCoolDownPeriod() throws InterruptedException {
        connPerRoute.setMaxPerRoute(route, 4);
        impl.backOff(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        Thread.sleep(1); // Sleep for 1 ms
        impl.backOff(route);
        assertEquals(max, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void backoffStillAdjustsAfterCoolDownPeriod() throws InterruptedException {
        connPerRoute.setMaxPerRoute(route, 8);
        impl.backOff(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        Thread.sleep(DEFAULT_COOL_DOWN_MS + 1); // Sleep for cooldown period + 1 ms
        impl.backOff(route);
        assertTrue(max == 1 || max > connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void probeDoesNotAdjustDuringCooldownPeriod() throws InterruptedException {
        connPerRoute.setMaxPerRoute(route, 4);
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        Thread.sleep(1); // Sleep for 1 ms
        impl.probe(route);
        assertEquals(max, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void probeStillAdjustsAfterCoolDownPeriod() throws InterruptedException {
        connPerRoute.setMaxPerRoute(route, 8);
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        Thread.sleep(DEFAULT_COOL_DOWN_MS + 1); // Sleep for cooldown period + 1 ms
        impl.probe(route);
        assertTrue(max < connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void willBackoffImmediatelyEvenAfterAProbe() {
        connPerRoute.setMaxPerRoute(route, 8);
        final long now = System.currentTimeMillis();
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        impl.backOff(route);
        assertTrue(connPerRoute.getMaxPerRoute(route) < max);
    }

    @Test
    public void coolDownPeriodIsConfigurable() throws InterruptedException {
        final long cd = 500; // Fixed cooldown period of 500 milliseconds
        impl.setCoolDown(TimeValue.ofMilliseconds(cd));

        // Sleep for a short duration before starting the test to reduce potential timing issues
        Thread.sleep(100);

        // Probe and check if the connection count remains the same during the cooldown period
        impl.probe(route);
        final int max0 = connPerRoute.getMaxPerRoute(route);
        Thread.sleep(cd / 2); // Sleep for half the cooldown period
        impl.probe(route);
        assertEquals(max0, connPerRoute.getMaxPerRoute(route));

        // Probe and check if the connection count increases after the cooldown period
        Thread.sleep(cd / 2 + 1); // Sleep for the remaining half of the cooldown period + 1 ms
        impl.probe(route);
        assertTrue(max0 < connPerRoute.getMaxPerRoute(route));
    }

}