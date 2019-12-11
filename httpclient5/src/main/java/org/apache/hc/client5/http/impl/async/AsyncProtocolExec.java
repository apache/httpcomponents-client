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
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.AuthSupport;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request execution handler in the asynchronous request execution chain
 * that is responsible for implementation of HTTP specification requirements.
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
public final class AsyncProtocolExec implements AsyncExecChainHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final HttpProcessor httpProcessor;
    private final AuthenticationStrategy targetAuthStrategy;
    private final AuthenticationStrategy proxyAuthStrategy;
    private final HttpAuthenticator authenticator;

    AsyncProtocolExec(
            final HttpProcessor httpProcessor,
            final AuthenticationStrategy targetAuthStrategy,
            final AuthenticationStrategy proxyAuthStrategy) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP protocol processor");
        this.targetAuthStrategy = Args.notNull(targetAuthStrategy, "Target authentication strategy");
        this.proxyAuthStrategy = Args.notNull(proxyAuthStrategy, "Proxy authentication strategy");
        this.authenticator = new HttpAuthenticator(log);
    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        final HttpRoute route = scope.route;
        final HttpClientContext clientContext = scope.clientContext;

        if (route.getProxyHost() != null && !route.isTunnelled()) {
            try {
                URI uri = request.getUri();
                if (!uri.isAbsolute()) {
                    uri = URIUtils.rewriteURI(uri, route.getTargetHost(), true);
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
            final CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
            if (credsProvider instanceof CredentialsStore) {
                AuthSupport.extractFromAuthority(request.getScheme(), authority, (CredentialsStore) credsProvider);
            }
        }

        final AtomicBoolean challenged = new AtomicBoolean(false);
        internalExecute(challenged, request, entityProducer, scope, chain, asyncExecCallback);
    }

    private void internalExecute(
            final AtomicBoolean challenged,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        final String exchangeId = scope.exchangeId;
        final HttpRoute route = scope.route;
        final HttpClientContext clientContext = scope.clientContext;
        final AsyncExecRuntime execRuntime = scope.execRuntime;

        final HttpHost target = route.getTargetHost();
        final HttpHost proxy = route.getProxyHost();

        final AuthExchange targetAuthExchange = clientContext.getAuthExchange(target);
        final AuthExchange proxyAuthExchange = proxy != null ? clientContext.getAuthExchange(proxy) : new AuthExchange();

        clientContext.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        clientContext.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        httpProcessor.process(request, entityProducer, clientContext);

        if (!request.containsHeader(HttpHeaders.AUTHORIZATION)) {
            if (log.isDebugEnabled()) {
                log.debug(exchangeId + ": target auth state: " + targetAuthExchange.getState());
            }
            authenticator.addAuthResponse(target, ChallengeType.TARGET, request, targetAuthExchange, clientContext);
        }
        if (!request.containsHeader(HttpHeaders.PROXY_AUTHORIZATION) && !route.isTunnelled()) {
            if (log.isDebugEnabled()) {
                log.debug(exchangeId + ": proxy auth state: " + proxyAuthExchange.getState());
            }
            authenticator.addAuthResponse(proxy, ChallengeType.PROXY, request, proxyAuthExchange, clientContext);
        }

        chain.proceed(request, entityProducer, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails) throws HttpException, IOException {

                clientContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                httpProcessor.process(response, entityDetails, clientContext);

                if (Method.TRACE.isSame(request.getMethod())) {
                    // Do not perform authentication for TRACE request
                    return asyncExecCallback.handleResponse(response, entityDetails);
                }
                if (needAuthentication(targetAuthExchange, proxyAuthExchange, route, request, response, clientContext)) {
                    challenged.set(true);
                    return null;
                }
                challenged.set(false);
                return asyncExecCallback.handleResponse(response, entityDetails);
            }

            @Override
            public void handleInformationResponse(
                    final HttpResponse response) throws HttpException, IOException {
                asyncExecCallback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                if (!execRuntime.isEndpointConnected()) {
                    if (proxyAuthExchange.getState() == AuthExchange.State.SUCCESS
                            && proxyAuthExchange.isConnectionBased()) {
                        if (log.isDebugEnabled()) {
                            log.debug(exchangeId + ": resetting proxy auth state");
                        }
                        proxyAuthExchange.reset();
                    }
                    if (targetAuthExchange.getState() == AuthExchange.State.SUCCESS
                            && targetAuthExchange.isConnectionBased()) {
                        if (log.isDebugEnabled()) {
                            log.debug(exchangeId + ": esetting target auth state");
                        }
                        targetAuthExchange.reset();
                    }
                }

                if (challenged.get()) {
                    if (entityProducer != null && !entityProducer.isRepeatable()) {
                        if (log.isDebugEnabled()) {
                            log.debug(exchangeId + ": annot retry non-repeatable request");
                        }
                        asyncExecCallback.completed();
                    } else {
                        // Reset request headers
                        final HttpRequest original = scope.originalRequest;
                        request.setHeaders();
                        for (final Iterator<Header> it = original.headerIterator(); it.hasNext(); ) {
                            request.addHeader(it.next());
                        }
                        try {
                            if (entityProducer != null) {
                                entityProducer.releaseResources();
                            }
                            internalExecute(challenged, request, entityProducer, scope, chain, asyncExecCallback);
                        } catch (final HttpException | IOException ex) {
                            asyncExecCallback.failed(ex);
                        }
                    }
                } else {
                    asyncExecCallback.completed();
                }
            }

            @Override
            public void failed(final Exception cause) {
                if (cause instanceof IOException || cause instanceof RuntimeException) {
                    if (proxyAuthExchange.isConnectionBased()) {
                        proxyAuthExchange.reset();
                    }
                    if (targetAuthExchange.isConnectionBased()) {
                        targetAuthExchange.reset();
                    }
                }
                asyncExecCallback.failed(cause);
            }

        });
    }

    private boolean needAuthentication(
            final AuthExchange targetAuthExchange,
            final AuthExchange proxyAuthExchange,
            final HttpRoute route,
            final HttpRequest request,
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
                return authenticator.updateAuthState(target, ChallengeType.TARGET, response,
                        targetAuthStrategy, targetAuthExchange, context);
            }
            if (proxyAuthRequested) {
                return authenticator.updateAuthState(proxy, ChallengeType.PROXY, response,
                        proxyAuthStrategy, proxyAuthExchange, context);
            }
        }
        return false;
    }

}
