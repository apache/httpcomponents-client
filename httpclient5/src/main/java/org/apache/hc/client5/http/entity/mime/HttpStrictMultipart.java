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
import java.util.List;

class HttpStrictMultipart extends AbstractMultipartFormat {

    private final List<MultipartPart> parts;

    /**
     * Constructs a new instance of {@code HttpStrictMultipart} with the given charset, boundary, and parts.
     *
     * @param charset the charset to use.
     * @param boundary the boundary string to use.
     * @param parts the list of parts to include in the multipart message.
     */
    public HttpStrictMultipart(
            final Charset charset,
            final String boundary,
            final List<MultipartPart> parts) {
       this(charset,boundary,parts,null, null);
    }


    /**
     * Constructs a new instance of {@code HttpStrictMultipart} with the given charset, boundary, parts, preamble, and epilogue.
     *
     * @param charset the charset to use.
     * @param boundary the boundary string to use.
     * @param parts the list of parts to include in the multipart message.
     * @param preamble the preamble string to use.
     * @param epilogue the epilogue string to use.
     */
    public HttpStrictMultipart(
            final Charset charset,
            final String boundary,
            final List<MultipartPart> parts,
            final String preamble,
            final String epilogue) {
        super(charset, boundary, preamble, epilogue);
        this.parts = parts;
    }

    @Override
    public List<MultipartPart> getParts() {
        return this.parts;
    }

    @Override
    protected void formatMultipartHeader(
        final MultipartPart part,
        final OutputStream out) throws IOException {

        // For strict, we output all fields with MIME-standard encoding.
        final Header header = part.getHeader();
        for (final MimeField field: header) {
            writeField(field, out);
        }
    }

}
