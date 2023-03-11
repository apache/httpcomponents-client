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
package org.apache.hc.client5.http;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.TextUtils;

/**
 * Signals a non 2xx HTTP response.
 *
 * @since 4.0
 */
public class HttpResponseException extends ClientProtocolException {

    private static final long serialVersionUID = -7186627969477257933L;

    private final int statusCode;
    private final String reasonPhrase;
    private final byte[] contentBytes;
    private final ContentType contentType;

    /**
     * Constructs a new instance of {@code HttpResponseException} with the given
     * status code and reason phrase, and no content bytes or content type.
     *
     * @param statusCode   the HTTP status code
     * @param reasonPhrase the reason phrase associated with the HTTP status code
     */
    public HttpResponseException(final int statusCode, final String reasonPhrase) {
        this(statusCode, reasonPhrase, null, null);
    }

    /**
     * Constructs a new instance of {@code HttpResponseException} with the given
     * status code, reason phrase, content bytes, and content type.
     *
     * @param statusCode   the HTTP status code
     * @param reasonPhrase the reason phrase associated with the HTTP status code
     * @param contentBytes the content bytes of the HTTP response
     * @param contentType  the content type of the HTTP response
     */
    public HttpResponseException(final int statusCode, final String reasonPhrase, final byte[] contentBytes, final ContentType contentType) {
        super(String.format("status code: %d" +
                        (TextUtils.isBlank(reasonPhrase) ? "" : ", reason phrase: %s") +
                        (contentBytes == null ? "" : ", content: %s"),
                statusCode, reasonPhrase,
                contentBytes == null || contentType == null || contentType.getCharset() == null ?
                        null :
                        new String(contentBytes, contentType.getCharset())));

        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.contentBytes = contentBytes;
        this.contentType = contentType;
    }


    public int getStatusCode() {
        return this.statusCode;
    }

    public String getReasonPhrase() {
        return this.reasonPhrase;
    }

    public byte[] getContentBytes() {
        return contentBytes;
    }

    public ContentType getContentType() {
        return contentType;
    }
}
