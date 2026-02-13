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
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.ssl.DefaultTlsSetupHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.websocket.WebSocketConfig;
import org.apache.hc.core5.websocket.WebSocketExtensionRegistry;
import org.apache.hc.core5.websocket.WebSocketHandler;

public class WebSocketServerBootstrap {

    private final List<RequestRouter.Entry<Supplier<WebSocketHandler>>> routeEntries;
    private String canonicalHostName;
    private int listenerPort;
    private InetAddress localAddress;
    private SocketConfig socketConfig;
    private Http1Config http1Config;
    private CharCodingConfig charCodingConfig;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;
    private ServerSocketFactory serverSocketFactory;
    private SSLContext sslContext;
    private Callback<SSLParameters> sslSetupHandler;
    private ExceptionListener exceptionListener;
    private Http1StreamListener streamListener;
    private BiFunction<String, URIAuthority, URIAuthority> authorityResolver;
    private HttpRequestMapper<Supplier<WebSocketHandler>> requestRouter;
    private WebSocketConfig webSocketConfig;
    private HttpConnectionFactory<? extends WebSocketServerConnection> connectionFactory;
    private WebSocketExtensionRegistry extensionRegistry;

    private WebSocketServerBootstrap() {
        this.routeEntries = new ArrayList<>();
    }

    public static WebSocketServerBootstrap bootstrap() {
        return new WebSocketServerBootstrap();
    }

    public WebSocketServerBootstrap setCanonicalHostName(final String canonicalHostName) {
        this.canonicalHostName = canonicalHostName;
        return this;
    }

    public WebSocketServerBootstrap setListenerPort(final int listenerPort) {
        this.listenerPort = listenerPort;
        return this;
    }

    public WebSocketServerBootstrap setLocalAddress(final InetAddress localAddress) {
        this.localAddress = localAddress;
        return this;
    }

    public WebSocketServerBootstrap setSocketConfig(final SocketConfig socketConfig) {
        this.socketConfig = socketConfig;
        return this;
    }

    public WebSocketServerBootstrap setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    public WebSocketServerBootstrap setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    public WebSocketServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    public WebSocketServerBootstrap setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connStrategy = connStrategy;
        return this;
    }

    public WebSocketServerBootstrap setServerSocketFactory(final ServerSocketFactory serverSocketFactory) {
        this.serverSocketFactory = serverSocketFactory;
        return this;
    }

    public WebSocketServerBootstrap setSslContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    public WebSocketServerBootstrap setSslSetupHandler(final Callback<SSLParameters> sslSetupHandler) {
        this.sslSetupHandler = sslSetupHandler;
        return this;
    }

    public WebSocketServerBootstrap setExceptionListener(final ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
        return this;
    }

    public WebSocketServerBootstrap setStreamListener(final Http1StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    public WebSocketServerBootstrap setAuthorityResolver(final BiFunction<String, URIAuthority, URIAuthority> authorityResolver) {
        this.authorityResolver = authorityResolver;
        return this;
    }

    public WebSocketServerBootstrap setRequestRouter(final HttpRequestMapper<Supplier<WebSocketHandler>> requestRouter) {
        this.requestRouter = requestRouter;
        return this;
    }

    public WebSocketServerBootstrap setWebSocketConfig(final WebSocketConfig webSocketConfig) {
        this.webSocketConfig = webSocketConfig;
        return this;
    }

    public WebSocketServerBootstrap setExtensionRegistry(final WebSocketExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
        return this;
    }

    public WebSocketServerBootstrap setConnectionFactory(
            final HttpConnectionFactory<? extends WebSocketServerConnection> connectionFactory) {
        this.connectionFactory = connectionFactory;
        return this;
    }

    public WebSocketServerBootstrap register(final String uriPattern, final Supplier<WebSocketHandler> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "WebSocket handler supplier");
        this.routeEntries.add(new RequestRouter.Entry<>(uriPattern, supplier));
        return this;
    }

    public WebSocketServerBootstrap register(final String hostname, final String uriPattern, final Supplier<WebSocketHandler> supplier) {
        Args.notNull(hostname, "Hostname");
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "WebSocket handler supplier");
        this.routeEntries.add(new RequestRouter.Entry<>(hostname, uriPattern, supplier));
        return this;
    }

    public WebSocketServer create() {
        final String actualCanonicalHostName = canonicalHostName != null ? canonicalHostName : InetAddressUtils.getCanonicalLocalHostName();
        final HttpRequestMapper<Supplier<WebSocketHandler>> requestRouterCopy;
        if (routeEntries.isEmpty()) {
            requestRouterCopy = requestRouter;
        } else {
            requestRouterCopy = RequestRouter.create(
                    new URIAuthority(actualCanonicalHostName),
                    UriPatternType.URI_PATTERN,
                    routeEntries,
                    authorityResolver != null ? authorityResolver : RequestRouter.IGNORE_PORT_AUTHORITY_RESOLVER,
                    requestRouter);
        }
        final HttpRequestMapper<Supplier<WebSocketHandler>> router = requestRouterCopy != null ? requestRouterCopy : (r, c) -> null;

        final WebSocketExtensionRegistry extensions = extensionRegistry != null
                ? extensionRegistry
                : WebSocketExtensionRegistry.createDefault();
        final WebSocketServerRequestHandler requestHandler = new WebSocketServerRequestHandler(
                router,
                webSocketConfig != null ? webSocketConfig : WebSocketConfig.DEFAULT,
                extensions);

        final HttpProcessor processor = httpProcessor != null ? httpProcessor : HttpProcessors.server();
        final WebSocketHttpService httpService = new WebSocketHttpService(
                processor,
                requestHandler,
                http1Config,
                connStrategy != null ? connStrategy : DefaultConnectionReuseStrategy.INSTANCE,
                streamListener);

        HttpConnectionFactory<? extends WebSocketServerConnection> connectionFactoryCopy = this.connectionFactory;
        if (connectionFactoryCopy == null) {
            final String scheme = serverSocketFactory instanceof SSLServerSocketFactory || sslContext != null ?
                    URIScheme.HTTPS.id : URIScheme.HTTP.id;
            connectionFactoryCopy = new WebSocketServerConnectionFactory(scheme, http1Config, charCodingConfig);
        }

        final HttpServer httpServer = new HttpServer(
                Math.max(this.listenerPort, 0),
                httpService,
                this.localAddress,
                this.socketConfig != null ? this.socketConfig : SocketConfig.DEFAULT,
                serverSocketFactory,
                connectionFactoryCopy,
                sslContext,
                sslSetupHandler != null ? sslSetupHandler : DefaultTlsSetupHandler.SERVER,
                this.exceptionListener != null ? this.exceptionListener : ExceptionListener.NO_OP);
        return new WebSocketServer(httpServer);
    }
}
