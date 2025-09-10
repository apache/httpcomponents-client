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
package org.apache.hc.core5.websocket.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class CloseCodecTest {

    @Test
    void readEmptyIs1005() {
        final ByteBuffer empty = ByteBuffer.allocate(0);
        assertEquals(1005, CloseCodec.readCloseCode(empty.asReadOnlyBuffer()));
        assertEquals("", CloseCodec.readCloseReason(empty.asReadOnlyBuffer()));
    }

    @Test
    void readCodeAndReason() {
        final ByteBuffer payload = ByteBuffer.allocate(2 + 4);
        payload.put((byte) 0x03).put((byte) 0xE8); // 1000
        payload.put(StandardCharsets.UTF_8.encode("done"));
        payload.flip();

        // Use the SAME buffer so the position advances
        final ByteBuffer buf = payload.asReadOnlyBuffer();
        assertEquals(1000, CloseCodec.readCloseCode(buf));     // advances position by 2
        assertEquals("done", CloseCodec.readCloseReason(buf)); // reads remaining bytes only
    }

    @Test
    void validateCloseCodes() {
        assertTrue(CloseCodec.isValidToSend(1000));
        assertTrue(CloseCodec.isValidToReceive(1000));
        assertTrue(CloseCodec.isValidToSend(3000));
        assertTrue(CloseCodec.isValidToReceive(3000));

        assertFalse(CloseCodec.isValidToSend(1005));
        assertFalse(CloseCodec.isValidToReceive(1005));
        assertFalse(CloseCodec.isValidToSend(1006));
        assertFalse(CloseCodec.isValidToReceive(1006));
        assertFalse(CloseCodec.isValidToSend(1015));
        assertFalse(CloseCodec.isValidToReceive(1015));

        assertFalse(CloseCodec.isValidToSend(2000));
        assertFalse(CloseCodec.isValidToReceive(2000));
    }

    @Test
    void truncateReasonUtf8_capsAt123Bytes() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 130; i++) {
            sb.append('a');
        }
        final String truncated = CloseCodec.truncateReasonUtf8(sb.toString());
        assertEquals(123, truncated.getBytes(StandardCharsets.UTF_8).length);
    }
}
