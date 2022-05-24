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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBase64 {

    public static final String EMOJI = "\uD83D\uDE15";
    public static final String ACCENT = "\u00E9";

    @Test
    void nullHandling() {
        checkDecode(null);
        checkEncode(null);
    }

    @Test
    void zeroLength() {
        checkDecode("");
        checkEncode(new byte[0]);
    }

    @Test
    void encodeByteValues() {
        for (int i = 0; i < 256; i++) {
            final byte[] value = new byte[10];
            for (int j = 0; j < value.length; j++) {
                value[j] = (byte) (i + j);
            }
            checkEncodeThenDecode(value);
        }
    }

    @Test
    void largeValue() {
        final byte[] longArray = new byte[8000];
        checkEncodeThenDecode(longArray);
    }


    @Test
    void varyingLengths() {
        for (int i = 0; i < 10; i++) {
            final byte[] value = new byte[i];
            checkEncodeThenDecode(value);
        }
    }


    @Test
    void decodeIgnoresEmbeddedInvalidChars() {
        checkDecode("This is\n\nA test\ttest 123\n!\r\ndone");
        checkDecode("AA AA");
        checkDecode("AA" + EMOJI + "AA");
        checkDecode("AA" + ACCENT + "AA");
        checkDecode(" A A A A ");
    }

    @Test
    void decodeInvalid() {
        final char space = ' ';

        checkDecode(fourOf(EMOJI));
        checkDecode(fourOf(ACCENT));
        checkDecode("A");
        checkDecode("A===");
        checkDecode(fourOf(space));
        checkDecode(fourOf('='));
        checkDecode(fourOf('@'));
        checkDecode(fourOf((char) 0));
    }

    @Test
    void decodeUnpadded() {
        checkDecode("AA");
        checkDecode("AAA");
        checkDecode("BBBBAA");
        checkDecode("BBBBAAA");
    }

    @Test
    void decodeUrlSafe() {
        final char underscore = '_';
        final char minus = '-';

        checkDecode(fourOf(minus));
        checkDecode(fourOf(underscore));
    }

    @Test
    void mixedUrlAndRegularEncoded() {
        checkDecode("++__");
        checkDecode("--//");
    }

    String checkEncode(final byte[] toEncode) {
        final String expected = encodeWithCommonsCodec(toEncode);
        final String actual = Base64.encodeBase64String(toEncode);

        assertEquals(expected, actual);
        return actual;
    }

    void checkDecode(final String encoded) {
        final byte[] expected = decodeWithCommonsCodec(encoded);
        final byte[] actual = Base64.decodeBase64(encoded);

        assertArrayEquals(expected, actual);
    }

    private void checkEncodeThenDecode(final byte[] longArray) {
        final String encoded = checkEncode(longArray);
        checkDecode(encoded);
    }

    private static String fourOf(final char c) {
        final String charStr = String.valueOf(c);
        return fourOf(charStr);
    }

    private static String fourOf(final String str) {
        return str + str + str + str;
    }
    // baseline methods

    String encodeWithCommonsCodec(final byte[] value) {
        return org.apache.commons.codec.binary.Base64.encodeBase64String(value);
    }

    byte[] decodeWithCommonsCodec(final String value) {
        return org.apache.commons.codec.binary.Base64.decodeBase64(value);
    }

}