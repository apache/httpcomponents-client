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
import java.nio.charset.UnsupportedCharsetException;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.entity.AbstractBinDataConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Abstract push response consumer that processes response body data as an octet stream.
 *
 * @since 5.0
 */
public abstract class AbstractBinPushConsumer extends AbstractBinDataConsumer implements AsyncPushConsumer {

    /**
     * Triggered to signal the beginning of response processing.
     *
     * @param response the response message head
     * @param contentType the content type of the response body,
     *                    or {@code null} if the response does not enclose a response entity.
     */
    protected abstract void start(HttpRequest promise, HttpResponse response, ContentType contentType) throws HttpException, IOException;

    @Override
    public final void consumePromise(
            final HttpRequest promise,
            final HttpResponse response,
            final EntityDetails entityDetails,
            final HttpContext context) throws HttpException, IOException {
        if (entityDetails != null) {
            final ContentType contentType;
            try {
                contentType = ContentType.parse(entityDetails.getContentType());
            } catch (final UnsupportedCharsetException ex) {
                throw new UnsupportedEncodingException(ex.getMessage());
            }
            start(promise, response, contentType != null ? contentType : ContentType.DEFAULT_BINARY);
        } else {
            start(promise, response, null);
            completed();
        }
    }

    @Override
    public void failed(final Exception cause) {
    }

}