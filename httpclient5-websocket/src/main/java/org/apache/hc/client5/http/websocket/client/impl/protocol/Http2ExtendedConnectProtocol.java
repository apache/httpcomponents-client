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

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.transport.DataStreamChannelTransport;
import org.apache.hc.client5.http.websocket.transport.WebSocketSessionEngine;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RFC 8441 (HTTP/2 Extended CONNECT) WebSocket protocol strategy.
 *
 * <p>The handshake is handled here; all frame processing, close handshake,
 * and the application-facing {@link WebSocket} facade are delegated to
 * {@link WebSocketSessionEngine}.</p>
 */
@Internal
public final class Http2ExtendedConnectProtocol implements WebSocketProtocolStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(Http2ExtendedConnectProtocol.class);

    private static final ScheduledExecutorService CLOSE_TIMER =
            Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("ws-h2-close", true));

    public static final class H2NotAvailable extends RuntimeException {
        public H2NotAvailable(final String msg) {
            super(msg);
        }
    }

    private final H2MultiplexingRequester requester;

    public Http2ExtendedConnectProtocol(final H2MultiplexingRequester requester) {
        this.requester = requester;
    }

    @Override
    public boolean isFallbackCandidate(final Throwable cause) {
        return cause instanceof H2NotAvailable || cause instanceof IOException;
    }

    @Override
    public CompletableFuture<WebSocket> connect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfg,
            final HttpContext context) {

        final CompletableFuture<WebSocket> f = new CompletableFuture<>();

        if (requester == null) {
            f.completeExceptionally(new H2NotAvailable("HTTP/2 requester not configured"));
            return f;
        }
        Args.notNull(uri, "uri");
        Args.notNull(listener, "listener");
        Args.notNull(cfg, "cfg");

        final boolean secure = "wss".equalsIgnoreCase(uri.getScheme());
        if (!secure && !"ws".equalsIgnoreCase(uri.getScheme())) {
            f.completeExceptionally(new IllegalArgumentException("Scheme must be ws or wss"));
            return f;
        }

        final String scheme = secure ? URIScheme.HTTPS.id : URIScheme.HTTP.id;
        final int port = uri.getPort() > 0 ? uri.getPort() : (secure ? 443 : 80);

        final String host = Args.notBlank(uri.getHost(), "host");
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        final String fullPath = uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;

        final HttpHost target = new HttpHost(scheme, host, port);

        final BasicHttpRequest req = new BasicHttpRequest(Method.CONNECT.name(), target, fullPath);
        req.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        req.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION_LOWER, "13");

        if (!cfg.getSubprotocols().isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (final String p : cfg.getSubprotocols()) {
                if (p != null && !p.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(p);
                }
            }
            if (sb.length() > 0) {
                req.addHeader(WebSocketConstants.SEC_WEBSOCKET_PROTOCOL_LOWER, sb.toString());
            }
        }

        if (cfg.isPerMessageDeflateEnabled()) {
            final StringBuilder ext = new StringBuilder("permessage-deflate");
            if (cfg.isOfferServerNoContextTakeover()) {
                ext.append("; server_no_context_takeover");
            }
            if (cfg.isOfferClientNoContextTakeover()) {
                ext.append("; client_no_context_takeover");
            }
            if (cfg.getOfferClientMaxWindowBits() != null && cfg.getOfferClientMaxWindowBits() == 15) {
                ext.append("; client_max_window_bits=15");
            }
            if (cfg.getOfferServerMaxWindowBits() != null) {
                ext.append("; server_max_window_bits=").append(cfg.getOfferServerMaxWindowBits());
            }
            req.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER, ext.toString());
        }

        final Timeout timeout = cfg.getConnectTimeout() != null ? cfg.getConnectTimeout() : Timeout.ofSeconds(10);
        requester.execute(target, new H2WebSocketExchangeHandler(req, listener, cfg, f), null, timeout, context);
        return f;
    }

    // ====================================================================
    //  H2 exchange handler — thin adapter to WebSocketSessionEngine
    // ====================================================================

    private static final class H2WebSocketExchangeHandler implements AsyncClientExchangeHandler {

        private final BasicHttpRequest request;
        private final WebSocketListener listener;
        private final WebSocketClientConfig cfg;
        private final CompletableFuture<WebSocket> future;

        private final DataStreamChannelTransport transport;
        private final AtomicBoolean outputPrimed = new AtomicBoolean(false);
        private WebSocketSessionEngine engine; // created in consumeResponse

        H2WebSocketExchangeHandler(
                final BasicHttpRequest request,
                final WebSocketListener listener,
                final WebSocketClientConfig cfg,
                final CompletableFuture<WebSocket> future) {
            this.request = request;
            this.listener = listener;
            this.cfg = cfg;
            this.future = future;
            this.transport = new DataStreamChannelTransport();
        }

        @Override
        public void produceRequest(final RequestChannel channel, final HttpContext context)
                throws HttpException, IOException {
            channel.sendRequest(request, new BasicEntityDetails(-1, null), context);
        }

        @Override
        public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails,
                                    final HttpContext context) throws HttpException, IOException {

            if (response.getCode() != HttpStatus.SC_OK) {
                failFuture(new IllegalStateException("Unexpected status: " + response.getCode()));
                return;
            }

            try {
                final String selectedProto = headerValue(response,
                        WebSocketConstants.SEC_WEBSOCKET_PROTOCOL_LOWER);
                if (selectedProto != null && !selectedProto.isEmpty()) {
                    final List<String> offered = cfg.getSubprotocols();
                    if (!offered.contains(selectedProto)) {
                        throw new ProtocolException(
                                "Server selected unsupported subprotocol: " + selectedProto);
                    }
                }

                final ExtensionChain chain = Http1UpgradeProtocol.buildExtensionChain(
                        cfg, headerValue(response, WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER));

                this.engine = new WebSocketSessionEngine(
                        transport, listener, cfg, chain, CLOSE_TIMER);

                if (!future.isDone()) {
                    future.complete(engine.facade());
                }
                listener.onOpen(engine.facade());
            } catch (final Exception ex) {
                failFuture(ex);
            }
        }

        @Override
        public void consumeInformation(final HttpResponse response, final HttpContext context)
                throws HttpException, IOException {
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            if (engine != null) {
                engine.onData(src);
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            if (engine != null) {
                engine.onDisconnected();
            } else if (!future.isDone()) {
                failFuture(new ProtocolException("Stream ended before handshake completed"));
            }
        }

        @Override
        public int available() {
            if (transport.getChannel() == null && outputPrimed.compareAndSet(false, true)) {
                return 1;
            }
            if (engine == null) {
                return 0;
            }
            return transport.isEndStreamPending() ? 1 : engine.availableForOutput();
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            transport.setChannel(channel);
            if (engine != null) {
                engine.onOutputReady();
            }
            if (transport.isEndStreamPending()) {
                transport.endStream();
            }
        }

        @Override
        public void failed(final Exception cause) {
            if (engine != null) {
                engine.onError(cause);
            } else {
                listener.onError(cause);
            }
            failFuture(cause);
        }

        @Override
        public void releaseResources() {
            if (!future.isDone()) {
                failFuture(new ProtocolException("WebSocket exchange released"));
            }
            if (engine != null) {
                engine.onDisconnected();
            }
        }

        @Override
        public void cancel() {
            if (!future.isDone()) {
                failFuture(new ProtocolException("WebSocket exchange cancelled"));
            }
            if (engine != null) {
                engine.onDisconnected();
            }
        }

        private static String headerValue(final HttpResponse r, final String name) {
            final Header h = r.getFirstHeader(name);
            return h != null ? h.getValue() : null;
        }

        private void failFuture(final Exception ex) {
            if (!future.isDone()) {
                future.completeExceptionally(ex);
            }
        }
    }
}
