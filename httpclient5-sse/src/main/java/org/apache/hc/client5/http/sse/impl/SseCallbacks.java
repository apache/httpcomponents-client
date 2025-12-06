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

import org.apache.hc.core5.annotation.Internal;

/**
 * Internal callback contract used by SSE entity consumers to
 * report lifecycle and parsed event data back to the owning
 * {@code EventSource} implementation.
 *
 * <p>This interface is package-private by design and not part of the
 * public API. Methods may be invoked on I/O or decoder threads;
 * implementations must be lightweight and non-blocking.</p>
 *
 * @since 5.7
 */
@Internal
public interface SseCallbacks {

    /**
     * Signals that the HTTP response has been accepted and the
     * SSE stream is ready to deliver events.
     */
    void onOpen();

    /**
     * Delivers a parsed SSE event.
     *
     * @param id   the event id, or {@code null} if not present
     * @param type the event type, or {@code null} (treat as {@code "message"})
     * @param data the event payload (never {@code null})
     */
    void onEvent(String id, String type, String data);

    /**
     * Notifies of a change to the client-side reconnect delay as
     * advertised by the server via the {@code retry:} field.
     *
     * @param retryMs new retry delay in milliseconds (non-negative)
     */
    void onRetry(long retryMs);
}
