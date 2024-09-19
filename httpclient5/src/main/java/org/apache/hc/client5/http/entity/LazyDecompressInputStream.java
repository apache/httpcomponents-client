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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.hc.core5.io.Closer;


/**
 * A {@link FilterInputStream} that lazily initializes and applies decompression on the underlying input stream.
 * This class supports multiple compression types and uses {@link CompressorFactory} to obtain the appropriate
 * decompression stream when the first read operation occurs.
 *
 * <p>This implementation delays the creation of the decompression stream until it is required, optimizing
 * the performance when the stream may not be read immediately or at all.</p>
 *
 * @since 5.5
 */
public class LazyDecompressInputStream extends FilterInputStream {

    /**
     * The lazily initialized decompression stream.
     */
    private InputStream wrapperStream;

    /**
     * The compression type used to determine which decompression algorithm to apply (e.g., "gzip", "deflate").
     */
    private final String compressionType;

    /**
     * The flag indicating if decompression should skip certain headers (noWrap).
     */
    private final boolean noWrap;

    /**
     * Constructs a new {@link LazyDecompressInputStream} that applies the specified compression type and noWrap setting.
     *
     * @param wrappedStream   the non-null {@link InputStream} to be wrapped and decompressed.
     * @param compressionType the compression type (e.g., "gzip", "deflate").
     * @param noWrap          whether to decompress without headers for certain compression formats.
     */
    public LazyDecompressInputStream(final InputStream wrappedStream, final String compressionType, final boolean noWrap) {
        super(wrappedStream);
        this.compressionType = compressionType;
        this.noWrap = noWrap;
    }

    /**
     * Constructs a new {@link LazyDecompressInputStream} that applies the specified compression type,
     * defaulting to no noWrap handling.
     *
     * @param wrappedStream   the non-null {@link InputStream} to be wrapped and decompressed.
     * @param compressionType the compression type (e.g., "gzip", "deflate").
     */
    public LazyDecompressInputStream(final InputStream wrappedStream, final String compressionType) {
        this(wrappedStream, compressionType, false);
    }

    /**
     * Initializes the decompression wrapper stream lazily, based on the compression type and noWrap flag.
     *
     * @return the initialized decompression stream.
     * @throws IOException if an error occurs during initialization.
     */
    private InputStream initWrapper() throws IOException {
        if (wrapperStream == null) {
            try {
                wrapperStream = CompressorFactory.INSTANCE.getCompressorInputStream(compressionType, in, noWrap);
            } catch (final CompressorException e) {
                throw new IOException("Error initializing decompression stream", e);
            }
        }
        return wrapperStream;
    }

    /**
     * Reads a single byte from the decompressed stream.
     *
     * @return the byte read, or -1 if the end of the stream is reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int read() throws IOException {
        return initWrapper().read();
    }

    /**
     * Reads bytes into the specified array from the decompressed stream.
     *
     * @param b the byte array to read into.
     * @return the number of bytes read, or -1 if the end of the stream is reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int read(final byte[] b) throws IOException {
        return initWrapper().read(b);
    }

    /**
     * Reads bytes into the specified array from the decompressed stream, starting at the specified offset and reading up to the specified length.
     *
     * @param b   the byte array to read into.
     * @param off the offset at which to start writing bytes.
     * @param len the maximum number of bytes to read.
     * @return the number of bytes read, or -1 if the end of the stream is reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return initWrapper().read(b, off, len);
    }

    /**
     * Skips over and discards a specified number of bytes from the decompressed stream.
     *
     * @param n the number of bytes to skip.
     * @return the actual number of bytes skipped.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public long skip(final long n) throws IOException {
        return initWrapper().skip(n);
    }

    /**
     * Returns whether this input stream supports the {@code mark} and {@code reset} methods.
     *
     * @return {@code false}, as marking is not supported by this stream.
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * Returns the number of bytes available in the decompressed stream for reading.
     *
     * @return the number of bytes available.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int available() throws IOException {
        return initWrapper().available();
    }

    /**
     * Closes the decompressed stream, releasing any resources associated with it.
     *
     * @throws IOException if an I/O error occurs during closing.
     */
    @Override
    public void close() throws IOException {
        try {
            Closer.close(wrapperStream);  // Ensures wrapperStream is closed properly.
        } finally {
            super.close();
        }
    }

    /**
     * Marks the current position in the decompressed stream.
     *
     * @param readlimit the maximum number of bytes that can be read before the mark position becomes invalid.
     */
    @Override
    public void mark(final int readlimit) {
        try {
            initWrapper().mark(readlimit);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Resets the stream to the most recent mark.
     *
     * @throws IOException if the stream has not been marked or if the mark has become invalid.
     */
    @Override
    public void reset() throws IOException {
        initWrapper().reset();
    }
}

