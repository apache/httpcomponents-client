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
import java.util.Iterator;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.RequestSupport;
import org.apache.hc.client5.http.impl.auth.AuthCacheKeeper;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request execution handler in the classic request execution chain
 * that is responsible for implementation of HTTP specification requirements.
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
public final class ProtocolExec implements ExecChainHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolExec.class);

    private final AuthenticationStrategy targetAuthStrategy;
    private final AuthenticationStrategy proxyAuthStrategy;
    private final HttpAuthenticator authenticator;
    private final SchemePortResolver schemePortResolver;
    private final AuthCacheKeeper authCacheKeeper;

    public ProtocolExec(
            final AuthenticationStrategy targetAuthStrategy,
            final AuthenticationStrategy proxyAuthStrategy,
            final SchemePortResolver schemePortResolver,
            final boolean authCachingDisabled) {
        this.targetAuthStrategy = Args.notNull(targetAuthStrategy, "Target authentication strategy");
        this.proxyAuthStrategy = Args.notNull(proxyAuthStrategy, "Proxy authentication strategy");
        this.authenticator = new HttpAuthenticator();
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
        this.authCacheKeeper = authCachingDisabled ? null : new AuthCacheKeeper(this.schemePortResolver);
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest userRequest,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(userRequest, "HTTP request");
        Args.notNull(scope, "Scope");

        if (Method.CONNECT.isSame(userRequest.getMethod())) {
            throw new ProtocolException("Direct execution of CONNECT is not allowed");
        }

        final String exchangeId = scope.exchangeId;
        final HttpRoute route = scope.route;
        final HttpClientContext context = scope.clientContext;
        final ExecRuntime execRuntime = scope.execRuntime;

        final HttpHost routeTarget = route.getTargetHost();
        final HttpHost proxy = route.getProxyHost();

        try {
            final ClassicHttpRequest request;
            if (proxy != null && !route.isTunnelled()) {
                final ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.copy(userRequest);
                if (requestBuilder.getAuthority() == null) {
                    requestBuilder.setAuthority(new URIAuthority(routeTarget));
                }
                requestBuilder.setAbsoluteRequestUri(true);
                request = requestBuilder.build();
            } else {
                request = userRequest;
            }

            // Ensure the request has a scheme and an authority
            if (request.getScheme() == null) {
                request.setScheme(routeTarget.getSchemeName());
            }
            if (request.getAuthority() == null) {
                request.setAuthority(new URIAuthority(routeTarget));
            }

            final URIAuthority authority = request.getAuthority();
            if (authority.getUserInfo() != null) {
                throw new ProtocolException("Request URI authority contains deprecated userinfo component");
            }

            final HttpHost target = new HttpHost(
                    request.getScheme(),
                    authority.getHostName(),
                    schemePortResolver.resolve(request.getScheme(), authority));
            final String pathPrefix = RequestSupport.extractPathPrefix(request);

            final AuthExchange targetAuthExchange = context.getAuthExchange(target);
            final AuthExchange proxyAuthExchange = proxy != null ? context.getAuthExchange(proxy) : new AuthExchange();

            if (!targetAuthExchange.isConnectionBased() &&
                    targetAuthExchange.getPathPrefix() != null &&
                    !pathPrefix.startsWith(targetAuthExchange.getPathPrefix())) {
                // force re-authentication if the current path prefix does not match
                // that of the previous authentication exchange.
                targetAuthExchange.reset();
            }
            if (targetAuthExchange.getPathPrefix() == null) {
                targetAuthExchange.setPathPrefix(pathPrefix);
            }

            if (authCacheKeeper != null) {
                authCacheKeeper.loadPreemptively(target, pathPrefix, targetAuthExchange, context);
                if (proxy != null) {
                    authCacheKeeper.loadPreemptively(proxy, null, proxyAuthExchange, context);
                }
            }

            RequestEntityProxy.enhance(request);

            for (;;) {

                if (!request.containsHeader(HttpHeaders.AUTHORIZATION)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} target auth state: {}", exchangeId, targetAuthExchange.getState());
                    }
                    authenticator.addAuthResponse(target, ChallengeType.TARGET, request, targetAuthExchange, context);
                }
                if (!request.containsHeader(HttpHeaders.PROXY_AUTHORIZATION) && !route.isTunnelled()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} proxy auth state: {}", exchangeId, proxyAuthExchange.getState());
                    }
                    authenticator.addAuthResponse(proxy, ChallengeType.PROXY, request, proxyAuthExchange, context);
                }

                final ClassicHttpResponse response = chain.proceed(request, scope);

                if (Method.TRACE.isSame(request.getMethod())) {
                    // Do not perform authentication for TRACE request
                    ResponseEntityProxy.enhance(response, execRuntime);
                    return response;
                }
                final HttpEntity requestEntity = request.getEntity();
                if (requestEntity != null && !requestEntity.isRepeatable()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} Cannot retry non-repeatable request", exchangeId);
                    }
                    ResponseEntityProxy.enhance(response, execRuntime);
                    return response;
                }
                if (needAuthentication(
                        targetAuthExchange,
                        proxyAuthExchange,
                        proxy != null ? proxy : target,
                        target,
                        pathPrefix,
                        response,
                        context)) {
                    // Make sure the response body is fully consumed, if present
                    final HttpEntity responseEntity = response.getEntity();
                    if (execRuntime.isConnectionReusable()) {
                        EntityUtils.consume(responseEntity);
                    } else {
                        execRuntime.disconnectEndpoint();
                        if (proxyAuthExchange.getState() == AuthExchange.State.SUCCESS
                                && proxyAuthExchange.isConnectionBased()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} resetting proxy auth state", exchangeId);
                            }
                            proxyAuthExchange.reset();
                        }
                        if (targetAuthExchange.getState() == AuthExchange.State.SUCCESS
                                && targetAuthExchange.isConnectionBased()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} resetting target auth state", exchangeId);
                            }
                            targetAuthExchange.reset();
                        }
                    }
                    // Reset request headers
                    final ClassicHttpRequest original = scope.originalRequest;
                    request.setHeaders();
                    for (final Iterator<Header> it = original.headerIterator(); it.hasNext(); ) {
                        request.addHeader(it.next());
                    }
                } else {
                    ResponseEntityProxy.enhance(response, execRuntime);
                    return response;
                }
            }
        } catch (final HttpException ex) {
            execRuntime.discardEndpoint();
            throw ex;
        } catch (final RuntimeException | IOException ex) {
            execRuntime.discardEndpoint();
            for (final AuthExchange authExchange : context.getAuthExchanges().values()) {
                if (authExchange.isConnectionBased()) {
                    authExchange.reset();
                }
            }
            throw ex;
        }
    }

    private boolean needAuthentication(
            final AuthExchange targetAuthExchange,
            final AuthExchange proxyAuthExchange,
            final HttpHost proxy,
            final HttpHost target,
            final String pathPrefix,
            final HttpResponse response,
            final HttpClientContext context) {
        final RequestConfig config = context.getRequestConfig();
        if (config.isAuthenticationEnabled()) {
            final boolean targetAuthRequested = authenticator.isChallenged(
                    target, ChallengeType.TARGET, response, targetAuthExchange, context);

            if (authCacheKeeper != null) {
                if (targetAuthRequested) {
                    authCacheKeeper.updateOnChallenge(target, pathPrefix, targetAuthExchange, context);
                } else {
                    authCacheKeeper.updateOnNoChallenge(target, pathPrefix, targetAuthExchange, context);
                }
            }

            final boolean proxyAuthRequested = authenticator.isChallenged(
                    proxy, ChallengeType.PROXY, response, proxyAuthExchange, context);

            if (authCacheKeeper != null) {
                if (proxyAuthRequested) {
                    authCacheKeeper.updateOnChallenge(proxy, null, proxyAuthExchange, context);
                } else {
                    authCacheKeeper.updateOnNoChallenge(proxy, null, proxyAuthExchange, context);
                }
            }

            if (targetAuthRequested) {
                final boolean updated = authenticator.updateAuthState(target, ChallengeType.TARGET, response,
                        targetAuthStrategy, targetAuthExchange, context);

                if (authCacheKeeper != null) {
                    authCacheKeeper.updateOnResponse(target, pathPrefix, targetAuthExchange, context);
                }

                return updated;
            }
            if (proxyAuthRequested) {
                final boolean updated = authenticator.updateAuthState(proxy, ChallengeType.PROXY, response,
                        proxyAuthStrategy, proxyAuthExchange, context);

                if (authCacheKeeper != null) {
                    authCacheKeeper.updateOnResponse(proxy, null, proxyAuthExchange, context);
                }

                return updated;
            }
        }
        return false;
    }

}
