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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
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
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class InternalH2AsyncExecRuntimeQueueCapTest {

    private static InternalH2AsyncExecRuntime newRuntime(final int maxQueued) {
        final IOSession ioSession = newImmediateFailSession();
        final FakeH2ConnPool connPool = new FakeH2ConnPool(ioSession);
        final AtomicInteger queued = maxQueued > 0 ? new AtomicInteger(0) : null;
        return new InternalH2AsyncExecRuntime(
                LoggerFactory.getLogger("test"),
                connPool,
                new NoopPushFactory(),
                maxQueued,
                queued);
    }

    private static void acquireEndpoint(
            final InternalH2AsyncExecRuntime runtime,
            final HttpClientContext ctx) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        runtime.acquireEndpoint(
                "id",
                new HttpRoute(new HttpHost("localhost", 80)),
                null,
                ctx,
                new FutureCallback<AsyncExecRuntime>() {
                    @Override
                    public void completed(final AsyncExecRuntime result) {
                        latch.countDown();
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
        assertTrue(latch.await(2, TimeUnit.SECONDS), "endpoint should be acquired");
    }

    /**
     * With no cap (maxQueued &lt;= 0) the recursive re-entry path should blow the stack.
     * This documents the pathological behaviour without queue protection.
     */
    @Test
    void testRecursiveReentryCausesSOEWithoutCap() throws Exception {
        final InternalH2AsyncExecRuntime runtime = newRuntime(-1);

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        acquireEndpoint(runtime, ctx);

        final ReentrantHandler loop = new ReentrantHandler(runtime, ctx);

        assertThrows(StackOverflowError.class, () -> {
            runtime.execute("loop", loop, ctx);
        });
    }

    /**
     * With a cap of 1, the second re-entrant execute call must be rejected and
     * the recursion broken.
     */
    @Test
    void testCapBreaksRecursiveReentry() throws Exception {
        final InternalH2AsyncExecRuntime runtime = newRuntime(1);

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom().build());

        acquireEndpoint(runtime, ctx);

        final ReentrantHandler loop = new ReentrantHandler(runtime, ctx);

        runtime.execute("loop", loop, ctx);
        // immediate fail path runs synchronously; small wait is just defensive
        Thread.sleep(50);

        assertTrue(loop.lastException.get() instanceof RejectedExecutionException,
                "Expected queue rejection to break recursion");
    }

    /**
     * Very small fake pool that always returns the same IOSession.
     */
    private static final class FakeH2ConnPool extends InternalH2ConnPool {

        private final IOSession session;

        FakeH2ConnPool(final IOSession session) {
            super(null, null, null);
            this.session = session;
        }

        @Override
        public Future<IOSession> getSession(
                final HttpRoute route,
                final Timeout timeout,
                final FutureCallback<IOSession> callback) {
            final CompletableFuture<IOSession> cf = new CompletableFuture<>();
            cf.complete(session);
            if (callback != null) {
                callback.completed(session);
            }
            return cf;
        }

    }

    /**
     * IOSession that immediately fails any RequestExecutionCommand passed
     * to enqueue(...), simulating an I/O failure that calls handler.failed(...)
     * synchronously.
     */
    private static IOSession newImmediateFailSession() {
        return (IOSession) Proxy.newProxyInstance(
                IOSession.class.getClassLoader(),
                new Class<?>[]{IOSession.class},
                (proxy, method, args) -> {
                    final String name = method.getName();
                    if ("isOpen".equals(name)) {
                        return Boolean.TRUE;
                    }
                    if ("enqueue".equals(name)) {
                        final Command cmd = (Command) args[0];
                        if (cmd instanceof RequestExecutionCommand) {
                            ((RequestExecutionCommand) cmd).failed(new IOException("immediate failure"));
                        }
                        return null;
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        if (method.getReturnType() == boolean.class) {
                            return false;
                        }
                        if (method.getReturnType() == int.class) {
                            return 0;
                        }
                        if (method.getReturnType() == long.class) {
                            return 0L;
                        }
                    }
                    return null;
                });
    }

    private static final class NoopPushFactory implements HandlerFactory<AsyncPushConsumer> {
        @Override
        public AsyncPushConsumer create(final HttpRequest request, final HttpContext context) {
            return null;
        }
    }

    private static final class ReentrantHandler implements AsyncClientExchangeHandler {

        private final InternalH2AsyncExecRuntime runtime;
        private final HttpClientContext context;
        final AtomicReference<Exception> lastException = new AtomicReference<>();

        ReentrantHandler(final InternalH2AsyncExecRuntime runtime, final HttpClientContext context) {
            this.runtime = runtime;
            this.context = context;
        }

        @Override
        public void failed(final Exception cause) {
            lastException.set(cause);
            if (!(cause instanceof RejectedExecutionException)) {
                runtime.execute("loop/reenter", this, context);
            }
        }

        @Override
        public void produceRequest(final RequestChannel channel, final HttpContext context) {
        }

        @Override
        public void consumeResponse(
                final HttpResponse response,
                final EntityDetails entityDetails,
                final HttpContext context) {
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
        public void streamEnd(final java.util.List<? extends Header> trailers) {
        }

        @Override
        public void releaseResources() {
        }

    }

}
