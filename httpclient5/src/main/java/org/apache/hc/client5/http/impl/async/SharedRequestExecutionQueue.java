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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.util.Args;

/**
 * Shared FIFO execution queue with a hard cap on concurrently executing tasks.
 * Tasks beyond the cap are queued and executed when in-flight count drops.
 */
@Internal
final class SharedRequestExecutionQueue {

    private final int maxConcurrent;
    private final AtomicInteger inFlight;
    private final ConcurrentLinkedQueue<Entry> queue;
    private final AtomicBoolean draining;

    SharedRequestExecutionQueue(final int maxConcurrent) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("maxConcurrent must be > 0");
        }
        this.maxConcurrent = maxConcurrent;
        this.inFlight = new AtomicInteger(0);
        this.queue = new ConcurrentLinkedQueue<>();
        this.draining = new AtomicBoolean(false);
    }

    Cancellable enqueue(final Runnable task, final Runnable onCancel) {
        Args.notNull(task, "task");
        Args.notNull(onCancel, "onCancel");

        final Entry entry = new Entry(task, onCancel);
        queue.add(entry);
        drain();
        return entry;
    }

    void completed() {
        inFlight.decrementAndGet();
        drain();
    }

    private void drain() {
        for (;;) {
            if (!draining.compareAndSet(false, true)) {
                return;
            }
            try {
                while (inFlight.get() < maxConcurrent) {
                    final Entry entry = queue.poll();
                    if (entry == null) {
                        break;
                    }
                    if (entry.isCancelled()) {
                        continue;
                    }
                    entry.markStarted();
                    inFlight.incrementAndGet();
                    entry.run();
                }
            } finally {
                draining.set(false);
            }
            if (inFlight.get() >= maxConcurrent || queue.isEmpty()) {
                return;
            }
        }
    }


    private static final class Entry implements Cancellable {

        private final Runnable task;
        private final Runnable onCancel;
        private final AtomicBoolean started;
        private final AtomicBoolean cancelled;

        Entry(final Runnable task, final Runnable onCancel) {
            this.task = task;
            this.onCancel = onCancel;
            this.started = new AtomicBoolean(false);
            this.cancelled = new AtomicBoolean(false);
        }

        void run() {
            task.run();
        }

        void markStarted() {
            started.set(true);
        }

        boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean cancel() {
            if (started.get()) {
                return false;
            }
            if (cancelled.compareAndSet(false, true)) {
                onCancel.run();
                return true;
            }
            return false;
        }
    }

}
