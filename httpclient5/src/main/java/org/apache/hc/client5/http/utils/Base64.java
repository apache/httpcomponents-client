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

package org.apache.hc.client5.http.utils;


import org.apache.hc.core5.annotation.Internal;

import java.nio.charset.StandardCharsets;

import static java.util.Base64.getEncoder;
import static java.util.Base64.getMimeDecoder;

/**
 * Provide implementations of the Base64 conversion methods from commons-codec, delegating to  the Java Base64
 * implementation.
 * <p>
 * Notes:
 * <p>
 * <ul>commons-codec accepts null inputs, so this is also accepted here. This is not done in the Java 8 implementation</ul>
 * <ul>decoding invalid inputs returns an empty value. The Java 8 implementation throws an exception, which is caught here</ul>
 * <ul>commons-codec decoders accept both standard and url-safe variants of input. This needs to be remapped for Java 8, as it
 * will accept either one or the other, but not both. This is likely a rarely-needed requirement, but is provided to avoid
 * compatibility surprises.
 * </ul>
 * <ul>Only the features currently used by http-client are implemented here rather than all the features of commons-coded</ul>
 */
@Internal
public class Base64 {
    private static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * Creates a Base64 codec used for decoding (all modes) and encoding in URL-unsafe mode.
     */
    public Base64() {
    }

    /**
     * Creates a Base64 codec used for decoding (all modes) and encoding in URL-unsafe mode.
     * <p>
     * As http-client never uses a non-zero length, this feature is not implemented here.
     */

    public Base64(final int lineLength) {
        if (lineLength != 0) {
            throw new UnsupportedOperationException("Line breaks not supported");
        }
    }

    /**
     * Decodes Base64 data into octets.
     * <p>
     * <b>Note:</b> this method seamlessly handles data encoded in URL-safe or normal mode.
     */
    public static byte[] decodeBase64(final byte[] base64) {
        if (null == base64) {
            return null;
        }

        return decodeOrEmptyBytes(UrlByteRemapper.toStandardUrlEncoding(base64));
    }

    /**
     * Decodes a Base64 String into octets.
     * <p>
     * <b>Note:</b> this method seamlessly handles data encoded in URL-safe or normal mode.
     */

    public static byte[] decodeBase64(final String base64) {
        if (null == base64) {
            return null;
        }

//        The acceptable Base64 alphabets have the same encodings in ASCII, ISO_8859_1, and UTF-8.
//        ISO_8859_1 is used here because it matches the choice in the Java 8 Base64 implementation, and is
//        in principle a tiny bit faster than the others to convert in versions of Java that support "compact strings".
//        Any inputs outside the accepted values will be invalid in any encoding.

        final byte[] bytes = base64.getBytes(StandardCharsets.ISO_8859_1);
        UrlByteRemapper.replaceUrlSafeBytes(bytes);

        return decodeOrEmptyBytes(bytes);
    }

    /**
     * Encodes binary data using the base64 algorithm but does not chunk the output.
     */

    public static byte[] encodeBase64(final byte[] bytes) {
        if (null == bytes) {
            return null;
        }

        return getEncoder().encode(bytes);
    }

    /**
     * Encodes binary data using the base64 algorithm but does not chunk the output.
     */

    public static String encodeBase64String(final byte[] bytes) {
        if (null == bytes) {
            return null;
        }

        return getEncoder().encodeToString(bytes);
    }

    /**
     * Encode bytes to their Base64 form, using specifications from this codec instance
     */

    public byte[] decode(final byte[] base64) {
        if (null == base64) {
            return null;
        }

        return decodeOrEmptyBytes(UrlByteRemapper.toStandardUrlEncoding(base64));
    }

    /**
     * Encode bytes to their Base64 form, using specifications from this codec instance
     */
    public byte[] encode(final byte[] value) {
        if (null == value) {
            return null;
        }
        return getEncoder().encode(value);
    }

    /**
     * Decode the provided byte array.
     * <p>
     * If the input cannot be coverted, an empty array is returned
     */
    private static byte[] decodeOrEmptyBytes(final byte[] bytes) {
        try {
            return getMimeDecoder().decode(bytes);
        } catch (final IllegalArgumentException e) {
            return EMPTY_BYTES;
        }
    }

    /**
     * Provide methods to remap values from url-safe Bas64 to standard encoding. The commons-codec
     * implementation accepts both variants in the same input. The jdk implementaion does not, so
     * inputs need to be
     */
    static class UrlByteRemapper {
        private static final byte[] base64Url = genMapping();

        /**
         * generate a mapping that maps url-safe characters to their standard values,
         * and leaves all other values the same.
         */
        private static byte[] genMapping() {
            final byte[] mapping = new byte[256];
            for (int i = 0; i < mapping.length; i++) {
                mapping[i] = (byte) (i & 0xff);
            }

            mapping['-'] = '+';
            mapping['_'] = '/';

            return mapping;
        }

        /**
         * Convert url-safe values to standard values, modifying the given array in-place.
         *
         * @param src Base64 encoded bytes, possibly containing url-safe characters
         */
        static void replaceUrlSafeBytes(final byte[] src) {
            for (int i = 0; i < src.length; i++) {
                src[i] = base64Url[src[i] & 0xff];
            }
        }


        /**
         * Convert url-safe values to standard values. The input array is left unmodified - a new array will be
         * returned if re-mappings were needed.
         *
         * @param src Base64 encoded bytes, possibly containing url-safe characters
         */
        static byte[] toStandardUrlEncoding(final byte[] src) {
            for (int i = 0; i < src.length; i++) {
                if (src[i] == '-' || src[i] == '_') {
                    final byte[] dup = src.clone();

                    for (; i < dup.length; i++) {
                        dup[i] = base64Url[dup[i] & 0xff];
                    }
                    return dup;
                }
            }

            return src;
        }
    }
}
