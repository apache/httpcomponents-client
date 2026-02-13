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
package org.apache.hc.core5.websocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.websocket.WebSocketConfig;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;

class WebSocketServerProcessorTest {

    private static final byte[] MASK = new byte[]{1, 2, 3, 4};

    private static byte[] maskedFrame(final int opcode, final byte[] payload) {
        final int len = payload.length;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x80 | (opcode & 0x0F));
        if (len <= 125) {
            out.write(0x80 | len);
        } else if (len <= 0xFFFF) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x80 | 127);
            final long l = len;
            for (int i = 7; i >= 0; i--) {
                out.write((int) ((l >> (i * 8)) & 0xFF));
            }
        }
        out.write(MASK, 0, MASK.length);
        for (int i = 0; i < len; i++) {
            out.write(payload[i] ^ MASK[i % 4]);
        }
        return out.toByteArray();
    }

    private static byte[] closePayload(final int code, final String reason) {
        final byte[] reasonBytes = reason != null ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        final byte[] payload = new byte[2 + reasonBytes.length];
        payload[0] = (byte) ((code >> 8) & 0xFF);
        payload[1] = (byte) (code & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
        return payload;
    }

    @Test
    void processesTextAndCloseFrames() throws Exception {
        final byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
        final byte[] close = closePayload(1000, "bye");
        final ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frames.write(maskedFrame(0x1, text));
        frames.write(maskedFrame(0x8, close));

        final ByteArrayInputStream in = new ByteArrayInputStream(frames.toByteArray());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final WebSocketSession session = new WebSocketSession(
                WebSocketConfig.DEFAULT,
                in,
                out,
                null,
                null,
                Collections.emptyList());

        final TrackingHandler handler = new TrackingHandler();
        final WebSocketServerProcessor processor = new WebSocketServerProcessor(session, handler, 1024);
        processor.process();

        assertEquals("hello", handler.text);
        assertEquals(1000, handler.closeCode);
        assertEquals("bye", handler.closeReason);
        assertTrue(out.size() > 0, "server should send close response");
    }

    private static final class TrackingHandler implements WebSocketHandler {
        private String text;
        private int closeCode;
        private String closeReason;

        @Override
        public void onText(final WebSocketSession session, final String payload) {
            this.text = payload;
        }

        @Override
        public void onBinary(final WebSocketSession session, final ByteBuffer payload) {
        }

        @Override
        public void onPing(final WebSocketSession session, final ByteBuffer payload) {
        }

        @Override
        public void onPong(final WebSocketSession session, final ByteBuffer payload) {
        }

        @Override
        public void onClose(final WebSocketSession session, final int code, final String reason) {
            this.closeCode = code;
            this.closeReason = reason;
        }

        @Override
        public void onError(final WebSocketSession session, final Exception cause) {
        }

        @Override
        public String selectSubprotocol(final List<String> protocols) {
            return null;
        }
    }
}
