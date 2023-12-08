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

package org.apache.hc.client5.http.impl.auth;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

import org.apache.hc.client5.http.utils.CodingException;
import org.conscrypt.Internal;

/**
 * Utility class for encoding and decoding strings according to RFC 5987.
 * This class provides methods to percent-encode and decode strings, particularly
 * useful for handling HTTP header parameters that include non-ASCII characters.
 *
 * @Internal This class is intended for internal use within the library and
 * should not be used as part of the public API.
 */
@Internal
public class RFC5987Codec {

    private static final BitSet UNRESERVED = new BitSet(256);
    private static final int RADIX = 16;

    static {
        // Alphanumeric characters
        for (int i = 'a'; i <= 'z'; i++) {
            UNRESERVED.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            UNRESERVED.set(i);
        }
        for (int i = '0'; i <= '9'; i++) {
            UNRESERVED.set(i);
        }

        // Additional characters as per RFC 5987 attr-char
        UNRESERVED.set('!');
        UNRESERVED.set('#');
        UNRESERVED.set('$');
        UNRESERVED.set('&');
        UNRESERVED.set('+');
        UNRESERVED.set('-');
        UNRESERVED.set('.');
        UNRESERVED.set('^');
        UNRESERVED.set('_');
        UNRESERVED.set('`');
        UNRESERVED.set('|');
        UNRESERVED.set('~');
    }



    /**
     * Encodes a string using the default UTF-8 charset.
     *
     * @param s The string to encode.
     * @return The percent-encoded string.
     */
    public static String encode(final String s) {
        return encode(s, StandardCharsets.UTF_8);
    }

    /**
     * Encodes a string using the specified charset.
     *
     * @param s       The string to encode.
     * @param charset The charset to use for encoding.
     * @return The percent-encoded string.
     */
    public static String encode(final String s, final Charset charset) {
        final ByteBuffer bb = charset.encode(CharBuffer.wrap(s));
        final StringBuilder sb = new StringBuilder();

        while (bb.hasRemaining()) {
            final int b = bb.get() & 0xff;
            if (UNRESERVED.get(b)) {
                sb.append((char) b);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, RADIX)));
                sb.append(Character.toUpperCase(Character.forDigit(b & 0xF, RADIX)));
            }
        }

        return sb.toString();
    }

    /**
     * Decodes a percent-encoded string using the default UTF-8 charset.
     *
     * @param s The percent-encoded string to decode.
     * @return The decoded string.
     * @throws IllegalArgumentException If the percent-encoded string is invalid.
     */
    public static String decode(final String s) throws CodingException {
        return decode(s, StandardCharsets.UTF_8);
    }

    /**
     * Decodes a percent-encoded string using the specified charset.
     *
     * @param s       The percent-encoded string to decode.
     * @param charset The charset to use for decoding.
     * @return The decoded CodingException.
     * @throws IllegalArgumentException If the percent-encoded string is invalid.
     */
    public static String decode(final String s, final Charset charset) throws CodingException {
        final ByteBuffer bb = ByteBuffer.allocate(s.length());
        final CharBuffer cb = CharBuffer.wrap(s);

        while (cb.hasRemaining()) {
            final char c = cb.get();
            if (c == '%') {
                if (cb.remaining() < 2) {
                    throw new CodingException("Incomplete percent encoding in " + s);
                }
                final int u = Character.digit(cb.get(), RADIX);
                final int l = Character.digit(cb.get(), RADIX);
                if (u != -1 && l != -1) {
                    bb.put((byte) ((u << 4) + l));
                } else {
                    throw new CodingException("Invalid percent encoding in " + s);
                }
            } else {
                bb.put((byte) c);
            }
        }
        bb.flip();
        return charset.decode(bb).toString();
    }

}