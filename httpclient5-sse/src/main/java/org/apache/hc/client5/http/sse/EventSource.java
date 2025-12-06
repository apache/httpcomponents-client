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

import java.util.Map;

import org.apache.hc.core5.annotation.Contract;

/**
 * Represents a Server-Sent Events (SSE) connection to a remote resource.
 *
 * <p>This interface exposes the minimal control surface for an SSE stream:
 * starting, cancelling, header management, and basic state and offset (Last-Event-ID).
 * Implementations are provided by this module (for example, via
 * a factory such as {@code SseExecutor#open(...)}), which wires the
 * {@link EventSourceListener} that receives events.</p>
 *
 * <h3>Thread-safety</h3>
 * <p>Implementations should be safe for concurrent use: {@link #start()} and
 * {@link #cancel()} are expected to be idempotent, and header methods should be
 * safe to call at any time prior to (re)connect.</p>
 *
 * <h3>Last-Event-ID</h3>
 * <p>{@code Last-Event-ID} follows the SSE spec: the most recent non-null event
 * id is remembered by the implementation and can be sent on subsequent reconnects
 * so the server can resume the stream.</p>
 *
 * @since 5.7
 */
@Contract
public interface EventSource {

    /**
     * Begins streaming events.
     * <ul>
     *   <li>Idempotent: calling when already started is a no-op.</li>
     *   <li>Non-blocking: returns immediately.</li>
     *   <li>Reconnects: implementation-specific and driven by configuration.</li>
     * </ul>
     *
     * @since 5.7
     */
    void start();

    /**
     * Cancels the stream and prevents further reconnects.
     * <ul>
     *   <li>Idempotent: safe to call multiple times.</li>
     *   <li>Implementations should eventually invoke the listener’s
     *       {@code onClosed()} exactly once.</li>
     * </ul>
     *
     * @since 5.7
     */
    void cancel();

    /**
     * Returns the last seen event id or {@code null} if none has been observed.
     *
     * @return the last event id, or {@code null}
     * @since 5.7
     */
    String lastEventId();

    /**
     * Sets the outbound {@code Last-Event-ID} that will be sent on the next connect
     * or reconnect attempt. Passing {@code null} clears the value.
     *
     * @param id the id to send, or {@code null} to clear
     * @since 5.7
     */
    void setLastEventId(String id);

    /**
     * Sets or replaces a request header for subsequent (re)connects.
     * Header names are case-insensitive per RFC 7230/9110.
     *
     * @param name  header name (non-null)
     * @param value header value (may be empty but not null)
     * @since 5.7
     */
    void setHeader(String name, String value);

    /**
     * Removes a previously set request header for subsequent (re)connects.
     *
     * @param name header name to remove (non-null)
     * @since 5.7
     */
    void removeHeader(String name);

    /**
     * Returns a snapshot of the currently configured headers that will be used
     * for the next request. The returned map is a copy and may be modified
     * by the caller without affecting the {@code EventSource}.
     *
     * @return copy of headers to be sent
     * @since 5.7
     */
    Map<String, String> getHeaders();

    /**
     * Indicates whether the SSE connection is currently open.
     * Implementations may report {@code false} during backoff between reconnects.
     *
     * @return {@code true} if the transport is open and events may be received
     * @since 5.7
     */
    boolean isConnected();
}
