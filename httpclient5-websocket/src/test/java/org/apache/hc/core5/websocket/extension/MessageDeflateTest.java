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
package org.apache.hc.core5.websocket.extension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;
import org.apache.hc.core5.websocket.frame.FrameHeaderBits;
import org.junit.jupiter.api.Test;

final class MessageDeflateTest {

    @Test
    void rsvMask_isRSV1() {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, false, null, null);
        assertEquals(FrameHeaderBits.RSV1, pmce.rsvMask());
    }

    @Test
    void encode_setsRSVOnlyOnFirst() {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, false, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();

        final byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        final WebSocketExtensionChain.Encoded first = enc.encode(data, true, false);
        final WebSocketExtensionChain.Encoded cont = enc.encode(data, false, true);

        assertTrue(first.setRsvOnFirst, "RSV on first fragment");
        assertFalse(cont.setRsvOnFirst, "no RSV on continuation");
        assertNotEquals(0, first.payload.length);
        assertNotEquals(0, cont.payload.length);
    }

    @Test
    void roundTrip_message() throws Exception {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, true, true, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();
        final WebSocketExtensionChain.Decoder dec = pmce.newDecoder();

        final String s = "The quick brown fox jumps over the lazy dog. "
                + "The quick brown fox jumps over the lazy dog.";
        final byte[] plain = s.getBytes(StandardCharsets.UTF_8);

        // Single-frame message: first=true, fin=true
        final byte[] wire = enc.encode(plain, true, true).payload;

        assertTrue(wire.length > 0);
        assertFalse(endsWithTail(wire), "tail must be stripped on wire");

        final byte[] roundTrip = dec.decode(wire);
        assertArrayEquals(plain, roundTrip);
    }

    @Test
    void encode_emptyMessage_isSingleZeroOctet() throws Exception {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, false, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();

        final WebSocketExtensionChain.Encoded encoded = enc.encode(new byte[0], true, true);

        // RFC 7692 section 7.2.3.6: an empty compressed message is the single octet 0x00.
        assertArrayEquals(new byte[]{0x00}, encoded.payload);
        assertTrue(encoded.setRsvOnFirst, "RSV1 on the first (and only) frame");
        assertArrayEquals(new byte[0], pmce.newDecoder().decode(encoded.payload),
                "an empty compressed message must round-trip to empty");
    }

    @Test
    void encode_emptyMessage_withContextTakeover_roundTrips() throws Exception {
        // clientNoContextTakeover = false: the deflater keeps its context across messages.
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, false, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();
        final WebSocketExtensionChain.Decoder dec = pmce.newDecoder();

        final byte[] hello = "hello".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(hello, dec.decode(enc.encode(hello, true, true).payload));

        final byte[] empty = enc.encode(new byte[0], true, true).payload;
        assertArrayEquals(new byte[]{0x00}, empty, "empty message compresses to 0x00 with context takeover");
        assertArrayEquals(new byte[0], dec.decode(empty));
    }

    @Test
    void encode_emptyMessage_withNoContextTakeover_roundTrips() throws Exception {
        // clientNoContextTakeover = true: the deflater is reset after each message.
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, true, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();
        final WebSocketExtensionChain.Decoder dec = pmce.newDecoder();

        final byte[] empty = enc.encode(new byte[0], true, true).payload;
        assertArrayEquals(new byte[]{0x00}, empty, "empty message compresses to 0x00 with no-context-takeover");
        assertArrayEquals(new byte[0], dec.decode(empty));
    }

    @Test
    void decode_withinLimit_succeeds() throws Exception {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, true, true, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();
        final WebSocketExtensionChain.Decoder dec = pmce.newDecoder();

        final byte[] plain = "hello world hello world hello world".getBytes(StandardCharsets.UTF_8);
        final byte[] wire = enc.encode(plain, true, true).payload;

        // Limit comfortably above the inflated size.
        final byte[] roundTrip = dec.decode(wire, plain.length + 16);
        assertArrayEquals(plain, roundTrip);
    }

    @Test
    void decode_inflationBomb_isRejectedDuringInflate() {
        // A small, highly compressible payload that inflates to a much larger plaintext.
        final byte[] plain = new byte[64 * 1024];
        Arrays.fill(plain, (byte) 'A');

        final PerMessageDeflate pmce = new PerMessageDeflate(true, true, true, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();
        final WebSocketExtensionChain.Decoder dec = pmce.newDecoder();

        final byte[] wire = enc.encode(plain, true, true).payload;
        // Sanity: the compressed wire form is far smaller than the inflated payload.
        assertTrue(wire.length < plain.length / 4,
                "test setup: payload should be highly compressible, was " + wire.length + " vs " + plain.length);

        // maxDecodedSize is well below the inflated size; decode must abort with 1009.
        final WebSocketProtocolException ex = assertThrows(WebSocketProtocolException.class,
                () -> dec.decode(wire, 1024L));
        assertEquals(1009, ex.closeCode);
        assertEquals("Message too big", ex.getMessage());
    }

    @Test
    void decode_zeroLimitMeansUnlimited() throws Exception {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, true, true, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();
        final WebSocketExtensionChain.Decoder dec = pmce.newDecoder();

        final byte[] plain = new byte[8 * 1024];
        Arrays.fill(plain, (byte) 'B');
        final byte[] wire = enc.encode(plain, true, true).payload;

        final byte[] roundTrip = dec.decode(wire, 0L);
        assertArrayEquals(plain, roundTrip);
    }

    @Test
    void encoderCloseReleasesDeflater() {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, false, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();
        enc.encode("hello".getBytes(StandardCharsets.UTF_8), true, true);
        enc.close();
        // After close() the Deflater has been ended; reusing the encoder must fail.
        assertThrows(Exception.class,
                () -> enc.encode("again".getBytes(StandardCharsets.UTF_8), true, true));
    }

    @Test
    void decoderCloseReleasesInflater() throws Exception {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, false, null, null);
        final WebSocketExtensionChain.Encoder enc = pmce.newEncoder();
        final WebSocketExtensionChain.Decoder dec = pmce.newDecoder();
        final byte[] wire = enc.encode("hello".getBytes(StandardCharsets.UTF_8), true, true).payload;
        dec.decode(wire);
        dec.close();
        assertThrows(Exception.class, () -> dec.decode(wire));
    }

    private static boolean endsWithTail(final byte[] b) {
        if (b.length < 4) {
            return false;
        }
        return b[b.length - 4] == 0x00 && b[b.length - 3] == 0x00 && (b[b.length - 2] & 0xFF) == 0xFF && (b[b.length - 1] & 0xFF) == 0xFF;
    }
}
