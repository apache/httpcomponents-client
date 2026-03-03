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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class PerMessageDeflateExtension implements WebSocketExtension {

    private static final byte[] TAIL = new byte[]{0x00, 0x00, (byte) 0xFF, (byte) 0xFF};
    private static final int MIN_WINDOW_BITS = 8;
    private static final int MAX_WINDOW_BITS = 15;

    private final boolean serverNoContextTakeover;
    private final boolean clientNoContextTakeover;
    private final Integer clientMaxWindowBits;
    private final Integer serverMaxWindowBits;

    private final Inflater inflater = new Inflater(true);
    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private boolean decodingMessage;

    public PerMessageDeflateExtension() {
        this(false, false, MAX_WINDOW_BITS, MAX_WINDOW_BITS);
    }

    PerMessageDeflateExtension(
            final boolean serverNoContextTakeover,
            final boolean clientNoContextTakeover,
            final Integer clientMaxWindowBits,
            final Integer serverMaxWindowBits) {
        this.serverNoContextTakeover = serverNoContextTakeover;
        this.clientNoContextTakeover = clientNoContextTakeover;
        this.clientMaxWindowBits = clientMaxWindowBits;
        this.serverMaxWindowBits = serverMaxWindowBits;
        this.decodingMessage = false;
    }

    @Override
    public String getName() {
        return "permessage-deflate";
    }

    @Override
    public boolean usesRsv1() {
        return true;
    }

    @Override
    public ByteBuffer decode(final WebSocketFrameType type, final boolean fin, final ByteBuffer payload) throws WebSocketException {
        if (!isDataFrame(type) && type != WebSocketFrameType.CONTINUATION) {
            throw new WebSocketException("Unsupported frame type for permessage-deflate: " + type);
        }
        if (type == WebSocketFrameType.CONTINUATION && !decodingMessage) {
            throw new WebSocketException("Unexpected continuation frame for permessage-deflate");
        }
        final byte[] input = toByteArray(payload);
        final byte[] withTail;
        if (fin) {
            withTail = new byte[input.length + TAIL.length];
            System.arraycopy(input, 0, withTail, 0, input.length);
            System.arraycopy(TAIL, 0, withTail, input.length, TAIL.length);
        } else {
            withTail = input;
        }
        inflater.setInput(withTail);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        final byte[] buffer = new byte[8192];
        try {
            while (!inflater.needsInput()) {
                final int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) {
                    break;
                }
                out.write(buffer, 0, count);
            }
        } catch (final Exception ex) {
            throw new WebSocketException("Unable to inflate payload", ex);
        }
        if (fin) {
            decodingMessage = false;
            if (clientNoContextTakeover) {
                inflater.reset();
            }
        } else {
            decodingMessage = true;
        }
        return ByteBuffer.wrap(out.toByteArray());
    }

    @Override
    public ByteBuffer encode(final WebSocketFrameType type, final boolean fin, final ByteBuffer payload) throws WebSocketException {
        if (!isDataFrame(type) && type != WebSocketFrameType.CONTINUATION) {
            throw new WebSocketException("Unsupported frame type for permessage-deflate: " + type);
        }
        final byte[] input = toByteArray(payload);
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
        final byte[] data = out.toByteArray();
        final ByteBuffer encoded;
        if (data.length >= 4) {
            encoded = ByteBuffer.wrap(data, 0, data.length - 4);
        } else {
            encoded = ByteBuffer.wrap(data);
        }
        if (fin && serverNoContextTakeover) {
            deflater.reset();
        }
        return encoded;
    }

    @Override
    public WebSocketExtensionData getResponseData() {
        final Map<String, String> params = new LinkedHashMap<>();
        if (serverNoContextTakeover) {
            params.put("server_no_context_takeover", null);
        }
        if (clientNoContextTakeover) {
            params.put("client_no_context_takeover", null);
        }
        if (clientMaxWindowBits != null) {
            params.put("client_max_window_bits", Integer.toString(clientMaxWindowBits));
        }
        if (serverMaxWindowBits != null) {
            params.put("server_max_window_bits", Integer.toString(serverMaxWindowBits));
        }
        return new WebSocketExtensionData(getName(), params);
    }

    private static boolean isDataFrame(final WebSocketFrameType type) {
        return type == WebSocketFrameType.TEXT || type == WebSocketFrameType.BINARY;
    }

    static boolean isValidWindowBits(final Integer bits) {
        return bits == null || bits >= MIN_WINDOW_BITS && bits <= MAX_WINDOW_BITS;
    }

    private static byte[] toByteArray(final ByteBuffer payload) {
        final ByteBuffer buffer = payload != null ? payload.asReadOnlyBuffer() : ByteBuffer.allocate(0);
        final byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }
}
