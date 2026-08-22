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
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.luben.zstd.ZstdDecompressCtx;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@link AsyncDataConsumer} that inflates a Dictionary-Compressed Zstandard ({@code dcz})
 * response body on the fly and forwards the decoded bytes to a downstream consumer.
 * <p>
 * The stream opens with the {@code dcz} framing prefix defined by Compression Dictionary Transport: an eight-byte
 * magic sequence followed by the 32-byte hash of the dictionary the origin used to compress
 * the body. The hash is checked against the supplied {@link CompressionDictionary}, which is
 * loaded into the Zstandard decompression context
 * before any payload is decoded. If the prefix is malformed, or no dictionary matching the
 * advertised hash is available, decoding fails with an {@link IOException} so the exchange is
 * not silently served undecoded content.
 * <p>
 * Input arrives incrementally and is decoded in bounded direct buffers; output is delivered to
 * the downstream consumer as it becomes available, honouring back-pressure by returning early
 * whenever the downstream cannot accept the whole batch. Instances are single-use and not
 * thread-safe: they follow the sequential {@code AsyncDataConsumer} callback contract.
 *
 * @since 5.7
 */
public final class InflatingDictionaryZstdDataConsumer implements AsyncDataConsumer {

    private static final byte[] MAGIC = {
            0x5e, 0x2a, 0x4d, 0x18, 0x20, 0x00, 0x00, 0x00
    };

    private static final int HASH_LENGTH = 32;
    private static final int HEADER_LENGTH = MAGIC.length + HASH_LENGTH;

    private static final int IN_BUF = 64 * 1024;
    private static final int OUT_BUF = 128 * 1024;

    private final AsyncDataConsumer downstream;
    private final CompressionDictionary dictionary;
    private final ByteBuffer header;
    private final ZstdDecompressCtx dctx;
    private final ByteBuffer inDirect;
    private final ByteBuffer outDirect;
    private final AtomicBoolean closed;

    private boolean initialized;
    private boolean frameComplete;

    /**
     * Creates a consumer that decodes a {@code dcz} body and forwards the inflated bytes.
     *
     * @param downstream the consumer that receives the decoded content; must not be {@code null}.
     * @param dictionary the exact dictionary advertised for this exchange; must not be {@code null}.
     * @since 5.7
     */
    public InflatingDictionaryZstdDataConsumer(
            final AsyncDataConsumer downstream,
            final CompressionDictionary dictionary) {
        this.downstream = Args.notNull(downstream, "Downstream data consumer");
        this.dictionary = Args.notNull(dictionary, "Compression dictionary");
        this.header = ByteBuffer.allocate(HEADER_LENGTH);
        this.dctx = new ZstdDecompressCtx();
        this.inDirect = ByteBuffer.allocateDirect(IN_BUF);
        this.outDirect = ByteBuffer.allocateDirect(OUT_BUF);
        this.closed = new AtomicBoolean(false);

        inDirect.limit(0);
        outDirect.limit(0);
    }

    /**
     * Propagates a capacity update to the downstream consumer. The channel is wrapped so that the
     * capacity requested reflects compressed input rather than the larger inflated output, keeping
     * back-pressure meaningful across the decoding step.
     *
     * @since 5.7
     */
    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        downstream.updateCapacity(new InflatingCapacityChannel(capacityChannel));
    }

    /**
     * Decodes the next chunk of the {@code dcz} body. The framing prefix is accumulated and
     * validated across the leading calls; once the dictionary has been resolved and loaded, the
     * remaining bytes are inflated and passed downstream. Calls made after the stream has ended or
     * its resources have been released are ignored.
     *
     * @throws IOException if the framing prefix is invalid, the required dictionary is not
     *                     available, or the compressed stream is corrupt.
     * @since 5.7
     */
    @Override
    public void consume(final ByteBuffer src) throws IOException {
        if (closed.get()) {
            return;
        }

        if (!initialized) {
            consumeHeader(src);
            if (!initialized) {
                return;
            }
        }

        try {
            while (src.hasRemaining()) {
                inDirect.compact();

                final int take = Math.min(inDirect.remaining(), src.remaining());
                final int oldLimit = src.limit();
                src.limit(src.position() + take);
                inDirect.put(src);
                src.limit(oldLimit);

                inDirect.flip();

                while (inDirect.hasRemaining()) {
                    outDirect.compact();

                    frameComplete =
                            dctx.decompressDirectByteBufferStream(outDirect, inDirect);

                    outDirect.flip();

                    if (outDirect.hasRemaining()) {
                        downstream.consume(outDirect);
                        if (outDirect.hasRemaining()) {
                            return;
                        }
                    } else if (!inDirect.hasRemaining()) {
                        break;
                    }
                }
            }
        } catch (final RuntimeException ex) {
            throw new IOException("DCZ stream corrupted", ex);
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
                throw new IOException("Invalid DCZ stream header");
            }
        }

        final byte[] hash = new byte[HASH_LENGTH];
        header.get(hash);

        if (!dictionary.matchesHash(hash)) {
            throw new IOException("DCZ stream does not use the negotiated dictionary");
        }

        try {
            dctx.loadDict(dictionary.getContent());
            initialized = true;
        } catch (final RuntimeException ex) {
            throw new IOException("Unable to initialize DCZ decoder", ex);
        }
    }

    private void finishBufferedInput() throws IOException {
        try {
            drainOutput();
            while (inDirect.hasRemaining()) {
                final int inputPosition = inDirect.position();
                outDirect.compact();
                frameComplete = dctx.decompressDirectByteBufferStream(outDirect, inDirect);
                outDirect.flip();
                drainOutput();
                if (inDirect.position() == inputPosition) {
                    throw new IOException("DCZ stream made no progress");
                }
            }
        } catch (final IOException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new IOException("DCZ stream corrupted", ex);
        }
    }

    private void drainOutput() throws IOException {
        while (outDirect.hasRemaining()) {
            final int position = outDirect.position();
            downstream.consume(outDirect);
            if (outDirect.position() == position) {
                throw new IOException("Unable to deliver decoded DCZ data");
            }
        }
    }

    /**
     * Finalises decoding once the origin has signalled end of stream. The header and the final
     * Zstandard frame must both be complete; an incomplete header or a truncated frame is reported
     * as an {@link IOException} rather than yielding partial content. On success the decompression
     * context is closed and {@code streamEnd} is propagated downstream exactly once.
     *
     * @throws IOException if the {@code dcz} header or the compressed frame was truncated.
     * @since 5.7
     */
    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (!initialized) {
            throw new IOException("Truncated DCZ stream header");
        }

        finishBufferedInput();

        if (!frameComplete) {
            throw new IOException("Truncated DCZ stream");
        }

        if (closed.compareAndSet(false, true)) {
            dctx.close();
            downstream.streamEnd(trailers);
        }
    }

    /**
     * Releases the native Zstandard decompression context, if it has not already been closed by
     * {@link #streamEnd(List)}, and releases the downstream consumer. Safe to call more than once
     * and safe to call to abort an in-flight exchange.
     *
     * @since 5.7
     */
    @Override
    public void releaseResources() {
        if (closed.compareAndSet(false, true)) {
            dctx.close();
        }
        downstream.releaseResources();
    }
}
