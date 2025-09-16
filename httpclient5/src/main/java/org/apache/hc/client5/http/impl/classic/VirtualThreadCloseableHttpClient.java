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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * {@code CloseableHttpClient} decorator that runs the classic (blocking) transport
 * on JDK&nbsp;21 <em>virtual threads</em> while preserving the classic client contract.
 *
 * <p><strong>Execution model</strong></p>
 * <ul>
 *   <li>By default, only the transport runs on a virtual thread; the response handler
 *       (for {@code execute(..., HttpClientResponseHandler)}) runs on the caller thread,
 *       exactly like the base implementation.</li>
 *   <li>If {@link #handlerOnVirtualThread} is set to {@code true}, both the transport and
 *       the response handler run inside a virtual thread, and the entity is consumed there
 *       before returning.</li>
 * </ul>
 *
 * <p><strong>Error semantics</strong></p>
 * <ul>
 *   <li>Transport failures propagate as {@link IOException}.</li>
 *   <li>{@link RuntimeException} and {@link Error} propagate unchanged.</li>
 *   <li>Executor rejection is mapped to {@code IOException("client closed")}.</li>
 *   <li>Thread interruption cancels the VT task, restores the interrupt flag, and throws
 *       {@link InterruptedIOException}.</li>
 * </ul>
 *
 * <p><strong>Lifecycle</strong></p>
 * <ul>
 *   <li>If this client owns the provided executor, it is shut down on {@link #close()} /
 *       {@link #close(CloseMode)} with a bounded wait.</li>
 * </ul>
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.SAFE)
@Experimental
public final class VirtualThreadCloseableHttpClient extends CloseableHttpClient {

    /**
     * Underlying classic client used for actual I/O.
     */
    private final CloseableHttpClient delegate;

    /**
     * Executor that creates/runs virtual threads (typically per-task VT executor).
     */
    private final ExecutorService vtExecutor;

    /**
     * Whether this instance manages (shuts down) {@link #vtExecutor} on close.
     */
    private final boolean shutdownVtExec;

    /**
     * Maximum time to wait for executor termination on graceful close.
     */
    private final TimeValue vtShutdownWait;

    /**
     * If {@code true}, both transport and {@link HttpClientResponseHandler} execute on a VT.
     * If {@code false}, only the transport runs on a VT and the handler runs on the caller thread.
     */
    private final boolean handlerOnVirtualThread;

    /**
     * Constructs a VT client that <em>owns</em> the executor, waits up to 2 seconds for graceful shutdown,
     * and keeps the response handler on the caller thread.
     */
    public VirtualThreadCloseableHttpClient(
            final CloseableHttpClient delegate,
            final ExecutorService vtExecutor) {
        this(delegate, vtExecutor, true, null, false);
    }

    /**
     * Convenience constructor matching the builder usage: handler remains on caller thread.
     */
    public VirtualThreadCloseableHttpClient(
            final CloseableHttpClient delegate,
            final ExecutorService vtExecutor,
            final boolean shutdownVtExec,
            final TimeValue vtShutdownWait) {
        this(delegate, vtExecutor, shutdownVtExec, vtShutdownWait, false);
    }

    /**
     * Full constructor with explicit ownership, shutdown wait and handler execution mode.
     *
     * @param delegate               underlying client used to perform I/O
     * @param vtExecutor             executor that creates and runs virtual threads
     * @param shutdownVtExec         whether this client should shut down {@code vtExecutor} on close
     * @param vtShutdownWait         maximum time to await executor termination on graceful close;
     *                               if {@code null}, defaults to 2 seconds
     * @param handlerOnVirtualThread whether to run the response handler on a VT as well
     */
    public VirtualThreadCloseableHttpClient(
            final CloseableHttpClient delegate,
            final ExecutorService vtExecutor,
            final boolean shutdownVtExec,
            final TimeValue vtShutdownWait,
            final boolean handlerOnVirtualThread) {
        this.delegate = Args.notNull(delegate, "delegate");
        this.vtExecutor = Args.notNull(vtExecutor, "vtExecutor");
        this.shutdownVtExec = shutdownVtExec;
        this.vtShutdownWait = vtShutdownWait != null ? vtShutdownWait : TimeValue.ofSeconds(2);
        this.handlerOnVirtualThread = handlerOnVirtualThread;
    }

    /**
     * Executes the request on a virtual thread by delegating to {@link #delegate}'s
     * {@link CloseableHttpClient#executeOpen(HttpHost, ClassicHttpRequest, HttpContext)}.
     * The method blocks until the VT task completes, preserving classic blocking semantics.
     */
    @Override
    protected CloseableHttpResponse doExecute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {

        final Future<ClassicHttpResponse> f;
        try {
            f = vtExecutor.submit(() -> delegate.executeOpen(target, request, context));
        } catch (final RejectedExecutionException rex) {
            throw new IOException("client closed", rex);
        }

        try {
            final ClassicHttpResponse rsp = f.get();
            return CloseableHttpResponse.adapt(rsp);
        } catch (final InterruptedException ie) {
            f.cancel(true);
            Thread.currentThread().interrupt();
            final InterruptedIOException iox = new InterruptedIOException("interrupted");
            iox.initCause(ie);
            throw iox;
        } catch (final ExecutionException ee) {
            final Throwable cause = ee.getCause();
            if (cause instanceof RejectedExecutionException) {
                throw new IOException("client closed", cause);
            }
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IOException(cause);
        }
    }

    /**
     * Optionally runs both transport and the response handler on a virtual thread.
     * If {@link #handlerOnVirtualThread} is {@code false}, defers to the base implementation
     * which keeps the handler on the caller thread (while transport still runs on a VT via {@link #doExecute}).
     */
    @Override
    public <T> T execute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context,
            final HttpClientResponseHandler<? extends T> responseHandler) throws IOException {

        Objects.requireNonNull(responseHandler, "Response handler");

        if (!handlerOnVirtualThread) {
            // Transport still runs on VT via doExecute(); handler runs on caller thread.
            return super.execute(target, request, context, responseHandler);
        }

        final Future<T> f;
        try {
            f = vtExecutor.submit(() -> {
                final ClassicHttpResponse rsp = delegate.executeOpen(target, request, context);
                try (final CloseableHttpResponse ch = CloseableHttpResponse.adapt(rsp)) {
                    try {
                        final T out = responseHandler.handleResponse(ch);
                        EntityUtils.consume(ch.getEntity()); // salvage connection
                        return out;
                    } catch (final HttpException t) {
                        try {
                            EntityUtils.consume(ch.getEntity());
                        } catch (final Exception ignore) {
                        }
                        throw new ClientProtocolException(t);
                    } catch (final IOException | RuntimeException t) {
                        try {
                            EntityUtils.consume(ch.getEntity());
                        } catch (final Exception ignore) {
                        }
                        throw t;
                    }
                }
            });
        } catch (final RejectedExecutionException rex) {
            throw new IOException("client closed", rex);
        }

        try {
            return f.get();
        } catch (final InterruptedException ie) {
            f.cancel(true);
            Thread.currentThread().interrupt();
            final InterruptedIOException iox = new InterruptedIOException("interrupted");
            iox.initCause(ie);
            throw iox;
        } catch (final ExecutionException ee) {
            final Throwable c = ee.getCause();
            if (c instanceof RejectedExecutionException) {
                throw new IOException("client closed", c);
            }
            if (c instanceof IOException) {
                throw (IOException) c;
            }
            if (c instanceof RuntimeException) {
                throw (RuntimeException) c;
            }
            if (c instanceof Error) {
                throw (Error) c;
            }
            throw new IOException(c);
        }
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (shutdownVtExec && vtExecutor != null) {
            if (closeMode == CloseMode.IMMEDIATE) {
                vtExecutor.shutdownNow();
            } else {
                vtExecutor.shutdown();
                try {
                    vtExecutor.awaitTermination(
                            vtShutdownWait.getDuration(),
                            vtShutdownWait.getTimeUnit());
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        delegate.close(closeMode);
    }

    @Override
    public void close() throws IOException {
        try {
            if (shutdownVtExec && vtExecutor != null) {
                vtExecutor.shutdown();
                try {
                    vtExecutor.awaitTermination(
                            vtShutdownWait.getDuration(),
                            vtShutdownWait.getTimeUnit());
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    vtExecutor.shutdownNow(); // harmless if already terminated
                }
            }
        } finally {
            delegate.close();
        }
    }
}
