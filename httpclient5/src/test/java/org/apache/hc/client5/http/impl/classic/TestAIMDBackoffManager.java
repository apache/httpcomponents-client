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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.BackoffManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestAIMDBackoffManager {

    private AIMDBackoffManager impl;
    private MockConnPoolControl connPerRoute;
    private HttpRoute route;
    private static final long DEFAULT_COOL_DOWN_MS = 5000; // Adjust this value to match the default cooldown period in AIMDBackoffManager


    @BeforeEach
    public void setUp() {
        connPerRoute = new MockConnPoolControl();
        route = new HttpRoute(new HttpHost("localhost", 80));
        impl = new AIMDBackoffManager(connPerRoute);
        impl.setPerHostConnectionCap(10);
        impl.setCoolDown(TimeValue.ofMilliseconds(DEFAULT_COOL_DOWN_MS));

    }

    @Test
    public void isABackoffManager() {
        assertTrue(impl instanceof BackoffManager);
    }

    @Test
    public void halvesConnectionsOnBackoff() {
        connPerRoute.setMaxPerRoute(route, 4);
        impl.backOff(route);
        assertEquals(2, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void doesNotBackoffBelowOneConnection() {
        connPerRoute.setMaxPerRoute(route, 1);
        impl.backOff(route);
        assertEquals(1, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void increasesByOneOnProbe() {
        connPerRoute.setMaxPerRoute(route, 2);
        impl.probe(route);
        assertEquals(3, connPerRoute.getMaxPerRoute(route));
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
    public void backOffFactorIsConfigurable() {
        connPerRoute.setMaxPerRoute(route, 10);
        impl.setBackoffFactor(0.9);
        impl.backOff(route);
        assertEquals(9, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void coolDownPeriodIsConfigurable() throws InterruptedException {
        final long cd = new Random().nextInt(500) + 500; // Random cooldown period between 500 and 1000 milliseconds
        impl.setCoolDown(TimeValue.ofMilliseconds(cd));

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

    @Test
    public void testConcurrency() throws InterruptedException {
        final int initialMaxPerRoute = 10;
        final int numberOfThreads = 20;
        final int numberOfOperationsPerThread = 100;

        connPerRoute.setMaxPerRoute(route, initialMaxPerRoute);
        final CountDownLatch latch = new CountDownLatch(numberOfThreads);

        final Runnable backoffAndProbeTask = () -> {
            for (int i = 0; i < numberOfOperationsPerThread; i++) {
                if (Math.random() < 0.5) {
                    impl.backOff(route);
                } else {
                    impl.probe(route);
                }
            }
            latch.countDown();
        };

        for (int i = 0; i < numberOfThreads; i++) {
            new Thread(backoffAndProbeTask).start();
        }

        latch.await();

        final int finalMaxPerRoute = connPerRoute.getMaxPerRoute(route);
        // The final value should be within an acceptable range (e.g., 5 to 15) since the number of backOff and probe operations should balance out over time
        assertTrue(finalMaxPerRoute >= initialMaxPerRoute - 5 && finalMaxPerRoute <= initialMaxPerRoute + 5);
    }
}
