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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.impl.classic.RequestFailedException;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.ComplexCancellable;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Minimal implementation of {@link CloseableHttpAsyncClient}. This client is
 * optimized for HTTP/1.1 and HTTP/2 message transport and does not support
 * advanced HTTP protocol functionality such as request execution via a proxy,
 * state management, authentication and request redirects.
 * <p>
 * Concurrent message exchanges executed by this client will get assigned to
 * separate connections leased from the connection pool.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public final class MinimalHttpAsyncClient extends AbstractMinimalHttpAsyncClientBase {

    private final AsyncClientConnectionManager manager;
    private final SchemePortResolver schemePortResolver;
    private final HttpVersionPolicy versionPolicy;

    MinimalHttpAsyncClient(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final HttpVersionPolicy versionPolicy,
            final IOReactorConfig reactorConfig,
            final ThreadFactory threadFactory,
            final ThreadFactory workerThreadFactory,
            final AsyncClientConnectionManager manager,
            final SchemePortResolver schemePortResolver) {
        super(new DefaultConnectingIOReactor(
                eventHandlerFactory,
                reactorConfig,
                workerThreadFactory,
                LoggingIOSessionDecorator.INSTANCE,
                LoggingExceptionCallback.INSTANCE,
                null,
                new Callback<IOSession>() {

                    @Override
                    public void execute(final IOSession ioSession) {
                        ioSession.enqueue(new ShutdownCommand(CloseMode.GRACEFUL), Command.Priority.NORMAL);
                    }

                }),
                pushConsumerRegistry,
                threadFactory);
        this.manager = manager;
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
    }

    private Future<AsyncConnectionEndpoint> leaseEndpoint(
            final HttpHost host,
            final Timeout connectionRequestTimeout,
            final Timeout connectTimeout,
            final HttpClientContext clientContext,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        final HttpRoute route = new HttpRoute(RoutingSupport.normalize(host, schemePortResolver));
        final ComplexFuture<AsyncConnectionEndpoint> resultFuture = new ComplexFuture<>(callback);
        final String exchangeId = ExecSupport.getNextExchangeId();
        final Future<AsyncConnectionEndpoint> leaseFuture = manager.lease(
                exchangeId,
                route,
                null,
                connectionRequestTimeout,
                new FutureCallback<AsyncConnectionEndpoint>() {

                    @Override
                    public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                        if (connectionEndpoint.isConnected()) {
                            resultFuture.completed(connectionEndpoint);
                        } else {
                            final Future<AsyncConnectionEndpoint> connectFuture = manager.connect(
                                    connectionEndpoint,
                                    getConnectionInitiator(),
                                    connectTimeout,
                                    versionPolicy,
                                    clientContext,
                                    new FutureCallback<AsyncConnectionEndpoint>() {

                                        @Override
                                        public void completed(final AsyncConnectionEndpoint result) {
                                            resultFuture.completed(result);
                                        }

                                        @Override
                                        public void failed(final Exception ex) {
                                            resultFuture.failed(ex);
                                        }

                                        @Override
                                        public void cancelled() {
                                            resultFuture.cancel(true);
                                        }

                                    });
                            resultFuture.setDependency(connectFuture);
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
        resultFuture.setDependency(leaseFuture);
        return resultFuture;
    }

    public final Future<AsyncClientEndpoint> lease(
            final HttpHost host,
            final FutureCallback<AsyncClientEndpoint> callback) {
        return lease(host, HttpClientContext.create(), callback);
    }

    public Future<AsyncClientEndpoint> lease(
            final HttpHost host,
            final HttpContext context,
            final FutureCallback<AsyncClientEndpoint> callback) {
        Args.notNull(host, "Host");
        Args.notNull(context, "HTTP context");
        final BasicFuture<AsyncClientEndpoint> future = new BasicFuture<>(callback);
        if (!isRunning()) {
            future.failed(new CancellationException("Connection lease cancelled"));
            return future;
        }
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final RequestConfig requestConfig = clientContext.getRequestConfig();
        final Timeout connectionRequestTimeout = requestConfig.getConnectionRequestTimeout();
        final Timeout connectTimeout = requestConfig.getConnectTimeout();
        leaseEndpoint(
                host,
                connectionRequestTimeout,
                connectTimeout,
                clientContext,
                new FutureCallback<AsyncConnectionEndpoint>() {

                    @Override
                    public void completed(final AsyncConnectionEndpoint result) {
                        future.completed(new InternalAsyncClientEndpoint(result));
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
        return future;
    }

    @Override
    public Cancellable execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context) {
        final ComplexCancellable cancellable = new ComplexCancellable();
        try {
            if (!isRunning()) {
                throw new CancellationException("Request execution cancelled");
            }
            final HttpClientContext clientContext = context != null ? HttpClientContext.adapt(context) : HttpClientContext.create();
            exchangeHandler.produceRequest(new RequestChannel() {

                @Override
                public void sendRequest(
                        final HttpRequest request,
                        final EntityDetails entityDetails,
                        final HttpContext context) throws HttpException, IOException {
                    RequestConfig requestConfig = null;
                    if (request instanceof Configurable) {
                        requestConfig = ((Configurable) request).getConfig();
                    }
                    if (requestConfig != null) {
                        clientContext.setRequestConfig(requestConfig);
                    } else {
                        requestConfig = clientContext.getRequestConfig();
                    }
                    final Timeout connectionRequestTimeout = requestConfig.getConnectionRequestTimeout();
                    final Timeout connectTimeout = requestConfig.getConnectTimeout();
                    final Timeout responseTimeout = requestConfig.getResponseTimeout();
                    final HttpHost target = new HttpHost(request.getScheme(), request.getAuthority());

                    final Future<AsyncConnectionEndpoint> leaseFuture = leaseEndpoint(
                            target,
                            connectionRequestTimeout,
                            connectTimeout,
                            clientContext,
                            new FutureCallback<AsyncConnectionEndpoint>() {

                                @Override
                                public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                                    final InternalAsyncClientEndpoint endpoint = new InternalAsyncClientEndpoint(connectionEndpoint);
                                    final AtomicInteger messageCountDown = new AtomicInteger(2);
                                    final AsyncClientExchangeHandler internalExchangeHandler = new AsyncClientExchangeHandler() {

                                        @Override
                                        public void releaseResources() {
                                            try {
                                                exchangeHandler.releaseResources();
                                            } finally {
                                                endpoint.releaseAndDiscard();
                                            }
                                        }

                                        @Override
                                        public void failed(final Exception cause) {
                                            try {
                                                exchangeHandler.failed(cause);
                                            } finally {
                                                endpoint.releaseAndDiscard();
                                            }
                                        }

                                        @Override
                                        public void cancel() {
                                            failed(new RequestFailedException("Request aborted"));
                                        }

                                        @Override
                                        public void produceRequest(
                                                final RequestChannel channel,
                                                final HttpContext context) throws HttpException, IOException {
                                            channel.sendRequest(request, entityDetails, context);
                                            if (entityDetails == null) {
                                                messageCountDown.decrementAndGet();
                                            }
                                        }

                                        @Override
                                        public int available() {
                                            return exchangeHandler.available();
                                        }

                                        @Override
                                        public void produce(final DataStreamChannel channel) throws IOException {
                                            exchangeHandler.produce(new DataStreamChannel() {

                                                @Override
                                                public void requestOutput() {
                                                    channel.requestOutput();
                                                }

                                                @Override
                                                public int write(final ByteBuffer src) throws IOException {
                                                    return channel.write(src);
                                                }

                                                @Override
                                                public void endStream(final List<? extends Header> trailers) throws IOException {
                                                    channel.endStream(trailers);
                                                    if (messageCountDown.decrementAndGet() <= 0) {
                                                        endpoint.releaseAndReuse();
                                                    }
                                                }

                                                @Override
                                                public void endStream() throws IOException {
                                                    channel.endStream();
                                                    if (messageCountDown.decrementAndGet() <= 0) {
                                                        endpoint.releaseAndReuse();
                                                    }
                                                }

                                            });
                                        }

                                        @Override
                                        public void consumeInformation(
                                                final HttpResponse response,
                                                final HttpContext context) throws HttpException, IOException {
                                            exchangeHandler.consumeInformation(response, context);
                                        }

                                        @Override
                                        public void consumeResponse(
                                                final HttpResponse response,
                                                final EntityDetails entityDetails,
                                                final HttpContext context) throws HttpException, IOException {
                                            exchangeHandler.consumeResponse(response, entityDetails, context);
                                            if (response.getCode() >= HttpStatus.SC_CLIENT_ERROR) {
                                                messageCountDown.decrementAndGet();
                                            }
                                            if (entityDetails == null) {
                                                if (messageCountDown.decrementAndGet() <= 0) {
                                                    endpoint.releaseAndReuse();
                                                }
                                            }
                                        }

                                        @Override
                                        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                                            exchangeHandler.updateCapacity(capacityChannel);
                                        }

                                        @Override
                                        public void consume(final ByteBuffer src) throws IOException {
                                            exchangeHandler.consume(src);
                                        }

                                        @Override
                                        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                                            if (messageCountDown.decrementAndGet() <= 0) {
                                                endpoint.releaseAndReuse();
                                            }
                                            exchangeHandler.streamEnd(trailers);
                                        }

                                    };
                                    if (responseTimeout != null) {
                                        endpoint.setSocketTimeout(responseTimeout);
                                    }
                                    endpoint.execute(internalExchangeHandler, pushHandlerFactory, clientContext);
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    exchangeHandler.failed(ex);
                                }

                                @Override
                                public void cancelled() {
                                    exchangeHandler.cancel();
                                }

                            });

                    cancellable.setDependency(new Cancellable() {

                        @Override
                        public boolean cancel() {
                            return leaseFuture.cancel(true);
                        }

                    });
                }
            }, context);

        } catch (final HttpException | IOException | IllegalStateException ex) {
            exchangeHandler.failed(ex);
        }
        return cancellable;
    }

    private class InternalAsyncClientEndpoint extends AsyncClientEndpoint {

        private final AsyncConnectionEndpoint connectionEndpoint;
        private final AtomicBoolean released;

        InternalAsyncClientEndpoint(final AsyncConnectionEndpoint connectionEndpoint) {
            this.connectionEndpoint = connectionEndpoint;
            this.released = new AtomicBoolean(false);
        }

        boolean isReleased() {
            return released.get();
        }

        @Override
        public boolean isConnected() {
            return !isReleased() && connectionEndpoint.isConnected();
        }

        @Override
        public void execute(
                final AsyncClientExchangeHandler exchangeHandler,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final HttpContext context) {
            Asserts.check(!released.get(), "Endpoint has already been released");

            final String exchangeId = ExecSupport.getNextExchangeId();
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(connectionEndpoint) + ": executing message exchange " + exchangeId);
                connectionEndpoint.execute(
                        exchangeId,
                        new LoggingAsyncClientExchangeHandler(log, exchangeId, exchangeHandler),
                        pushHandlerFactory,
                        context);
            } else {
                connectionEndpoint.execute(exchangeId, exchangeHandler, context);
            }
        }

        public void setSocketTimeout(final Timeout timeout) {
            connectionEndpoint.setSocketTimeout(timeout);
        }

        @Override
        public void releaseAndReuse() {
            if (released.compareAndSet(false, true)) {
                manager.release(connectionEndpoint, null, TimeValue.NEG_ONE_MILLISECOND);
            }
        }

        @Override
        public void releaseAndDiscard() {
            if (released.compareAndSet(false, true)) {
                Closer.closeQuietly(connectionEndpoint);
                manager.release(connectionEndpoint, null, TimeValue.ZERO_MILLISECONDS);
            }
        }

    }

}
