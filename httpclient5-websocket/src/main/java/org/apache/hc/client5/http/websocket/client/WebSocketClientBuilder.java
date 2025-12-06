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

import java.util.concurrent.ThreadFactory;

import org.apache.hc.client5.http.impl.DefaultClientConnectionReuseStrategy;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.client.impl.DefaultWebSocketClient;
import org.apache.hc.client5.http.websocket.client.impl.logging.WsLoggingExceptionCallback;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandlerFactory;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.DefaultDisposalCallback;
import org.apache.hc.core5.pool.LaxConnPool;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorMetricsListener;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.IOWorkerSelector;
import org.apache.hc.core5.util.Timeout;

public final class WebSocketClientBuilder {

    private IOReactorConfig ioReactorConfig;
    private Http1Config http1Config;
    private CharCodingConfig charCodingConfig;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;
    private int defaultMaxPerRoute;
    private int maxTotal;
    private Timeout timeToLive;
    private PoolReusePolicy poolReusePolicy;
    private PoolConcurrencyPolicy poolConcurrencyPolicy;
    private TlsStrategy tlsStrategy;
    private Timeout handshakeTimeout;
    private Decorator<IOSession> ioSessionDecorator;
    private Callback<Exception> exceptionCallback;
    private IOSessionListener sessionListener;
    private org.apache.hc.core5.http.impl.Http1StreamListener streamListener;
    private ConnPoolListener<HttpHost> connPoolListener;
    private ThreadFactory threadFactory;

    // NEW: allow caller to provide these, otherwise we’ll default them
    private IOReactorMetricsListener reactorMetricsListener;
    private IOWorkerSelector workerSelector;


    private WebSocketClientConfig defaultConfig = WebSocketClientConfig.custom().build();

    private WebSocketClientBuilder() {
    }

    public static WebSocketClientBuilder create() {
        return new WebSocketClientBuilder();
    }

    public WebSocketClientBuilder defaultConfig(final WebSocketClientConfig defaultConfig) {
        if (defaultConfig != null) {
            this.defaultConfig = defaultConfig;
        }
        return this;
    }

    public WebSocketClientBuilder setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    public WebSocketClientBuilder setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    public WebSocketClientBuilder setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    public WebSocketClientBuilder setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    public WebSocketClientBuilder setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connStrategy = connStrategy;
        return this;
    }

    public WebSocketClientBuilder setDefaultMaxPerRoute(final int defaultMaxPerRoute) {
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        return this;
    }

    public WebSocketClientBuilder setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
        return this;
    }

    public WebSocketClientBuilder setTimeToLive(final Timeout timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    public WebSocketClientBuilder setPoolReusePolicy(final PoolReusePolicy poolReusePolicy) {
        this.poolReusePolicy = poolReusePolicy;
        return this;
    }

    public WebSocketClientBuilder setPoolConcurrencyPolicy(final PoolConcurrencyPolicy poolConcurrencyPolicy) {
        this.poolConcurrencyPolicy = poolConcurrencyPolicy;
        return this;
    }

    public WebSocketClientBuilder setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    public WebSocketClientBuilder setTlsHandshakeTimeout(final Timeout handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
        return this;
    }

    public WebSocketClientBuilder setIOSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    public WebSocketClientBuilder setExceptionCallback(final Callback<Exception> exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }

    public WebSocketClientBuilder setIOSessionListener(final IOSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    public WebSocketClientBuilder setStreamListener(final org.apache.hc.core5.http.impl.Http1StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    public WebSocketClientBuilder setConnPoolListener(final ConnPoolListener<HttpHost> connPoolListener) {
        this.connPoolListener = connPoolListener;
        return this;
    }

    public WebSocketClientBuilder setThreadFactory(final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    public WebSocketClientBuilder setReactorMetricsListener(final IOReactorMetricsListener reactorMetricsListener) {
        this.reactorMetricsListener = reactorMetricsListener;
        return this;
    }

    public WebSocketClientBuilder setWorkerSelector(final IOWorkerSelector workerSelector) {
        this.workerSelector = workerSelector;
        return this;
    }

    public CloseableWebSocketClient build() {

        final PoolConcurrencyPolicy conc = poolConcurrencyPolicy != null ? poolConcurrencyPolicy : PoolConcurrencyPolicy.STRICT;
        final PoolReusePolicy reuse = poolReusePolicy != null ? poolReusePolicy : PoolReusePolicy.LIFO;
        final Timeout ttl = timeToLive != null ? timeToLive : Timeout.DISABLED;

        final ManagedConnPool<HttpHost, IOSession> connPool;
        if (conc == PoolConcurrencyPolicy.LAX) {
            connPool = new LaxConnPool<>(
                    defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 20,
                    ttl, reuse, new DefaultDisposalCallback<IOSession>(), connPoolListener);
        } else {
            connPool = new StrictConnPool<>(
                    defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 20,
                    maxTotal > 0 ? maxTotal : 50,
                    ttl, reuse, new DefaultDisposalCallback<>(), connPoolListener);
        }

        final HttpProcessor proc = httpProcessor != null ? httpProcessor : HttpProcessors.client();
        final Http1Config h1 = http1Config != null ? http1Config : Http1Config.DEFAULT;
        final CharCodingConfig coding = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;

        final ConnectionReuseStrategy reuseStrategyCopy = connStrategy != null ? connStrategy : new DefaultClientConnectionReuseStrategy();

        final ClientHttp1StreamDuplexerFactory duplexerFactory =
                new ClientHttp1StreamDuplexerFactory(proc, h1, coding, reuseStrategyCopy, null, null, streamListener);

        final TlsStrategy tls = tlsStrategy != null ? tlsStrategy : new BasicClientTlsStrategy();
        final IOEventHandlerFactory iohFactory =
                new ClientHttp1IOEventHandlerFactory(duplexerFactory, tls, handshakeTimeout);

        final IOReactorMetricsListener metricsListener = reactorMetricsListener != null ? reactorMetricsListener : null;

        final IOWorkerSelector selector = workerSelector != null ? workerSelector : null;

        final HttpAsyncRequester requester = new HttpAsyncRequester(
                ioReactorConfig != null ? ioReactorConfig : IOReactorConfig.DEFAULT,
                iohFactory,
                ioSessionDecorator,
                exceptionCallback != null ? exceptionCallback : WsLoggingExceptionCallback.INSTANCE,
                sessionListener,
                connPool,
                tls,
                handshakeTimeout,
                metricsListener,
                selector
        );

        final ThreadFactory tf = threadFactory != null
                ? threadFactory
                : new DefaultThreadFactory("websocket-main", true);

        return new DefaultWebSocketClient(
                requester,
                connPool,
                defaultConfig,
                tf
        );
    }
}
