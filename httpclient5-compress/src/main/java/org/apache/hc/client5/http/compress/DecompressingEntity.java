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
 * Wrapping entity that decompresses content when {@link #getContent()} is called.
 *
 * @since 5.6
 */
public class DecompressingEntity extends HttpEntityWrapper {

    private final String contentEncoding;
    private final boolean noWrap;

    /**
     * Creates a new DecompressingEntity.
     *
     * @param entity          the original entity
     * @param contentEncoding the content encoding to use for decompression
     * @param noWrap          whether to exclude zlib headers and trailers for deflate streams
     * @since 5.6
     */
    public DecompressingEntity(final HttpEntity entity, final String contentEncoding, final boolean noWrap) {
        super(entity);
        this.contentEncoding = contentEncoding;
        this.noWrap = noWrap;
    }

    @Override
    public InputStream getContent() throws IOException {
        return CompressingFactory.INSTANCE.getDecompressorInputStream(contentEncoding, super.getContent(), noWrap);
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        try (final InputStream inStream = getContent()) {
            final byte[] buffer = new byte[4096];
            int l;
            while ((l = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, l);
            }
        }
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public boolean isChunked() {
        return true;
    }
}