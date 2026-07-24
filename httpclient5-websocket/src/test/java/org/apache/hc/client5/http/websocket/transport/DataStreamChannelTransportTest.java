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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the transport tolerates a channel whose selection key has been cancelled by a
 * concurrent shutdown, so a late CLOSE frame does not surface a {@link CancelledKeyException}.
 */
class DataStreamChannelTransportTest {

    private static final class CancelledChannel implements DataStreamChannel {

        @Override
        public int write(final ByteBuffer src) {
            throw new CancelledKeyException();
        }

        @Override
        public void requestOutput() {
            throw new CancelledKeyException();
        }

        @Override
        public void endStream() {
        }

        @Override
        public void endStream(final List<? extends Header> trailers) {
        }
    }

    @Test
    void requestOutputSwallowsCancelledKeyDuringShutdown() {
        final DataStreamChannelTransport transport = new DataStreamChannelTransport();
        transport.setChannel(new CancelledChannel());
        assertDoesNotThrow(transport::requestOutput);
    }

    @Test
    void writeTreatsCancelledChannelAsGone() throws Exception {
        final DataStreamChannelTransport transport = new DataStreamChannelTransport();
        transport.setChannel(new CancelledChannel());
        assertEquals(0, transport.write(ByteBuffer.allocate(4)));
    }

    @Test
    void requestOutputIsNoOpWithoutChannel() {
        assertDoesNotThrow(new DataStreamChannelTransport()::requestOutput);
    }
}
