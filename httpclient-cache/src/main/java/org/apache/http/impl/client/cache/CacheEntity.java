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
package org.apache.http.impl.client.cache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.Immutable;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;

@Immutable
class CacheEntity implements HttpEntity, Cloneable, Serializable {

    private static final long serialVersionUID = -3467082284120936233L;

    private final byte[] content;
    private final String contentType;
    private final String contentEncoding;

    public CacheEntity(final byte[] b, final HttpResponse response) {
        super();
        this.content = b;

        Header ct = response.getFirstHeader(HTTP.CONTENT_TYPE);
        Header ce = response.getFirstHeader(HTTP.CONTENT_ENCODING);

        this.contentType  = ct != null ? ct.getValue() : null;
        this.contentEncoding = ce != null ? ce.getValue() : null;
    }

    public Header getContentType() {
        if (this.contentType == null) {
            return null;
        }

        return new BasicHeader(HTTP.CONTENT_TYPE, this.contentType);
    }

    public Header getContentEncoding() {
        if (this.contentEncoding == null) {
            return null;
        }

        return new BasicHeader(HTTP.CONTENT_ENCODING, this.contentEncoding);
    }

    public boolean isChunked() {
        return false;
    }

    public boolean isRepeatable() {
        return true;
    }

    public long getContentLength() {
        return this.content.length;
    }

    public InputStream getContent() {
        return new ByteArrayInputStream(this.content);
    }

    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        outstream.write(this.content);
        outstream.flush();
    }

    public boolean isStreaming() {
        return false;
    }

    public void consumeContent() throws IOException {
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
