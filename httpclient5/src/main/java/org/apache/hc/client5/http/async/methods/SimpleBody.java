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

package org.apache.hc.client5.http.async.methods;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 * Message body representation as a simple text string or an array of bytes.
 *
 * @since 5.0
 */
public final class SimpleBody {

    private final byte[] bodyAsBytes;
    private final String bodyAsText;
    private final ContentType contentType;

    SimpleBody(final byte[] bodyAsBytes, final String bodyAsText, final ContentType contentType) {
        this.bodyAsBytes = bodyAsBytes;
        this.bodyAsText = bodyAsText;
        this.contentType = contentType;
    }

    static SimpleBody create(final String body, final ContentType contentType) {
        Args.notNull(body, "Body");
        if (body.length() > 2048) {
            return new SimpleBody(null, body, contentType);
        }
        final Charset charset = (contentType != null ? contentType : ContentType.DEFAULT_TEXT).getCharset();
        final byte[] bytes = body.getBytes(charset != null ? charset : StandardCharsets.US_ASCII);
        return new SimpleBody(bytes, null, contentType);
    }

    static SimpleBody create(final byte[] body, final ContentType contentType) {
        Args.notNull(body, "Body");
        return new SimpleBody(body, null, contentType);
    }

    public ContentType getContentType() {
        return contentType;
    }

    public byte[] getBodyBytes() {
        if (bodyAsBytes != null) {
            return bodyAsBytes;
        } else if (bodyAsText != null) {
            final Charset charset = (contentType != null ? contentType : ContentType.DEFAULT_TEXT).getCharset();
            return bodyAsText.getBytes(charset != null ? charset : StandardCharsets.US_ASCII);
        } else {
            return null;
        }
    }

    public String getBodyText() {
        if (bodyAsBytes != null) {
            final Charset charset = (contentType != null ? contentType : ContentType.DEFAULT_TEXT).getCharset();
            return new String(bodyAsBytes, charset != null ? charset : StandardCharsets.US_ASCII);
        } else if (bodyAsText != null) {
            return bodyAsText;
        } else {
            return null;
        }
    }

    public boolean isText() {
        return bodyAsText != null;
    }

    public boolean isBytes() {
        return bodyAsBytes != null;
    }

    @Override
    public String toString() {
        return "content length=" + (bodyAsBytes != null ? bodyAsBytes.length : "chunked") +
                ", content type=" + contentType;
    }

}

