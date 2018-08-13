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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Random;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.content.ContentBody;

/**
 * Multipart/form coded HTTP entity consisting of multiple body parts.
 *
 * @since 4.0
 *
 * @deprecated 4.3 Use {@link MultipartEntityBuilder}.
 */
@Deprecated
public class MultipartEntity implements HttpEntity {

    /**
     * The pool of ASCII chars to be used for generating a multipart boundary.
     */
    private final static char[] MULTIPART_CHARS =
        "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            .toCharArray();

    private final MultipartEntityBuilder builder;
    private volatile MultipartFormEntity entity;

    /**
     * Creates an instance using the specified parameters
     * @param mode the mode to use, may be {@code null}, in which case {@link HttpMultipartMode#STRICT} is used
     * @param boundary the boundary string, may be {@code null}, in which case {@link #generateBoundary()} is invoked to create the string
     * @param charset the character set to use, may be {@code null}, in which case {@link MIME#DEFAULT_CHARSET} - i.e. US-ASCII - is used.
     */
    public MultipartEntity(
            final HttpMultipartMode mode,
            final String boundary,
            final Charset charset) {
        super();
        this.builder = new MultipartEntityBuilder()
                .setMode(mode)
                .setCharset(charset != null ? charset : MIME.DEFAULT_CHARSET)
                .setBoundary(boundary);
        this.entity = null;
    }

    /**
     * Creates an instance using the specified {@link HttpMultipartMode} mode.
     * Boundary and charset are set to {@code null}.
     * @param mode the desired mode
     */
    public MultipartEntity(final HttpMultipartMode mode) {
        this(mode, null, null);
    }

    /**
     * Creates an instance using mode {@link HttpMultipartMode#STRICT}
     */
    public MultipartEntity() {
        this(HttpMultipartMode.STRICT, null, null);
    }

    protected String generateContentType(
            final String boundary,
            final Charset charset) {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("multipart/form-data; boundary=");
        buffer.append(boundary);
        if (charset != null) {
            buffer.append("; charset=");
            buffer.append(charset.name());
        }
        return buffer.toString();
    }

    protected String generateBoundary() {
        final StringBuilder buffer = new StringBuilder();
        final Random rand = new Random();
        final int count = rand.nextInt(11) + 30; // a random size from 30 to 40
        for (int i = 0; i < count; i++) {
            buffer.append(MULTIPART_CHARS[rand.nextInt(MULTIPART_CHARS.length)]);
        }
        return buffer.toString();
    }

    private MultipartFormEntity getEntity() {
        if (this.entity == null) {
            this.entity = this.builder.buildEntity();
        }
        return this.entity;
    }

    public void addPart(final FormBodyPart bodyPart) {
        this.builder.addPart(bodyPart);
        this.entity = null;
    }

    public void addPart(final String name, final ContentBody contentBody) {
        addPart(new FormBodyPart(name, contentBody));
    }

    @Override
    public boolean isRepeatable() {
        return getEntity().isRepeatable();
    }

    @Override
    public boolean isChunked() {
        return getEntity().isChunked();
    }

    @Override
    public boolean isStreaming() {
        return getEntity().isStreaming();
    }

    @Override
    public long getContentLength() {
        return getEntity().getContentLength();
    }

    @Override
    public Header getContentType() {
        return getEntity().getContentType();
    }

    @Override
    public Header getContentEncoding() {
        return getEntity().getContentEncoding();
    }

    @Override
    public void consumeContent()
        throws IOException, UnsupportedOperationException{
        if (isStreaming()) {
            throw new UnsupportedOperationException(
                    "Streaming entity does not implement #consumeContent()");
        }
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        throw new UnsupportedOperationException(
                    "Multipart form entity does not implement #getContent()");
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        getEntity().writeTo(outStream);
    }

}
