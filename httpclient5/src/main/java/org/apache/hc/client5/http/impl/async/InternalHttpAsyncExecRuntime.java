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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.EndpointInfo;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

class InternalHttpAsyncExecRuntime implements AsyncExecRuntime {

    static class ReUseData {

        final Object state;
        final TimeValue validDuration;

        ReUseData(final Object state, final TimeValue validDuration) {
            this.state = state;
            this.validDuration = validDuration;
        }

    }

    private static final ConcurrentMap<AsyncClientConnectionManager, AtomicInteger> QUEUE_COUNTERS = new ConcurrentHashMap<>();

    private final Logger log;
    private final AsyncClientConnectionManager manager;
    private final ConnectionInitiator connectionInitiator;
    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;
    /**
     * @deprecated TLS should be configured by the connection manager
     */
    @Deprecated
    private final TlsConfig tlsConfig;
    private final AtomicReference<AsyncConnectionEndpoint> endpointRef;
    private final AtomicReference<ReUseData> reuseDataRef;
    private final int maxQueued;
    private final AtomicInteger sharedQueued;

    InternalHttpAsyncExecRuntime(
            final Logger log,
            final AsyncClientConnectionManager manager,
            final ConnectionInitiator connectionInitiator,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final TlsConfig tlsConfig) {
        this(log, manager, connectionInitiator, pushHandlerFactory, tlsConfig, -1);
    }

    InternalHttpAsyncExecRuntime(
            final Logger log,
            final AsyncClientConnectionManager manager,
            final ConnectionInitiator connectionInitiator,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final TlsConfig tlsConfig,
            final int maxQueued) {
        super();
        this.log = log;
        this.manager = manager;
        this.connectionInitiator = connectionInitiator;
        this.pushHandlerFactory = pushHandlerFactory;
        this.tlsConfig = tlsConfig;
        this.endpointRef = new AtomicReference<>();
        this.reuseDataRef = new AtomicReference<>();
        this.maxQueued = maxQueued;
        this.sharedQueued = maxQueued > 0 ? QUEUE_COUNTERS.computeIfAbsent(manager, m -> new AtomicInteger(0)) : null;
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
            final RequestConfig requestConfig = context.getRequestConfigOrDefault();
            final Timeout connectionRequestTimeout = requestConfig.getConnectionRequestTimeout();
            if (log.isDebugEnabled()) {
                log.debug("{} acquiring endpoint ({})", id, connectionRequestTimeout);
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
                            if (log.isDebugEnabled()) {
                                log.debug("{} acquired endpoint {}", id, ConnPoolSupport.getId(connectionEndpoint));
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
                log.debug("{} endpoint closed", ConnPoolSupport.getId(endpoint));
            }
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("{} discarding endpoint", ConnPoolSupport.getId(endpoint));
            }
            manager.release(endpoint, null, TimeValue.ZERO_MILLISECONDS);
        }
    }

    @Override
    public void releaseEndpoint() {
        final AsyncConnectionEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            final ReUseData reUseData = reuseDataRef.getAndSet(null);
            if (reUseData != null) {
                if (log.isDebugEnabled()) {
                    log.debug("{} releasing valid endpoint", ConnPoolSupport.getId(endpoint));
                }
                manager.release(endpoint, reUseData.state, reUseData.validDuration);
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
        if (reuseDataRef != null) {
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
        final RequestConfig requestConfig = context.getRequestConfigOrDefault();
        @SuppressWarnings("deprecation") final Timeout connectTimeout = requestConfig.getConnectTimeout();
        if (log.isDebugEnabled()) {
            log.debug("{} connecting endpoint ({})", ConnPoolSupport.getId(endpoint), connectTimeout);
        }
        return Operations.cancellable(manager.connect(
                endpoint,
                connectionInitiator,
                connectTimeout,
                tlsConfig,
                context,
                new CallbackContribution<AsyncConnectionEndpoint>(callback) {

                    @Override
                    public void completed(final AsyncConnectionEndpoint endpoint) {
                        if (log.isDebugEnabled()) {
                            log.debug("{} endpoint connected", ConnPoolSupport.getId(endpoint));
                        }
                        if (callback != null) {
                            callback.completed(InternalHttpAsyncExecRuntime.this);
                        }
                    }

                }));

    }

    @Override
    public void disconnectEndpoint() {
        final AsyncConnectionEndpoint endpoint = endpointRef.get();
        if (endpoint != null) {
            endpoint.close(CloseMode.GRACEFUL);
        }
    }

    @Override
    public void upgradeTls(final HttpClientContext context) {
        upgradeTls(context, null);
    }

    @Override
    public void upgradeTls(final HttpClientContext context, final FutureCallback<AsyncExecRuntime> callback) {
        final AsyncConnectionEndpoint endpoint = ensureValid();
        if (log.isDebugEnabled()) {
            log.debug("{} upgrading endpoint", ConnPoolSupport.getId(endpoint));
        }
        manager.upgrade(endpoint, tlsConfig, context, new CallbackContribution<AsyncConnectionEndpoint>(callback) {

            @Override
            public void completed(final AsyncConnectionEndpoint endpoint) {
                if (callback != null) {
                    callback.completed(InternalHttpAsyncExecRuntime.this);
                }
            }

        });
    }

    @Override
    public EndpointInfo getEndpointInfo() {
        final AsyncConnectionEndpoint endpoint = endpointRef.get();
        return endpoint != null ? endpoint.getInfo() : null;
    }

    private boolean tryAcquireSlot() {
        if (sharedQueued == null) {
            return true;
        }
        for (; ; ) {
            final int q = sharedQueued.get();
            if (q >= maxQueued) {
                return false;
            }
            if (sharedQueued.compareAndSet(q, q + 1)) {
                return true;
            }
        }
    }

    private void releaseSlot() {
        if (sharedQueued != null) {
            sharedQueued.decrementAndGet();
        }
    }

    private AsyncClientExchangeHandler guard(final AsyncClientExchangeHandler h) {
        return new AsyncClientExchangeHandler() {
            private final AtomicBoolean done = new AtomicBoolean(false);

            private void finish() {
                if (done.compareAndSet(false, true)) {
                    releaseSlot();
                }
            }

            @Override
            public void failed(final Exception cause) {
                try {
                    h.failed(cause);
                } finally {
                    finish();
                }
            }

            @Override
            public void cancel() {
                try {
                    h.cancel();
                } finally {
                    finish();
                }
            }

            @Override
            public void releaseResources() {
                try {
                    h.releaseResources();
                } finally {
                    finish();
                }
            }

            @Override
            public void produceRequest(final RequestChannel channel, final HttpContext ctx) throws HttpException, IOException {
                h.produceRequest(channel, ctx);
            }

            @Override
            public int available() {
                return h.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                h.produce(channel);
            }

            @Override
            public void consumeInformation(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
                h.consumeInformation(response, context);
            }

            @Override
            public void consumeResponse(final HttpResponse response, final EntityDetails entity, final HttpContext context) throws HttpException, IOException {
                h.consumeResponse(response, entity, context);
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                h.updateCapacity(capacityChannel);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
                h.consume(src);
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                h.streamEnd(trailers);
            }
        };
    }

    @Override
    public Cancellable execute(
            final String id, final AsyncClientExchangeHandler exchangeHandler, final HttpClientContext context) {
        final AsyncConnectionEndpoint endpoint = ensureValid();
        if (sharedQueued != null && !tryAcquireSlot()) {
            exchangeHandler.failed(new RejectedExecutionException("Execution pipeline queue limit reached (max=" + maxQueued + ")"));
            return Operations.nonCancellable();
        }
        final AsyncClientExchangeHandler guardedHandler = guard(exchangeHandler);
        if (endpoint.isConnected()) {
            if (log.isDebugEnabled()) {
                log.debug("{} start execution {}", ConnPoolSupport.getId(endpoint), id);
            }
            final RequestConfig requestConfig = context.getRequestConfigOrDefault();
            final Timeout responseTimeout = requestConfig.getResponseTimeout();
            if (responseTimeout != null) {
                endpoint.setSocketTimeout(responseTimeout);
            }
            endpoint.execute(id, guardedHandler, pushHandlerFactory, context);
            if (context.getRequestConfigOrDefault().isHardCancellationEnabled()) {
                return () -> {
                    guardedHandler.cancel();
                    return true;
                };
            }
        } else {
            connectEndpoint(context, new FutureCallback<AsyncExecRuntime>() {

                @Override
                public void completed(final AsyncExecRuntime runtime) {
                    if (log.isDebugEnabled()) {
                        log.debug("{} start execution {}", ConnPoolSupport.getId(endpoint), id);
                    }
                    try {
                        endpoint.execute(id, guardedHandler, pushHandlerFactory, context);
                    } catch (final RuntimeException ex) {
                        failed(ex);
                    }
                }

                @Override
                public void failed(final Exception ex) {
                    guardedHandler.failed(ex);
                }

                @Override
                public void cancelled() {
                    guardedHandler.failed(new InterruptedIOException());
                }

            });
        }
        return Operations.nonCancellable();
    }

    @Override
    public void markConnectionReusable(final Object newState, final TimeValue newValidDuration) {
        reuseDataRef.set(new ReUseData(newState, newValidDuration));
    }

    @Override
    public void markConnectionNonReusable() {
        reuseDataRef.set(null);
    }

    @Override
    public AsyncExecRuntime fork() {
        return new InternalHttpAsyncExecRuntime(log, manager, connectionInitiator, pushHandlerFactory, tlsConfig, maxQueued);
    }

}
