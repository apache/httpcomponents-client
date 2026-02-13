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
package org.apache.hc.core5.websocket.frame;

import static org.apache.hc.core5.websocket.frame.FrameHeaderBits.RSV1;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


class FrameWriterTest {

    private static class Parsed {
        int b0;
        int b1;
        int opcode;
        boolean fin;
        boolean mask;
        long len;
        final byte[] maskKey = new byte[4];
        int headerLen;
        ByteBuffer payloadSlice;
    }

    private static Parsed parse(final ByteBuffer frame) {
        final ByteBuffer frameCopy = frame.asReadOnlyBuffer();
        final Parsed r = new Parsed();
        r.b0 = frameCopy.get() & 0xFF;
        r.fin = (r.b0 & 0x80) != 0;
        r.opcode = r.b0 & 0x0F;

        r.b1 = frameCopy.get() & 0xFF;
        r.mask = (r.b1 & 0x80) != 0;
        final int low = r.b1 & 0x7F;
        if (low <= 125) {
            r.len = low;
        } else if (low == 126) {
            r.len = frameCopy.getShort() & 0xFFFF;
        } else {
            r.len = frameCopy.getLong();
        }

        if (r.mask) {
            frameCopy.get(r.maskKey);
        }
        r.headerLen = frameCopy.position();
        r.payloadSlice = frameCopy.slice();
        return r;
    }

    private static byte[] unmask(final Parsed p) {
        final byte[] out = new byte[(int) p.len];
        for (int i = 0; i < out.length; i++) {
            int b = p.payloadSlice.get(i) & 0xFF;
            b ^= p.maskKey[i & 3] & 0xFF;
            out[i] = (byte) b;
        }
        return out;
    }

    @Test
    void text_small_masked_roundtrip() {
        final WebSocketFrameWriter w = new WebSocketFrameWriter();
        final ByteBuffer f = w.text("hello", true);
        final Parsed p = parse(f);
        assertTrue(p.fin);
        assertEquals(FrameOpcode.TEXT, p.opcode);
        assertTrue(p.mask, "client frame must be masked");
        assertEquals(5, p.len);
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), unmask(p));
    }

    @Test
    void binary_len_126_masked_roundtrip() {
        final byte[] payload = new byte[300];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xFF);
        }

        final WebSocketFrameWriter w = new WebSocketFrameWriter();
        final ByteBuffer f = w.binary(ByteBuffer.wrap(payload), true);

        final Parsed p = parse(f);
        assertTrue(p.mask);
        assertEquals(FrameOpcode.BINARY, p.opcode);
        assertEquals(300, p.len);
        assertArrayEquals(payload, unmask(p));
    }

    @Test
    void binary_len_127_masked_roundtrip() {
        final int len = 70000;
        final byte[] payload = new byte[len];
        Arrays.fill(payload, (byte) 0xA5);

        final WebSocketFrameWriter w = new WebSocketFrameWriter();
        final ByteBuffer f = w.binary(ByteBuffer.wrap(payload), true);

        final Parsed p = parse(f);
        assertTrue(p.mask);
        assertEquals(FrameOpcode.BINARY, p.opcode);
        assertEquals(len, p.len);
        assertArrayEquals(payload, unmask(p));
    }

    @Test
    void rsv1_set_with_frameWithRSV() {
        final WebSocketFrameWriter w = new WebSocketFrameWriter();
        final ByteBuffer payload = StandardCharsets.UTF_8.encode("x");
        // Use RSV1 bit
        final ByteBuffer f = w.frameWithRSV(FrameOpcode.TEXT, payload, true, true, RSV1);
        final Parsed p = parse(f);
        assertTrue(p.fin);
        assertEquals(FrameOpcode.TEXT, p.opcode);
        assertTrue((p.b0 & RSV1) != 0, "RSV1 must be set");
        assertArrayEquals("x".getBytes(StandardCharsets.UTF_8), unmask(p));
    }

    @Test
    void close_frame_contains_code_and_reason() {
        final WebSocketFrameWriter w = new WebSocketFrameWriter();
        final ByteBuffer f = w.close(1000, "done");
        final Parsed p = parse(f);
        assertTrue(p.mask);
        assertEquals(FrameOpcode.CLOSE, p.opcode);
        assertTrue(p.len >= 2);

        final byte[] raw = unmask(p);
        final int code = (raw[0] & 0xFF) << 8 | raw[1] & 0xFF;
        final String reason = new String(raw, 2, raw.length - 2, StandardCharsets.UTF_8);

        assertEquals(1000, code);
        assertEquals("done", reason);
    }

    @Test
    void closeEcho_masks_and_preserves_payload() {
        // Build a close payload manually
        final byte[] reason = "bye".getBytes(StandardCharsets.UTF_8);
        final ByteBuffer payload = ByteBuffer.allocate(2 + reason.length);
        payload.put((byte) (1000 >>> 8));
        payload.put((byte) (1000 & 0xFF));
        payload.put(reason);
        payload.flip();

        final WebSocketFrameWriter w = new WebSocketFrameWriter();
        final ByteBuffer f = w.closeEcho(payload);
        final Parsed p = parse(f);

        assertTrue(p.mask);
        assertEquals(FrameOpcode.CLOSE, p.opcode);
        assertEquals(2 + reason.length, p.len);

        final byte[] got = unmask(p);
        assertEquals(1000, (got[0] & 0xFF) << 8 | got[1] & 0xFF);
        assertEquals("bye", new String(got, 2, got.length - 2, StandardCharsets.UTF_8));
    }
}
