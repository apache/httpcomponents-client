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
package org.apache.hc.client5.http.sse.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.sse.EventSourceListener;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;

class DefaultEventSourceTest {

    @Test
    void stopReconnectExceptionDoesNotScheduleReconnect() throws Exception {
        final RecordingScheduler scheduler = new RecordingScheduler();
        final CapturingClient client = new CapturingClient();
        final CountDownLatch failure = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);

        final EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onFailure(final Throwable t, final boolean willReconnect) {
                if (!willReconnect) {
                    failure.countDown();
                }
            }

            @Override
            public void onClosed() {
                closed.countDown();
            }

            @Override
            public void onEvent(final String id, final String type, final String data) {
            }
        };

        final DefaultEventSource es = new DefaultEventSource(
                client,
                URI.create("http://example.org/sse"),
                Collections.emptyMap(),
                listener,
                scheduler,
                null,
                null,
                SseParser.CHAR);

        es.start();
        client.lastCallback.failed(new SseResponseConsumer.StopReconnectException("Server closed stream (204)"));

        assertTrue(failure.await(1, TimeUnit.SECONDS), "failure observed");
        assertTrue(closed.await(1, TimeUnit.SECONDS), "closed observed");
        assertTrue(scheduler.scheduledCount.get() == 0, "no reconnect scheduled");
    }

    @Test
    void callerSchedulerIsNotShutdownOnCancel() {
        final RecordingScheduler scheduler = new RecordingScheduler();
        final CapturingClient client = new CapturingClient();

        final DefaultEventSource es = new DefaultEventSource(
                client,
                URI.create("http://example.org/sse"),
                Collections.emptyMap(),
                (id, type, data) -> { },
                scheduler,
                null,
                null,
                SseParser.CHAR);

        es.start();
        es.cancel();

        assertFalse(scheduler.shutdownCalled.get(), "caller scheduler not shutdown");
    }

    static final class CapturingClient extends CloseableHttpAsyncClient {
        volatile FutureCallback<Void> lastCallback;

        @Override
        public void start() { }

        @Override
        public IOReactorStatus getStatus() {
            return IOReactorStatus.ACTIVE;
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) throws InterruptedException { }

        @Override
        public void initiateShutdown() { }

        @Override
        public void close(final CloseMode closeMode) { }

        @Override
        public void close() { }

        @Override
        protected <T> Future<T> doExecute(
                final HttpHost target,
                final AsyncRequestProducer requestProducer,
                final AsyncResponseConsumer<T> responseConsumer,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final HttpContext context,
                final FutureCallback<T> callback) {
            @SuppressWarnings("unchecked") final FutureCallback<Void> cb = (FutureCallback<Void>) callback;
            this.lastCallback = cb;
            return new CompletableFuture<>();
        }

        @Override
        @Deprecated
        public void register(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        }
    }

    static final class RecordingScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        final AtomicBoolean shutdownCalled = new AtomicBoolean(false);
        final AtomicBoolean shutdown = new AtomicBoolean(false);
        final AtomicInteger scheduledCount = new AtomicInteger(0);

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownCalled.set(true);
            shutdown.set(true);
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return shutdown.get();
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(final Runnable command) {
            command.run();
        }

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
            scheduledCount.incrementAndGet();
            return new DummyScheduledFuture<>();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
            scheduledCount.incrementAndGet();
            return new DummyScheduledFuture<>();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
            throw new UnsupportedOperationException("not used");
        }
    }

    static final class DummyScheduledFuture<V> implements ScheduledFuture<V> {
        @Override
        public long getDelay(final TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(final Delayed o) {
            return 0;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public V get() {
            return null;
        }

        @Override
        public V get(final long timeout, final TimeUnit unit) {
            return null;
        }
    }
}
