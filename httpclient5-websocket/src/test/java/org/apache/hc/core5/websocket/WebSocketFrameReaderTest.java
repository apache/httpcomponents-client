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
package org.apache.hc.core5.websocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class WebSocketFrameReaderTest {

    private static final byte[] MASK_KEY = new byte[] { 0x11, 0x22, 0x33, 0x44 };

    private static WebSocketFrame readFrame(final ByteBuffer frame, final int maxFrameSize) throws IOException {
        final ByteBuffer copy = frame.asReadOnlyBuffer();
        final byte[] data = new byte[copy.remaining()];
        copy.get(data);
        final WebSocketFrameReader reader = new WebSocketFrameReader(
                WebSocketConfig.custom().setMaxFramePayloadSize(maxFrameSize).build(),
                new ByteArrayInputStream(data),
                Collections.emptyList());
        return reader.readFrame();
    }

    private static ByteBuffer maskedFrame(final int firstByte, final byte[] payload) {
        final int len = payload.length;
        final int hdrExtra = len <= 125 ? 0 : len <= 0xFFFF ? 2 : 8;
        final ByteBuffer buf = ByteBuffer.allocate(2 + hdrExtra + 4 + len);
        buf.put((byte) firstByte);
        if (len <= 125) {
            buf.put((byte) (0x80 | len));
        } else if (len <= 0xFFFF) {
            buf.put((byte) (0x80 | 126));
            buf.putShort((short) len);
        } else {
            buf.put((byte) (0x80 | 127));
            buf.putLong(len);
        }
        buf.put(MASK_KEY);
        for (int i = 0; i < len; i++) {
            buf.put((byte) (payload[i] ^ MASK_KEY[i % 4]));
        }
        buf.flip();
        return buf;
    }

    private static ByteBuffer unmaskedFrame(final int firstByte, final byte[] payload) {
        final int len = payload.length;
        final int hdrExtra = len <= 125 ? 0 : len <= 0xFFFF ? 2 : 8;
        final ByteBuffer buf = ByteBuffer.allocate(2 + hdrExtra + len);
        buf.put((byte) firstByte);
        if (len <= 125) {
            buf.put((byte) len);
        } else if (len <= 0xFFFF) {
            buf.put((byte) 126);
            buf.putShort((short) len);
        } else {
            buf.put((byte) 127);
            buf.putLong(len);
        }
        buf.put(payload);
        buf.flip();
        return buf;
    }

    @Test
    void decode_small_text_masked() throws Exception {
        final byte[] p = "hello".getBytes(StandardCharsets.UTF_8);
        final ByteBuffer f = maskedFrame(0x81, p); // FIN|TEXT
        final WebSocketFrame frame = readFrame(f, 8192);
        assertNotNull(frame);
        assertEquals(WebSocketFrameType.TEXT, frame.getType());
        assertEquals("hello", StandardCharsets.UTF_8.decode(frame.getPayload()).toString());
    }

    @Test
    void decode_extended_126_length() throws Exception {
        final byte[] p = new byte[300];
        for (int i = 0; i < p.length; i++) {
            p[i] = (byte) (i & 0xFF);
        }
        final ByteBuffer f = maskedFrame(0x82, p); // FIN|BINARY
        final WebSocketFrame frame = readFrame(f, 4096);
        assertNotNull(frame);
        assertEquals(WebSocketFrameType.BINARY, frame.getType());
        final ByteBuffer payload = frame.getPayload();
        final byte[] got = new byte[p.length];
        payload.get(got);
        assertArrayEquals(p, got);
    }

    @Test
    void decode_extended_127_length() throws Exception {
        final int len = 66000;
        final byte[] p = new byte[len];
        Arrays.fill(p, (byte) 0xAB);
        final ByteBuffer f = maskedFrame(0x82, p); // FIN|BINARY
        final WebSocketFrame frame = readFrame(f, len + 64);
        assertNotNull(frame);
        assertEquals(len, frame.getPayload().remaining());
    }

    @Test
    void unmasked_client_frame_is_rejected() {
        final ByteBuffer f = unmaskedFrame(0x81, new byte[0]); // FIN|TEXT, no MASK
        assertThrows(WebSocketException.class, () -> readFrame(f, 1024));
    }

    @Test
    void rsv_bits_without_extension_is_rejected() {
        final ByteBuffer f = maskedFrame(0xC1, new byte[0]); // FIN|RSV1|TEXT
        assertThrows(WebSocketException.class, () -> readFrame(f, 1024));
    }

    @Test
    void truncated_frame_throws() {
        final ByteBuffer f = ByteBuffer.allocate(2);
        f.put((byte) 0x81);
        f.put((byte) 0xFE); // MASK|126, but missing extended length and mask
        f.flip();
        assertThrows(IOException.class, () -> readFrame(f, 1024));
    }

    @Test
    void frame_too_large_throws() {
        final int len = 2000;
        final ByteBuffer f = maskedFrame(0x82, new byte[len]);
        assertThrows(WebSocketException.class, () -> readFrame(f, 1024));
    }
}
