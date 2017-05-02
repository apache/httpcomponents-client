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
import java.net.URISyntaxException;
import java.util.Iterator;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.StandardMethods;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.impl.AuthSupport;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.protocol.AuthenticationStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.NonRepeatableRequestException;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecChainHandler;
import org.apache.hc.client5.http.sync.ExecRuntime;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Request executor in the request execution chain that is responsible
 * for implementation of HTTP specification requirements.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
final class ProtocolExec implements ExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

    private final HttpProcessor httpProcessor;
    private final AuthenticationStrategy targetAuthStrategy;
    private final AuthenticationStrategy proxyAuthStrategy;
    private final HttpAuthenticator authenticator;

    public ProtocolExec(
            final HttpProcessor httpProcessor,
            final AuthenticationStrategy targetAuthStrategy,
            final AuthenticationStrategy proxyAuthStrategy) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP protocol processor");
        this.targetAuthStrategy = Args.notNull(targetAuthStrategy, "Target authentication strategy");
        this.proxyAuthStrategy = Args.notNull(proxyAuthStrategy, "Proxy authentication strategy");
        this.authenticator = new HttpAuthenticator();
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");

        final HttpRoute route = scope.route;
        final HttpClientContext context = scope.clientContext;
        final ExecRuntime execRuntime = scope.execRuntime;

        try {
            final HttpHost target = route.getTargetHost();
            final HttpHost proxy = route.getProxyHost();
            if (proxy != null && !route.isTunnelled()) {
                try {
                    URI uri = request.getUri();
                    if (!uri.isAbsolute()) {
                        uri = URIUtils.rewriteURI(uri, target, true);
                    } else {
                        uri = URIUtils.rewriteURI(uri);
                    }
                    request.setPath(uri.toASCIIString());
                } catch (final URISyntaxException ex) {
                    throw new ProtocolException("Invalid request URI: " + request.getRequestUri(), ex);
                }
            }

            final URIAuthority authority = request.getAuthority();
            if (authority != null) {
                final CredentialsProvider credsProvider = context.getCredentialsProvider();
                if (credsProvider instanceof CredentialsStore) {
                    AuthSupport.extractFromAuthority(authority, (CredentialsStore) credsProvider);
                }
            }

            final AuthExchange targetAuthExchange = context.getAuthExchange(target);
            final AuthExchange proxyAuthExchange = proxy != null ? context.getAuthExchange(proxy) : new AuthExchange();

            for (int execCount = 1;; execCount++) {

                if (execCount > 1) {
                    final HttpEntity entity = request.getEntity();
                    if (entity != null && !entity.isRepeatable()) {
                        throw new NonRepeatableRequestException("Cannot retry request " +
                                "with a non-repeatable request entity.");
                    }
                }

                // Run request protocol interceptors
                context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
                context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

                httpProcessor.process(request, request.getEntity(), context);

                if (!request.containsHeader(HttpHeaders.AUTHORIZATION)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Target auth state: " + targetAuthExchange.getState());
                    }
                    authenticator.addAuthResponse(target, ChallengeType.TARGET, request, targetAuthExchange, context);
                }
                if (!request.containsHeader(HttpHeaders.PROXY_AUTHORIZATION) && !route.isTunnelled()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Proxy auth state: " + proxyAuthExchange.getState());
                    }
                    authenticator.addAuthResponse(proxy, ChallengeType.PROXY, request, proxyAuthExchange, context);
                }

                final ClassicHttpResponse response = chain.proceed(request, scope);

                context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                httpProcessor.process(response, response.getEntity(), context);

                if (request.getMethod().equalsIgnoreCase(StandardMethods.TRACE.name())) {
                    // Do not perform authentication for TRACE request
                    return response;
                }

                if (needAuthentication(targetAuthExchange, proxyAuthExchange, route, request, response, context)) {
                    // Make sure the response body is fully consumed, if present
                    final HttpEntity entity = response.getEntity();
                    if (execRuntime.isConnectionReusable()) {
                        EntityUtils.consume(entity);
                    } else {
                        execRuntime.disconnect();
                        if (proxyAuthExchange.getState() == AuthExchange.State.SUCCESS
                                && proxyAuthExchange.getAuthScheme() != null
                                && proxyAuthExchange.getAuthScheme().isConnectionBased()) {
                            log.debug("Resetting proxy auth state");
                            proxyAuthExchange.reset();
                        }
                        if (targetAuthExchange.getState() == AuthExchange.State.SUCCESS
                                && targetAuthExchange.getAuthScheme() != null
                                && targetAuthExchange.getAuthScheme().isConnectionBased()) {
                            log.debug("Resetting target auth state");
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
                    return response;
                }
            }
        } catch (final RuntimeException | HttpException | IOException ex) {
            execRuntime.discardConnection();
            throw ex;
        }
    }

    private boolean needAuthentication(
            final AuthExchange targetAuthExchange,
            final AuthExchange proxyAuthExchange,
            final HttpRoute route,
            final ClassicHttpRequest request,
            final HttpResponse response,
            final HttpClientContext context) {
        final RequestConfig config = context.getRequestConfig();
        if (config.isAuthenticationEnabled()) {
            final HttpHost target = AuthSupport.resolveAuthTarget(request, route);
            final boolean targetAuthRequested = authenticator.isChallenged(
                    target, ChallengeType.TARGET, response, targetAuthExchange, context);

            HttpHost proxy = route.getProxyHost();
            // if proxy is not set use target host instead
            if (proxy == null) {
                proxy = route.getTargetHost();
            }
            final boolean proxyAuthRequested = authenticator.isChallenged(
                    proxy, ChallengeType.PROXY, response, proxyAuthExchange, context);

            if (targetAuthRequested) {
                return authenticator.prepareAuthResponse(target, ChallengeType.TARGET, response,
                        targetAuthStrategy, targetAuthExchange, context);
            }
            if (proxyAuthRequested) {
                return authenticator.prepareAuthResponse(proxy, ChallengeType.PROXY, response,
                        proxyAuthStrategy, proxyAuthExchange, context);
            }
        }
        return false;
    }

}
