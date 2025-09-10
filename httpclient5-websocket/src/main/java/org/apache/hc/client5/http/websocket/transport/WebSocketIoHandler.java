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
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP/1.1 WebSocket I/O handler. Reads from {@link IOSession}, pushes data
 * into the unified {@link WebSocketSessionEngine}, and drains outbound frames
 * when the channel is writable.
 */
@Internal
public final class WebSocketIoHandler implements IOEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketIoHandler.class);

    private final ProtocolIOSession session;
    private final IOSessionTransport transport;
    private final WebSocketSessionEngine engine;
    private final AsyncClientEndpoint endpoint;
    private final AtomicBoolean endpointReleased;

    private final ByteBuffer readBuf;

    public WebSocketIoHandler(final ProtocolIOSession session,
                              final WebSocketListener listener,
                              final WebSocketClientConfig cfg,
                              final ExtensionChain chain,
                              final AsyncClientEndpoint endpoint) {
        this.session = session;
        this.transport = new IOSessionTransport(session);
        this.engine = new WebSocketSessionEngine(transport, listener, cfg, chain, null);
        this.endpoint = endpoint;
        this.endpointReleased = new AtomicBoolean(false);

        final int outChunk = Math.max(256, cfg.getOutgoingChunkSize());
        final int bufSize = Math.max(8192, outChunk);
        this.readBuf = cfg.isDirectBuffers()
                ? ByteBuffer.allocateDirect(bufSize)
                : ByteBuffer.allocate(bufSize);
    }

    /**
     * Expose the application WebSocket facade.
     */
    public WebSocket exposeWebSocket() {
        return engine.facade();
    }

    // ---- package-private for tests ----
    WebSocketSessionEngine engine() {
        return engine;
    }

    // ---- IOEventHandler ----

    @Override
    public void connected(final IOSession ioSession) {
        ioSession.setSocketTimeout(Timeout.DISABLED);
        ioSession.setEventMask(EventMask.READ | EventMask.WRITE);
    }

    @Override
    public void inputReady(final IOSession ioSession, final ByteBuffer src) {
        try {
            // Push any data already provided by the reactor
            if (src != null && src.hasRemaining()) {
                engine.onData(src);
            }

            // Pull more from the IOSession
            int n;
            do {
                readBuf.clear();
                n = ioSession.read(readBuf);
                if (n > 0) {
                    readBuf.flip();
                    engine.onData(readBuf);
                }
            } while (n > 0);

            if (n < 0) {
                engine.onDisconnected();
            }
        } catch (final Exception ex) {
            engine.onError(ex);
            ioSession.close(CloseMode.GRACEFUL);
        }
    }

    @Override
    public void outputReady(final IOSession ioSession) {
        final boolean morePending = engine.onOutputReady();
        if (!morePending) {
            transport.clearWriteEvent();
        }
    }

    @Override
    public void timeout(final IOSession ioSession, final Timeout timeout) {
        engine.onError(new java.util.concurrent.TimeoutException(
                "I/O timeout: " + (timeout != null ? timeout : Timeout.ZERO_MILLISECONDS)));
        ioSession.close(CloseMode.GRACEFUL);
    }

    @Override
    public void exception(final IOSession ioSession, final Exception cause) {
        engine.onError(cause);
        ioSession.close(CloseMode.GRACEFUL);
    }

    @Override
    public void disconnected(final IOSession ioSession) {
        engine.onDisconnected();
        transport.clearAllEvents();
        session.enqueue(new ShutdownCommand(CloseMode.GRACEFUL), Command.Priority.IMMEDIATE);
        if (endpoint != null && endpointReleased.compareAndSet(false, true)) {
            try {
                endpoint.releaseAndDiscard();
            } catch (final Throwable ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Error releasing endpoint: {}", ex.getMessage(), ex);
                }
            }
        }
    }
}
