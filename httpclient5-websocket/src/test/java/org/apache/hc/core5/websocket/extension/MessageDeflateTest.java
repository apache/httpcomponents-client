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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

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

    private static boolean endsWithTail(final byte[] b) {
        if (b.length < 4) {
            return false;
        }
        return b[b.length - 4] == 0x00 && b[b.length - 3] == 0x00 && (b[b.length - 2] & 0xFF) == 0xFF && (b[b.length - 1] & 0xFF) == 0xFF;
    }
}
