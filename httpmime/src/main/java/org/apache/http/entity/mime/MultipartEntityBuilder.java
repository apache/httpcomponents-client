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

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.util.Args;

/**
 * @since 4.3
 */
public class MultipartEntityBuilder {

    private boolean lax;
    private String boundary;
    private Charset charset;
    private List<FormBodyPart> bodyParts;

    public static MultipartEntityBuilder create() {
        return new MultipartEntityBuilder();
    }

    MultipartEntityBuilder() {
        super();
    }

    public MultipartEntityBuilder setLaxMode() {
        this.lax = true;
        return this;
    }

    public MultipartEntityBuilder setStrictMode() {
        this.lax = false;
        return this;
    }

    public MultipartEntityBuilder setBoundary(final String boundary) {
        this.boundary = boundary;
        return this;
    }

    public MultipartEntityBuilder setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    public MultipartEntityBuilder addTextBody(
            final String name, final String text, final ContentType contentType) {
        Args.notNull(name, "Name");
        Args.notNull(text, "Text");
        if (this.bodyParts == null) {
            this.bodyParts = new ArrayList<FormBodyPart>();
        }
        this.bodyParts.add(new FormBodyPart(name, new StringBody(text, contentType)));
        return this;
    }

    public MultipartEntityBuilder addTextBody(
            final String name, final String text) {
        return addTextBody(name, text, ContentType.DEFAULT_TEXT);
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final byte[] b, final ContentType contentType, final String filename) {
        if (this.bodyParts == null) {
            this.bodyParts = new ArrayList<FormBodyPart>();
        }
        this.bodyParts.add(new FormBodyPart(name, new ByteArrayBody(b, contentType, filename)));
        return this;
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final byte[] b) {
        return addBinaryBody(name, b, ContentType.DEFAULT_BINARY, null);
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final File file, final ContentType contentType, final String filename) {
        if (this.bodyParts == null) {
            this.bodyParts = new ArrayList<FormBodyPart>();
        }
        this.bodyParts.add(
                new FormBodyPart(name, new FileBody(file, contentType, filename)));
        return this;
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final File file) {
        return addBinaryBody(name, file, ContentType.DEFAULT_BINARY, null);
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final InputStream stream, final ContentType contentType,
            final String filename) {
        if (this.bodyParts == null) {
            this.bodyParts = new ArrayList<FormBodyPart>();
        }
        this.bodyParts.add(
                new FormBodyPart(name, new InputStreamBody(stream, contentType, filename)));
        return this;
    }

    public MultipartEntityBuilder addBinaryBody(final String name, final InputStream stream) {
        return addBinaryBody(name, stream, ContentType.DEFAULT_BINARY, null);
    }

    public MultipartEntity build() {
        final MultipartEntity e = new MultipartEntity(
                this.lax ? HttpMultipartMode.BROWSER_COMPATIBLE : HttpMultipartMode.STRICT,
                this.boundary, this.charset);
        if (this.bodyParts != null) {
            for (final FormBodyPart bp: this.bodyParts) {
                e.addPart(bp);
            }
        }
        return e;
    }

}
