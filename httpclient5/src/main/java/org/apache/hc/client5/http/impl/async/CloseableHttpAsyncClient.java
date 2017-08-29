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

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.HttpAsyncClient;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;

/**
 * Base implementation of {@link HttpAsyncClient} that also implements {@link Closeable}.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class CloseableHttpAsyncClient implements HttpAsyncClient, Closeable {

    public abstract void start();

    public abstract IOReactorStatus getStatus();

    public abstract List<ExceptionEvent> getExceptionLog();

    public abstract void awaitShutdown(TimeValue waitTime) throws InterruptedException;

    public abstract void initiateShutdown();

    public abstract void shutdown(ShutdownType shutdownType);

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, HttpClientContext.create(), callback);
    }

    public final void register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        register(null, uriPattern, supplier);
    }

    public final Future<SimpleHttpResponse> execute(
            final SimpleHttpRequest request,
            final HttpContext context,
            final FutureCallback<SimpleHttpResponse> callback) {
        final BasicFuture<SimpleHttpResponse> future = new BasicFuture<>(callback);
        execute(new SimpleRequestProducer(request), new SimpleResponseConsumer(), context, new FutureCallback<SimpleHttpResponse>() {

            @Override
            public void completed(final SimpleHttpResponse response) {
                future.completed(response);
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

    public final Future<SimpleHttpResponse> execute(
            final SimpleHttpRequest request,
            final FutureCallback<SimpleHttpResponse> callback) {
        return execute(request, HttpClientContext.create(), callback);
    }

}
