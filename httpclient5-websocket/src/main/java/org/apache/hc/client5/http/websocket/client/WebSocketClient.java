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
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Client for establishing WebSocket connections using the underlying
 * asynchronous HttpClient infrastructure.
 *
 * <p>This interface represents the minimal contract for initiating
 * WebSocket handshakes. Implementations are expected to be thread-safe.</p>
 *
 * @since 5.7
 */
public interface WebSocketClient {

    /**
     * Initiates an asynchronous WebSocket connection to the given target URI.
     *
     * <p>The URI must use the {@code ws} or {@code wss} scheme. This method
     * performs an HTTP/1.1 upgrade to the WebSocket protocol and, on success,
     * creates a {@link WebSocket} associated with the supplied
     * {@link WebSocketListener}.</p>
     *
     * <p>The operation is fully asynchronous. The returned
     * {@link CompletableFuture} completes when the opening WebSocket
     * handshake has either succeeded or failed.</p>
     *
     * @param uri      target WebSocket URI, must not be {@code null}.
     * @param listener callback that receives WebSocket events, must not be {@code null}.
     * @param cfg      optional per-connection configuration; if {@code null}, the
     *                 client’s default configuration is used.
     * @param context  optional HTTP context for the underlying upgrade request;
     *                 may be {@code null}.
     * @return a future that completes with a connected {@link WebSocket} on
     * success, or completes exceptionally if the connection attempt
     * or protocol handshake fails.
     * @since 5.7
     */
    CompletableFuture<WebSocket> connect(
            URI uri,
            WebSocketListener listener,
            WebSocketClientConfig cfg,
            HttpContext context);

}
