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

import java.util.List;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.content.AbstractContentBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * Builder for individual {@link org.apache.http.entity.mime.FormBodyPart}s.
 *
 * @since 4.4
 */
public class FormBodyPartBuilder {

    private String name;
    private ContentBody body;
    private final Header header;

    public static FormBodyPartBuilder create(final String name, final ContentBody body) {
        return new FormBodyPartBuilder(name, body);
    }

    public static FormBodyPartBuilder create() {
        return new FormBodyPartBuilder();
    }

    FormBodyPartBuilder(final String name, final ContentBody body) {
        this();
        this.name = name;
        this.body = body;
    }

    FormBodyPartBuilder() {
        this.header = new Header();
    }

    public FormBodyPartBuilder setName(final String name) {
        this.name = name;
        return this;
    }

    public FormBodyPartBuilder setBody(final ContentBody body) {
        this.body = body;
        return this;
    }

    public FormBodyPartBuilder addField(final String name, final String value) {
        Args.notNull(name, "Field name");
        this.header.addField(new MinimalField(name, value));
        return this;
    }

    public FormBodyPartBuilder setField(final String name, final String value) {
        Args.notNull(name, "Field name");
        this.header.setField(new MinimalField(name, value));
        return this;
    }

    public FormBodyPartBuilder removeFields(final String name) {
        Args.notNull(name, "Field name");
        this.header.removeFields(name);
        return this;
    }

    public FormBodyPart build() {
        Asserts.notBlank(this.name, "Name");
        Asserts.notNull(this.body, "Content body");
        final Header headerCopy = new Header();
        final List<MinimalField> fields = this.header.getFields();
        for (final MinimalField field: fields) {
            headerCopy.addField(field);
        }
        if (headerCopy.getField(MIME.CONTENT_DISPOSITION) == null) {
            final StringBuilder buffer = new StringBuilder();
            buffer.append("form-data; name=\"");
            buffer.append(encodeForHeader(this.name));
            buffer.append("\"");
            if (this.body.getFilename() != null) {
                buffer.append("; filename=\"");
                buffer.append(encodeForHeader(this.body.getFilename()));
                buffer.append("\"");
            }
            headerCopy.addField(new MinimalField(MIME.CONTENT_DISPOSITION, buffer.toString()));
        }
        if (headerCopy.getField(MIME.CONTENT_TYPE) == null) {
            final ContentType contentType;
            if (body instanceof AbstractContentBody) {
                contentType = ((AbstractContentBody) body).getContentType();
            } else {
                contentType = null;
            }
            if (contentType != null) {
                headerCopy.addField(new MinimalField(MIME.CONTENT_TYPE, contentType.toString()));
            } else {
                final StringBuilder buffer = new StringBuilder();
                buffer.append(this.body.getMimeType()); // MimeType cannot be null
                if (this.body.getCharset() != null) { // charset may legitimately be null
                    buffer.append("; charset=");
                    buffer.append(this.body.getCharset());
                }
                headerCopy.addField(new MinimalField(MIME.CONTENT_TYPE, buffer.toString()));
            }
        }
        if (headerCopy.getField(MIME.CONTENT_TRANSFER_ENC) == null) {
            // TE cannot be null
            headerCopy.addField(new MinimalField(MIME.CONTENT_TRANSFER_ENC, body.getTransferEncoding()));
        }
        return new FormBodyPart(this.name, this.body, headerCopy);
    }

    private static String encodeForHeader(final String headerName) {
        if (headerName == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headerName.length(); i++) {
            final char x = headerName.charAt(i);
            if (x == '"' || x == '\\' || x == '\r') {
                sb.append("\\");
            }
            sb.append(x);
        }
        return sb.toString();
    }

}
