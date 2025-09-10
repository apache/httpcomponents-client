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

/**
 * Application callbacks for a client-side WebSocket session (RFC 6455, RFC 7692).
 *
 * <h3>Threading</h3>
 * Callbacks are invoked by the I/O reactor thread. Implementations should return quickly
 * and avoid blocking operations. Offload long-running work to application executors.
 *
 * <h3>Delivery semantics</h3>
 * <ul>
 *   <li>Text is delivered as valid UTF-8. Decoding errors are reported via {@link #onError(Throwable)}.</li>
 *   <li>Binary/text messages may arrive fragmented. The {@code last} flag indicates the final fragment.</li>
 *   <li>Control frames (PING/PONG/CLOSE) follow RFC 6455 size and fragmentation rules.</li>
 * </ul>
 *
 * @since 5.6
 */
public interface WebSocketListener {

    /**
     * Called once the connection is open and ready to send/receive frames.
     *
     * @param ws an active {@link WebSocket} handle; may be used to send frames
     */
    default void onOpen(final WebSocket ws) {
    }

    /**
     * Called when a text message fragment is received.
     *
     * @param text UTF-8 text fragment (may be partial)
     * @param last {@code true} if this fragment is the final fragment of the message
     */
    default void onText(final CharSequence text, final boolean last) {
    }

    /**
     * Called when a binary message fragment is received.
     *
     * @param payload binary payload (read-only view)
     * @param last    {@code true} if this fragment is the final fragment of the message
     */
    default void onBinary(final java.nio.ByteBuffer payload, final boolean last) {
    }

    /**
     * Called when a PING control frame is received.
     *
     * <p>If {@code autoPong} is enabled in the client config, a PONG is sent automatically.</p>
     *
     * @param payload optional application data (≤ 125 bytes; read-only view)
     */
    default void onPing(final java.nio.ByteBuffer payload) {
    }

    /**
     * Called when a PONG control frame is received.
     *
     * @param payload optional application data (≤ 125 bytes; read-only view)
     */
    default void onPong(final java.nio.ByteBuffer payload) {
    }

    /**
     * Called when the close handshake completes or the connection is terminated.
     *
     * @param statusCode RFC 6455 close code (e.g. 1000), or 1006 for abnormal closure (local event only)
     * @param reason     optional human-readable reason from the peer (may be empty)
     */
    default void onClose(final int statusCode, final String reason) {
    }

    /**
     * Called on protocol or transport errors.
     *
     * <p>Typical causes include protocol violations (mapped to RFC close codes), I/O failures,
     * or timeouts. The implementation will close the session gracefully where possible.</p>
     *
     * @param ex the error that occurred
     */
    default void onError(final Throwable ex) {
    }
}
