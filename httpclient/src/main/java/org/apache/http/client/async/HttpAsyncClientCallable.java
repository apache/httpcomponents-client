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
package org.apache.http.client.async;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.HttpContext;

/**
 * Implementation of Callable that is wrapped with a {@link HttpAsyncClientFutureTask} by
 * {@link HttpAsyncClientWithFuture}. The callable orchestrates the invocation of
 * {@link HttpClient#execute(HttpUriRequest, ResponseHandler, HttpContext)} and callbacks in
 * {@link HttpAsyncClientCallback}.
 *
 * @param <V>
 *            type returned by the responseHandler
 */
final class HttpAsyncClientCallable<V> implements Callable<V> {

    private final HttpUriRequest request;

    private final HttpClient httpclient;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    final long scheduled = System.currentTimeMillis();
    long started = -1;
    long ended = -1;

    private final HttpContext context;
    private final ResponseHandler<V> responseHandler;
    private final FutureCallback<V> callback;

    private final ConnectionMetrics metrics;

    HttpAsyncClientCallable(
            final HttpClient httpClient,
            final HttpUriRequest request,
            final HttpContext context,
            final ResponseHandler<V> responseHandler,
            final FutureCallback<V> callback,
            final ConnectionMetrics metrics) {
        this.httpclient = httpClient;
        this.responseHandler = responseHandler;
        this.request = request;
        this.context = context;
        this.callback = callback;
        this.metrics = metrics;
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.Callable#call()
     */
    public V call() throws Exception {
        if (!cancelled.get()) {
            try {
                metrics.activeConnections.incrementAndGet();
                started = System.currentTimeMillis();
                try {
                    metrics.scheduledConnections.decrementAndGet();
                    final V result = httpclient.execute(request, responseHandler, context);
                    ended = System.currentTimeMillis();
                    metrics.successfulConnections.increment(started);
                    if (callback != null) {
                        callback.completed(result);
                    }
                    return result;
                } catch (final Exception e) {
                    metrics.failedConnections.increment(started);
                    ended = System.currentTimeMillis();
                    if (callback != null) {
                        callback.failed(e);
                    }
                    throw e;
                }
            } finally {
                metrics.requests.increment(started);
                metrics.tasks.increment(started);
                metrics.activeConnections.decrementAndGet();
            }
        } else {
            throw new IllegalStateException("call has been cancelled for request " + request.getURI());
        }
    }

    public void cancel() {
        cancelled.set(true);
        if (callback != null) {
            callback.cancelled();
        }
    }
}