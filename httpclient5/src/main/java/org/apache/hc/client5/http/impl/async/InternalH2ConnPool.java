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
import java.time.Clock;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.reactor.AbstractIOSessionPool;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

class InternalH2ConnPool implements ModalCloseable {

    private final SessionPool sessionPool;

    InternalH2ConnPool(
            final Clock clock,
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy) {
        this.sessionPool = new SessionPool(clock, connectionInitiator, addressResolver, tlsStrategy);
    }

    @Override
    public void close(final CloseMode closeMode) {
        sessionPool.close(closeMode);
    }

    @Override
    public void close() {
        sessionPool.close();
    }

    public Future<IOSession> getSession(
            final HttpRoute route,
            final Timeout connectTimeout,
            final FutureCallback<IOSession> callback) {
        return sessionPool.getSession(route, connectTimeout, callback);
    }

    public void setConnectionConfigResolver(final Resolver<HttpHost, ConnectionConfig> connectionConfigResolver) {
        this.sessionPool.setConnectionConfigResolver(connectionConfigResolver);
    }

    public void evictExpired() {
        sessionPool.evictExpired();
    }

    static class SessionPool extends AbstractIOSessionPool<HttpRoute> {

        private final ConnectionInitiator connectionInitiator;
        private final Resolver<HttpHost, InetSocketAddress> addressResolver;
        private final TlsStrategy tlsStrategy;

        private volatile Resolver<HttpHost, ConnectionConfig> connectionConfigResolver;

        SessionPool(final Clock clock,
                    final ConnectionInitiator connectionInitiator,
                    final Resolver<HttpHost, InetSocketAddress> addressResolver,
                    final TlsStrategy tlsStrategy) {
            super(clock);
            this.connectionInitiator = connectionInitiator;
            this.addressResolver = addressResolver;
            this.tlsStrategy = tlsStrategy;
        }

        public void setConnectionConfigResolver(final Resolver<HttpHost, ConnectionConfig> connectionConfigResolver) {
            this.connectionConfigResolver = connectionConfigResolver;
        }

        @Override
        protected Future<IOSession> connectSession(final HttpRoute route,
                                                   final Timeout timeout,
                                                   final FutureCallback<IOSession> callback) {
            final HttpHost target = route.getTargetHost();
            final InetSocketAddress localAddress = route.getLocalSocketAddress();
            final InetSocketAddress remoteAddress = addressResolver.resolve(target);
            final ConnectionConfig connectionConfig = resolveConnectionConfig(route);
            final Timeout connectTimeout = timeout != null ? timeout : connectionConfig.getConnectTimeout();
            return connectionInitiator.connect(
                    target,
                    remoteAddress,
                    localAddress,
                    connectTimeout,
                    null,
                    new CallbackContribution<IOSession>(callback) {

                        @Override
                        public void completed(final IOSession ioSession) {
                            final Timeout socketTimeout = connectionConfig.getSocketTimeout();
                            if (tlsStrategy != null
                                    && URIScheme.HTTPS.same(target.getSchemeName())
                                    && ioSession instanceof TransportSecurityLayer) {
                                ioSession.setSocketTimeout(connectTimeout);
                                tlsStrategy.upgrade(
                                        (TransportSecurityLayer) ioSession,
                                        target,
                                        null,
                                        connectTimeout,
                                        new CallbackContribution<TransportSecurityLayer>(callback) {

                                            @Override
                                            public void completed(final TransportSecurityLayer transportSecurityLayer) {
                                                if (socketTimeout != null) {
                                                    ioSession.setSocketTimeout(socketTimeout);
                                                }
                                                callback.completed(ioSession);
                                            }

                                        });
                            } else {
                                if (socketTimeout != null) {
                                    ioSession.setSocketTimeout(socketTimeout);
                                }
                                callback.completed(ioSession);
                            }
                        }

                    });
        }

        @Override
        protected void validateSession(final HttpRoute route,
                                       final IOSession ioSession,
                                       final Callback<Boolean> callback) {
            if (ioSession.isOpen()) {
                final ConnectionConfig connectionConfig = resolveConnectionConfig(route);
                final TimeValue validateAfterInactivity = connectionConfig.getValidateAfterInactivity();
                if (TimeValue.isNonNegative(validateAfterInactivity)) {
                    final long deadline = this.clockMillis() - validateAfterInactivity.toMilliseconds();
                    if (ioSession.getLastEventTime() <= deadline) {
                        ioSession.enqueue(new PingCommand(new BasicPingHandler(callback)), Command.Priority.NORMAL);
                        return;
                    }
                }
                callback.execute(Boolean.TRUE);
            } else {
                callback.execute(Boolean.FALSE);
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

        ConnectionConfig resolveConnectionConfig(final HttpRoute route) {
            final HttpHost target = route.getTargetHost();
            final Resolver<HttpHost, ConnectionConfig> localResolver = connectionConfigResolver;
            final ConnectionConfig connectionConfig = localResolver != null ? localResolver.resolve(target) : null;
            return connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
        }

        @Override
        protected boolean isIdle(final IOSession session) {
            final IOEventHandler handler = session.getHandler();
            return handler instanceof HttpConnection && ((HttpConnection) handler).isIdle();
        }

        @Override
        protected boolean isExpired(final HttpRoute route, final IOSession ioSession) {
            final ConnectionConfig connectionConfig = resolveConnectionConfig(route);
            final Timeout idleTimeout = connectionConfig.getIdleTimeout();
            if (idleTimeout != null) {
                final long deadline = this.clockMillis() - idleTimeout.toMilliseconds();
                if (ioSession.getLastEventTime() <= deadline) {
                    return true;
                }
            }
            final TimeValue timeToLive = connectionConfig.getTimeToLive();
            if (TimeValue.isPositive(timeToLive)) {
                final long initiationTime = ioSession.getInitiationTime();
                if (initiationTime > 0) {
                    final long totalLifeTime = clockMillis() - initiationTime;
                    if (totalLifeTime > timeToLive.toMilliseconds()) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

}
