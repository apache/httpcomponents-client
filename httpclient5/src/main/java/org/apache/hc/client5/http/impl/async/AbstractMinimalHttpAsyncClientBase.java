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

import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;

abstract class AbstractMinimalHttpAsyncClientBase extends AbstractHttpAsyncClientBase {

    AbstractMinimalHttpAsyncClientBase(
            final DefaultConnectingIOReactor ioReactor,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final ThreadFactory threadFactory) {
        super(ioReactor, pushConsumerRegistry, threadFactory);
    }

    @Override
    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        final ComplexFuture<T> future = new ComplexFuture<>(callback);
        execute(new BasicClientExchangeHandler<>(
                        requestProducer,
                        responseConsumer,
                        callback),
                context, future, new Supplier<T>() {

                    @Override
                    public T get() {
                        return responseConsumer.getResult();
                    }

                });
        return future;
    }

    public final <T extends AsyncClientExchangeHandler> Future<T> execute(
            final T exchangeHandler,
            final HttpContext context,
            final FutureCallback<T> callback) {
        final ComplexFuture<T> future = new ComplexFuture<>(callback);
        execute(exchangeHandler, context, future, new Supplier<T>() {

            @Override
            public T get() {
                return exchangeHandler;
            }

        });
        return future;
    }

    abstract <T> void execute(
            AsyncClientExchangeHandler exchangeHandler,
            HttpContext context,
            ComplexFuture<T> resultFuture,
            Supplier<T> resultSupplier);

}
