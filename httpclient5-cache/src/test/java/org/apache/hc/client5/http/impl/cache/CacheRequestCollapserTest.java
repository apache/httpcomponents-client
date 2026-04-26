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
package org.apache.hc.client5.http.impl.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.concurrent.Cancellable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestCacheRequestCollapser {

    @Test
    void testSingleLeaderForSameKey() throws Exception {
        final String key = "testSingleLeaderForSameKey-" + System.nanoTime();
        final int threads = 32;

        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            final CountDownLatch ready = new CountDownLatch(threads);
            final CountDownLatch start = new CountDownLatch(1);

            final AtomicInteger leaders = new AtomicInteger(0);
            final List<Future<CacheRequestCollapser.Token>> futures = new ArrayList<>(threads);

            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    final CacheRequestCollapser.Token token = CacheRequestCollapser.INSTANCE.enter(key);
                    if (token.isLeader()) {
                        leaders.incrementAndGet();
                    }
                    return token;
                }));
            }

            Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            final List<CacheRequestCollapser.Token> tokens = new ArrayList<>(threads);
            for (final Future<CacheRequestCollapser.Token> f : futures) {
                tokens.add(f.get(5, TimeUnit.SECONDS));
            }

            Assertions.assertEquals(1, leaders.get(), "Expected exactly one leader");

            // Complete using the leader token to clean up.
            CacheRequestCollapser.Token leaderToken = null;
            for (final CacheRequestCollapser.Token t : tokens) {
                if (t.isLeader()) {
                    leaderToken = t;
                    break;
                }
            }
            Assertions.assertNotNull(leaderToken);
            leaderToken.complete();

            // After completion, next enter should create a new leader.
            final CacheRequestCollapser.Token next = CacheRequestCollapser.INSTANCE.enter(key);
            Assertions.assertTrue(next.isLeader());
            next.complete();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testAwaitRunsAfterComplete() throws Exception {
        final String key = "testAwaitRunsAfterComplete-" + System.nanoTime();

        final CacheRequestCollapser.Token leader = CacheRequestCollapser.INSTANCE.enter(key);
        Assertions.assertTrue(leader.isLeader());

        final CacheRequestCollapser.Token follower = CacheRequestCollapser.INSTANCE.enter(key);
        Assertions.assertFalse(follower.isLeader());

        final CountDownLatch ran = new CountDownLatch(1);

        final Cancellable cancellable = CacheRequestCollapser.INSTANCE.await(follower, () -> ran.countDown());
        Assertions.assertNotNull(cancellable);

        // Must not run before completion.
        Assertions.assertFalse(ran.await(150, TimeUnit.MILLISECONDS));

        leader.complete();

        // Must run after completion.
        Assertions.assertTrue(ran.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testCancelledWaiterDoesNotRun() throws Exception {
        final String key = "testCancelledWaiterDoesNotRun-" + System.nanoTime();

        final CacheRequestCollapser.Token leader = CacheRequestCollapser.INSTANCE.enter(key);
        Assertions.assertTrue(leader.isLeader());

        final CacheRequestCollapser.Token follower = CacheRequestCollapser.INSTANCE.enter(key);
        Assertions.assertFalse(follower.isLeader());

        final AtomicInteger runs = new AtomicInteger(0);

        final Cancellable cancellable = CacheRequestCollapser.INSTANCE.await(follower, () -> runs.incrementAndGet());
        Assertions.assertTrue(cancellable.cancel());

        leader.complete();

        // Give drain a moment.
        Thread.sleep(100);

        Assertions.assertEquals(0, runs.get(), "Cancelled waiter must not run");
    }

    @Test
    void testCompleteIsIdempotent() throws Exception {
        final String key = "testCompleteIsIdempotent-" + System.nanoTime();

        final CacheRequestCollapser.Token leader = CacheRequestCollapser.INSTANCE.enter(key);
        Assertions.assertTrue(leader.isLeader());

        final CacheRequestCollapser.Token follower = CacheRequestCollapser.INSTANCE.enter(key);
        Assertions.assertFalse(follower.isLeader());

        final AtomicInteger runs = new AtomicInteger(0);
        CacheRequestCollapser.INSTANCE.await(follower, () -> runs.incrementAndGet());

        leader.complete();
        leader.complete();
        leader.complete();

        // Give drain a moment.
        Thread.sleep(100);

        Assertions.assertEquals(1, runs.get(), "Waiters must run exactly once");
    }

}
