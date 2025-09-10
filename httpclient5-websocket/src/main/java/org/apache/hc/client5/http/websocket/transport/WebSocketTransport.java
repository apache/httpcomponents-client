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
package org.apache.hc.client5.http.websocket.transport;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.Timeout;

/**
 * Abstraction over the underlying I/O channel for a WebSocket session.
 *
 * <p>HTTP/1.1 wraps an {@code IOSession}; HTTP/2 wraps a {@code DataStreamChannel}.
 * The unified {@link WebSocketSessionEngine} writes through this interface so that
 * frame encoding, queue management, and close-handshake logic are shared.</p>
 */
@Internal
public interface WebSocketTransport {

    /**
     * Write bytes to the underlying channel.
     *
     * @return number of bytes actually written (may be 0 if the channel is not ready)
     */
    int write(ByteBuffer src) throws IOException;

    /**
     * Request that the transport arrange a callback to
     * {@link WebSocketSessionEngine#onOutputReady()} when the channel
     * is ready for more writes.
     */
    void requestOutput();

    /**
     * Set the I/O timeout on the underlying transport.
     * For HTTP/2 this is a no-op (close timeout is handled externally).
     */
    void setTimeout(Timeout timeout);

    /**
     * Initiate a graceful shutdown of the transport.
     */
    void closeGracefully();

    /**
     * Immediately abort the transport.
     */
    void abort();

    /**
     * Signal that the WebSocket stream is ending.
     * H1: closes the IOSession. H2: sends {@code endStream} on the data channel.
     */
    void endStream() throws IOException;
}
