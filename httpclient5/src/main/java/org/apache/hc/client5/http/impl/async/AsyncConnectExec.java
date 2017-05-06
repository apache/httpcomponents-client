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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteTracker;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.impl.routing.BasicRouteDirector;
import org.apache.hc.client5.http.impl.sync.TunnelRefusedException;
import org.apache.hc.client5.http.protocol.AuthenticationStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRouteDirector;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Request executor in the HTTP request execution chain
 * that is responsible for establishing connection to the target
 * origin server as specified by the current route.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public final class AsyncConnectExec implements AsyncExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

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
        this.authenticator      = new HttpAuthenticator();
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
        final HttpClientContext clientContext = scope.clientContext;
        final AsyncExecRuntime execRuntime = scope.execRuntime;
        final State state = new State(route);

        final Runnable routeInitiation = new Runnable() {

            @Override
            public void run() {
                if (log.isDebugEnabled()) {
                    log.debug(exchangeId + ": connection acquired");
                }
                if (execRuntime.isConnected()) {
                    try {
                        chain.proceed(request, entityProducer, scope, asyncExecCallback);
                    } catch (final HttpException | IOException ex) {
                        asyncExecCallback.failed(ex);
                    }
                } else {
                    proceedToNextHop(state, request, entityProducer, scope, chain, asyncExecCallback);
                }
            }

        };

        if (!execRuntime.isConnectionAcquired()) {
            final Object userToken = clientContext.getUserToken();
            if (log.isDebugEnabled()) {
                log.debug(exchangeId + ": acquiring connection with route " + route);
            }
            execRuntime.acquireConnection(route, userToken, clientContext, new FutureCallback<AsyncExecRuntime>() {

                @Override
                public void completed(final AsyncExecRuntime execRuntime) {
                    routeInitiation.run();
                }

                @Override
                public void failed(final Exception ex) {
                    asyncExecCallback.failed(ex);
                }

                @Override
                public void cancelled() {
                    asyncExecCallback.failed(new InterruptedIOException());
                }

            });
        } else {
            routeInitiation.run();
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
        final AsyncExecRuntime execRuntime = scope.execRuntime;
        final HttpRoute route = scope.route;
        final HttpClientContext clientContext = scope.clientContext;

        int step;
        do {
            final HttpRoute fact = tracker.toRoute();
            step = routeDirector.nextStep(route, fact);
            switch (step) {
                case HttpRouteDirector.CONNECT_TARGET:
                    execRuntime.connect(clientContext, new FutureCallback<AsyncExecRuntime>() {

                        @Override
                        public void completed(final AsyncExecRuntime execRuntime) {
                            tracker.connectTarget(route.isSecure());
                            log.debug("Connected to target");
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

                    });
                    return;

                case HttpRouteDirector.CONNECT_PROXY:
                    execRuntime.connect(clientContext, new FutureCallback<AsyncExecRuntime>() {

                        @Override
                        public void completed(final AsyncExecRuntime execRuntime) {
                            final HttpHost proxy  = route.getProxyHost();
                            tracker.connectProxy(proxy, false);
                            log.debug("Connected to proxy");
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

                    });
                    return;

                case HttpRouteDirector.TUNNEL_TARGET:
                    try {
                        final HttpHost proxy = route.getProxyHost();
                        final HttpHost target = route.getTargetHost();
                        createTunnel(state, proxy ,target, scope, chain, new AsyncExecCallback() {

                            @Override
                            public AsyncDataConsumer handleResponse(
                                    final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
                                return asyncExecCallback.handleResponse(response, entityDetails);
                            }

                            @Override
                            public void completed() {
                                log.debug("Tunnel to target created");
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
                    log.debug("Upgraded to TLS");
                    tracker.layerProtocol(route.isSecure());
                    break;

                case HttpRouteDirector.UNREACHABLE:
                    asyncExecCallback.failed(new HttpException("Unable to establish route: " +
                            "planned = " + route + "; current = " + fact));
                    return;

                case HttpRouteDirector.COMPLETE:
                    log.debug("Route fully established");
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
                    final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {

                clientContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                proxyHttpProcessor.process(response, entityDetails, clientContext);

                final int status = response.getCode();
                if (status < HttpStatus.SC_SUCCESS) {
                    throw new HttpException("Unexpected response to CONNECT request: " + new StatusLine(response));
                }

                if (needAuthentication(proxyAuthExchange, proxy, response, clientContext)) {
                    state.challenged = true;
                    return null;
                } else {
                    state.challenged = false;
                    if (status >= HttpStatus.SC_REDIRECTION) {
                        state.tunnelRefused = true;
                        return asyncExecCallback.handleResponse(response, entityDetails);
                    } else {
                        return null;
                    }
                }
            }

            @Override
            public void completed() {
                if (!execRuntime.isConnected()) {
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
                return authenticator.prepareAuthResponse(proxy, ChallengeType.PROXY, response,
                        proxyAuthStrategy, proxyAuthExchange, context);
            }
        }
        return false;
    }

}
