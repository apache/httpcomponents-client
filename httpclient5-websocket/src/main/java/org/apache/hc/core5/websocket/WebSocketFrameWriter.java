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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.util.Args;

class WebSocketFrameWriter {

    private final OutputStream outputStream;
    private final List<WebSocketExtension> extensions;

    WebSocketFrameWriter(final OutputStream outputStream, final List<WebSocketExtension> extensions) {
        this.outputStream = Args.notNull(outputStream, "Output stream");
        this.extensions = extensions != null ? extensions : Collections.emptyList();
    }

    void writeText(final String text) throws IOException, WebSocketException {
        Args.notNull(text, "Text");
        writeDataFrame(WebSocketFrameType.TEXT, ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    void writeBinary(final ByteBuffer payload) throws IOException, WebSocketException {
        writeDataFrame(WebSocketFrameType.BINARY, payload);
    }

    void writePing(final ByteBuffer payload) throws IOException {
        writeFrame(WebSocketFrameType.PING, payload, false, false, false);
    }

    void writePong(final ByteBuffer payload) throws IOException {
        writeFrame(WebSocketFrameType.PONG, payload, false, false, false);
    }

    void writeClose(final int statusCode, final String reason) throws IOException {
        final byte[] reasonBytes = reason != null ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        final int len = 2 + reasonBytes.length;
        final ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.put((byte) ((statusCode >> 8) & 0xFF));
        buffer.put((byte) (statusCode & 0xFF));
        buffer.put(reasonBytes);
        buffer.flip();
        writeFrame(WebSocketFrameType.CLOSE, buffer, false, false, false);
    }

    private void writeDataFrame(final WebSocketFrameType type, final ByteBuffer payload) throws IOException, WebSocketException {
        ByteBuffer data = payload != null ? payload.asReadOnlyBuffer() : ByteBuffer.allocate(0);
        boolean rsv1 = false;
        boolean rsv2 = false;
        boolean rsv3 = false;
        for (final WebSocketExtension extension : extensions) {
            if (extension.usesRsv1()) {
                rsv1 = true;
            }
            if (extension.usesRsv2()) {
                rsv2 = true;
            }
            if (extension.usesRsv3()) {
                rsv3 = true;
            }
            data = extension.encode(type, true, data);
        }
        writeFrame(type, data, rsv1, rsv2, rsv3);
    }

    private void writeFrame(
            final WebSocketFrameType type,
            final ByteBuffer payload,
            final boolean rsv1,
            final boolean rsv2,
            final boolean rsv3) throws IOException {
        Args.notNull(type, "Frame type");
        final ByteBuffer buffer = payload != null ? payload.asReadOnlyBuffer() : ByteBuffer.allocate(0);
        final int payloadLen = buffer.remaining();
        int firstByte = 0x80 | (type.getOpcode() & 0x0F);
        if (rsv1) {
            firstByte |= 0x40;
        }
        if (rsv2) {
            firstByte |= 0x20;
        }
        if (rsv3) {
            firstByte |= 0x10;
        }
        outputStream.write(firstByte);
        if (payloadLen <= 125) {
            outputStream.write(payloadLen);
        } else if (payloadLen <= 0xFFFF) {
            outputStream.write(126);
            outputStream.write((payloadLen >> 8) & 0xFF);
            outputStream.write(payloadLen & 0xFF);
        } else {
            outputStream.write(127);
            for (int i = 7; i >= 0; i--) {
                outputStream.write((int) (((long) payloadLen >> (i * 8)) & 0xFF));
            }
        }
        if (payloadLen > 0) {
            final byte[] data = new byte[payloadLen];
            buffer.get(data);
            outputStream.write(data);
        }
        outputStream.flush();
    }
}
