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
import java.nio.CharBuffer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.impl.protocol.Http1UpgradeProtocol;
import org.apache.hc.client5.http.websocket.client.impl.protocol.Http2ExtendedConnectProtocol;
import org.apache.hc.client5.http.websocket.client.impl.protocol.WebSocketProtocolStrategy;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal internal WS client: owns requester + pool, no extra closeables.
 */
@Internal
abstract class InternalWebSocketClientBase extends AbstractWebSocketClient {

    private static final Logger LOG = LoggerFactory.getLogger(InternalWebSocketClientBase.class);

    private final WebSocketClientConfig defaultConfig;
    private final ManagedConnPool<HttpHost, IOSession> connPool;

    private final WebSocketProtocolStrategy h1;
    private final WebSocketProtocolStrategy h2;

    InternalWebSocketClientBase(
            final HttpAsyncRequester requester,
            final ManagedConnPool<HttpHost, IOSession> connPool,
            final WebSocketClientConfig defaultConfig,
            final ThreadFactory threadFactory,
            final H2MultiplexingRequester h2Requester) {
        super(Args.notNull(requester, "requester"), threadFactory, h2Requester);
        this.connPool = Args.notNull(connPool, "connPool");
        this.defaultConfig = defaultConfig != null ? defaultConfig : WebSocketClientConfig.custom().build();
        this.h1 = newH1Protocol(requester, connPool);
        this.h2 = newH2Protocol(h2Requester);
    }

    /**
     * HTTP/1.1 Upgrade protocol.
     */
    protected WebSocketProtocolStrategy newH1Protocol(
            final HttpAsyncRequester requester,
            final ManagedConnPool<HttpHost, IOSession> connPool) {
        return new Http1UpgradeProtocol(requester, connPool);
    }

    /**
     * HTTP/2 Extended CONNECT protocol.
     */
    protected WebSocketProtocolStrategy newH2Protocol(final H2MultiplexingRequester requester) {
        return requester != null ? new Http2ExtendedConnectProtocol(requester) : null;
    }

    @Override
    protected CompletableFuture<WebSocket> doConnect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfgOrNull,
            final HttpContext context) {

        final WebSocketClientConfig cfg = cfgOrNull != null ? cfgOrNull : defaultConfig;
        if (cfg.isHttp2Enabled() && h2 != null) {
            final CompletableFuture<WebSocket> result = new CompletableFuture<>();
            final AtomicBoolean h2Opened = new AtomicBoolean(false);
            final AtomicReference<Throwable> suppressedError = new AtomicReference<>();
            final WebSocketListener h2Listener = new WebSocketListener() {
                @Override
                public void onOpen(final WebSocket webSocket) {
                    h2Opened.set(true);
                    listener.onOpen(webSocket);
                }

                @Override
                public void onText(final CharBuffer data, final boolean last) {
                    listener.onText(data, last);
                }

                @Override
                public void onBinary(final ByteBuffer data, final boolean last) {
                    listener.onBinary(data, last);
                }

                @Override
                public void onPing(final ByteBuffer data) {
                    listener.onPing(data);
                }

                @Override
                public void onPong(final ByteBuffer data) {
                    listener.onPong(data);
                }

                @Override
                public void onClose(final int statusCode, final String reason) {
                    listener.onClose(statusCode, reason);
                }

                @Override
                public void onError(final Throwable cause) {
                    if (!h2Opened.get() && !result.isDone()) {
                        suppressedError.compareAndSet(null, cause);
                        return;
                    }
                    listener.onError(cause);
                }
            };

            h2.connect(uri, h2Listener, cfg, context).whenComplete((ws, ex) -> {
                if (ex == null) {
                    result.complete(ws);
                    return;
                }
                final Throwable cause = unwrap(ex);
                if (!h2Opened.get() && shouldFallbackToH1(cause)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("H2 WebSocket attempt failed, falling back to HTTP/1.1: {}", cause.getMessage());
                    }
                    h1.connect(uri, listener, cfg, context).whenComplete((ws2, ex2) -> {
                        if (ex2 == null) {
                            result.complete(ws2);
                        } else {
                            result.completeExceptionally(unwrap(ex2));
                        }
                    });
                } else {
                    if (!h2Opened.get()) {
                        final Throwable suppressed = suppressedError.get();
                        if (suppressed != null) {
                            listener.onError(suppressed);
                        }
                    }
                    result.completeExceptionally(cause);
                }
            });
            return result;
        }
        return h1.connect(uri, listener, cfg, context);
    }

    private static Throwable unwrap(final Throwable ex) {
        if (ex instanceof CompletionException) {
            return ex.getCause() != null ? ex.getCause() : ex;
        }
        return ex;
    }

    private static boolean shouldFallbackToH1(final Throwable ex) {
        if (ex instanceof CancellationException) {
            return false;
        }
        return !(ex instanceof IllegalArgumentException);
    }

    @Override
    protected void internalClose(final CloseMode closeMode) {
        try {
            final CloseMode mode = closeMode != null ? closeMode : CloseMode.GRACEFUL;
            connPool.close(mode);
        } catch (final Exception ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error closing pool: {}", ex.getMessage(), ex);
            }
        }
    }
}
