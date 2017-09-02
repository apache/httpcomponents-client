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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.util.TimeValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class AsyncMainClientExec implements AsyncExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

    private final ConnectionKeepAliveStrategy keepAliveStrategy;
    private final UserTokenHandler userTokenHandler;

    AsyncMainClientExec(final ConnectionKeepAliveStrategy keepAliveStrategy, final UserTokenHandler userTokenHandler) {
        this.keepAliveStrategy = keepAliveStrategy;
        this.userTokenHandler = userTokenHandler;
    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        final String exchangeId = scope.exchangeId;
        final HttpRoute route = scope.route;
        final HttpClientContext clientContext = scope.clientContext;
        final AsyncExecRuntime execRuntime = scope.execRuntime;

        if (log.isDebugEnabled()) {
            log.debug(exchangeId + ": executing " + new RequestLine(request));
        }

        final AsyncClientExchangeHandler internalExchangeHandler = new AsyncClientExchangeHandler() {

            private final AtomicReference<AsyncDataConsumer> entityConsumerRef = new AtomicReference<>(null);

            @Override
            public void releaseResources() {
                final AsyncDataConsumer entityConsumer = entityConsumerRef.getAndSet(null);
                if (entityConsumer != null) {
                    entityConsumer.releaseResources();
                }
            }

            @Override
            public void failed(final Exception cause) {
                execRuntime.markConnectionNonReusable();
                asyncExecCallback.failed(cause);
            }

            @Override
            public void cancel() {
                failed(new InterruptedIOException());
            }

            @Override
            public void produceRequest(final RequestChannel channel) throws HttpException, IOException {
                channel.sendRequest(request, entityProducer);
            }

            @Override
            public int available() {
                return entityProducer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                entityProducer.produce(channel);
            }

            @Override
            public void consumeInformation(final HttpResponse response) throws HttpException, IOException {
            }

            @Override
            public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
                entityConsumerRef.set(asyncExecCallback.handleResponse(response, entityDetails));
                execRuntime.markConnectionReusable();
                final TimeValue duration = keepAliveStrategy.getKeepAliveDuration(response, clientContext);
                execRuntime.setConnectionValidFor(duration);
                Object userToken = clientContext.getUserToken();
                if (userToken == null) {
                    userToken = userTokenHandler.getUserToken(route, clientContext);
                    clientContext.setAttribute(HttpClientContext.USER_TOKEN, userToken);
                }
                if (userToken != null) {
                    execRuntime.setConnectionState(userToken);
                }
                if (entityDetails == null) {
                    if (!execRuntime.isConnectionReusable()) {
                        execRuntime.discardConnection();
                    } else {
                        execRuntime.validateConnection();
                    }
                    asyncExecCallback.completed();
                }
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                final AsyncDataConsumer entityConsumer = entityConsumerRef.get();
                if (entityConsumer != null) {
                    entityConsumer.updateCapacity(capacityChannel);
                } else {
                    capacityChannel.update(Integer.MAX_VALUE);
                }
            }

            @Override
            public int consume(final ByteBuffer src) throws IOException {
                final AsyncDataConsumer entityConsumer = entityConsumerRef.get();
                if (entityConsumer != null) {
                    return entityConsumer.consume(src);
                } else {
                    return Integer.MAX_VALUE;
                }
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                final AsyncDataConsumer entityConsumer = entityConsumerRef.getAndSet(null);
                if (entityConsumer != null) {
                    // Release connection early
                    execRuntime.releaseConnection();
                    entityConsumer.streamEnd(trailers);
                } else {
                    if (!execRuntime.isConnectionReusable()) {
                        execRuntime.discardConnection();
                    } else {
                        execRuntime.validateConnection();
                    }
                }
                asyncExecCallback.completed();
            }

        };

        execRuntime.execute(
                log.isDebugEnabled() ? new LoggingAsyncClientExchangeHandler(log, exchangeId, internalExchangeHandler) : internalExchangeHandler,
                clientContext);
    }

}
