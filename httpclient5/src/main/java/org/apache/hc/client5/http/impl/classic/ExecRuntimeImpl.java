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

import org.apache.hc.client5.http.CancellableAware;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.Logger;

class ExecRuntimeImpl implements ExecRuntime, Cancellable {

    private final Logger log;

    private final HttpClientConnectionManager manager;
    private final HttpRequestExecutor requestExecutor;
    private final CancellableAware cancellableAware;
    private final AtomicReference<ConnectionEndpoint> endpointRef;

    private volatile boolean reusable;
    private volatile Object state;
    private volatile TimeValue validDuration;

    ExecRuntimeImpl(
            final Logger log,
            final HttpClientConnectionManager manager,
            final HttpRequestExecutor requestExecutor,
            final CancellableAware cancellableAware) {
        super();
        this.log = log;
        this.manager = manager;
        this.requestExecutor = requestExecutor;
        this.cancellableAware = cancellableAware;
        this.endpointRef = new AtomicReference<>(null);
        this.validDuration = TimeValue.NEG_ONE_MILLISECONDS;
    }

    @Override
    public boolean isExecutionAborted() {
        return cancellableAware != null && cancellableAware.isCancelled();
    }

    @Override
    public boolean isConnectionAcquired() {
        return endpointRef.get() != null;
    }

    @Override
    public void acquireConnection(final HttpRoute route, final Object object, final HttpClientContext context) throws IOException {
        Args.notNull(route, "Route");
        if (endpointRef.get() == null) {
            final RequestConfig requestConfig = context.getRequestConfig();
            final Timeout requestTimeout = requestConfig.getConnectionRequestTimeout();
            final LeaseRequest connRequest = manager.lease(route, requestTimeout, object);
            state = object;
            if (cancellableAware != null) {
                if (cancellableAware.isCancelled()) {
                    connRequest.cancel();
                    throw new RequestFailedException("Request aborted");
                }
                cancellableAware.setCancellable(connRequest);
            }
            try {
                final ConnectionEndpoint connectionEndpoint = connRequest.get(requestTimeout.getDuration(), requestTimeout.getTimeUnit());
                endpointRef.set(connectionEndpoint);
                reusable = connectionEndpoint.isConnected();
                if (cancellableAware != null) {
                    cancellableAware.setCancellable(this);
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
    public boolean isConnected() {
        final ConnectionEndpoint endpoint = endpointRef.get();
        return endpoint != null && endpoint.isConnected();
    }

    private void connectEndpoint(final ConnectionEndpoint endpoint, final HttpClientContext context) throws IOException {
        if (cancellableAware != null) {
            if (cancellableAware.isCancelled()) {
                throw new RequestFailedException("Request aborted");
            }
        }
        final RequestConfig requestConfig = context.getRequestConfig();
        final TimeValue connectTimeout = requestConfig.getConnectTimeout();
        manager.connect(endpoint, connectTimeout, context);
        final TimeValue socketTimeout = requestConfig.getSocketTimeout();
        if (socketTimeout.getDuration() >= 0) {
            endpoint.setSocketTimeout(socketTimeout.toMillisIntBound());
        }
    }

    @Override
    public void connect(final HttpClientContext context) throws IOException {
        final ConnectionEndpoint endpoint = ensureValid();
        if (!endpoint.isConnected()) {
            connectEndpoint(endpoint, context);
        }
    }

    @Override
    public void disconnect() throws IOException {
        final ConnectionEndpoint endpoint = endpointRef.get();
        if (endpoint != null) {
            endpoint.close();
            log.debug("Disconnected");
        }
    }

    @Override
    public void upgradeTls(final HttpClientContext context) throws IOException {
        final ConnectionEndpoint endpoint = ensureValid();
        manager.upgrade(endpoint, context);
    }

    @Override
    public ClassicHttpResponse execute(final ClassicHttpRequest request, final HttpClientContext context) throws IOException, HttpException {
        final ConnectionEndpoint endpoint = ensureValid();
        if (!endpoint.isConnected()) {
            connectEndpoint(endpoint, context);
        }
        return endpoint.execute(request, requestExecutor, context);
    }

    @Override
    public boolean isConnectionReusable() {
        return reusable;
    }

    @Override
    public void markConnectionReusable() {
        reusable = true;
    }

    @Override
    public void markConnectionNonReusable() {
        reusable = false;
    }

    @Override
    public void setConnectionState(final Object state) {
        this.state = state;
    }

    @Override
    public void setConnectionValidFor(final TimeValue duration) {
        validDuration = duration;
    }

    @Override
    public void releaseConnection() {
        final ConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            if (reusable) {
                manager.release(endpoint, state, validDuration);
            } else {
                try {
                    endpoint.close();
                    log.debug("Connection discarded");
                } catch (final IOException ex) {
                    if (log.isDebugEnabled()) {
                        log.debug(ex.getMessage(), ex);
                    }
                } finally {
                    manager.release(endpoint, null, TimeValue.ZERO_MILLISECONDS);
                }
            }
        }
    }

    @Override
    public void discardConnection() {
        final ConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            try {
                endpoint.shutdown(ShutdownType.IMMEDIATE);
                log.debug("Connection discarded");
            } finally {
                manager.release(endpoint, null, TimeValue.ZERO_MILLISECONDS);
            }
        }
    }

    @Override
    public boolean cancel() {
        final boolean alreadyReleased = endpointRef.get() == null;
        log.debug("Cancelling request execution");
        discardConnection();
        return !alreadyReleased;
    }

}
