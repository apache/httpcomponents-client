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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    void decodesMessagesEndingWithBfinalBlock() {
        // RFC 7692 section 7.2.3.4: "Hello" compressed using a DEFLATE block with BFINAL set to 1.
        final byte[] compressed = {(byte) 0xf3, 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, 0x07, 0x00, 0x00};
        final byte[] plain = "Hello".getBytes(StandardCharsets.UTF_8);

        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertArrayEquals(plain, toBytes(ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed))));
            assertArrayEquals(plain, toBytes(ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed))));
        });
    }

    @Test
    void decodesBlocksFollowingBfinalBlock() {
        // RFC 7692 section 7.2.1: a BFINAL block may be followed by further blocks. "Hello" in a
        // DEFLATE block with BFINAL set to 1 (section 7.2.3.4) followed by "Hello" in a DEFLATE
        // block with no compression (section 7.2.3.3).
        final byte[] compressed = {
                (byte) 0xf3, 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, 0x07, 0x00,
                0x00, 0x05, 0x00, (byte) 0xfa, (byte) 0xff, 0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x00};
        final byte[] plain = "HelloHello".getBytes(StandardCharsets.UTF_8);

        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertArrayEquals(plain, toBytes(ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed))));
            assertArrayEquals(plain, toBytes(ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed))));
        });
    }

    @Test
    void decodesFragmentedMessageWithBfinalBlock() {
        // Same message as above, split into two frames at every possible point: at the BFINAL
        // block boundary, inside the BFINAL block and inside the block that follows it.
        final byte[] compressed = {
                (byte) 0xf3, 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, 0x07, 0x00,
                0x00, 0x05, 0x00, (byte) 0xfa, (byte) 0xff, 0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x00};
        final byte[] plain = "HelloHello".getBytes(StandardCharsets.UTF_8);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (int split = 1; split < compressed.length; split++) {
                final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
                final ByteBuffer part1 = ByteBuffer.wrap(compressed, 0, split);
                final ByteBuffer part2 = ByteBuffer.wrap(compressed, split, compressed.length - split);

                final ByteArrayOutputStream joined = new ByteArrayOutputStream();
                joined.write(toBytes(ext.decode(WebSocketFrameType.TEXT, false, part1)));
                joined.write(toBytes(ext.decode(WebSocketFrameType.CONTINUATION, true, part2)));
                assertArrayEquals(plain, joined.toByteArray(), "split at " + split);
            }
        });
    }

    @Test
    void decodesIndependentMessageAfterBfinalMessage_noSharedWindowNeeded() throws Exception {
        // Deflater.finish() alone omits the extra 0x00 octet section 7.2.3.4 requires a BFINAL
        // message to end with, so these two unrelated messages are themselves non-conformant on
        // the wire; a conformant sender never produces this. It still has to decode: with no extra
        // octet, only this method's own synthetic sync-flush tail (the 4 bytes appended to every
        // fin frame) is left unconsumed when the block finishes, and feeding just that into the
        // reset inflater leaves it mid-header rather than at a clean block boundary, corrupting
        // whatever this decoder processes next. Independent of the window-sharing fix above:
        // message 2 needs none of message 1's LZ77 window, so this isolates the tail-handling
        // guard, regardless of whether a dictionary is involved.
        final byte[] compressed1 = deflateFinish("Hello".getBytes(StandardCharsets.UTF_8), null);
        final byte[] compressed2 = deflateFinish("World!!".getBytes(StandardCharsets.UTF_8), null);

        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8),
                    toBytes(ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed1))));
            assertArrayEquals("World!!".getBytes(StandardCharsets.UTF_8),
                    toBytes(ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed2))));
        });
    }

    @Test
    void decodesMessageAfterLargeBfinalMessage_windowCoversLast32KiB() throws Exception {
        // Message 1: a real BFINAL-terminated DEFLATE stream (Deflater.finish(), not SYNC_FLUSH)
        // producing more than 32 KiB of plaintext, so the sliding window the mid-stream reset
        // re-primes must have wrapped at least once by the time message 1 finishes.
        final byte[] prefix = new byte[40_000];
        for (int i = 0; i < prefix.length; i++) {
            prefix[i] = (byte) (i % 251);
        }
        final byte[] compressed1 = deflateFinish(prefix, null);

        // Message 2 is only producible by a sender holding the same last-32-KiB window this
        // decoder is expected to retain: it is compressed against exactly that window as its
        // dictionary, so it decodes correctly only if the decoder's own window matches it byte
        // for byte, not just "some" window.
        final byte[] window = Arrays.copyOfRange(prefix, prefix.length - 32768, prefix.length);
        final byte[] message2Plain = Arrays.copyOfRange(window, window.length - 300, window.length);
        final byte[] compressed2 = deflateFinish(message2Plain, window);
        assertTrue(compressed2.length < message2Plain.length,
                "message 2 must compress to a real back-reference, not literal bytes");

        final PerMessageDeflateExtension ext = new PerMessageDeflateExtension();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertArrayEquals(prefix, toBytes(ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed1))));
            assertArrayEquals(message2Plain,
                    toBytes(ext.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressed2))));
        });
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

    /** Real BFINAL=1-terminated raw DEFLATE stream, optionally primed with a dictionary. */
    private static byte[] deflateFinish(final byte[] input, final byte[] dictionary) {
        final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        if (dictionary != null) {
            deflater.setDictionary(dictionary);
        }
        deflater.setInput(input);
        deflater.finish();
        final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(128, input.length / 2));
        final byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            final int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
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
