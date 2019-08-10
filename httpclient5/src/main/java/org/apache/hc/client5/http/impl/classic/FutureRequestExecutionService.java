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
package org.apache.hc.client5.http.impl.classic;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * This class schedules message execution execution and processing
 * as {@link FutureTask}s with the provided {@link ExecutorService}.
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class FutureRequestExecutionService implements Closeable {

    private final HttpClient httpclient;
    private final ExecutorService executorService;
    private final FutureRequestExecutionMetrics metrics = new FutureRequestExecutionMetrics();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Create a new FutureRequestExecutionService.
     *
     * @param httpclient
     *            you should tune your httpclient instance to match your needs. You should
     *            align the max number of connections in the pool and the number of threads
     *            in the executor; it doesn't make sense to have more threads than connections
     *            and if you have less connections than threads, the threads will just end up
     *            blocking on getting a connection from the pool.
     * @param executorService
     *            any executorService will do here. E.g.
     *            {@link java.util.concurrent.Executors#newFixedThreadPool(int)}
     */
    public FutureRequestExecutionService(
            final HttpClient httpclient,
            final ExecutorService executorService) {
        this.httpclient = httpclient;
        this.executorService = executorService;
    }

    /**
     * Schedule a request for execution.
     *
     * @param <T>
     *
     * @param request
     *            request to execute
     * @param HttpClientResponseHandler
     *            handler that will process the response.
     * @return HttpAsyncClientFutureTask for the scheduled request.
     */
    public <T> FutureTask<T> execute(
            final ClassicHttpRequest request,
            final HttpContext context,
            final HttpClientResponseHandler<T> HttpClientResponseHandler) {
        return execute(request, context, HttpClientResponseHandler, null);
    }

    /**
     * Schedule a request for execution.
     *
     * @param <T>
     *
     * @param request
     *            request to execute
     * @param context
     *            optional context; use null if not needed.
     * @param HttpClientResponseHandler
     *            handler that will process the response.
     * @param callback
     *            callback handler that will be called when the request is scheduled,
     *            started, completed, failed, or cancelled.
     * @return HttpAsyncClientFutureTask for the scheduled request.
     */
    public <T> FutureTask<T> execute(
            final ClassicHttpRequest request,
            final HttpContext context,
            final HttpClientResponseHandler<T> HttpClientResponseHandler,
            final FutureCallback<T> callback) {
        if(closed.get()) {
            throw new IllegalStateException("Close has been called on this httpclient instance.");
        }
        metrics.getScheduledConnections().incrementAndGet();
        final HttpRequestTaskCallable<T> callable = new HttpRequestTaskCallable<>(
                httpclient, request, context, HttpClientResponseHandler, callback, metrics);
        final HttpRequestFutureTask<T> httpRequestFutureTask = new HttpRequestFutureTask<>(
                request, callable);
        executorService.execute(httpRequestFutureTask);

        return httpRequestFutureTask;
    }

    /**
     * @return metrics gathered for this instance.
     * @see FutureRequestExecutionMetrics
     */
    public FutureRequestExecutionMetrics metrics() {
        return metrics;
    }

    @Override
    public void close() throws IOException {
        closed.set(true);
        executorService.shutdownNow();
        if (httpclient instanceof Closeable) {
            ((Closeable) httpclient).close();
        }
    }
}
