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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.impl.classic.RequestFailedException;
import org.apache.hc.client5.http.impl.nio.MultuhomeConnectionInitiator;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.nio.pool.H2ConnPool;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;

public final class MinimalHttp2AsyncClient extends AbstractMinimalHttpAsyncClientBase {

    private final H2ConnPool connPool;
    private final ConnectionInitiator connectionInitiator;

    MinimalHttp2AsyncClient(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final IOReactorConfig reactorConfig,
            final ThreadFactory threadFactory,
            final ThreadFactory workerThreadFactory,
            final DnsResolver dnsResolver,
            final TlsStrategy tlsStrategy) {
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
        this.connectionInitiator = new MultuhomeConnectionInitiator(getConnectionInitiator(), dnsResolver);
        this.connPool = new H2ConnPool(this.connectionInitiator, new Resolver<HttpHost, InetSocketAddress>() {

            @Override
            public InetSocketAddress resolve(final HttpHost object) {
                return null;
            }

        }, tlsStrategy);
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

                    final Future<IOSession> sessionFuture = connPool.getSession(target, connectTimeout, new FutureCallback<IOSession>() {

                        @Override
                        public void completed(final IOSession session) {
                            final AsyncClientExchangeHandler internalExchangeHandler = new AsyncClientExchangeHandler() {

                                @Override
                                public void releaseResources() {
                                    exchangeHandler.releaseResources();
                                }

                                @Override
                                public void failed(final Exception cause) {
                                    try {
                                        exchangeHandler.failed(cause);
                                    } finally {
                                        resultFuture.failed(cause);
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
                                }

                                @Override
                                public int available() {
                                    return exchangeHandler.available();
                                }

                                @Override
                                public void produce(final DataStreamChannel channel) throws IOException {
                                    exchangeHandler.produce(channel);
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
                                    if (entityDetails == null) {
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
                                    exchangeHandler.streamEnd(trailers);
                                    resultFuture.completed(resultSupplier.get());
                                }

                            };
                            if (log.isDebugEnabled()) {
                                final String exchangeId = ExecSupport.getNextExchangeId();
                                log.debug(ConnPoolSupport.getId(session) + ": executing message exchange " + exchangeId);
                                session.addLast(new ExecutionCommand(
                                        new LoggingAsyncClientExchangeHandler(log, exchangeId, internalExchangeHandler),
                                        resultFuture, clientContext));
                            } else {
                                session.addLast(new ExecutionCommand(internalExchangeHandler, resultFuture, clientContext));
                            }
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
                    if (resultFuture != null) {
                        resultFuture.setDependency(sessionFuture);
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

}
