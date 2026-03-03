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

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.AuthCacheKeeper;
import org.apache.hc.client5.http.impl.auth.AuthenticationHandler;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Completes an HTTP/2 route by establishing a CONNECT tunnel through the proxy
 * and optionally upgrading to TLS. Handles proxy authentication with bounded retry.
 *
 * @since 5.7
 */
@Internal
final class H2RouteOperator {

    private static final Logger LOG = LoggerFactory.getLogger(H2RouteOperator.class);
    private static final int MAX_TUNNEL_AUTH_ATTEMPTS = 3;

    private final TlsStrategy tlsStrategy;
    private final IOEventHandlerFactory tunnelProtocolStarter;
    private final HttpProcessor proxyHttpProcessor;
    private final AuthenticationStrategy proxyAuthStrategy;
    private final AuthenticationHandler authenticator;
    private final AuthCacheKeeper authCacheKeeper;
    private final Lookup<AuthSchemeFactory> authSchemeRegistry;
    private final CredentialsProvider credentialsProvider;
    private final RequestConfig defaultRequestConfig;

    H2RouteOperator(
            final TlsStrategy tlsStrategy,
            final IOEventHandlerFactory tunnelProtocolStarter) {
        this(tlsStrategy, tunnelProtocolStarter, null, null, null, true, null, null, null);
    }

    H2RouteOperator(
            final TlsStrategy tlsStrategy,
            final IOEventHandlerFactory tunnelProtocolStarter,
            final HttpProcessor proxyHttpProcessor,
            final AuthenticationStrategy proxyAuthStrategy,
            final SchemePortResolver schemePortResolver,
            final boolean authCachingDisabled,
            final Lookup<AuthSchemeFactory> authSchemeRegistry,
            final CredentialsProvider credentialsProvider,
            final RequestConfig defaultRequestConfig) {
        this.tlsStrategy = tlsStrategy;
        this.tunnelProtocolStarter = tunnelProtocolStarter;
        this.proxyHttpProcessor = proxyHttpProcessor;
        this.proxyAuthStrategy = proxyAuthStrategy;
        this.authenticator = proxyHttpProcessor != null && proxyAuthStrategy != null
                ? new AuthenticationHandler() : null;
        this.authCacheKeeper = proxyHttpProcessor != null && proxyAuthStrategy != null
                && !authCachingDisabled && schemePortResolver != null
                ? new AuthCacheKeeper(schemePortResolver)
                : null;
        this.authSchemeRegistry = authSchemeRegistry;
        this.credentialsProvider = credentialsProvider;
        this.defaultRequestConfig = defaultRequestConfig != null ? defaultRequestConfig : RequestConfig.DEFAULT;
    }

    void completeRoute(
            final HttpRoute route,
            final Timeout connectTimeout,
            final IOSession ioSession,
            final FutureCallback<IOSession> callback) {
        if (!route.isTunnelled()) {
            callback.completed(ioSession);
            return;
        }
        if (route.getHopCount() > 2) {
            callback.failed(new HttpException("Proxy chains are not supported for HTTP/2 CONNECT tunneling"));
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
        final NamedEndpoint targetEndpoint = route.getTargetName() != null
                ? route.getTargetName() : route.getTargetHost();
        final HttpHost proxy = route.getProxyHost();
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} establishing H2 tunnel to {} via {}", ioSession.getId(), targetEndpoint, proxy);
        }
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
                route, ioSession, targetEndpoint, proxy, connectTimeout,
                callback, tunnelContext, proxyAuthExchange, 1);
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
                            final boolean retry = needAuthentication(
                                    proxyAuthExchange, proxy, response, tunnelContext);
                            if (retry) {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("{} tunnel auth challenge from {}; attempt {}/{}",
                                            ioSession.getId(), proxy, attemptCount, MAX_TUNNEL_AUTH_ATTEMPTS);
                                }
                                establishTunnelWithAuthAttempt(
                                        route, ioSession, targetEndpoint, proxy, connectTimeout,
                                        callback, tunnelContext, proxyAuthExchange, attemptCount + 1);
                            } else {
                                callback.failed(ex);
                            }
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
        return authenticator.needProxyAuthentication(
                proxyAuthExchange, proxy, response, proxyAuthStrategy, authCacheKeeper, context);
    }

}
