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

package org.apache.hc.client5.http.impl.async;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.util.Args;

/**
 * Shared FIFO execution queue with a cap on concurrently executing requests.
 */
@Internal
final class SharedRequestExecutionQueue {

    private final int maxConcurrent;
    private final AtomicInteger inFlight;
    private final ConcurrentLinkedQueue<Entry> queue;
    private final AtomicBoolean draining;
    private final AtomicBoolean closed;

    SharedRequestExecutionQueue(final int maxConcurrent) {
        this.maxConcurrent = Args.positive(maxConcurrent, "Max concurrent requests");
        this.inFlight = new AtomicInteger();
        this.queue = new ConcurrentLinkedQueue<>();
        this.draining = new AtomicBoolean();
        this.closed = new AtomicBoolean();
    }

    Cancellable enqueue(final Runnable task, final Runnable onCancel) {
        Args.notNull(task, "Task");
        Args.notNull(onCancel, "Cancel callback");

        final Entry entry = new Entry(task, onCancel);

        if (closed.get()) {
            entry.cancel();
            return entry;
        }

        queue.add(entry);

        if (closed.get()) {
            cancelPending();
            return entry;
        }

        drain();
        return entry;
    }

    void completed() {
        inFlight.decrementAndGet();
        if (closed.get()) {
            cancelPending();
        } else {
            drain();
        }
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            cancelPending();
        }
    }

    private void cancelPending() {
        Entry entry;
        while ((entry = queue.poll()) != null) {
            entry.cancel();
        }
    }

    private void drain() {
        for (;;) {
            if (!draining.compareAndSet(false, true)) {
                return;
            }
            try {
                while (!closed.get() && inFlight.get() < maxConcurrent) {
                    final Entry entry = queue.poll();
                    if (entry == null) {
                        break;
                    }
                    if (closed.get()) {
                        entry.cancel();
                        continue;
                    }
                    if (!entry.tryStart()) {
                        continue;
                    }
                    inFlight.incrementAndGet();
                    entry.run();
                }
            } finally {
                draining.set(false);
            }

            if (closed.get() || inFlight.get() >= maxConcurrent || queue.isEmpty()) {
                return;
            }
        }
    }

    private enum State {

        QUEUED,
        STARTED,
        CANCELLED

    }

    private static final class Entry implements Cancellable {

        private final Runnable task;
        private final Runnable onCancel;
        private final AtomicReference<State> state;

        Entry(final Runnable task, final Runnable onCancel) {
            this.task = task;
            this.onCancel = onCancel;
            this.state = new AtomicReference<>(State.QUEUED);
        }

        void run() {
            task.run();
        }

        boolean tryStart() {
            return state.compareAndSet(State.QUEUED, State.STARTED);
        }

        @Override
        public boolean cancel() {
            if (state.compareAndSet(State.QUEUED, State.CANCELLED)) {
                onCancel.run();
                return true;
            }
            return false;
        }

    }

}