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
package org.apache.hc.client5.http.websocket.client;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * Public WebSocket client API mirroring {@code CloseableHttpAsyncClient}'s shape.
 *
 * <p>Subclasses provide the actual connect implementation in {@link #doConnect(URI, WebSocketListener, WebSocketClientConfig, HttpContext)}.
 * Overloads of {@code connect(...)} funnel into that single method.</p>
 *
 * <p>This type is a {@link ModalCloseable}; use {@link #close(CloseMode)} to select graceful or immediate shutdown.</p>
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public abstract class CloseableWebSocketClient implements WebSocketClient, ModalCloseable {

    /**
     * Start underlying I/O. Safe to call once; subsequent calls are no-ops.
     */
    public abstract void start();

    /**
     * Current I/O reactor status.
     */
    public abstract IOReactorStatus getStatus();

    /**
     * Best-effort await of shutdown.
     */
    public abstract void awaitShutdown(TimeValue waitTime) throws InterruptedException;

    /**
     * Initiate shutdown (non-blocking).
     */
    public abstract void initiateShutdown();

    /**
     * Core connect hook for subclasses.
     *
     * @param uri      target WebSocket URI (ws:// or wss://)
     * @param listener application callbacks
     * @param cfg      optional per-connection config (may be {@code null} for defaults)
     * @param context  optional HTTP context (may be {@code null})
     */
    protected abstract CompletableFuture<WebSocket> doConnect(
            URI uri,
            WebSocketListener listener,
            WebSocketClientConfig cfg,
            HttpContext context);

    public final CompletableFuture<WebSocket> connect(
            final URI uri,
            final WebSocketListener listener) {
        Args.notNull(uri, "URI");
        Args.notNull(listener, "WebSocketListener");
        return connect(uri, listener, WebSocketClientConfig.custom().build(), HttpCoreContext.create());
    }

    public final CompletableFuture<WebSocket> connect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfg) {
        Args.notNull(uri, "URI");
        Args.notNull(listener, "WebSocketListener");
        return connect(uri, listener, cfg, HttpCoreContext.create());
    }

    @Override
    public final CompletableFuture<WebSocket> connect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfg,
            final HttpContext context) {
        Args.notNull(uri, "URI");
        Args.notNull(listener, "WebSocketListener");
        return doConnect(uri, listener, cfg, context);
    }

}
