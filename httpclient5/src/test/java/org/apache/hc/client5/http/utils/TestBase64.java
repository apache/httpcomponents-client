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

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestBase64 {

    public static final char CHAR_ZERO = 0;
    public static final String EMPTY_STR = "";
    public static final byte[] EMPTY_BYTES = new byte[0];
    public static final String NULL_STR = null;
    public static final byte[] NULL_BYTE_ARRAY = null;
    public static final String EMOJI = "\uD83D\uDE15";
    public static final char SPACE = ' ';

    private final Base64 target = new Base64();

    @Test
    void nullHandling() {
        assertNull(target.decode(NULL_STR));
        assertNull(target.decode(NULL_BYTE_ARRAY));
        assertNull(Base64.decodeBase64(NULL_STR));
        assertNull(Base64.decodeBase64(NULL_BYTE_ARRAY));

        assertNull(target.encode(NULL_BYTE_ARRAY));
        assertNull(Base64.encodeBase64(NULL_BYTE_ARRAY));
        assertNull(Base64.encodeBase64String(NULL_BYTE_ARRAY));
    }

    @Test
    void zeroLength() {
        assertArrayEquals(EMPTY_BYTES, target.decode(EMPTY_STR));
        assertArrayEquals(EMPTY_BYTES, target.decode(EMPTY_BYTES));
        assertArrayEquals(EMPTY_BYTES, Base64.decodeBase64(EMPTY_STR));
        assertArrayEquals(EMPTY_BYTES, Base64.decodeBase64(EMPTY_BYTES));

        assertArrayEquals(EMPTY_BYTES, target.encode(EMPTY_BYTES));
        assertArrayEquals(EMPTY_BYTES, Base64.encodeBase64(EMPTY_BYTES));
        assertEquals(EMPTY_STR, Base64.encodeBase64String(EMPTY_BYTES));
    }

    @Test
    void validValues() {
        final byte[] unencodedBytes = "Hello World!".getBytes(US_ASCII);
        checkDecode(unencodedBytes, "SGVsbG8gV29ybGQh");
        checkEncode("SGVsbG8gV29ybGQh", unencodedBytes);
    }

    @Test
    void decodeIgnoresEmbeddedInvalidChars() {
        checkEquivalentDecode(fourOf("A"), " A A A A ");
        checkEquivalentDecode(fourOf("A"), "AA" + EMOJI + "AA");
    }

    @Test
    void decodeInvalid() {
        checkDecode(EMPTY_BYTES, fourOf(EMOJI));
        checkDecode(EMPTY_BYTES, "A");
        checkDecode(EMPTY_BYTES, "A===");
        checkDecode(EMPTY_BYTES, fourOf(SPACE));
        checkDecode(EMPTY_BYTES, fourOf('='));
        checkDecode(EMPTY_BYTES, fourOf('@'));
        checkDecode(EMPTY_BYTES, fourOf(CHAR_ZERO));
    }

    @Test
    void decodeUnpadded() {
        checkEquivalentDecode("AA==", "AA");
    }

    private void checkDecode(final byte[] expectedDecoded, final String testInput) {
        final byte[] decoded = target.decode(testInput);
        assertArrayEquals(expectedDecoded, decoded);
    }

    private void checkEncode(final String expectedEncoded, final byte[] testInput) {
        final byte[] encoded = target.encode(testInput);
        assertEquals(expectedEncoded, new String(encoded, US_ASCII));
    }

    private void checkEquivalentDecode(final String expectedEquivalentTo, final String testInput) {
        final byte[] decoded = target.decode(testInput);

        final byte[] expectedDecoded = java.util.Base64.getDecoder().decode(expectedEquivalentTo);
        assertArrayEquals(expectedDecoded, decoded);
    }

    private static String fourOf(final char c) {
        final String charStr = String.valueOf(c);
        return fourOf(charStr);
    }

    private static String fourOf(final String str) {
        return str + str + str + str;
    }

}