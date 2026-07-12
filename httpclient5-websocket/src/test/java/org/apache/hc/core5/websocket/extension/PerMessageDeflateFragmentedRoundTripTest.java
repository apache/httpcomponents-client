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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.websocket.extension.WebSocketExtensionChain.Decoder;
import org.apache.hc.core5.websocket.extension.WebSocketExtensionChain.Encoder;
import org.junit.jupiter.api.Test;

/**
 * Proves that a permessage-deflate message split across several WebSocket data frames survives a
 * compress/decompress round trip (RFC 7692 §7.2). The existing {@link MessageDeflateTest} only
 * exercises the single-frame path ({@code encode(data, true, true)}); the fragmented path
 * ({@code compressFragment}) is never decoded back.
 */
final class PerMessageDeflateFragmentedRoundTripTest {

    private static final String TEXT =
            "The quick brown fox jumps over the lazy dog. "
                    + "Pack my box with five dozen liquor jugs. "
                    + "How vexingly quick daft zebras jump! "
                    + "The five boxing wizards jump quickly.";

    private static byte[] encodeFragmented(final Encoder enc, final byte[] plain, final int fragments) {
        final ByteArrayOutputStream wire = new ByteArrayOutputStream();
        final int chunk = (plain.length + fragments - 1) / fragments;
        for (int i = 0; i < fragments; i++) {
            final int off = i * chunk;
            final int len = Math.min(chunk, plain.length - off);
            final byte[] part = new byte[len];
            System.arraycopy(plain, off, part, 0, len);
            final boolean first = i == 0;
            final boolean fin = i == fragments - 1;
            final byte[] out = enc.encode(part, first, fin).payload;
            wire.write(out, 0, out.length);
        }
        return wire.toByteArray();
    }

    @Test
    void fragmentedMessageRoundTripsAcrossThreeFrames() throws Exception {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, false, null, null);
        final Encoder enc = pmce.newEncoder();
        final Decoder dec = pmce.newDecoder();

        final byte[] plain = TEXT.getBytes(StandardCharsets.UTF_8);

        final byte[] wire = encodeFragmented(enc, plain, 3);
        final byte[] roundTrip = dec.decode(wire);

        assertArrayEquals(plain, roundTrip,
                "A fragmented compressed message must reassemble to the original bytes");
    }

    @Test
    void fragmentedMessageWithEmptyFinalFragmentRoundTrips() throws Exception {
        final PerMessageDeflate pmce = new PerMessageDeflate(true, false, false, null, null);
        final Encoder enc = pmce.newEncoder();
        final Decoder dec = pmce.newDecoder();

        final byte[] head = "hello".getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream wire = new ByteArrayOutputStream();
        final byte[] firstFragment = enc.encode(head, true, false).payload;
        wire.write(firstFragment, 0, firstFragment.length);
        final byte[] finalFragment = enc.encode(new byte[0], false, true).payload;
        wire.write(finalFragment, 0, finalFragment.length);

        // RFC 7692 section 7.2.3.6: an empty final fragment is the single octet 0x00.
        assertArrayEquals(new byte[]{0x00}, finalFragment,
                "an empty final fragment must be encoded as the single octet 0x00");
        assertArrayEquals(head, dec.decode(wire.toByteArray()),
                "a fragmented message whose final fragment is empty must round-trip");
    }

    @Test
    void fragmentedMessageMatchesSingleFrameEncoding() throws Exception {
        final byte[] plain = TEXT.getBytes(StandardCharsets.UTF_8);

        final byte[] fragmentedWire = encodeFragmented(
                new PerMessageDeflate(true, false, false, null, null).newEncoder(), plain, 4);
        final byte[] singleWire =
                new PerMessageDeflate(true, false, false, null, null).newEncoder()
                        .encode(plain, true, true).payload;

        // The wire bytes may differ (block boundaries), but both must decode to the same plaintext.
        final byte[] fromFragmented =
                new PerMessageDeflate(true, false, false, null, null).newDecoder().decode(fragmentedWire);
        final byte[] fromSingle =
                new PerMessageDeflate(true, false, false, null, null).newDecoder().decode(singleWire);

        assertArrayEquals(plain, fromSingle, "single-frame control must round-trip");
        assertArrayEquals(plain, fromFragmented, "fragmented message must round-trip to the same plaintext");
    }
}
