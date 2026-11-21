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
import java.nio.ByteBuffer;

import com.aayushatharva.brotli4j.decoder.DecoderJNI;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.core5.util.Args;

/**
 * Input stream decoder for Dictionary-Compressed Brotli ({@code dcb}).
 */
final class DictionaryBrotliInputStream extends InputStream {

    private static final byte[] MAGIC = {
            (byte) 0xff, 0x44, 0x43, 0x42
    };
    private static final int HASH_LENGTH = 32;
    private static final int HEADER_LENGTH = MAGIC.length + HASH_LENGTH;
    private static final int INPUT_BUFFER_SIZE = 8 * 1024;

    private final InputStream source;
    private final DecoderJNI.Wrapper decoder;
    // Keep the direct dictionary buffer strongly reachable for the lifetime of the native decoder.
    private final ByteBuffer dictionaryBuffer;
    private final byte[] inputBuffer;
    private final byte[] singleByte;

    private ByteBuffer decoded;
    private boolean inputEnded;
    private boolean closed;

    DictionaryBrotliInputStream(
            final InputStream source,
            final CompressionDictionary dictionary) throws IOException {
        this.source = Args.notNull(source, "Source input stream");
        final CompressionDictionary compressionDictionary =
                Args.notNull(dictionary, "Compression dictionary");
        validateHeader(this.source, compressionDictionary);
        this.decoder = new DecoderJNI.Wrapper(INPUT_BUFFER_SIZE);
        final byte[] content = compressionDictionary.getContent();
        this.dictionaryBuffer = ByteBuffer.allocateDirect(content.length);
        this.dictionaryBuffer.put(content).flip();
        if (!decoder.attachDictionary(dictionaryBuffer)) {
            decoder.destroy();
            throw new IOException("Unable to attach Brotli dictionary");
        }
        this.inputBuffer = new byte[INPUT_BUFFER_SIZE];
        this.singleByte = new byte[1];
    }

    private static void validateHeader(
            final InputStream source,
            final CompressionDictionary dictionary) throws IOException {
        final byte[] header = new byte[HEADER_LENGTH];
        readFully(source, header);
        for (int i = 0; i < MAGIC.length; i++) {
            if (header[i] != MAGIC[i]) {
                throw new IOException("Invalid DCB stream header");
            }
        }
        final byte[] hash = new byte[HASH_LENGTH];
        System.arraycopy(header, MAGIC.length, hash, 0, HASH_LENGTH);
        if (!dictionary.matchesHash(hash)) {
            throw new IOException("DCB stream does not use the negotiated dictionary");
        }
    }

    private static void readFully(final InputStream source, final byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            final int count = source.read(buffer, offset, buffer.length - offset);
            if (count == -1) {
                throw new IOException("Truncated DCB stream header");
            }
            if (count == 0) {
                continue;
            }
            offset += count;
        }
    }

    @Override
    public int read() throws IOException {
        final int count = read(singleByte, 0, 1);
        return count == -1 ? -1 : singleByte[0] & 0xff;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (b == null) {
            throw new NullPointerException("Buffer");
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        ensureOpen();

        while (decoded == null || !decoded.hasRemaining()) {
            decoded = null;
            if (!pump()) {
                return -1;
            }
        }

        final int count = Math.min(len, decoded.remaining());
        decoded.get(b, off, count);
        return count;
    }

    private boolean pump() throws IOException {
        try {
            for (;;) {
                switch (decoder.getStatus()) {
                    case DONE:
                        if (decoder.hasOutput()) {
                            final ByteBuffer output = decoder.pull();
                            if (output != null && output.hasRemaining()) {
                                decoded = output;
                                return true;
                            }
                        }
                        return false;
                    case NEEDS_MORE_OUTPUT: {
                        final ByteBuffer output = decoder.pull();
                        if (output != null && output.hasRemaining()) {
                            decoded = output;
                            return true;
                        }
                        break;
                    }
                    case OK:
                        decoder.push(0);
                        break;
                    case NEEDS_MORE_INPUT:
                        if (decoder.hasOutput()) {
                            final ByteBuffer output = decoder.pull();
                            if (output != null && output.hasRemaining()) {
                                decoded = output;
                                return true;
                            }
                        }
                        if (inputEnded) {
                            decoder.push(0);
                            if (decoder.getStatus() == DecoderJNI.Status.NEEDS_MORE_INPUT) {
                                throw new IOException("Truncated DCB stream");
                            }
                            break;
                        }
                        final ByteBuffer input = decoder.getInputBuffer();
                        input.clear();
                        final int count = source.read(
                                inputBuffer, 0, Math.min(input.remaining(), inputBuffer.length));
                        if (count == -1) {
                            inputEnded = true;
                            decoder.push(0);
                        } else if (count > 0) {
                            input.put(inputBuffer, 0, count);
                            decoder.push(count);
                        }
                        break;
                    default:
                        throw new IOException("DCB stream corrupted");
                }
            }
        } catch (final IOException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new IOException("DCB stream corrupted", ex);
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                decoder.destroy();
            } finally {
                source.close();
            }
        }
    }
}
