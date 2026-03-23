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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InternalTestHttpAsyncExecRuntime extends InternalHttpAsyncExecRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(InternalTestHttpAsyncExecRuntime.class);

    private final AtomicBoolean cancelled;

    public InternalTestHttpAsyncExecRuntime(final AsyncClientConnectionManager manager,
                                            final ConnectionInitiator connectionInitiator,
                                            final TlsConfig tlsConfig) {
        super(LOG, manager, connectionInitiator, null, tlsConfig, -1, new AtomicInteger());
        this.cancelled = new AtomicBoolean();
    }

    public Future<Boolean> leaseAndConnect(final HttpHost target, final HttpClientContext context) {
        final BasicFuture<Boolean> resultFuture = new BasicFuture<>(null);
        acquireEndpoint("test", new HttpRoute(target), null, context, new FutureContribution<AsyncExecRuntime>(resultFuture) {

            @Override
            public void completed(final AsyncExecRuntime runtime) {
                if (!runtime.isEndpointConnected()) {
                    runtime.connectEndpoint(context, new FutureContribution<AsyncExecRuntime>(resultFuture) {

                        @Override
                        public void completed(final AsyncExecRuntime runtime) {
                            resultFuture.completed(true);
                        }

                    });
                } else {
                    resultFuture.completed(true);
                }
            }

        });
        return resultFuture;
    }

    @Override
    public Cancellable execute(final String id, final AsyncClientExchangeHandler exchangeHandler, final HttpClientContext context) {
        return super.execute(id, new AsyncClientExchangeHandler() {

            public void cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    exchangeHandler.cancel();
                }
            }

            public void failed(final Exception cause) {
                exchangeHandler.failed(cause);
            }

            public void produceRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
                exchangeHandler.produceRequest(channel, context);
            }

            public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context) throws HttpException, IOException {
                exchangeHandler.consumeResponse(response, entityDetails, context);
            }

            public void consumeInformation(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
                exchangeHandler.consumeInformation(response, context);
            }

            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                exchangeHandler.updateCapacity(capacityChannel);
            }

            public void consume(final ByteBuffer src) throws IOException {
                exchangeHandler.consume(src);
            }

            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                exchangeHandler.streamEnd(trailers);
            }

            public void releaseResources() {
                exchangeHandler.releaseResources();
            }

            public int available() {
                return exchangeHandler.available();
            }

            public void produce(final DataStreamChannel channel) throws IOException {
                exchangeHandler.produce(channel);
            }

        }, context);
    }

    public boolean isAborted() {
        return cancelled.get();
    }

}
