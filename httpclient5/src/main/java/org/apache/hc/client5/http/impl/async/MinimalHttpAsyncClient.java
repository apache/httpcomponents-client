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
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.impl.classic.RequestFailedException;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public final class MinimalHttpAsyncClient extends AbstractMinimalHttpAsyncClientBase {

    private final AsyncClientConnectionManager connmgr;
    private final HttpVersionPolicy versionPolicy;

    MinimalHttpAsyncClient(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final HttpVersionPolicy versionPolicy,
            final IOReactorConfig reactorConfig,
            final ThreadFactory threadFactory,
            final ThreadFactory workerThreadFactory,
            final AsyncClientConnectionManager connmgr) {
        super(new DefaultConnectingIOReactor(
                eventHandlerFactory,
                reactorConfig,
                workerThreadFactory,
                null,
                null,
                new Callback<IOSession>() {

                    @Override
                    public void execute(final IOSession ioSession) {
                        ioSession.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
                    }

                }),
                pushConsumerRegistry,
                threadFactory);
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.connmgr = connmgr;
    }

    private Future<AsyncConnectionEndpoint> leaseEndpoint(
            final HttpHost host,
            final Timeout connectTimeout,
            final HttpClientContext clientContext,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        final ComplexFuture<AsyncConnectionEndpoint> resultFuture = new ComplexFuture<>(callback);
        final Future<AsyncConnectionEndpoint> leaseFuture = connmgr.lease(
                new HttpRoute(host), null, connectTimeout,
                new FutureCallback<AsyncConnectionEndpoint>() {

                    @Override
                    public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                        if (connectionEndpoint.isConnected()) {
                            resultFuture.completed(connectionEndpoint);
                        } else {
                            final Future<AsyncConnectionEndpoint> connectFuture = connmgr.connect(
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
        ensureRunning();
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final RequestConfig requestConfig = clientContext.getRequestConfig();
        final BasicFuture<AsyncClientEndpoint> future = new BasicFuture<>(callback);
        leaseEndpoint(host, requestConfig.getConnectionTimeout(), clientContext, new FutureCallback<AsyncConnectionEndpoint>() {

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
    <T> void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpContext context,
            final ComplexFuture<T> resultFuture,
            final Supplier<T> resultSupplier) {
        ensureRunning();
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        try {
            exchangeHandler.produceRequest(new RequestChannel() {

                @Override
                public void sendRequest(
                        final HttpRequest request,
                        final EntityDetails entityDetails) throws HttpException, IOException {
                    RequestConfig requestConfig = null;
                    if (request instanceof Configurable) {
                        requestConfig = ((Configurable) request).getConfig();
                    }
                    if (requestConfig != null) {
                        clientContext.setRequestConfig(requestConfig);
                    } else {
                        requestConfig = clientContext.getRequestConfig();
                    }
                    final Timeout connectTimeout = requestConfig.getConnectionTimeout();
                    final HttpHost target = new HttpHost(request.getAuthority(), request.getScheme());

                    final Future<AsyncConnectionEndpoint> leaseFuture = leaseEndpoint(target, connectTimeout, clientContext,
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
                                                final RequestChannel channel) throws HttpException, IOException {
                                            channel.sendRequest(request, entityDetails);
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
                                                final HttpResponse response) throws HttpException, IOException {
                                            exchangeHandler.consumeInformation(response);
                                        }

                                        @Override
                                        public void consumeResponse(
                                                final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
                                            exchangeHandler.consumeResponse(response, entityDetails);
                                            if (response.getCode() >= HttpStatus.SC_CLIENT_ERROR) {
                                                messageCountDown.decrementAndGet();
                                            }
                                            if (entityDetails == null) {
                                                if (messageCountDown.decrementAndGet() <= 0) {
                                                    endpoint.releaseAndReuse();
                                                }
                                                resultFuture.completed(resultSupplier.get());
                                            }
                                        }

                                        @Override
                                        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                                            exchangeHandler.updateCapacity(capacityChannel);
                                        }

                                        @Override
                                        public int consume(final ByteBuffer src) throws IOException {
                                            return exchangeHandler.consume(src);
                                        }

                                        @Override
                                        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                                            if (messageCountDown.decrementAndGet() <= 0) {
                                                endpoint.releaseAndReuse();
                                            }
                                            exchangeHandler.streamEnd(trailers);
                                            resultFuture.completed(resultSupplier.get());
                                        }

                                    };
                                    endpoint.execute(internalExchangeHandler, clientContext);
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    resultFuture.failed(ex);
                                }

                                @Override
                                public void cancelled() {
                                    resultFuture.cancel();
                                }

                            });
                    if (resultFuture != null) {
                        resultFuture.setDependency(leaseFuture);
                    }
                }
            });

        } catch (final HttpException | IOException ex) {
            try {
                exchangeHandler.failed(ex);
            } finally {
                resultFuture.failed(ex);
            }
        }
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
        public void execute(final AsyncClientExchangeHandler exchangeHandler, final HttpContext context) {
            Asserts.check(!released.get(), "Endpoint has already been released");

            if (log.isDebugEnabled()) {
                final String exchangeId = ExecSupport.getNextExchangeId();
                log.debug(ConnPoolSupport.getId(connectionEndpoint) + ": executing message exchange " + exchangeId);
                connectionEndpoint.execute(
                        new LoggingAsyncClientExchangeHandler(log, exchangeId, exchangeHandler),
                        context);
            } else {
                connectionEndpoint.execute(exchangeHandler, context);
            }
        }

        @Override
        public void releaseAndReuse() {
            if (released.compareAndSet(false, true)) {
                connmgr.release(connectionEndpoint, null, TimeValue.NEG_ONE_MILLISECONDS);
            }
        }

        @Override
        public void releaseAndDiscard() {
            if (released.compareAndSet(false, true)) {
                try {
                    connectionEndpoint.close();
                } catch (final IOException ignore) {
                }
                connmgr.release(connectionEndpoint, null, TimeValue.ZERO_MILLISECONDS);
            }
        }

    }

}
