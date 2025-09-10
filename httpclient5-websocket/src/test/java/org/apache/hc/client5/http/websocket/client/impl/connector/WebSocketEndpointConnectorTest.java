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
package org.apache.hc.client5.http.websocket.client.impl.connector;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebSocketEndpointConnectorTest {

    @Test
    void connectReturnsEndpointWhenPoolEntryHasConnection() throws Exception {
        final StubConnPool pool = new StubConnPool();
        final StubSession session = new StubSession(true);
        final PoolEntry<HttpHost, IOSession> entry = new PoolEntry<>(new HttpHost("http", "localhost", 80));
        entry.assignConnection(session);
        pool.entry = entry;

        final TestRequester requester = new TestRequester(pool);
        final WebSocketEndpointConnector connector = new WebSocketEndpointConnector(requester, pool);

        final Future<WebSocketEndpointConnector.ProtoEndpoint> future =
                connector.connect(new HttpHost("http", "localhost", 80), Timeout.ofSeconds(1), null, null);
        final WebSocketEndpointConnector.ProtoEndpoint endpoint = future.get(1, TimeUnit.SECONDS);

        Assertions.assertNotNull(endpoint);
        Assertions.assertEquals(0, requester.requestSessionCalls);
        Assertions.assertTrue(endpoint.isConnected());
    }

    @Test
    void connectRequestsSessionWhenPoolEntryIsEmpty() throws Exception {
        final StubConnPool pool = new StubConnPool();
        pool.entry = new PoolEntry<>(new HttpHost("http", "localhost", 80));
        final TestRequester requester = new TestRequester(pool);
        requester.nextSession = new StubSession(true);

        final WebSocketEndpointConnector connector = new WebSocketEndpointConnector(requester, pool);
        final Timeout timeout = Timeout.ofSeconds(5);
        final Future<WebSocketEndpointConnector.ProtoEndpoint> future =
                connector.connect(new HttpHost("http", "localhost", 80), timeout, "attach", null);
        final WebSocketEndpointConnector.ProtoEndpoint endpoint = future.get(1, TimeUnit.SECONDS);

        Assertions.assertNotNull(endpoint);
        Assertions.assertEquals(1, requester.requestSessionCalls);
        Assertions.assertNotNull(pool.entry.getConnection());
        Assertions.assertEquals(timeout, ((StubSession) pool.entry.getConnection()).socketTimeout);
    }

    @Test
    void failedSessionRequestReleasesEntry() throws Exception {
        final StubConnPool pool = new StubConnPool();
        pool.entry = new PoolEntry<>(new HttpHost("http", "localhost", 80));
        final TestRequester requester = new TestRequester(pool);
        requester.failWith = new IllegalStateException("boom");

        final WebSocketEndpointConnector connector = new WebSocketEndpointConnector(requester, pool);
        final Future<WebSocketEndpointConnector.ProtoEndpoint> future =
                connector.connect(new HttpHost("http", "localhost", 80), Timeout.ofSeconds(1), null, null);

        Assertions.assertThrows(Exception.class, () -> future.get(1, TimeUnit.SECONDS));
        Assertions.assertNotNull(pool.lastReleaseEntry);
        Assertions.assertFalse(pool.lastReusable);
    }

    @Test
    void protoEndpointThrowsWhenNotProtocolSession() {
        final StubConnPool pool = new StubConnPool();
        final StubSession session = new StubSession(true);
        final PoolEntry<HttpHost, IOSession> entry = new PoolEntry<>(new HttpHost("http", "localhost", 80));
        entry.assignConnection(session);
        pool.entry = entry;

        final WebSocketEndpointConnector.ProtoEndpoint endpoint =
                new WebSocketEndpointConnector(new TestRequester(pool), pool).new ProtoEndpoint(entry);

        Assertions.assertThrows(IllegalStateException.class, endpoint::getProtocolIOSession);
    }

    private static final class TestRequester extends HttpAsyncRequester {
        private int requestSessionCalls;
        private IOSession nextSession;
        private Exception failWith;

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
        public Future<IOSession> requestSession(
                final HttpHost host,
                final Timeout timeout,
                final Object attachment,
                final FutureCallback<IOSession> callback) {
            requestSessionCalls++;
            if (failWith != null) {
                if (callback != null) {
                    callback.failed(failWith);
                }
                final CompletableFuture<IOSession> f = new CompletableFuture<>();
                f.completeExceptionally(failWith);
                return f;
            }
            final IOSession session = nextSession != null ? nextSession : new StubSession(true);
            if (callback != null) {
                callback.completed(session);
            }
            return CompletableFuture.completedFuture(session);
        }
    }

    private static final class StubConnPool implements ManagedConnPool<HttpHost, IOSession> {
        private PoolEntry<HttpHost, IOSession> entry;
        private PoolEntry<HttpHost, IOSession> lastReleaseEntry;
        private boolean lastReusable;

        @Override
        public Future<PoolEntry<HttpHost, IOSession>> lease(
                final HttpHost route,
                final Object state,
                final Timeout requestTimeout,
                final FutureCallback<PoolEntry<HttpHost, IOSession>> callback) {
            if (callback != null) {
                callback.completed(entry);
            }
            return CompletableFuture.completedFuture(entry);
        }

        @Override
        public void release(final PoolEntry<HttpHost, IOSession> entry, final boolean reusable) {
            this.lastReleaseEntry = entry;
            this.lastReusable = reusable;
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

    private static final class StubSession implements IOSession {
        private final Lock lock = new ReentrantLock();
        private final boolean open;
        private Timeout socketTimeout = Timeout.DISABLED;

        private StubSession(final boolean open) {
            this.open = open;
        }

        @Override
        public int read(final ByteBuffer dst) {
            return 0;
        }

        @Override
        public int write(final ByteBuffer src) {
            return 0;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }

        @Override
        public String getId() {
            return "stub";
        }

        @Override
        public IOEventHandler getHandler() {
            return null;
        }

        @Override
        public void upgrade(final IOEventHandler handler) {
        }

        @Override
        public Lock getLock() {
            return lock;
        }

        @Override
        public void enqueue(final Command command, final Command.Priority priority) {
        }

        @Override
        public boolean hasCommands() {
            return false;
        }

        @Override
        public Command poll() {
            return null;
        }

        @Override
        public ByteChannel channel() {
            return this;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public int getEventMask() {
            return 0;
        }

        @Override
        public void setEventMask(final int ops) {
        }

        @Override
        public void setEvent(final int op) {
        }

        @Override
        public void clearEvent(final int op) {
        }

        @Override
        public Status getStatus() {
            return open ? Status.ACTIVE : Status.CLOSED;
        }

        @Override
        public Timeout getSocketTimeout() {
            return socketTimeout;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            socketTimeout = timeout != null ? timeout : Timeout.DISABLED;
        }

        @Override
        public long getLastReadTime() {
            return 0;
        }

        @Override
        public long getLastWriteTime() {
            return 0;
        }

        @Override
        public long getLastEventTime() {
            return 0;
        }

        @Override
        public void updateReadTime() {
        }

        @Override
        public void updateWriteTime() {
        }
    }
}
