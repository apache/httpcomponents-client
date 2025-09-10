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
package org.apache.hc.client5.http.websocket.api;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 * Callback interface for receiving WebSocket events.
 *
 * <p>Implementations should be fast and non-blocking because callbacks
 * are normally invoked on I/O dispatcher threads.</p>
 *
 * <p>Exceptions thrown by callbacks are treated as errors and may result
 * in the connection closing. Implementations should handle their own
 * failures and avoid throwing unless they intend to abort the session.</p>
 *
 * @since 5.7
 */
public interface WebSocketListener {

    /**
     * Invoked when the WebSocket connection has been established.
     */
    default void onOpen(WebSocket webSocket) {
    }

    /**
     * Invoked when a complete text message has been received.
     *
     * @param data characters of the message; the buffer is only valid
     *             for the duration of the callback.
     * @param last always {@code true} for now; reserved for future
     *             streaming support.
     */
    default void onText(CharBuffer data, boolean last) {
    }

    /**
     * Invoked when a complete binary message has been received.
     *
     * @param data binary payload; the buffer is only valid for the
     *             duration of the callback.
     * @param last always {@code true} for now; reserved for future
     *             streaming support.
     */
    default void onBinary(ByteBuffer data, boolean last) {
    }

    /**
     * Invoked when a PING control frame is received.
     */
    default void onPing(ByteBuffer data) {
    }

    /**
     * Invoked when a PONG control frame is received.
     */
    default void onPong(ByteBuffer data) {
    }

    /**
     * Invoked when the WebSocket has been closed.
     *
     * @param statusCode close status code.
     * @param reason     close reason, never {@code null} but may be empty.
     */
    default void onClose(int statusCode, String reason) {
    }

    /**
     * Invoked when a fatal error occurs on the WebSocket connection.
     *
     * <p>After this callback the connection is considered closed.</p>
     */
    default void onError(Throwable cause) {
    }
}

