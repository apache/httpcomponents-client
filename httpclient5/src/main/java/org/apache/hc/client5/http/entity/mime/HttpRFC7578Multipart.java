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

package org.apache.hc.client5.http.entity.mime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.ByteArrayBuffer;

class HttpRFC7578Multipart extends AbstractMultipartFormat {

    private static final PercentCodec PERCENT_CODEC = new PercentCodec();

    private final List<MultipartPart> parts;

    public HttpRFC7578Multipart(
        final Charset charset,
        final String boundary,
        final List<MultipartPart> parts) {
        super(charset, boundary);
        this.parts = parts;
    }

    @Override
    public List<MultipartPart> getParts() {
        return parts;
    }

    @Override
    protected void formatMultipartHeader(final MultipartPart part, final OutputStream out) throws IOException {
        for (final MimeField field: part.getHeader()) {
            if (MimeConsts.CONTENT_DISPOSITION.equalsIgnoreCase(field.getName())) {
                writeBytes(field.getName(), charset, out);
                writeBytes(FIELD_SEP, out);
                writeBytes(field.getValue(), out);
                final List<NameValuePair> parameters = field.getParameters();
                for (int i = 0; i < parameters.size(); i++) {
                    final NameValuePair parameter = parameters.get(i);
                    final String name = parameter.getName();
                    final String value = parameter.getValue();
                    writeBytes("; ", out);
                    writeBytes(name, out);
                    writeBytes("=\"", out);
                    if (value != null) {
                        if (name.equalsIgnoreCase(MimeConsts.FIELD_PARAM_FILENAME)) {
                            out.write(PERCENT_CODEC.encode(value.getBytes(charset)));
                        } else {
                            writeBytes(value, out);
                        }
                    }
                    writeBytes("\"", out);
                }
                writeBytes(CR_LF, out);
            } else {
                writeField(field, charset, out);
            }
        }
    }

    static class PercentCodec {

        private static final byte ESCAPE_CHAR = '%';

        private static final BitSet ALWAYSENCODECHARS = new BitSet();

        static {
            ALWAYSENCODECHARS.set(' ');
            ALWAYSENCODECHARS.set('%');
        }

        /**
         * Percent-Encoding implementation based on RFC 3986
         */
        public byte[] encode(final byte[] bytes) {
            if (bytes == null) {
                return null;
            }

            final CharsetEncoder characterSetEncoder = StandardCharsets.US_ASCII.newEncoder();
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (final byte c : bytes) {
                int b = c;
                if (b < 0) {
                    b = 256 + b;
                }
                if (characterSetEncoder.canEncode((char) b) && !ALWAYSENCODECHARS.get(c)) {
                    buffer.write(b);
                } else {
                    buffer.write(ESCAPE_CHAR);
                    final char hex1 = hexDigit(b >> 4);
                    final char hex2 = hexDigit(b);
                    buffer.write(hex1);
                    buffer.write(hex2);
                }
            }
            return buffer.toByteArray();
        }

        public byte[] decode(final byte[] bytes) throws DecoderException {
            if (bytes == null) {
                return null;
            }
            final ByteArrayBuffer buffer = new ByteArrayBuffer(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                final int b = bytes[i];
                if (b == ESCAPE_CHAR) {
                    try {
                        final int u = digit16(bytes[++i]);
                        final int l = digit16(bytes[++i]);
                        buffer.append((char) ((u << 4) + l));
                    } catch (final ArrayIndexOutOfBoundsException e) {
                        throw new DecoderException("Invalid URL encoding: ", e);
                    }
                } else {
                    buffer.append(b);
                }
            }
            return buffer.toByteArray();
        }
    }

    /**
     * Radix used in encoding and decoding.
     */
    private static final int RADIX = 16;

    /**
     * Returns the numeric value of the character <code>b</code> in radix 16.
     *
     * @param b
     *            The byte to be converted.
     * @return The numeric value represented by the character in radix 16.
     *
     * @throws DecoderException
     *             Thrown when the byte is not valid per {@link Character#digit(char,int)}
     */
    static int digit16(final byte b) throws DecoderException {
        final int i = Character.digit((char) b, RADIX);
        if (i == -1) {
            throw new DecoderException("Invalid URL encoding: not a valid digit (radix " + RADIX + "): " + b);
        }
        return i;
    }

    /**
     * Returns the upper case hex digit of the lower 4 bits of the int.
     *
     * @param b the input int
     * @return the upper case hex digit of the lower 4 bits of the int.
     */
    static char hexDigit(final int b) {
        return Character.toUpperCase(Character.forDigit(b & 0xF, RADIX));
    }

}
