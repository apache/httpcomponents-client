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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
import org.apache.hc.client5.http.websocket.client.impl.connector.WebSocketProtocolConnector;
import org.apache.hc.client5.http.websocket.client.impl.protocol.Http1UpgradeProtocol;
import org.apache.hc.client5.http.websocket.client.impl.protocol.Http2ExtendedConnectProtocol;
import org.apache.hc.client5.http.websocket.client.impl.protocol.WebSocketProtocolStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default WebSocket client implementation that manages the I/O reactor lifecycle,
 * connection pool, and protocol strategy selection (HTTP/1.1 Upgrade or HTTP/2
 * Extended CONNECT with automatic fallback).
 *
 * <p>Instances are created via
 * {@link org.apache.hc.client5.http.websocket.client.WebSocketClientBuilder}.</p>
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
@Internal
public class DefaultWebSocketClient extends CloseableWebSocketClient {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWebSocketClient.class);

    enum Status { READY, RUNNING, TERMINATED }

    // Lifecycle
    private final AsyncRequester primaryRequester;
    private final AsyncRequester[] extraRequesters;
    private final ExecutorService executorService;
    private final AtomicReference<Status> status;

    // Protocol
    private final WebSocketClientConfig defaultConfig;
    private final ManagedConnPool<HttpHost, IOSession> connPool;
    private final WebSocketProtocolStrategy h1;
    private final WebSocketProtocolStrategy h2;
    private final WebSocketProtocolConnector connector;

    public DefaultWebSocketClient(
            final HttpAsyncRequester requester,
            final ManagedConnPool<HttpHost, IOSession> connPool,
            final WebSocketClientConfig defaultConfig,
            final ThreadFactory threadFactory,
            final H2MultiplexingRequester h2Requester) {
        super();
        this.primaryRequester = Args.notNull(requester, "requester");
        this.extraRequesters = h2Requester != null
                ? new AsyncRequester[]{h2Requester}
                : new AsyncRequester[0];
        final int threads = Math.max(1, 1 + this.extraRequesters.length);
        this.executorService = Executors.newFixedThreadPool(threads, threadFactory);
        this.status = new AtomicReference<>(Status.READY);

        this.connPool = Args.notNull(connPool, "connPool");
        this.defaultConfig = defaultConfig != null ? defaultConfig : WebSocketClientConfig.custom().build();
        this.h1 = newH1Protocol(requester, connPool);
        this.h2 = newH2Protocol(h2Requester);
        this.connector = h2 != null ? new WebSocketProtocolConnector(h2, h1) : null;
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

    // ---- Lifecycle ----

    @Override
    public final void start() {
        if (status.compareAndSet(Status.READY, Status.RUNNING)) {
            executorService.execute(primaryRequester::start);
            for (final AsyncRequester requester : extraRequesters) {
                executorService.execute(requester::start);
            }
        }
    }

    boolean isRunning() {
        return status.get() == Status.RUNNING;
    }

    @Override
    public final IOReactorStatus getStatus() {
        return primaryRequester.getStatus();
    }

    @Override
    public final void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        primaryRequester.awaitShutdown(waitTime);
        for (final AsyncRequester requester : extraRequesters) {
            requester.awaitShutdown(waitTime);
        }
    }

    @Override
    public final void initiateShutdown() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initiating shutdown");
        }
        primaryRequester.initiateShutdown();
        for (final AsyncRequester requester : extraRequesters) {
            requester.initiateShutdown();
        }
    }

    @Override
    public final void close(final CloseMode closeMode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Shutdown {}", closeMode);
        }
        primaryRequester.initiateShutdown();
        primaryRequester.close(closeMode != null ? closeMode : CloseMode.IMMEDIATE);
        for (final AsyncRequester requester : extraRequesters) {
            requester.initiateShutdown();
            requester.close(closeMode != null ? closeMode : CloseMode.IMMEDIATE);
        }
        executorService.shutdownNow();
        try {
            final CloseMode mode = closeMode != null ? closeMode : CloseMode.GRACEFUL;
            connPool.close(mode);
        } catch (final Exception ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error closing pool: {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    // ---- Protocol ----

    @Override
    protected CompletableFuture<WebSocket> doConnect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfgOrNull,
            final HttpContext context) {

        final WebSocketClientConfig cfg = cfgOrNull != null ? cfgOrNull : defaultConfig;
        if (cfg.isHttp2Enabled() && connector != null) {
            return connector.connect(uri, listener, cfg, context);
        }
        return h1.connect(uri, listener, cfg, context);
    }
}
