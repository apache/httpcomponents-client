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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.StreamControl;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLIOSession;
import org.apache.hc.core5.reactor.ssl.SSLMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Timeout;

/**
 * {@link ProtocolIOSession} backed by a single HTTP/2 CONNECT stream.
 * <p>
 * Supports optional TLS upgrade via {@link #startTls} for establishing
 * secure tunnels to the target endpoint.
 * </p>
 *
 * @since 5.7
 */
final class H2TunnelProtocolIOSession implements ProtocolIOSession {

    private static final IOEventHandler NOOP_HANDLER = new IOEventHandler() {

        @Override
        public void connected(final IOSession session) {
        }

        @Override
        public void inputReady(final IOSession session, final ByteBuffer src) {
        }

        @Override
        public void outputReady(final IOSession session) {
        }

        @Override
        public void timeout(final IOSession session, final Timeout timeout) {
        }

        @Override
        public void exception(final IOSession session, final Exception cause) {
        }

        @Override
        public void disconnected(final IOSession session) {
        }
    };

    private final NamedEndpoint initialEndpoint;
    private final H2TunnelRawIOSession ioSession;
    private final AtomicReference<SSLIOSession> tlsSessionRef;
    private final AtomicReference<IOSession> currentSessionRef;

    H2TunnelProtocolIOSession(
            final IOSession physicalSession,
            final NamedEndpoint initialEndpoint,
            final Timeout socketTimeout,
            final StreamControl streamControl) {
        this.initialEndpoint = initialEndpoint;
        this.ioSession = new H2TunnelRawIOSession(physicalSession, socketTimeout, streamControl);
        this.tlsSessionRef = new AtomicReference<>();
        this.currentSessionRef = new AtomicReference<>(ioSession);
        this.ioSession.upgrade(NOOP_HANDLER);
    }

    void bindStreamControl(final StreamControl streamControl) {
        ioSession.bindStreamControl(streamControl);
    }

    void attachChannel(final DataStreamChannel channel) {
        ioSession.attachChannel(channel);
    }

    void updateCapacityChannel(final CapacityChannel capacityChannel) throws IOException {
        ioSession.updateCapacityChannel(capacityChannel);
    }

    int available() {
        return ioSession.available();
    }

    void onInput(final ByteBuffer src) throws IOException {
        final ByteBuffer handlerSrc = src != null ? src.asReadOnlyBuffer() : null;

        ioSession.appendInput(src);

        final IOSession currentSession = currentSessionRef.get();
        final IOEventHandler handler = currentSession.getHandler();
        if (handler != null) {
            handler.inputReady(currentSession, handlerSrc);
            if (handlerSrc != null) {
                final int consumed = handlerSrc.position();
                if (consumed > 0) {
                    ioSession.discardInbound(consumed);
                }
            }
        }

        if (ioSession.available() > 0) {
            ioSession.requestOutput();
        }
    }

    void onOutputReady() throws IOException {
        final IOSession currentSession = currentSessionRef.get();
        final IOEventHandler handler = currentSession.getHandler();
        if (handler != null) {
            handler.outputReady(currentSession);
        }
        ioSession.flushOutput();
        if (ioSession.available() > 0) {
            ioSession.requestOutput();
        }
    }

    void onRemoteStreamEnd() {
        ioSession.onRemoteStreamEnd();
        final IOSession currentSession = currentSessionRef.get();
        final IOEventHandler handler = currentSession.getHandler();
        if (handler != null) {
            handler.disconnected(currentSession);
        }
    }

    @Override
    public NamedEndpoint getInitialEndpoint() {
        return initialEndpoint;
    }

    @Override
    public IOEventHandler getHandler() {
        return currentSessionRef.get().getHandler();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        currentSessionRef.get().upgrade(handler);
    }

    @Override
    public Lock getLock() {
        return ioSession.getLock();
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        currentSessionRef.get().enqueue(command, priority);
    }

    @Override
    public boolean hasCommands() {
        return currentSessionRef.get().hasCommands();
    }

    @Override
    public Command poll() {
        return currentSessionRef.get().poll();
    }

    @Override
    public ByteChannel channel() {
        return currentSessionRef.get().channel();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ioSession.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return ioSession.getLocalAddress();
    }

    @Override
    public int getEventMask() {
        return currentSessionRef.get().getEventMask();
    }

    @Override
    public void setEventMask(final int ops) {
        currentSessionRef.get().setEventMask(ops);
    }

    @Override
    public void setEvent(final int op) {
        currentSessionRef.get().setEvent(op);
    }

    @Override
    public void clearEvent(final int op) {
        currentSessionRef.get().clearEvent(op);
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (closeMode == CloseMode.IMMEDIATE) {
            ioSession.close(closeMode);
        } else {
            currentSessionRef.get().close(closeMode);
        }
    }

    @Override
    public Status getStatus() {
        return currentSessionRef.get().getStatus();
    }

    @Override
    public Timeout getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public long getLastReadTime() {
        return ioSession.getLastReadTime();
    }

    @Override
    public long getLastWriteTime() {
        return ioSession.getLastWriteTime();
    }

    @Override
    public long getLastEventTime() {
        return ioSession.getLastEventTime();
    }

    @Override
    public void updateReadTime() {
        ioSession.updateReadTime();
    }

    @Override
    public void updateWriteTime() {
        ioSession.updateWriteTime();
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        return currentSessionRef.get().read(dst);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return currentSessionRef.get().write(src);
    }

    @Override
    public boolean isOpen() {
        return currentSessionRef.get().isOpen();
    }

    @Override
    public String getId() {
        return ioSession.getId();
    }

    @Override
    public void startTls(
            final SSLContext sslContext,
            final NamedEndpoint endpoint,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier,
            final Timeout handshakeTimeout) {
        startTls(sslContext, endpoint, sslBufferMode, initializer, verifier, handshakeTimeout, null);
    }

    @Override
    public void startTls(
            final SSLContext sslContext,
            final NamedEndpoint endpoint,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier,
            final Timeout handshakeTimeout,
            final FutureCallback<TransportSecurityLayer> callback) {

        final SSLIOSession sslioSession = new SSLIOSession(
                endpoint != null ? endpoint : initialEndpoint,
                ioSession,
                SSLMode.CLIENT,
                sslContext,
                sslBufferMode,
                initializer,
                verifier,
                handshakeTimeout,
                null,
                null,
                new CallbackContribution<SSLSession>(callback) {

                    @Override
                    public void completed(final SSLSession sslSession) {
                        if (callback != null) {
                            callback.completed(H2TunnelProtocolIOSession.this);
                        }
                    }

                });

        if (tlsSessionRef.compareAndSet(null, sslioSession)) {
            currentSessionRef.set(sslioSession);
        } else {
            throw new IllegalStateException("TLS already activated");
        }

        try {
            sslioSession.beginHandshake(this);
        } catch (final Exception ex) {
            if (callback != null) {
                callback.failed(ex);
            }
            close(CloseMode.IMMEDIATE);
        }
    }

    @Override
    public TlsDetails getTlsDetails() {
        final SSLIOSession sslIoSession = tlsSessionRef.get();
        return sslIoSession != null ? sslIoSession.getTlsDetails() : null;
    }

    @Override
    public int getPendingCommandCount() {
        return currentSessionRef.get().getPendingCommandCount();
    }
}
