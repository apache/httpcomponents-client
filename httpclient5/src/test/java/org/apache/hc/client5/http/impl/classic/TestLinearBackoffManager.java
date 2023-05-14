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

public class TestLinearBackoffManager {

    private LinearBackoffManager impl;
    private MockConnPoolControl connPerRoute;
    private HttpRoute route;
    private static final long DEFAULT_COOL_DOWN_MS = 10;

    @BeforeEach
    public void setUp() {
        connPerRoute = new MockConnPoolControl();
        route = new HttpRoute(new HttpHost("localhost", 80));
        impl = new LinearBackoffManager(connPerRoute);
        impl.setCoolDown(TimeValue.ofMilliseconds(DEFAULT_COOL_DOWN_MS));
    }

    @Test
    public void incrementsConnectionsOnBackoff() {
        final LinearBackoffManager impl = new LinearBackoffManager(connPerRoute);
        impl.setCoolDown(TimeValue.ofMilliseconds(DEFAULT_COOL_DOWN_MS)); // Set the cool-down period
        connPerRoute.setMaxPerRoute(route, 4);
        impl.backOff(route);
        assertEquals(5, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void decrementsConnectionsOnProbe() {
        connPerRoute.setMaxPerRoute(route, 4);
        impl.probe(route);
        assertEquals(3, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void backoffDoesNotAdjustDuringCoolDownPeriod() {
        connPerRoute.setMaxPerRoute(route, 4);
        impl.backOff(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        // Replace Thread.sleep(1) with busy waiting
        final long end = System.currentTimeMillis() + 1;
        while (System.currentTimeMillis() < end) {
            // Busy waiting
        }

        impl.backOff(route);
        assertEquals(max, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void backoffStillAdjustsAfterCoolDownPeriod() throws InterruptedException {
        final LinearBackoffManager impl = new LinearBackoffManager(connPerRoute);
        impl.setCoolDown(TimeValue.ofMilliseconds(DEFAULT_COOL_DOWN_MS)); // Set the cool-down period
        connPerRoute.setMaxPerRoute(route, 4);
        impl.backOff(route);
        final int max1 = connPerRoute.getMaxPerRoute(route);

        Thread.sleep(DEFAULT_COOL_DOWN_MS + 1); // Sleep for cooldown period + 1 ms

        impl.backOff(route);
        final int max2 = connPerRoute.getMaxPerRoute(route);

        assertTrue(max2 > max1);
    }


    @Test
    public void probeDoesNotAdjustDuringCooldownPeriod() {
        connPerRoute.setMaxPerRoute(route, 4);
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        // Replace Thread.sleep(1) with busy waiting
        final long end = System.currentTimeMillis() + 1;
        while (System.currentTimeMillis() < end) {
            // Busy waiting
        }

        impl.probe(route);
        assertEquals(max, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void probeStillAdjustsAfterCoolDownPeriod() throws InterruptedException {
        connPerRoute.setMaxPerRoute(route, 4);
        impl.probe(route);
        Thread.sleep(DEFAULT_COOL_DOWN_MS + 1); // Sleep for cooldown period + 1 ms
        impl.probe(route);
        final long newMax = connPerRoute.getMaxPerRoute(route);
        assertEquals(2, newMax); // The cap is set to 2 by default
    }

    @Test
    public void testSetPerHostConnectionCap() {
        connPerRoute.setMaxPerRoute(route, 5);
        impl.setPerHostConnectionCap(10); // Set the cap to a higher value
        impl.backOff(route);
        assertEquals(6, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void probeUpdatesRemainingAttemptsIndirectly() throws InterruptedException {
        // Set initial max per route
        connPerRoute.setMaxPerRoute(route, 4);

        // Apply backOff twice
        impl.backOff(route);
        Thread.sleep(DEFAULT_COOL_DOWN_MS + 1);
        impl.backOff(route);

        // Ensure that connection pool size has increased
        assertEquals(6, connPerRoute.getMaxPerRoute(route));

        // Apply probe once
        impl.probe(route);

        // Wait for a longer cool down period
        Thread.sleep(DEFAULT_COOL_DOWN_MS * 2);

        // Apply probe once more
        impl.probe(route);

        // Check that connection pool size has decreased once, indicating that the remaining attempts were updated
        assertEquals(5, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void linearIncrementTest() throws InterruptedException {
        final int initialMax = 4;
        connPerRoute.setMaxPerRoute(route, initialMax);
        for (int i = 1; i <= 5; i++) {
            impl.backOff(route);
            assertEquals(initialMax + i, connPerRoute.getMaxPerRoute(route));
            Thread.sleep(DEFAULT_COOL_DOWN_MS + 1);

        }
    }
}
