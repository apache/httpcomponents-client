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
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteTracker;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.impl.routing.BasicRouteDirector;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.protocol.AuthenticationStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.NonRepeatableRequestException;
import org.apache.hc.client5.http.protocol.UserTokenHandler;
import org.apache.hc.client5.http.routing.HttpRouteDirector;
import org.apache.hc.client5.http.sync.methods.HttpExecutionAware;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The last request executor in the HTTP request execution chain
 * that is responsible for execution of request / response
 * exchanges with the opposite endpoint.
 * This executor will automatically retry the request in case
 * of an authentication challenge by an intermediate proxy or
 * by the target server.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class MainClientExec implements ClientExecChain {

    private final Logger log = LogManager.getLogger(getClass());

    private final HttpRequestExecutor requestExecutor;
    private final HttpClientConnectionManager connManager;
    private final ConnectionReuseStrategy reuseStrategy;
    private final ConnectionKeepAliveStrategy keepAliveStrategy;
    private final HttpProcessor proxyHttpProcessor;
    private final AuthenticationStrategy targetAuthStrategy;
    private final AuthenticationStrategy proxyAuthStrategy;
    private final HttpAuthenticator authenticator;
    private final UserTokenHandler userTokenHandler;
    private final HttpRouteDirector routeDirector;

    /**
     * @since 4.4
     */
    public MainClientExec(
            final HttpRequestExecutor requestExecutor,
            final HttpClientConnectionManager connManager,
            final ConnectionReuseStrategy reuseStrategy,
            final ConnectionKeepAliveStrategy keepAliveStrategy,
            final HttpProcessor proxyHttpProcessor,
            final AuthenticationStrategy targetAuthStrategy,
            final AuthenticationStrategy proxyAuthStrategy,
            final UserTokenHandler userTokenHandler) {
        Args.notNull(requestExecutor, "HTTP request executor");
        Args.notNull(connManager, "Client connection manager");
        Args.notNull(reuseStrategy, "Connection reuse strategy");
        Args.notNull(keepAliveStrategy, "Connection keep alive strategy");
        Args.notNull(proxyHttpProcessor, "Proxy HTTP processor");
        Args.notNull(targetAuthStrategy, "Target authentication strategy");
        Args.notNull(proxyAuthStrategy, "Proxy authentication strategy");
        Args.notNull(userTokenHandler, "User token handler");
        this.authenticator      = new HttpAuthenticator();
        this.routeDirector      = new BasicRouteDirector();
        this.requestExecutor    = requestExecutor;
        this.connManager        = connManager;
        this.reuseStrategy      = reuseStrategy;
        this.keepAliveStrategy  = keepAliveStrategy;
        this.proxyHttpProcessor = proxyHttpProcessor;
        this.targetAuthStrategy = targetAuthStrategy;
        this.proxyAuthStrategy  = proxyAuthStrategy;
        this.userTokenHandler   = userTokenHandler;
    }

    public MainClientExec(
            final HttpRequestExecutor requestExecutor,
            final HttpClientConnectionManager connManager,
            final ConnectionReuseStrategy reuseStrategy,
            final ConnectionKeepAliveStrategy keepAliveStrategy,
            final AuthenticationStrategy targetAuthStrategy,
            final AuthenticationStrategy proxyAuthStrategy,
            final UserTokenHandler userTokenHandler) {
        this(requestExecutor, connManager, reuseStrategy, keepAliveStrategy,
                new DefaultHttpProcessor(new RequestTargetHost()),
                targetAuthStrategy, proxyAuthStrategy, userTokenHandler);
    }

    @Override
    public ClassicHttpResponse execute(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");
        final HttpRoute route = request.getRoute();

        RequestEntityProxy.enhance(request);

        Object userToken = context.getUserToken();

        final LeaseRequest leaseRequest = connManager.lease(route, userToken);
        if (execAware != null) {
            if (execAware.isAborted()) {
                leaseRequest.cancel();
                throw new RequestAbortedException("Request aborted");
            } else {
                execAware.setCancellable(leaseRequest);
            }
        }

        final RequestConfig config = context.getRequestConfig();

        final ConnectionEndpoint endpoint;
        try {
            final int timeout = config.getConnectionRequestTimeout();
            endpoint = leaseRequest.get(timeout > 0 ? timeout : 0, TimeUnit.MILLISECONDS);
        } catch(final TimeoutException ex) {
            throw new ConnectionRequestTimeoutException(ex.getMessage());
        } catch(final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RequestAbortedException("Request aborted", interrupted);
        } catch(final ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause == null) {
                cause = ex;
            }
            throw new RequestAbortedException("Request execution failed", cause);
        }

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, endpoint);

        final EndpointHolder endpointHolder = new EndpointHolder(this.log, this.connManager, endpoint);
        try {
            if (execAware != null) {
                execAware.setCancellable(endpointHolder);
            }

            final AuthExchange targetAuthExchange = context.getAuthExchange(route.getTargetHost());
            final AuthExchange proxyAuthExchange = route.getProxyHost() != null ?
                    context.getAuthExchange(route.getProxyHost()) : new AuthExchange();

            ClassicHttpResponse response;
            for (int execCount = 1;; execCount++) {

                if (execCount > 1 && !RequestEntityProxy.isRepeatable(request)) {
                    throw new NonRepeatableRequestException("Cannot retry request " +
                            "with a non-repeatable request entity.");
                }

                if (execAware != null && execAware.isAborted()) {
                    throw new RequestAbortedException("Request aborted");
                }

                if (!endpoint.isConnected()) {
                    endpointHolder.markNonReusable();
                    this.log.debug("Opening connection " + route);
                    try {
                        establishRoute(endpoint, route, request, context);
                        endpointHolder.markReusable();
                    } catch (final TunnelRefusedException ex) {
                        if (this.log.isDebugEnabled()) {
                            this.log.debug(ex.getMessage());
                        }
                        response = ex.getResponse();
                        break;
                    }
                }
                final int timeout = config.getSocketTimeout();
                if (timeout >= 0) {
                    endpoint.setSocketTimeout(timeout);
                }

                if (execAware != null && execAware.isAborted()) {
                    throw new RequestAbortedException("Request aborted");
                }

                if (this.log.isDebugEnabled()) {
                    this.log.debug("Executing request " + new RequestLine(request));
                }

                if (!request.containsHeader(HttpHeaders.AUTHORIZATION)) {
                    if (this.log.isDebugEnabled()) {
                        this.log.debug("Target auth state: " + targetAuthExchange.getState());
                    }
                    this.authenticator.addAuthResponse(
                            route.getTargetHost(), ChallengeType.TARGET, request, targetAuthExchange, context);
                }
                if (!request.containsHeader(HttpHeaders.PROXY_AUTHORIZATION) && !route.isTunnelled()) {
                    if (this.log.isDebugEnabled()) {
                        this.log.debug("Proxy auth state: " + proxyAuthExchange.getState());
                    }
                    this.authenticator.addAuthResponse(
                            route.getProxyHost(), ChallengeType.PROXY, request, proxyAuthExchange, context);
                }

                response = endpoint.execute(request, requestExecutor, context);

                // The connection is in or can be brought to a re-usable state.
                if (reuseStrategy.keepAlive(request, response, context)) {
                    // Set the idle duration of this connection
                    final long duration = keepAliveStrategy.getKeepAliveDuration(response, context);
                    if (this.log.isDebugEnabled()) {
                        final String s;
                        if (duration > 0) {
                            s = "for " + duration + " " + TimeUnit.MILLISECONDS;
                        } else {
                            s = "indefinitely";
                        }
                        this.log.debug("Connection can be kept alive " + s);
                    }
                    endpointHolder.setValidFor(duration, TimeUnit.MILLISECONDS);
                    endpointHolder.markReusable();
                } else {
                    endpointHolder.markNonReusable();
                }

                if (request.getMethod().equalsIgnoreCase("TRACE")) {
                    // Do not perform authentication for TRACE request
                    break;
                }

                if (needAuthentication(
                        targetAuthExchange, proxyAuthExchange, route, request, response, context)) {
                    // Make sure the response body is fully consumed, if present
                    final HttpEntity entity = response.getEntity();
                    if (endpointHolder.isReusable()) {
                        EntityUtils.consume(entity);
                    } else {
                        endpoint.close();
                        if (proxyAuthExchange.getState() == AuthExchange.State.SUCCESS
                                && proxyAuthExchange.getAuthScheme() != null
                                && proxyAuthExchange.getAuthScheme().isConnectionBased()) {
                            this.log.debug("Resetting proxy auth state");
                            proxyAuthExchange.reset();
                        }
                        if (targetAuthExchange.getState() == AuthExchange.State.SUCCESS
                                && targetAuthExchange.getAuthScheme() != null
                                && targetAuthExchange.getAuthScheme().isConnectionBased()) {
                            this.log.debug("Resetting target auth state");
                            targetAuthExchange.reset();
                        }
                    }
                    // discard previous auth headers
                    final HttpRequest original = request.getOriginal();
                    if (!original.containsHeader(HttpHeaders.AUTHORIZATION)) {
                        request.removeHeaders(HttpHeaders.AUTHORIZATION);
                    }
                    if (!original.containsHeader(HttpHeaders.PROXY_AUTHORIZATION)) {
                        request.removeHeaders(HttpHeaders.PROXY_AUTHORIZATION);
                    }
                } else {
                    break;
                }
            }

            if (userToken == null) {
                userToken = userTokenHandler.getUserToken(route, context);
                context.setAttribute(HttpClientContext.USER_TOKEN, userToken);
            }
            if (userToken != null) {
                endpointHolder.setState(userToken);
            }

            // check for entity, release connection if possible
            final HttpEntity entity = response.getEntity();
            if (entity == null || !entity.isStreaming()) {
                // connection not needed and (assumed to be) in re-usable state
                endpointHolder.releaseConnection();
                return new CloseableHttpResponse(response, null);
            } else {
                ResponseEntityProxy.enchance(response, endpointHolder);
                return new CloseableHttpResponse(response, endpointHolder);
            }
        } catch (final ConnectionShutdownException ex) {
            final InterruptedIOException ioex = new InterruptedIOException(
                    "Connection has been shut down");
            ioex.initCause(ex);
            throw ioex;
        } catch (final HttpException | RuntimeException | IOException ex) {
            endpointHolder.abortConnection();
            throw ex;
        }
    }

    /**
     * Establishes the target route.
     */
    void establishRoute(
            final ConnectionEndpoint endpoint,
            final HttpRoute route,
            final HttpRequest request,
            final HttpClientContext context) throws HttpException, IOException {
        final RequestConfig config = context.getRequestConfig();
        final int timeout = config.getConnectTimeout();
        final RouteTracker tracker = new RouteTracker(route);
        int step;
        do {
            final HttpRoute fact = tracker.toRoute();
            step = this.routeDirector.nextStep(route, fact);

            switch (step) {

            case HttpRouteDirector.CONNECT_TARGET:
                this.connManager.connect(
                        endpoint,
                        timeout > 0 ? timeout : 0,
                        TimeUnit.MILLISECONDS,
                        context);
                tracker.connectTarget(route.isSecure());
                break;
            case HttpRouteDirector.CONNECT_PROXY:
                this.connManager.connect(
                        endpoint,
                        timeout > 0 ? timeout : 0,
                        TimeUnit.MILLISECONDS,
                        context);
                final HttpHost proxy  = route.getProxyHost();
                tracker.connectProxy(proxy, false);
                break;
            case HttpRouteDirector.TUNNEL_TARGET: {
                final boolean secure = createTunnelToTarget(endpoint, route, request, context);
                this.log.debug("Tunnel to target created.");
                tracker.tunnelTarget(secure);
            }   break;

            case HttpRouteDirector.TUNNEL_PROXY: {
                // The most simple example for this case is a proxy chain
                // of two proxies, where P1 must be tunnelled to P2.
                // route: Source -> P1 -> P2 -> Target (3 hops)
                // fact:  Source -> P1 -> Target       (2 hops)
                final int hop = fact.getHopCount()-1; // the hop to establish
                final boolean secure = createTunnelToProxy(route, hop, context);
                this.log.debug("Tunnel to proxy created.");
                tracker.tunnelProxy(route.getHopTarget(hop), secure);
            }   break;

            case HttpRouteDirector.LAYER_PROTOCOL:
                this.connManager.upgrade(endpoint, context);
                tracker.layerProtocol(route.isSecure());
                break;

            case HttpRouteDirector.UNREACHABLE:
                throw new HttpException("Unable to establish route: " +
                        "planned = " + route + "; current = " + fact);
            case HttpRouteDirector.COMPLETE:
                break;
            default:
                throw new IllegalStateException("Unknown step indicator "
                        + step + " from RouteDirector.");
            }

        } while (step > HttpRouteDirector.COMPLETE);
    }

    /**
     * Creates a tunnel to the target server.
     * The connection must be established to the (last) proxy.
     * A CONNECT request for tunnelling through the proxy will
     * be created and sent, the response received and checked.
     * This method does <i>not</i> processChallenge the connection with
     * information about the tunnel, that is left to the caller.
     */
    private boolean createTunnelToTarget(
            final ConnectionEndpoint endpoint,
            final HttpRoute route,
            final HttpRequest request,
            final HttpClientContext context) throws HttpException, IOException {

        final RequestConfig config = context.getRequestConfig();
        final int timeout = config.getConnectTimeout();

        final HttpHost target = route.getTargetHost();
        final HttpHost proxy = route.getProxyHost();
        final AuthExchange proxyAuthExchange = context.getAuthExchange(proxy);
        ClassicHttpResponse response = null;

        final String authority = target.toHostString();
        final ClassicHttpRequest connect = new BasicClassicHttpRequest("CONNECT", authority);
        connect.setVersion(HttpVersion.HTTP_1_1);

        this.requestExecutor.preProcess(connect, this.proxyHttpProcessor, context);

        while (response == null) {
            if (!endpoint.isConnected()) {
                this.connManager.connect(
                        endpoint,
                        timeout > 0 ? timeout : 0,
                        TimeUnit.MILLISECONDS,
                        context);
            }

            connect.removeHeaders(HttpHeaders.PROXY_AUTHORIZATION);
            this.authenticator.addAuthResponse(proxy, ChallengeType.PROXY, connect, proxyAuthExchange, context);

            response = endpoint.execute(connect, this.requestExecutor, context);

            final int status = response.getCode();
            if (status < HttpStatus.SC_SUCCESS) {
                throw new HttpException("Unexpected response to CONNECT request: " + new StatusLine(response));
            }

            if (config.isAuthenticationEnabled()) {
                if (this.authenticator.isChallenged(proxy, ChallengeType.PROXY, response,
                        proxyAuthExchange, context)) {
                    if (this.authenticator.prepareAuthResponse(proxy, ChallengeType.PROXY, response,
                            this.proxyAuthStrategy, proxyAuthExchange, context)) {
                        // Retry request
                        if (this.reuseStrategy.keepAlive(request, response, context)) {
                            this.log.debug("Connection kept alive");
                            // Consume response content
                            final HttpEntity entity = response.getEntity();
                            EntityUtils.consume(entity);
                        } else {
                            endpoint.close();
                        }
                        response = null;
                    }
                }
            }
        }

        final int status = response.getCode();
        if (status >= HttpStatus.SC_REDIRECTION) {

            // Buffer response content
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                response.setEntity(new BufferedHttpEntity(entity));
            }

            endpoint.close();
            throw new TunnelRefusedException("CONNECT refused by proxy: " +
                    new StatusLine(response), response);
        }

        // How to decide on security of the tunnelled connection?
        // The socket factory knows only about the segment to the proxy.
        // Even if that is secure, the hop to the target may be insecure.
        // Leave it to derived classes, consider insecure by default here.
        return false;
    }

    /**
     * Creates a tunnel to an intermediate proxy.
     * This method is <i>not</i> implemented in this class.
     * It just throws an exception here.
     */
    private boolean createTunnelToProxy(
            final HttpRoute route,
            final int hop,
            final HttpClientContext context) throws HttpException {

        // Have a look at createTunnelToTarget and replicate the parts
        // you need in a custom derived class. If your proxies don't require
        // authentication, it is not too hard. But for the stock version of
        // HttpClient, we cannot make such simplifying assumptions and would
        // have to include proxy authentication code. The HttpComponents team
        // is currently not in a position to support rarely used code of this
        // complexity. Feel free to submit patches that refactor the code in
        // createTunnelToTarget to facilitate re-use for proxy tunnelling.

        throw new HttpException("Proxy chains are not supported.");
    }

    private boolean needAuthentication(
            final AuthExchange targetAuthExchange,
            final AuthExchange proxyAuthExchange,
            final HttpRoute route,
            final RoutedHttpRequest request,
            final HttpResponse response,
            final HttpClientContext context) {
        final RequestConfig config = context.getRequestConfig();
        if (config.isAuthenticationEnabled()) {
            HttpHost target = request.getTargetHost();
            if (target.getPort() < 0) {
                target = new HttpHost(
                        target.getHostName(),
                        route.getTargetHost().getPort(),
                        target.getSchemeName());
            }
            final boolean targetAuthRequested = this.authenticator.isChallenged(
                    target, ChallengeType.TARGET, response, targetAuthExchange, context);

            HttpHost proxy = route.getProxyHost();
            // if proxy is not set use target host instead
            if (proxy == null) {
                proxy = route.getTargetHost();
            }
            final boolean proxyAuthRequested = this.authenticator.isChallenged(
                    proxy, ChallengeType.PROXY, response, proxyAuthExchange, context);

            if (targetAuthRequested) {
                return this.authenticator.prepareAuthResponse(target, ChallengeType.TARGET, response,
                        this.targetAuthStrategy, targetAuthExchange, context);
            }
            if (proxyAuthRequested) {
                return this.authenticator.prepareAuthResponse(proxy, ChallengeType.PROXY, response,
                        this.proxyAuthStrategy, proxyAuthExchange, context);
            }
        }
        return false;
    }

}
