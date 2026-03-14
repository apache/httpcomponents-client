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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.annotation.Internal;

/**
 * Helpers for RFC6455 CLOSE parsing & validation.
 */
@Internal
public final class CloseCodec {

    private CloseCodec() {
    }


    /**
     * Reads the close status code from the payload buffer, if present.
     * Returns {@code 1005} (“no status code present”) when the payload
     * does not contain at least two bytes.
     */
    public static int readCloseCode(final ByteBuffer payloadRO) {
        if (payloadRO == null || payloadRO.remaining() < 2) {
            return 1005; // “no status code present”
        }
        final int b1 = payloadRO.get() & 0xFF;
        final int b2 = payloadRO.get() & 0xFF;
        return (b1 << 8) | b2;
    }

    /**
     * Reads the close reason from the remaining bytes of the payload
     * as UTF-8. Returns an empty string if there is no payload left.
     */
    public static String readCloseReason(final ByteBuffer payloadRO) {
        if (payloadRO == null || !payloadRO.hasRemaining()) {
            return "";
        }
        final ByteBuffer dup = payloadRO.slice();
        return StandardCharsets.UTF_8.decode(dup).toString();
    }

    // ---- RFC validation (sender & receiver) ---------------------------------

    /**
     * RFC 6455 §7.4.2: MUST NOT appear on the wire.
     */
    private static boolean isForbiddenOnWire(final int code) {
        return code == 1005 || code == 1006 || code == 1015;
    }

    /**
     * Codes defined by RFC 6455 to send (and likewise valid to receive).
     */
    private static boolean isRfcDefined(final int code) {
        switch (code) {
            case 1000: // normal
            case 1001: // going away
            case 1002: // protocol error
            case 1003: // unsupported data
            case 1007: // invalid payload data
            case 1008: // policy violation
            case 1009: // message too big
            case 1010: // mandatory extension
            case 1011: // internal error
                return true;
            default:
                return false;
        }
    }

    /**
     * Application/reserved range that may be sent by endpoints.
     */
    private static boolean isAppRange(final int code) {
        return code >= 3000 && code <= 4999;
    }

    /**
     * Validate a code we intend to PUT ON THE WIRE (sender-side).
     */
    public static boolean isValidToSend(final int code) {
        if (code < 0) {
            return false;
        }
        if (isForbiddenOnWire(code)) {
            return false;
        }
        return isRfcDefined(code) || isAppRange(code);
    }

    /**
     * Validate a code we PARSED FROM THE WIRE (receiver-side).
     */
    public static boolean isValidToReceive(final int code) {
        // 1005, 1006, 1015 must not appear on the wire
        if (isForbiddenOnWire(code)) {
            return false;
        }
        // Same allowed sets otherwise
        return isRfcDefined(code) || isAppRange(code);
    }

    // ---- Reason handling: max 123 bytes (2 bytes used by code) --------------

    /**
     * Returns a UTF-8 string truncated to ≤ 123 bytes, preserving code-points.
     * This ensures that a CLOSE frame payload (2-byte status code + reason)
     * never exceeds the 125-byte control frame limit.
     */
    public static String truncateReasonUtf8(final String reason) {
        if (reason == null || reason.isEmpty()) {
            return "";
        }
        final byte[] bytes = reason.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 123) {
            return reason;
        }
        int i = 0;
        int byteCount = 0;
        while (i < reason.length()) {
            final int cp = reason.codePointAt(i);
            final int charCount = Character.charCount(cp);
            final int extra = new String(Character.toChars(cp))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + extra > 123) {
                break;
            }
            byteCount += extra;
            i += charCount;
        }
        return reason.substring(0, i);
    }

    // ---- Encoding -----------------------------------------------------------

    /**
     * Encodes a close status code and reason into a payload suitable for a
     * CLOSE control frame:
     *
     * <pre>
     *   payload[0] = high-byte of status code
     *   payload[1] = low-byte of status code
     *   payload[2..] = UTF-8 bytes of the (possibly truncated) reason
     * </pre>
     * <p>
     * The reason is internally truncated to ≤ 123 UTF-8 bytes to ensure the
     * resulting payload never exceeds the 125-byte control frame limit.
     * <p>
     * The caller is expected to have already validated the status code with
     * {@link #isValidToSend(int)}.
     */
    public static byte[] encode(final int statusCode, final String reason) {
        final String truncated = truncateReasonUtf8(reason);
        final byte[] reasonBytes = truncated.getBytes(StandardCharsets.UTF_8);
        // 2 bytes for the status code
        final byte[] payload = new byte[2 + reasonBytes.length];
        payload[0] = (byte) ((statusCode >>> 8) & 0xFF);
        payload[1] = (byte) (statusCode & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
        return payload;
    }
}
