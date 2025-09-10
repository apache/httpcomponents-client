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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebSocketFrameCodecTest {

    @Test
    void readsMaskedTextFrame() throws Exception {
        final byte[] payload = "hi".getBytes(StandardCharsets.UTF_8);
        final byte[] mask = new byte[] { 1, 2, 3, 4 };
        final byte[] masked = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            masked[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x81);
        out.write(0x80 | payload.length);
        out.write(mask);
        out.write(masked);
        final WebSocketFrameReader reader = new WebSocketFrameReader(
                WebSocketConfig.DEFAULT,
                new ByteArrayInputStream(out.toByteArray()),
                Collections.emptyList());
        final WebSocketFrame frame = reader.readFrame();
        Assertions.assertNotNull(frame);
        Assertions.assertEquals(WebSocketFrameType.TEXT, frame.getType());
        Assertions.assertEquals("hi", WebSocketSession.decodeText(frame.getPayload()));
    }

    @Test
    void writesUnmaskedServerFrame() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final WebSocketFrameWriter writer = new WebSocketFrameWriter(out, Collections.emptyList());
        writer.writeBinary(ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
        final byte[] data = out.toByteArray();
        Assertions.assertEquals((byte) 0x82, data[0]);
        Assertions.assertEquals((byte) 0x03, data[1]);
        Assertions.assertEquals((byte) 1, data[2]);
        Assertions.assertEquals((byte) 2, data[3]);
        Assertions.assertEquals((byte) 3, data[4]);
    }

    @Test
    void encodesAndDecodesPerMessageDeflate() throws Exception {
        final PerMessageDeflateExtension extension = new PerMessageDeflateExtension();
        final ByteBuffer payload = ByteBuffer.wrap("compress me".getBytes(StandardCharsets.UTF_8));
        final ByteBuffer encoded = extension.encode(WebSocketFrameType.TEXT, true, payload);
        final ByteBuffer decoded = extension.decode(WebSocketFrameType.TEXT, true, encoded);
        Assertions.assertEquals("compress me", WebSocketSession.decodeText(decoded));
    }
}
