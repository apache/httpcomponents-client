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
package org.apache.hc.client5.http.websocket.client.impl;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.impl.protocol.WebSocketProtocolStrategy;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"resource", "try"})
class WebSocketClientImplTest {

    @Test
    @SuppressWarnings("resource")
    void abstractClientStartsAllRequesters() throws Exception {
        final StubConnPool pool = new StubConnPool();
        final TestRequester primary = new TestRequester(pool);
        final TestRequester extra = new TestRequester(pool);
        final TestClient client = new TestClient(primary, extra);
        boolean closed = false;
        try {
            client.start();

            Assertions.assertTrue(primary.started.await(1, TimeUnit.SECONDS));
            Assertions.assertTrue(extra.started.await(1, TimeUnit.SECONDS));
            Assertions.assertTrue(client.isRunning());

            client.initiateShutdown();
            Assertions.assertEquals(1, primary.shutdownCalls);
            Assertions.assertEquals(1, extra.shutdownCalls);

            client.close(CloseMode.GRACEFUL);
            closed = true;
            Assertions.assertEquals(1, primary.closeCalls);
            Assertions.assertEquals(1, extra.closeCalls);
        } finally {
            if (!closed) {
                client.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void internalClientFallsBackToH1OnH2Failure() throws Exception {
        final StubConnPool pool = new StubConnPool();
        final TestRequester requester = new TestRequester(pool);
        final StubProtocol h1 = new StubProtocol();
        final StubProtocol h2 = new StubProtocol();
        h2.failWith = new IllegalStateException("h2 fail");

        final TestInternalClient client = new TestInternalClient(requester, pool, h1, h2);
        boolean closed = false;
        try {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom().enableHttp2(true).build();
            final WebSocket ws = client.connect(URI.create("ws://localhost"), new WebSocketListener() {
                    }, cfg, null)
                    .get(1, TimeUnit.SECONDS);

            Assertions.assertNotNull(ws);
            Assertions.assertEquals(1, h2.calls);
            Assertions.assertEquals(1, h1.calls);
            client.close(CloseMode.GRACEFUL);
            closed = true;
        } finally {
            if (!closed) {
                client.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void internalClientUsesH2WhenAvailable() throws Exception {
        final StubConnPool pool = new StubConnPool();
        final TestRequester requester = new TestRequester(pool);
        final StubProtocol h1 = new StubProtocol();
        final StubProtocol h2 = new StubProtocol();

        final TestInternalClient client = new TestInternalClient(requester, pool, h1, h2);
        boolean closed = false;
        try {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom().enableHttp2(true).build();
            final WebSocket ws = client.connect(URI.create("ws://localhost"), new WebSocketListener() {
                    }, cfg, null)
                    .get(1, TimeUnit.SECONDS);

            Assertions.assertNotNull(ws);
            Assertions.assertEquals(1, h2.calls);
            Assertions.assertEquals(0, h1.calls);
            client.close(CloseMode.GRACEFUL);
            closed = true;
        } finally {
            if (!closed) {
                client.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void internalClientUsesH1WhenH2Disabled() throws Exception {
        final StubConnPool pool = new StubConnPool();
        final TestRequester requester = new TestRequester(pool);
        final StubProtocol h1 = new StubProtocol();
        final StubProtocol h2 = new StubProtocol();

        final TestInternalClient client = new TestInternalClient(requester, pool, h1, h2);
        boolean closed = false;
        try {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom().enableHttp2(false).build();
            final WebSocket ws = client.connect(URI.create("ws://localhost"), new WebSocketListener() {
                    }, cfg, null)
                    .get(1, TimeUnit.SECONDS);

            Assertions.assertNotNull(ws);
            Assertions.assertEquals(0, h2.calls);
            Assertions.assertEquals(1, h1.calls);
            client.close(CloseMode.GRACEFUL);
            closed = true;
        } finally {
            if (!closed) {
                client.close(CloseMode.IMMEDIATE);
            }
        }
    }

    private static final class TestClient extends AbstractWebSocketClient {
        TestClient(final HttpAsyncRequester requester, final HttpAsyncRequester extra) {
            super(requester, r -> new Thread(r, "ws-test"), extra);
        }

        @Override
        protected CompletableFuture<WebSocket> doConnect(
                final URI uri, final WebSocketListener listener, final WebSocketClientConfig cfg, final HttpContext context) {
            return CompletableFuture.completedFuture(new StubWebSocket());
        }
    }

    private static final class TestInternalClient extends InternalWebSocketClientBase {
        private final WebSocketProtocolStrategy h1;
        private final WebSocketProtocolStrategy h2;

        TestInternalClient(final HttpAsyncRequester requester,
                           final ManagedConnPool<HttpHost, IOSession> connPool,
                           final WebSocketProtocolStrategy h1,
                           final WebSocketProtocolStrategy h2) {
            super(requester, connPool, WebSocketClientConfig.custom().build(), r -> new Thread(r, "ws-test"),
                    new NoopH2Requester());
            this.h1 = h1;
            this.h2 = h2;
        }

        @Override
        protected WebSocketProtocolStrategy newH1Protocol(
                final HttpAsyncRequester requester, final ManagedConnPool<HttpHost, IOSession> connPool) {
            return new DelegatingProtocol(() -> h1);
        }

        @Override
        protected WebSocketProtocolStrategy newH2Protocol(final H2MultiplexingRequester requester) {
            return new DelegatingProtocol(() -> h2);
        }
    }

    private static final class DelegatingProtocol implements WebSocketProtocolStrategy {
        private final Supplier<WebSocketProtocolStrategy> delegateSupplier;

        private DelegatingProtocol(final Supplier<WebSocketProtocolStrategy> delegateSupplier) {
            this.delegateSupplier = delegateSupplier;
        }

        @Override
        public CompletableFuture<WebSocket> connect(
                final URI uri,
                final WebSocketListener listener,
                final WebSocketClientConfig cfg,
                final HttpContext context) {
            final WebSocketProtocolStrategy delegate = delegateSupplier.get();
            if (delegate == null) {
                final CompletableFuture<WebSocket> f = new CompletableFuture<>();
                f.completeExceptionally(new IllegalStateException("Protocol not configured"));
                return f;
            }
            return delegate.connect(uri, listener, cfg, context);
        }
    }

    private static final class StubProtocol implements WebSocketProtocolStrategy {
        private int calls;
        private RuntimeException failWith;

        @Override
        public CompletableFuture<WebSocket> connect(final URI uri, final WebSocketListener listener,
                                                    final WebSocketClientConfig cfg, final HttpContext context) {
            calls++;
            if (failWith != null) {
                final CompletableFuture<WebSocket> f = new CompletableFuture<>();
                f.completeExceptionally(failWith);
                return f;
            }
            return CompletableFuture.completedFuture(new StubWebSocket());
        }
    }

    private static final class StubWebSocket implements WebSocket {
        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean ping(final ByteBuffer data) {
            return true;
        }

        @Override
        public boolean pong(final ByteBuffer data) {
            return true;
        }

        @Override
        public boolean sendText(final CharSequence data, final boolean finalFragment) {
            return true;
        }

        @Override
        public boolean sendBinary(final ByteBuffer data, final boolean finalFragment) {
            return true;
        }

        @Override
        public boolean sendTextBatch(final List<CharSequence> fragments, final boolean finalFragment) {
            return true;
        }

        @Override
        public boolean sendBinaryBatch(final List<ByteBuffer> fragments, final boolean finalFragment) {
            return true;
        }

        @Override
        public long queueSize() {
            return 0;
        }

        @Override
        public CompletableFuture<Void> close(final int statusCode, final String reason) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class NoopH2Requester extends H2MultiplexingRequester {
        NoopH2Requester() {
            super(IOReactorConfig.DEFAULT,
                    (ioSession, attachment) -> null,
                    null,
                    null,
                    null,
                    DefaultAddressResolver.INSTANCE,
                    null,
                    null,
                    null
//                    ,
//                    0
            );
        }

        @Override
        public void start() {
        }

        @Override
        public void initiateShutdown() {
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }
    }

    private static final class TestRequester extends HttpAsyncRequester {
        private final CountDownLatch started = new CountDownLatch(1);
        private int shutdownCalls;
        private int closeCalls;

        TestRequester(final ManagedConnPool<HttpHost, IOSession> pool) {
            super(IOReactorConfig.DEFAULT,
                    (ioSession, attachment) -> null,
                    null,
                    null,
                    null,
                    pool,
                    null,
                    null,
                    null,
                    null
            );
        }

        @Override
        public void start() {
            started.countDown();
        }

        @Override
        public void initiateShutdown() {
            shutdownCalls++;
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) {
        }

        @Override
        public void close(final CloseMode closeMode) {
            closeCalls++;
        }
    }

    private static final class StubConnPool implements ManagedConnPool<HttpHost, IOSession> {
        @Override
        public Future<PoolEntry<HttpHost, IOSession>> lease(
                final HttpHost route, final Object state, final Timeout requestTimeout,
                final FutureCallback<PoolEntry<HttpHost, IOSession>> callback) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void release(final PoolEntry<HttpHost, IOSession> entry, final boolean reusable) {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }

        @Override
        public void close() {
        }

        @Override
        public void closeIdle(final TimeValue idleTime) {
        }

        @Override
        public void closeExpired() {
        }

        @Override
        public Set<HttpHost> getRoutes() {
            return Collections.emptySet();
        }

        @Override
        public void setMaxTotal(final int max) {
        }

        @Override
        public int getMaxTotal() {
            return 0;
        }

        @Override
        public void setDefaultMaxPerRoute(final int max) {
        }

        @Override
        public int getDefaultMaxPerRoute() {
            return 0;
        }

        @Override
        public void setMaxPerRoute(final HttpHost route, final int max) {
        }

        @Override
        public int getMaxPerRoute(final HttpHost route) {
            return 0;
        }

        @Override
        public PoolStats getTotalStats() {
            return new PoolStats(0, 0, 0, 0);
        }

        @Override
        public PoolStats getStats(final HttpHost route) {
            return new PoolStats(0, 0, 0, 0);
        }
    }
}
