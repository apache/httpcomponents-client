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
package org.apache.hc.client5.http.fluent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * HTTP response used by the fluent facade.
 *
 * @since 4.2
 */
public class Response {

    private final ClassicHttpResponse response;
    private boolean consumed;

    Response(final ClassicHttpResponse response) {
        super();
        this.response = response;
    }

    private void assertNotConsumed() {
        if (this.consumed) {
            throw new IllegalStateException("Response content has been already consumed");
        }
    }

    private void dispose() {
        if (this.consumed) {
            return;
        }
        try {
            final HttpEntity entity = this.response.getEntity();
            final InputStream content = entity.getContent();
            if (content != null) {
                content.close();
            }
        } catch (final Exception ignore) {
        } finally {
            this.consumed = true;
        }
    }

    /**
     * Discards response content and deallocates all resources associated with it.
     */
    public void discardContent() {
        dispose();
    }

    /**
     * Handles the response using the specified {@link HttpClientResponseHandler}
     */
    public <T> T handleResponse(final HttpClientResponseHandler<T> handler) throws IOException {
        assertNotConsumed();
        try {
            return handler.handleResponse(this.response);
        } catch (final HttpException ex) {
            throw new ClientProtocolException(ex);
        } finally {
            dispose();
        }
    }

    public Content returnContent() throws IOException {
        return handleResponse(new ContentResponseHandler());
    }

    public HttpResponse returnResponse() throws IOException {
        assertNotConsumed();
        try {
            final HttpEntity entity = this.response.getEntity();
            if (entity != null) {
                final ByteArrayEntity byteArrayEntity = new ByteArrayEntity(
                        EntityUtils.toByteArray(entity), ContentType.parse(entity.getContentType()));
                this.response.setEntity(byteArrayEntity);
            }
            return this.response;
        } finally {
            this.consumed = true;
        }
    }

    public void saveContent(final File file) throws IOException {
        assertNotConsumed();
        final int status = response.getCode();
        if (status >= HttpStatus.SC_REDIRECTION) {
            throw new HttpResponseException(status, response.getReasonPhrase());
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            final HttpEntity entity = this.response.getEntity();
            if (entity != null) {
                entity.writeTo(out);
            }
        } finally {
            this.consumed = true;

        }
    }

}
