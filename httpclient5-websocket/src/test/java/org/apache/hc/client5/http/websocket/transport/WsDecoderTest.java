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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.junit.jupiter.api.Test;

class WsDecoderTest {

    @Test
    void serverMaskedFrame_isRejected() {
        // Build a minimal TEXT frame with MASK bit set (which servers MUST NOT set).
        // 0x81 FIN|TEXT, 0x80 | 0 = mask + length 0, then 4-byte masking key.
        final ByteBuffer buf = ByteBuffer.allocate(2 + 4);
        buf.put((byte) 0x81);
        buf.put((byte) 0x80); // MASK set, len=0
        buf.putInt(0x11223344);
        buf.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192);
        assertThrows(WebSocketProtocolException.class, () -> d.decode(buf));
    }

    @Test
    void clientMaskedFrame_isAccepted_whenExpectMasked() {
        final ByteBuffer buf = ByteBuffer.allocate(2 + 4);
        buf.put((byte) 0x81);
        buf.put((byte) 0x80); // MASK set, len=0
        buf.putInt(0x01020304);
        buf.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192, true, true);
        assertTrue(d.decode(buf));
        assertEquals(FrameOpcode.TEXT, d.opcode());
    }

    @Test
    void maskedPayload_isUnmasked_whenExpectMasked() {
        final byte[] plain = "Hi".getBytes(StandardCharsets.US_ASCII);
        final byte[] mask = new byte[] { 1, 2, 3, 4 };
        final ByteBuffer buf = ByteBuffer.allocate(2 + 4 + plain.length);
        buf.put((byte) 0x81);
        buf.put((byte) (0x80 | plain.length));
        buf.put(mask);
        for (int i = 0; i < plain.length; i++) {
            buf.put((byte) (plain[i] ^ mask[i & 3]));
        }
        buf.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192, true, true);
        assertTrue(d.decode(buf));
        final byte[] decoded = new byte[d.payload().remaining()];
        d.payload().get(decoded);
        assertEquals("Hi", new String(decoded, StandardCharsets.US_ASCII));
    }

    @Test
    void clientUnmaskedFrame_isRejected_whenExpectMasked() {
        final ByteBuffer buf = ByteBuffer.allocate(2);
        buf.put((byte) 0x81);
        buf.put((byte) 0x00); // no MASK
        buf.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192, true, true);
        assertThrows(WebSocketProtocolException.class, () -> d.decode(buf));
    }

    @Test
    void controlFrame_fragmented_isRejected() {
        final ByteBuffer buf = ByteBuffer.allocate(2);
        buf.put((byte) 0x09); // FIN=0, PING
        buf.put((byte) 0x00); // len=0
        buf.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192);
        assertThrows(WebSocketProtocolException.class, () -> d.decode(buf));
    }

    @Test
    void controlFrame_tooLarge_isRejected() {
        final ByteBuffer buf = ByteBuffer.allocate(4);
        buf.put((byte) 0x89); // FIN=1, PING
        buf.put((byte) 126);  // len=126 (invalid for control frame)
        buf.putShort((short) 126);
        buf.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192);
        assertThrows(WebSocketProtocolException.class, () -> d.decode(buf));
    }

    @Test
    void rsvBitsWithoutExtensions_areRejected() {
        final ByteBuffer buf = ByteBuffer.allocate(2);
        buf.put((byte) 0xC1); // FIN=1, RSV1=1, TEXT
        buf.put((byte) 0x00);
        buf.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192);
        assertThrows(WebSocketProtocolException.class, () -> d.decode(buf));
    }

    @Test
    void reservedOpcode_isRejected() {
        final ByteBuffer buf = ByteBuffer.allocate(2);
        buf.put((byte) 0x83); // FIN=1, opcode=3 (reserved)
        buf.put((byte) 0x00);
        buf.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192);
        assertThrows(WebSocketProtocolException.class, () -> d.decode(buf));
    }

    @Test
    void extendedLen_126_and_127_parse() {
        // A FIN|BINARY with 126 length, len=300
        final byte[] payload = new byte[300];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xFF);
        }

        final ByteBuffer f126 = ByteBuffer.allocate(2 + 2 + payload.length);
        f126.put((byte) 0x82); // FIN+BINARY
        f126.put((byte) 126);
        f126.putShort((short) payload.length);
        f126.put(payload);
        f126.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(4096);
        assertTrue(d.decode(f126));
        assertEquals(FrameOpcode.BINARY, d.opcode());
        assertEquals(payload.length, d.payload().remaining());

        // Now 127 with len=65540 (> 0xFFFF)
        final int big = 65540;
        final byte[] p2 = new byte[big];
        final ByteBuffer f127 = ByteBuffer.allocate(2 + 8 + p2.length);
        f127.put((byte) 0x82);
        f127.put((byte) 127);
        f127.putLong(big);
        f127.put(p2);
        f127.flip();

        final WebSocketFrameDecoder d2 = new WebSocketFrameDecoder(big + 32);
        assertTrue(d2.decode(f127));
        assertEquals(big, d2.payload().remaining());
    }

    @Test
    void partialBuffer_returnsFalse_and_consumesNothing() {
        final ByteBuffer f = ByteBuffer.allocate(2);
        f.put((byte) 0x81);
        f.put((byte) 0x7E); // says 126, but no length bytes present
        f.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(1024);
        // Should mark/reset and return false; buffer remains at same position after call (no throw).
        final int posBefore = f.position();
        assertFalse(d.decode(f));
        assertEquals(posBefore, f.position());
    }
}
