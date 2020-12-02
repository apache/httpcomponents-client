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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;

@Contract(threading = ThreadingBehavior.IMMUTABLE)
class CacheEntity implements HttpEntity, Serializable {

    private static final long serialVersionUID = -3467082284120936233L;

    private final HttpCacheEntry cacheEntry;

    public CacheEntity(final HttpCacheEntry cacheEntry) {
        super();
        this.cacheEntry = cacheEntry;
    }

    @Override
    public Header getContentType() {
        return this.cacheEntry.getFirstHeader(HTTP.CONTENT_TYPE);
    }

    @Override
    public Header getContentEncoding() {
        return this.cacheEntry.getFirstHeader(HTTP.CONTENT_ENCODING);
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public long getContentLength() {
        return this.cacheEntry.getResource().length();
    }

    @Override
    public InputStream getContent() throws IOException {
        return this.cacheEntry.getResource().getInputStream();
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        final InputStream inStream = this.cacheEntry.getResource().getInputStream();
        try {
            IOUtils.copy(inStream, outStream);
        } finally {
            inStream.close();
        }
    }

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public void consumeContent() throws IOException {
        // consume nothing
    }

}
