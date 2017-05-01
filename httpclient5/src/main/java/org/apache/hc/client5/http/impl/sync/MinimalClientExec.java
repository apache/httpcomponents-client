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

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RequestClientConnControl;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecChainHandler;
import org.apache.hc.client5.http.sync.ExecRuntime;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.VersionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Request executor that implements the most fundamental aspects of
 * the HTTP specification and the most straight-forward request / response
 * exchange with the target server. This executor does not support
 * execution via proxy and will make no attempts to retry the request
 * in case of a redirect, authentication challenge or I/O error.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
final class MinimalClientExec implements ExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

    private final ConnectionReuseStrategy reuseStrategy;
    private final ConnectionKeepAliveStrategy keepAliveStrategy;
    private final HttpProcessor httpProcessor;

    public MinimalClientExec(
            final ConnectionReuseStrategy reuseStrategy,
            final ConnectionKeepAliveStrategy keepAliveStrategy) {
        this.reuseStrategy = reuseStrategy != null ? reuseStrategy : DefaultConnectionReuseStrategy.INSTANCE;
        this.keepAliveStrategy = keepAliveStrategy != null ? keepAliveStrategy : DefaultConnectionKeepAliveStrategy.INSTANCE;
        this.httpProcessor = new DefaultHttpProcessor(
                new RequestContent(),
                new RequestTargetHost(),
                new RequestClientConnControl(),
                new RequestUserAgent(VersionInfo.getSoftwareInfo(
                        "Apache-HttpClient", "org.apache.hc.client5", getClass())));
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
        if (!execRuntime.isConnectionAcquired()) {
            execRuntime.acquireConnection(route, null, context);
        }
        try {
            if (!execRuntime.isConnected()) {
                execRuntime.connect(context);
            }

            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
            context.setAttribute(HttpClientContext.HTTP_ROUTE, route);

            httpProcessor.process(request, request.getEntity(), context);
            final ClassicHttpResponse response = execRuntime.execute(request, context);
            httpProcessor.process(response, response.getEntity(), context);

            // The connection is in or can be brought to a re-usable state.
            if (reuseStrategy.keepAlive(request, response, context)) {
                // Set the idle duration of this connection
                final TimeValue duration = keepAliveStrategy.getKeepAliveDuration(response, context);
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
            final InterruptedIOException ioex = new InterruptedIOException("Connection has been shut down");
            ioex.initCause(ex);
            execRuntime.discardConnection();
            throw ioex;
        } catch (final HttpException | RuntimeException | IOException ex) {
            execRuntime.discardConnection();
            throw ex;
        }
    }

}
