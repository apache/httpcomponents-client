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
import java.util.concurrent.RejectedExecutionException;
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
    void testFailFastWhenQueueFull() throws Exception {
        final FakeEndpoint endpoint = new FakeEndpoint();
        final FakeManager manager = new FakeManager(endpoint);
        final InternalHttpAsyncExecRuntime runtime = new InternalHttpAsyncExecRuntime(
                LoggerFactory.getLogger("test"),
                manager,
                new NoopInitiator(),
                new NoopPushFactory(),
                TlsConfig.DEFAULT,
                2,
                new AtomicInteger()
        );

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        runtime.acquireEndpoint("id", new HttpRoute(new HttpHost("localhost", 80)), null, ctx, new FutureCallback<AsyncExecRuntime>() {
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
        });

        final CountDownLatch rejected = new CountDownLatch(1);

        final LatchingHandler h1 = new LatchingHandler();
        final LatchingHandler h2 = new LatchingHandler();
        runtime.execute("r1", h1, ctx);
        runtime.execute("r2", h2, ctx);

        final LatchingHandler h3 = new LatchingHandler() {
            @Override
            public void failed(final Exception cause) {
                super.failed(cause);
                rejected.countDown();
            }
        };
        runtime.execute("r3", h3, ctx);

        assertTrue(rejected.await(2, TimeUnit.SECONDS), "r3 should be failed fast");
        assertTrue(h3.failedException.get() instanceof RejectedExecutionException);
        assertNull(h1.failedException.get());
        assertNull(h2.failedException.get());
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
                1,
                new AtomicInteger()
        );

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        runtime.acquireEndpoint("id", new HttpRoute(new HttpHost("localhost", 80)), null, ctx,
                new FutureCallback<AsyncExecRuntime>() {
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
                });

        final LatchingHandler h1 = new LatchingHandler();
        runtime.execute("r1", h1, ctx);

        final LatchingHandler h2 = new LatchingHandler();
        runtime.execute("r2", h2, ctx);
        assertTrue(h2.awaitFailed(2, TimeUnit.SECONDS));
        assertTrue(h2.failedException.get() instanceof RejectedExecutionException);

        // free the slot via releaseResources(), not failed()
        endpoint.completeOne();

        final LatchingHandler h3 = new LatchingHandler();
        runtime.execute("r3", h3, ctx);
        Thread.sleep(150);
        assertNull(h3.failedException.get(), "r3 should not be rejected after slot released");
        h3.cancel();
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
        private final ConcurrentLinkedQueue<AsyncClientExchangeHandler> inFlight = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(final String id,
                            final AsyncClientExchangeHandler handler,
                            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                            final HttpContext context) {
            // keep the guarded handler so tests can signal terminal events
            inFlight.add(handler);
        }

        // helpers for tests
        void failOne(final Exception ex) {
            final AsyncClientExchangeHandler h = inFlight.poll();
            if (h != null) {
                h.failed(ex);
            }
        }

        void cancelOne() {
            final AsyncClientExchangeHandler h = inFlight.poll();
            if (h != null) {
                h.cancel();
            }
        }

        void completeOne() {
            final AsyncClientExchangeHandler h = inFlight.poll();
            if (h != null) {
                h.releaseResources();
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

    @Test
    void testRecursiveReentryCausesSOEWithoutCap() {
        final ImmediateFailEndpoint endpoint = new ImmediateFailEndpoint();
        final FakeManager manager = new FakeManager(endpoint);

        final InternalHttpAsyncExecRuntime runtime = new InternalHttpAsyncExecRuntime(
                LoggerFactory.getLogger("test"),
                manager,
                new NoopInitiator(),
                new NoopPushFactory(),
                TlsConfig.DEFAULT,
                -1,
                null // no cap, no counter
        );

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        runtime.acquireEndpoint("id", new HttpRoute(new HttpHost("localhost", 80)), null, ctx,
                new FutureCallback<AsyncExecRuntime>() {
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
                });

        final ReentrantHandler loop = new ReentrantHandler(runtime, ctx);

        assertThrows(StackOverflowError.class, () -> {
            runtime.execute("loop", loop, ctx); // execute -> endpoint.execute -> failed() -> execute -> ...
        });
    }

    @Test
    void testCapBreaksRecursiveReentry() throws Exception {
        final ImmediateFailEndpoint endpoint = new ImmediateFailEndpoint();
        final FakeManager manager = new FakeManager(endpoint);

        final InternalHttpAsyncExecRuntime runtime = new InternalHttpAsyncExecRuntime(
                LoggerFactory.getLogger("test"),
                manager,
                new NoopInitiator(),
                new NoopPushFactory(),
                TlsConfig.DEFAULT,
                1,
                new AtomicInteger()
        );

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        runtime.acquireEndpoint("id", new HttpRoute(new HttpHost("localhost", 80)), null, ctx,
                new FutureCallback<AsyncExecRuntime>() {
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
                });

        final ReentrantHandler loop = new ReentrantHandler(runtime, ctx);

        // Should NOT blow the stack; the re-entrant call should be rejected.
        runtime.execute("loop", loop, ctx);
        // allow the immediate fail+re-submit path to run
        Thread.sleep(50);

        assertTrue(loop.lastException.get() instanceof RejectedExecutionException,
                "Expected rejection to break the recursion");
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
            // Re-enter only if this was NOT the cap rejecting us
            if (!(cause instanceof RejectedExecutionException)) {
                runtime.execute("loop/reenter", this, ctx);
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
