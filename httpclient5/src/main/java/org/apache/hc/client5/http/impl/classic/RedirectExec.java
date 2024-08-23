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

package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request execution handler in the classic request execution chain
 * responsible for handling of request redirects.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class RedirectExec implements ExecChainHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectExec.class);

    private final RedirectStrategy redirectStrategy;
    private final HttpRoutePlanner routePlanner;

    public RedirectExec(
            final HttpRoutePlanner routePlanner,
            final RedirectStrategy redirectStrategy) {
        super();
        Args.notNull(routePlanner, "HTTP route planner");
        Args.notNull(redirectStrategy, "HTTP redirect strategy");
        this.routePlanner = routePlanner;
        this.redirectStrategy = redirectStrategy;
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");

        final HttpClientContext context = scope.clientContext;
        context.setRedirectLocations(null);

        final RequestConfig config = context.getRequestConfigOrDefault();
        final int maxRedirects = config.getMaxRedirects() > 0 ? config.getMaxRedirects() : 50;
        ClassicHttpRequest currentRequest = request;
        ExecChain.Scope currentScope = scope;
        for (int redirectCount = 0;;) {
            final String exchangeId = currentScope.exchangeId;
            final ClassicHttpResponse response = chain.proceed(currentRequest, currentScope);
            try {
                if (config.isRedirectsEnabled() && this.redirectStrategy.isRedirected(request, response, context)) {
                    final HttpEntity requestEntity = request.getEntity();
                    if (requestEntity != null && !requestEntity.isRepeatable()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} cannot redirect non-repeatable request", exchangeId);
                        }
                        return response;
                    }
                    if (redirectCount >= maxRedirects) {
                        throw new RedirectException("Maximum redirects ("+ maxRedirects + ") exceeded");
                    }
                    redirectCount++;

                    final URI redirectUri = this.redirectStrategy.getLocationURI(currentRequest, response, context);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} redirect requested to location '{}'", exchangeId, redirectUri);
                    }

                    final HttpHost newTarget = URIUtils.extractHost(redirectUri);
                    if (newTarget == null) {
                        throw new ProtocolException("Redirect URI does not specify a valid host name: " +
                                redirectUri);
                    }

                    final RedirectLocations redirectLocations = context.getRedirectLocations();
                    if (!config.isCircularRedirectsAllowed()) {
                        if (redirectLocations.contains(redirectUri)) {
                            throw new CircularRedirectException("Circular redirect to '" + redirectUri + "'");
                        }
                    }
                    redirectLocations.add(redirectUri);

                    final int statusCode = response.getCode();
                    final ClassicRequestBuilder redirectBuilder;
                    switch (statusCode) {
                        case HttpStatus.SC_MOVED_PERMANENTLY:
                        case HttpStatus.SC_MOVED_TEMPORARILY:
                            if (Method.POST.isSame(request.getMethod())) {
                                redirectBuilder = ClassicRequestBuilder.get();
                            } else {
                                redirectBuilder = ClassicRequestBuilder.copy(currentScope.originalRequest);
                            }
                            break;
                        case HttpStatus.SC_SEE_OTHER:
                            if (!Method.GET.isSame(request.getMethod()) && !Method.HEAD.isSame(request.getMethod())) {
                                redirectBuilder = ClassicRequestBuilder.get();
                            } else {
                                redirectBuilder = ClassicRequestBuilder.copy(currentScope.originalRequest);
                            }
                            break;
                        default:
                            redirectBuilder = ClassicRequestBuilder.copy(currentScope.originalRequest);
                    }
                    redirectBuilder.setUri(redirectUri);

                    HttpRoute currentRoute = currentScope.route;
                    if (!Objects.equals(currentRoute.getTargetHost(), newTarget)) {
                        final HttpRoute newRoute = this.routePlanner.determineRoute(newTarget, context);
                        if (!Objects.equals(currentRoute, newRoute)) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} new route required", exchangeId);
                            }
                            final AuthExchange targetAuthExchange = context.getAuthExchange(currentRoute.getTargetHost());
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} resetting target auth state", exchangeId);
                            }
                            targetAuthExchange.reset();
                            if (currentRoute.getProxyHost() != null) {
                                final AuthExchange proxyAuthExchange = context.getAuthExchange(currentRoute.getProxyHost());
                                if (proxyAuthExchange.isConnectionBased()) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("{} resetting proxy auth state", exchangeId);
                                    }
                                    proxyAuthExchange.reset();
                                }
                            }
                            currentRoute = newRoute;
                        }
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} redirecting to '{}' via {}", exchangeId, redirectUri, currentRoute);
                    }
                    currentScope = new ExecChain.Scope(
                            scope.exchangeId,
                            currentRoute,
                            redirectBuilder.build(),
                            scope.execRuntime,
                            scope.clientContext);
                    currentRequest = redirectBuilder.build();
                    RequestEntityProxy.enhance(currentRequest);

                    EntityUtils.consume(response.getEntity());
                    response.close();
                } else {
                    return response;
                }
            } catch (final RuntimeException | IOException ex) {
                response.close();
                throw ex;
            } catch (final HttpException ex) {
                // Protocol exception related to a direct.
                // The underlying connection may still be salvaged.
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (final IOException ioex) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} I/O error while releasing connection", exchangeId, ioex);
                    }
                } finally {
                    response.close();
                }
                throw ex;
            }
        }
    }

}
