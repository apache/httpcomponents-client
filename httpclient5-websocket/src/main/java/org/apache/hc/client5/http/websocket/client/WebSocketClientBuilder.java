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
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequesterBootstrap;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.DefaultDisposalCallback;
import org.apache.hc.core5.pool.LaxConnPool;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorMetricsListener;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Timeout;

/**
 * Builder for {@link CloseableWebSocketClient} instances.
 *
 * <p>This builder assembles a WebSocket client on top of the asynchronous
 * HTTP/1.1 requester and connection pool infrastructure provided by
 * HttpComponents Core. Unless otherwise specified, sensible defaults
 * are used for all components.</p>
 *
 * <p>The resulting {@link CloseableWebSocketClient} manages its own I/O
 * reactor and connection pool and must be {@link java.io.Closeable#close()
 * closed} when no longer needed.</p>
 *
 * <p>Builders are mutable and not thread-safe. Configure the instance
 * on a single thread and then call {@link #build()}.</p>
 *
 * @since 5.7
 */
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
    private Http1StreamListener streamListener;
    private H2Config h2Config;
    private ConnPoolListener<HttpHost> connPoolListener;
    private ThreadFactory threadFactory;

    // Optional listeners for reactor metrics and worker selection.
    private IOReactorMetricsListener reactorMetricsListener;
    @SuppressWarnings("unused")
    private int maxPendingCommandsPerConnection;

    private WebSocketClientConfig defaultConfig = WebSocketClientConfig.custom().build();

    private WebSocketClientBuilder() {
    }

    /**
     * Creates a new {@code WebSocketClientBuilder} instance.
     *
     * @return a new builder.
     */
    public static WebSocketClientBuilder create() {
        return new WebSocketClientBuilder();
    }

    /**
     * Sets the default configuration applied to WebSocket connections
     * created by the resulting client.
     *
     * @param defaultConfig default WebSocket configuration; if {@code null}
     *                      the existing default is retained.
     * @return this builder.
     */
    public WebSocketClientBuilder defaultConfig(final WebSocketClientConfig defaultConfig) {
        if (defaultConfig != null) {
            this.defaultConfig = defaultConfig;
        }
        return this;
    }

    /**
     * Sets the I/O reactor configuration.
     *
     * @param ioReactorConfig I/O reactor configuration, or {@code null}
     *                        to use {@link IOReactorConfig#DEFAULT}.
     * @return this builder.
     */
    public WebSocketClientBuilder setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets the HTTP/1.1 configuration for the underlying requester.
     *
     * @param http1Config HTTP/1.1 configuration, or {@code null}
     *                    to use {@link Http1Config#DEFAULT}.
     * @return this builder.
     */
    public WebSocketClientBuilder setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    /**
     * Sets the character coding configuration for HTTP message processing.
     *
     * @param charCodingConfig character coding configuration, or {@code null}
     *                         to use {@link CharCodingConfig#DEFAULT}.
     * @return this builder.
     */
    public WebSocketClientBuilder setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Sets a custom {@link HttpProcessor} for HTTP/1.1 requests.
     *
     * @param httpProcessor HTTP processor, or {@code null} to use
     *                      {@link HttpProcessors#client()}.
     * @return this builder.
     */
    public WebSocketClientBuilder setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Sets the connection reuse strategy for persistent HTTP connections.
     *
     * @param connStrategy connection reuse strategy, or {@code null}
     *                     to use {@link DefaultClientConnectionReuseStrategy}.
     * @return this builder.
     */
    public WebSocketClientBuilder setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connStrategy = connStrategy;
        return this;
    }

    /**
     * Sets the default maximum number of connections per route.
     *
     * @param defaultMaxPerRoute maximum connections per route; values
     *                           &le; 0 cause the default of {@code 20}
     *                           to be used.
     * @return this builder.
     */
    public WebSocketClientBuilder setDefaultMaxPerRoute(final int defaultMaxPerRoute) {
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        return this;
    }

    /**
     * Sets the maximum total number of connections in the pool.
     *
     * @param maxTotal maximum total connections; values &le; 0 cause
     *                 the default of {@code 50} to be used.
     * @return this builder.
     */
    public WebSocketClientBuilder setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
        return this;
    }

    /**
     * Sets the time-to-live for persistent connections in the pool.
     *
     * @param timeToLive connection time-to-live, or {@code null} to use
     *                   {@link Timeout#DISABLED}.
     * @return this builder.
     */
    public WebSocketClientBuilder setTimeToLive(final Timeout timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    /**
     * Sets the reuse policy for connections in the pool.
     *
     * @param poolReusePolicy reuse policy, or {@code null} to use
     *                        {@link PoolReusePolicy#LIFO}.
     * @return this builder.
     */
    public WebSocketClientBuilder setPoolReusePolicy(final PoolReusePolicy poolReusePolicy) {
        this.poolReusePolicy = poolReusePolicy;
        return this;
    }

    /**
     * Sets the concurrency policy for the connection pool.
     *
     * @param poolConcurrencyPolicy concurrency policy, or {@code null}
     *                              to use {@link PoolConcurrencyPolicy#STRICT}.
     * @return this builder.
     */
    public WebSocketClientBuilder setPoolConcurrencyPolicy(final PoolConcurrencyPolicy poolConcurrencyPolicy) {
        this.poolConcurrencyPolicy = poolConcurrencyPolicy;
        return this;
    }

    /**
     * Sets the maximum number of pending commands per connection.
     *
     * @param maxPendingCommandsPerConnection maximum pending commands; values &lt; 0
     *                                        cause the default of {@code 0} to be used.
     * @return this builder.
     */
    public WebSocketClientBuilder setMaxPendingCommandsPerConnection(final int maxPendingCommandsPerConnection) {
        this.maxPendingCommandsPerConnection = maxPendingCommandsPerConnection;
        return this;
    }

    /**
     * Sets the TLS strategy used to establish HTTPS or WSS connections.
     *
     * @param tlsStrategy TLS strategy, or {@code null} to use
     *                    {@link BasicClientTlsStrategy}.
     * @return this builder.
     */
    public WebSocketClientBuilder setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Sets the timeout for the TLS handshake.
     *
     * @param handshakeTimeout handshake timeout, or {@code null} for no
     *                         specific timeout.
     * @return this builder.
     */
    public WebSocketClientBuilder setTlsHandshakeTimeout(final Timeout handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
        return this;
    }

    /**
     * Sets a decorator for low-level I/O sessions created by the reactor.
     *
     * @param ioSessionDecorator decorator, or {@code null} for none.
     * @return this builder.
     */
    public WebSocketClientBuilder setIOSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    /**
     * Sets a callback to be notified of fatal I/O exceptions.
     *
     * @param exceptionCallback exception callback, or {@code null} to use
     *                          {@link WsLoggingExceptionCallback#INSTANCE}.
     * @return this builder.
     */
    public WebSocketClientBuilder setExceptionCallback(final Callback<Exception> exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }

    /**
     * Sets a listener for I/O session lifecycle events.
     *
     * @param sessionListener session listener, or {@code null} for none.
     * @return this builder.
     */
    public WebSocketClientBuilder setIOSessionListener(final IOSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    /**
     * Sets a listener for HTTP/1.1 stream events.
     *
     * @param streamListener stream listener, or {@code null} for none.
     * @return this builder.
     */
    public WebSocketClientBuilder setStreamListener(
            final Http1StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    /**
     * Sets a listener for connection pool events.
     *
     * @param connPoolListener pool listener, or {@code null} for none.
     * @return this builder.
     */
    public WebSocketClientBuilder setConnPoolListener(final ConnPoolListener<HttpHost> connPoolListener) {
        this.connPoolListener = connPoolListener;
        return this;
    }

    /**
     * Sets the thread factory used to create the main I/O reactor thread.
     *
     * @param threadFactory thread factory, or {@code null} to use a
     *                      {@link DefaultThreadFactory} named
     *                      {@code "websocket-main"}.
     * @return this builder.
     */
    public WebSocketClientBuilder setThreadFactory(final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * Sets a metrics listener for the I/O reactor.
     *
     * @param reactorMetricsListener metrics listener, or {@code null} for none.
     * @return this builder.
     */
    public WebSocketClientBuilder setReactorMetricsListener(
            final IOReactorMetricsListener reactorMetricsListener) {
        this.reactorMetricsListener = reactorMetricsListener;
        return this;
    }

    /**
     * Builds a new {@link CloseableWebSocketClient} instance using the
     * current builder configuration.
     *
     * <p>The returned client owns its underlying I/O reactor and connection
     * pool and must be closed to release system resources.</p>
     *
     * @return a newly created {@link CloseableWebSocketClient}.
     */
    public CloseableWebSocketClient build() {

        final PoolConcurrencyPolicy conc = poolConcurrencyPolicy != null ? poolConcurrencyPolicy : PoolConcurrencyPolicy.STRICT;
        final PoolReusePolicy reuse = poolReusePolicy != null ? poolReusePolicy : PoolReusePolicy.LIFO;
        final Timeout ttl = timeToLive != null ? timeToLive : Timeout.DISABLED;

        final ManagedConnPool<HttpHost, IOSession> connPool;
        if (conc == PoolConcurrencyPolicy.LAX) {
            connPool = new LaxConnPool<>(
                    defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 20,
                    ttl, reuse, new DefaultDisposalCallback<>(), connPoolListener);
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

        final TlsStrategy tls = tlsStrategy != null ? tlsStrategy : new BasicClientTlsStrategy();

        final IOReactorMetricsListener metricsListener = reactorMetricsListener != null ? reactorMetricsListener : null;

        final HttpAsyncRequester requester = AsyncRequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig != null ? ioReactorConfig : IOReactorConfig.DEFAULT)
                .setHttpProcessor(proc)
                .setHttp1Config(h1)
                .setCharCodingConfig(coding)
                .setConnectionReuseStrategy(reuseStrategyCopy)
                .setPoolConcurrencyPolicy(conc)
                .setPoolReusePolicy(reuse)
                .setDefaultMaxPerRoute(defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 20)
                .setMaxTotal(maxTotal > 0 ? maxTotal : 50)
                .setTimeToLive(ttl)
                .setTlsStrategy(tls)
                .setTlsHandshakeTimeout(handshakeTimeout)
                .setIOSessionDecorator(ioSessionDecorator)
                .setExceptionCallback(exceptionCallback != null ? exceptionCallback : WsLoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(sessionListener)
                .setIOReactorMetricsListener(metricsListener)
                .setStreamListener(streamListener)
                .setConnPoolListener(connPoolListener)
                // version 5.5 of the core
//                .setMaxPendingCommandsPerConnection(Math.max(maxPendingCommandsPerConnection, 0))
                .create();

        final H2MultiplexingRequester h2Requester = H2MultiplexingRequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig != null ? ioReactorConfig : IOReactorConfig.DEFAULT)
                .setHttpProcessor(httpProcessor != null ? httpProcessor : H2Processors.client())
                .setH2Config(h2Config != null ? h2Config : H2Config.DEFAULT)
                .setTlsStrategy(tls)
                .setIOSessionDecorator(ioSessionDecorator)
                .setExceptionCallback(exceptionCallback != null ? exceptionCallback : WsLoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(sessionListener)
                .setIOReactorMetricsListener(metricsListener)
                .create();

        final ThreadFactory tf = threadFactory != null
                ? threadFactory
                : new DefaultThreadFactory("websocket-main", true);

        return new DefaultWebSocketClient(
                requester,
                connPool,
                defaultConfig,
                tf,
                h2Requester
        );
    }
}