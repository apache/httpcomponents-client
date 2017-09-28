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

package org.apache.hc.client5.http.cache;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.util.Args;

public final class HttpCacheResponse implements EntityDetails {

    private final int code;
    private final String reasonPhrase;
    private final byte[] body;
    private final ContentType contentType;

    public static HttpCacheResponse create(
            final int code,
            final String reasonPhrase,
            final byte[] body,
            final ContentType contentType) {
        return new HttpCacheResponse(code, reasonPhrase, body, contentType);
    }

    public static HttpCacheResponse create(
            final int code,
            final byte[] body,
            final ContentType contentType) {
        return new HttpCacheResponse(code, null, body, contentType);
    }

    public static HttpCacheResponse create(
            final int code,
            final String reasonPhrase) {
        return new HttpCacheResponse(code, reasonPhrase, null, null);
    }

    public static HttpCacheResponse create(final int code) {
        return new HttpCacheResponse(code, null, null, null);
    }

    public static HttpCacheResponse create(
            final int code,
            final String reasonPhrase,
            final String body,
            final ContentType contentType) {
        if (body != null) {
            final Charset charset = contentType != null ? contentType.getCharset() : null;
            final byte[] b = body.getBytes(charset != null ? charset : StandardCharsets.US_ASCII);
            return new HttpCacheResponse(code, reasonPhrase, b, contentType);
        } else {
            return create(code, reasonPhrase);
        }
    }

    public static HttpCacheResponse create(
            final int code,
            final String body,
            final ContentType contentType) {
        return create(code, null, body, contentType);
    }

    private HttpCacheResponse(
            final int code,
            final String reasonPhrase,
            final byte[] body,
            final ContentType contentType) {
        this.code = Args.checkRange(code, 200, 599, "HTTP status");
        this.reasonPhrase = reasonPhrase;
        this.body = body;
        this.contentType = contentType;
    }

    public int getCode() {
        return code;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public long getContentLength() {
        return body != null ? body.length : 0;
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

}

