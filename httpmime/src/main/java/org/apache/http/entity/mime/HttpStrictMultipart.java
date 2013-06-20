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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.util.Args;
import org.apache.http.util.ByteArrayBuffer;

/**
 * HttpStrictMultipart represents a collection of MIME multipart encoded content bodies, implementing the
 * strict (RFC 822, RFC 2045, RFC 2046 compliant) interpretation of the spec.
 *
 * @since 4.3
 */
public class HttpStrictMultipart extends HttpMultipartForm {

    /**
     * Creates an instance with the specified settings.
     *
     * @param subType mime subtype - must not be {@code null}
     * @param charset the character set to use. May be {@code null}, in which case {@link MIME#DEFAULT_CHARSET} - i.e. US-ASCII - is used.
     * @param boundary to use  - must not be {@code null}
     * @throws IllegalArgumentException if charset is null or boundary is null
     */
    public HttpStrictMultipart(final String subType, final Charset charset, final String boundary) {
        super(subType, charset, boundary);
    }

    public HttpStrictMultipart(final String subType, final String boundary) {
        this(subType, null, boundary);
    }

    /**
      * Write the multipart header fields; depends on the style.
      */
    @Override
    protected void formatMultipartHeader(
        final FormBodyPart part,
        final OutputStream out) throws IOException {

        // For strict, we output all fields with mime-standard encoding.
        final Header header = part.getHeader();
        for (final MinimalField field: header) {
            writeField(field, out);
        }
    }
    
}
