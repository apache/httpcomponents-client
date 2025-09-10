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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WsInboundBufferTest {

    @Test
    void emptyDataDoesNotCrash() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, new WebSocketListener() { }, cfg, null, null);

        // Push empty buffer — should not crash
        engine.onData(ByteBuffer.allocate(0));
    }

    @Test
    void onDisconnectedNotifiesClose() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final StubTransport transport = new StubTransport();
        final CloseCapture listener = new CloseCapture();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, listener, cfg, null, null);

        engine.onDisconnected();

        Assertions.assertEquals(1006, listener.closeCode.get());
    }

    private static final class CloseCapture implements WebSocketListener {
        final AtomicInteger closeCode = new AtomicInteger(-1);

        @Override
        public void onClose(final int code, final String reason) {
            closeCode.compareAndSet(-1, code);
        }
    }
}
