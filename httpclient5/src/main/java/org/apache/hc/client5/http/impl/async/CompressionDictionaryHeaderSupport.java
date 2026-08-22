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
package org.apache.hc.client5.http.impl.async;

import java.util.Base64;

import org.apache.hc.core5.util.Args;

/**
 * Names the Compression Dictionary Transport headers and encodes the request headers used to
 * negotiate a dictionary. {@code Available-Dictionary} carries the SHA-256 hash of the stored
 * dictionary as an HTTP Structured Field Byte Sequence, and {@code Dictionary-ID} carries the
 * opaque identifier the origin assigned through {@code Use-As-Dictionary} as a Structured Field
 * String. Both header values are produced in the wire form required by the negotiation rather than
 * as raw bytes or text.
 * <p>
 * Stateless and not instantiable.
 */
final class CompressionDictionaryHeaderSupport {

    /**
     * Response header through which an origin designates a resource as a dictionary and declares
     * how it may be matched against future requests.
     */
    static final String USE_AS_DICTIONARY = "Use-As-Dictionary";

    /**
     * Request header naming the dictionary the client holds, keyed by the SHA-256 hash of its
     * content encoded as a Structured Field Byte Sequence.
     */
    static final String AVAILABLE_DICTIONARY = "Available-Dictionary";

    /**
     * Request header echoing the opaque identifier the origin bound to the dictionary via
     * {@code Use-As-Dictionary}, encoded as a Structured Field String.
     */
    static final String DICTIONARY_ID = "Dictionary-ID";

    private CompressionDictionaryHeaderSupport() {
    }

    /**
     * Encodes a dictionary hash as the {@code Available-Dictionary} value. The hash is emitted as a
     * Structured Field Byte Sequence, that is base64 wrapped in a leading and trailing colon.
     *
     * @param hash the SHA-256 hash of the dictionary content; must not be {@code null}.
     * @return the {@code Available-Dictionary} field value.
     */
    static String formatAvailableDictionary(final byte[] hash) {
        return ":" + Base64.getEncoder().encodeToString(Args.notNull(hash, "Dictionary hash")) + ":";
    }

    /**
     * Encodes a dictionary identifier as the {@code Dictionary-ID} value. The identifier is emitted
     * as a Structured Field String, double quoted with a backslash escaping any embedded quote or
     * backslash. The string form only admits printable ASCII, so any character outside the range
     * {@code 0x20} to {@code 0x7e} is rejected, as is a value longer than the 1024-character limit
     * imposed on the identifier.
     *
     * @param value the opaque dictionary identifier; must not be {@code null}.
     * @return the {@code Dictionary-ID} field value.
     * @throws IllegalArgumentException if the value exceeds 1024 characters or contains a character
     *         that a Structured Field String cannot represent.
     */
    static String formatDictionaryId(final String value) {
        Args.notNull(value, "Dictionary ID");
        if (value.length() > 1024) {
            throw new IllegalArgumentException("Dictionary ID length exceeds 1024 characters");
        }
        final StringBuilder buffer = new StringBuilder(value.length() + 2);
        buffer.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch < 0x20 || ch > 0x7e) {
                throw new IllegalArgumentException("Dictionary ID contains a character not permitted in a Structured Field String");
            }
            if (ch == '"' || ch == '\\') {
                buffer.append('\\');
            }
            buffer.append(ch);
        }
        buffer.append('"');
        return buffer.toString();
    }
}
