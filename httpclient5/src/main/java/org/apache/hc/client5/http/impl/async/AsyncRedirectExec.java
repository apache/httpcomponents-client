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

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.util.LangUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request execution handler in the asynchronous request execution chain
 * responsible for handling of request redirects.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class AsyncRedirectExec implements AsyncExecChainHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncRedirectExec.class);

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
        volatile RedirectLocations redirectLocations;
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
        final String exchangeId = scope.exchangeId;
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
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} redirect requested to location '{}'", exchangeId, redirectUri);
                    }
                    if (!config.isCircularRedirectsAllowed()) {
                        if (state.redirectLocations.contains(redirectUri)) {
                            throw new CircularRedirectException("Circular redirect to '" + redirectUri + "'");
                        }
                    }
                    state.redirectLocations.add(redirectUri);

                    final HttpHost newTarget = URIUtils.extractHost(redirectUri);
                    if (newTarget == null) {
                        throw new ProtocolException("Redirect URI does not specify a valid host name: " + redirectUri);
                    }

                    final int statusCode = response.getCode();
                    final BasicRequestBuilder redirectBuilder;
                    switch (statusCode) {
                        case HttpStatus.SC_MOVED_PERMANENTLY:
                        case HttpStatus.SC_MOVED_TEMPORARILY:
                            if (Method.POST.isSame(request.getMethod())) {
                                redirectBuilder = BasicRequestBuilder.get();
                                state.currentEntityProducer = null;
                            } else {
                                redirectBuilder = BasicRequestBuilder.copy(scope.originalRequest);
                            }
                            break;
                        case HttpStatus.SC_SEE_OTHER:
                            if (!Method.GET.isSame(request.getMethod()) && !Method.HEAD.isSame(request.getMethod())) {
                                redirectBuilder = BasicRequestBuilder.get();
                                state.currentEntityProducer = null;
                            } else {
                                redirectBuilder = BasicRequestBuilder.copy(scope.originalRequest);
                            }
                            break;
                        default:
                            redirectBuilder = BasicRequestBuilder.copy(scope.originalRequest);
                    }
                    redirectBuilder.setUri(redirectUri);
                    state.reroute = false;
                    state.redirectURI = redirectUri;
                    state.currentRequest = redirectBuilder.build();

                    if (!LangUtils.equals(currentRoute.getTargetHost(), newTarget)) {
                        final HttpRoute newRoute = routePlanner.determineRoute(newTarget, clientContext);
                        if (!LangUtils.equals(currentRoute, newRoute)) {
                            state.reroute = true;
                            final AuthExchange targetAuthExchange = clientContext.getAuthExchange(currentRoute.getTargetHost());
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} resetting target auth state", exchangeId);
                            }
                            targetAuthExchange.reset();
                            if (currentRoute.getProxyHost() != null) {
                                final AuthExchange proxyAuthExchange = clientContext.getAuthExchange(currentRoute.getProxyHost());
                                if (proxyAuthExchange.isConnectionBased()) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("{} resetting proxy auth state", exchangeId);
                                    }
                                    proxyAuthExchange.reset();
                                }
                            }
                            state.currentScope = new AsyncExecChain.Scope(
                                    scope.exchangeId,
                                    newRoute,
                                    scope.originalRequest,
                                    scope.cancellableDependency,
                                    scope.clientContext,
                                    scope.execRuntime,
                                    scope.scheduler,
                                    scope.execCount);
                        }
                    }
                }
                if (state.redirectURI != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} redirecting to '{}' via {}", exchangeId, state.redirectURI, currentRoute);
                    }
                    return null;
                }
                return asyncExecCallback.handleResponse(response, entityDetails);
            }

            @Override
            public void handleInformationResponse(
                    final HttpResponse response) throws HttpException, IOException {
                asyncExecCallback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                if (state.redirectURI == null) {
                    asyncExecCallback.completed();
                } else {
                    final AsyncEntityProducer entityProducer = state.currentEntityProducer;
                    if (entityProducer != null) {
                        entityProducer.releaseResources();
                    }
                    if (entityProducer != null && !entityProducer.isRepeatable()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} cannot redirect non-repeatable request", exchangeId);
                        }
                        asyncExecCallback.completed();
                    } else {
                        try {
                            if (state.reroute) {
                                scope.execRuntime.releaseEndpoint();
                            }
                            internalExecute(state, chain, asyncExecCallback);
                        } catch (final IOException | HttpException ex) {
                            asyncExecCallback.failed(ex);
                        }
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
        RedirectLocations redirectLocations = clientContext.getRedirectLocations();
        if (redirectLocations == null) {
            redirectLocations = new RedirectLocations();
            clientContext.setAttribute(HttpClientContext.REDIRECT_LOCATIONS, redirectLocations);
        }
        redirectLocations.clear();

        final RequestConfig config = clientContext.getRequestConfig();

        final State state = new State();
        state.maxRedirects = config.getMaxRedirects() > 0 ? config.getMaxRedirects() : 50;
        state.redirectCount = 0;
        state.currentRequest = request;
        state.currentEntityProducer = entityProducer;
        state.redirectLocations = redirectLocations;
        state.currentScope = scope;

        internalExecute(state, chain, asyncExecCallback);
    }

}
