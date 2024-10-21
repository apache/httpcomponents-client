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
package org.apache.hc.client5.http.entity.compress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.util.Args;

/**
 * An {@link HttpEntity} wrapper that applies compression to the content before writing it to
 * an output stream. This class supports various compression algorithms based on the
 * specified content encoding.
 *
 * <p>Compression is performed using {@link CompressingFactory}, which returns a corresponding
 * {@link OutputStream} for the requested compression type. This class does not support
 * reading the content directly through {@link #getContent()} as the content is always compressed
 * during write operations.</p>
 *
 * @since 5.5
 */
public class CompressingEntity extends HttpEntityWrapper {

    /**
     * The content encoding type, e.g., "gzip", "deflate", etc.
     */
    private final String contentEncoding;

    /**
     * Creates a new {@link CompressingEntity} that compresses the wrapped entity's content
     * using the specified content encoding.
     *
     * @param entity          the {@link HttpEntity} to wrap and compress; must not be {@code null}.
     * @param contentEncoding the content encoding to use for compression, e.g., "gzip".
     */
    public CompressingEntity(final HttpEntity entity, final String contentEncoding) {
        super(entity);
        this.contentEncoding = Args.notNull(contentEncoding, "Content encoding");
    }

    /**
     * Returns the content encoding used for compression.
     *
     * @return the content encoding (e.g., "gzip", "deflate").
     */
    @Override
    public String getContentEncoding() {
        return contentEncoding;
    }

    /**
     * Returns whether the entity is chunked. This is determined by the wrapped entity.
     *
     * @return {@code true} if the entity is chunked, {@code false} otherwise.
     */
    @Override
    public boolean isChunked() {
        return super.isChunked();
    }

    /**
     * This method is unsupported because the content is meant to be compressed during the
     * {@link #writeTo(OutputStream)} operation.
     *
     * @throws UnsupportedOperationException always, as this method is not supported.
     */
    @Override
    public InputStream getContent() throws IOException {
        throw new UnsupportedOperationException("Reading content is not supported for CompressingEntity");
    }

    /**
     * Writes the compressed content to the provided {@link OutputStream}. Compression is performed
     * using the content encoding provided during entity construction.
     *
     * @param outStream the {@link OutputStream} to which the compressed content will be written; must not be {@code null}.
     * @throws IOException                   if an I/O error occurs during compression or writing.
     * @throws UnsupportedOperationException if the specified compression type is not supported.
     */
    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        // Get the compressor based on the specified content encoding
        final OutputStream compressorStream;
        try {
            compressorStream = CompressingFactory.INSTANCE.getCompressorOutputStream(contentEncoding, outStream);
        } catch (final CompressorException e) {
            throw new IOException("Error initializing decompression stream", e);
        }
        if (compressorStream != null) {
            // Write compressed data
            super.writeTo(compressorStream);
            // Close the compressor stream after writing
            compressorStream.close();
        } else {
            throw new UnsupportedOperationException("Unsupported compression: " + contentEncoding);
        }
    }
}
