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
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.impl.ComplexFuture;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.util.Args;

final class AsyncClientConnectionOperator {

    private final SchemePortResolver schemePortResolver;
    private final DnsResolver dnsResolver;
    private final Lookup<TlsStrategy> tlsStrategyLookup;

    AsyncClientConnectionOperator(
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final Lookup<TlsStrategy> tlsStrategyLookup) {
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
        this.tlsStrategyLookup = tlsStrategyLookup;
    }

    public Future<ManagedAsyncClientConnection> connect(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final long timeout,
            final TimeUnit timeUnit,
            final FutureCallback<ManagedAsyncClientConnection> callback) {
        Args.notNull(connectionInitiator, "Connection initiator");
        Args.notNull(host, "Host");
        final ComplexFuture<ManagedAsyncClientConnection> future = new ComplexFuture<>(callback);
        final InetAddress[] remoteAddresses;
        try {
            remoteAddresses = dnsResolver.resolve(host.getHostName());
        } catch (UnknownHostException ex) {
            future.failed(ex);
            return future;
        }
        final int port;
        try {
            port = schemePortResolver.resolve(host);
        } catch (UnsupportedSchemeException ex) {
            future.failed(ex);
            return future;
        }
        final TlsStrategy tlsStrategy = tlsStrategyLookup != null ? tlsStrategyLookup.lookup(host.getSchemeName()) : null;
        final Runnable runnable = new Runnable() {

            private final AtomicInteger attempt = new AtomicInteger(0);

            void executeNext() {
                final int index = attempt.getAndIncrement();
                final InetSocketAddress remoteAddress = new InetSocketAddress(remoteAddresses[index], port);
                final SessionRequest sessionRequest = connectionInitiator.connect(
                        host,
                        // TODO: fix after upgrading to HttpCore 5.0a3
                        // TODO: remoteAddress
                        localAddress,
                        null, new SessionRequestCallback() {

                            @Override
                            public void completed(final SessionRequest request) {
                                final IOSession session = request.getSession();
                                final ManagedAsyncClientConnection connection = new ManagedAsyncClientConnection(session);
                                if (tlsStrategy != null) {
                                    tlsStrategy.upgrade(
                                            connection,
                                            host.getHostName(),
                                            session.getLocalAddress(),
                                            session.getRemoteAddress());
                                }
                                future.completed(connection);
                            }

                            @Override
                            public void failed(final SessionRequest request) {
                                if (attempt.get() >= remoteAddresses.length) {
                                    future.failed(new HttpHostConnectException(request.getException(), host, remoteAddresses));
                                } else {
                                    executeNext();
                                }
                            }

                            @Override
                            public void timeout(final SessionRequest request) {
                                future.failed(new ConnectTimeoutException(new SocketException(), host, remoteAddresses));
                            }

                            @Override
                            public void cancelled(final SessionRequest request) {
                                future.cancel();
                            }

                        });
                future.setDependency(sessionRequest);
                final int connectTimeout = (int) (timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS).toMillis(timeout);
                sessionRequest.setConnectTimeout(connectTimeout);
            }

            @Override
            public void run() {
                executeNext();
            }

        };
        runnable.run();
        return future;
    }

    public void upgrade(final ManagedAsyncClientConnection connection, final HttpHost host) {
        final TlsStrategy tlsStrategy = tlsStrategyLookup != null ? tlsStrategyLookup.lookup(host.getHostName()) : null;
        if (tlsStrategy != null) {
            tlsStrategy.upgrade(
                    connection,
                    // TODO: fix after upgrading to HttpCore 5.0a3
                    host.getHostName(),
                    connection.getLocalAddress(),
                    connection.getRemoteAddress());
        }

    }
}
