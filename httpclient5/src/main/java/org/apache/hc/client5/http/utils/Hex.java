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

@Internal
public class Hex {

    private Hex() {
    }

    public static String encodeHexString(final byte[] bytes) {

        final char[] out = new char[bytes.length * 2];

        encodeHex(bytes, 0, bytes.length, DIGITS_LOWER, out, 0);
        return new String(out);
    }

    //
    // The following comes from commons-codec
    // https://github.com/apache/commons-codec/blob/master/src/main/java/org/apache/commons/codec/binary/Hex.java

    /**
     * Used to build output as hex.
     */

    private static final char[] DIGITS_LOWER = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     *
     * @param data       a byte[] to convert to hex characters
     * @param dataOffset the position in {@code data} to start encoding from
     * @param dataLen    the number of bytes from {@code dataOffset} to encode
     * @param toDigits   the output alphabet (must contain at least 16 chars)
     * @param out        a char[] which will hold the resultant appropriate characters from the alphabet.
     * @param outOffset  the position within {@code out} at which to start writing the encoded characters.
     */
    private static void encodeHex(final byte[] data, final int dataOffset, final int dataLen, final char[] toDigits,
                                  final char[] out, final int outOffset) {
        // two characters form the hex value.
        for (int i = dataOffset, j = outOffset; i < dataOffset + dataLen; i++) {
            out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
            out[j++] = toDigits[0x0F & data[i]];
        }
    }

    /*

       // Can be replaced in Java 17 with the following:


    private static final java.util.HexFormat HEX_FORMAT = HexFormat.of();

    public static String encodeHex(byte[] bytes) {
        return HEX_FORMAT.formatHex(bytes);
    }


     */


}
