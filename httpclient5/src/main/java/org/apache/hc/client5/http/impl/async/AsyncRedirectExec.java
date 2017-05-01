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
import java.net.URI;
import java.util.List;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.StandardMethods;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectException;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class AsyncRedirectExec implements AsyncExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

    private final HttpRoutePlanner routePlanner;
    private final RedirectStrategy redirectStrategy;

    AsyncRedirectExec(final HttpRoutePlanner routePlanner, final RedirectStrategy redirectStrategy) {
        this.routePlanner = routePlanner;
        this.redirectStrategy = redirectStrategy;
    }

    private static class State {

        volatile URI redirectURI;
        volatile int maxRedirects;
        volatile int redirectCount;
        volatile HttpRequest currentRequest;
        volatile AsyncEntityProducer currentEntityProducer;
        volatile AsyncExecChain.Scope currentScope;
        volatile boolean reroute;

    }

    private void internalExecute(
            final State state,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {

        final HttpRequest request = state.currentRequest;
        final AsyncEntityProducer entityProducer = state.currentEntityProducer;
        final AsyncExecChain.Scope scope = state.currentScope;
        final HttpClientContext clientContext = scope.clientContext;
        final HttpRoute currentRoute = scope.route;
        chain.proceed(request, entityProducer, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails) throws HttpException, IOException {

                state.redirectURI = null;
                final RequestConfig config = clientContext.getRequestConfig();
                if (config.isRedirectsEnabled() && redirectStrategy.isRedirected(request, response, clientContext)) {
                    if (state.redirectCount >= state.maxRedirects) {
                        throw new RedirectException("Maximum redirects (" + state.maxRedirects + ") exceeded");
                    }

                    state.redirectCount++;

                    final URI redirectUri = redirectStrategy.getLocationURI(request, response, clientContext);
                    final int statusCode = response.getCode();

                    switch (statusCode) {
                        case HttpStatus.SC_MOVED_PERMANENTLY:
                        case HttpStatus.SC_MOVED_TEMPORARILY:
                        case HttpStatus.SC_SEE_OTHER:
                            if (!StandardMethods.isSafe(request.getMethod())) {
                                final HttpRequest httpGet = new BasicHttpRequest(StandardMethods.GET.name(), redirectUri);
                                httpGet.setHeaders(scope.originalRequest.getAllHeaders());
                                state.currentRequest = httpGet;
                                state.currentEntityProducer = null;
                                break;
                            }
                        default:
                            state.currentRequest = new BasicHttpRequest(request.getMethod(), redirectUri);
                    }
                    final HttpHost newTarget = URIUtils.extractHost(redirectUri);
                    if (newTarget == null) {
                        throw new ProtocolException("Redirect URI does not specify a valid host name: " + redirectUri);
                    }

                    if (!currentRoute.getTargetHost().equals(newTarget)) {
                        log.debug("New route required");
                        state.reroute = true;
                        final AuthExchange targetAuthExchange = clientContext.getAuthExchange(currentRoute.getTargetHost());
                        log.debug("Resetting target auth state");
                        targetAuthExchange.reset();
                        if (currentRoute.getProxyHost() != null) {
                            final AuthExchange proxyAuthExchange = clientContext.getAuthExchange(currentRoute.getProxyHost());
                            final AuthScheme authScheme = proxyAuthExchange.getAuthScheme();
                            if (authScheme != null && authScheme.isConnectionBased()) {
                                log.debug("Resetting proxy auth state");
                                proxyAuthExchange.reset();
                            }
                        }
                        final HttpRoute newRoute = routePlanner.determineRoute(newTarget, clientContext);
                        state.currentScope = new AsyncExecChain.Scope(
                                scope.exchangeId, newRoute, scope.originalRequest, clientContext, scope.execRuntime);
                        state.redirectURI = redirectUri;
                    } else {
                        state.reroute = false;
                        state.redirectURI = redirectUri;
                    }
                }
                if (state.redirectURI != null) {
                    if (log.isDebugEnabled()) {
                        log.debug(scope.exchangeId + ": redirecting to '" + state.redirectURI + "' via " + currentRoute);
                    }
                    return null;
                } else {
                    return asyncExecCallback.handleResponse(response, entityDetails);
                }
            }

            @Override
            public void completed() {
                if (state.redirectURI == null) {
                    asyncExecCallback.completed();
                } else {
                    try {
                        if (state.reroute) {
                            scope.execRuntime.releaseConnection();
                        }
                        internalExecute(state, chain, asyncExecCallback);
                    } catch (IOException | HttpException ex) {
                        asyncExecCallback.failed(ex);
                    }
                }
            }

            @Override
            public void failed(final Exception cause) {
                asyncExecCallback.failed(cause);
            }

        });

    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        final HttpClientContext clientContext = scope.clientContext;
        final List<URI> redirectLocations = clientContext.getRedirectLocations();
        if (redirectLocations != null) {
            redirectLocations.clear();
        }
        final RequestConfig config = clientContext.getRequestConfig();

        final State state = new State();
        state.maxRedirects = config.getMaxRedirects() > 0 ? config.getMaxRedirects() : 50;
        state.redirectCount = 0;
        state.currentRequest = request;
        state.currentEntityProducer = entityProducer;
        state.currentScope = scope;

        internalExecute(state, chain, asyncExecCallback);
    }

}
