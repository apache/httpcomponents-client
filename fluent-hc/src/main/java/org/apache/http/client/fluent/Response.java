/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.fluent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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

    public void dispose() {
        if (this.consumed) {
            return;
        }
        try {
            EntityUtils.consume(this.response.getEntity());
        } catch (Exception ignore) {
        } finally {
            this.consumed = true;
        }
    }

    public <T> T handle(final ResponseHandler<T> handler) throws ClientProtocolException, IOException {
        assertNotConsumed();
        try {
            return handler.handleResponse(this.response);
        } finally {
            dispose();
        }
    }

    public Content content() throws ClientProtocolException, IOException {
        return handle(new ResponseHandler<Content>() {

            public Content handleResponse(
                    final HttpResponse response) throws ClientProtocolException, IOException {
                StatusLine statusLine = response.getStatusLine();
                HttpEntity entity = response.getEntity();
                if (statusLine.getStatusCode() >= 300) {
                    throw new HttpResponseException(statusLine.getStatusCode(),
                            statusLine.getReasonPhrase());
                }
                if (entity != null) {
                    return new Content(EntityUtils.toByteArray(entity),
                            ContentType.getOrDefault(entity));
                } else {
                    return null;
                }
            }

        });
    }

    public HttpResponse response() throws IOException {
        assertNotConsumed();
        try {
            HttpEntity entity = this.response.getEntity();
            if (entity != null) {
                this.response.setEntity(new ByteArrayEntity(EntityUtils.toByteArray(entity),
                                ContentType.getOrDefault(entity)));
            }
            return this.response;
        } finally {
            this.consumed = true;
        }
    }

    public void save(final File file) throws IOException {
        assertNotConsumed();
        FileOutputStream out = new FileOutputStream(file);
        try {
            HttpEntity entity = this.response.getEntity();
            if (entity != null) {
                entity.writeTo(out);
            }
        } finally {
            this.consumed = true;
            out.close();
        }
    }

}
