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
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side representation of a single WebSocket connection.
 *
 * <p>Instances of this interface are thread-safe. Outbound operations may be
 * invoked from arbitrary application threads. Inbound events are delivered
 * to the associated {@link WebSocketListener}.</p>
 *
 * <p>Outbound calls return {@code true} when the frame has been accepted for
 * transmission. They do not indicate that the peer has received or processed
 * the message. Applications that require acknowledgements must implement them
 * at the protocol layer.</p>
 *
 * <p>The close handshake follows RFC 6455. Applications should call
 * {@link #close(int, String)} and wait for the {@link WebSocketListener#onClose(int, String)}
 * callback to consider the connection terminated.</p>
 *
 * @since 5.7
 */
public interface WebSocket {

    /**
     * Returns {@code true} if the WebSocket is still open and not in the
     * process of closing.
     */
    boolean isOpen();

    /**
     * Sends a PING control frame with the given payload.
     * <p>The payload size must not exceed 125 bytes.</p>
     *
     * @param data optional payload buffer; may be {@code null}.
     * @return {@code true} if the frame was accepted for sending,
     * {@code false} if the connection is closing or closed.
     */
    boolean ping(ByteBuffer data);

    /**
     * Sends a PONG control frame with the given payload.
     * <p>The payload size must not exceed 125 bytes.</p>
     *
     * @param data optional payload buffer; may be {@code null}.
     * @return {@code true} if the frame was accepted for sending,
     * {@code false} if the connection is closing or closed.
     */
    boolean pong(ByteBuffer data);

    /**
     * Sends a text message fragment.
     *
     * @param data          text data to send. Must not be {@code null}.
     * @param finalFragment {@code true} if this is the final fragment of
     *                      the message, {@code false} if more fragments
     *                      will follow.
     * @return {@code true} if the fragment was accepted for sending,
     * {@code false} if the connection is closing or closed.
     */
    boolean sendText(CharSequence data, boolean finalFragment);

    /**
     * Sends a binary message fragment.
     *
     * @param data          binary data to send. Must not be {@code null}.
     * @param finalFragment {@code true} if this is the final fragment of
     *                      the message, {@code false} if more fragments
     *                      will follow.
     * @return {@code true} if the fragment was accepted for sending,
     * {@code false} if the connection is closing or closed.
     */
    boolean sendBinary(ByteBuffer data, boolean finalFragment);

    /**
     * Sends a batch of text fragments as a single message.
     *
     * @param fragments     ordered list of fragments; must not be {@code null}
     *                      or empty.
     * @param finalFragment {@code true} if this batch completes the logical
     *                      message, {@code false} if subsequent batches
     *                      will follow.
     * @return {@code true} if the batch was accepted for sending,
     * {@code false} if the connection is closing or closed.
     */
    boolean sendTextBatch(List<CharSequence> fragments, boolean finalFragment);

    /**
     * Sends a batch of binary fragments as a single message.
     *
     * @param fragments     ordered list of fragments; must not be {@code null}
     *                      or empty.
     * @param finalFragment {@code true} if this batch completes the logical
     *                      message, {@code false} if subsequent batches
     *                      will follow.
     * @return {@code true} if the batch was accepted for sending,
     * {@code false} if the connection is closing or closed.
     */
    boolean sendBinaryBatch(List<ByteBuffer> fragments, boolean finalFragment);

    /**
     * Returns the number of bytes currently queued for outbound transmission.
     *
     * <p>This value is intended for backpressure and observability. It reflects
     * bytes accepted by the implementation but not fully written to the
     * transport yet.</p>
     *
     * @return queued outbound bytes, or {@code -1} if unavailable
     * @since 5.7
     */
    long queueSize();
    /**
     * Initiates the WebSocket close handshake.
     *
     * <p>The returned future is completed once the close frame has been
     * queued for sending. It does <em>not</em> wait for the peer's close
     * frame or for the underlying TCP connection to be closed.</p>
     *
     * @param statusCode close status code to send.
     * @param reason     optional close reason; may be {@code null}.
     * @return a future that completes when the close frame has been
     * enqueued, or completes exceptionally if the close
     * could not be initiated.
     */
    CompletableFuture<Void> close(int statusCode, String reason);
}

