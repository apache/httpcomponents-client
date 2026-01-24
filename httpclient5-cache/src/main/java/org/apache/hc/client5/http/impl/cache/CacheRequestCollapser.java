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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.util.Args;

/**
 * Coordinates concurrent requests for the same cache key so that only one request
 * goes to the backend while others wait for it to complete and then re-check the cache.
 * <p>
 * Internal helper used to implement request collapsing ({@code HTTPCLIENT-1165}).
 */
@Internal
final class CacheRequestCollapser {

    static final CacheRequestCollapser INSTANCE = new CacheRequestCollapser();

    static final class Token {

        private final ConcurrentHashMap<String, Entry> inflight;
        private final String key;
        private final Entry entry;
        private final boolean leader;

        private Token(final ConcurrentHashMap<String, Entry> inflight, final String key, final Entry entry, final boolean leader) {
            this.inflight = inflight;
            this.key = key;
            this.entry = entry;
            this.leader = leader;
        }

        boolean isLeader() {
            return leader;
        }

        void complete() {
            if (entry.completed.compareAndSet(false, true)) {
                inflight.remove(key, entry);
                entry.drain();
            }
        }

    }

    private static final class Waiter implements Cancellable {

        private final AtomicBoolean cancelled;
        private final Runnable task;

        private Waiter(final Runnable task) {
            this.cancelled = new AtomicBoolean(false);
            this.task = task;
        }

        @Override
        public boolean cancel() {
            return cancelled.compareAndSet(false, true);
        }

        void runIfNotCancelled() {
            if (!cancelled.get()) {
                task.run();
            }
        }

    }

    private static final class Entry {

        private final AtomicBoolean completed;
        private final ConcurrentLinkedQueue<Waiter> waiters;

        private Entry() {
            this.completed = new AtomicBoolean(false);
            this.waiters = new ConcurrentLinkedQueue<>();
        }

        private Cancellable await(final Runnable task) {
            if (completed.get()) {
                task.run();
                return () -> false;
            }
            final Waiter waiter = new Waiter(task);
            waiters.add(waiter);
            if (completed.get()) {
                drain();
            }
            return waiter;
        }

        private void drain() {
            for (; ; ) {
                final Waiter waiter = waiters.poll();
                if (waiter == null) {
                    return;
                }
                waiter.runIfNotCancelled();
            }
        }

    }

    private final ConcurrentHashMap<String, Entry> inflight;

    private CacheRequestCollapser() {
        this.inflight = new ConcurrentHashMap<>();
    }

    Token enter(final String key) {
        Args.notEmpty(key, "Key");
        final Entry created = new Entry();
        final Entry existing = inflight.putIfAbsent(key, created);
        if (existing == null) {
            return new Token(inflight, key, created, true);
        }
        return new Token(inflight, key, existing, false);
    }

    Cancellable await(final Token token, final Runnable task) {
        Args.notNull(token, "Token");
        Args.notNull(task, "Task");
        return token.entry.await(task);
    }

}
