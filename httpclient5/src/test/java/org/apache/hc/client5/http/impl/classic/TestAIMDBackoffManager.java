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

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

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
    private static final long DEFAULT_COOL_DOWN_MS = 10; // Adjust this value to match the default cooldown period in AIMDBackoffManager


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
    public void backoffDoesNotAdjustDuringCoolDownPeriod() {
        // Arrange
        connPerRoute.setMaxPerRoute(route, 4);

        // Act
        impl.backOff(route);
        final long max1 = connPerRoute.getMaxPerRoute(route);

        // Manipulate lastRouteBackoffs to simulate that not enough time has passed
        final Map<HttpRoute, Instant> lastRouteBackoffs = impl.getLastRouteBackoffs();
        lastRouteBackoffs.put(route, Instant.now().minusMillis(1));

        // Act again
        impl.backOff(route);
        final long max2 = connPerRoute.getMaxPerRoute(route);

        // Assert
        assertEquals(max1, max2);
    }


    @Test
    public void backoffStillAdjustsAfterCoolDownPeriod() {
        // Arrange: Initialize the maximum number of connections for a route to 8
        connPerRoute.setMaxPerRoute(route, 8);

        // Act: Perform the first backoff operation
        impl.backOff(route);
        final long initialMax = connPerRoute.getMaxPerRoute(route);

        // Act: Simulate that the cooldown period has passed
        final Map<HttpRoute, Instant> lastRouteBackoffs = impl.getLastRouteBackoffs();
        lastRouteBackoffs.put(route, Instant.now().minusMillis(DEFAULT_COOL_DOWN_MS + 1));

        // Act: Perform the second backoff operation
        impl.backOff(route);
        final long finalMax = connPerRoute.getMaxPerRoute(route);

        // Assert: Verify that the maximum number of connections has decreased or reached the minimum limit (1)
        if (initialMax != 1) {
            assertTrue(finalMax < initialMax, "Max connections should decrease after cooldown");
        } else {
            assertEquals(1, finalMax, "Max connections should remain 1 if it's already at the minimum");
        }
    }


    @Test
    public void probeDoesNotAdjustDuringCooldownPeriod() {
        // Arrange
        connPerRoute.setMaxPerRoute(route, 4);

        // First probe
        impl.probe(route);
        final long max1 = connPerRoute.getMaxPerRoute(route);

        // Manipulate lastRouteProbes to simulate that not enough time has passed
        final Map<HttpRoute, Instant> lastRouteProbes = impl.getLastRouteProbes();
        lastRouteProbes.put(route, Instant.now().minusMillis(1));

        // Second probe
        impl.probe(route);
        final long max2 = connPerRoute.getMaxPerRoute(route);

        // Assert
        assertEquals(max1, max2);
    }


    @Test
    public void probeStillAdjustsAfterCoolDownPeriod() {
        connPerRoute.setMaxPerRoute(route, 8);

        // First probe
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);

        // Manipulate lastRouteProbes to simulate that enough time has passed for the cooldown period
        final Map<HttpRoute, Instant> lastRouteProbes = impl.getLastRouteProbes();
        lastRouteProbes.put(route, Instant.now().minusMillis(DEFAULT_COOL_DOWN_MS + 1));

        // Second probe
        impl.probe(route);

        // Assert that the max connections have increased
        assertTrue(max < connPerRoute.getMaxPerRoute(route));
    }


    @Test
    public void willBackoffImmediatelyEvenAfterAProbe() {
        connPerRoute.setMaxPerRoute(route, 8);
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
    public void coolDownPeriodIsConfigurable() {
        final long cd = new Random().nextInt(500) + 500; // Random cooldown period between 500 and 1000 milliseconds
        impl.setCoolDown(TimeValue.ofMilliseconds(cd));

        // Probe and check if the connection count remains the same during the cooldown period
        impl.probe(route);
        final int max0 = connPerRoute.getMaxPerRoute(route);

        // Manipulate lastRouteProbes to simulate that not enough time has passed
        final Map<HttpRoute, Instant> lastRouteProbes = impl.getLastRouteProbes();
        lastRouteProbes.put(route, Instant.now().minusMillis(cd / 2));

        // Probe again
        impl.probe(route);
        assertEquals(max0, connPerRoute.getMaxPerRoute(route));

        // Manipulate lastRouteProbes to simulate that enough time has passed
        lastRouteProbes.put(route, Instant.now().minusMillis(cd + 1));

        // Probe again
        impl.probe(route);
        assertTrue(max0 < connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void testConcurrency() throws InterruptedException {
        final int initialMaxPerRoute = 10;
        final int numberOfThreads = 20;
        final int numberOfOperationsPerThread = 100;  // reduced operations

        // Create a cyclic barrier that will wait for all threads to be ready before proceeding
        final CyclicBarrier barrier = new CyclicBarrier(numberOfThreads);

        final CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            final HttpRoute threadRoute = new HttpRoute(new HttpHost("localhost", 8080 + i)); // Each thread gets its own route
            connPerRoute.setMaxPerRoute(threadRoute, initialMaxPerRoute);

            new Thread(() -> {
                try {
                    // Wait for all threads to be ready
                    barrier.await();

                    // Run operations
                    for (int j = 0; j < numberOfOperationsPerThread; j++) {
                        if (Math.random() < 0.5) {
                            impl.backOff(threadRoute);
                        } else {
                            impl.probe(threadRoute);
                        }
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        // Check that the final value for each route is within an acceptable range
        for (int i = 0; i < numberOfThreads; i++) {
            final HttpRoute threadRoute = new HttpRoute(new HttpHost("localhost", 8080 + i));
            final int finalMaxPerRoute = connPerRoute.getMaxPerRoute(threadRoute);
            assertTrue(finalMaxPerRoute >= 1 && finalMaxPerRoute <= initialMaxPerRoute + 7);  // more permissive check
        }
    }
}
