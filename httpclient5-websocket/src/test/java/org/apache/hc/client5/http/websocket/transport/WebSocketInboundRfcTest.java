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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.junit.jupiter.api.Test;

class WebSocketInboundRfcTest {

    @Test
    void closeReasonInvalidUtf8_closesWith1007() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketSessionEngine engine = newEngine(listener);

        final byte[] payload = new byte[]{0x03, (byte) 0xE8, (byte) 0xC3, (byte) 0x28}; // 1000 + invalid UTF-8
        engine.onData(unmaskedFrame(FrameOpcode.CLOSE, true, payload));

        assertEquals(1007, listener.closeCode.get());
    }

    @Test
    void closeCodeInvalidOnWire_closesWith1002() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketSessionEngine engine = newEngine(listener);

        final byte[] payload = new byte[]{0x03, (byte) 0xED}; // 1005 (invalid on wire)
        engine.onData(unmaskedFrame(FrameOpcode.CLOSE, true, payload));

        assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void dataFrameWhileFragmentedMessage_closesWith1002() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketSessionEngine engine = newEngine(listener);

        engine.onData(unmaskedFrame(FrameOpcode.TEXT, false, new byte[]{0x61}));
        engine.onData(unmaskedFrame(FrameOpcode.BINARY, true, new byte[]{0x62}));

        assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void unexpectedContinuation_closesWith1002() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketSessionEngine engine = newEngine(listener);

        engine.onData(unmaskedFrame(FrameOpcode.CONT, true, new byte[]{0x01}));

        assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void fragmentedControlFrame_closesWith1002() {
        final CloseCaptureListener listener = new CloseCaptureListener();
        final WebSocketSessionEngine engine = newEngine(listener);

        engine.onData(unmaskedFrame(FrameOpcode.PING, false, new byte[0]));

        assertEquals(1002, listener.closeCode.get());
    }

    private static WebSocketSessionEngine newEngine(final CloseCaptureListener listener) {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .enablePerMessageDeflate(false)
                .build();
        final StubTransport transport = new StubTransport();
        return new WebSocketSessionEngine(transport, listener, cfg, null, null);
    }

    private static ByteBuffer unmaskedFrame(final int opcode, final boolean fin, final byte[] payload) {
        final int len = payload != null ? payload.length : 0;
        final ByteBuffer buf = ByteBuffer.allocate(2 + len);
        buf.put((byte) ((fin ? 0x80 : 0x00) | (opcode & 0x0F)));
        buf.put((byte) len);
        if (len > 0) {
            buf.put(payload);
        }
        buf.flip();
        return buf;
    }

    private static final class CloseCaptureListener implements WebSocketListener {
        private final AtomicInteger closeCode = new AtomicInteger(-1);

        @Override
        public void onClose(final int code, final String reason) {
            closeCode.compareAndSet(-1, code);
        }
    }
}
