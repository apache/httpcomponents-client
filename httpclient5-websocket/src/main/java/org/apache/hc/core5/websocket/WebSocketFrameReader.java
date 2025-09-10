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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.apache.hc.core5.util.Args;

class WebSocketFrameReader {

    private final WebSocketConfig config;
    private final InputStream inputStream;
    private final List<WebSocketExtension> extensions;
    private final WebSocketExtension rsv1Extension;
    private final WebSocketExtension rsv2Extension;
    private final WebSocketExtension rsv3Extension;
    private boolean continuationCompressed;

    WebSocketFrameReader(final WebSocketConfig config, final InputStream inputStream, final List<WebSocketExtension> extensions) {
        this.config = Args.notNull(config, "WebSocket config");
        this.inputStream = Args.notNull(inputStream, "Input stream");
        this.extensions = extensions != null ? extensions : Collections.emptyList();
        this.rsv1Extension = selectExtension(WebSocketExtension::usesRsv1);
        this.rsv2Extension = selectExtension(WebSocketExtension::usesRsv2);
        this.rsv3Extension = selectExtension(WebSocketExtension::usesRsv3);
        this.continuationCompressed = false;
    }

    WebSocketFrame readFrame() throws IOException {
        final int b1 = inputStream.read();
        if (b1 == -1) {
            return null;
        }
        final int b2 = readByte();
        final boolean fin = (b1 & 0x80) != 0;
        final boolean rsv1 = (b1 & 0x40) != 0;
        final boolean rsv2 = (b1 & 0x20) != 0;
        final boolean rsv3 = (b1 & 0x10) != 0;
        final int opcode = b1 & 0x0F;
        final WebSocketFrameType type = WebSocketFrameType.fromOpcode(opcode);
        if (type == null) {
            throw new WebSocketException("Unsupported opcode: " + opcode);
        }
        if (type.isControl() && (rsv1 || rsv2 || rsv3)) {
            throw new WebSocketException("Invalid RSV bits for control frame");
        }
        if (type == WebSocketFrameType.CONTINUATION && rsv1) {
            throw new WebSocketException("RSV1 must be 0 on continuation frames");
        }
        if (rsv1 && rsv1Extension == null) {
            throw new WebSocketException("Unexpected RSV1 bit");
        }
        if (rsv2 && rsv2Extension == null) {
            throw new WebSocketException("Unexpected RSV2 bit");
        }
        if (rsv3 && rsv3Extension == null) {
            throw new WebSocketException("Unexpected RSV3 bit");
        }
        final boolean masked = (b2 & 0x80) != 0;
        if (!masked) {
            throw new WebSocketException("Client frame is not masked");
        }
        long len = b2 & 0x7F;
        if (len == 126) {
            len = ((readByte() & 0xFF) << 8) | (readByte() & 0xFF);
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | (readByte() & 0xFF);
            }
        }
        if (len > Integer.MAX_VALUE) {
            throw new WebSocketException("Frame payload too large: " + len);
        }
        if (len > config.getMaxFramePayloadSize()) {
            throw new WebSocketException("Frame payload exceeds limit: " + len);
        }
        final byte[] maskKey = new byte[4];
        readFully(maskKey);
        final byte[] payload = new byte[(int) len];
        readFully(payload);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
        }
        ByteBuffer data = ByteBuffer.wrap(payload);
        if (rsv1 && rsv1Extension != null) {
            data = rsv1Extension.decode(type, fin, data);
            continuationCompressed = !fin && (type == WebSocketFrameType.TEXT || type == WebSocketFrameType.BINARY);
        } else if (type == WebSocketFrameType.CONTINUATION && continuationCompressed && rsv1Extension != null) {
            data = rsv1Extension.decode(type, fin, data);
            if (fin) {
                continuationCompressed = false;
            }
        } else if (type == WebSocketFrameType.CONTINUATION && fin) {
            continuationCompressed = false;
        }
        if (rsv2 && rsv2Extension != null) {
            data = rsv2Extension.decode(type, fin, data);
        }
        if (rsv3 && rsv3Extension != null) {
            data = rsv3Extension.decode(type, fin, data);
        }
        return new WebSocketFrame(fin, false, false, false, type, data);
    }

    private WebSocketExtension selectExtension(final Predicate<WebSocketExtension> predicate) {
        WebSocketExtension selected = null;
        for (final WebSocketExtension extension : extensions) {
            if (predicate.test(extension)) {
                if (selected != null) {
                    throw new IllegalStateException("Multiple extensions use the same RSV bit");
                }
                selected = extension;
            }
        }
        return selected;
    }

    private int readByte() throws IOException {
        final int b = inputStream.read();
        if (b == -1) {
            throw new IOException("Unexpected end of stream");
        }
        return b;
    }

    private void readFully(final byte[] buffer) throws IOException {
        int off = 0;
        while (off < buffer.length) {
            final int read = inputStream.read(buffer, off, buffer.length - off);
            if (read == -1) {
                throw new IOException("Unexpected end of stream");
            }
            off += read;
        }
    }
}
