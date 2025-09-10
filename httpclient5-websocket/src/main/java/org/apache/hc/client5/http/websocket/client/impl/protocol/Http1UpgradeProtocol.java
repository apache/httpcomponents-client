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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.impl.connector.WebSocketEndpointConnector;
import org.apache.hc.client5.http.websocket.core.extension.ExtensionChain;
import org.apache.hc.client5.http.websocket.core.extension.PerMessageDeflate;
import org.apache.hc.client5.http.websocket.transport.WebSocketUpgrader;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP/1.1 Upgrade (RFC 6455). Uses getters on WebSocketClientConfig.
 */
@Internal
public final class Http1UpgradeProtocol implements WebSocketProtocolStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(Http1UpgradeProtocol.class);

    private final org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester requester;
    private final ManagedConnPool<HttpHost, IOSession> connPool;

    public Http1UpgradeProtocol(final HttpAsyncRequester requester, final ManagedConnPool<HttpHost, IOSession> connPool) {
        this.requester = requester;
        this.connPool = connPool;
    }

    @Override
    public CompletableFuture<WebSocket> connect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfg,
            final HttpContext context) {

        Args.notNull(uri, "uri");
        Args.notNull(listener, "listener");
        Args.notNull(cfg, "cfg");

        final boolean secure = "wss".equalsIgnoreCase(uri.getScheme());
        if (!secure && !"ws".equalsIgnoreCase(uri.getScheme())) {
            final CompletableFuture<WebSocket> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("Scheme must be ws or wss"));
            return f;
        }

        final String scheme = secure ? URIScheme.HTTPS.id : URIScheme.HTTP.id;
        final int port = uri.getPort() > 0 ? uri.getPort() : secure ? 443 : 80;
        final String host = Args.notBlank(uri.getHost(), "host");
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        final String fullPath = uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;
        final HttpHost target = new HttpHost(scheme, host, port);

        final CompletableFuture<WebSocket> result = new CompletableFuture<>();
        final WebSocketEndpointConnector wsRequester = new WebSocketEndpointConnector(requester, connPool);

        final Timeout timeout = cfg.getConnectTimeout() != null ? cfg.getConnectTimeout() : Timeout.ofSeconds(10);

        wsRequester.connect(target, timeout, null,
                new FutureCallback<WebSocketEndpointConnector.ProtoEndpoint>() {
                    @Override
                    public void completed(final WebSocketEndpointConnector.ProtoEndpoint endpoint) {
                        try {
                            final String secKey = randomKey();
                            final BasicHttpRequest req = new BasicHttpRequest(HttpGet.METHOD_NAME, target, fullPath);

                            req.addHeader(HttpHeaders.CONNECTION, "Upgrade");
                            req.addHeader(HttpHeaders.UPGRADE, "websocket");
                            req.addHeader("Sec-WebSocket-Version", "13");
                            req.addHeader("Sec-WebSocket-Key", secKey);

                            // subprotocols
                            if (cfg.getSubprotocols() != null && !cfg.getSubprotocols().isEmpty()) {
                                final StringJoiner sj = new StringJoiner(", ");
                                for (final String p : cfg.getSubprotocols()) {
                                    if (p != null && !p.isEmpty()) {
                                        sj.add(p);
                                    }
                                }
                                final String offered = sj.toString();
                                if (!offered.isEmpty()) {
                                    req.addHeader("Sec-WebSocket-Protocol", offered);
                                }
                            }

                            // PMCE offer
                            if (cfg.isPerMessageDeflateEnabled()) {
                                final StringBuilder ext = new StringBuilder("permessage-deflate");
                                if (cfg.isOfferServerNoContextTakeover()) {
                                    ext.append("; server_no_context_takeover");
                                }
                                if (cfg.isOfferClientNoContextTakeover()) {
                                    ext.append("; client_no_context_takeover");
                                }
                                if (cfg.getOfferClientMaxWindowBits() != null) {
                                    ext.append("; client_max_window_bits=").append(cfg.getOfferClientMaxWindowBits());
                                }
                                if (cfg.getOfferServerMaxWindowBits() != null) {
                                    ext.append("; server_max_window_bits=").append(cfg.getOfferServerMaxWindowBits());
                                }
                                req.addHeader("Sec-WebSocket-Extensions", ext.toString());
                            }

                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Dispatching HTTP/1.1 Upgrade: GET {} with headers:", fullPath);
                                for (final Header h : req.getHeaders()) {
                                    LOG.debug("  {}: {}", h.getName(), h.getValue());
                                }
                            }

                            final AtomicBoolean done = new AtomicBoolean(false);

                            final AsyncClientExchangeHandler upgrade = new AsyncClientExchangeHandler() {
                                @Override
                                public void releaseResources() {
                                }

                                @Override
                                public void failed(final Exception cause) {
                                    if (done.compareAndSet(false, true)) {
                                        try {
                                            endpoint.releaseAndDiscard();
                                        } catch (final Throwable ignore) {
                                        }
                                        result.completeExceptionally(cause);
                                    }
                                }

                                @Override
                                public void cancel() {
                                    if (done.compareAndSet(false, true)) {
                                        try {
                                            endpoint.releaseAndDiscard();
                                        } catch (final Throwable ignore) {
                                        }
                                        result.cancel(true);
                                    }
                                }

                                @Override
                                public void produceRequest(final RequestChannel ch,
                                                           final org.apache.hc.core5.http.protocol.HttpContext hc)
                                        throws java.io.IOException, HttpException {
                                    ch.sendRequest(req, null, hc);
                                }

                                @Override
                                public int available() {
                                    return 0;
                                }

                                @Override
                                public void produce(final DataStreamChannel channel) {
                                }

                                @Override
                                public void updateCapacity(final CapacityChannel capacityChannel) {
                                }

                                @Override
                                public void consume(final ByteBuffer src) {
                                }

                                @Override
                                public void streamEnd(final java.util.List<? extends Header> trailers) {
                                }

                                @Override
                                public void consumeInformation(final HttpResponse response,
                                                               final HttpContext hc) {
                                    final int code = response.getCode();
                                    if (code == HttpStatus.SC_SWITCHING_PROTOCOLS && done.compareAndSet(false, true)) {
                                        finishUpgrade(endpoint, response, secKey, listener, cfg, result);
                                    }
                                }

                                @Override
                                public void consumeResponse(final HttpResponse response,
                                                            final EntityDetails entity,
                                                            final HttpContext hc) {
                                    final int code = response.getCode();
                                    if (code == HttpStatus.SC_SWITCHING_PROTOCOLS && done.compareAndSet(false, true)) {
                                        finishUpgrade(endpoint, response, secKey, listener, cfg, result);
                                        return;
                                    }
                                    failed(new IllegalStateException("Unexpected status: " + code));
                                }
                            };

                            endpoint.execute(upgrade, null, context);

                        } catch (final Exception ex) {
                            try {
                                endpoint.releaseAndDiscard();
                            } catch (final Throwable ignore) {
                            }
                            result.completeExceptionally(ex);
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        result.completeExceptionally(ex);
                    }

                    @Override
                    public void cancelled() {
                        result.cancel(true);
                    }
                });

        return result;
    }

    private void finishUpgrade(
            final WebSocketEndpointConnector.ProtoEndpoint endpoint,
            final HttpResponse response,
            final String secKey,
            final WebSocketListener listener,
            final WebSocketClientConfig cfg,
            final CompletableFuture<WebSocket> result) {
        try {
            final String accept = headerValue(response, "Sec-WebSocket-Accept");
            final String expected = expectedAccept(secKey);
            if (!expected.equals(accept)) {
                throw new IllegalStateException("Bad Sec-WebSocket-Accept");
            }

            final String upgrade = headerValue(response, "Upgrade");
            if (upgrade == null || !"websocket".equalsIgnoreCase(upgrade.trim())) {
                throw new IllegalStateException("Missing/invalid Upgrade header: " + upgrade);
            }
            if (!containsToken(response, "Connection", "Upgrade")) {
                throw new IllegalStateException("Missing/invalid Connection header");
            }

            final String proto = headerValue(response, "Sec-WebSocket-Protocol");
            if (proto != null && !proto.isEmpty()) {
                boolean matched = false;
                if (cfg.getSubprotocols() != null) {
                    for (final String p : cfg.getSubprotocols()) {
                        if (p.equals(proto)) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) {
                    throw new IllegalStateException("Server selected subprotocol not offered: " + proto);
                }
            }

            final ExtensionChain chain = new ExtensionChain();
            final String ext = headerValue(response, "Sec-WebSocket-Extensions");
            if (ext != null && !ext.isEmpty()) {
                boolean pmceSeen = false, serverNoCtx = false, clientNoCtx = false;
                Integer clientBits = null, serverBits = null;

                final String[] tokens = ext.split(",");
                for (final String raw0 : tokens) {
                    final String raw = raw0.trim();
                    final String[] parts = raw.split(";");
                    final String token = parts[0].trim().toLowerCase();

                    // Only permessage-deflate is supported
                    if (!"permessage-deflate".equals(token)) {
                        throw new IllegalStateException("Server selected unsupported extension: " + token);
                    }
                    pmceSeen = true;

                    for (int i = 1; i < parts.length; i++) {
                        final String p = parts[i].trim();
                        final int eq = p.indexOf('=');
                        if (eq < 0) {
                            if ("server_no_context_takeover".equalsIgnoreCase(p)) {
                                serverNoCtx = true;
                            } else if ("client_no_context_takeover".equalsIgnoreCase(p)) {
                                clientNoCtx = true;
                            }
                        } else {
                            final String k = p.substring(0, eq).trim();
                            String v = p.substring(eq + 1).trim();
                            if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
                                v = v.substring(1, v.length() - 1); // strip quotes if any
                            }
                            if ("client_max_window_bits".equalsIgnoreCase(k)) {
                                try {
                                    clientBits = Integer.parseInt(v);
                                    if (clientBits < 8 || clientBits > 15) {
                                        throw new IllegalStateException("client_max_window_bits out of range: " + clientBits);
                                    }
                                } catch (final NumberFormatException nfe) {
                                    throw new IllegalStateException("Invalid client_max_window_bits: " + v, nfe);
                                }
                            } else if ("server_max_window_bits".equalsIgnoreCase(k)) {
                                try {
                                    serverBits = Integer.parseInt(v);
                                    if (serverBits < 8 || serverBits > 15) {
                                        throw new IllegalStateException("server_max_window_bits out of range: " + serverBits);
                                    }
                                } catch (final NumberFormatException nfe) {
                                    throw new IllegalStateException("Invalid server_max_window_bits: " + v, nfe);
                                }
                            }
                        }
                    }
                }

                if (pmceSeen) {
                    if (!cfg.isPerMessageDeflateEnabled()) {
                        throw new IllegalStateException("Server negotiated PMCE but client disabled it");
                    }
                    chain.add(new PerMessageDeflate(true, serverNoCtx, clientNoCtx, clientBits, serverBits));
                }
            }

            final ProtocolIOSession ioSession = endpoint.getProtocolIOSession();
            final WebSocketUpgrader upgrader = new WebSocketUpgrader(listener, cfg, chain);
            ioSession.registerProtocol("websocket", upgrader);
            ioSession.switchProtocol("websocket", new FutureCallback<ProtocolIOSession>() {
                @Override
                public void completed(final ProtocolIOSession s) {
                    s.setSocketTimeout(Timeout.DISABLED);
                    final WebSocket ws = upgrader.getWebSocket();
                    try {
                        listener.onOpen(ws);
                    } catch (final Throwable ignore) {
                    }
                    result.complete(ws);
                }

                @Override
                public void failed(final Exception ex) {
                    try {
                        endpoint.releaseAndDiscard();
                    } catch (final Throwable ignore) {
                    }
                    result.completeExceptionally(ex);
                }

                @Override
                public void cancelled() {
                    try {
                        endpoint.releaseAndDiscard();
                    } catch (final Throwable ignore) {
                    }
                    result.cancel(true);
                }
            });

        } catch (final Exception ex) {
            try {
                endpoint.releaseAndDiscard();
            } catch (final Throwable ignore) {
            }
            result.completeExceptionally(ex);
        }
    }

    private static String headerValue(final HttpResponse r, final String name) {
        final Header h = r.getFirstHeader(name);
        return h != null ? h.getValue() : null;
    }

    private static boolean containsToken(final HttpResponse r, final String header, final String token) {
        for (final Header h : r.getHeaders(header)) {
            for (final String p : h.getValue().split(",")) {
                if (p.trim().equalsIgnoreCase(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String randomKey() {
        final byte[] nonce = new byte[16];
        ThreadLocalRandom.current().nextBytes(nonce);
        return java.util.Base64.getEncoder().encodeToString(nonce);
    }

    private static String expectedAccept(final String key) throws Exception {
        final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
        return java.util.Base64.getEncoder().encodeToString(sha1.digest());
    }
}
