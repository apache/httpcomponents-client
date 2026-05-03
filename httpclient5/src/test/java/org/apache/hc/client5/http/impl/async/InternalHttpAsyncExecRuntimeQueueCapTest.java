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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.EndpointInfo;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class InternalHttpAsyncExecRuntimeQueueCapTest {

    @Test
    void testRequestsQueuedWhenOverCap() throws Exception {
        final FakeEndpoint endpoint = new FakeEndpoint();
        final FakeManager manager = new FakeManager(endpoint);

        final InternalHttpAsyncExecRuntime runtime = new InternalHttpAsyncExecRuntime(
                LoggerFactory.getLogger("test"),
                manager,
                new NoopInitiator(),
                new NoopPushFactory(),
                TlsConfig.DEFAULT,
                new SharedRequestExecutionQueue(2));

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        runtime.acquireEndpoint("id", new HttpRoute(new HttpHost("localhost", 80)), null, ctx, new NoopRuntimeCallback());

        final LatchingHandler h1 = new LatchingHandler();
        final LatchingHandler h2 = new LatchingHandler();
        final LatchingHandler h3 = new LatchingHandler();

        runtime.execute("r1", h1, ctx);
        runtime.execute("r2", h2, ctx);
        runtime.execute("r3", h3, ctx);

        assertTrue(waitFor(() -> endpoint.executedCount() == 2, 2, TimeUnit.SECONDS), "r1 and r2 should start");
        assertTrue(endpoint.executedIds.contains("r1"));
        assertTrue(endpoint.executedIds.contains("r2"));
        assertTrue(!endpoint.executedIds.contains("r3"), "r3 should be queued, not started yet");

        endpoint.completeOne(); // releases a slot and should trigger queued r3

        assertTrue(waitFor(() -> endpoint.executedIds.contains("r3"), 2, TimeUnit.SECONDS), "r3 should start after slot release");

        assertNull(h1.failedException.get());
        assertNull(h2.failedException.get());
        assertNull(h3.failedException.get());
    }

    @Test
    void testSlotReleasedOnTerminalSignalAllowsNext() throws Exception {
        final FakeEndpoint endpoint = new FakeEndpoint();
        final FakeManager manager = new FakeManager(endpoint);

        final InternalHttpAsyncExecRuntime runtime = new InternalHttpAsyncExecRuntime(
                LoggerFactory.getLogger("test"),
                manager,
                new NoopInitiator(),
                new NoopPushFactory(),
                TlsConfig.DEFAULT,
                new SharedRequestExecutionQueue(1));

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        runtime.acquireEndpoint("id", new HttpRoute(new HttpHost("localhost", 80)), null, ctx, new NoopRuntimeCallback());

        final LatchingHandler h1 = new LatchingHandler();
        final LatchingHandler h2 = new LatchingHandler();

        runtime.execute("r1", h1, ctx);
        runtime.execute("r2", h2, ctx);

        assertTrue(waitFor(() -> endpoint.executedIds.contains("r1"), 2, TimeUnit.SECONDS));
        assertTrue(!endpoint.executedIds.contains("r2"), "r2 should be queued until r1 completes");

        endpoint.completeOne();

        assertTrue(waitFor(() -> endpoint.executedIds.contains("r2"), 2, TimeUnit.SECONDS), "r2 should start after r1 completes");

        assertNull(h1.failedException.get());
        assertNull(h2.failedException.get());
    }

    @Test
    void testRecursiveReentryCausesSOEWithoutCap() {
        final ImmediateFailEndpoint endpoint = new ImmediateFailEndpoint();
        final FakeManager manager = new FakeManager(endpoint);

        // no queue => old synchronous recursion behaviour remains
        final InternalHttpAsyncExecRuntime runtime = new InternalHttpAsyncExecRuntime(
                LoggerFactory.getLogger("test"),
                manager,
                new NoopInitiator(),
                new NoopPushFactory(),
                TlsConfig.DEFAULT,
                null);

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        runtime.acquireEndpoint("id", new HttpRoute(new HttpHost("localhost", 80)), null, ctx, new NoopRuntimeCallback());

        final ReentrantHandler loop = new ReentrantHandler(runtime, ctx);

        assertThrows(StackOverflowError.class, () -> runtime.execute("loop", loop, ctx));
    }

    @Test
    void testCapBreaksRecursiveReentry() throws Exception {
        final ImmediateFailEndpoint endpoint = new ImmediateFailEndpoint();
        final FakeManager manager = new FakeManager(endpoint);

        // queue => no synchronous recursion -> no SOE
        final InternalHttpAsyncExecRuntime runtime = new InternalHttpAsyncExecRuntime(
                LoggerFactory.getLogger("test"),
                manager,
                new NoopInitiator(),
                new NoopPushFactory(),
                TlsConfig.DEFAULT,
                new SharedRequestExecutionQueue(1));

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        runtime.acquireEndpoint("id", new HttpRoute(new HttpHost("localhost", 80)), null, ctx, new NoopRuntimeCallback());

        final CountDownLatch done = new CountDownLatch(1);
        final BoundedReentrantHandler loop = new BoundedReentrantHandler(runtime, ctx, 50, done);

        assertDoesNotThrow(() -> runtime.execute("loop", loop, ctx));
        assertTrue(done.await(2, TimeUnit.SECONDS), "Expected bounded re-entry loop to complete without SOE");
        assertTrue(loop.invocations.get() >= 1);
        assertTrue(loop.lastException.get() instanceof IOException);
    }

    private static boolean waitFor(final Condition condition, final long time, final TimeUnit unit) throws InterruptedException {
        final long deadline = System.nanoTime() + unit.toNanos(time);
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.get();
    }

    @FunctionalInterface
    private interface Condition {
        boolean get();
    }

    private static final class NoopRuntimeCallback implements FutureCallback<AsyncExecRuntime> {
        @Override
        public void completed(final AsyncExecRuntime result) {
        }

        @Override
        public void failed(final Exception ex) {
            fail(ex);
        }

        @Override
        public void cancelled() {
            fail("cancelled");
        }
    }

    private static final class NoopInitiator implements ConnectionInitiator {
        @Override
        public Future<IOSession> connect(final NamedEndpoint endpoint,
                                         final SocketAddress remoteAddress,
                                         final SocketAddress localAddress,
                                         final Timeout timeout,
                                         final Object attachment,
                                         final FutureCallback<IOSession> callback) {
            final CompletableFuture<IOSession> cf = new CompletableFuture<>();
            final UnsupportedOperationException ex = new UnsupportedOperationException("not used");
            cf.completeExceptionally(ex);
            if (callback != null) {
                callback.failed(ex);
            }
            return cf;
        }
    }

    private static final class NoopPushFactory implements HandlerFactory<AsyncPushConsumer> {
        @Override
        public AsyncPushConsumer create(final HttpRequest request, final HttpContext context) {
            return null;
        }
    }

    private static final class FakeManager implements AsyncClientConnectionManager {
        private final AsyncConnectionEndpoint endpoint;

        FakeManager(final AsyncConnectionEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public Future<AsyncConnectionEndpoint> lease(final String id,
                                                     final HttpRoute route,
                                                     final Object state,
                                                     final Timeout requestTimeout,
                                                     final FutureCallback<AsyncConnectionEndpoint> callback) {
            final CompletableFuture<AsyncConnectionEndpoint> cf = CompletableFuture.completedFuture(endpoint);
            if (callback != null) {
                callback.completed(endpoint);
            }
            return cf;
        }

        @Override
        public Future<AsyncConnectionEndpoint> connect(final AsyncConnectionEndpoint endpoint,
                                                       final ConnectionInitiator connectionInitiator,
                                                       final Timeout connectTimeout,
                                                       final Object attachment,
                                                       final HttpContext context,
                                                       final FutureCallback<AsyncConnectionEndpoint> callback) {
            ((FakeEndpoint) this.endpoint).connected = true;
            final CompletableFuture<AsyncConnectionEndpoint> cf = CompletableFuture.completedFuture(endpoint);
            if (callback != null) {
                callback.completed(endpoint);
            }
            return cf;
        }

        @Override
        public void upgrade(final AsyncConnectionEndpoint endpoint,
                            final Object attachment,
                            final HttpContext context) {
        }

        @Override
        public void release(final AsyncConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeEndpoint extends AsyncConnectionEndpoint {
        volatile boolean connected = true;

        private static final class InFlightEntry {
            final String id;
            final AsyncClientExchangeHandler handler;

            InFlightEntry(final String id, final AsyncClientExchangeHandler handler) {
                this.id = id;
                this.handler = handler;
            }
        }

        final ConcurrentLinkedQueue<String> executedIds = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<InFlightEntry> inFlight = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(final String id,
                            final AsyncClientExchangeHandler handler,
                            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                            final HttpContext context) {
            executedIds.add(id);
            inFlight.add(new InFlightEntry(id, handler));
        }

        int executedCount() {
            return executedIds.size();
        }

        void completeOne() {
            final InFlightEntry e = inFlight.poll();
            if (e != null) {
                e.handler.releaseResources();
            }
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
        }

        @Override
        public void close(final CloseMode closeMode) {
            connected = false;
        }

        @Override
        public EndpointInfo getInfo() {
            return null;
        }
    }

    private static class LatchingHandler implements AsyncClientExchangeHandler {
        final AtomicReference<Exception> failedException = new AtomicReference<>();
        final CountDownLatch failLatch = new CountDownLatch(1);

        boolean awaitFailed(final long t, final TimeUnit u) throws InterruptedException {
            return failLatch.await(t, u);
        }

        @Override
        public void produceRequest(final RequestChannel channel, final HttpContext context) {
        }

        @Override
        public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context) {
        }

        @Override
        public void consumeInformation(final HttpResponse response, final HttpContext context) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) {
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) {
        }

        @Override
        public void consume(final ByteBuffer src) {
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) {
        }

        @Override
        public void releaseResources() {
        }

        @Override
        public void failed(final Exception cause) {
            failedException.compareAndSet(null, Objects.requireNonNull(cause));
            failLatch.countDown();
        }
    }

    /**
     * Endpoint that synchronously fails any handler passed to execute().
     */
    private static final class ImmediateFailEndpoint extends AsyncConnectionEndpoint {
        volatile boolean connected = true;

        @Override
        public void execute(final String id,
                            final AsyncClientExchangeHandler handler,
                            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                            final HttpContext context) {
            handler.failed(new IOException("immediate failure"));
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
        }

        @Override
        public void close(final CloseMode closeMode) {
            connected = false;
        }

        @Override
        public EndpointInfo getInfo() {
            return null;
        }
    }

    private static final class ReentrantHandler implements AsyncClientExchangeHandler {
        private final InternalHttpAsyncExecRuntime runtime;
        private final HttpClientContext ctx;
        final AtomicReference<Exception> lastException = new AtomicReference<>();

        ReentrantHandler(final InternalHttpAsyncExecRuntime runtime, final HttpClientContext ctx) {
            this.runtime = runtime;
            this.ctx = ctx;
        }

        @Override
        public void failed(final Exception cause) {
            lastException.set(cause);
            runtime.execute("loop/reenter", this, ctx);
        }

        @Override
        public void produceRequest(final RequestChannel channel, final HttpContext context) {
        }

        @Override
        public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context) {
        }

        @Override
        public void consumeInformation(final HttpResponse response, final HttpContext context) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) {
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) {
        }

        @Override
        public void consume(final ByteBuffer src) {
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) {
        }

        @Override
        public void releaseResources() {
        }
    }

    private static final class BoundedReentrantHandler implements AsyncClientExchangeHandler {
        private final InternalHttpAsyncExecRuntime runtime;
        private final HttpClientContext ctx;
        private final AtomicInteger remaining;
        private final CountDownLatch done;

        final AtomicInteger invocations = new AtomicInteger(0);
        final AtomicReference<Exception> lastException = new AtomicReference<>();

        BoundedReentrantHandler(final InternalHttpAsyncExecRuntime runtime,
                                final HttpClientContext ctx,
                                final int maxReentries,
                                final CountDownLatch done) {
            this.runtime = runtime;
            this.ctx = ctx;
            this.remaining = new AtomicInteger(maxReentries);
            this.done = done;
        }

        @Override
        public void failed(final Exception cause) {
            invocations.incrementAndGet();
            lastException.set(cause);
            if (remaining.getAndDecrement() > 0) {
                runtime.execute("loop/reenter", this, ctx);
            } else {
                done.countDown();
            }
        }

        @Override
        public void produceRequest(final RequestChannel channel, final HttpContext context) {
        }

        @Override
        public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context) {
        }

        @Override
        public void consumeInformation(final HttpResponse response, final HttpContext context) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) {
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) {
        }

        @Override
        public void consume(final ByteBuffer src) {
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) {
        }

        @Override
        public void releaseResources() {
        }
    }

}
