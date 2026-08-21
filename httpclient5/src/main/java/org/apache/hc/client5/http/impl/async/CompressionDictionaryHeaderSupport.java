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
 * Encodes the request headers used to negotiate a
 * {@link org.apache.hc.client5.http.entity.compress.CompressionDictionary} for Compression
 * Dictionary Transport. The header values are HTTP Structured Field
 * items: {@code Available-Dictionary} carries the dictionary hash as a Byte Sequence and
 * {@code Dictionary-ID} carries the server-supplied identifier as a String.
 * <p>
 * This class is a stateless collection of static helpers and holds no mutable state; it is therefore
 * safe for concurrent use. It cannot be instantiated.
 */
final class CompressionDictionaryHeaderSupport {

    static final String USE_AS_DICTIONARY = "Use-As-Dictionary";
    static final String AVAILABLE_DICTIONARY = "Available-Dictionary";
    static final String DICTIONARY_ID = "Dictionary-ID";

    private CompressionDictionaryHeaderSupport() {
    }

    static String formatHash(final byte[] hash) {
        return ":" + Base64.getEncoder().encodeToString(hash) + ":";
    }

    static String formatString(final String value) {
        final StringBuilder buffer =
                new StringBuilder(value.length() + 2);

        buffer.append('"');

        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);

            if (ch == '"' || ch == '\\') {
                buffer.append('\\');
            }

            buffer.append(ch);
        }

        buffer.append('"');

        return buffer.toString();
    }
}