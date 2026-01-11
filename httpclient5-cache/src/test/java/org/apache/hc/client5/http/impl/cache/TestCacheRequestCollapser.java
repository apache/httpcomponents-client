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

import org.apache.hc.core5.concurrent.ComplexCancellable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestCacheRequestCollapser {

    @Test
    void testSingleLeaderForSameKey() throws Exception {
        final CacheRequestCollapser collapser = new CacheRequestCollapser();
        final String key = "k";
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
                    final CacheRequestCollapser.Token token = collapser.enter(key);
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

            CacheRequestCollapser.Token leaderToken = null;
            for (final CacheRequestCollapser.Token t : tokens) {
                if (t.isLeader()) {
                    leaderToken = t;
                    break;
                }
            }
            Assertions.assertNotNull(leaderToken);
            leaderToken.complete();

            // After completion, the next enter must produce a fresh leader.
            final CacheRequestCollapser.Token next = collapser.enter(key);
            Assertions.assertTrue(next.isLeader());
            next.complete();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testAwaitRunsAfterComplete() throws Exception {
        final CacheRequestCollapser collapser = new CacheRequestCollapser();
        final String key = "k";

        final CacheRequestCollapser.Token leader = collapser.enter(key);
        Assertions.assertTrue(leader.isLeader());

        final CacheRequestCollapser.Token follower = collapser.enter(key);
        Assertions.assertFalse(follower.isLeader());

        final AtomicInteger runs = new AtomicInteger(0);
        final ComplexCancellable holder = new ComplexCancellable();
        collapser.await(follower, holder, runs::incrementAndGet);
        Assertions.assertEquals(0, runs.get());

        leader.complete();

        // drain() runs synchronously inside complete(), so the task has already executed.
        Assertions.assertEquals(1, runs.get());
    }

    @Test
    void testCancelledWaiterDoesNotRun() {
        final CacheRequestCollapser collapser = new CacheRequestCollapser();
        final String key = "k";

        final CacheRequestCollapser.Token leader = collapser.enter(key);
        final CacheRequestCollapser.Token follower = collapser.enter(key);

        final AtomicInteger runs = new AtomicInteger(0);
        final ComplexCancellable holder = new ComplexCancellable();
        collapser.await(follower, holder, runs::incrementAndGet);
        Assertions.assertTrue(holder.cancel());

        leader.complete();

        Assertions.assertEquals(0, runs.get(), "Cancelled waiter must not run");
    }

    @Test
    void testCompleteIsIdempotent() {
        final CacheRequestCollapser collapser = new CacheRequestCollapser();
        final String key = "k";

        final CacheRequestCollapser.Token leader = collapser.enter(key);
        final CacheRequestCollapser.Token follower = collapser.enter(key);

        final AtomicInteger runs = new AtomicInteger(0);
        final ComplexCancellable holder = new ComplexCancellable();
        collapser.await(follower, holder, runs::incrementAndGet);

        leader.complete();
        leader.complete();
        leader.complete();

        Assertions.assertEquals(1, runs.get(), "Waiters must run exactly once");
    }

    @Test
    void testLeaderCompletesBetweenEnterAndAwait() {
        final CacheRequestCollapser collapser = new CacheRequestCollapser();
        final String key = "k";

        final CacheRequestCollapser.Token leader = collapser.enter(key);
        final CacheRequestCollapser.Token follower = collapser.enter(key);
        Assertions.assertFalse(follower.isLeader());

        leader.complete();

        final AtomicInteger runs = new AtomicInteger(0);
        final ComplexCancellable holder = new ComplexCancellable();
        collapser.await(follower, holder, runs::incrementAndGet);

        Assertions.assertEquals(1, runs.get(), "Task must run synchronously when the leader is already complete");
    }

    @Test
    void testFollowerCancelWhileWaiting() {
        final CacheRequestCollapser collapser = new CacheRequestCollapser();
        final String key = "k";

        final CacheRequestCollapser.Token leader = collapser.enter(key);
        final CacheRequestCollapser.Token follower = collapser.enter(key);

        final AtomicInteger runs = new AtomicInteger(0);
        final ComplexCancellable holder = new ComplexCancellable();
        collapser.await(follower, holder, runs::incrementAndGet);

        Assertions.assertTrue(holder.cancel(), "Outer cancel via the holder must succeed");
        Assertions.assertTrue(holder.isCancelled());

        leader.complete();

        Assertions.assertEquals(0, runs.get(), "Task must not run after the holder has been cancelled");
    }

    @Test
    void testLeaderFailureReleasesFollowers() {
        final CacheRequestCollapser collapser = new CacheRequestCollapser();
        final String key = "k";

        final CacheRequestCollapser.Token leader = collapser.enter(key);
        final CacheRequestCollapser.Token f1 = collapser.enter(key);
        final CacheRequestCollapser.Token f2 = collapser.enter(key);
        final CacheRequestCollapser.Token f3 = collapser.enter(key);

        final AtomicInteger runs = new AtomicInteger(0);
        collapser.await(f1, new ComplexCancellable(), runs::incrementAndGet);
        collapser.await(f2, new ComplexCancellable(), runs::incrementAndGet);
        collapser.await(f3, new ComplexCancellable(), runs::incrementAndGet);

        // Simulate the leader failure path: callback.failed() ends with token.complete().
        leader.complete();

        Assertions.assertEquals(3, runs.get(), "All followers must be released when the leader completes (success or failure)");

        // After release the key is free and the next caller becomes a fresh leader.
        final CacheRequestCollapser.Token next = collapser.enter(key);
        Assertions.assertTrue(next.isLeader());
        next.complete();
    }

}
