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

package org.apache.hc.client5.http.impl.nio;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

final class DefaultAsyncClientConnectionOperator implements AsyncClientConnectionOperator {

    private final SchemePortResolver schemePortResolver;
    private final MultihomeIOSessionRequester sessionRequester;
    private final Lookup<TlsStrategy> tlsStrategyLookup;

    DefaultAsyncClientConnectionOperator(
            final Lookup<TlsStrategy> tlsStrategyLookup,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        this.tlsStrategyLookup = Args.notNull(tlsStrategyLookup, "TLS strategy lookup");
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
        this.sessionRequester = new MultihomeIOSessionRequester(dnsResolver);
    }

    @Override
    public Future<ManagedAsyncClientConnection> connect(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<ManagedAsyncClientConnection> callback) {
        return connect(connectionInitiator, host, localAddress, connectTimeout,
            attachment, null, callback);
    }

    @Override
    public Future<ManagedAsyncClientConnection> connect(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final HttpContext context,
            final FutureCallback<ManagedAsyncClientConnection> callback) {
        Args.notNull(connectionInitiator, "Connection initiator");
        Args.notNull(host, "Host");
        final ComplexFuture<ManagedAsyncClientConnection> future = new ComplexFuture<>(callback);
        final HttpHost remoteEndpoint = RoutingSupport.normalize(host, schemePortResolver);
        final InetAddress remoteAddress = host.getAddress();
        final TlsStrategy tlsStrategy = tlsStrategyLookup != null ? tlsStrategyLookup.lookup(host.getSchemeName()) : null;
        final TlsConfig tlsConfig = attachment instanceof TlsConfig ? (TlsConfig) attachment : TlsConfig.DEFAULT;
        final Future<IOSession> sessionFuture = sessionRequester.connect(
                connectionInitiator,
                remoteEndpoint,
                remoteAddress != null ? new InetSocketAddress(remoteAddress, remoteEndpoint.getPort()) : null,
                localAddress,
                connectTimeout,
                tlsConfig.getHttpVersionPolicy(),
                new FutureCallback<IOSession>() {

                    @Override
                    public void completed(final IOSession session) {
                        final DefaultManagedAsyncClientConnection connection = new DefaultManagedAsyncClientConnection(session);
                        if (tlsStrategy != null && URIScheme.HTTPS.same(host.getSchemeName())) {
                            try {
                                final Timeout socketTimeout = connection.getSocketTimeout();
                                final Timeout handshakeTimeout = tlsConfig.getHandshakeTimeout();
                                tlsStrategy.upgrade(
                                        connection,
                                        host,
                                        attachment,
                                        handshakeTimeout != null ? handshakeTimeout : connectTimeout,
                                        new FutureContribution<TransportSecurityLayer>(future) {

                                            @Override
                                            public void completed(final TransportSecurityLayer transportSecurityLayer) {
                                                connection.setSocketTimeout(socketTimeout);
                                                future.completed(connection);
                                            }

                                        });
                            } catch (final Exception ex) {
                                future.failed(ex);
                            }
                        } else {
                            future.completed(connection);
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        future.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        future.cancel();
                    }

                });
        future.setDependency(sessionFuture);
        return future;
    }

    @Override
    public void upgrade(
            final ManagedAsyncClientConnection connection,
            final HttpHost host,
            final Object attachment) {
        upgrade(connection, host, attachment, null, null);
    }

    @Override
    public void upgrade(
            final ManagedAsyncClientConnection connection,
            final HttpHost host,
            final Object attachment,
            final HttpContext context) {
        upgrade(connection, host, attachment, context, null);
    }

    @Override
    public void upgrade(
            final ManagedAsyncClientConnection connection,
            final HttpHost host,
            final Object attachment,
            final HttpContext context,
            final FutureCallback<ManagedAsyncClientConnection> callback) {
        final TlsStrategy tlsStrategy = tlsStrategyLookup != null ? tlsStrategyLookup.lookup(host.getSchemeName()) : null;
        if (tlsStrategy != null) {
            tlsStrategy.upgrade(
                    connection,
                    host,
                    attachment,
                    null,
                    new CallbackContribution<TransportSecurityLayer>(callback) {

                        @Override
                        public void completed(final TransportSecurityLayer transportSecurityLayer) {
                            if (callback != null) {
                                callback.completed(connection);
                            }
                        }

                    });
        }

    }

}
