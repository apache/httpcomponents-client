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
package org.apache.hc.client5.http.sse;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.sse.impl.DefaultEventSource;
import org.apache.hc.client5.http.sse.impl.SseParser;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * Entry point for creating and managing {@link EventSource} instances backed by an
 * {@link CloseableHttpAsyncClient}.
 *
 * <p>This type provides:
 * <ul>
 *   <li>A process-wide shared async client (see {@link #newInstance()}),</li>
 *   <li>Factory methods that accept a caller-supplied client (see {@link #newInstance(CloseableHttpAsyncClient)}),</li>
 *   <li>A builder for fine-grained defaults (headers, backoff, parser, executors) applied to
 *       all streams opened via this executor (see {@link #custom()}).</li>
 * </ul>
 *
 * <p><strong>Lifecycle.</strong> When using the shared client, {@link #close()} is a no-op,
 * and the client remains available process-wide until {@link #closeSharedClient()} is called.
 * When you supply your own client, {@link #close()} will close that client.</p>
 *
 * <p><strong>Thread-safety.</strong> Instances are thread-safe. Methods may be called from any thread.</p>
 *
 * <p><strong>Usage example</strong></p>
 * <pre>{@code
 * SseExecutor exec = SseExecutor.custom()
 *     .setDefaultBackoff(new ExponentialJitterBackoff(1000, 30000, 2.0, 250))
 *     .setDefaultMaxReconnects(-1)
 *     .build();
 *
 * EventSource es = exec.open(URI.create("https://example/sse"),
 *     Collections.singletonMap("X-Token", "abc"),
 *     new EventSourceListener() {
 *         public void onEvent(String id, String type, String data) {
 *             System.out.println("event " + type + ": " + data);
 *         }
 *     });
 *
 * es.start();
 * }</pre>
 *
 * @since 5.7
 */
public final class SseExecutor {

    // Visible for tests
    static final ReentrantLock LOCK = new ReentrantLock();
    static volatile CloseableHttpAsyncClient SHARED_CLIENT;

    /**
     * Returns the lazily-initialized shared async client. If it does not yet exist, it is
     * created with a pooling connection manager and started.
     */
    static CloseableHttpAsyncClient getSharedClient() {
        CloseableHttpAsyncClient c = SHARED_CLIENT;
        if (c != null) {
            return c;
        }
        LOCK.lock();
        try {
            c = SHARED_CLIENT;
            if (c == null) {
                c = HttpAsyncClientBuilder.create()
                        .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                                .useSystemProperties()
                                .setMaxConnPerRoute(100)
                                .setMaxConnTotal(200)
                                .setMessageMultiplexing(true)
                                .build())
                        .useSystemProperties()
                        .evictExpiredConnections()
                        .evictIdleConnections(TimeValue.ofMinutes(1))
                        .build();
                c.start();
                SHARED_CLIENT = c;
            }
            return c;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Creates a builder for a fully configurable {@link SseExecutor}.
     *
     * <p>Use this when you want to set defaults such as headers, backoff,
     * parser strategy (char vs. byte), or custom executors for scheduling and callbacks.</p>
     * @return a new {@link SseExecutorBuilder}
     */
    public static SseExecutorBuilder custom() {
        return new SseExecutorBuilder();
    }

    /**
     * Creates an {@code SseExecutor} that uses a process-wide shared async client.
     *
     * <p>Streams opened by this executor will share one underlying {@link CloseableHttpAsyncClient}
     * instance. {@link #close()} will be a no-op; call {@link #closeSharedClient()} to
     * explicitly shut the shared client down (for tests / application shutdown).</p>
     * @return a new {@link SseExecutor}
     */
    public static SseExecutor newInstance() {
        final CloseableHttpAsyncClient c = getSharedClient();
        return new SseExecutor(c, true, null, null, EventSourceConfig.DEFAULT,
                Collections.<String, String>emptyMap(), SseParser.CHAR);
    }

    /**
     * Creates an {@code SseExecutor} using the caller-supplied async client.
     *
     * <p>The caller owns the lifecycle of the given client. {@link #close()} will close it.</p>
     *
     * @param client an already constructed async client
     * @throws NullPointerException  if {@code client} is {@code null}
     * @throws IllegalStateException if the client is shutting down or shut down
     * @return a new {@link SseExecutor}
     *
     */
    public static SseExecutor newInstance(final CloseableHttpAsyncClient client) {
        Args.notNull(client, "HTTP Async Client");
        final boolean isShared = client == SHARED_CLIENT;
        return new SseExecutor(client, isShared, null, null, EventSourceConfig.DEFAULT,
                Collections.<String, String>emptyMap(), SseParser.CHAR);
    }

    /**
     * Closes and clears the shared async client, if present.
     *
     * <p>Useful for tests or orderly application shutdown.</p>
     * @throws IOException if closing the client fails
     */
    public static void closeSharedClient() throws IOException {
        LOCK.lock();
        try {
            if (SHARED_CLIENT != null) {
                SHARED_CLIENT.close();
                SHARED_CLIENT = null;
            }
        } finally {
            LOCK.unlock();
        }
    }

    private final CloseableHttpAsyncClient client;
    private final boolean isSharedClient;
    private final ScheduledExecutorService defaultScheduler;   // nullable
    private final Executor defaultCallbackExecutor;            // nullable
    private final EventSourceConfig defaultConfig;
    private final Map<String, String> defaultHeaders;          // unmodifiable
    private final SseParser defaultParser;

    SseExecutor(final CloseableHttpAsyncClient client,
                final boolean isSharedClient,
                final ScheduledExecutorService defaultScheduler,
                final Executor defaultCallbackExecutor,
                final EventSourceConfig defaultConfig,
                final Map<String, String> defaultHeaders,
                final SseParser defaultParser) {
        this.client = client;
        this.isSharedClient = isSharedClient;
        this.defaultScheduler = defaultScheduler;
        this.defaultCallbackExecutor = defaultCallbackExecutor;
        this.defaultConfig = defaultConfig != null ? defaultConfig : EventSourceConfig.DEFAULT;
        this.defaultHeaders = defaultHeaders != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(defaultHeaders))
                : Collections.emptyMap();
        this.defaultParser = defaultParser != null ? defaultParser : SseParser.CHAR;

        final IOReactorStatus status = client.getStatus();
        if (status == IOReactorStatus.INACTIVE) {
            client.start();
        } else if (status == IOReactorStatus.SHUTTING_DOWN || status == IOReactorStatus.SHUT_DOWN) {
            throw new IllegalStateException("Async client not usable: " + status);
        }
    }

    /**
     * Closes the underlying async client if this executor does <em>not</em> use
     * the process-wide shared client. No-op otherwise.
     * @throws IOException if closing the client fails
     */
    public void close() throws IOException {
        if (!isSharedClient) {
            client.close();
        }
    }

    /**
     * Opens an {@link EventSource} with the executor's defaults (headers, config, parser, executors).
     *
     * @param uri      target SSE endpoint (must produce {@code text/event-stream})
     * @param listener event callbacks
     * @return the created {@link EventSource}
     */
    public EventSource open(final URI uri, final EventSourceListener listener) {
        return open(uri, this.defaultHeaders, listener, this.defaultConfig,
                this.defaultParser, this.defaultScheduler, this.defaultCallbackExecutor);
    }

    /**
     * Opens an {@link EventSource} overriding headers; other defaults are inherited.
     *
     * @param uri      target SSE endpoint
     * @param headers  extra request headers (merged with executor defaults)
     * @param listener event callbacks
     * @return the created {@link EventSource}
     */
    public EventSource open(final URI uri,
                            final Map<String, String> headers,
                            final EventSourceListener listener) {
        return open(uri, mergeHeaders(this.defaultHeaders, headers), listener, this.defaultConfig,
                this.defaultParser, this.defaultScheduler, this.defaultCallbackExecutor);
    }

    /**
     * Opens an {@link EventSource} overriding headers and reconnect policy; other defaults are inherited.
     *
     * @param uri      target SSE endpoint
     * @param headers  extra request headers (merged with executor defaults)
     * @param listener event callbacks
     * @param config   reconnect/backoff config
     * @return the created {@link EventSource}
     */
    public EventSource open(final URI uri,
                            final Map<String, String> headers,
                            final EventSourceListener listener,
                            final EventSourceConfig config) {
        return open(uri, mergeHeaders(this.defaultHeaders, headers), listener, config,
                this.defaultParser, this.defaultScheduler, this.defaultCallbackExecutor);
    }

    /**
     * Full-control open allowing a custom parser strategy and executors.
     *
     * @param uri              target SSE endpoint
     * @param headers          request headers (not {@code null}, may be empty)
     * @param listener         event callbacks
     * @param config           reconnect/backoff config (uses {@link EventSourceConfig#DEFAULT} if {@code null})
     * @param parser           parsing strategy ({@link SseParser#CHAR} or {@link SseParser#BYTE})
     * @param scheduler        scheduler for reconnects (nullable → internal shared scheduler)
     * @param callbackExecutor executor for listener callbacks (nullable → run inline)
     * @return the created {@link EventSource}
     */
    public EventSource open(final URI uri,
                            final Map<String, String> headers,
                            final EventSourceListener listener,
                            final EventSourceConfig config,
                            final SseParser parser,
                            final ScheduledExecutorService scheduler,
                            final Executor callbackExecutor) {
        return new DefaultEventSource(
                client,
                uri,
                headers != null ? headers : Collections.<String, String>emptyMap(),
                listener,
                scheduler,
                callbackExecutor,
                config,
                parser != null ? parser : this.defaultParser);
    }

    /**
     * Returns the underlying {@link CloseableHttpAsyncClient}.
     * @return the client
     */
    public CloseableHttpAsyncClient getClient() {
        return client;
    }

    private static Map<String, String> mergeHeaders(final Map<String, String> base, final Map<String, String> extra) {
        if (base == null || base.isEmpty()) {
            return extra != null ? extra : Collections.<String, String>emptyMap();
        }
        final LinkedHashMap<String, String> merged = new LinkedHashMap<>(base);
        if (extra != null && !extra.isEmpty()) {
            merged.putAll(extra);
        }
        return merged;
    }
}
