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

import java.net.InetSocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.AbstractIOSessionPool;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

class InternalH2ConnPool implements ModalCloseable {

    private final SessionPool sessionPool;

    private volatile Resolver<HttpHost, ConnectionConfig> connectionConfigResolver;

    InternalH2ConnPool(final ConnectionInitiator connectionInitiator,
                       final Resolver<HttpHost, InetSocketAddress> addressResolver,
                       final TlsStrategy tlsStrategy) {
        this.sessionPool = new SessionPool(connectionInitiator, addressResolver, tlsStrategy);
    }

    @Override
    public void close(final CloseMode closeMode) {
        sessionPool.close(closeMode);
    }

    @Override
    public void close() {
        sessionPool.close();
    }

    private ConnectionConfig resolveConnectionConfig(final HttpHost httpHost) {
        final Resolver<HttpHost, ConnectionConfig> resolver = this.connectionConfigResolver;
        final ConnectionConfig connectionConfig = resolver != null ? resolver.resolve(httpHost) : null;
        return connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
    }

    public Future<IOSession> getSession(
            final HttpRoute route,
            final Timeout connectTimeout,
            final FutureCallback<IOSession> callback) {
        final ConnectionConfig connectionConfig = resolveConnectionConfig(route.getTargetHost());
        return sessionPool.getSession(
                route,
                connectTimeout != null ? connectTimeout : connectionConfig.getConnectTimeout(),
                new CallbackContribution<IOSession>(callback) {

                    @Override
                    public void completed(final IOSession ioSession) {
                        final Timeout socketTimeout = connectionConfig.getSocketTimeout();
                        if (socketTimeout != null) {
                            ioSession.setSocketTimeout(socketTimeout);
                        }
                        callback.completed(ioSession);
                    }

                });
    }

    public void closeIdle(final TimeValue idleTime) {
        sessionPool.closeIdle(idleTime);
    }

    public void setConnectionConfigResolver(final Resolver<HttpHost, ConnectionConfig> connectionConfigResolver) {
        this.connectionConfigResolver = connectionConfigResolver;
    }

    public TimeValue getValidateAfterInactivity() {
        return sessionPool.validateAfterInactivity;
    }

    public void setValidateAfterInactivity(final TimeValue timeValue) {
        sessionPool.validateAfterInactivity = timeValue;
    }


    static class SessionPool extends AbstractIOSessionPool<HttpRoute> {

        private final ConnectionInitiator connectionInitiator;
        private final Resolver<HttpHost, InetSocketAddress> addressResolver;
        private final TlsStrategy tlsStrategy;

        private volatile TimeValue validateAfterInactivity = TimeValue.NEG_ONE_MILLISECOND;

        SessionPool(final ConnectionInitiator connectionInitiator,
                    final Resolver<HttpHost, InetSocketAddress> addressResolver,
                    final TlsStrategy tlsStrategy) {
            this.connectionInitiator = connectionInitiator;
            this.addressResolver = addressResolver;
            this.tlsStrategy = tlsStrategy;
        }

        @Override
        protected Future<IOSession> connectSession(final HttpRoute route,
                                                   final Timeout connectTimeout,
                                                   final FutureCallback<IOSession> callback) {
            final HttpHost target = route.getTargetHost();
            final InetSocketAddress localAddress = route.getLocalSocketAddress();
            final InetSocketAddress remoteAddress = addressResolver.resolve(target);
            return connectionInitiator.connect(
                    target,
                    remoteAddress,
                    localAddress,
                    connectTimeout,
                    null,
                    new CallbackContribution<IOSession>(callback) {

                        @Override
                        public void completed(final IOSession ioSession) {
                            if (tlsStrategy != null
                                    && URIScheme.HTTPS.same(target.getSchemeName())
                                    && ioSession instanceof TransportSecurityLayer) {
                                final NamedEndpoint tlsName = route.getTargetName() != null ? route.getTargetName() : target;
                                tlsStrategy.upgrade(
                                        (TransportSecurityLayer) ioSession,
                                        tlsName,
                                        null,
                                        connectTimeout,
                                        new CallbackContribution<TransportSecurityLayer>(callback) {

                                            @Override
                                            public void completed(final TransportSecurityLayer transportSecurityLayer) {
                                                callback.completed(ioSession);
                                            }

                                        });
                                ioSession.setSocketTimeout(connectTimeout);
                            } else {
                                callback.completed(ioSession);
                            }
                        }

                    });
        }

        @Override
        protected void validateSession(final IOSession ioSession,
                                       final Callback<Boolean> callback) {
            if (ioSession.isOpen()) {
                final TimeValue timeValue = validateAfterInactivity;
                if (TimeValue.isNonNegative(timeValue)) {
                    final long lastAccessTime = Math.min(ioSession.getLastReadTime(), ioSession.getLastWriteTime());
                    final long deadline = lastAccessTime + timeValue.toMilliseconds();
                    if (deadline <= System.currentTimeMillis()) {
                        final Timeout socketTimeoutMillis = ioSession.getSocketTimeout();
                        ioSession.enqueue(new PingCommand(new BasicPingHandler(result -> {
                            ioSession.setSocketTimeout(socketTimeoutMillis);
                            callback.execute(result);
                        })), Command.Priority.NORMAL);
                        return;
                    }
                }
                callback.execute(true);
            } else {
                callback.execute(false);
            }
        }

        @Override
        protected void closeSession(final IOSession ioSession,
                                    final CloseMode closeMode) {
            if (closeMode == CloseMode.GRACEFUL) {
                ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);
            } else {
                ioSession.close(closeMode);
            }
        }
    }

}
