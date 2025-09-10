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
package org.apache.hc.client5.http.websocket.transport;

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ProtocolUpgradeHandler;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebSocketUpgraderTest {

    @Test
    void upgradeInstallsHandlerAndExposesWebSocket() {
        final StubSession session = new StubSession(false);
        final WebSocketUpgrader upgrader = new WebSocketUpgrader(
                new WebSocketListener() { }, WebSocketClientConfig.custom().build(), null);
        final CapturingCallback callback = new CapturingCallback();

        upgrader.upgrade(session, callback);

        Assertions.assertNotNull(session.lastHandler);
        Assertions.assertNotNull(upgrader.getWebSocket());
        Assertions.assertSame(session, callback.completed.get());
        Assertions.assertNull(callback.failed.get());
    }

    @Test
    void upgradeFailureReportsCallback() {
        final StubSession session = new StubSession(true);
        final WebSocketUpgrader upgrader = new WebSocketUpgrader(
                new WebSocketListener() { }, WebSocketClientConfig.custom().build(), null);
        final CapturingCallback callback = new CapturingCallback();

        upgrader.upgrade(session, callback);

        Assertions.assertNull(callback.completed.get());
        Assertions.assertNotNull(callback.failed.get());
    }

    private static final class CapturingCallback implements FutureCallback<ProtocolIOSession> {
        private final AtomicReference<ProtocolIOSession> completed = new AtomicReference<>();
        private final AtomicReference<Exception> failed = new AtomicReference<>();

        @Override
        public void completed(final ProtocolIOSession result) {
            completed.compareAndSet(null, result);
        }

        @Override
        public void failed(final Exception ex) {
            failed.compareAndSet(null, ex);
        }

        @Override
        public void cancelled() {
        }
    }

    private static final class StubSession implements ProtocolIOSession {
        private final Lock lock = new ReentrantLock();
        private final boolean failUpgrade;
        private volatile IOEventHandler lastHandler;

        private StubSession(final boolean failUpgrade) {
            this.failUpgrade = failUpgrade;
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
            return true;
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
            if (failUpgrade) {
                throw new IllegalStateException("upgrade failed");
            }
            lastHandler = handler;
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
        public IOSession.Status getStatus() {
            return IOSession.Status.ACTIVE;
        }

        @Override
        public Timeout getSocketTimeout() {
            return Timeout.DISABLED;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
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

        @Override
        public void startTls(final SSLContext sslContext, final NamedEndpoint endpoint, final SSLBufferMode sslBufferMode,
                             final SSLSessionInitializer initializer, final SSLSessionVerifier verifier,
                             final Timeout handshakeTimeout) throws UnsupportedOperationException {
        }

        @Override
        public TlsDetails getTlsDetails() {
            return null;
        }

        @Override
        public NamedEndpoint getInitialEndpoint() {
            return null;
        }

        @Override
        public void registerProtocol(final String protocolId, final ProtocolUpgradeHandler upgradeHandler) {
        }

        @Override
        public void switchProtocol(final String protocolId, final FutureCallback<ProtocolIOSession> callback)
                throws UnsupportedOperationException {
            if (callback != null) {
                callback.failed(new UnsupportedOperationException());
            }
        }
    }
}
