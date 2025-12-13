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
package org.apache.hc.client5.http.websocket.core.frame;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.hc.client5.http.websocket.core.exceptions.WebSocketProtocolException;
import org.apache.hc.client5.http.websocket.transport.WebSocketFrameDecoder;
import org.junit.jupiter.api.Test;

class FrameReaderTest {

    private static ByteBuffer serverTextFrame(final String s) {
        final byte[] p = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final int len = p.length;
        final ByteBuffer buf;
        if (len <= 125) {
            buf = ByteBuffer.allocate(2 + len);
            buf.put((byte) 0x81);           // FIN|TEXT
            buf.put((byte) len);            // no MASK
        } else if (len <= 0xFFFF) {
            buf = ByteBuffer.allocate(2 + 2 + len);
            buf.put((byte) 0x81);
            buf.put((byte) 126);
            buf.putShort((short) len);
        } else {
            buf = ByteBuffer.allocate(2 + 8 + len);
            buf.put((byte) 0x81);
            buf.put((byte) 127);
            buf.putLong(len);
        }
        buf.put(p);
        buf.flip();
        return buf;
    }

    @Test
    void decode_small_text_unmasked() {
        final ByteBuffer f = serverTextFrame("hello");
        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(8192);
        assertTrue(d.decode(f));
        assertEquals(FrameOpcode.TEXT, d.opcode());
        assertTrue(d.fin());
        assertFalse(d.rsv1());
        assertEquals("hello", java.nio.charset.StandardCharsets.UTF_8.decode(d.payload()).toString());
    }

    @Test
    void decode_extended_126_length() {
        final byte[] p = new byte[300];
        for (int i = 0; i < p.length; i++) {
            p[i] = (byte) (i & 0xFF);
        }
        final ByteBuffer f = ByteBuffer.allocate(2 + 2 + p.length);
        f.put((byte) 0x82); // FIN|BINARY
        f.put((byte) 126);
        f.putShort((short) p.length);
        f.put(p);
        f.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(4096);
        assertTrue(d.decode(f));
        assertEquals(FrameOpcode.BINARY, d.opcode());
        final ByteBuffer payload = d.payload();
        final byte[] got = new byte[p.length];
        payload.get(got);
        assertArrayEquals(p, got);
    }

    @Test
    void decode_extended_127_length() {
        final int len = 66000;
        final byte[] p = new byte[len];
        Arrays.fill(p, (byte) 0xAB);
        final ByteBuffer f = ByteBuffer.allocate(2 + 8 + len);
        f.put((byte) 0x82); // FIN|BINARY
        f.put((byte) 127);
        f.putLong(len);
        f.put(p);
        f.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(len + 64);
        assertTrue(d.decode(f));
        assertEquals(len, d.payload().remaining());
    }

    @Test
    void masked_server_frame_is_rejected() {
        // FIN|TEXT, MASK bit set, len=0, + 4-byte mask key
        final ByteBuffer f = ByteBuffer.allocate(2 + 4);
        f.put((byte) 0x81);
        f.put((byte) 0x80);
        f.putInt(0x11223344);
        f.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(1024);
        assertThrows(WebSocketProtocolException.class, () -> d.decode(f));
    }

    @Test
    void rsv_bits_without_extension_is_rejected() {
        final ByteBuffer f = ByteBuffer.allocate(2);
        f.put((byte) 0xC1); // FIN|RSV1|TEXT
        f.put((byte) 0x00); // no mask, len=0
        f.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(1024); // strict by default
        final WebSocketProtocolException ex =
                assertThrows(WebSocketProtocolException.class, () -> d.decode(f));
        assertEquals(1002, ex.closeCode);
    }

    @Test
    void partial_buffer_returns_false_and_does_not_consume() {
        final ByteBuffer f = ByteBuffer.allocate(2);
        f.put((byte) 0x81);
        f.put((byte) 0x7E); // says 126 (extended), but no length bytes present
        f.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(1024);
        final int pos = f.position();
        assertFalse(d.decode(f));
        assertEquals(pos, f.position(), "decoder must reset position on incomplete frame");
    }

    @Test
    void negative_127_length_throws() {
        final ByteBuffer f = ByteBuffer.allocate(2 + 8);
        f.put((byte) 0x82);
        f.put((byte) 127);
        f.putLong(-1L);
        f.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(1024);
        assertThrows(WebSocketProtocolException.class, () -> d.decode(f));
    }

    @Test
    void frame_too_large_throws() {
        final int len = 2000;
        final ByteBuffer f = ByteBuffer.allocate(2 + 2 + len);
        f.put((byte) 0x82);
        f.put((byte) 126);
        f.putShort((short) len);
        f.put(new byte[len]);
        f.flip();

        final WebSocketFrameDecoder d = new WebSocketFrameDecoder(1024); // max frame size smaller than len
        assertThrows(WebSocketProtocolException.class, () -> d.decode(f));
    }
}
