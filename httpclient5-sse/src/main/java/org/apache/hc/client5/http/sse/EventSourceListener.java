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

/**
 * Callback interface for receiving Server-Sent Events (SSE) and lifecycle
 * notifications from an {@link EventSource}.
 *
 * <p>Implementations should keep handlers lightweight and non-blocking.
 * If you need to do heavy work, offload to your own executor.</p>
 *
 * <h3>Invocation &amp; threading</h3>
 * <ul>
 *   <li>Methods may be invoked on an internal callback executor supplied to the
 *       {@code EventSource} (or on the caller thread if none was supplied).
 *       Do not assume a specific thread.</li>
 *   <li>Handlers may be invoked concurrently; make your listener thread-safe.</li>
 *   <li>Exceptions thrown by handlers are caught and logged by the caller; they
 *       do not stop the stream.</li>
 * </ul>
 *
 * <h3>Event semantics</h3>
 * <ul>
 *   <li>{@link #onOpen()} is called once the HTTP response is accepted and the
 *       SSE stream is ready.</li>
 *   <li>{@link #onEvent(String, String, String)} is called for each SSE event.
 *       The {@code type} defaults to {@code "message"} when {@code null}.</li>
 *   <li>{@link #onFailure(Throwable, boolean)} is called when the stream fails.
 *       If {@code willReconnect} is {@code true}, a reconnect attempt has been scheduled.</li>
 *   <li>{@link #onClosed()} is called exactly once when the stream is permanently
 *       closed (either by {@link EventSource#cancel()} or after giving up on reconnects).</li>
 * </ul>
 *
 * @since 5.7
 */
@FunctionalInterface
public interface EventSourceListener {

    /**
     * Called for each SSE event received.
     *
     * @param id   the event id, or {@code null} if not present
     * @param type the event type, or {@code null} (treat as {@code "message"})
     * @param data the event data (never {@code null})
     * @since 5.7
     */
    void onEvent(String id, String type, String data);

    /**
     * Called when the SSE stream is opened and ready to receive events.
     *
     * @since 5.7
     */
    default void onOpen() {
    }

    /**
     * Called once when the stream is permanently closed (no further reconnects).
     *
     * @since 5.7
     */
    default void onClosed() {
    }

    /**
     * Called when the stream fails.
     *
     * <p>If {@code willReconnect} is {@code true}, the implementation has scheduled
     * a reconnect attempt according to the configured {@link EventSourceConfig} and
     * {@link BackoffStrategy}.</p>
     *
     * @param t              the failure cause (never {@code null})
     * @param willReconnect  {@code true} if a reconnect has been scheduled
     * @since 5.7
     */
    default void onFailure(final Throwable t, final boolean willReconnect) {
    }
}
