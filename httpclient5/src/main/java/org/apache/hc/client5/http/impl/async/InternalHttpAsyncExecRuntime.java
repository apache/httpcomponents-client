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

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

class InternalHttpAsyncExecRuntime implements AsyncExecRuntime {

    private final Logger log;
    private final AsyncClientConnectionManager manager;
    private final ConnectionInitiator connectionInitiator;
    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final AtomicReference<AsyncConnectionEndpoint> endpointRef;
    private volatile boolean reusable;
    private volatile Object state;
    private volatile TimeValue validDuration;

    InternalHttpAsyncExecRuntime(
            final Logger log,
            final AsyncClientConnectionManager manager,
            final ConnectionInitiator connectionInitiator,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpVersionPolicy versionPolicy) {
        super();
        this.log = log;
        this.manager = manager;
        this.connectionInitiator = connectionInitiator;
        this.pushHandlerFactory = pushHandlerFactory;
        this.versionPolicy = versionPolicy;
        this.endpointRef = new AtomicReference<>(null);
        this.validDuration = TimeValue.NEG_ONE_MILLISECOND;
    }

    @Override
    public boolean isEndpointAcquired() {
        return endpointRef.get() != null;
    }

    @Override
    public Cancellable acquireEndpoint(
            final String id,
            final HttpRoute route,
            final Object object,
            final HttpClientContext context,
            final FutureCallback<AsyncExecRuntime> callback) {
        if (endpointRef.get() == null) {
            state = object;
            final RequestConfig requestConfig = context.getRequestConfig();
            final Timeout connectionRequestTimeout = requestConfig.getConnectionRequestTimeout();
            if (log.isDebugEnabled()) {
                log.debug(id + ": acquiring endpoint (" + connectionRequestTimeout + ")");
            }
            return Operations.cancellable(manager.lease(
                    id,
                    route,
                    object,
                    connectionRequestTimeout,
                    new FutureCallback<AsyncConnectionEndpoint>() {

                        @Override
                        public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                            endpointRef.set(connectionEndpoint);
                            reusable = connectionEndpoint.isConnected();
                            if (log.isDebugEnabled()) {
                                log.debug(id + ": acquired endpoint " + ConnPoolSupport.getId(connectionEndpoint));
                            }
                            callback.completed(InternalHttpAsyncExecRuntime.this);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            callback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            callback.cancelled();
                        }
                    }));
        }
        callback.completed(this);
        return Operations.nonCancellable();
    }

    private void discardEndpoint(final AsyncConnectionEndpoint endpoint) {
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
        final AsyncConnectionEndpoint endpoint = endpointRef.getAndSet(null);
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
        final AsyncConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            discardEndpoint(endpoint);
        }
    }

    @Override
    public boolean validateConnection() {
        if (reusable) {
            final AsyncConnectionEndpoint endpoint = endpointRef.get();
            return endpoint != null && endpoint.isConnected();
        }
        final AsyncConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            discardEndpoint(endpoint);
        }
        return false;
    }

    AsyncConnectionEndpoint ensureValid() {
        final AsyncConnectionEndpoint endpoint = endpointRef.get();
        if (endpoint == null) {
            throw new IllegalStateException("Endpoint not acquired / already released");
        }
        return endpoint;
    }

    @Override
    public boolean isEndpointConnected() {
        final AsyncConnectionEndpoint endpoint = endpointRef.get();
        return endpoint != null && endpoint.isConnected();
    }

    @Override
    public Cancellable connectEndpoint(
            final HttpClientContext context,
            final FutureCallback<AsyncExecRuntime> callback) {
        final AsyncConnectionEndpoint endpoint = ensureValid();
        if (endpoint.isConnected()) {
            callback.completed(this);
            return Operations.nonCancellable();
        }
        final RequestConfig requestConfig = context.getRequestConfig();
        final Timeout connectTimeout = requestConfig.getConnectTimeout();
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": connecting endpoint (" + connectTimeout + ")");
        }
        return Operations.cancellable(manager.connect(
                endpoint,
                connectionInitiator,
                connectTimeout,
                versionPolicy,
                context,
                new FutureCallback<AsyncConnectionEndpoint>() {

                    @Override
                    public void completed(final AsyncConnectionEndpoint endpoint) {
                        if (log.isDebugEnabled()) {
                            log.debug(ConnPoolSupport.getId(endpoint) + ": endpoint connected");
                        }
                        callback.completed(InternalHttpAsyncExecRuntime.this);
                    }

                    @Override
                    public void failed(final Exception ex) {
                        callback.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        callback.cancelled();
                    }

        }));

    }

    @Override
    public void upgradeTls(final HttpClientContext context) {
        final AsyncConnectionEndpoint endpoint = ensureValid();
        final RequestConfig requestConfig = context.getRequestConfig();
        final Timeout connectTimeout = requestConfig.getConnectTimeout();
        if (TimeValue.isPositive(connectTimeout)) {
            endpoint.setSocketTimeout(connectTimeout);
        }
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": upgrading endpoint (" + connectTimeout + ")");
        }
        manager.upgrade(endpoint, versionPolicy, context);
    }

    @Override
    public Cancellable execute(
            final String id, final AsyncClientExchangeHandler exchangeHandler, final HttpClientContext context) {
        final AsyncConnectionEndpoint endpoint = ensureValid();
        if (endpoint.isConnected()) {
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": start execution " + id);
            }
            final RequestConfig requestConfig = context.getRequestConfig();
            final Timeout responseTimeout = requestConfig.getResponseTimeout();
            if (responseTimeout != null) {
                endpoint.setSocketTimeout(responseTimeout);
            }
            endpoint.execute(id, exchangeHandler, context);
            if (context.getRequestConfig().isHardCancellationEnabled()) {
                return new Cancellable() {
                    @Override
                    public boolean cancel() {
                        exchangeHandler.cancel();
                        return true;
                    }
                };
            }
        } else {
            connectEndpoint(context, new FutureCallback<AsyncExecRuntime>() {

                @Override
                public void completed(final AsyncExecRuntime runtime) {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(endpoint) + ": start execution " + id);
                    }
                    try {
                        endpoint.execute(id, exchangeHandler, pushHandlerFactory, context);
                    } catch (final RuntimeException ex) {
                        failed(ex);
                    }
                }

                @Override
                public void failed(final Exception ex) {
                    exchangeHandler.failed(ex);
                }

                @Override
                public void cancelled() {
                    exchangeHandler.failed(new InterruptedIOException());
                }

            });
        }
        return Operations.nonCancellable();
    }

    @Override
    public void markConnectionReusable(final Object newState, final TimeValue newValidDuration) {
        reusable = true;
        state = newState;
        validDuration = newValidDuration;
    }

    @Override
    public void markConnectionNonReusable() {
        reusable = false;
        state = null;
        validDuration = null;
    }

    @Override
    public AsyncExecRuntime fork() {
        return new InternalHttpAsyncExecRuntime(log, manager, connectionInitiator, pushHandlerFactory, versionPolicy);
    }

}
