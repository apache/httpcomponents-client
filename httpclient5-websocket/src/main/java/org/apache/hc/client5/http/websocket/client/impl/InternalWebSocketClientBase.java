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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.impl.protocol.Http1UpgradeProtocol;
import org.apache.hc.client5.http.websocket.client.impl.protocol.WebSocketProtocolStrategy;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
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

    InternalWebSocketClientBase(
            final HttpAsyncRequester requester,
            final ManagedConnPool<HttpHost, IOSession> connPool,
            final WebSocketClientConfig defaultConfig,
            final ThreadFactory threadFactory) {
        super(Args.notNull(requester, "requester"), threadFactory);
        this.connPool = Args.notNull(connPool, "connPool");
        this.defaultConfig = defaultConfig != null ? defaultConfig : WebSocketClientConfig.custom().build();
        this.h1 = newH1Protocol(requester, connPool);
    }

    /**
     * HTTP/1.1 Upgrade protocol.
     */
    protected WebSocketProtocolStrategy newH1Protocol(
            final HttpAsyncRequester requester,
            final ManagedConnPool<HttpHost, IOSession> connPool) {
        return new Http1UpgradeProtocol(requester, connPool);
    }

    @Override
    protected CompletableFuture<WebSocket> doConnect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfgOrNull,
            final HttpContext context) {

        final WebSocketClientConfig cfg = cfgOrNull != null ? cfgOrNull : defaultConfig;
        return h1.connect(uri, listener, cfg, context);
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
