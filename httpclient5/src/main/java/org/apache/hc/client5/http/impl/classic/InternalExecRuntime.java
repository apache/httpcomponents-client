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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

class InternalExecRuntime implements ExecRuntime, Cancellable {

    private final Logger log;

    private final HttpClientConnectionManager manager;
    private final HttpRequestExecutor requestExecutor;
    private final CancellableDependency cancellableDependency;
    private final AtomicReference<ConnectionEndpoint> endpointRef;

    private volatile boolean reusable;
    private volatile Object state;
    private volatile TimeValue validDuration;

    InternalExecRuntime(
            final Logger log,
            final HttpClientConnectionManager manager,
            final HttpRequestExecutor requestExecutor,
            final CancellableDependency cancellableDependency) {
        super();
        this.log = log;
        this.manager = manager;
        this.requestExecutor = requestExecutor;
        this.cancellableDependency = cancellableDependency;
        this.endpointRef = new AtomicReference<>(null);
        this.validDuration = TimeValue.NEG_ONE_MILLISECOND;
    }

    @Override
    public boolean isExecutionAborted() {
        return cancellableDependency != null && cancellableDependency.isCancelled();
    }

    @Override
    public boolean isEndpointAcquired() {
        return endpointRef.get() != null;
    }

    @Override
    public void acquireEndpoint(
            final String id, final HttpRoute route, final Object object, final HttpClientContext context) throws IOException {
        Args.notNull(route, "Route");
        if (endpointRef.get() == null) {
            final RequestConfig requestConfig = context.getRequestConfig();
            final Timeout connectionRequestTimeout = requestConfig.getConnectionRequestTimeout();
            if (log.isDebugEnabled()) {
                log.debug(id + ": acquiring endpoint (" + connectionRequestTimeout + ")");
            }
            final LeaseRequest connRequest = manager.lease(id, route, connectionRequestTimeout, object);
            state = object;
            if (cancellableDependency != null) {
                if (cancellableDependency.isCancelled()) {
                    connRequest.cancel();
                    throw new RequestFailedException("Request aborted");
                }
                cancellableDependency.setDependency(connRequest);
            }
            try {
                final ConnectionEndpoint connectionEndpoint = connRequest.get(connectionRequestTimeout);
                endpointRef.set(connectionEndpoint);
                reusable = connectionEndpoint.isConnected();
                if (cancellableDependency != null) {
                    cancellableDependency.setDependency(this);
                }
                if (log.isDebugEnabled()) {
                    log.debug(id + ": acquired endpoint " + ConnPoolSupport.getId(connectionEndpoint));
                }
            } catch(final TimeoutException ex) {
                throw new ConnectionRequestTimeoutException(ex.getMessage());
            } catch(final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RequestFailedException("Request aborted", interrupted);
            } catch(final ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause == null) {
                    cause = ex;
                }
                throw new RequestFailedException("Request execution failed", cause);
            }
        } else {
            throw new IllegalStateException("Endpoint already acquired");
        }
    }

    ConnectionEndpoint ensureValid() {
        final ConnectionEndpoint endpoint = endpointRef.get();
        if (endpoint == null) {
            throw new IllegalStateException("Endpoint not acquired / already released");
        }
        return endpoint;
    }

    @Override
    public boolean isEndpointConnected() {
        final ConnectionEndpoint endpoint = endpointRef.get();
        return endpoint != null && endpoint.isConnected();
    }

    private void connectEndpoint(final ConnectionEndpoint endpoint, final HttpClientContext context) throws IOException {
        if (cancellableDependency != null) {
            if (cancellableDependency.isCancelled()) {
                throw new RequestFailedException("Request aborted");
            }
        }
        final RequestConfig requestConfig = context.getRequestConfig();
        final Timeout connectTimeout = requestConfig.getConnectTimeout();
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": connecting endpoint (" + connectTimeout + ")");
        }
        manager.connect(endpoint, connectTimeout, context);
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": endpoint connected");
        }
    }

    @Override
    public void connectEndpoint(final HttpClientContext context) throws IOException {
        final ConnectionEndpoint endpoint = ensureValid();
        if (!endpoint.isConnected()) {
            connectEndpoint(endpoint, context);
        }
    }

    @Override
    public void disconnectEndpoint() throws IOException {
        final ConnectionEndpoint endpoint = endpointRef.get();
        if (endpoint != null) {
            endpoint.close();
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": endpoint closed");
            }
        }
    }

    @Override
    public void upgradeTls(final HttpClientContext context) throws IOException {
        final ConnectionEndpoint endpoint = ensureValid();
        final RequestConfig requestConfig = context.getRequestConfig();
        final Timeout connectTimeout = requestConfig.getConnectTimeout();
        if (TimeValue.isPositive(connectTimeout)) {
            endpoint.setSocketTimeout(connectTimeout);
        }
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": upgrading endpoint (" + connectTimeout + ")");
        }
        manager.upgrade(endpoint, context);
    }

    @Override
    public ClassicHttpResponse execute(
            final String id,
            final ClassicHttpRequest request,
            final HttpClientContext context) throws IOException, HttpException {
        final ConnectionEndpoint endpoint = ensureValid();
        if (!endpoint.isConnected()) {
            connectEndpoint(endpoint, context);
        }
        final RequestConfig requestConfig = context.getRequestConfig();
        final Timeout responseTimeout = requestConfig.getResponseTimeout();
        if (responseTimeout != null) {
            endpoint.setSocketTimeout(responseTimeout);
        }
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": start execution " + id);
        }
        return endpoint.execute(id, request, requestExecutor, context);
    }

    @Override
    public boolean isConnectionReusable() {
        return reusable;
    }

    @Override
    public void markConnectionReusable(final Object state, final TimeValue validDuration) {
        this.reusable = true;
        this.state = state;
        this.validDuration = validDuration;
    }

    @Override
    public void markConnectionNonReusable() {
        reusable = false;
    }

    private void discardEndpoint(final ConnectionEndpoint endpoint) {
        try {
            endpoint.close(CloseMode.IMMEDIATE);
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": endpoint closed");
            }
        } finally {
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": discarding endpoint");
            }
            manager.release(endpoint, null, TimeValue.ZERO_MILLISECONDS);
        }
    }

    @Override
    public void releaseEndpoint() {
        final ConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            if (reusable) {
                if (log.isDebugEnabled()) {
                    log.debug(ConnPoolSupport.getId(endpoint) + ": releasing valid endpoint");
                }
                manager.release(endpoint, state, validDuration);
            } else {
                discardEndpoint(endpoint);
            }
        }
    }

    @Override
    public void discardEndpoint() {
        final ConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            discardEndpoint(endpoint);
        }
    }

    @Override
    public boolean cancel() {
        final boolean alreadyReleased = endpointRef.get() == null;
        final ConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": cancel");
            }
            discardEndpoint(endpoint);
        }
        return !alreadyReleased;
    }

    @Override
    public ExecRuntime fork(final CancellableDependency cancellableDependency) {
        return new InternalExecRuntime(log, manager, requestExecutor, cancellableDependency);
    }

}
