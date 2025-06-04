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

package org.apache.hc.client5.http.compress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;

/**
 * Wrapping entity that compresses content when {@link #writeTo(OutputStream)} is called.
 *
 * @since 5.6
 */
public class CompressingEntity extends HttpEntityWrapper {

    private final String contentEncoding;

    /**
     * Creates a new CompressingEntity.
     *
     * @param entity          the original entity
     * @param contentEncoding the content encoding to use for compression
     */
    public CompressingEntity(final HttpEntity entity, final String contentEncoding) {
        super(entity);
        this.contentEncoding = contentEncoding;
    }

    @Override
    public String getContentEncoding() {
        return contentEncoding;
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public InputStream getContent() throws IOException {
        throw new UnsupportedOperationException("Compressed entities do not support getContent()");
    }

    @Override
    public boolean isChunked() {
        return true;
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        try (final OutputStream compressedStream = CompressingFactory.INSTANCE.getCompressorOutputStream(contentEncoding, outStream)) {
            super.writeTo(compressedStream);
        }
    }
}