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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebSocketFrameWriterTest {

    @Test
    void writesSmallBinaryFrame() throws Exception {
        final byte[] payload = new byte[] {0x01, 0x02, 0x03};
        final byte[] out = writeBinary(payload, Collections.emptyList());

        assertEquals((byte) 0x82, out[0]); // FIN + BINARY
        assertEquals(payload.length, out[1] & 0xFF);
        assertEquals(payload[0], out[2]);
        assertEquals(payload[1], out[3]);
        assertEquals(payload[2], out[4]);
    }

    @Test
    void writesExtendedLength126() throws Exception {
        final byte[] payload = new byte[126];
        final byte[] out = writeBinary(payload, Collections.emptyList());

        assertEquals((byte) 0x82, out[0]);
        assertEquals(126, out[1] & 0xFF);
        assertEquals(0, out[2] & 0xFF);
        assertEquals(126, out[3] & 0xFF);
        assertEquals(payload.length, out.length - 4);
    }

    @Test
    void writesExtendedLength127() throws Exception {
        final int len = 66000;
        final byte[] payload = new byte[len];
        final byte[] out = writeBinary(payload, Collections.emptyList());

        assertEquals((byte) 0x82, out[0]);
        assertEquals(127, out[1] & 0xFF);
        final long declared = ((long) (out[2] & 0xFF) << 56)
                | ((long) (out[3] & 0xFF) << 48)
                | ((long) (out[4] & 0xFF) << 40)
                | ((long) (out[5] & 0xFF) << 32)
                | ((long) (out[6] & 0xFF) << 24)
                | ((long) (out[7] & 0xFF) << 16)
                | ((long) (out[8] & 0xFF) << 8)
                | ((long) (out[9] & 0xFF));
        assertEquals(len, declared);
        assertEquals(payload.length, out.length - 10);
    }

    @Test
    void setsRsv1WhenExtensionUsesIt() throws Exception {
        final byte[] payload = new byte[] {0x01};
        final byte[] out = writeBinary(payload, Collections.singletonList(new Rsv1Extension()));
        assertTrue((out[0] & 0x40) != 0, "RSV1 must be set");
    }

    private static byte[] writeBinary(final byte[] payload, final List<WebSocketExtension> extensions) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WebSocketFrameWriter writer = new WebSocketFrameWriter(baos, extensions);
        writer.writeBinary(ByteBuffer.wrap(payload));
        return baos.toByteArray();
    }

    private static final class Rsv1Extension implements WebSocketExtension {
        @Override
        public String getName() {
            return "rsv1-test";
        }

        @Override
        public boolean usesRsv1() {
            return true;
        }
    }
}
