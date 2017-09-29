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

package org.apache.http.entity.mime;

import org.apache.commons.codec.DecoderException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HttpRFC7578Multipart extends AbstractMultipartForm {

    private final List<FormBodyPart> parts;

    public HttpRFC7578Multipart(
        final String boundary,
        final List<FormBodyPart> parts) {
        super(Charset.forName("UTF-8"), boundary);
        this.parts = parts;
    }

    @Override
    public List<FormBodyPart> getBodyParts() {
        return parts;
    }

    @Override
    protected void formatMultipartHeader(final FormBodyPart part, final OutputStream out) throws IOException {
        for (final MinimalField field : part.getHeader()) {
            if (MIME.CONTENT_DISPOSITION.equals(field.getName()) && field.getParameters() != null) {
                //need to create a copy of field to perform encoding on, because this might happen multiple times
                final MinimalField fieldCopy = new MinimalField(field);
                for (final Iterator<Map.Entry<MIME.HeaderFieldParam, String>> it = fieldCopy.getParameters().entrySet().iterator(); it.hasNext(); ) {
                    final Map.Entry<MIME.HeaderFieldParam, String> next = it.next();
                    if (MIME.HeaderFieldParam.FILENAME.equals(next.getKey())) {
                        final String encodedFilenameValue = encodeWithPercentEncoding(next.getValue());
                        fieldCopy.getParameters().put(next.getKey(), encodedFilenameValue);
                    }
                }
                writeField(fieldCopy, charset, out);
            } else {
                writeField(field, charset, out);
            }
        }
    }

    private String encodeWithPercentEncoding(final String str) {
        final PercentCodec percentCodec = new PercentCodec();
        final byte[] percentEncodeResult = percentCodec.encode(str.getBytes(charset));
        return new String(percentEncodeResult, charset);
    }

    static class PercentCodec {

        protected static final byte ESCAPE_CHAR = '%';

        private static BitSet alwaysEncodeChars = new BitSet();

        static {
            alwaysEncodeChars.set(' ');
            alwaysEncodeChars.set('%');
        }

        /**
         * Percent-Encoding implementation based on RFC 3986
         */
        public byte[] encode(final byte[] bytes) {
            if (bytes == null) {
                return null;
            }

            final CharsetEncoder characterSetEncoder = Charset.forName("US-ASCII").newEncoder();

            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (final byte c : bytes) {
                int b = c;
                if (b < 0) {
                    b = 256 + b;
                }
                if (characterSetEncoder.canEncode((char) b) && !alwaysEncodeChars.get(c)) {
                    buffer.write(b);
                } else {
                    buffer.write(ESCAPE_CHAR);
                    final char hex1 = Utils.hexDigit(b >> 4);
                    final char hex2 = Utils.hexDigit(b);
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
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (int i = 0; i < bytes.length; i++) {
                final int b = bytes[i];
                if (b == ESCAPE_CHAR) {
                    try {
                        final int u = Utils.digit16(bytes[++i]);
                        final int l = Utils.digit16(bytes[++i]);
                        buffer.write((char) ((u << 4) + l));
                    } catch (final ArrayIndexOutOfBoundsException e) {
                        throw new DecoderException("Invalid URL encoding: ", e);
                    }
                } else {
                    buffer.write(b);
                }
            }
            return buffer.toByteArray();
        }
    }

    static class Utils {

        /**
         * Radix used in encoding and decoding.
         */
        private static final int RADIX = 16;

        /**
         * Returns the numeric value of the character <code>b</code> in radix 16.
         *
         * @param b The byte to be converted.
         * @return The numeric value represented by the character in radix 16.
         * @throws DecoderException Thrown when the byte is not valid per {@link Character#digit(char, int)}
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

}
