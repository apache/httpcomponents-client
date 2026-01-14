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
package org.apache.hc.client5.http.fluent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.util.Args;

/**
 * Asynchronous executor for {@link Request}s.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class Async {

    private static final int DEFAULT_MAX_THREADS =
            Math.max(2, Math.min(32, Runtime.getRuntime().availableProcessors() * 2));

    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(0);

    private Executor executor;
    private volatile java.util.concurrent.Executor concurrentExec;
    private volatile ExecutorService ownedConcurrentExec;

    private int maxThreads = DEFAULT_MAX_THREADS;
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

    public static Async newInstance() {
        return new Async();
    }

    Async() {
        super();
        // Keep legacy behavior by default.
    }

    public Async maxThreads(final int maxThreads) {
        Args.positive(maxThreads, "maxThreads");
        this.maxThreads = maxThreads;
        rebuildOwnedExecutorIfActive();
        return this;
    }

    public Async queueCapacity(final int queueCapacity) {
        Args.positive(queueCapacity, "queueCapacity");
        this.queueCapacity = queueCapacity;
        rebuildOwnedExecutorIfActive();
        return this;
    }

    /**
     * Enables an owned bounded default executor for asynchronous request execution using the
     * current {@code maxThreads} and {@code queueCapacity} settings.
     *
     * @return this instance.
     * @since 5.7
     */
    public Async useDefaultExecutor() {
        return useDefaultExecutor(this.maxThreads, this.queueCapacity);
    }

    /**
     * Enables an owned bounded default executor for asynchronous request execution.
     *
     * @param maxThreads    maximum number of threads.
     * @param queueCapacity maximum number of queued tasks.
     * @return this instance.
     * @since 5.7
     */
    public Async useDefaultExecutor(final int maxThreads, final int queueCapacity) {
        Args.positive(maxThreads, "maxThreads");
        Args.positive(queueCapacity, "queueCapacity");
        this.maxThreads = maxThreads;
        this.queueCapacity = queueCapacity;

        shutdown();
        this.ownedConcurrentExec = createDefaultExecutor(this.maxThreads, this.queueCapacity);
        this.concurrentExec = this.ownedConcurrentExec;
        return this;
    }

    private void rebuildOwnedExecutorIfActive() {
        if (this.ownedConcurrentExec != null) {
            shutdown();
            this.ownedConcurrentExec = createDefaultExecutor(this.maxThreads, this.queueCapacity);
            this.concurrentExec = this.ownedConcurrentExec;
        }
    }

    private static ExecutorService createDefaultExecutor(final int maxThreads, final int queueCapacity) {
        final int instanceId = INSTANCE_COUNT.incrementAndGet();
        final DefaultThreadFactory threadFactory = new DefaultThreadFactory(
                "httpclient5-fluent-async-" + instanceId + "-",
                true);

        final ThreadPoolExecutor exec = new ThreadPoolExecutor(
                maxThreads,
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());

        exec.allowCoreThreadTimeOut(true);
        return exec;
    }

    public Async use(final Executor executor) {
        this.executor = executor;
        return this;
    }

    public Async use(final java.util.concurrent.Executor concurrentExec) {
        this.concurrentExec = concurrentExec;
        shutdown();
        return this;
    }

    /**
     * Shuts down resources owned by this instance, if any.
     * <p>
     * This method never attempts to shut down executors supplied via {@link #use(java.util.concurrent.Executor)}.
     *
     * @since 5.7
     */
    public void shutdown() {
        final ExecutorService exec = this.ownedConcurrentExec;
        if (exec != null) {
            this.ownedConcurrentExec = null;
            exec.shutdown();
        }
    }

    static class ExecRunnable<T> implements Runnable {

        private final BasicFuture<T> future;
        private final Request request;
        private final Executor executor;
        private final HttpClientResponseHandler<T> handler;

        ExecRunnable(
                final BasicFuture<T> future,
                final Request request,
                final Executor executor,
                final HttpClientResponseHandler<T> handler) {
            super();
            this.future = future;
            this.request = request;
            this.executor = executor;
            this.handler = handler;
        }

        @Override
        public void run() {
            try {
                final Response response = this.executor.execute(this.request);
                final T result = response.handleResponse(this.handler);
                this.future.completed(result);
            } catch (final Exception ex) {
                this.future.failed(ex);
            }
        }

    }

    public <T> Future<T> execute(
            final Request request, final HttpClientResponseHandler<T> handler, final FutureCallback<T> callback) {
        final BasicFuture<T> future = new BasicFuture<>(callback);
        final ExecRunnable<T> runnable = new ExecRunnable<>(
                future,
                request,
                this.executor != null ? this.executor : Executor.newInstance(),
                handler);

        final java.util.concurrent.Executor exec = this.concurrentExec;
        if (exec != null) {
            try {
                exec.execute(runnable);
            } catch (final RejectedExecutionException ex) {
                future.failed(ex);
            }
        } else {
            final Thread t = new Thread(runnable);
            t.setDaemon(true);
            t.start();
        }
        return future;
    }

    public <T> Future<T> execute(final Request request, final HttpClientResponseHandler<T> handler) {
        return execute(request, handler, null);
    }

    public Future<Content> execute(final Request request, final FutureCallback<Content> callback) {
        return execute(request, new ContentResponseHandler(), callback);
    }

    public Future<Content> execute(final Request request) {
        return execute(request, new ContentResponseHandler(), null);
    }

    /**
     * Executes the given request asynchronously and returns a {@link CompletableFuture} that completes
     * when the response has been fully received and converted by the given response handler.
     *
     * @param request the request to execute.
     * @param handler the response handler.
     * @param <T>     the handler result type.
     * @return a {@code CompletableFuture} producing the handler result.
     * @since 5.7
     */
    public <T> CompletableFuture<T> executeAsync(final Request request, final HttpClientResponseHandler<T> handler) {
        final CompletableFuture<T> cf = new CompletableFuture<>();
        execute(request, handler, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                cf.complete(result);
            }

            @Override
            public void failed(final Exception ex) {
                cf.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                cf.cancel(false);
            }

        });
        return cf;
    }

    /**
     * Executes the given request asynchronously and returns a {@link CompletableFuture} that completes
     * when the response has been fully received and converted by the given response handler. The given
     * callback is invoked on completion, failure, or cancellation.
     *
     * @param request  the request to execute.
     * @param handler  the response handler.
     * @param callback the callback to invoke on completion, failure, or cancellation; may be {@code null}.
     * @param <T>      the handler result type.
     * @return a {@code CompletableFuture} producing the handler result.
     * @since 5.7
     */
    public <T> CompletableFuture<T> executeAsync(
            final Request request, final HttpClientResponseHandler<T> handler, final FutureCallback<T> callback) {
        final CompletableFuture<T> cf = new CompletableFuture<>();
        execute(request, handler, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                if (callback != null) {
                    callback.completed(result);
                }
                cf.complete(result);
            }

            @Override
            public void failed(final Exception ex) {
                if (callback != null) {
                    callback.failed(ex);
                }
                cf.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                if (callback != null) {
                    callback.cancelled();
                }
                cf.cancel(false);
            }

        });
        return cf;
    }

    /**
     * Executes the given request asynchronously and returns a {@link CompletableFuture} that completes
     * when the response has been fully received and converted to {@link Content}.
     *
     * @param request the request to execute.
     * @return a {@code CompletableFuture} producing the response {@code Content}.
     * @since 5.7
     */
    public CompletableFuture<Content> executeAsync(final Request request) {
        return executeAsync(request, new ContentResponseHandler());
    }

    /**
     * Executes the given request asynchronously and returns a {@link CompletableFuture} that completes
     * when the response has been fully received and converted to {@link Content}. The given callback
     * is invoked on completion, failure, or cancellation.
     *
     * @param request  the request to execute.
     * @param callback the callback to invoke on completion, failure, or cancellation; may be {@code null}.
     * @return a {@code CompletableFuture} producing the response {@code Content}.
     * @since 5.7
     */
    public CompletableFuture<Content> executeAsync(final Request request, final FutureCallback<Content> callback) {
        return executeAsync(request, new ContentResponseHandler(), callback);
    }

}
