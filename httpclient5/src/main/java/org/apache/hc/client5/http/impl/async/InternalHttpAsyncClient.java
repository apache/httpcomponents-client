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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncClientEndpoint;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.UserTokenHandler;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

class InternalHttpAsyncClient extends AbstractHttpAsyncClientBase {

    private final static AtomicLong COUNT = new AtomicLong(0);

    private final AsyncClientConnectionManager connmgr;
    private final HttpRoutePlanner routePlanner;
    private final ConnectionKeepAliveStrategy keepAliveStrategy;
    private final UserTokenHandler userTokenHandler;
    private final RequestConfig defaultConfig;
    private final List<Closeable> closeables;

    InternalHttpAsyncClient(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final IOReactorConfig reactorConfig,
            final ThreadFactory threadFactory,
            final ThreadFactory workerThreadFactory,
            final AsyncClientConnectionManager connmgr,
            final HttpRoutePlanner routePlanner,
            final ConnectionKeepAliveStrategy keepAliveStrategy,
            final UserTokenHandler userTokenHandler,
            final RequestConfig defaultConfig,
            final List<Closeable> closeables) throws IOReactorException {
        super(eventHandlerFactory, pushConsumerRegistry, reactorConfig, threadFactory, workerThreadFactory);
        this.connmgr = connmgr;
        this.routePlanner = routePlanner;
        this.keepAliveStrategy = keepAliveStrategy;
        this.userTokenHandler = userTokenHandler;
        this.defaultConfig = defaultConfig;
        this.closeables = closeables;
    }

    @Override
    public void close() {
        super.close();
        if (closeables != null) {
            for (final Closeable closeable: closeables) {
                try {
                    closeable.close();
                } catch (final IOException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private void leaseEndpoint(
            final HttpRoute route,
            final Object userToken,
            final HttpClientContext clientContext,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        final RequestConfig requestConfig = clientContext.getRequestConfig();
        connmgr.lease(route, userToken, requestConfig.getConnectTimeout(), TimeUnit.MILLISECONDS,
                new FutureCallback<AsyncConnectionEndpoint>() {

                    @Override
                    public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                        if (connectionEndpoint.isConnected()) {
                            callback.completed(connectionEndpoint);
                        } else {
                            connmgr.connect(
                                    connectionEndpoint,
                                    getConnectionInitiator(),
                                    requestConfig.getConnectTimeout(), TimeUnit.MILLISECONDS,
                                    clientContext,
                                    new FutureCallback<AsyncConnectionEndpoint>() {

                                        @Override
                                        public void completed(final AsyncConnectionEndpoint result) {
                                            callback.completed(result);
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
                    public void failed(final Exception ex) {
                        callback.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        callback.cancelled();
                    }

                });
    }

    @Override
    public Future<AsyncClientEndpoint> lease(
            final HttpHost host,
            final HttpContext context,
            final FutureCallback<AsyncClientEndpoint> callback) {
        Args.notNull(host, "Host");
        Args.notNull(context, "HTTP context");
        ensureRunning();
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final BasicFuture<AsyncClientEndpoint> future = new BasicFuture<>(callback);
        try {
            final HttpRoute route = routePlanner.determineRoute(host, clientContext);
            final Object userToken = clientContext.getUserToken();
            leaseEndpoint(route, userToken, clientContext, new FutureCallback<AsyncConnectionEndpoint>() {

                @Override
                public void completed(final AsyncConnectionEndpoint result) {
                    future.completed(new InternalAsyncClientEndpoint(route, result));
                }

                @Override
                public void failed(final Exception ex) {
                    future.failed(ex);
                }

                @Override
                public void cancelled() {
                    future.cancel(true);
                }

            });
        } catch (HttpException ex) {
            future.failed(ex);
        }
        return future;
    }

    @Override
    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        ensureRunning();
        final BasicFuture<T> future = new BasicFuture<>(callback);
        try {
            final HttpClientContext clientContext = HttpClientContext.adapt(context);
            final HttpRequest request = requestProducer.produceRequest();
            RequestConfig requestConfig = null;
            if (requestProducer instanceof Configurable) {
                requestConfig = ((Configurable) requestProducer).getConfig();
            }
            if (requestConfig != null) {
                clientContext.setRequestConfig(requestConfig);
            }
            final HttpHost target = routePlanner.determineTargetHost(request, clientContext);
            final HttpRoute route = routePlanner.determineRoute(target, clientContext);
            final Object userToken = clientContext.getUserToken();
            leaseEndpoint(route, userToken, clientContext, new FutureCallback<AsyncConnectionEndpoint>() {

                @Override
                public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                    final InternalAsyncClientEndpoint endpoint = new InternalAsyncClientEndpoint(route, connectionEndpoint);
                    endpoint.executeAndRelease(requestProducer, responseConsumer, clientContext, new FutureCallback<T>() {

                        @Override
                        public void completed(final T result) {
                            future.completed(result);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            future.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            future.cancel();
                        }

                    });

                }

                @Override
                public void failed(final Exception ex) {
                    future.failed(ex);
                }

                @Override
                public void cancelled() {
                    future.cancel();
                }

            });
        } catch (HttpException ex) {
            future.failed(ex);
        }
        return future;
    }

    private void setupContext(final HttpClientContext context) {
        if (context.getAttribute(HttpClientContext.REQUEST_CONFIG) == null) {
            context.setAttribute(HttpClientContext.REQUEST_CONFIG, defaultConfig);
        }
    }

    private class InternalAsyncClientEndpoint extends AsyncClientEndpoint {

        private final HttpRoute route;
        private final AsyncConnectionEndpoint connectionEndpoint;
        private final AtomicBoolean reusable;
        private final AtomicReference<Object> userTokenRef;
        private final AtomicLong keepAlive;
        private final AtomicBoolean released;

        InternalAsyncClientEndpoint(final HttpRoute route, final AsyncConnectionEndpoint connectionEndpoint) {
            this.route = route;
            this.connectionEndpoint = connectionEndpoint;
            this.reusable = new AtomicBoolean(true);
            this.keepAlive = new AtomicLong(Long.MAX_VALUE);
            this.userTokenRef = new AtomicReference<>(null);
            this.released = new AtomicBoolean(false);
        }

        @Override
        public void execute(final AsyncClientExchangeHandler exchangeHandler, final HttpContext context) {
            Asserts.check(!released.get(), ConnPoolSupport.getId(connectionEndpoint) + " endpoint has already been released");

            final HttpClientContext clientContext = HttpClientContext.adapt(context);
            setupContext(clientContext);

            connectionEndpoint.execute(new AsyncClientExchangeHandler() {

                private final String id = Long.toString(COUNT.incrementAndGet());

                void updateState() {
                    reusable.set(true);
                    Object userToken = clientContext.getUserToken();
                    if (userToken == null) {
                        userToken = userTokenHandler.getUserToken(route, context);
                        context.setAttribute(HttpClientContext.USER_TOKEN, userToken);
                    }
                    userTokenRef.set(userToken);
                }

                public void produceRequest(
                        final RequestChannel channel) throws HttpException, IOException {
                    exchangeHandler.produceRequest(log.isDebugEnabled() ? new RequestChannel() {

                        @Override
                        public void sendRequest(
                                final HttpRequest request,
                                final EntityDetails entityDetails) throws HttpException, IOException {
                            if (log.isDebugEnabled()) {
                                log.debug(ConnPoolSupport.getId(connectionEndpoint) + " exchange " + id  + ": request " + new RequestLine(request));
                            }
                            channel.sendRequest(request, entityDetails);
                        }

                    } : channel);
                }

                public int available() {
                    return exchangeHandler.available();
                }

                public void produce(final DataStreamChannel channel) throws IOException {
                    exchangeHandler.produce(channel);
                }

                public void consumeResponse(
                        final HttpResponse response,
                        final EntityDetails entityDetails) throws HttpException, IOException {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(connectionEndpoint) + " exchange " + id  + ": response " + new StatusLine(response));
                    }
                    exchangeHandler.consumeResponse(response, entityDetails);

                    keepAlive.set(keepAliveStrategy.getKeepAliveDuration(response, context));

                    if (entityDetails == null) {
                        updateState();
                        if (log.isDebugEnabled()) {
                            log.debug(ConnPoolSupport.getId(connectionEndpoint) + " exchange " + id  + ": completed");
                        }
                    }
                }

                public void consumeInformation(final HttpResponse response) throws HttpException, IOException {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(connectionEndpoint) + " exchange " + id  + ": intermediate response " + new StatusLine(response));
                    }
                    exchangeHandler.consumeInformation(response);
                }

                public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                    exchangeHandler.updateCapacity(capacityChannel);
                }

                public int consume(final ByteBuffer src) throws IOException {
                    return exchangeHandler.consume(src);
                }

                public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(connectionEndpoint) + " exchange " + id  + ": completed");
                    }
                    exchangeHandler.streamEnd(trailers);
                    updateState();
                }

                public void failed(final Exception cause) {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(connectionEndpoint) + " exchange " + id  + ": failed", cause);
                    }
                    reusable.set(false);
                    exchangeHandler.failed(cause);
                }

                public void cancel() {
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(connectionEndpoint) + " exchange " + id  + ": cancelled");
                    }
                    reusable.set(false);
                    exchangeHandler.cancel();
                }

                public void releaseResources() {
                    exchangeHandler.releaseResources();
                }

            }, clientContext);
        }

        private void closeEndpoint() {
            try {
                connectionEndpoint.close();
            } catch (IOException ex) {
                log.debug("I/O error closing connection endpoint: " + ex.getMessage(), ex);
            }
        }

        @Override
        public void releaseAndReuse() {
            if (released.compareAndSet(false, true)) {
                if (!reusable.get()) {
                    closeEndpoint();
                    connmgr.release(connectionEndpoint, null, -1L, TimeUnit.MILLISECONDS);
                } else {
                    connmgr.release(connectionEndpoint, userTokenRef.get(), keepAlive.get(), TimeUnit.MILLISECONDS);
                }
            }
        }

        @Override
        public void releaseAndDiscard() {
            if (released.compareAndSet(false, true)) {
                closeEndpoint();
                connmgr.release(connectionEndpoint, null, -1L, TimeUnit.MILLISECONDS);
            }
        }

    }

}
