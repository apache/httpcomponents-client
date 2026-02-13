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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.EventMask;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Timeout;

/**
 * RFC6455/7692 WebSocket handler front-end. Delegates to WsInbound / WsOutbound.
 */
@Internal
public final class WebSocketIoHandler implements IOEventHandler {

    private final WebSocketSessionState state;
    private final WebSocketInbound inbound;
    private final WebSocketOutbound outbound;
    private final AsyncClientEndpoint endpoint;
    private final AtomicBoolean endpointReleased;

    public WebSocketIoHandler(final ProtocolIOSession session,
                              final WebSocketListener listener,
                              final WebSocketClientConfig cfg,
                              final ExtensionChain chain,
                              final AsyncClientEndpoint endpoint) {
        this.state = new WebSocketSessionState(session, listener, cfg, chain);
        this.outbound = new WebSocketOutbound(state);
        this.inbound = new WebSocketInbound(state, outbound);
        this.endpoint = endpoint;
        this.endpointReleased = new AtomicBoolean(false);
    }

    /**
     * Expose the application WebSocket facade.
     */
    public WebSocket exposeWebSocket() {
        return outbound.facade();
    }

    // ---- IOEventHandler ----
    @Override
    public void connected(final IOSession ioSession) {
        inbound.onConnected(ioSession);
    }

    @Override
    public void inputReady(final IOSession ioSession, final ByteBuffer src) {
        inbound.onInputReady(ioSession, src);
    }

    @Override
    public void outputReady(final IOSession ioSession) {
        outbound.onOutputReady(ioSession);
    }

    @Override
    public void timeout(final IOSession ioSession, final Timeout timeout) {
        inbound.onTimeout(ioSession, timeout);
        // Best-effort graceful close on timeout
        ioSession.close(CloseMode.GRACEFUL);
    }

    @Override
    public void exception(final IOSession ioSession, final Exception cause) {
        inbound.onException(ioSession, cause);
        ioSession.close(CloseMode.GRACEFUL);
    }

    @Override
    public void disconnected(final IOSession ioSession) {
        inbound.onDisconnected(ioSession);
        ioSession.clearEvent(EventMask.READ | EventMask.WRITE);
        // Ensure the underlying protocol session does not linger
        state.session.enqueue(new ShutdownCommand(CloseMode.GRACEFUL), Command.Priority.IMMEDIATE);
        if (endpoint != null && endpointReleased.compareAndSet(false, true)) {
            try {
                endpoint.releaseAndDiscard();
            } catch (final Throwable ignore) {
                // best effort
            }
        }
    }
}
