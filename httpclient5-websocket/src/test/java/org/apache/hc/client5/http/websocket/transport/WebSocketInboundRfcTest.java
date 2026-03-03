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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ProtocolUpgradeHandler;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.junit.jupiter.api.Test;

class WebSocketInboundRfcTest {

    @Test
    void closeReasonInvalidUtf8_closesWith1007() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketInbound inbound = newInbound(listener);
        final ProtocolIOSession session = new StubSession();

        final byte[] payload = new byte[]{0x03, (byte) 0xE8, (byte) 0xC3, (byte) 0x28}; // 1000 + invalid UTF-8
        inbound.onInputReady(session, unmaskedFrame(FrameOpcode.CLOSE, true, payload));

        assertEquals(1007, listener.closeCode.get());
    }

    @Test
    void closeCodeInvalidOnWire_closesWith1002() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketInbound inbound = newInbound(listener);
        final ProtocolIOSession session = new StubSession();

        final byte[] payload = new byte[]{0x03, (byte) 0xED}; // 1005 (invalid on wire)
        inbound.onInputReady(session, unmaskedFrame(FrameOpcode.CLOSE, true, payload));

        assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void dataFrameWhileFragmentedMessage_closesWith1002() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketInbound inbound = newInbound(listener);
        final ProtocolIOSession session = new StubSession();

        inbound.onInputReady(session, unmaskedFrame(FrameOpcode.TEXT, false, new byte[]{0x61}));
        inbound.onInputReady(session, unmaskedFrame(FrameOpcode.BINARY, true, new byte[]{0x62}));

        assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void unexpectedContinuation_closesWith1002() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketInbound inbound = newInbound(listener);
        final ProtocolIOSession session = new StubSession();

        inbound.onInputReady(session, unmaskedFrame(FrameOpcode.CONT, true, new byte[]{0x01}));

        assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void fragmentedControlFrame_closesWith1002() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketInbound inbound = newInbound(listener);
        final ProtocolIOSession session = new StubSession();

        inbound.onInputReady(session, unmaskedFrame(FrameOpcode.PING, false, new byte[0]));

        assertEquals(1002, listener.closeCode.get());
    }

    private static WebSocketInbound newInbound(final CloseCaptureListener listener) {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .enablePerMessageDeflate(false)
                .build();
        final WebSocketSessionState state = new WebSocketSessionState(
                new StubSession(),
                listener,
                cfg,
                null);
        final WebSocketOutbound outbound = new WebSocketOutbound(state);
        return new WebSocketInbound(state, outbound);
    }

    private static ByteBuffer unmaskedFrame(final int opcode, final boolean fin, final byte[] payload) {
        final int len = payload != null ? payload.length : 0;
        final ByteBuffer buf = ByteBuffer.allocate(2 + len);
        buf.put((byte) ((fin ? 0x80 : 0x00) | (opcode & 0x0F)));
        buf.put((byte) len);
        if (len > 0) {
            buf.put(payload);
        }
        buf.flip();
        return buf;
    }

    private static final class CloseCaptureListener implements WebSocketListener {
        private final AtomicInteger closeCode = new AtomicInteger(-1);

        @Override
        public void onClose(final int code, final String reason) {
            closeCode.compareAndSet(-1, code);
        }
    }

    private static final class StubSession implements ProtocolIOSession {
        private final Lock lock = new ReentrantLock();
        private volatile Timeout socketTimeout = Timeout.DISABLED;
        private volatile Status status = Status.ACTIVE;

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
            return status == Status.ACTIVE;
        }

        @Override
        public void close() {
            status = Status.CLOSED;
        }

        @Override
        public void close(final CloseMode closeMode) {
            status = Status.CLOSED;
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
            return status;
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
