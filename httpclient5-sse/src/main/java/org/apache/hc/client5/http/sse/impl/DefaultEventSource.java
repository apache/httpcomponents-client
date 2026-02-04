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
package org.apache.hc.client5.http.sse.impl;

import static org.apache.hc.core5.http.ContentType.TEXT_EVENT_STREAM;

import java.io.InterruptedIOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.sse.BackoffStrategy;
import org.apache.hc.client5.http.sse.EventSource;
import org.apache.hc.client5.http.sse.EventSourceConfig;
import org.apache.hc.client5.http.sse.EventSourceListener;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link EventSource} implementation that manages the SSE connection lifecycle:
 * establishing the connection, parsing events, handling failures, and performing
 * bounded, policy-driven reconnects.
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Builds and executes an HTTP GET with {@code Accept: text/event-stream}.</li>
 *   <li>Parses SSE using either a char-based or byte-based parser as configured
 *       by {@link SseParser}.</li>
 *   <li>Tracks {@code Last-Event-ID} and forwards events to the user listener
 *       on a caller-provided or inline executor.</li>
 *   <li>Applies {@link BackoffStrategy} with optional server-provided hints
 *       ({@code retry:} field and {@code Retry-After} header) to schedule reconnects.</li>
 *   <li>Honors a maximum reconnect count and emits {@link EventSourceListener#onClosed()}
 *       exactly once at the end of the lifecycle.</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * <p>Instances are safe for typical usage: public methods are idempotent and guarded by atomics.
 * Callbacks are dispatched on {@code callbackExecutor} (inline by default) and must not block.</p>
 *
 * <p><strong>Internal:</strong> this class is not part of the public API and can change without notice.</p>
 *
 * @since 5.7
 */
@Internal
public final class DefaultEventSource implements EventSource {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEventSource.class);

    /**
     * Scalable shared scheduler used when callers do not provide their own.
     * Uses a small daemon pool; canceled tasks are removed to reduce heap churn.
     */
    private static final ScheduledExecutorService SHARED_SCHED;

    static {
        final int nThreads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        final ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(
                nThreads, new DefaultThreadFactory("hc-sse", true));
        exec.setRemoveOnCancelPolicy(true);
        exec.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        SHARED_SCHED = exec;
    }

    private final CloseableHttpAsyncClient client;
    private final URI uri;
    private final Map<String, String> headers;
    private final EventSourceListener listener;

    private final ScheduledExecutorService scheduler;
    private final boolean ownScheduler;
    private final Executor callbackExecutor;
    private final BackoffStrategy backoff;
    private final int maxReconnects;
    private final SseParser parser;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean closedOnce = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private volatile String lastEventId;
    /**
     * Sticky retry from SSE {@code retry:} field (ms); {@code -1} if not set.
     */
    private volatile long stickyRetryMs = -1L;
    /**
     * One-shot hint from HTTP {@code Retry-After} (ms); {@code -1} if absent.
     */
    private volatile long retryAfterHintMs = -1L;

    private final AtomicInteger attempts = new AtomicInteger(0);
    private volatile long previousDelayMs = 0L;
    private volatile Future<?> inFlight;

    /**
     * Creates a new {@code DefaultEventSource} using the shared scheduler, inline callback execution,
     * default config, and char-based parser.
     *
     * @param client   non-null async client
     * @param uri      non-null SSE endpoint
     * @param headers  initial headers (copied)
     * @param listener listener to receive events; may be {@code null} for a no-op
     */
    DefaultEventSource(final CloseableHttpAsyncClient client,
                       final URI uri,
                       final Map<String, String> headers,
                       final EventSourceListener listener) {
        this(client, uri, headers, listener, null, null, null, SseParser.CHAR);
    }

    /**
     * Creates a new {@code DefaultEventSource} with full control over scheduling, callback dispatch,
     * reconnect policy, and parser selection.
     *
     * @param client           non-null async client
     * @param uri              non-null SSE endpoint
     * @param headers          initial headers (copied)
     * @param listener         listener to receive events; may be {@code null} for a no-op
     * @param scheduler        optional scheduler; if {@code null}, a shared pool is used
     * @param callbackExecutor optional executor for listener callbacks; if {@code null}, runs inline
     * @param config           optional configuration; if {@code null}, {@link EventSourceConfig#DEFAULT} is used
     * @param parser           parser strategy ({@link SseParser#CHAR} or {@link SseParser#BYTE}); defaults to CHAR if {@code null}
     */
    public DefaultEventSource(final CloseableHttpAsyncClient client,
                       final URI uri,
                       final Map<String, String> headers,
                       final EventSourceListener listener,
                       final ScheduledExecutorService scheduler,
                       final Executor callbackExecutor,
                       final EventSourceConfig config,
                       final SseParser parser) {
        this.client = Objects.requireNonNull(client, "client");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.headers = new ConcurrentHashMap<>(Objects.requireNonNull(headers, "headers"));
        this.listener = listener != null ? listener : (id, type, data) -> { /* no-op */ };

        if (scheduler != null) {
            this.scheduler = scheduler;
        } else {
            this.scheduler = SHARED_SCHED;
        }
        this.ownScheduler = false;

        this.callbackExecutor = callbackExecutor != null ? callbackExecutor : Runnable::run;

        final EventSourceConfig cfg = (config != null) ? config : EventSourceConfig.DEFAULT;
        this.backoff = cfg.backoff;
        this.maxReconnects = cfg.maxReconnects;
        this.parser = parser != null ? parser : SseParser.CHAR;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Idempotent. Resets retry state and attempts the first connection immediately.</p>
     *
     */
    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            attempts.set(0);
            previousDelayMs = 0;
            connect(0L);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Idempotent. Cancels any in-flight exchange, shuts down the owned scheduler (if any),
     * and ensures {@link EventSourceListener#onClosed()} is invoked exactly once.</p>
     *
     */
    @Override
    public void cancel() {
        final Future<?> f = inFlight;
        if (f != null) {
            f.cancel(true);
        }
        if (cancelled.compareAndSet(false, true)) {
            connected.set(false);
            if (ownScheduler) {
                try {
                    scheduler.shutdownNow();
                } catch (final Exception ignore) {
                }
            }
            notifyClosedOnce();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String lastEventId() {
        return lastEventId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLastEventId(final String id) {
        this.lastEventId = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(final String name, final String value) {
        headers.put(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeHeader(final String name) {
        headers.remove(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getHeaders() {
        return new ConcurrentHashMap<>(headers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Schedules or immediately performs a connection attempt.
     *
     * @param delayMs delay in milliseconds; non-positive runs immediately
     */
    private void connect(final long delayMs) {
        if (cancelled.get()) {
            return;
        }
        final Runnable task = this::doConnect;
        try {
            if (delayMs <= 0L) {
                task.run();
            } else {
                scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
            }
        } catch (final RejectedExecutionException e) {
            if (!cancelled.get()) {
                dispatch(() -> listener.onFailure(e, false));
                notifyClosedOnce();
            }
        }
    }

    /**
     * Builds the request, installs the response consumer, and executes the exchange.
     *
     * <p>Completion/failure callbacks determine whether to reconnect based on
     * {@link #willReconnectNext()} and {@link #scheduleReconnect()}.</p>
     *
     */
    private void doConnect() {
        if (cancelled.get()) {
            return;
        }

        final SimpleRequestBuilder rb = SimpleRequestBuilder.get(uri);
        rb.setHeader(HttpHeaders.ACCEPT, TEXT_EVENT_STREAM.getMimeType());
        rb.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        if (lastEventId != null) {
            rb.setHeader("Last-Event-ID", lastEventId);
        }
        for (final Map.Entry<String, String> e : headers.entrySet()) {
            rb.setHeader(e.getKey(), e.getValue());
        }
        final SimpleHttpRequest req = rb.build();

        final AsyncResponseConsumer<Void> consumer = getAsyncResponseConsumer();

        inFlight = client.execute(SimpleRequestProducer.create(req), consumer, new FutureCallback<Void>() {
            @Override
            public void completed(final Void v) {
                connected.set(false);
                if (cancelled.get()) {
                    notifyClosedOnce();
                    return;
                }
                if (willReconnectNext()) {
                    scheduleReconnect();
                } else {
                    notifyClosedOnce();
                }
            }

            @Override
            public void failed(final Exception ex) {
                connected.set(false);
                if (ex instanceof SseResponseConsumer.StopReconnectException) {
                    dispatch(() -> listener.onFailure(ex, false));
                    notifyClosedOnce();
                    return;
                }
                if (cancelled.get() || isBenignCancel(ex)) {
                    notifyClosedOnce();
                    return;
                }
                final boolean will = willReconnectNext();
                dispatch(() -> listener.onFailure(ex, will));
                if (will) {
                    scheduleReconnect();
                } else {
                    notifyClosedOnce();
                }
            }

            @Override
            public void cancelled() {
                connected.set(false);
                notifyClosedOnce();
            }
        });
    }

    /**
     * Creates the {@link AsyncResponseConsumer} chain for SSE, selecting the low-level
     * entity consumer per {@link SseParser} and capturing {@code Retry-After} hints.
     *
     * @return response consumer that feeds parsed events into the listener
     */
    private AsyncResponseConsumer<Void> getAsyncResponseConsumer() {
        final SseCallbacks cbs = new SseCallbacks() {
            @Override
            public void onOpen() {
                connected.set(true);
                attempts.set(0);
                previousDelayMs = 0;
                dispatch(listener::onOpen);
            }

            @Override
            public void onEvent(final String id, final String type, final String data) {
                if (id != null) {
                    lastEventId = id;
                }
                dispatch(() -> listener.onEvent(id, type, data));
            }

            @Override
            public void onRetry(final long retryMs) {
                stickyRetryMs = Math.max(0L, retryMs);
            }
        };

        final AsyncEntityConsumer<Void> entity =
                (parser == SseParser.BYTE) ? new ByteSseEntityConsumer(cbs)
                        : new SseEntityConsumer(cbs);

        return new SseResponseConsumer(entity, ms -> retryAfterHintMs = Math.max(0L, ms));
    }

    /**
     * Decides whether a subsequent reconnect should be attempted, without mutating state.
     *
     * <p>Respects {@code maxReconnects}. Delegates to {@link BackoffStrategy#shouldReconnect(int, long, Long)}.
     * If the strategy throws, the method returns {@code false} to avoid spin.</p>
     *
     * @return {@code true} if a reconnect should be attempted
     */
    private boolean willReconnectNext() {
        if (cancelled.get()) {
            return false;
        }
        if (maxReconnects >= 0 && attempts.get() >= maxReconnects) {
            return false;
        }

        final int nextAttempt = attempts.get() + 1;
        final Long hint = (retryAfterHintMs >= 0L) ? Long.valueOf(retryAfterHintMs)
                : (stickyRetryMs >= 0L ? stickyRetryMs : null);
        boolean decision;
        try {
            decision = backoff.shouldReconnect(nextAttempt, previousDelayMs, hint);
        } catch (final RuntimeException rex) {
            // be conservative: if strategy blew up, do not spin forever
            LOG.warn("BackoffStrategy.shouldReconnect threw: {}; stopping reconnects", rex.toString());
            decision = false;
        }
        return decision;
    }

    /**
     * Computes the next delay using the {@link BackoffStrategy} and schedules a reconnect.
     *
     * <p>Consumes the one-shot {@code Retry-After} hint if present; the SSE {@code retry:}
     * hint remains sticky until overridden by the server.</p>
     *
     */
    private void scheduleReconnect() {
        if (!willReconnectNext()) {
            notifyClosedOnce();
            return;
        }
        final int attempt = attempts.incrementAndGet();
        final Long hint = (retryAfterHintMs >= 0L) ? Long.valueOf(retryAfterHintMs)
                : (stickyRetryMs >= 0L ? stickyRetryMs : null);
        long d;
        try {
            d = backoff.nextDelayMs(attempt, previousDelayMs, hint);
        } catch (final RuntimeException rex) {
            LOG.warn("BackoffStrategy.nextDelayMs threw: {}; defaulting to 1000ms", rex.toString());
            d = 1000L;
        }
        previousDelayMs = Math.max(0L, d);
        retryAfterHintMs = -1L; // one-shot hint consumed
        connect(previousDelayMs);
    }

    /**
     * Dispatches a listener task using the configured executor, falling back
     * to the caller thread if submission fails.
     *
     * @param r task to run
     */
    private void dispatch(final Runnable r) {
        try {
            callbackExecutor.execute(r);
        } catch (final RuntimeException e) {
            try {
                r.run();
            } catch (final Exception ex) {
                LOG.error("EventSource listener failed after submit failure: {}", ex, ex);

            }
        }
    }

    /**
     * Ensures {@link EventSourceListener#onClosed()} is invoked at most once.
     *
     */
    private void notifyClosedOnce() {
        if (closedOnce.compareAndSet(false, true)) {
            connected.set(false);
            dispatch(listener::onClosed);
        }
    }

    /**
     * Returns {@code true} for failure types that are expected during cancel/close.
     *
     * @param ex the exception to inspect
     * @return {@code true} if the exception represents a benign cancellation
     */
    private static boolean isBenignCancel(final Exception ex) {
        return ex instanceof RequestNotExecutedException
                || ex instanceof ConnectionClosedException
                || ex instanceof CancellationException
                || ex instanceof InterruptedIOException;
    }
}
