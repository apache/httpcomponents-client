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

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteTracker;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.TunnelRefusedException;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.impl.routing.BasicRouteDirector;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRouteDirector;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request execution handler in the asynchronous request execution chain
 * that is responsible for establishing connection to the target
 * origin server as specified by the current connection route.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class AsyncConnectExec implements AsyncExecChainHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final HttpProcessor proxyHttpProcessor;
    private final AuthenticationStrategy proxyAuthStrategy;
    private final HttpAuthenticator authenticator;
    private final HttpRouteDirector routeDirector;

    public AsyncConnectExec(
            final HttpProcessor proxyHttpProcessor,
            final AuthenticationStrategy proxyAuthStrategy) {
        Args.notNull(proxyHttpProcessor, "Proxy HTTP processor");
        Args.notNull(proxyAuthStrategy, "Proxy authentication strategy");
        this.proxyHttpProcessor = proxyHttpProcessor;
        this.proxyAuthStrategy  = proxyAuthStrategy;
        this.authenticator      = new HttpAuthenticator(log);
        this.routeDirector      = new BasicRouteDirector();
    }

    static class State {

        State(final HttpRoute route) {
            tracker = new RouteTracker(route);
        }

        final RouteTracker tracker;

        volatile boolean challenged;
        volatile boolean tunnelRefused;

    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");

        final String exchangeId = scope.exchangeId;
        final HttpRoute route = scope.route;
        final CancellableDependency cancellableDependency = scope.cancellableDependency;
        final HttpClientContext clientContext = scope.clientContext;
        final AsyncExecRuntime execRuntime = scope.execRuntime;
        final State state = new State(route);

        if (!execRuntime.isEndpointAcquired()) {
            final Object userToken = clientContext.getUserToken();
            if (log.isDebugEnabled()) {
                log.debug(exchangeId + ": acquiring connection with route " + route);
            }
            cancellableDependency.setDependency(execRuntime.acquireEndpoint(
                    exchangeId, route, userToken, clientContext, new FutureCallback<AsyncExecRuntime>() {

                        @Override
                        public void completed(final AsyncExecRuntime execRuntime) {
                            if (execRuntime.isEndpointConnected()) {
                                try {
                                    chain.proceed(request, entityProducer, scope, asyncExecCallback);
                                } catch (final HttpException | IOException ex) {
                                    asyncExecCallback.failed(ex);
                                }
                            } else {
                                proceedToNextHop(state, request, entityProducer, scope, chain, asyncExecCallback);
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            asyncExecCallback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            asyncExecCallback.failed(new InterruptedIOException());
                        }

                    }));
        } else {
            if (execRuntime.isEndpointConnected()) {
                try {
                    chain.proceed(request, entityProducer, scope, asyncExecCallback);
                } catch (final HttpException | IOException ex) {
                    asyncExecCallback.failed(ex);
                }
            } else {
                proceedToNextHop(state, request, entityProducer, scope, chain, asyncExecCallback);
            }
        }

    }

    private void proceedToNextHop(
            final State state,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        final RouteTracker tracker = state.tracker;
        final String exchangeId = scope.exchangeId;
        final HttpRoute route = scope.route;
        final AsyncExecRuntime execRuntime = scope.execRuntime;
        final CancellableDependency operation = scope.cancellableDependency;
        final HttpClientContext clientContext = scope.clientContext;

        int step;
        do {
            final HttpRoute fact = tracker.toRoute();
            step = routeDirector.nextStep(route, fact);
            switch (step) {
                case HttpRouteDirector.CONNECT_TARGET:
                    operation.setDependency(execRuntime.connectEndpoint(clientContext, new FutureCallback<AsyncExecRuntime>() {

                        @Override
                        public void completed(final AsyncExecRuntime execRuntime) {
                            tracker.connectTarget(route.isSecure());
                            if (log.isDebugEnabled()) {
                                log.debug(exchangeId + ": connected to target");
                            }
                            proceedToNextHop(state, request, entityProducer, scope, chain, asyncExecCallback);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            asyncExecCallback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            asyncExecCallback.failed(new InterruptedIOException());
                        }

                    }));
                    return;

                case HttpRouteDirector.CONNECT_PROXY:
                    operation.setDependency(execRuntime.connectEndpoint(clientContext, new FutureCallback<AsyncExecRuntime>() {

                        @Override
                        public void completed(final AsyncExecRuntime execRuntime) {
                            final HttpHost proxy  = route.getProxyHost();
                            tracker.connectProxy(proxy, route.isSecure() && !route.isTunnelled());
                            if (log.isDebugEnabled()) {
                                log.debug(exchangeId + ": connected to proxy");
                            }
                            proceedToNextHop(state, request, entityProducer, scope, chain, asyncExecCallback);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            asyncExecCallback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            asyncExecCallback.failed(new InterruptedIOException());
                        }

                    }));
                    return;

                case HttpRouteDirector.TUNNEL_TARGET:
                    try {
                        final HttpHost proxy = route.getProxyHost();
                        final HttpHost target = route.getTargetHost();
                        createTunnel(state, proxy ,target, scope, chain, new AsyncExecCallback() {

                            @Override
                            public AsyncDataConsumer handleResponse(
                                    final HttpResponse response,
                                    final EntityDetails entityDetails) throws HttpException, IOException {
                                return asyncExecCallback.handleResponse(response, entityDetails);
                            }

                            @Override
                            public void handleInformationResponse(
                                    final HttpResponse response) throws HttpException, IOException {
                                asyncExecCallback.handleInformationResponse(response);
                            }

                            @Override
                            public void completed() {
                                if (log.isDebugEnabled()) {
                                    log.debug(exchangeId + ": tunnel to target created");
                                }
                                tracker.tunnelTarget(false);
                                proceedToNextHop(state, request, entityProducer, scope, chain, asyncExecCallback);
                            }

                            @Override
                            public void failed(final Exception cause) {
                                asyncExecCallback.failed(cause);
                            }

                        });
                    } catch (final HttpException | IOException ex) {
                        asyncExecCallback.failed(ex);
                    }
                    return;

                case HttpRouteDirector.TUNNEL_PROXY:
                    // The most simple example for this case is a proxy chain
                    // of two proxies, where P1 must be tunnelled to P2.
                    // route: Source -> P1 -> P2 -> Target (3 hops)
                    // fact:  Source -> P1 -> Target       (2 hops)
                    asyncExecCallback.failed(new HttpException("Proxy chains are not supported"));
                    return;

                case HttpRouteDirector.LAYER_PROTOCOL:
                    execRuntime.upgradeTls(clientContext);
                    if (log.isDebugEnabled()) {
                        log.debug(exchangeId + ": upgraded to TLS");
                    }
                    tracker.layerProtocol(route.isSecure());
                    break;

                case HttpRouteDirector.UNREACHABLE:
                    asyncExecCallback.failed(new HttpException("Unable to establish route: " +
                            "planned = " + route + "; current = " + fact));
                    return;

                case HttpRouteDirector.COMPLETE:
                    if (log.isDebugEnabled()) {
                        log.debug(exchangeId + ": route fully established");
                    }
                    try {
                        chain.proceed(request, entityProducer, scope, asyncExecCallback);
                    } catch (final HttpException | IOException ex) {
                        asyncExecCallback.failed(ex);
                    }
                    break;

                default:
                    throw new IllegalStateException("Unknown step indicator "  + step + " from RouteDirector.");
            }
        } while (step > HttpRouteDirector.COMPLETE);
    }

    private void createTunnel(
            final State state,
            final HttpHost proxy,
            final HttpHost nextHop,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {

        final AsyncExecRuntime execRuntime = scope.execRuntime;
        final HttpClientContext clientContext = scope.clientContext;

        final AuthExchange proxyAuthExchange = proxy != null ? clientContext.getAuthExchange(proxy) : new AuthExchange();

        final HttpRequest connect = new BasicHttpRequest("CONNECT", nextHop, nextHop.toHostString());
        connect.setVersion(HttpVersion.HTTP_1_1);

        proxyHttpProcessor.process(connect, null, clientContext);
        authenticator.addAuthResponse(proxy, ChallengeType.PROXY, connect, proxyAuthExchange, clientContext);

        chain.proceed(connect, null, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails) throws HttpException, IOException {

                clientContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                proxyHttpProcessor.process(response, entityDetails, clientContext);

                final int status = response.getCode();
                if (status < HttpStatus.SC_SUCCESS) {
                    throw new HttpException("Unexpected response to CONNECT request: " + new StatusLine(response));
                }

                if (needAuthentication(proxyAuthExchange, proxy, response, clientContext)) {
                    state.challenged = true;
                    return null;
                }
                state.challenged = false;
                if (status >= HttpStatus.SC_REDIRECTION) {
                    state.tunnelRefused = true;
                    return asyncExecCallback.handleResponse(response, entityDetails);
                }
                return null;
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
            }

            @Override
            public void completed() {
                if (!execRuntime.isEndpointConnected()) {
                    state.tracker.reset();
                }
                if (state.challenged) {
                    try {
                        createTunnel(state, proxy, nextHop, scope, chain, asyncExecCallback);
                    } catch (final HttpException | IOException ex) {
                        asyncExecCallback.failed(ex);
                    }
                } else {
                    if (state.tunnelRefused) {
                        asyncExecCallback.failed(new TunnelRefusedException("Tunnel refused", null));
                    } else {
                        asyncExecCallback.completed();
                    }
                }
            }

            @Override
            public void failed(final Exception cause) {
                asyncExecCallback.failed(cause);
            }

        });

    }

    private boolean needAuthentication(
            final AuthExchange proxyAuthExchange,
            final HttpHost proxy,
            final HttpResponse response,
            final HttpClientContext context) {
        final RequestConfig config = context.getRequestConfig();
        if (config.isAuthenticationEnabled()) {
            final boolean proxyAuthRequested = authenticator.isChallenged(proxy, ChallengeType.PROXY, response, proxyAuthExchange, context);
            if (proxyAuthRequested) {
                return authenticator.updateAuthState(proxy, ChallengeType.PROXY, response,
                        proxyAuthStrategy, proxyAuthExchange, context);
            }
        }
        return false;
    }

}
