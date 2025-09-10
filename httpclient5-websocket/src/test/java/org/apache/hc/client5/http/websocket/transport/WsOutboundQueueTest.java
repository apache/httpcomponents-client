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

import java.nio.ByteBuffer;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WsOutboundQueueTest {

    @Test
    void dataQueueLimitRejectsExcess() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .setMaxOutboundDataBytes(7)
                .build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, new WebSocketListener() { }, cfg, null, null);
        final WebSocket ws = engine.facade();

        Assertions.assertTrue(ws.sendBinary(ByteBuffer.wrap(new byte[] {1}), true));
        Assertions.assertFalse(ws.sendBinary(ByteBuffer.wrap(new byte[] {1}), true));
    }

    @Test
    void queueSizeReflectsQueuedDataBytes() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .setMaxOutboundDataBytes(64)
                .build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, new WebSocketListener() { }, cfg, null, null);
        final WebSocket ws = engine.facade();

        Assertions.assertEquals(0, ws.queueSize());
        Assertions.assertTrue(ws.sendBinary(ByteBuffer.wrap(new byte[] {1, 2, 3}), true));
        Assertions.assertTrue(ws.queueSize() > 0);
    }

    @Test
    void drainAfterOutputReadyMovesData() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .setMaxOutboundDataBytes(64)
                .build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, new WebSocketListener() { }, cfg, null, null);
        final WebSocket ws = engine.facade();

        Assertions.assertTrue(ws.sendBinary(ByteBuffer.wrap(new byte[] {1, 2, 3}), true));
        // Drain via onOutputReady (StubTransport swallows all bytes)
        engine.onOutputReady();
        Assertions.assertEquals(0, ws.queueSize());
    }
}
