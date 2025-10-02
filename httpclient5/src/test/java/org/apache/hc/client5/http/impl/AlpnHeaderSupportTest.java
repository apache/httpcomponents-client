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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class AlpnHeaderSupportTest {

    @Test
    void encodes_slash_and_percent_and_space() {
        assertEquals("http%2F1.1", AlpnHeaderSupport.encodeId("http/1.1"));
        assertEquals("h2%25", AlpnHeaderSupport.encodeId("h2%"));
        assertEquals("foo%20bar", AlpnHeaderSupport.encodeId("foo bar"));
    }

    @Test
    void encodes_unicode_utf8() {
        final String raw = "ws/é"; // \u00E9 -> C3 A9
        final String enc = AlpnHeaderSupport.encodeId(raw);
        assertEquals("ws%2F%C3%A9", enc);
        assertEquals(raw, AlpnHeaderSupport.decodeId(enc));
    }

    @Test
    void keeps_tchar_plain_and_upper_hex() {
        assertEquals("h2", AlpnHeaderSupport.encodeId("h2"));
        assertEquals("A1+B", AlpnHeaderSupport.encodeId("A1+B")); // '+' is a tchar → stays literal
        assertEquals("http%2F1.1", AlpnHeaderSupport.encodeId("http/1.1")); // slash encoded, hex uppercase
    }

    @Test
    void decode_is_liberal_on_hex_case_and_incomplete_sequences() {
        assertEquals("http/1.1", AlpnHeaderSupport.decodeId("http%2f1.1"));
        // incomplete % — treat as literal
        assertEquals("h2%", AlpnHeaderSupport.decodeId("h2%"));
        assertEquals("h2%G1", AlpnHeaderSupport.decodeId("h2%G1"));
    }

    @Test
    void format_and_parse_roundtrip_with_ows() {
        final String v = "h2,   http%2F1.1 ,ws";
        final List<String> ids = AlpnHeaderSupport.parseValue(v);
        assertEquals(Arrays.asList("h2", "http/1.1", "ws"), ids);
        assertEquals("h2, http%2F1.1, ws", AlpnHeaderSupport.formatValue(ids));
    }

    @Test
    void parse_empty_and_blank() {
        assertTrue(AlpnHeaderSupport.parseValue(null).isEmpty());
        assertTrue(AlpnHeaderSupport.parseValue("").isEmpty());
        assertTrue(AlpnHeaderSupport.parseValue(" , \t ").isEmpty());
    }

    @Test
    void all_tchar_pass_through() {
        // digits
        for (char c = '0'; c <= '9'; c++) {
            assertEquals(String.valueOf(c), AlpnHeaderSupport.encodeId(String.valueOf(c)));
        }
        // uppercase letters
        for (char c = 'A'; c <= 'Z'; c++) {
            assertEquals(String.valueOf(c), AlpnHeaderSupport.encodeId(String.valueOf(c)));
        }
        // lowercase letters
        for (char c = 'a'; c <= 'z'; c++) {
            assertEquals(String.valueOf(c), AlpnHeaderSupport.encodeId(String.valueOf(c)));
        }
        // the symbol set (minus '%' which must be encoded)
        final String symbols = "!#$&'*+-.^_`|~";
        for (int i = 0; i < symbols.length(); i++) {
            final String s = String.valueOf(symbols.charAt(i));
            assertEquals(s, AlpnHeaderSupport.encodeId(s));
        }
    }

    @Test
    void percent_is_always_encoded_and_uppercase_hex() {
        assertEquals("%25", AlpnHeaderSupport.encodeId("%"));            // '%' must be encoded
        assertEquals("h2%25", AlpnHeaderSupport.encodeId("h2%"));        // stays uppercase hex
    }

    @Test
    void non_tchar_bytes_are_percent_encoded_uppercase() {
        assertEquals("http%2F1.1", AlpnHeaderSupport.encodeId("http/1.1")); // 'F' uppercase
        assertEquals("foo%20bar", AlpnHeaderSupport.encodeId("foo bar"));   // space → %20
    }

    @Test
    void utf8_roundtrip_works() {
        final String raw = "ws/é";
        final String enc = AlpnHeaderSupport.encodeId(raw);
        assertEquals("ws%2F%C3%A9", enc);
        assertEquals(raw, AlpnHeaderSupport.decodeId(enc));
    }

    @Test
    void decoder_is_liberal() {
        assertEquals("http/1.1", AlpnHeaderSupport.decodeId("http%2f1.1")); // lower hex ok
        assertEquals("h2%", AlpnHeaderSupport.decodeId("h2%"));             // incomplete stays literal
        assertEquals("h2%G1", AlpnHeaderSupport.decodeId("h2%G1"));         // invalid hex stays literal
    }
}