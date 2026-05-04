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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.concurrent.Cancellable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestSharedRequestExecutionQueue {

    @Test
    void testConstructorRejectsNonPositiveMaxConcurrentRequests() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new SharedRequestExecutionQueue(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new SharedRequestExecutionQueue(-1));
    }

    @Test
    void testRequestsExecuteImmediatelyUpToConcurrencyLimit() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(2);
        final List<String> events = new ArrayList<>();

        queue.enqueue(() -> events.add("run-1"), () -> events.add("cancel-1"));
        queue.enqueue(() -> events.add("run-2"), () -> events.add("cancel-2"));
        queue.enqueue(() -> events.add("run-3"), () -> events.add("cancel-3"));

        Assertions.assertEquals(Arrays.asList("run-1", "run-2"), events);

        queue.completed();

        Assertions.assertEquals(Arrays.asList("run-1", "run-2", "run-3"), events);
    }

    @Test
    void testQueuedRequestsAreExecutedInFifoOrder() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(1);
        final List<String> events = new ArrayList<>();

        queue.enqueue(() -> events.add("run-1"), () -> events.add("cancel-1"));
        queue.enqueue(() -> events.add("run-2"), () -> events.add("cancel-2"));
        queue.enqueue(() -> events.add("run-3"), () -> events.add("cancel-3"));

        Assertions.assertEquals(Arrays.asList("run-1"), events);

        queue.completed();

        Assertions.assertEquals(Arrays.asList("run-1", "run-2"), events);

        queue.completed();

        Assertions.assertEquals(Arrays.asList("run-1", "run-2", "run-3"), events);
    }

    @Test
    void testCancellingPendingRequestPreventsExecution() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(1);
        final List<String> events = new ArrayList<>();

        final Cancellable running = queue.enqueue(() -> events.add("run-1"), () -> events.add("cancel-1"));
        final Cancellable pending1 = queue.enqueue(() -> events.add("run-2"), () -> events.add("cancel-2"));
        final Cancellable pending2 = queue.enqueue(() -> events.add("run-3"), () -> events.add("cancel-3"));

        Assertions.assertFalse(running.cancel());
        Assertions.assertTrue(pending1.cancel());
        Assertions.assertFalse(pending1.cancel());

        queue.completed();

        Assertions.assertEquals(Arrays.asList("run-1", "cancel-2", "run-3"), events);
        Assertions.assertFalse(pending2.cancel());
    }

    @Test
    void testCloseCancelsPendingRequests() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(1);
        final AtomicInteger executed = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();

        final Cancellable running = queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);
        final Cancellable pending1 = queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);
        final Cancellable pending2 = queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);

        queue.close();

        Assertions.assertEquals(1, executed.get());
        Assertions.assertEquals(2, cancelled.get());

        Assertions.assertFalse(running.cancel());
        Assertions.assertFalse(pending1.cancel());
        Assertions.assertFalse(pending2.cancel());
    }

    @Test
    void testCloseDoesNotCancelAlreadyCancelledPendingRequestAgain() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(1);
        final AtomicInteger executed = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();

        queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);
        final Cancellable pending1 = queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);
        final Cancellable pending2 = queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);

        Assertions.assertTrue(pending1.cancel());

        queue.close();

        Assertions.assertEquals(1, executed.get());
        Assertions.assertEquals(2, cancelled.get());
        Assertions.assertFalse(pending1.cancel());
        Assertions.assertFalse(pending2.cancel());
    }

    @Test
    void testCompletedAfterCloseDoesNotStartPendingRequests() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(1);
        final List<String> events = new ArrayList<>();

        queue.enqueue(() -> events.add("run-1"), () -> events.add("cancel-1"));
        queue.enqueue(() -> events.add("run-2"), () -> events.add("cancel-2"));
        queue.enqueue(() -> events.add("run-3"), () -> events.add("cancel-3"));

        queue.close();

        Assertions.assertEquals(Arrays.asList("run-1", "cancel-2", "cancel-3"), events);

        queue.completed();

        Assertions.assertEquals(Arrays.asList("run-1", "cancel-2", "cancel-3"), events);
    }

    @Test
    void testCloseRejectsNewRequests() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(1);
        final AtomicInteger executed = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();

        queue.close();

        final Cancellable cancellable = queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);

        Assertions.assertEquals(0, executed.get());
        Assertions.assertEquals(1, cancelled.get());
        Assertions.assertFalse(cancellable.cancel());
    }

    @Test
    void testCloseIsIdempotent() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(1);
        final AtomicInteger executed = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();

        queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);
        queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);
        queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);

        queue.close();
        queue.close();
        queue.close();

        Assertions.assertEquals(1, executed.get());
        Assertions.assertEquals(2, cancelled.get());
    }

    @Test
    void testCompletedAfterClosedAndDrainedQueueIsNoOp() {
        final SharedRequestExecutionQueue queue = new SharedRequestExecutionQueue(1);
        final AtomicInteger executed = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();

        queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);
        queue.enqueue(executed::incrementAndGet, cancelled::incrementAndGet);

        queue.close();
        queue.completed();

        Assertions.assertEquals(1, executed.get());
        Assertions.assertEquals(1, cancelled.get());
    }

}