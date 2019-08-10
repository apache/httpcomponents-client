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

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteTracker;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.TunnelRefusedException;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.impl.routing.BasicRouteDirector;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRouteDirector;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request execution handler in the classic request execution chain
 * that is responsible for establishing connection to the target
 * origin server as specified by the current connection route.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class ConnectExec implements ExecChainHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ConnectionReuseStrategy reuseStrategy;
    private final HttpProcessor proxyHttpProcessor;
    private final AuthenticationStrategy proxyAuthStrategy;
    private final HttpAuthenticator authenticator;
    private final HttpRouteDirector routeDirector;

    public ConnectExec(
            final ConnectionReuseStrategy reuseStrategy,
            final HttpProcessor proxyHttpProcessor,
            final AuthenticationStrategy proxyAuthStrategy) {
        Args.notNull(reuseStrategy, "Connection reuse strategy");
        Args.notNull(proxyHttpProcessor, "Proxy HTTP processor");
        Args.notNull(proxyAuthStrategy, "Proxy authentication strategy");
        this.reuseStrategy      = reuseStrategy;
        this.proxyHttpProcessor = proxyHttpProcessor;
        this.proxyAuthStrategy  = proxyAuthStrategy;
        this.authenticator      = new HttpAuthenticator(log);
        this.routeDirector      = new BasicRouteDirector();
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");

        final String exchangeId = scope.exchangeId;
        final HttpRoute route = scope.route;
        final HttpClientContext context = scope.clientContext;
        final ExecRuntime execRuntime = scope.execRuntime;

        if (!execRuntime.isEndpointAcquired()) {
            final Object userToken = context.getUserToken();
            if (log.isDebugEnabled()) {
                log.debug(exchangeId + ": acquiring connection with route " + route);
            }
            execRuntime.acquireEndpoint(exchangeId, route, userToken, context);
        }
        try {
            if (!execRuntime.isEndpointConnected()) {
                if (log.isDebugEnabled()) {
                    log.debug(exchangeId + ": opening connection " + route);
                }

                final RouteTracker tracker = new RouteTracker(route);
                int step;
                do {
                    final HttpRoute fact = tracker.toRoute();
                    step = this.routeDirector.nextStep(route, fact);

                    switch (step) {

                        case HttpRouteDirector.CONNECT_TARGET:
                            execRuntime.connectEndpoint(context);
                            tracker.connectTarget(route.isSecure());
                            break;
                        case HttpRouteDirector.CONNECT_PROXY:
                            execRuntime.connectEndpoint(context);
                            final HttpHost proxy  = route.getProxyHost();
                            tracker.connectProxy(proxy, route.isSecure() && !route.isTunnelled());
                            break;
                        case HttpRouteDirector.TUNNEL_TARGET: {
                            final boolean secure = createTunnelToTarget(exchangeId, route, request, execRuntime, context);
                            if (log.isDebugEnabled()) {
                                log.debug(exchangeId + ": tunnel to target created.");
                            }
                            tracker.tunnelTarget(secure);
                        }   break;

                        case HttpRouteDirector.TUNNEL_PROXY: {
                            // The most simple example for this case is a proxy chain
                            // of two proxies, where P1 must be tunnelled to P2.
                            // route: Source -> P1 -> P2 -> Target (3 hops)
                            // fact:  Source -> P1 -> Target       (2 hops)
                            final int hop = fact.getHopCount()-1; // the hop to establish
                            final boolean secure = createTunnelToProxy(route, hop, context);
                            if (log.isDebugEnabled()) {
                                log.debug(exchangeId + ": tunnel to proxy created.");
                            }
                            tracker.tunnelProxy(route.getHopTarget(hop), secure);
                        }   break;

                        case HttpRouteDirector.LAYER_PROTOCOL:
                            execRuntime.upgradeTls(context);
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
            return chain.proceed(request, scope);

        } catch (final IOException | HttpException | RuntimeException ex) {
            execRuntime.discardEndpoint();
            throw ex;
        }
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
            final String exchangeId,
            final HttpRoute route,
            final HttpRequest request,
            final ExecRuntime execRuntime,
            final HttpClientContext context) throws HttpException, IOException {

        final RequestConfig config = context.getRequestConfig();

        final HttpHost target = route.getTargetHost();
        final HttpHost proxy = route.getProxyHost();
        final AuthExchange proxyAuthExchange = context.getAuthExchange(proxy);
        ClassicHttpResponse response = null;

        final String authority = target.toHostString();
        final ClassicHttpRequest connect = new BasicClassicHttpRequest("CONNECT", target, authority);
        connect.setVersion(HttpVersion.HTTP_1_1);

        this.proxyHttpProcessor.process(connect, null, context);

        while (response == null) {
            connect.removeHeaders(HttpHeaders.PROXY_AUTHORIZATION);
            this.authenticator.addAuthResponse(proxy, ChallengeType.PROXY, connect, proxyAuthExchange, context);

            response = execRuntime.execute(exchangeId, connect, context);
            this.proxyHttpProcessor.process(response, response.getEntity(), context);

            final int status = response.getCode();
            if (status < HttpStatus.SC_SUCCESS) {
                throw new HttpException("Unexpected response to CONNECT request: " + new StatusLine(response));
            }

            if (config.isAuthenticationEnabled()) {
                if (this.authenticator.isChallenged(proxy, ChallengeType.PROXY, response,
                        proxyAuthExchange, context)) {
                    if (this.authenticator.updateAuthState(proxy, ChallengeType.PROXY, response,
                            this.proxyAuthStrategy, proxyAuthExchange, context)) {
                        // Retry request
                        if (this.reuseStrategy.keepAlive(request, response, context)) {
                            if (log.isDebugEnabled()) {
                                log.debug(exchangeId + ": connection kept alive");
                            }
                            // Consume response content
                            final HttpEntity entity = response.getEntity();
                            EntityUtils.consume(entity);
                        } else {
                            execRuntime.disconnectEndpoint();
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
            final String responseMessage = entity != null ? EntityUtils.toString(entity) : null;
            execRuntime.disconnectEndpoint();
            throw new TunnelRefusedException("CONNECT refused by proxy: " + new StatusLine(response), responseMessage);
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

}
