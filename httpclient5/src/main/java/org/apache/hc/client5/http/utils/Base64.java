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


import static java.util.Base64.getEncoder;
import static java.util.Base64.getMimeDecoder;

import org.apache.hc.core5.annotation.Internal;

/**
 * Provide implementations of the Base64 conversion methods from Commons Codec, delegating to the Java Base64
 * implementation.
 * <p>
 * Only the features currently used by HttpClient are implemented here rather than all the features of Commons Codec.
 * <p>
 * Notes:
 * <ul>
 * <li>Commons Codec accepts null inputs, so this is also accepted here. This is not done in the Java 8 implementation</li>
 * <li>Decoding invalid input returns an empty value. The Java 8 implementation throws an exception, which is caught here</li>
 * <li>Commons Codec decoders accept both standard and url-safe variants of input. As this is not a requirement for
 * HttpClient, this is NOT implemented here.
 * </li>
 * </ul>
 * This class is intended as in interim convenience. Any new code should use `java.util.Base64` directly.
 */
@Internal
public class Base64 {
    private static final Base64 CODEC = new Base64();
    private static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * Return an instance of the Base64 codec that use the regular Base64 alphabet
     * (as opposed to the URL-safe alphabet). Note that unlike the Commons Codec version,
     * thus class will NOT decode characters from URL-safe alphabet.
     */
    public Base64() {
    }

    /**
     * Creates a Base64 codec used for decoding and encoding in URL-unsafe mode.
     * <p>
     * As HttpClient never uses a non-zero length, this feature is not implemented here.
     */

    public Base64(final int lineLength) {
        if (lineLength != 0) {
            throw new UnsupportedOperationException("Line breaks not supported");
        }
    }

    /**
     * Decodes Base64 data into octets.
     * <p>
     * <b>Note:</b> this method does NOT accept URL-safe encodings
     */
    public static byte[] decodeBase64(final byte[] base64) {
        return CODEC.decode(base64);
    }

    /**
     * Decodes a Base64 String into octets.
     * <p>
     * <b>Note:</b> this method does NOT accept URL-safe encodings
     */

    public static byte[] decodeBase64(final String base64) {
        return CODEC.decode(base64);
    }

    /**
     * Encodes binary data using the base64 algorithm but does not chunk the output.
     */

    public static byte[] encodeBase64(final byte[] base64) {
        return CODEC.encode(base64);
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
     * Decode Base64-encoded bytes to their original form, using specifications from this codec instance
     */

    public byte[] decode(final byte[] base64) {
        if (null == base64) {
            return null;
        }

        try {
            return getMimeDecoder().decode(base64);
        } catch (final IllegalArgumentException e) {
            return EMPTY_BYTES;
        }
    }

    /**
     * Decode a Base64 String to its original form, using specifications from this codec instance
     */

    public byte[] decode(final String base64) {
        if (null == base64) {
            return null;
        }

        try {

            // getMimeDecoder is used instead of getDecoder as it better matches the
            // functionality of the default Commons Codec implementation (primarily more forgiving of strictly
            // invalid inputs to decode)
            // Code using java.util.Base64 directly should make a choice based on whether this forgiving nature is
            // appropriate.

            return getMimeDecoder().decode(base64);
        } catch (final IllegalArgumentException e) {
            return EMPTY_BYTES;
        }
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

}
