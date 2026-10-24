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
package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;
import java.io.InputStream;

import com.github.luben.zstd.ZstdInputStream;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.core5.util.Args;

/**
 * Input stream decoder for Dictionary-Compressed Zstandard ({@code dcz}).
 */
final class DictionaryZstdInputStream extends InputStream {

    private static final byte[] MAGIC = {
            0x5e, 0x2a, 0x4d, 0x18, 0x20, 0x00, 0x00, 0x00
    };
    private static final int HASH_LENGTH = 32;
    private static final int HEADER_LENGTH = MAGIC.length + HASH_LENGTH;

    private final ZstdInputStream decoder;

    DictionaryZstdInputStream(
            final InputStream source,
            final CompressionDictionary dictionary) throws IOException {
        final InputStream input = Args.notNull(source, "Source input stream");
        final CompressionDictionary compressionDictionary =
                Args.notNull(dictionary, "Compression dictionary");
        validateHeader(input, compressionDictionary);
        this.decoder = new ZstdInputStream(input);
        this.decoder.setDict(compressionDictionary.getContent());
    }

    private static void validateHeader(
            final InputStream source,
            final CompressionDictionary dictionary) throws IOException {
        final byte[] header = new byte[HEADER_LENGTH];
        int offset = 0;
        while (offset < header.length) {
            final int count = source.read(header, offset, header.length - offset);
            if (count == -1) {
                throw new IOException("Truncated DCZ stream header");
            }
            if (count == 0) {
                continue;
            }
            offset += count;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (header[i] != MAGIC[i]) {
                throw new IOException("Invalid DCZ stream header");
            }
        }
        final byte[] hash = new byte[HASH_LENGTH];
        System.arraycopy(header, MAGIC.length, hash, 0, HASH_LENGTH);
        if (!dictionary.matchesHash(hash)) {
            throw new IOException("DCZ stream does not use the negotiated dictionary");
        }
    }

    @Override
    public int read() throws IOException {
        return decoder.read();
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return decoder.read(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return decoder.skip(n);
    }

    @Override
    public int available() throws IOException {
        return decoder.available();
    }

    @Override
    public void close() throws IOException {
        decoder.close();
    }
}
