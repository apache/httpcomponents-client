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

import org.apache.hc.client5.http.async.HttpAsyncClient;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * Base implementation of {@link HttpAsyncClient} that also implements {@link ModalCloseable}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public abstract class CloseableHttpAsyncClient implements HttpAsyncClient, ModalCloseable {

    public abstract void start();

    public abstract IOReactorStatus getStatus();

    public abstract void awaitShutdown(TimeValue waitTime) throws InterruptedException;

    public abstract void initiateShutdown();

    protected abstract <T> Future<T> doExecute(
            final HttpHost target,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context,
            final FutureCallback<T> callback);

    public final <T> Future<T> execute(
            final HttpHost target,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "Request producer");
        Args.notNull(responseConsumer, "Response consumer");
        return doExecute(target, requestProducer, responseConsumer, pushHandlerFactory, context, callback);
    }

    @Override
    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "Request producer");
        Args.notNull(responseConsumer, "Response consumer");
        return doExecute(null, requestProducer, responseConsumer, pushHandlerFactory, context, callback);
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "Request producer");
        Args.notNull(responseConsumer, "Response consumer");
        return execute(requestProducer, responseConsumer, null, context, callback);
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "Request producer");
        Args.notNull(responseConsumer, "Response consumer");
        return execute(requestProducer, responseConsumer, HttpClientContext.create(), callback);
    }

    public final Future<SimpleHttpResponse> execute(
            final SimpleHttpRequest request,
            final HttpContext context,
            final FutureCallback<SimpleHttpResponse> callback) {
        Args.notNull(request, "Request");
        return execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), context, callback);
    }

    public final Future<SimpleHttpResponse> execute(
            final SimpleHttpRequest request,
            final FutureCallback<SimpleHttpResponse> callback) {
        return execute(request, HttpClientContext.create(), callback);
    }

    public abstract void register(String hostname, String uriPattern, Supplier<AsyncPushConsumer> supplier);

    public final void register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        register(null, uriPattern, supplier);
    }

}
