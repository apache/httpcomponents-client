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
package org.apache.hc.core5.websocket.server;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.websocket.WebSocketConfig;
import org.apache.hc.core5.websocket.WebSocketExtensionRegistry;
import org.apache.hc.core5.websocket.WebSocketHandler;

/**
 * Bootstrap for HTTP/2 WebSocket servers using RFC 8441 (Extended CONNECT).
 *
 * @since 5.7
 */
public final class WebSocketH2ServerBootstrap {

    private final List<RequestRouter.Entry<Supplier<WebSocketHandler>>> routeEntries;
    private String canonicalHostName;
    private int listenerPort;
    private InetAddress localAddress;
    private IOReactorConfig ioReactorConfig;
    private HttpProcessor httpProcessor;
    private HttpVersionPolicy versionPolicy;
    private H2Config h2Config = H2Config.custom().setPushEnabled(false).build();
    private Http1Config http1Config = Http1Config.DEFAULT;
    private CharCodingConfig charCodingConfig;
    private TlsStrategy tlsStrategy;
    private Timeout handshakeTimeout;
    private H2StreamListener h2StreamListener;
    private Http1StreamListener http1StreamListener;
    private WebSocketConfig webSocketConfig;
    private WebSocketExtensionRegistry extensionRegistry;
    private Executor executor;

    private WebSocketH2ServerBootstrap() {
        this.routeEntries = new ArrayList<>();
    }

    public static WebSocketH2ServerBootstrap bootstrap() {
        return new WebSocketH2ServerBootstrap();
    }

    public WebSocketH2ServerBootstrap setCanonicalHostName(final String canonicalHostName) {
        this.canonicalHostName = canonicalHostName;
        return this;
    }

    public WebSocketH2ServerBootstrap setListenerPort(final int listenerPort) {
        this.listenerPort = listenerPort;
        return this;
    }

    public WebSocketH2ServerBootstrap setLocalAddress(final InetAddress localAddress) {
        this.localAddress = localAddress;
        return this;
    }

    public WebSocketH2ServerBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    public WebSocketH2ServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    public WebSocketH2ServerBootstrap setVersionPolicy(final HttpVersionPolicy versionPolicy) {
        this.versionPolicy = versionPolicy;
        return this;
    }

    public WebSocketH2ServerBootstrap setH2Config(final H2Config h2Config) {
        if (h2Config == null) {
            this.h2Config = H2Config.custom().setPushEnabled(false).build();
        } else if (h2Config.isPushEnabled()) {
            this.h2Config = H2Config.copy(h2Config).setPushEnabled(false).build();
        } else {
            this.h2Config = h2Config;
        }
        return this;
    }

    public WebSocketH2ServerBootstrap setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        return this;
    }

    public WebSocketH2ServerBootstrap setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    public WebSocketH2ServerBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    public WebSocketH2ServerBootstrap setHandshakeTimeout(final Timeout handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
        return this;
    }

    public WebSocketH2ServerBootstrap setH2StreamListener(final H2StreamListener h2StreamListener) {
        this.h2StreamListener = h2StreamListener;
        return this;
    }

    public WebSocketH2ServerBootstrap setHttp1StreamListener(final Http1StreamListener http1StreamListener) {
        this.http1StreamListener = http1StreamListener;
        return this;
    }

    public WebSocketH2ServerBootstrap setWebSocketConfig(final WebSocketConfig webSocketConfig) {
        this.webSocketConfig = webSocketConfig;
        return this;
    }

    public WebSocketH2ServerBootstrap setExtensionRegistry(final WebSocketExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
        return this;
    }

    public WebSocketH2ServerBootstrap setExecutor(final Executor executor) {
        this.executor = executor;
        return this;
    }

    public WebSocketH2ServerBootstrap register(final String uriPattern, final Supplier<WebSocketHandler> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "WebSocket handler supplier");
        this.routeEntries.add(new RequestRouter.Entry<>(uriPattern, supplier));
        return this;
    }

    public WebSocketH2ServerBootstrap register(final String hostname, final String uriPattern, final Supplier<WebSocketHandler> supplier) {
        Args.notNull(hostname, "Hostname");
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "WebSocket handler supplier");
        this.routeEntries.add(new RequestRouter.Entry<>(hostname, uriPattern, supplier));
        return this;
    }

    public WebSocketH2Server create() {
        final WebSocketConfig cfg = webSocketConfig != null ? webSocketConfig : WebSocketConfig.DEFAULT;
        final WebSocketExtensionRegistry ext = extensionRegistry != null
                ? extensionRegistry
                : WebSocketExtensionRegistry.createDefault();

        final H2ServerBootstrap bootstrap = H2ServerBootstrap.bootstrap()
                .setCanonicalHostName(canonicalHostName)
                .setIOReactorConfig(ioReactorConfig)
                .setHttpProcessor(httpProcessor)
                .setVersionPolicy(versionPolicy != null ? versionPolicy : HttpVersionPolicy.FORCE_HTTP_2)
                .setTlsStrategy(tlsStrategy)
                .setHandshakeTimeout(handshakeTimeout)
                .setStreamListener(h2StreamListener)
                .setStreamListener(http1StreamListener);

        if (h2Config != null) {
            bootstrap.setH2Config(h2Config);
        }
        if (http1Config != null) {
            bootstrap.setHttp1Config(http1Config);
        }

        if (charCodingConfig != null) {
            bootstrap.setCharset(charCodingConfig);
        }

        for (final RequestRouter.Entry<Supplier<WebSocketHandler>> entry : routeEntries) {
            final Supplier<AsyncServerExchangeHandler> exchangeSupplier = () ->
                    new WebSocketH2ServerExchangeHandler(entry.route.handler.get(), cfg, ext, executor);
            if (entry.uriAuthority != null) {
                bootstrap.register(entry.uriAuthority.getHostName(), entry.route.pattern, exchangeSupplier);
            } else {
                bootstrap.register(entry.route.pattern, exchangeSupplier);
            }
        }

        final HttpAsyncServer server = bootstrap.create();
        final URIScheme scheme = tlsStrategy != null ? URIScheme.HTTPS : URIScheme.HTTP;
        return new WebSocketH2Server(server, localAddress, listenerPort, scheme);
    }
}
