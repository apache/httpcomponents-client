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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class TestCompressionDictionaryHeaderSupport {

    @Test
    void testHeaderNameConstants() {
        assertEquals("Use-As-Dictionary", CompressionDictionaryHeaderSupport.USE_AS_DICTIONARY);
        assertEquals("Available-Dictionary", CompressionDictionaryHeaderSupport.AVAILABLE_DICTIONARY);
        assertEquals("Dictionary-ID", CompressionDictionaryHeaderSupport.DICTIONARY_ID);
    }

    @Test
    void testFormatAvailableDictionaryKnownInput() {
        final byte[] sha256 = "abc".getBytes(StandardCharsets.US_ASCII);
        assertEquals(":YWJj:", CompressionDictionaryHeaderSupport.formatAvailableDictionary(sha256));
    }

    @Test
    void testFormatAvailableDictionaryMatchesBase64() {
        final byte[] sha256 = new byte[32];
        for (int i = 0; i < sha256.length; i++) {
            sha256[i] = (byte) i;
        }
        final String expected = ":" + Base64.getEncoder().encodeToString(sha256) + ":";
        assertEquals(expected, CompressionDictionaryHeaderSupport.formatAvailableDictionary(sha256));
    }

    @Test
    void testFormatAvailableDictionaryEmpty() {
        assertEquals("::", CompressionDictionaryHeaderSupport.formatAvailableDictionary(new byte[0]));
    }

    @Test
    void testFormatAvailableDictionaryNull() {
        assertThrows(NullPointerException.class,
                () -> CompressionDictionaryHeaderSupport.formatAvailableDictionary(null));
    }

    @Test
    void testFormatDictionaryIdPlain() {
        assertEquals("\"foo\"", CompressionDictionaryHeaderSupport.formatDictionaryId("foo"));
    }

    @Test
    void testFormatDictionaryIdEmpty() {
        assertEquals("\"\"", CompressionDictionaryHeaderSupport.formatDictionaryId(""));
    }

    @Test
    void testFormatDictionaryIdEscapesQuotes() {
        assertEquals("\"a\\\"b\"", CompressionDictionaryHeaderSupport.formatDictionaryId("a\"b"));
    }

    @Test
    void testFormatDictionaryIdEscapesBackslash() {
        assertEquals("\"a\\\\b\"", CompressionDictionaryHeaderSupport.formatDictionaryId("a\\b"));
    }

    @Test
    void testFormatDictionaryIdEscapesBoth() {
        assertEquals("\"\\\\\\\"\"", CompressionDictionaryHeaderSupport.formatDictionaryId("\\\""));
    }

    @Test
    void testFormatDictionaryIdNull() {
        assertThrows(NullPointerException.class,
                () -> CompressionDictionaryHeaderSupport.formatDictionaryId(null));
    }
}
