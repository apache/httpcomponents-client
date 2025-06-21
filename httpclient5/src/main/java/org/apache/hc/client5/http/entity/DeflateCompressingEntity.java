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

package org.apache.hc.client5.http.entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.util.Args;

/**
 * Entity wrapper that compresses the wrapped entity with
 * <code>Content-Encoding: deflate</code> on write-out.
 *
 * @since 5.6
 */
public final class DeflateCompressingEntity extends HttpEntityWrapper {

    private static final String DEFLATE_CODEC = "deflate";

    public DeflateCompressingEntity(final HttpEntity entity) {
        super(entity);
    }

    @Override
    public String getContentEncoding() {
        return DEFLATE_CODEC;
    }

    @Override
    public long getContentLength() {
        return -1;                // length unknown after compression
    }

    @Override
    public boolean isChunked() {
        return true;              // force chunked transfer-encoding
    }

    @Override
    public InputStream getContent() throws IOException {
        throw new UnsupportedOperationException("getContent() not supported");
    }

    @Override
    public void writeTo(final OutputStream out) throws IOException {
        Args.notNull(out, "Output stream");
        // ‘false’ second arg = include zlib wrapper (= RFC 1950 = HTTP “deflate”)
        try (DeflaterOutputStream deflater =
                     new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION, /*nowrap*/ false))) {
            super.writeTo(deflater);
        }
    }
}