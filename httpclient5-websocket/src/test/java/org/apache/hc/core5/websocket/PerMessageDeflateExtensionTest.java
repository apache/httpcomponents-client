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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;

import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;
import org.junit.jupiter.api.Test;

class PerMessageDeflateExtensionTest {

    @Test
    void decodesFragmentedMessage() throws Exception {
        final byte[] plain = "fragmented message".getBytes(StandardCharsets.UTF_8);
        final byte[] compressed = deflateWithSyncFlush(plain);
        final int mid = compressed.length / 2;
        final ByteBuffer part1 = ByteBuffer.wrap(compressed, 0, mid);
        final ByteBuffer part2 = ByteBuffer.wrap(compressed, mid, compressed.length - mid);

        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        final ByteBuffer out1 = ext.decode(WebSocketFrameType.TEXT, false, part1);
        final ByteBuffer out2 = ext.decode(WebSocketFrameType.CONTINUATION, true, part2);

        final ByteArrayOutputStream joined = new ByteArrayOutputStream();
        joined.write(toBytes(out1));
        joined.write(toBytes(out2));

        assertEquals("fragmented message", WebSocketSession.decodeText(ByteBuffer.wrap(joined.toByteArray())));
    }

    @Test
    void decodeWithinLimitSucceeds() throws Exception {
        final byte[] plain = "hello world hello world hello world".getBytes(StandardCharsets.UTF_8);
        final byte[] compressed = deflateWithSyncFlush(plain);

        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        final ByteBuffer out = ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed), plain.length + 16L);

        assertArrayEquals(plain, toBytes(out));
    }

    @Test
    void decodeInflationBombIsRejectedDuringInflate() {
        final byte[] plain = new byte[64 * 1024];
        Arrays.fill(plain, (byte) 'A');
        final byte[] compressed = deflateWithSyncFlush(plain);

        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        final WebSocketProtocolException ex = assertThrows(WebSocketProtocolException.class,
                () -> ext.decode(WebSocketFrameType.BINARY, true, ByteBuffer.wrap(compressed), 1024L));
        assertEquals(1009, ex.closeCode);
        assertEquals("Message too big", ex.getMessage());
    }

    @Test
    void decodeZeroLimitMeansUnlimited() throws Exception {
        final byte[] plain = new byte[8 * 1024];
        Arrays.fill(plain, (byte) 'B');
        final byte[] compressed = deflateWithSyncFlush(plain);

        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        final ByteBuffer out = ext.decode(WebSocketFrameType.BINARY, true, ByteBuffer.wrap(compressed), 0L);

        assertArrayEquals(plain, toBytes(out));
    }

    @Test
    void fragmentedEncodeRoundTripsThroughDecode() throws Exception {
        // Encode a message as three frames (fin=false, false, true), then decode them back.
        // Non-final fragments must retain the 00 00 FF FF flush trailer so the reassembled
        // DEFLATE stream stays valid (RFC 7692 section 7.2.1).
        final PerMessageDeflateExtension enc = new PerMessageDeflateExtension();
        final byte[] p1 = "The quick brown fox ".getBytes(StandardCharsets.UTF_8);
        final byte[] p2 = "jumps over the lazy ".getBytes(StandardCharsets.UTF_8);
        final byte[] p3 = "dog, and again the fox.".getBytes(StandardCharsets.UTF_8);

        final ByteBuffer f1 = enc.encode(WebSocketFrameType.TEXT, false, ByteBuffer.wrap(p1));
        final ByteBuffer f2 = enc.encode(WebSocketFrameType.CONTINUATION, false, ByteBuffer.wrap(p2));
        final ByteBuffer f3 = enc.encode(WebSocketFrameType.CONTINUATION, true, ByteBuffer.wrap(p3));

        final PerMessageDeflateExtension dec = new PerMessageDeflateExtension();
        final ByteArrayOutputStream joined = new ByteArrayOutputStream();
        joined.write(toBytes(dec.decode(WebSocketFrameType.TEXT, false, f1)));
        joined.write(toBytes(dec.decode(WebSocketFrameType.CONTINUATION, false, f2)));
        joined.write(toBytes(dec.decode(WebSocketFrameType.CONTINUATION, true, f3)));

        final byte[] expected = new byte[p1.length + p2.length + p3.length];
        System.arraycopy(p1, 0, expected, 0, p1.length);
        System.arraycopy(p2, 0, expected, p1.length, p2.length);
        System.arraycopy(p3, 0, expected, p1.length + p2.length, p3.length);
        assertArrayEquals(expected, joined.toByteArray());
    }

    @Test
    void encodesEmptyMessageAsSingleZeroOctet() throws Exception {
        final PerMessageDeflateExtension enc = new PerMessageDeflateExtension();
        final ByteBuffer encoded = enc.encode(WebSocketFrameType.BINARY, true, ByteBuffer.allocate(0));

        // RFC 7692 section 7.2.3.6: an empty compressed message is the single octet 0x00.
        assertArrayEquals(new byte[]{0x00}, toBytes(encoded));

        final PerMessageDeflateExtension dec = new PerMessageDeflateExtension();
        assertArrayEquals(new byte[0],
                toBytes(dec.decode(WebSocketFrameType.BINARY, true, ByteBuffer.wrap(new byte[]{0x00}))));
    }

    @Test
    void encodesEmptyMessageAsZeroOctetWithContextTakeover() throws Exception {
        // The default extension keeps deflate/inflate context across messages.
        final PerMessageDeflateExtension enc = new PerMessageDeflateExtension();
        final PerMessageDeflateExtension dec = new PerMessageDeflateExtension();

        final byte[] hello = "hello".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(hello, toBytes(dec.decode(WebSocketFrameType.TEXT, true,
                enc.encode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(hello)))));

        // The deflater now holds context, so an empty message flushes to only 00 00 FF FF; it must
        // still encode to the single octet 0x00 (RFC 7692 section 7.2.3.6), not to an empty payload.
        final ByteBuffer emptyEncoded = enc.encode(WebSocketFrameType.TEXT, true, ByteBuffer.allocate(0));
        assertArrayEquals(new byte[]{0x00}, toBytes(emptyEncoded));
        assertArrayEquals(new byte[0], toBytes(dec.decode(WebSocketFrameType.TEXT, true, emptyEncoded)));
    }

    @Test
    void closeReleasesNativeCodecs() throws Exception {
        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        ext.encode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
        ext.close();
        // After close() the Deflater has been ended; reusing the extension must fail.
        assertThrows(Exception.class,
                () -> ext.encode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap("again".getBytes(StandardCharsets.UTF_8))));
    }

    private static byte[] deflateWithSyncFlush(final byte[] input) {
        final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(input);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(128, input.length / 2));
        final byte[] buffer = new byte[8192];
        while (!deflater.needsInput()) {
            final int count = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH);
            if (count > 0) {
                out.write(buffer, 0, count);
            } else {
                break;
            }
        }
        deflater.end();
        final byte[] data = out.toByteArray();
        if (data.length >= 4) {
            final byte[] trimmed = new byte[data.length - 4];
            System.arraycopy(data, 0, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return data;
    }

    private static byte[] toBytes(final ByteBuffer buf) {
        final ByteBuffer copy = buf.asReadOnlyBuffer();
        final byte[] out = new byte[copy.remaining()];
        copy.get(out);
        return out;
    }
}
