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
package org.apache.hc.client5.http.impl.async;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * Helper for establishing HTTP/2 CONNECT tunnels through HTTP/2 proxies.
 * <p>
 * Multiplexing-safe: tunnel close affects only the CONNECT stream,
 * not the underlying physical HTTP/2 connection.
 * </p>
 * <p>
 * This helper does not handle proxy authentication (407 retries).
 * That responsibility belongs to client implementations that maintain
 * authentication state.
 * </p>
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class H2OverH2TunnelSupport {

    private H2OverH2TunnelSupport() {
    }

    /**
     * Establishes a CONNECT tunnel and returns the resulting {@link ProtocolIOSession}
     * via the callback. The tunnel session supports optional TLS upgrade.
     *
     * @param proxySession   the HTTP/2 session to the proxy
     * @param target         the target endpoint for the CONNECT request authority
     * @param connectTimeout timeout for the CONNECT handshake
     * @param secure         whether to upgrade the tunnel to TLS after establishment
     * @param tlsStrategy    TLS strategy for the upgrade (required when {@code secure} is true)
     * @param callback       completion callback receiving the tunnel session
     * @since 5.7
     */
    public static void establish(
            final IOSession proxySession,
            final NamedEndpoint target,
            final Timeout connectTimeout,
            final boolean secure,
            final TlsStrategy tlsStrategy,
            final FutureCallback<ProtocolIOSession> callback) {
        establishInternal(proxySession, target, connectTimeout, secure, tlsStrategy, null, null, callback);
    }

    /**
     * Establishes a CONNECT tunnel with a request interceptor for injecting
     * headers (e.g. proxy authentication) into the CONNECT request.
     *
     * @param proxySession               the HTTP/2 session to the proxy
     * @param target                     the target endpoint for the CONNECT request authority
     * @param connectTimeout             timeout for the CONNECT handshake
     * @param secure                     whether to upgrade the tunnel to TLS after establishment
     * @param tlsStrategy                TLS strategy for the upgrade (required when {@code secure} is true)
     * @param connectRequestInterceptor  interceptor applied to the CONNECT request before sending
     * @param callback                   completion callback receiving the tunnel session
     * @since 5.7
     */
    public static void establish(
            final IOSession proxySession,
            final NamedEndpoint target,
            final Timeout connectTimeout,
            final boolean secure,
            final TlsStrategy tlsStrategy,
            final HttpRequestInterceptor connectRequestInterceptor,
            final FutureCallback<ProtocolIOSession> callback) {
        establishInternal(proxySession, target, connectTimeout, secure, tlsStrategy, connectRequestInterceptor, null, callback);
    }

    /**
     * Establishes a CONNECT tunnel and starts a protocol handler inside it.
     * The protocol starter factory creates an {@link org.apache.hc.core5.reactor.IOEventHandler}
     * that is connected to the tunnel session immediately after establishment.
     *
     * @param proxySession    the HTTP/2 session to the proxy
     * @param target          the target endpoint for the CONNECT request authority
     * @param connectTimeout  timeout for the CONNECT handshake
     * @param secure          whether to upgrade the tunnel to TLS after establishment
     * @param tlsStrategy     TLS strategy for the upgrade (required when {@code secure} is true)
     * @param protocolStarter factory for the protocol handler to run inside the tunnel
     * @param callback        completion callback receiving the tunnel session
     * @since 5.7
     */
    public static void establish(
            final IOSession proxySession,
            final NamedEndpoint target,
            final Timeout connectTimeout,
            final boolean secure,
            final TlsStrategy tlsStrategy,
            final IOEventHandlerFactory protocolStarter,
            final FutureCallback<IOSession> callback) {
        establish(proxySession, target, connectTimeout, secure, tlsStrategy, null, protocolStarter, callback);
    }

    /**
     * Establishes a CONNECT tunnel with both a request interceptor and a protocol starter.
     * This is the most general overload combining proxy authentication support with
     * automatic protocol initialization inside the tunnel.
     *
     * @param proxySession               the HTTP/2 session to the proxy
     * @param target                     the target endpoint for the CONNECT request authority
     * @param connectTimeout             timeout for the CONNECT handshake
     * @param secure                     whether to upgrade the tunnel to TLS after establishment
     * @param tlsStrategy                TLS strategy for the upgrade (required when {@code secure} is true)
     * @param connectRequestInterceptor  interceptor applied to the CONNECT request before sending
     * @param protocolStarter            factory for the protocol handler to run inside the tunnel
     * @param callback                   completion callback receiving the tunnel session
     * @since 5.7
     */
    public static void establish(
            final IOSession proxySession,
            final NamedEndpoint target,
            final Timeout connectTimeout,
            final boolean secure,
            final TlsStrategy tlsStrategy,
            final HttpRequestInterceptor connectRequestInterceptor,
            final IOEventHandlerFactory protocolStarter,
            final FutureCallback<IOSession> callback) {

        final FutureCallback<ProtocolIOSession> adapter = callback != null ? new FutureCallback<ProtocolIOSession>() {

            @Override
            public void completed(final ProtocolIOSession result) {
                callback.completed(result);
            }

            @Override
            public void failed(final Exception ex) {
                callback.failed(ex);
            }

            @Override
            public void cancelled() {
                callback.cancelled();
            }

        } : null;

        establishInternal(proxySession, target, connectTimeout, secure, tlsStrategy, connectRequestInterceptor, protocolStarter, adapter);
    }

    private static void establishInternal(
            final IOSession proxySession,
            final NamedEndpoint target,
            final Timeout connectTimeout,
            final boolean secure,
            final TlsStrategy tlsStrategy,
            final HttpRequestInterceptor connectRequestInterceptor,
            final IOEventHandlerFactory protocolStarter,
            final FutureCallback<ProtocolIOSession> callback) {

        Args.notNull(proxySession, "Proxy I/O session");
        Args.notNull(target, "Tunnel target endpoint");
        if (secure) {
            Args.notNull(tlsStrategy, "TLS strategy");
        }

        final H2OverH2TunnelExchangeHandler exchangeHandler = new H2OverH2TunnelExchangeHandler(
                proxySession,
                target,
                connectTimeout,
                secure,
                tlsStrategy,
                connectRequestInterceptor,
                protocolStarter,
                callback);

        proxySession.enqueue(
                new RequestExecutionCommand(
                        exchangeHandler,
                        null,
                        HttpCoreContext.create(),
                        exchangeHandler::initiated),
                Command.Priority.NORMAL);
    }

    static void closeQuietly(final ProtocolIOSession session) {
        if (session != null) {
            session.close(CloseMode.IMMEDIATE);
        }
    }
}
