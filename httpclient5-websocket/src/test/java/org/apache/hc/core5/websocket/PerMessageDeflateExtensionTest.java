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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

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
