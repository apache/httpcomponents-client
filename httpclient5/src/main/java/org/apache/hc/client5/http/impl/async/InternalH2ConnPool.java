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

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.AuthCacheKeeper;
import org.apache.hc.client5.http.impl.auth.AuthenticationHandler;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.http2.nio.support.H2OverH2TunnelSupport;
import org.apache.hc.core5.http2.nio.support.TunnelRefusedException;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.AbstractIOSessionPool;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

class InternalH2ConnPool implements ModalCloseable {

    private final SessionPool sessionPool;

    private volatile Resolver<HttpHost, ConnectionConfig> connectionConfigResolver;

    InternalH2ConnPool(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy) {
        this(connectionInitiator, addressResolver, tlsStrategy, null);
    }

    InternalH2ConnPool(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy,
            final IOEventHandlerFactory tunnelProtocolStarter) {
        this(connectionInitiator, addressResolver, tlsStrategy, tunnelProtocolStarter,
                null, null, null, true, null, null, null);
    }

    InternalH2ConnPool(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy,
            final IOEventHandlerFactory tunnelProtocolStarter,
            final HttpProcessor proxyHttpProcessor,
            final AuthenticationStrategy proxyAuthStrategy,
            final SchemePortResolver schemePortResolver,
            final boolean authCachingDisabled,
            final Lookup<AuthSchemeFactory> authSchemeRegistry,
            final CredentialsProvider credentialsProvider,
            final RequestConfig defaultRequestConfig) {
        this.sessionPool = new SessionPool(
                connectionInitiator,
                addressResolver,
                tlsStrategy,
                tunnelProtocolStarter,
                proxyHttpProcessor,
                proxyAuthStrategy,
                schemePortResolver,
                authCachingDisabled,
                authSchemeRegistry,
                credentialsProvider,
                defaultRequestConfig);
    }

    @Override
    public void close(final CloseMode closeMode) {
        sessionPool.close(closeMode);
    }

    @Override
    public void close() {
        sessionPool.close();
    }

    private ConnectionConfig resolveConnectionConfig(final HttpRoute route) {
        final HttpHost firstHop = route.getProxyHost() != null ? route.getProxyHost() : route.getTargetHost();
        final Resolver<HttpHost, ConnectionConfig> resolver = this.connectionConfigResolver;
        final ConnectionConfig connectionConfig = resolver != null ? resolver.resolve(firstHop) : null;
        return connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
    }

    public Future<IOSession> getSession(
            final HttpRoute route,
            final Timeout connectTimeout,
            final FutureCallback<IOSession> callback) {
        final ConnectionConfig connectionConfig = resolveConnectionConfig(route);
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

        private static final int MAX_TUNNEL_AUTH_ATTEMPTS = 3;

        private final ConnectionInitiator connectionInitiator;
        private final Resolver<HttpHost, InetSocketAddress> addressResolver;
        private final TlsStrategy tlsStrategy;
        private final IOEventHandlerFactory tunnelProtocolStarter;
        private final HttpProcessor proxyHttpProcessor;
        private final AuthenticationStrategy proxyAuthStrategy;
        private final AuthenticationHandler authenticator;
        private final AuthCacheKeeper authCacheKeeper;
        private final Lookup<AuthSchemeFactory> authSchemeRegistry;
        private final CredentialsProvider credentialsProvider;
        private final RequestConfig defaultRequestConfig;

        private volatile TimeValue validateAfterInactivity = TimeValue.NEG_ONE_MILLISECOND;

        SessionPool(
                final ConnectionInitiator connectionInitiator,
                final Resolver<HttpHost, InetSocketAddress> addressResolver,
                final TlsStrategy tlsStrategy,
                final IOEventHandlerFactory tunnelProtocolStarter,
                final HttpProcessor proxyHttpProcessor,
                final AuthenticationStrategy proxyAuthStrategy,
                final SchemePortResolver schemePortResolver,
                final boolean authCachingDisabled,
                final Lookup<AuthSchemeFactory> authSchemeRegistry,
                final CredentialsProvider credentialsProvider,
                final RequestConfig defaultRequestConfig) {
            this.connectionInitiator = connectionInitiator;
            this.addressResolver = addressResolver;
            this.tlsStrategy = tlsStrategy;
            this.tunnelProtocolStarter = tunnelProtocolStarter;
            this.proxyHttpProcessor = proxyHttpProcessor;
            this.proxyAuthStrategy = proxyAuthStrategy;
            this.authenticator = proxyHttpProcessor != null && proxyAuthStrategy != null ? new AuthenticationHandler() : null;
            this.authCacheKeeper = proxyHttpProcessor != null && proxyAuthStrategy != null && !authCachingDisabled && schemePortResolver != null
                    ? new AuthCacheKeeper(schemePortResolver)
                    : null;
            this.authSchemeRegistry = authSchemeRegistry;
            this.credentialsProvider = credentialsProvider;
            this.defaultRequestConfig = defaultRequestConfig != null ? defaultRequestConfig : RequestConfig.DEFAULT;
        }

        @Override
        protected Future<IOSession> connectSession(
                final HttpRoute route,
                final Timeout connectTimeout,
                final FutureCallback<IOSession> callback) {
            final HttpHost proxy = route.getProxyHost();
            final HttpHost target = route.getTargetHost();
            final HttpHost firstHop = proxy != null ? proxy : target;
            final NamedEndpoint firstHopName = proxy == null && route.getTargetName() != null ? route.getTargetName() : firstHop;
            final InetSocketAddress localAddress = route.getLocalSocketAddress();
            final InetSocketAddress remoteAddress = addressResolver.resolve(firstHop);
            return connectionInitiator.connect(
                    firstHopName,
                    remoteAddress,
                    localAddress,
                    connectTimeout,
                    null,
                    new CallbackContribution<IOSession>(callback) {

                        @Override
                        public void completed(final IOSession ioSession) {
                            if (tlsStrategy != null
                                    && URIScheme.HTTPS.same(firstHop.getSchemeName())
                                    && ioSession instanceof TransportSecurityLayer) {
                                tlsStrategy.upgrade(
                                        (TransportSecurityLayer) ioSession,
                                        firstHopName,
                                        null,
                                        connectTimeout,
                                        new CallbackContribution<TransportSecurityLayer>(callback) {

                                            @Override
                                            public void completed(final TransportSecurityLayer transportSecurityLayer) {
                                                completeConnection(route, connectTimeout, ioSession, callback);
                                            }

                                        });
                                ioSession.setSocketTimeout(connectTimeout);
                            } else {
                                completeConnection(route, connectTimeout, ioSession, callback);
                            }
                        }

                    });
        }

        private void completeConnection(
                final HttpRoute route,
                final Timeout connectTimeout,
                final IOSession ioSession,
                final FutureCallback<IOSession> callback) {
            if (!route.isTunnelled()) {
                callback.completed(ioSession);
                return;
            }
            if (tunnelProtocolStarter == null) {
                callback.failed(new IllegalStateException("HTTP/2 tunnel protocol starter not configured"));
                return;
            }
            if (route.isLayered() && tlsStrategy == null) {
                callback.failed(new IllegalStateException("TLS strategy not configured"));
                return;
            }
            final NamedEndpoint targetEndpoint = route.getTargetName() != null ? route.getTargetName() : route.getTargetHost();
            final HttpHost proxy = route.getProxyHost();
            if (proxy != null && proxyHttpProcessor != null && proxyAuthStrategy != null && authenticator != null) {
                establishTunnelWithAuth(route, ioSession, targetEndpoint, proxy, connectTimeout, callback);
            } else {
                H2OverH2TunnelSupport.establish(
                        ioSession,
                        targetEndpoint,
                        connectTimeout,
                        route.isLayered(),
                        tlsStrategy,
                        tunnelProtocolStarter,
                        callback);
            }
        }

        private void establishTunnelWithAuth(
                final HttpRoute route,
                final IOSession ioSession,
                final NamedEndpoint targetEndpoint,
                final HttpHost proxy,
                final Timeout connectTimeout,
                final FutureCallback<IOSession> callback) {
            final HttpClientContext tunnelContext = HttpClientContext.create();
            if (authSchemeRegistry != null) {
                tunnelContext.setAuthSchemeRegistry(authSchemeRegistry);
            }
            if (credentialsProvider != null) {
                tunnelContext.setCredentialsProvider(credentialsProvider);
            }
            tunnelContext.setRequestConfig(defaultRequestConfig);

            final AuthExchange proxyAuthExchange = tunnelContext.getAuthExchange(proxy);
            if (authCacheKeeper != null) {
                authCacheKeeper.loadPreemptively(proxy, null, proxyAuthExchange, tunnelContext);
            }
            establishTunnelWithAuthAttempt(
                    route,
                    ioSession,
                    targetEndpoint,
                    proxy,
                    connectTimeout,
                    callback,
                    tunnelContext,
                    proxyAuthExchange,
                    1);
        }

        private void establishTunnelWithAuthAttempt(
                final HttpRoute route,
                final IOSession ioSession,
                final NamedEndpoint targetEndpoint,
                final HttpHost proxy,
                final Timeout connectTimeout,
                final FutureCallback<IOSession> callback,
                final HttpClientContext tunnelContext,
                final AuthExchange proxyAuthExchange,
                final int attemptCount) {
            H2OverH2TunnelSupport.establish(
                    ioSession,
                    targetEndpoint,
                    connectTimeout,
                    route.isLayered(),
                    tlsStrategy,
                    (request, entityDetails, context) -> {
                        proxyHttpProcessor.process(request, null, tunnelContext);
                        authenticator.addAuthResponse(proxy, ChallengeType.PROXY, request, proxyAuthExchange, tunnelContext);
                    },
                    tunnelProtocolStarter,
                    new FutureCallback<IOSession>() {

                        @Override
                        public void completed(final IOSession result) {
                            callback.completed(result);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            if (!(ex instanceof TunnelRefusedException)) {
                                callback.failed(ex);
                                return;
                            }
                            final TunnelRefusedException tunnelRefusedException = (TunnelRefusedException) ex;
                            final HttpResponse response = tunnelRefusedException.getResponse();
                            if (response.getCode() != HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED
                                    || attemptCount >= MAX_TUNNEL_AUTH_ATTEMPTS) {
                                callback.failed(ex);
                                return;
                            }
                            try {
                                proxyHttpProcessor.process(response, null, tunnelContext);
                                final boolean retry = needAuthentication(proxyAuthExchange, proxy, response, tunnelContext);
                                if (retry) {
                                    establishTunnelWithAuthAttempt(
                                            route,
                                            ioSession,
                                            targetEndpoint,
                                            proxy,
                                            connectTimeout,
                                            callback,
                                            tunnelContext,
                                            proxyAuthExchange,
                                            attemptCount + 1);
                                } else {
                                    callback.failed(ex);
                                }
                            } catch (final AuthenticationException | MalformedChallengeException authEx) {
                                callback.failed(authEx);
                            } catch (final Exception ioEx) {
                                callback.failed(ioEx);
                            }
                        }

                        @Override
                        public void cancelled() {
                            callback.cancelled();
                        }

                    });
        }

        private boolean needAuthentication(
                final AuthExchange proxyAuthExchange,
                final HttpHost proxy,
                final HttpResponse response,
                final HttpClientContext context) throws AuthenticationException, MalformedChallengeException {
            final RequestConfig config = context.getRequestConfigOrDefault();
            if (config.isAuthenticationEnabled()) {
                final boolean proxyAuthRequested = authenticator.isChallenged(
                        proxy, ChallengeType.PROXY, response, proxyAuthExchange, context);
                final boolean proxyMutualAuthRequired = authenticator.isChallengeExpected(proxyAuthExchange);

                if (authCacheKeeper != null) {
                    if (proxyAuthRequested) {
                        authCacheKeeper.updateOnChallenge(proxy, null, proxyAuthExchange, context);
                    } else {
                        authCacheKeeper.updateOnNoChallenge(proxy, null, proxyAuthExchange, context);
                    }
                }

                if (proxyAuthRequested || proxyMutualAuthRequired) {
                    final boolean updated = authenticator.handleResponse(
                            proxy, ChallengeType.PROXY, response, proxyAuthStrategy, proxyAuthExchange, context);

                    if (authCacheKeeper != null) {
                        authCacheKeeper.updateOnResponse(proxy, null, proxyAuthExchange, context);
                    }

                    return updated;
                }
            }
            return false;
        }

        @Override
        protected void validateSession(
                final IOSession ioSession,
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
        protected void closeSession(
                final IOSession ioSession,
                final CloseMode closeMode) {
            if (closeMode == CloseMode.GRACEFUL) {
                ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);
            } else {
                ioSession.close(closeMode);
            }
        }
    }

}
