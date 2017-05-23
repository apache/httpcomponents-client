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
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.TimeValue;
import org.apache.logging.log4j.Logger;

class AsyncExecRuntimeImpl implements AsyncExecRuntime {

    private final Logger log;
    private final AsyncClientConnectionManager manager;
    private final ConnectionInitiator connectionInitiator;
    private final HttpVersionPolicy versionPolicy;
    private final AtomicReference<AsyncConnectionEndpoint> endpointRef;
    private volatile boolean reusable;
    private volatile Object state;
    private volatile TimeValue validDuration;

    AsyncExecRuntimeImpl(
            final Logger log,
            final AsyncClientConnectionManager manager,
            final ConnectionInitiator connectionInitiator,
            final HttpVersionPolicy versionPolicy) {
        super();
        this.log = log;
        this.manager = manager;
        this.connectionInitiator = connectionInitiator;
        this.versionPolicy = versionPolicy;
        this.endpointRef = new AtomicReference<>(null);
        this.validDuration = TimeValue.NEG_ONE_MILLISECONDS;
    }

    @Override
    public boolean isConnectionAcquired() {
        return endpointRef.get() != null;
    }

    @Override
    public void acquireConnection(
            final HttpRoute route,
            final Object object,
            final HttpClientContext context,
            final FutureCallback<AsyncExecRuntime> callback) {
        if (endpointRef.get() == null) {
            state = object;
            final RequestConfig requestConfig = context.getRequestConfig();
            manager.lease(route, object, requestConfig.getConnectionRequestTimeout(), new FutureCallback<AsyncConnectionEndpoint>() {

                @Override
                public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                    endpointRef.set(connectionEndpoint);
                    reusable = connectionEndpoint.isConnected();
                    callback.completed(AsyncExecRuntimeImpl.this);
                }

                @Override
                public void failed(final Exception ex) {
                    callback.failed(ex);
                }

                @Override
                public void cancelled() {
                    callback.cancelled();
                }
            });
        } else {
            callback.completed(this);
        }
    }

    @Override
    public void releaseConnection() {
        final AsyncConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            if (reusable) {
                if (log.isDebugEnabled()) {
                    log.debug(ConnPoolSupport.getId(endpoint) + ": releasing valid endpoint");
                }
                manager.release(endpoint, state, validDuration);
            } else {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(endpoint) + ": releasing invalid endpoint");
                    }
                    endpoint.close();
                } catch (final IOException ex) {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(endpoint) + ": " + ex.getMessage(), ex);
                    }
                } finally {
                    manager.release(endpoint, null, TimeValue.ZERO_MILLISECONDS);
                }
            }
        }
    }

    @Override
    public void discardConnection() {
        final AsyncConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            try {
                endpoint.shutdown();
                if (log.isDebugEnabled()) {
                    log.debug(ConnPoolSupport.getId(endpoint) + ": discarding endpoint");
                }
            } catch (final IOException ex) {
                if (log.isDebugEnabled()) {
                    log.debug(ConnPoolSupport.getId(endpoint) + ": " + ex.getMessage(), ex);
                }
            } finally {
                manager.release(endpoint, null, TimeValue.ZERO_MILLISECONDS);
            }
        }
    }

    AsyncConnectionEndpoint ensureValid() {
        final AsyncConnectionEndpoint endpoint = endpointRef.get();
        if (endpoint == null) {
            throw new IllegalStateException("Endpoint not acquired / already released");
        }
        return endpoint;
    }

    @Override
    public boolean isConnected() {
        final AsyncConnectionEndpoint endpoint = endpointRef.get();
        return endpoint != null && endpoint.isConnected();
    }

    @Override
    public void disconnect() {
        final AsyncConnectionEndpoint endpoint = endpointRef.get();
        if (endpoint != null) {
            try {
                endpoint.close();
            } catch (final IOException ex) {
                if (log.isDebugEnabled()) {
                    log.debug(ConnPoolSupport.getId(endpoint) + ": " + ex.getMessage(), ex);
                }
                discardConnection();
            }
        }

    }

    @Override
    public void connect(
            final HttpClientContext context,
            final FutureCallback<AsyncExecRuntime> callback) {
        final AsyncConnectionEndpoint endpoint = ensureValid();
        if (endpoint.isConnected()) {
            callback.completed(this);
        } else {
            final RequestConfig requestConfig = context.getRequestConfig();
            manager.connect(
                    endpoint,
                    connectionInitiator,
                    requestConfig.getConnectTimeout(),
                    versionPolicy,
                    context,
                    new FutureCallback<AsyncConnectionEndpoint>() {

                        @Override
                        public void completed(final AsyncConnectionEndpoint endpoint) {
                            final TimeValue socketTimeout = requestConfig.getSocketTimeout();
                            if (TimeValue.isPositive(socketTimeout)) {
                                endpoint.setSocketTimeout(socketTimeout.toMillisIntBound());
                            }
                            callback.completed(AsyncExecRuntimeImpl.this);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            callback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            callback.cancelled();
                        }

            });
        }

    }

    @Override
    public void upgradeTls(final HttpClientContext context) {
        final AsyncConnectionEndpoint endpoint = ensureValid();
        manager.upgrade(endpoint, versionPolicy, context);
    }

    @Override
    public void execute(final AsyncClientExchangeHandler exchangeHandler, final HttpClientContext context) {
        final AsyncConnectionEndpoint endpoint = ensureValid();
        if (endpoint.isConnected()) {
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": executing " + ConnPoolSupport.getId(exchangeHandler));
            }
            endpoint.execute(exchangeHandler, context);
        } else {
            connect(context, new FutureCallback<AsyncExecRuntime>() {

                @Override
                public void completed(final AsyncExecRuntime runtime) {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(endpoint) + ": executing " + ConnPoolSupport.getId(exchangeHandler));
                    }
                    try {
                        endpoint.execute(exchangeHandler, context);
                    } catch (RuntimeException ex) {
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

    }

    @Override
    public boolean validateConnection() {
        final AsyncConnectionEndpoint endpoint = endpointRef.get();
        return endpoint != null && endpoint.isConnected();
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

}
