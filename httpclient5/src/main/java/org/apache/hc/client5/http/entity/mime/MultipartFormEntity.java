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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentTooLongException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;

class MultipartFormEntity implements HttpEntity {

    private final AbstractMultipartFormat multipart;
    private final ContentType contentType;
    private final long contentLength;

    MultipartFormEntity(
            final AbstractMultipartFormat multipart,
            final ContentType contentType,
            final long contentLength) {
        super();
        this.multipart = multipart;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    AbstractMultipartFormat getMultipart() {
        return this.multipart;
    }

    @Override
    public boolean isRepeatable() {
        return this.contentLength != -1;
    }

    @Override
    public boolean isChunked() {
        return !isRepeatable();
    }

    @Override
    public boolean isStreaming() {
        return !isRepeatable();
    }

    @Override
    public long getContentLength() {
        return this.contentLength;
    }

    @Override
    public String getContentType() {
        return this.contentType != null ? this.contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public InputStream getContent() throws IOException {
        if (this.contentLength < 0) {
            throw new ContentTooLongException("Content length is unknown");
        } else if (this.contentLength > 25 * 1024) {
            throw new ContentTooLongException("Content length is too long: " + this.contentLength);
        }
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        writeTo(outStream);
        outStream.flush();
        return new ByteArrayInputStream(outStream.toByteArray());
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        this.multipart.writeTo(outStream);
    }

    @Override
    public Supplier<List<? extends Header>> getTrailers() {
        return null;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

    @Override
    public void close() throws IOException {
    }

}
