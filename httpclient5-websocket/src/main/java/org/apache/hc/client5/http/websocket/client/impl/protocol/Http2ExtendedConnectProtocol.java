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
 * RFC 8441 (HTTP/2 Extended CONNECT) placeholder.
 * No-args ctor (matches your build error). Falls back to H1.
 */
@Internal
public final class Http2ExtendedConnectProtocol implements WebSocketProtocolStrategy {

    public static final class H2NotAvailable extends RuntimeException {
        public H2NotAvailable(final String msg) {
            super(msg);
        }
    }

    public Http2ExtendedConnectProtocol() {
    }

    @Override
    public CompletableFuture<WebSocket> connect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfg,
            final HttpContext context) {
        final CompletableFuture<WebSocket> f = new CompletableFuture<>();
        f.completeExceptionally(new H2NotAvailable("HTTP/2 Extended CONNECT not wired yet"));
        return f;
    }
}
