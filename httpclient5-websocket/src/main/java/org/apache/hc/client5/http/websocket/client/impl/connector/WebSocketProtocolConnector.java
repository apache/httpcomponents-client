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

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.impl.protocol.WebSocketProtocolStrategy;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses the negotiated WebSocket transport protocol and applies the fallback policy.
 */
@Internal
public final class WebSocketProtocolConnector {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketProtocolConnector.class);

    private final WebSocketProtocolStrategy primaryProtocol;
    private final WebSocketProtocolStrategy fallbackProtocol;

    public WebSocketProtocolConnector(
            final WebSocketProtocolStrategy primaryProtocol,
            final WebSocketProtocolStrategy fallbackProtocol) {
        this.primaryProtocol = primaryProtocol;
        this.fallbackProtocol = fallbackProtocol;
    }

    public CompletableFuture<WebSocket> connect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfg,
            final HttpContext context) {

        if (cfg.isHttp2Enabled() && primaryProtocol != null) {
            final CompletableFuture<WebSocket> result = new CompletableFuture<>();
            final AtomicBoolean primaryOpened = new AtomicBoolean(false);
            final AtomicReference<Throwable> suppressedError = new AtomicReference<>();
            final WebSocketListener primaryListener = new WebSocketListener() {
                @Override
                public void onOpen(final WebSocket webSocket) {
                    primaryOpened.set(true);
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
                    if (!primaryOpened.get() && !result.isDone()) {
                        suppressedError.compareAndSet(null, cause);
                        return;
                    }
                    listener.onError(cause);
                }
            };

            try {
                primaryProtocol.connect(uri, primaryListener, cfg, context).whenComplete((ws, ex) -> {
                    if (ex == null) {
                        result.complete(ws);
                        return;
                    }
                    final Throwable cause = unwrap(ex);
                    if (!primaryOpened.get() && primaryProtocol.isFallbackCandidate(cause) && fallbackProtocol != null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Primary WebSocket attempt failed, falling back: {}", cause.getMessage());
                        }
                        try {
                            fallbackProtocol.connect(uri, listener, cfg, context).whenComplete((fallbackWs, fallbackEx) -> {
                                if (fallbackEx == null) {
                                    result.complete(fallbackWs);
                                } else {
                                    result.completeExceptionally(unwrap(fallbackEx));
                                }
                            });
                        } catch (final RuntimeException fallbackEx) {
                            result.completeExceptionally(fallbackEx);
                        }
                    } else {
                        if (!primaryOpened.get()) {
                            final Throwable suppressed = suppressedError.get();
                            if (suppressed != null) {
                                listener.onError(suppressed);
                            }
                        }
                        result.completeExceptionally(cause);
                    }
                });
            } catch (final RuntimeException ex) {
                if (primaryProtocol.isFallbackCandidate(ex) && fallbackProtocol != null) {
                    return fallbackProtocol.connect(uri, listener, cfg, context);
                }
                result.completeExceptionally(ex);
            }
            return result;
        }
        return fallbackProtocol.connect(uri, listener, cfg, context);
    }

    private static Throwable unwrap(final Throwable ex) {
        if (ex instanceof CompletionException) {
            return ex.getCause() != null ? ex.getCause() : ex;
        }
        return ex;
    }
}
