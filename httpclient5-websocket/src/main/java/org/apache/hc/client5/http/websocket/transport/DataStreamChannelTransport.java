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
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 transport adapter wrapping a {@link DataStreamChannel}.
 *
 * <p>The channel reference is set lazily via {@link #setChannel(DataStreamChannel)}
 * when the first {@code produce()} callback arrives from the H2 multiplexer.</p>
 */
@Internal
public final class DataStreamChannelTransport implements WebSocketTransport {

    private volatile DataStreamChannel channel;
    private volatile boolean endStreamPending;

    @Override
    public int write(final ByteBuffer src) throws IOException {
        final DataStreamChannel ch = channel;
        if (ch == null) {
            return 0;
        }
        return ch.write(src);
    }

    @Override
    public void requestOutput() {
        final DataStreamChannel ch = channel;
        if (ch != null) {
            ch.requestOutput();
        }
    }

    @Override
    public void setTimeout(final Timeout timeout) {
        // H2 does not use socket-level timeouts for close wait;
        // the engine uses ScheduledExecutorService instead.
    }

    @Override
    public void closeGracefully() {
        endStreamPending = true;
        requestOutput();
    }

    @Override
    public void abort() {
        endStreamPending = true;
        requestOutput();
    }

    @Override
    public void endStream() throws IOException {
        endStreamPending = false;
        final DataStreamChannel ch = channel;
        if (ch != null) {
            ch.endStream(null);
        }
    }

    public void setChannel(final DataStreamChannel channel) {
        this.channel = channel;
    }

    public DataStreamChannel getChannel() {
        return channel;
    }

    public boolean isEndStreamPending() {
        return endStreamPending;
    }

    public void markEndStreamPending() {
        endStreamPending = true;
    }
}
