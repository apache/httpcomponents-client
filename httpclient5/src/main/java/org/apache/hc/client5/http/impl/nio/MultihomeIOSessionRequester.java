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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.ConnectExceptionSupport;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MultihomeIOSessionRequester {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final DnsResolver dnsResolver;

    MultihomeIOSessionRequester(final DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
    }

    public Future<IOSession> connect(
            final ConnectionInitiator connectionInitiator,
            final NamedEndpoint remoteEndpoint,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<IOSession> callback) {

        if (remoteAddress != null) {
            if (log.isDebugEnabled()) {
                log.debug(remoteEndpoint + ": connecting " + localAddress + " to " + remoteAddress + " (" + connectTimeout + ")");
            }
            return connectionInitiator.connect(remoteEndpoint, remoteAddress, localAddress, connectTimeout, attachment, callback);
        }

        if (log.isDebugEnabled()) {
            log.debug(remoteEndpoint + ": resolving remote address");
        }

        final ComplexFuture<IOSession> future = new ComplexFuture<>(callback);
        final InetAddress[] remoteAddresses;
        try {
            remoteAddresses = dnsResolver.resolve(remoteEndpoint.getHostName());
        } catch (final UnknownHostException ex) {
            future.failed(ex);
            return future;
        }

        if (log.isDebugEnabled()) {
            log.debug(remoteEndpoint + ": resolved to " + Arrays.asList(remoteAddresses));
        }

        final Runnable runnable = new Runnable() {

            private final AtomicInteger attempt = new AtomicInteger(0);

            void executeNext() {
                final int index = attempt.getAndIncrement();
                final InetSocketAddress remoteAddress = new InetSocketAddress(remoteAddresses[index], remoteEndpoint.getPort());

                if (log.isDebugEnabled()) {
                    log.debug(remoteEndpoint + ": connecting " + localAddress + " to " + remoteAddress + " (" + connectTimeout + ")");
                }

                final Future<IOSession> sessionFuture = connectionInitiator.connect(
                        remoteEndpoint,
                        remoteAddress,
                        localAddress,
                        connectTimeout,
                        attachment,
                        new FutureCallback<IOSession>() {

                            @Override
                            public void completed(final IOSession session) {
                                if (log.isDebugEnabled()) {
                                    if (log.isDebugEnabled()) {
                                        log.debug(remoteEndpoint + ": connected " + session.getId() + " " +
                                                session.getLocalAddress() + "->" + session.getRemoteAddress());
                                    }
                                }
                                future.completed(session);
                            }

                            @Override
                            public void failed(final Exception cause) {
                                if (attempt.get() >= remoteAddresses.length) {
                                    if (log.isDebugEnabled()) {
                                        log.debug(remoteEndpoint + ": connection to " + remoteAddress + " failed " +
                                                "(" + cause.getClass() + "); terminating operation");
                                    }
                                    if (cause instanceof IOException) {
                                        future.failed(ConnectExceptionSupport.enhance((IOException) cause, remoteEndpoint, remoteAddresses));
                                    } else {
                                        future.failed(cause);
                                    }
                                } else {
                                    if (log.isDebugEnabled()) {
                                        log.debug(remoteEndpoint + ": connection to " + remoteAddress + " failed " +
                                                "(" + cause.getClass() + "); retrying connection to the next address");
                                    }
                                    executeNext();
                                }
                            }

                            @Override
                            public void cancelled() {
                                future.cancel();
                            }

                        });
                future.setDependency(sessionFuture);
            }

            @Override
            public void run() {
                executeNext();
            }

        };
        runnable.run();
        return future;
    }

    public Future<IOSession> connect(
            final ConnectionInitiator connectionInitiator,
            final NamedEndpoint remoteEndpoint,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<IOSession> callback) {
        return connect(connectionInitiator, remoteEndpoint, null, localAddress, connectTimeout, attachment, callback);
    }

}
