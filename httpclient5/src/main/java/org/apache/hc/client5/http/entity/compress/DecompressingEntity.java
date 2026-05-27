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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.io.IOFunction;
import org.apache.hc.core5.util.Args;

public class DecompressingEntity extends HttpEntityWrapper {

    private static final int BUF_SIZE = 8 * 1024;   // 8 KiB buffer
    private final IOFunction<InputStream, InputStream> decoder;
    private volatile InputStream cached;

    public DecompressingEntity(
            final HttpEntity src,
            final IOFunction<InputStream, InputStream> decoder) {
        super(src);
        this.decoder = Args.notNull(decoder, "Stream decoder");
    }

    private InputStream getDecompressingStream() throws IOException {
        return new LazyDecompressingInputStream(super.getContent(), decoder);
    }

    /**
     * Returns the cached <em>decoded</em> stream, creating it once if necessary.
     */
    @Override
    public InputStream getContent() throws IOException {
        if (!isStreaming()) {
            return getDecompressingStream();
        }

        InputStream local = cached;
        if (local == null) {
            if (cached == null) {
                cached = getDecompressingStream();
            }
            local = cached;
        }
        return local;
    }

    /**
     * Length is unknown after decompression.
     */
    @Override
    public long getContentLength() {
        return -1;
    }

    /**
     * Content is no longer encoded
     */
    @Override
    public String getContentEncoding() {
        return null;
    }

    /**
     * Streams the decoded bytes directly to {@code out}.
     */
    @Override
    public void writeTo(final OutputStream out) throws IOException {
        Args.notNull(out, "Output stream");
        try (InputStream in = getContent()) {
            final byte[] buf = new byte[BUF_SIZE];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
    }

    private static final class LazyDecompressingInputStream extends FilterInputStream {

        private final IOFunction<InputStream, InputStream> decoder;
        private InputStream decompressedStream;

        private LazyDecompressingInputStream(
                final InputStream inputStream,
                final IOFunction<InputStream, InputStream> decoder) {
            super(inputStream);
            this.decoder = decoder;
        }

        private InputStream getDecompressedStream() throws IOException {
            if (decompressedStream == null) {
                decompressedStream = decoder.apply(in);
            }
            return decompressedStream;
        }

        @Override
        public int read() throws IOException {
            return getDecompressedStream().read();
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return getDecompressedStream().read(b);
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            return getDecompressedStream().read(b, off, len);
        }

        @Override
        public long skip(final long n) throws IOException {
            return getDecompressedStream().skip(n);
        }

        @Override
        public int available() throws IOException {
            return getDecompressedStream().available();
        }

        @Override
        public void close() throws IOException {
            final InputStream local = decompressedStream;
            if (local != null) {
                local.close();
            } else {
                super.close();
            }
        }

    }

}