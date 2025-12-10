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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.sse.impl.SseParser;
import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link SseExecutor}.
 *
 * <p>Use this builder when you want to provide defaults (headers, reconnect policy,
 * parser strategy, custom executors, etc.) for all {@link EventSource}s opened
 * through the resulting {@link SseExecutor}.</p>
 *
 * <p>If no {@link CloseableHttpAsyncClient} is supplied, the process-wide shared client
 * from {@link SseExecutor#getSharedClient()} is used and {@link SseExecutor#close()} becomes
 * a no-op.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * SseExecutor exec = SseExecutor.custom()
 *     .setEventSourceConfig(
 *         EventSourceConfig.builder()
 *             .backoff(new ExponentialJitterBackoff(1000, 30000, 2.0, 250))
 *             .maxReconnects(-1)
 *             .build())
 *     .addDefaultHeader("User-Agent", "my-sse-client/1.0")
 *     .setParserStrategy(SseParser.BYTE)
 *     .build();
 * }</pre>
 *
 * @since 5.7
 */
public final class SseExecutorBuilder {

    private CloseableHttpAsyncClient client;
    private ScheduledExecutorService scheduler;   // optional
    private Executor callbackExecutor;            // optional
    private EventSourceConfig config = EventSourceConfig.DEFAULT;
    private final LinkedHashMap<String, String> defaultHeaders = new LinkedHashMap<>();
    private SseParser parserStrategy = SseParser.CHAR;

    SseExecutorBuilder() {
    }

    /**
     * Supplies a custom async HTTP client. The caller owns its lifecycle and
     * {@link SseExecutor#close()} will close it.
     * @param client the client to use
     * @return this builder
     */
    public SseExecutorBuilder setHttpClient(final CloseableHttpAsyncClient client) {
        this.client = Args.notNull(client, "HTTP Async Client");
        return this;
    }

    /**
     * Sets the scheduler to use for reconnect delays. If not provided, the internal shared
     * scheduler is used.
     * @param scheduler the scheduler to use
     * @return this builder
     */
    public SseExecutorBuilder setScheduler(final ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    /**
     * Sets the executor used to dispatch {@link EventSourceListener} callbacks.
     * If not provided, callbacks run inline on the I/O thread.
     * @param callbackExecutor the executor to use
     * @return this builder
     */
    public SseExecutorBuilder setCallbackExecutor(final Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
        return this;
    }

    /**
     * Sets the default reconnect/backoff configuration applied to opened streams.
     * @param cfg the reconnect configuration
     * @return this builder
     */
    public SseExecutorBuilder setEventSourceConfig(final EventSourceConfig cfg) {
        this.config = Args.notNull(cfg, "EventSourceConfig");
        return this;
    }

    /**
     * Replaces the default headers (sent on every opened stream).
     * @param headers the headers to use
     * @return this builder
     */
    public SseExecutorBuilder setDefaultHeaders(final Map<String, String> headers) {
        this.defaultHeaders.clear();
        if (headers != null && !headers.isEmpty()) {
            this.defaultHeaders.putAll(headers);
        }
        return this;
    }

    /**
     * Adds or replaces a single default header.
     * @param name the header name
     * @param value the header value
     * @return this builder
     */
    public SseExecutorBuilder addDefaultHeader(final String name, final String value) {
        this.defaultHeaders.put(Args.notNull(name, "name"), value);
        return this;
    }

    /**
     * Chooses the parser strategy: {@link SseParser#CHAR} (spec-level, default)
     * or {@link SseParser#BYTE} (byte-level framing with minimal decoding).
     * @param parser the parser strategy to use
     * @return this builder
     */
    public SseExecutorBuilder setParserStrategy(final SseParser parser) {
        this.parserStrategy = parser != null ? parser : SseParser.CHAR;
        return this;
    }

    /**
     * Builds the {@link SseExecutor}.
     * @return a new {@link SseExecutor}
     */
    public SseExecutor build() {
        final CloseableHttpAsyncClient c = (client != null) ? client : SseExecutor.getSharedClient();
        final boolean isShared = c == SseExecutor.SHARED_CLIENT;
        final Map<String, String> dh = defaultHeaders.isEmpty()
                ? Collections.emptyMap()
                : new LinkedHashMap<>(defaultHeaders);
        return new SseExecutor(c, isShared, scheduler, callbackExecutor, config, dh, parserStrategy);
    }
}
