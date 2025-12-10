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

/**
 * Client-side <em>Server-Sent Events</em> (SSE) support for HttpClient 5.
 *
 * <p>This package provides a small, focused API for consuming {@code text/event-stream}
 * resources with automatic reconnection, pluggable backoff strategies, and a
 * configurable parsing pipeline. It is designed for very low-latency, high-fan-out
 * read workloads and integrates with HttpClient 5's asynchronous I/O stack.</p>
 *
 * <h2>Key types</h2>
 * <ul>
 *   <li>{@link org.apache.hc.client5.http.sse.SseExecutor} — entry point that opens
 *       {@link org.apache.hc.client5.http.sse.EventSource} instances and manages the underlying
 *       {@code CloseableHttpAsyncClient} lifecycle (shared or caller-supplied).</li>
 *   <li>{@link org.apache.hc.client5.http.sse.EventSource} — represents a single SSE connection:
 *       start, cancel, inspect connection state, manipulate headers, and manage {@code Last-Event-ID}.</li>
 *   <li>{@link org.apache.hc.client5.http.sse.EventSourceListener} — callback interface for
 *       open/close, events, and failures (with a flag indicating whether a reconnect is scheduled).</li>
 *   <li>{@link org.apache.hc.client5.http.sse.EventSourceConfig} — policy and limits
 *       (e.g., {@link org.apache.hc.client5.http.sse.BackoffStrategy}, max reconnects).</li>
 *   <li>{@link org.apache.hc.client5.http.sse.BackoffStrategy} — reconnection policy SPI
 *       with built-ins:
 *       {@link org.apache.hc.client5.http.sse.impl.ExponentialJitterBackoff},
 *       {@link org.apache.hc.client5.http.sse.impl.FixedBackoffStrategy},
 *       {@link org.apache.hc.client5.http.sse.impl.NoBackoffStrategy}.</li>
 *   <li>{@link org.apache.hc.client5.http.sse.impl.SseParser} — choose between a spec-friendly
 *       char parser or a byte parser optimized for minimal allocations.</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>
 * import java.net.URI;
 * import java.util.Collections;
 * import org.apache.hc.client5.http.sse.*;
 *
 * SseExecutor exec = SseExecutor.newInstance(); // shared async client
 *
 * EventSourceListener listener = new EventSourceListener() {
 *   &#64;Override public void onOpen() { System.out.println("open"); }
 *   &#64;Override public void onEvent(String id, String type, String data) {
 *     System.out.println(type + " id=" + id + " data=" + data);
 *   }
 *   &#64;Override public void onClosed() { System.out.println("closed"); }
 *   &#64;Override public void onFailure(Throwable t, boolean willReconnect) {
 *     t.printStackTrace();
 *   }
 * };
 *
 * EventSource es = exec.open(URI.create("https://example.com/stream"),
 *                            Collections.&lt;String,String&gt;emptyMap(),
 *                            listener);
 * es.start();
 *
 * Runtime.getRuntime().addShutdownHook(new Thread(es::cancel));
 * </pre>
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li><b>Backoff:</b> Provide a {@link org.apache.hc.client5.http.sse.BackoffStrategy} via
 *       {@link org.apache.hc.client5.http.sse.EventSourceConfig}. Server {@code retry:} lines and
 *       HTTP {@code Retry-After} headers are honored when present.</li>
 *   <li><b>Headers:</b> Add defaults on the {@link org.apache.hc.client5.http.sse.SseExecutorBuilder}
 *       or per-connection using {@link org.apache.hc.client5.http.sse.EventSource#setHeader(String, String)}.</li>
 *   <li><b>Parser:</b> {@link org.apache.hc.client5.http.sse.impl.SseParser#CHAR} (default) is spec-compliant;
 *       {@link org.apache.hc.client5.http.sse.impl.SseParser#BYTE} reduces intermediate allocations for
 *       very high event rates.</li>
 *   <li><b>Executors:</b> You can supply a {@code ScheduledExecutorService} for reconnect delays and an
 *       {@code Executor} for listener callbacks. If not provided, a shared scheduler is used
 *       and callbacks execute inline; keep your listener lightweight.</li>
 *   <li><b>Resumption:</b> {@code Last-Event-ID} is tracked automatically and sent on reconnects.
 *       You can seed it with {@link org.apache.hc.client5.http.sse.EventSource#setLastEventId(String)}.</li>
 * </ul>
 *
 * <h2>Resource management</h2>
 * <ul>
 *   <li>{@link org.apache.hc.client5.http.sse.SseExecutor#newInstance()} uses a process-wide shared
 *       async client; {@link org.apache.hc.client5.http.sse.SseExecutor#close()} is a no-op in this case.</li>
 *   <li>If you supply your own client via the builder, you own it and {@code close()} will shut it down.</li>
 *   <li>Call {@link org.apache.hc.client5.http.sse.EventSource#cancel()} to stop a stream and deliver
 *       {@link org.apache.hc.client5.http.sse.EventSourceListener#onClosed()}.</li>
 * </ul>
 *
 * <h2>HTTP/2 and scaling</h2>
 * <p>When used with {@code httpcore5-h2} and a pooling connection manager configured for
 * message multiplexing, multiple SSE streams can share the same HTTP/2 connection to reduce
 * socket overhead. This package does not require HTTP/2; it also operates over HTTP/1.1.</p>
 *
 * <h2>Threading</h2>
 * <ul>
 *   <li>Methods on {@link org.apache.hc.client5.http.sse.EventSource} are thread-safe.</li>
 *   <li>Listener callbacks may run on the I/O thread unless a callback executor was supplied.
 *       Keep callbacks fast and non-blocking.</li>
 * </ul>
 *
 * <h2>Compatibility</h2>
 * <p>All public types in this package are source- and binary-compatible with Java 8.</p>
 *
 * <h2>Internals</h2>
 * <p>Implementation classes in {@code org.apache.hc.client5.http.sse.impl} annotated with
 * {@link org.apache.hc.core5.annotation.Internal} (for example,
 * {@link org.apache.hc.client5.http.sse.impl.DefaultEventSource},
 * {@link org.apache.hc.client5.http.sse.impl.SseResponseConsumer},
 * {@link org.apache.hc.client5.http.sse.impl.ServerSentEventReader},
 * and the concrete entity consumers) are not part of the public API and may
 * change without notice.</p>
 *
 * @since 5.7
 */
package org.apache.hc.client5.http.sse;
