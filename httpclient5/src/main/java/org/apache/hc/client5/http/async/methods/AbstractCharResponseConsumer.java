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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.entity.AbstractCharDataConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Abstract response consumer that processes response body data as a character stream.
 *
 * @since 5.0
 *
 * @param <T> response message representation.
 */
public abstract class AbstractCharResponseConsumer<T> extends AbstractCharDataConsumer implements AsyncResponseConsumer<T> {

    private volatile FutureCallback<T> resultCallback;
    private final Charset defaultCharset;

    public AbstractCharResponseConsumer() {
        this.defaultCharset = StandardCharsets.UTF_8;
    }

    protected AbstractCharResponseConsumer(final int bufSize,
                                           final CharCodingConfig charCodingConfig) {
        super(bufSize, charCodingConfig);
        this.defaultCharset = charCodingConfig != null && charCodingConfig.getCharset() != null
                ? charCodingConfig.getCharset() : StandardCharsets.UTF_8;
    }

    /**
     * Triggered to signal the beginning of data processing.
     *
     * @param response the response message head
     * @param contentType the content type of the response body,
     *                    or {@code null} if the response does not enclose a response entity.
     */
    protected abstract void start(HttpResponse response, ContentType contentType) throws HttpException, IOException;

    /**
     * Triggered to generate object that represents a result of response message processing.
     *
     * @return the result of response processing.
     */
    protected abstract T buildResult() throws IOException;

    @Override
    public void informationResponse(
            final HttpResponse response,
            final HttpContext context) throws HttpException, IOException {
    }

    @Override
    public final void consumeResponse(
            final HttpResponse response,
            final EntityDetails entityDetails,
            final HttpContext context,
            final FutureCallback<T> resultCallback) throws HttpException, IOException {
        this.resultCallback = resultCallback;
        if (entityDetails != null) {
            final ContentType contentType;
            try {
                contentType = ContentType.parse(entityDetails.getContentType());
            } catch (final UnsupportedCharsetException ex) {
                throw new UnsupportedEncodingException(ex.getMessage());
            }
            Charset charset = contentType != null ? contentType.getCharset() : null;
            if (charset == null) {
                charset = defaultCharset;
            }
            setCharset(charset);
            start(response, contentType != null ? contentType : ContentType.DEFAULT_TEXT);
        } else {
            start(response, null);
            completed();
        }
    }

    @Override
    protected final void completed() throws IOException {
        resultCallback.completed(buildResult());
    }

    @Override
    public void failed(final Exception cause) {
    }

}