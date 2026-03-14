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
package org.apache.hc.client5.http.websocket.client.impl.protocol;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Minimal pluggable protocol strategy. One impl for H1 (RFC6455),
 * one for H2 Extended CONNECT (RFC8441).
 */
@Internal
public interface WebSocketProtocolStrategy {

    /**
     * Establish a WebSocket connection using a specific HTTP transport/protocol.
     *
     * @param uri      ws:// or wss:// target
     * @param listener user listener for WS events
     * @param cfg      client config (timeouts, subprotocols, PMCE offer, etc.)
     * @param context  optional HttpContext (may be {@code null})
     * @return future completing with a connected {@link WebSocket} or exceptionally on failure
     */
    CompletableFuture<WebSocket> connect(
            URI uri,
            WebSocketListener listener,
            WebSocketClientConfig cfg,
            HttpContext context);
}
