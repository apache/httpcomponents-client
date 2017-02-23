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
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RequestClientConnControl;
import org.apache.hc.client5.http.sync.methods.HttpExecutionAware;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.util.Args;
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
public class MinimalClientExec implements ClientExecChain {

    private final Logger log = LogManager.getLogger(getClass());

    private final HttpRequestExecutor requestExecutor;
    private final HttpClientConnectionManager connManager;
    private final ConnectionReuseStrategy reuseStrategy;
    private final ConnectionKeepAliveStrategy keepAliveStrategy;
    private final HttpProcessor httpProcessor;

    public MinimalClientExec(
            final HttpRequestExecutor requestExecutor,
            final HttpClientConnectionManager connManager,
            final ConnectionReuseStrategy reuseStrategy,
            final ConnectionKeepAliveStrategy keepAliveStrategy) {
        this.requestExecutor = Args.notNull(requestExecutor, "Request executor");
        this.connManager = Args.notNull(connManager, "Connection manager");
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
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final HttpRoute route = request.getRoute();
        final LeaseRequest connRequest = connManager.lease(route, null);
        if (execAware != null) {
            if (execAware.isAborted()) {
                connRequest.cancel();
                throw new RequestAbortedException("Request aborted");
            }
            execAware.setCancellable(connRequest);
        }

        final RequestConfig config = context.getRequestConfig();

        final ConnectionEndpoint endpoint;
        try {
            final int timeout = config.getConnectionRequestTimeout();
            endpoint = connRequest.get(timeout > 0 ? timeout : 0, TimeUnit.MILLISECONDS);
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

        final EndpointHolder endpointHolder = new EndpointHolder(log, connManager, endpoint);
        try {
            if (execAware != null) {
                if (execAware.isAborted()) {
                    endpointHolder.close();
                    throw new RequestAbortedException("Request aborted");
                }
                execAware.setCancellable(endpointHolder);
            }
            if (!endpoint.isConnected()) {
                final int timeout = config.getConnectTimeout();
                this.connManager.connect(
                        endpoint,
                        timeout > 0 ? timeout : 0,
                        TimeUnit.MILLISECONDS,
                        context);
            }
            final int timeout = config.getSocketTimeout();
            if (timeout >= 0) {
                endpoint.setSocketTimeout(timeout);
            }

            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
            context.setAttribute(HttpClientContext.HTTP_ROUTE, route);

            httpProcessor.process(request, request.getEntity(), context);
            final ClassicHttpResponse response = endpoint.execute(request, requestExecutor, context);
            httpProcessor.process(response, response.getEntity(), context);

            // The connection is in or can be brought to a re-usable state.
            if (reuseStrategy.keepAlive(request, response, context)) {
                // Set the idle duration of this connection
                final long duration = keepAliveStrategy.getKeepAliveDuration(response, context);
                endpointHolder.setValidFor(duration, TimeUnit.MILLISECONDS);
                endpointHolder.markReusable();
            } else {
                endpointHolder.markNonReusable();
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

}
