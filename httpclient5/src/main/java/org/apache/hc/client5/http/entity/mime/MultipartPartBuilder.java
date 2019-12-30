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

import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * Builder for individual {@link MultipartPart}s.
 *
 * @since 4.4
 */
public class MultipartPartBuilder {

    private ContentBody body;
    private final Header header;

    public static MultipartPartBuilder create(final ContentBody body) {
        return new MultipartPartBuilder(body);
    }

    public static MultipartPartBuilder create() {
        return new MultipartPartBuilder();
    }

    MultipartPartBuilder(final ContentBody body) {
        this();
        this.body = body;
    }

    MultipartPartBuilder() {
        this.header = new Header();
    }

    public MultipartPartBuilder setBody(final ContentBody body) {
        this.body = body;
        return this;
    }

    public MultipartPartBuilder addHeader(final String name, final String value, final List<NameValuePair> parameters) {
        Args.notNull(name, "Header name");
        this.header.addField(new MimeField(name, value, parameters));
        return this;
    }

    public MultipartPartBuilder addHeader(final String name, final String value) {
        Args.notNull(name, "Header name");
        this.header.addField(new MimeField(name, value));
        return this;
    }

    public MultipartPartBuilder setHeader(final String name, final String value) {
        Args.notNull(name, "Header name");
        this.header.setField(new MimeField(name, value));
        return this;
    }

    public MultipartPartBuilder removeHeaders(final String name) {
        Args.notNull(name, "Header name");
        this.header.removeFields(name);
        return this;
    }

    public MultipartPart build() {
        Asserts.notNull(this.body, "Content body");
        final Header headerCopy = new Header();
        final List<MimeField> fields = this.header.getFields();
        for (final MimeField field: fields) {
            headerCopy.addField(field);
        }
        if (headerCopy.getField(MimeConsts.CONTENT_TYPE) == null) {
            final ContentType contentType;
            if (body instanceof AbstractContentBody) {
                contentType = ((AbstractContentBody) body).getContentType();
            } else {
                contentType = null;
            }
            if (contentType != null) {
                headerCopy.addField(new MimeField(MimeConsts.CONTENT_TYPE, contentType.toString()));
            } else {
                final StringBuilder buffer = new StringBuilder();
                buffer.append(this.body.getMimeType()); // MimeType cannot be null
                if (this.body.getCharset() != null) { // charset may legitimately be null
                    buffer.append("; charset=");
                    buffer.append(this.body.getCharset());
                }
                headerCopy.addField(new MimeField(MimeConsts.CONTENT_TYPE, buffer.toString()));
            }
        }
        return new MultipartPart(this.body, headerCopy);
    }
}
