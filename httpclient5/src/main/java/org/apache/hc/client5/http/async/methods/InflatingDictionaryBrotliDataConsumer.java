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
package org.apache.hc.client5.http.async.methods;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.aayushatharva.brotli4j.decoder.DecoderJNI;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@link AsyncDataConsumer} that decodes a Dictionary-Compressed Brotli ({@code dcb}) response
 * on the fly and forwards the plain output to a downstream consumer.
 * <p>
 * A {@code dcb} stream begins with a fixed header: the four-byte magic sequence
 * {@code 0xFF 0x44 0x43 0x42} followed by the 32-byte SHA-256 hash of the dictionary the origin
 * used to compress the body. The header is buffered until complete, the hash is looked up in the
 * supplied {@link CompressionDictionaryStore}, and the matching {@link CompressionDictionary} is
 * attached to the Brotli decoder as the shared dictionary before any compressed payload is
 * decoded. If the hash is unknown, or the header does not validate, the stream is rejected. See
 * the Compression Dictionary Transport specification for the dictionary negotiation and framing.
 * <p>
 * This consumer is stateful and not thread-safe; a fresh instance is required per response.
 *
 * @since 5.7
 */
public final class InflatingDictionaryBrotliDataConsumer implements AsyncDataConsumer {

    private static final byte[] MAGIC = {
            (byte) 0xff, 0x44, 0x43, 0x42
    };

    private static final int HASH_LENGTH = 32;
    private static final int HEADER_LENGTH = MAGIC.length + HASH_LENGTH;

    private final AsyncDataConsumer downstream;
    private final CompressionDictionaryStore store;
    private final ByteBuffer header;

    private DecoderJNI.Wrapper decoder;

    /**
     * Creates a consumer that decodes a {@code dcb} stream and forwards the decoded bytes to
     * {@code downstream}.
     *
     * @param downstream the consumer that receives the decompressed content; must not be {@code null}.
     * @param store      the store consulted to resolve the dictionary referenced by the stream header;
     *                   must not be {@code null}.
     * @since 5.7
     */
    public InflatingDictionaryBrotliDataConsumer(
            final AsyncDataConsumer downstream,
            final CompressionDictionaryStore store) {
        this.downstream = Args.notNull(downstream, "Downstream data consumer");
        this.store = Args.notNull(store, "Dictionary store");
        this.header = ByteBuffer.allocate(HEADER_LENGTH);
    }

    /**
     * Propagates capacity signalling to the transport. The downstream consumer's demand is relayed
     * through a wrapper so that back-pressure it exerts on the decoded output is translated into
     * capacity requests on the compressed input.
     *
     * @since 5.7
     */
    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        downstream.updateCapacity(new InflatingCapacityChannel(capacityChannel));
    }

    /**
     * Consumes a chunk of the compressed stream. Until the fixed header has been fully buffered and
     * the dictionary resolved, incoming bytes feed header parsing and no output is produced; once the
     * decoder is initialised the chunk is handed to it and any decoded bytes are pushed downstream.
     * A single call may be a partial header, may straddle the header and payload boundary, or may be
     * pure payload.
     *
     * @param src the next slice of compressed bytes; fully drained on return.
     * @throws IOException if the header is malformed, the dictionary is unavailable, the stream is
     *                     corrupt, or trailing bytes follow a completed stream.
     * @since 5.7
     */
    @Override
    public void consume(final ByteBuffer src) throws IOException {
        if (decoder == null) {
            consumeHeader(src);
            if (decoder == null) {
                return;
            }
        }

        while (src.hasRemaining()) {
            if (decoder.getStatus() == DecoderJNI.Status.DONE) {
                throw new IOException("Unexpected data after DCB stream");
            }

            final ByteBuffer in = decoder.getInputBuffer();
            in.clear();

            final int xfer = Math.min(src.remaining(), in.remaining());
            final int lim = src.limit();
            src.limit(src.position() + xfer);
            in.put(src);
            src.limit(lim);

            decoder.push(xfer);
            pump();
        }
    }

    private void consumeHeader(final ByteBuffer src) throws IOException {
        final int xfer = Math.min(src.remaining(), header.remaining());
        final int lim = src.limit();
        src.limit(src.position() + xfer);
        header.put(src);
        src.limit(lim);

        if (header.hasRemaining()) {
            return;
        }

        header.flip();

        for (final byte expected : MAGIC) {
            if (header.get() != expected) {
                throw new IOException("Invalid DCB stream header");
            }
        }

        final byte[] hash = new byte[HASH_LENGTH];
        header.get(hash);

        final CompressionDictionary dictionary = store.getByHash(hash);
        if (dictionary == null || !dictionary.matchesHash(hash)) {
            throw new IOException("Compression dictionary not available");
        }

        try {
            decoder = new DecoderJNI.Wrapper(8 * 1024);

            final byte[] content = dictionary.getContent();
            final ByteBuffer dictionaryBuffer = ByteBuffer.allocateDirect(content.length);
            dictionaryBuffer.put(content);
            dictionaryBuffer.flip();

            if (!decoder.attachDictionary(dictionaryBuffer)) {
                decoder.destroy();
                decoder = null;
                throw new IOException("Unable to attach Brotli dictionary");
            }
        } catch (final IOException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new IOException("Unable to initialize DCB decoder", ex);
        }
    }

    private void pump() throws IOException {
        for (; ; ) {
            switch (decoder.getStatus()) {
                case OK:
                    decoder.push(0);
                    break;
                case NEEDS_MORE_OUTPUT: {
                    final ByteBuffer nativeBuf = decoder.pull();
                    if (nativeBuf != null && nativeBuf.hasRemaining()) {
                        final ByteBuffer copy = ByteBuffer.allocateDirect(nativeBuf.remaining());
                        copy.put(nativeBuf).flip();
                        downstream.consume(copy);
                    }
                    break;
                }
                case NEEDS_MORE_INPUT:
                    if (decoder.hasOutput()) {
                        final ByteBuffer nativeBuf = decoder.pull();
                        if (nativeBuf != null && nativeBuf.hasRemaining()) {
                            final ByteBuffer copy = ByteBuffer.allocateDirect(nativeBuf.remaining());
                            copy.put(nativeBuf).flip();
                            downstream.consume(copy);
                            break;
                        }
                    }
                    return;
                case DONE:
                    if (decoder.hasOutput()) {
                        final ByteBuffer nativeBuf = decoder.pull();
                        if (nativeBuf != null && nativeBuf.hasRemaining()) {
                            final ByteBuffer copy = ByteBuffer.allocateDirect(nativeBuf.remaining());
                            copy.put(nativeBuf).flip();
                            downstream.consume(copy);
                            break;
                        }
                    }
                    return;
                default:
                    throw new IOException("DCB stream corrupted");
            }
        }
    }

    /**
     * Finalises decoding at end of stream. The decoder is drained of any pending output and the
     * completion is signalled to the downstream consumer. A stream that ends before the header is
     * complete, or before the Brotli decoder reaches its terminal state, is treated as truncated.
     *
     * @param trailers the response trailers, forwarded unchanged to the downstream consumer.
     * @throws IOException if the stream ends prematurely or the decoder has not fully consumed it.
     * @since 5.7
     */
    @Override
    public void streamEnd(final List<? extends Header> trailers) throws IOException, HttpException {
        if (header.hasRemaining() || decoder == null) {
            throw new IOException("Truncated DCB stream header");
        }

        pump();

        if (decoder.getStatus() != DecoderJNI.Status.DONE) {
            throw new IOException("Truncated DCB stream");
        }

        downstream.streamEnd(trailers);
    }

    /**
     * Releases the native Brotli decoder and propagates the call to the downstream consumer. Safe to
     * invoke whether or not the decoder was ever initialised; failures while destroying the native
     * decoder are suppressed so that downstream release always runs.
     *
     * @since 5.7
     */
    @Override
    public void releaseResources() {
        if (decoder != null) {
            try {
                decoder.destroy();
            } catch (final Throwable ignore) {
            }
        }
        downstream.releaseResources();
    }
}