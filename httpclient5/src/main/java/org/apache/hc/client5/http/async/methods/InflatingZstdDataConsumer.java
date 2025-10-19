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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * {@code AsyncDataConsumer} that inflates Zstandard (zstd) content codings.
 *
 * <p>This consumer accepts compressed bytes and forwards decompressed data to a downstream
 * {@link org.apache.hc.core5.http.nio.AsyncDataConsumer}. It is intended to be installed by the
 * client execution chain when a response carries {@code Content-Encoding: zstd}. Applications
 * normally do not instantiate it directly—enable content compression (default) and let
 * {@code ContentCompressionAsyncExec} wire it automatically.</p>
 *
 * <p><strong>Behavior</strong></p>
 * <ul>
 *   <li>Streams decompression as data arrives; does not require the full message in memory.</li>
 *   <li>Updates the downstream with plain bytes; the client removes the original
 *       {@code Content-Encoding} and related headers.</li>
 *   <li>On malformed input it throws an {@link java.io.IOException} with a descriptive message.</li>
 *   <li>{@link #releaseResources()} must be called to free native resources.</li>
 * </ul>
 *
 * @since 5.6
 */

public final class InflatingZstdDataConsumer implements AsyncDataConsumer {

    private static final int IN_BUF = 64 * 1024;
    private static final int OUT_BUF = 128 * 1024; // ~ZSTD_DStreamOutSize(); tweak if you like. :contentReference[oaicite:3]{index=3}

    private final AsyncDataConsumer downstream;
    private final ZstdDecompressCtx dctx = new ZstdDecompressCtx();

    private final ByteBuffer inDirect = ByteBuffer.allocateDirect(IN_BUF);
    private final ByteBuffer outDirect = ByteBuffer.allocateDirect(OUT_BUF);

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public InflatingZstdDataConsumer(final AsyncDataConsumer downstream) {
        this.downstream = downstream;
        inDirect.limit(0);
        outDirect.limit(0);
    }

    @Override
    public void updateCapacity(final CapacityChannel c) throws IOException {
        downstream.updateCapacity(c);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        if (closed.get()) {
            return;
        }

        // Copy any incoming bytes into the direct input buffer, draining as we go.
        while (src.hasRemaining()) {
            inDirect.compact();
            final int take = Math.min(inDirect.remaining(), src.remaining());
            final int oldLimit = src.limit();
            src.limit(src.position() + take);
            inDirect.put(src);
            src.limit(oldLimit);
            inDirect.flip();

            // Pull decompressed bytes until we either need more input or downstream back-pressures
            while (inDirect.hasRemaining()) {
                outDirect.compact();
                // Streaming decompress: fills outDirect from inDirect; returns when either
                // input exhausted or output buffer full.
                dctx.decompressDirectByteBufferStream(outDirect, inDirect);
                outDirect.flip();
                if (outDirect.hasRemaining()) {
                    downstream.consume(outDirect);
                    if (outDirect.hasRemaining()) {
                        // downstream applied back-pressure; stop here, we’ll resume on next callback
                        return;
                    }
                } else {
                    break; // need more input
                }
            }
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (closed.compareAndSet(false, true)) {
            dctx.close();
            downstream.streamEnd(trailers);
        }
    }

    @Override
    public void releaseResources() {
        dctx.close();
        downstream.releaseResources();
    }
}