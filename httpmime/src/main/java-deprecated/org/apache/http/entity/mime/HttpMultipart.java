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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * HttpMultipart represents a collection of MIME multipart encoded content bodies. This class is
 * capable of operating either in the strict (RFC 822, RFC 2045, RFC 2046 compliant) or
 * the browser compatible modes.
 *
 * @since 4.0
 *
 * @deprecated  (4.3) Use {@link MultipartEntityBuilder}.
 */
 @Deprecated
public class HttpMultipart extends AbstractMultipartForm {

    private final HttpMultipartMode mode;
    private final List<FormBodyPart> parts;

    private final String subType;

    /**
     * Creates an instance with the specified settings.
     *
     * @param subType MIME subtype - must not be {@code null}
     * @param charset the character set to use. May be {@code null},
     *  in which case {@link MIME#DEFAULT_CHARSET} - i.e. US-ASCII - is used.
     * @param boundary to use  - must not be {@code null}
     * @param mode the mode to use
     * @throws IllegalArgumentException if charset is null or boundary is null
     */
    public HttpMultipart(
            final String subType, final Charset charset, final String boundary,
            final HttpMultipartMode mode) {
        super(charset, boundary);
        this.subType = subType;
        this.mode = mode;
        this.parts = new ArrayList<FormBodyPart>();
    }

    /**
     * Creates an instance with the specified settings.
     * Mode is set to {@link HttpMultipartMode#STRICT}
     *
     * @param subType MIME subtype - must not be {@code null}
     * @param charset the character set to use. May be {@code null},
     *   in which case {@link MIME#DEFAULT_CHARSET} - i.e. US-ASCII - is used.
     * @param boundary to use  - must not be {@code null}
     * @throws IllegalArgumentException if charset is null or boundary is null
     */
    public HttpMultipart(final String subType, final Charset charset, final String boundary) {
        this(subType, charset, boundary, HttpMultipartMode.STRICT);
    }

    public HttpMultipart(final String subType, final String boundary) {
        this(subType, null, boundary);
    }

    public HttpMultipartMode getMode() {
        return this.mode;
    }

    @Override
    protected void formatMultipartHeader(
            final FormBodyPart part, final OutputStream out) throws IOException {
        final Header header = part.getHeader();
        switch (this.mode) {
            case BROWSER_COMPATIBLE:
                // For browser-compatible, only write Content-Disposition
                // Use content charset
                final MinimalField cd = header.getField(MIME.CONTENT_DISPOSITION);
                writeField(cd, this.charset, out);
                final String filename = part.getBody().getFilename();
                if (filename != null) {
                    final MinimalField ct = header.getField(MIME.CONTENT_TYPE);
                    writeField(ct, this.charset, out);
                }
                break;
            default:
                for (final MinimalField field: header) {
                    writeField(field, out);
                }
        }
    }

    @Override
    public List<FormBodyPart> getBodyParts() {
        return this.parts;
    }

    public void addBodyPart(final FormBodyPart part) {
        if (part == null) {
            return;
        }
        this.parts.add(part);
    }

    public String getSubType() {
        return this.subType;
    }

    public Charset getCharset() {
        return this.charset;
    }

    public String getBoundary() {
        return this.boundary;
    }

}
