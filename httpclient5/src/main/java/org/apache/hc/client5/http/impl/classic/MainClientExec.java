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
import java.io.InterruptedIOException;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The last request executor in the HTTP request execution chain
 * that is responsible for execution of request / response
 * exchanges with the opposite endpoint.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
final class MainClientExec implements ExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

    private final ConnectionReuseStrategy reuseStrategy;
    private final ConnectionKeepAliveStrategy keepAliveStrategy;
    private final UserTokenHandler userTokenHandler;

    /**
     * @since 4.4
     */
    public MainClientExec(
            final ConnectionReuseStrategy reuseStrategy,
            final ConnectionKeepAliveStrategy keepAliveStrategy,
            final UserTokenHandler userTokenHandler) {
        Args.notNull(reuseStrategy, "Connection reuse strategy");
        Args.notNull(keepAliveStrategy, "Connection keep alive strategy");
        Args.notNull(userTokenHandler, "User token handler");
        this.reuseStrategy      = reuseStrategy;
        this.keepAliveStrategy  = keepAliveStrategy;
        this.userTokenHandler   = userTokenHandler;
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
            if (this.log.isDebugEnabled()) {
                this.log.debug("Executing request " + new RequestLine(request));
            }
            RequestEntityProxy.enhance(request);

            final ClassicHttpResponse response = execRuntime.execute(request, context);

            Object userToken = context.getUserToken();
            if (userToken == null) {
                userToken = userTokenHandler.getUserToken(route, context);
                context.setAttribute(HttpClientContext.USER_TOKEN, userToken);
            }
            if (userToken != null) {
                execRuntime.setConnectionState(userToken);
            }

            // The connection is in or can be brought to a re-usable state.
            if (reuseStrategy.keepAlive(request, response, context)) {
                // Set the idle duration of this connection
                final TimeValue duration = keepAliveStrategy.getKeepAliveDuration(response, context);
                if (this.log.isDebugEnabled()) {
                    final String s;
                    if (duration != null) {
                        s = "for " + duration;
                    } else {
                        s = "indefinitely";
                    }
                    this.log.debug("Connection can be kept alive " + s);
                }
                execRuntime.setConnectionValidFor(duration);
                execRuntime.markConnectionReusable();
            } else {
                execRuntime.markConnectionNonReusable();
            }
            // check for entity, release connection if possible
            final HttpEntity entity = response.getEntity();
            if (entity == null || !entity.isStreaming()) {
                // connection not needed and (assumed to be) in re-usable state
                execRuntime.releaseConnection();
                return new CloseableHttpResponse(response, null);
            } else {
                ResponseEntityProxy.enchance(response, execRuntime);
                return new CloseableHttpResponse(response, execRuntime);
            }
        } catch (final ConnectionShutdownException ex) {
            final InterruptedIOException ioex = new InterruptedIOException(
                    "Connection has been shut down");
            ioex.initCause(ex);
            execRuntime.discardConnection();
            throw ioex;
        } catch (final HttpException | RuntimeException | IOException ex) {
            execRuntime.discardConnection();
            throw ex;
        }

    }

}
