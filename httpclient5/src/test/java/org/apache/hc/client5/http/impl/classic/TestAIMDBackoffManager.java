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
        connPerRoute.setMaxPerRoute(route, 8);
        impl.backOff(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        Thread.sleep(DEFAULT_COOL_DOWN_MS + 100); // Sleep for cooldown period + 100 ms
        impl.backOff(route);
        assertTrue(max == 1 || max > connPerRoute.getMaxPerRoute(route));
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
        connPerRoute.setMaxPerRoute(route, 8);
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        Thread.sleep(DEFAULT_COOL_DOWN_MS + 100); // Sleep for cooldown period + 1 ms
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
        Thread.sleep(cd / 2 + 100); // Sleep for half the cooldown period + 100 ms buffer
        impl.probe(route);
        assertEquals(max0, connPerRoute.getMaxPerRoute(route));

        // Probe and check if the connection count increases after the cooldown period
        Thread.sleep(cd / 2 + 100); // Sleep for the remaining half of the cooldown period + 100 ms buffer
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
