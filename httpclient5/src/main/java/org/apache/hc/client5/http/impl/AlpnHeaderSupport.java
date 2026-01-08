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
package org.apache.hc.client5.http.impl;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Args;

/**
 * Codec for the HTTP {@code ALPN} header field (RFC 7639).
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
@Internal
public final class AlpnHeaderSupport {

    private static final char[] HEXADECIMAL = "0123456789ABCDEF".toCharArray();

    private AlpnHeaderSupport() {
    }

    /**
     * Formats a list of raw ALPN protocol IDs into a single {@code ALPN} header value.
     */
    public static String formatValue(final List<String> protocolIds) {
        Args.notEmpty(protocolIds, "protocolIds");
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (final String id : protocolIds) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(encodeId(id));
            first = false;
        }
        return sb.toString();
    }

    /**
     * Parses an {@code ALPN} header value into decoded protocol IDs.
     */
    public static List<String> parseValue(final String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> out = new ArrayList<>();
        final ParserCursor cursor = new ParserCursor(0, value.length());
        MessageSupport.parseTokens(value, cursor, token -> {
            if (!token.isEmpty()) {
                out.add(decodeId(token));
            }
        });
        return out;
    }

    /**
     * Encodes a single raw protocol ID to canonical token form.
     */
    public static String encodeId(final String id) {
        Args.notBlank(id, "id");
        final byte[] bytes = id.getBytes(StandardCharsets.UTF_8);
        final StringBuilder sb = new StringBuilder(bytes.length);
        for (final byte b0 : bytes) {
            final int b = b0 & 0xFF;
            if (b == '%' || !isTchar(b)) {
                appendPctEncoded(b, sb);
            } else {
                sb.append((char) b);
            }
        }
        return sb.toString();
    }

    /**
     * Decodes percent-encoded token to raw ID using UTF-8.
     * Accepts lowercase hex; malformed/incomplete sequences are left literal.
     */
    public static String decodeId(final String token) {
        Args.notBlank(token, "token");
        final byte[] buf = new byte[token.length()];
        int bi = 0;
        for (int i = 0; i < token.length(); ) {
            final char c = token.charAt(i);
            if (c == '%' && i + 2 < token.length()) {
                final int hi = hexVal(token.charAt(i + 1));
                final int lo = hexVal(token.charAt(i + 2));
                if (hi >= 0 && lo >= 0) {
                    buf[bi++] = (byte) ((hi << 4) | lo);
                    i += 3;
                    continue;
                }
            }
            buf[bi++] = (byte) c;
            i++;
        }
        return new String(buf, 0, bi, StandardCharsets.UTF_8);
    }

    // RFC7230 tchar minus '%' (RFC7639 requires '%' be percent-encoded)
    private static boolean isTchar(final int c) {
        if (c >= '0' && c <= '9') {
            return true;
        }
        if (c >= 'A' && c <= 'Z') {
            return true;
        }
        if (c >= 'a' && c <= 'z') {
            return true;
        }
        switch (c) {
            case '!':
            case '#':
            case '$':
            case '&':
            case '\'':
            case '*':
            case '+':
            case '-':
            case '.':
            case '^':
            case '_':
            case '`':
            case '|':
            case '~':
                return true;
            default:
                return false;
        }
    }

    private static void appendPctEncoded(final int b, final StringBuilder sb) {
        sb.append('%');
        sb.append(HEXADECIMAL[(b >>> 4) & 0x0F]);
        sb.append(HEXADECIMAL[b & 0x0F]);
    }

    private static int hexVal(final char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return 10 + (c - 'A');
        }
        if (c >= 'a' && c <= 'f') {
            return 10 + (c - 'a');
        }
        return -1;
    }
}
