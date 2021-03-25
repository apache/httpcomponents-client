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

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.impl.classic.RequestFailedException;
import org.apache.hc.client5.http.impl.nio.MultihomeConnectionInitiator;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.ComplexCancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal implementation of HTTP/2 only {@link CloseableHttpAsyncClient}. This client
 * is optimized for HTTP/2 multiplexing message transport and does not support advanced
 * HTTP protocol functionality such as request execution via a proxy, state management,
 * authentication and request redirects.
 * <p>
 * Concurrent message exchanges with the same connection route executed by
 * this client will get automatically multiplexed over a single physical HTTP/2
 * connection.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public final class MinimalH2AsyncClient extends AbstractMinimalHttpAsyncClientBase {

    private static final Logger LOG = LoggerFactory.getLogger(MinimalH2AsyncClient.class);
    private final InternalH2ConnPool connPool;
    private final ConnectionInitiator connectionInitiator;

    MinimalH2AsyncClient(
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
                        LoggingIOSessionDecorator.INSTANCE,
                        LoggingExceptionCallback.INSTANCE,
                        null,
                        ioSession -> ioSession.enqueue(new ShutdownCommand(CloseMode.GRACEFUL), Command.Priority.IMMEDIATE)),
                pushConsumerRegistry,
                threadFactory);
        this.connectionInitiator = new MultihomeConnectionInitiator(getConnectionInitiator(), dnsResolver);
        this.connPool = new InternalH2ConnPool(this.connectionInitiator, object -> null, tlsStrategy);
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
            exchangeHandler.produceRequest((request, entityDetails, context1) -> {
                RequestConfig requestConfig = null;
                if (request instanceof Configurable) {
                    requestConfig = ((Configurable) request).getConfig();
                }
                if (requestConfig != null) {
                    clientContext.setRequestConfig(requestConfig);
                } else {
                    requestConfig = clientContext.getRequestConfig();
                }
                final Timeout connectTimeout = requestConfig.getConnectTimeout();
                final HttpHost target = new HttpHost(request.getScheme(), request.getAuthority());

                final Future<IOSession> sessionFuture = connPool.getSession(target, connectTimeout,
                    new FutureCallback<IOSession>() {

                    @Override
                    public void completed(final IOSession session) {
                        final AsyncClientExchangeHandler internalExchangeHandler = new AsyncClientExchangeHandler() {

                            @Override
                            public void releaseResources() {
                                exchangeHandler.releaseResources();
                            }

                            @Override
                            public void failed(final Exception cause) {
                                exchangeHandler.failed(cause);
                            }

                            @Override
                            public void cancel() {
                                failed(new RequestFailedException("Request aborted"));
                            }

                            @Override
                            public void produceRequest(
                                    final RequestChannel channel,
                                    final HttpContext context1) throws HttpException, IOException {
                                channel.sendRequest(request, entityDetails, context1);
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
                                    final HttpResponse response,
                                    final HttpContext context1) throws HttpException, IOException {
                                exchangeHandler.consumeInformation(response, context1);
                            }

                            @Override
                            public void consumeResponse(
                                    final HttpResponse response,
                                    final EntityDetails entityDetails,
                                    final HttpContext context1) throws HttpException, IOException {
                                exchangeHandler.consumeResponse(response, entityDetails, context1);
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
                                exchangeHandler.streamEnd(trailers);
                            }

                        };
                        if (LOG.isDebugEnabled()) {
                            final String exchangeId = ExecSupport.getNextExchangeId();
                            LOG.debug("{} executing message exchange {}", exchangeId, ConnPoolSupport.getId(session));
                            session.enqueue(
                                    new RequestExecutionCommand(
                                            new LoggingAsyncClientExchangeHandler(LOG, exchangeId, internalExchangeHandler),
                                            pushHandlerFactory,
                                            cancellable,
                                            clientContext),
                                    Command.Priority.NORMAL);
                        } else {
                            session.enqueue(
                                    new RequestExecutionCommand(
                                            internalExchangeHandler,
                                            pushHandlerFactory,
                                            cancellable,
                                            clientContext),
                                    Command.Priority.NORMAL);
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
                cancellable.setDependency(() -> sessionFuture.cancel(true));
            }, context);
        } catch (final HttpException | IOException | IllegalStateException ex) {
            exchangeHandler.failed(ex);
        }
        return cancellable;
    }

    /**
     * Sets {@link Resolver} for {@link ConnectionConfig} on a per host basis.
     *
     * @since 5.2
     */
    public void setConnectionConfigResolver(final Resolver<HttpHost, ConnectionConfig> connectionConfigResolver) {
        connPool.setConnectionConfigResolver(connectionConfigResolver);
    }

}
