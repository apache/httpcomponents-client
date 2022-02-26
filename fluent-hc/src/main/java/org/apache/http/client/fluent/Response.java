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
package org.apache.http.client.fluent;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

public class Response {

    private final HttpResponse response;
    private boolean consumed;

    Response(final HttpResponse response) {
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
     * Handles the response using the specified {@link ResponseHandler}
     */
    public <T> T handleResponse(
            final ResponseHandler<T> handler) throws ClientProtocolException, IOException {
        assertNotConsumed();
        try {
            return handler.handleResponse(this.response);
        } finally {
            dispose();
        }
    }

    public Content returnContent() throws ClientProtocolException, IOException {
        return handleResponse(new ContentResponseHandler());
    }

    public HttpResponse returnResponse() throws IOException {
        assertNotConsumed();
        try {
            final HttpEntity entity = this.response.getEntity();
            if (entity != null) {
                final ByteArrayEntity byteArrayEntity = new ByteArrayEntity(
                        EntityUtils.toByteArray(entity));
                final ContentType contentType = ContentType.getOrDefault(entity);
                byteArrayEntity.setContentType(contentType.toString());
                this.response.setEntity(byteArrayEntity);
            }
            return this.response;
        } finally {
            this.consumed = true;
        }
    }

    public void saveContent(final File file) throws IOException {
        assertNotConsumed();
        try {
            final StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() >= 300) {
                throw new HttpResponseException(statusLine.getStatusCode(),
                        statusLine.getReasonPhrase());
            }
            final FileOutputStream out = new FileOutputStream(file);
            try {
                final HttpEntity entity = this.response.getEntity();
                if (entity != null) {
                    entity.writeTo(out);
                }
            } finally {
                out.close();
            }
        } finally {
            this.consumed = true;
            if (this.response instanceof Closeable) {
                ((Closeable) this.response).close();
            }
        }
    }

}
