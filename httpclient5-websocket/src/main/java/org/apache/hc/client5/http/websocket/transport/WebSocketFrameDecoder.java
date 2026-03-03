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
package org.apache.hc.client5.http.websocket.transport;

import java.nio.ByteBuffer;

import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.apache.hc.core5.annotation.Internal;

@Internal
public final class WebSocketFrameDecoder {
    private final int maxFrameSize;
    private final boolean strictNoExtensions;

    private int opcode;
    private boolean fin;
    private boolean rsv1, rsv2, rsv3;
    private ByteBuffer payload = ByteBuffer.allocate(0);
    private final boolean expectMasked;



    public WebSocketFrameDecoder(final int maxFrameSize, final boolean strictNoExtensions) {
        this(maxFrameSize, strictNoExtensions, false);
    }

    public WebSocketFrameDecoder(final int maxFrameSize) {
        this(maxFrameSize, true, false);
    }

    public WebSocketFrameDecoder(final int maxFrameSize,
                                 final boolean strictNoExtensions,
                                 final boolean expectMasked) {
        this.maxFrameSize = maxFrameSize;
        this.strictNoExtensions = strictNoExtensions;
        this.expectMasked = expectMasked;
    }

    public boolean decode(final ByteBuffer in) {
        in.mark();
        if (in.remaining() < 2) {
            in.reset();
            return false;
        }

        final int b0 = in.get() & 0xFF;
        final int b1 = in.get() & 0xFF;

        fin = (b0 & 0x80) != 0;
        rsv1 = (b0 & 0x40) != 0;
        rsv2 = (b0 & 0x20) != 0;
        rsv3 = (b0 & 0x10) != 0;

        if (strictNoExtensions && (rsv1 || rsv2 || rsv3)) {
            throw new WebSocketProtocolException(1002, "RSV bits set without extension");
        }

        opcode = b0 & 0x0F;

        if (opcode != 0 && opcode != 1 && opcode != 2 && opcode != 8 && opcode != 9 && opcode != 10) {
            throw new WebSocketProtocolException(1002, "Reserved/unknown opcode: " + opcode);
        }

        final boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;

        // Mode-aware masking rule
        if (masked != expectMasked) {
            if (expectMasked) {
                // server decoding client frames: clients MUST mask
                throw new WebSocketProtocolException(1002, "Client frame is not masked");
            } else {
                // client decoding server frames: servers MUST NOT mask
                throw new WebSocketProtocolException(1002, "Server frame is masked");
            }
        }

        if (len == 126) {
            if (in.remaining() < 2) {
                in.reset();
                return false;
            }
            len = in.getShort() & 0xFFFF;
        } else if (len == 127) {
            if (in.remaining() < 8) {
                in.reset();
                return false;
            }
            final long l = in.getLong();
            if (l < 0) {
                throw new WebSocketProtocolException(1002, "Negative length");
            }
            len = l;
        }

        if (FrameOpcode.isControl(opcode)) {
            if (!fin) {
                throw new WebSocketProtocolException(1002, "fragmented control frame");
            }
            if (len > 125) {
                throw new WebSocketProtocolException(1002, "control frame too large");
            }
            // (RSV checks above already cover RSV!=0)
        }

        if (len > Integer.MAX_VALUE || maxFrameSize > 0 && len > maxFrameSize) {
            throw new WebSocketProtocolException(1009, "Frame too large: " + len);
        }

        final long required = len + (masked ? 4L : 0L);
        if (in.remaining() < required) {
            in.reset();
            return false;
        }

        final byte[] maskKey;
        if (masked) {
            maskKey = new byte[4];
            in.get(maskKey);
        } else {
            maskKey = null;
        }

        final ByteBuffer data = ByteBuffer.allocate((int) len);
        for (int i = 0; i < len; i++) {
            byte b = in.get();
            if (masked) {
                b = (byte) (b ^ maskKey[i & 3]);
            }
            data.put(b);
        }
        data.flip();
        payload = data.asReadOnlyBuffer();
        return true;
    }

    public int opcode() {
        return opcode;
    }

    public boolean fin() {
        return fin;
    }

    public boolean rsv1() {
        return rsv1;
    }

    public boolean rsv2() {
        return rsv2;
    }

    public boolean rsv3() {
        return rsv3;
    }

    public ByteBuffer payload() {
        return payload.asReadOnlyBuffer();
    }
}
