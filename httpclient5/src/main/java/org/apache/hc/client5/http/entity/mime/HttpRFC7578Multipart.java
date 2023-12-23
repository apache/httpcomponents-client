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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.PercentCodec;

class HttpRFC7578Multipart extends AbstractMultipartFormat {

    private final List<MultipartPart> parts;

    /**
     * Constructs a new instance of {@code HttpRFC7578Multipart} with the given charset, boundary, parts, preamble, and epilogue.
     *
     * @param charset  the charset to use.
     * @param boundary the boundary string to use.
     * @param parts    the list of parts to include in the multipart message.
     * @param preamble the optional preamble string to include before the first part. May be {@code null}.
     * @param epilogue the optional epilogue string to include after the last part. May be {@code null}.
     */
    public HttpRFC7578Multipart(
        final Charset charset,
        final String boundary,
        final List<MultipartPart> parts,
        final String preamble,
        final String epilogue) {
        super(charset, boundary, preamble, epilogue);
        this.parts = parts;
    }

    /**
     * Constructs a new instance of {@code HttpRFC7578Multipart} with the given charset, boundary, and parts.
     *
     * @param charset  the charset to use.
     * @param boundary the boundary string to use.
     * @param parts    the list of parts to include in the multipart message.
     */
    public HttpRFC7578Multipart(
            final Charset charset,
            final String boundary,
            final List<MultipartPart> parts) {
        this(charset,boundary,parts,null, null);
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
                        if (name.equalsIgnoreCase(MimeConsts.FIELD_PARAM_FILENAME) ||
                                name.equalsIgnoreCase(MimeConsts.FIELD_PARAM_FILENAME_START)) {
                            final String encodedValue = name.equalsIgnoreCase(MimeConsts.FIELD_PARAM_FILENAME_START) ?
                                    "UTF-8''" + PercentCodec.RFC5987.encode(value) : PercentCodec.RFC5987.encode(value);
                            final byte[] encodedBytes = encodedValue.getBytes(StandardCharsets.US_ASCII);
                            out.write(encodedBytes);
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

}
