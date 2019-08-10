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

package org.apache.hc.client5.http.nio;

import java.io.IOException;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;

/**
 * Client connection endpoint that can be used to execute message exchanges.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class AsyncConnectionEndpoint implements ModalCloseable {

    /**
     * Initiates a message exchange using the given handler.
     *
     * @param id unique operation ID or {@code null}.
     * @param exchangeHandler the message exchange handler.
     * @param pushHandlerFactory the push handler factory.
     * @param context the execution context.
     */
    public abstract void execute(
            String id,
            AsyncClientExchangeHandler exchangeHandler,
            HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            HttpContext context);

    /**
     * Determines if the connection to the remote endpoint is still open and valid.
     */
    public abstract boolean isConnected();

    /**
     * Sets socket timeout.
     *
     * @param timeout the socket timeout.
     */
    public abstract void setSocketTimeout(Timeout timeout);

    @Override
    public final void close() throws IOException {
        close(CloseMode.GRACEFUL);
    }

    /**
     * Initiates a message exchange using the given handler.
     *
     * @param id unique operation ID or {@code null}.
     * @param exchangeHandler the message exchange handler.
     * @param context the execution context.
     */
    public void execute(
            final String id,
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpContext context) {
        execute(id, exchangeHandler, null, context);
    }

    /**
     * Initiates message exchange using the given request producer and response consumer.
     *
     * @param id unique operation ID or {@code null}.
     * @param requestProducer the request producer.
     * @param responseConsumer the response consumer.
     * @param pushHandlerFactory the push handler factory.
     * @param context the execution context.
     * @param callback the result callback.
     * @param <T> the result representation.
     * @return the result future.
     */
    public final <T> Future<T> execute(
            final String id,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context,
            final FutureCallback<T> callback) {
        final BasicFuture<T> future = new BasicFuture<>(callback);
        execute(id, new BasicClientExchangeHandler<>(requestProducer, responseConsumer,
                        new FutureCallback<T>() {

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

                        }),
                pushHandlerFactory,
                context != null ? context : HttpCoreContext.create());
        return future;
    }

    /**
     * Initiates message exchange using the given request producer and response consumer.
     *
     * @param id unique operation ID or {@code null}.
     * @param requestProducer the request producer.
     * @param responseConsumer the response consumer.
     * @param context the execution context.
     * @param callback the result callback.
     * @param <T> the result representation.
     * @return the result future.
     */
    public final <T> Future<T> execute(
            final String id,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return execute(id, requestProducer, responseConsumer, null, context, callback);
    }

    /**
     * Initiates message exchange using the given request producer and response consumer.
     *
     * @param id unique operation ID or {@code null}.
     * @param requestProducer the request producer.
     * @param responseConsumer the response consumer.
     * @param pushHandlerFactory the push handler factory.
     * @param callback the result callback.
     * @param <T> the result representation.
     * @return the result future.
     */
    public final <T> Future<T> execute(
            final String id,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final FutureCallback<T> callback) {
        return execute(id, requestProducer, responseConsumer, pushHandlerFactory, null, callback);
    }

    /**
     * Initiates message exchange using the given request producer and response consumer.
     *
     * @param id unique operation ID or {@code null}.
     * @param requestProducer the request producer.
     * @param responseConsumer the response consumer.
     * @param callback the result callback.
     * @param <T> the result representation.
     * @return the result future.
     */
    public final <T> Future<T> execute(
            final String id,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return execute(id, requestProducer, responseConsumer, null, null, callback);
    }

}
