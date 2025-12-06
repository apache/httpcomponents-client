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
package org.apache.hc.client5.http.websocket.core.frame;

import static org.apache.hc.client5.http.websocket.core.frame.FrameHeaderBits.FIN;
import static org.apache.hc.client5.http.websocket.core.frame.FrameHeaderBits.MASK_BIT;
import static org.apache.hc.client5.http.websocket.core.frame.FrameHeaderBits.RSV1;
import static org.apache.hc.client5.http.websocket.core.frame.FrameHeaderBits.RSV2;
import static org.apache.hc.client5.http.websocket.core.frame.FrameHeaderBits.RSV3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.hc.client5.http.websocket.core.message.CloseCodec;
import org.apache.hc.core5.annotation.Internal;

/**
 * RFC 6455 frame writer with helpers to build into an existing target buffer.
 *
 * @since 5.6
 */
@Internal
public final class WebSocketFrameWriter {

    // -- Text/Binary -----------------------------------------------------------

    public ByteBuffer text(final CharSequence data, final boolean fin) {
        final ByteBuffer payload = data == null ? ByteBuffer.allocate(0)
                : StandardCharsets.UTF_8.encode(data.toString());
        // Client → server MUST be masked
        return frame(FrameOpcode.TEXT, payload, fin, true);
    }

    public ByteBuffer binary(final ByteBuffer data, final boolean fin) {
        final ByteBuffer payload = data == null ? ByteBuffer.allocate(0) : data.asReadOnlyBuffer();
        return frame(FrameOpcode.BINARY, payload, fin, true);
    }

    // -- Control frames (FIN=true, payload ≤ 125, never compressed) -----------

    public ByteBuffer ping(final ByteBuffer payloadOrNull) {
        final ByteBuffer p = payloadOrNull == null ? ByteBuffer.allocate(0) : payloadOrNull.asReadOnlyBuffer();
        if (p.remaining() > 125) {
            throw new IllegalArgumentException("PING payload > 125 bytes");
        }
        return frame(FrameOpcode.PING, p, true, true);
    }

    public ByteBuffer pong(final ByteBuffer payloadOrNull) {
        final ByteBuffer p = payloadOrNull == null ? ByteBuffer.allocate(0) : payloadOrNull.asReadOnlyBuffer();
        if (p.remaining() > 125) {
            throw new IllegalArgumentException("PONG payload > 125 bytes");
        }
        return frame(FrameOpcode.PONG, p, true, true);
    }

    public ByteBuffer close(final int code, final String reason) {
        if (!CloseCodec.isValidToSend(code)) {
            throw new IllegalArgumentException("Invalid close code to send: " + code);
        }
        final String safeReason = CloseCodec.truncateReasonUtf8(reason);
        final ByteBuffer reasonBuf = safeReason.isEmpty()
                ? ByteBuffer.allocate(0)
                : StandardCharsets.UTF_8.encode(safeReason);

        if (reasonBuf.remaining() > 123) {
            throw new IllegalArgumentException("Close reason too long (UTF-8 bytes > 123)");
        }

        final ByteBuffer p = ByteBuffer.allocate(2 + reasonBuf.remaining());
        p.put((byte) (code >> 8 & 0xFF));
        p.put((byte) (code & 0xFF));
        if (reasonBuf.hasRemaining()) {
            p.put(reasonBuf);
        }
        p.flip();
        return frame(FrameOpcode.CLOSE, p, true, true);
    }

    public ByteBuffer closeEcho(final ByteBuffer payload) {
        final ByteBuffer p = payload == null ? ByteBuffer.allocate(0) : payload.asReadOnlyBuffer();
        if (p.remaining() > 125) {
            throw new IllegalArgumentException("Close payload > 125 bytes");
        }
        return frame(FrameOpcode.CLOSE, p, true, true);
    }

    // -- Core framing ----------------------------------------------------------

    public ByteBuffer frame(final int opcode, final ByteBuffer payload, final boolean fin, final boolean mask) {
        return frameWithRSV(opcode, payload, fin, mask, 0);
    }

    public ByteBuffer frameWithRSV(final int opcode, final ByteBuffer payload, final boolean fin,
                                   final boolean mask, final int rsvBits) {
        final int len = payload == null ? 0 : payload.remaining();
        final int hdrExtra = len <= 125 ? 0 : len <= 0xFFFF ? 2 : 8;
        final int maskLen = mask ? 4 : 0;
        final ByteBuffer out = ByteBuffer.allocate(2 + hdrExtra + maskLen + len).order(ByteOrder.BIG_ENDIAN);
        frameIntoWithRSV(opcode, payload, fin, mask, rsvBits, out);
        out.flip();
        return out;
    }

    public ByteBuffer frameInto(final int opcode, final ByteBuffer payload, final boolean fin,
                                final boolean mask, final ByteBuffer out) {
        return frameIntoWithRSV(opcode, payload, fin, mask, 0, out);
    }

    public ByteBuffer frameIntoWithRSV(final int opcode, final ByteBuffer payload, final boolean fin,
                                       final boolean mask, final int rsvBits, final ByteBuffer out) {
        final int len = payload == null ? 0 : payload.remaining();

        if (FrameOpcode.isControl(opcode)) {
            if (!fin) {
                throw new IllegalArgumentException("Control frames must not be fragmented (FIN=false)");
            }
            if (len > 125) {
                throw new IllegalArgumentException("Control frame payload > 125 bytes");
            }
            if ((rsvBits & (RSV1 | RSV2 | RSV3)) != 0) {
                throw new IllegalArgumentException("RSV bits must be 0 on control frames");
            }
        }

        final int finBit = fin ? FIN : 0;
        out.put((byte) (finBit | rsvBits & (RSV1 | RSV2 | RSV3) | opcode & 0x0F));

        if (len <= 125) {
            out.put((byte) ((mask ? MASK_BIT : 0) | len));
        } else if (len <= 0xFFFF) {
            out.put((byte) ((mask ? MASK_BIT : 0) | 126));
            out.putShort((short) len);
        } else {
            out.put((byte) ((mask ? MASK_BIT : 0) | 127));
            out.putLong(len & 0x7FFF_FFFF_FFFF_FFFFL);
        }

        int[] mkey = null;
        if (mask) {
            mkey = new int[]{rnd(), rnd(), rnd(), rnd()};
            out.put((byte) mkey[0]).put((byte) mkey[1]).put((byte) mkey[2]).put((byte) mkey[3]);
        }

        if (len > 0) {
            final ByteBuffer src = payload.asReadOnlyBuffer();
            int i = 0; // simpler, safer mask index
            while (src.hasRemaining()) {
                int b = src.get() & 0xFF;
                if (mask) {
                    b ^= mkey[i & 3];
                    i++;
                }
                out.put((byte) b);
            }
        }
        return out;
    }

    private static int rnd() {
        return ThreadLocalRandom.current().nextInt(256);
    }
}
