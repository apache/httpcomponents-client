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

package org.apache.hc.client5.http.impl.sync;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.StandardMethods;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectException;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecChainHandler;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.RequestBuilder;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Request executor in the request execution chain that is responsible
 * for handling of request redirects.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
final class RedirectExec implements ExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

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

        final List<URI> redirectLocations = context.getRedirectLocations();
        if (redirectLocations != null) {
            redirectLocations.clear();
        }

        final RequestConfig config = context.getRequestConfig();
        final int maxRedirects = config.getMaxRedirects() > 0 ? config.getMaxRedirects() : 50;
        ClassicHttpRequest currentRequest = request;
        ExecChain.Scope currentScope = scope;
        for (int redirectCount = 0;;) {
            final ClassicHttpResponse response = chain.proceed(currentRequest, currentScope);
            try {
                if (config.isRedirectsEnabled() && this.redirectStrategy.isRedirected(request, response, context)) {

                    if (redirectCount >= maxRedirects) {
                        throw new RedirectException("Maximum redirects ("+ maxRedirects + ") exceeded");
                    }
                    redirectCount++;

                    final URI redirectUri = this.redirectStrategy.getLocationURI(currentRequest, response, context);
                    final ClassicHttpRequest originalRequest = scope.originalRequest;
                    ClassicHttpRequest redirect = null;
                    final int statusCode = response.getCode();
                    switch (statusCode) {
                        case HttpStatus.SC_MOVED_PERMANENTLY:
                        case HttpStatus.SC_MOVED_TEMPORARILY:
                        case HttpStatus.SC_SEE_OTHER:
                            if (!StandardMethods.isSafe(request.getMethod())) {
                                final HttpGet httpGet = new HttpGet(redirectUri);
                                httpGet.setHeaders(originalRequest.getAllHeaders());
                                redirect = httpGet;
                            } else {
                                redirect = null;
                            }
                    }
                    if (redirect == null) {
                        redirect = RequestBuilder.copy(originalRequest).setUri(redirectUri).build();
                    }

                    final HttpHost newTarget = URIUtils.extractHost(redirectUri);
                    if (newTarget == null) {
                        throw new ProtocolException("Redirect URI does not specify a valid host name: " +
                                redirectUri);
                    }

                    HttpRoute currentRoute = currentScope.route;
                    final boolean crossSiteRedirect = !currentRoute.getTargetHost().equals(newTarget);
                    if (crossSiteRedirect) {

                        final AuthExchange targetAuthExchange = context.getAuthExchange(currentRoute.getTargetHost());
                        this.log.debug("Resetting target auth state");
                        targetAuthExchange.reset();
                        if (currentRoute.getProxyHost() != null) {
                            final AuthExchange proxyAuthExchange = context.getAuthExchange(currentRoute.getProxyHost());
                            final AuthScheme authScheme = proxyAuthExchange.getAuthScheme();
                            if (authScheme != null && authScheme.isConnectionBased()) {
                                this.log.debug("Resetting proxy auth state");
                                proxyAuthExchange.reset();
                            }
                        }
                        currentRoute = this.routePlanner.determineRoute(newTarget, context);
                        currentScope = new ExecChain.Scope(
                                currentRoute,
                                currentScope.originalRequest,
                                currentScope.execRuntime,
                                currentScope.clientContext);
                    }

                    if (this.log.isDebugEnabled()) {
                        this.log.debug("Redirecting to '" + redirectUri + "' via " + currentRoute);
                    }
                    currentRequest = redirect;
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
                    this.log.debug("I/O error while releasing connection", ioex);
                } finally {
                    response.close();
                }
                throw ex;
            }
        }
    }

}
