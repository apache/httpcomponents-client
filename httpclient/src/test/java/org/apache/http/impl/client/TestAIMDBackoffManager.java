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
package org.apache.http.impl.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.apache.http.HttpHost;
import org.apache.http.client.BackoffManager;
import org.apache.http.conn.routing.HttpRoute;
import org.junit.Before;
import org.junit.Test;

public class TestAIMDBackoffManager {

    private AIMDBackoffManager impl;
    private MockConnPoolControl connPerRoute;
    private HttpRoute route;
    private MockClock clock;

    @Before
    public void setUp() {
        connPerRoute = new MockConnPoolControl();
        route = new HttpRoute(new HttpHost("localhost", 80));
        clock = new MockClock();
        impl = new AIMDBackoffManager(connPerRoute, clock);
        impl.setPerHostConnectionCap(10);
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
        final long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.backOff(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        clock.setCurrentTime(now + 1);
        impl.backOff(route);
        assertEquals(max, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void backoffStillAdjustsAfterCoolDownPeriod() {
        connPerRoute.setMaxPerRoute(route, 8);
        final long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.backOff(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        clock.setCurrentTime(now + 10 * 1000L);
        impl.backOff(route);
        assertTrue(max == 1 || max > connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void probeDoesNotAdjustDuringCooldownPeriod() {
        connPerRoute.setMaxPerRoute(route, 4);
        final long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        clock.setCurrentTime(now + 1);
        impl.probe(route);
        assertEquals(max, connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void probeStillAdjustsAfterCoolDownPeriod() {
        connPerRoute.setMaxPerRoute(route, 8);
        final long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        clock.setCurrentTime(now + 10 * 1000L);
        impl.probe(route);
        assertTrue(max < connPerRoute.getMaxPerRoute(route));
    }

    @Test
    public void willBackoffImmediatelyEvenAfterAProbe() {
        connPerRoute.setMaxPerRoute(route, 8);
        final long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.probe(route);
        final long max = connPerRoute.getMaxPerRoute(route);
        clock.setCurrentTime(now + 1);
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
        long cd = new Random().nextLong() / 2;
        if (cd < 0) {
            cd *= -1;
        }
        if (cd < 1) {
            cd++;
        }
        final long now = System.currentTimeMillis();
        impl.setCooldownMillis(cd);
        clock.setCurrentTime(now);
        impl.probe(route);
        final int max0 = connPerRoute.getMaxPerRoute(route);
        clock.setCurrentTime(now);
        impl.probe(route);
        assertEquals(max0, connPerRoute.getMaxPerRoute(route));
        clock.setCurrentTime(now + cd + 1);
        impl.probe(route);
        assertTrue(max0 < connPerRoute.getMaxPerRoute(route));
    }
}
