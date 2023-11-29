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

import java.io.File;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Args;

/**
 * Builder for multipart {@link HttpEntity}s.
 *
 * @since 5.0
 */
    public class MultipartEntityBuilder {

    /**
     * The pool of ASCII chars to be used for generating a multipart boundary.
     */
    private final static char[] MULTIPART_CHARS =
            "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    .toCharArray();

    private ContentType contentType;
    private HttpMultipartMode mode = HttpMultipartMode.STRICT;
    private String boundary;
    private Charset charset;
    private List<MultipartPart> multipartParts;

    /**
     * The preamble of the multipart message.
     * This field stores the optional preamble that should be added at the beginning of the multipart message.
     * It can be {@code null} if no preamble is needed.
     */
    private String preamble;

    /**
     * The epilogue of the multipart message.
     * This field stores the optional epilogue that should be added at the end of the multipart message.
     * It can be {@code null} if no epilogue is needed.
     */
    private String epilogue;

    /**
     * An empty immutable {@code NameValuePair} array.
     */
    private static final NameValuePair[] EMPTY_NAME_VALUE_ARRAY = {};

    public static MultipartEntityBuilder create() {
        return new MultipartEntityBuilder();
    }

    MultipartEntityBuilder() {
    }

    public MultipartEntityBuilder setMode(final HttpMultipartMode mode) {
        this.mode = mode;
        return this;
    }

    public MultipartEntityBuilder setLaxMode() {
        this.mode = HttpMultipartMode.LEGACY;
        return this;
    }

    public MultipartEntityBuilder setStrictMode() {
        this.mode = HttpMultipartMode.STRICT;
        return this;
    }

    public MultipartEntityBuilder setBoundary(final String boundary) {
        this.boundary = boundary;
        return this;
    }

    /**
     * @since 4.4
     */
    public MultipartEntityBuilder setMimeSubtype(final String subType) {
        Args.notBlank(subType, "MIME subtype");
        this.contentType = ContentType.create("multipart/" + subType);
        return this;
    }

    /**
     * @since 4.5
     */
    public MultipartEntityBuilder setContentType(final ContentType contentType) {
        Args.notNull(contentType, "Content type");
        this.contentType = contentType;
        return this;
    }
    /**
     *  Add parameter to the current {@link ContentType}.
     *
     * @param parameter The name-value pair parameter to add to the {@link ContentType}.
     * @return the {@link MultipartEntityBuilder} instance.
     * @since 5.2
     */
    public MultipartEntityBuilder addParameter(final BasicNameValuePair parameter) {
        this.contentType = contentType.withParameters(parameter);
        return this;
    }

    public MultipartEntityBuilder setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    /**
     * @since 4.4
     */
    public MultipartEntityBuilder addPart(final MultipartPart multipartPart) {
        if (multipartPart == null) {
            return this;
        }
        if (this.multipartParts == null) {
            this.multipartParts = new ArrayList<>();
        }
        this.multipartParts.add(multipartPart);
        return this;
    }

    public MultipartEntityBuilder addPart(final String name, final ContentBody contentBody) {
        Args.notNull(name, "Name");
        Args.notNull(contentBody, "Content body");
        return addPart(FormBodyPartBuilder.create(name, contentBody).build());
    }

    public MultipartEntityBuilder addTextBody(
            final String name, final String text, final ContentType contentType) {
        return addPart(name, new StringBody(text, contentType));
    }

    public MultipartEntityBuilder addTextBody(
            final String name, final String text) {
        return addTextBody(name, text, ContentType.DEFAULT_TEXT);
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final byte[] b, final ContentType contentType, final String filename) {
        return addPart(name, new ByteArrayBody(b, contentType, filename));
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final byte[] b) {
        return addPart(name, new ByteArrayBody(b, ContentType.DEFAULT_BINARY));
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final File file, final ContentType contentType, final String filename) {
        return addPart(name, new FileBody(file, contentType, filename));
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final File file) {
        return addBinaryBody(name, file, ContentType.DEFAULT_BINARY, file != null ? file.getName() : null);
    }

    public MultipartEntityBuilder addBinaryBody(
            final String name, final InputStream stream, final ContentType contentType,
            final String filename) {
        return addPart(name, new InputStreamBody(stream, contentType, filename));
    }

    public MultipartEntityBuilder addBinaryBody(final String name, final InputStream stream) {
        return addBinaryBody(name, stream, ContentType.DEFAULT_BINARY, null);
    }

    /**
     * Adds a preamble to the multipart entity being constructed. The preamble is the text that appears before the first
     * boundary delimiter. The preamble is optional and may be null.
     *
     * @param preamble The preamble text to add to the multipart entity
     * @return This MultipartEntityBuilder instance, to allow for method chaining
     *
     * @since 5.3
     */
    public MultipartEntityBuilder addPreamble(final String preamble) {
        this.preamble = preamble;
        return this;
    }

    /**
     * Adds an epilogue to the multipart entity being constructed. The epilogue is the text that appears after the last
     * boundary delimiter. The epilogue is optional and may be null.
     *
     * @param epilogue The epilogue text to add to the multipart entity
     * @return This MultipartEntityBuilder instance, to allow for method chaining
     * @since 5.3
     */
    public MultipartEntityBuilder addEpilogue(final String epilogue) {
        this.epilogue = epilogue;
        return this;
    }

    private String generateBoundary() {
        final ThreadLocalRandom rand = ThreadLocalRandom.current();
        final int count = rand.nextInt(30, 41); // a random size from 30 to 40
        final CharBuffer buffer = CharBuffer.allocate(count);
        while (buffer.hasRemaining()) {
            buffer.put(MULTIPART_CHARS[rand.nextInt(MULTIPART_CHARS.length)]);
        }
        buffer.flip();
        return buffer.toString();
    }

    MultipartFormEntity buildEntity() {
        String boundaryCopy = boundary;
        if (boundaryCopy == null && contentType != null) {
            boundaryCopy = contentType.getParameter("boundary");
        }
        if (boundaryCopy == null) {
            boundaryCopy = generateBoundary();
        }
        Charset charsetCopy = charset;
        if (charsetCopy == null && contentType != null) {
            charsetCopy = contentType.getCharset();
        }
        final List<NameValuePair> paramsList = new ArrayList<>(2);
        paramsList.add(new BasicNameValuePair("boundary", boundaryCopy));
        if (charsetCopy != null) {
            paramsList.add(new BasicNameValuePair("charset", charsetCopy.name()));
        }
        final NameValuePair[] params = paramsList.toArray(EMPTY_NAME_VALUE_ARRAY);

        final ContentType contentTypeCopy;
        if (contentType != null) {
            contentTypeCopy = contentType.withParameters(params);
        } else {
            boolean formData = false;
            if (multipartParts != null) {
                for (final MultipartPart multipartPart : multipartParts) {
                    if (multipartPart instanceof FormBodyPart) {
                        formData = true;
                        break;
                    }
                }
            }

            if (formData) {
                contentTypeCopy = ContentType.MULTIPART_FORM_DATA.withParameters(params);
            } else {
                contentTypeCopy = ContentType.create("multipart/mixed", params);
            }
        }
        final List<MultipartPart> multipartPartsCopy = multipartParts != null ? new ArrayList<>(multipartParts) :
                Collections.emptyList();
        final HttpMultipartMode modeCopy = mode != null ? mode : HttpMultipartMode.STRICT;
        final AbstractMultipartFormat form;
        switch (modeCopy) {
            case LEGACY:
                form = new LegacyMultipart(charsetCopy, boundaryCopy, multipartPartsCopy);
                break;
            case EXTENDED:
                if (contentTypeCopy.isSameMimeType(ContentType.MULTIPART_FORM_DATA)) {
                    if (charsetCopy == null) {
                        charsetCopy = StandardCharsets.UTF_8;
                    }
                    form = new HttpRFC7578Multipart(charsetCopy, boundaryCopy, multipartPartsCopy, preamble, epilogue);
                } else {
                    form = new HttpRFC6532Multipart(charsetCopy, boundaryCopy, multipartPartsCopy, preamble, epilogue);
                }
                break;
            default:
                form = new HttpStrictMultipart(StandardCharsets.US_ASCII, boundaryCopy, multipartPartsCopy, preamble, epilogue);
        }
        return new MultipartFormEntity(form, contentTypeCopy, form.getTotalLength());
    }

    public HttpEntity build() {
        return buildEntity();
    }

}
