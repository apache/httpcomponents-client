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
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncClientEndpoint;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ComplexFuture;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

class MinimalHttpAsyncClient extends AbstractHttpAsyncClientBase {

    private final AsyncClientConnectionManager connmgr;

    public MinimalHttpAsyncClient(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final IOReactorConfig reactorConfig,
            final ThreadFactory threadFactory,
            final ThreadFactory workerThreadFactory,
            final AsyncClientConnectionManager connmgr) throws IOReactorException {
        super(eventHandlerFactory, pushConsumerRegistry, reactorConfig, threadFactory, workerThreadFactory);
        this.connmgr = connmgr;
    }

    private Future<AsyncConnectionEndpoint> leaseEndpoint(
            final HttpHost host,
            final int connectTimeout,
            final HttpClientContext clientContext,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        final ComplexFuture<AsyncConnectionEndpoint> resultFuture = new ComplexFuture<>(callback);
        final Future<AsyncConnectionEndpoint> leaseFuture = connmgr.lease(
                new HttpRoute(host), null,
                connectTimeout, TimeUnit.MILLISECONDS,
                new FutureCallback<AsyncConnectionEndpoint>() {

                    @Override
                    public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                        if (connectionEndpoint.isConnected()) {
                            resultFuture.completed(connectionEndpoint);
                        } else {
                            final Future<AsyncConnectionEndpoint> connectFuture = connmgr.connect(
                                    connectionEndpoint,
                                    getConnectionInitiator(),
                                    connectTimeout, TimeUnit.MILLISECONDS,
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

    @Override
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
        leaseEndpoint(host, requestConfig.getConnectTimeout(), clientContext,
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
    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        ensureRunning();
        final HttpRequest request = requestProducer.produceRequest();
        final HttpHost target = new HttpHost(request.getAuthority(), request.getScheme());
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        RequestConfig requestConfig = null;
        if (requestProducer instanceof Configurable) {
            requestConfig = ((Configurable) requestProducer).getConfig();
        }
        if (requestConfig != null) {
            clientContext.setRequestConfig(requestConfig);
        } else {
            requestConfig = clientContext.getRequestConfig();
        }
        final ComplexFuture<T> resultFuture = new ComplexFuture<>(callback);
        final Future<AsyncConnectionEndpoint> leaseFuture = leaseEndpoint(target, requestConfig.getConnectTimeout(), clientContext,
                new FutureCallback<AsyncConnectionEndpoint>() {

                    @Override
                    public void completed(final AsyncConnectionEndpoint connectionEndpoint) {
                        final InternalAsyncClientEndpoint endpoint = new InternalAsyncClientEndpoint(connectionEndpoint);
                        endpoint.executeAndRelease(requestProducer, responseConsumer, clientContext, new FutureCallback<T>() {

                            @Override
                            public void completed(final T result) {
                                resultFuture.completed(result);
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
                        resultFuture.setDependency(new Cancellable() {

                            @Override
                            public boolean cancel() {
                                final boolean active = !endpoint.isReleased();
                                endpoint.releaseAndDiscard();
                                return active;
                            }

                        });

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
        resultFuture.setDependency(leaseFuture);
        return resultFuture;
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
            connectionEndpoint.execute(exchangeHandler, context);
        }

        @Override
        public void releaseAndReuse() {
            if (released.compareAndSet(false, true)) {
                connmgr.release(connectionEndpoint, null, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void releaseAndDiscard() {
            if (released.compareAndSet(false, true)) {
                try {
                    connectionEndpoint.close();
                } catch (IOException ignore) {
                }
                connmgr.release(connectionEndpoint, null, -1L, TimeUnit.MILLISECONDS);
            }
        }

    }

}
