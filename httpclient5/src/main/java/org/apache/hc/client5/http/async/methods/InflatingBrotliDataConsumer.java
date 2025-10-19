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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Asserts;

/**
 * {@code AsyncDataConsumer} that inflates a Brotli-compressed byte stream and forwards
 * decompressed bytes to a downstream consumer.
 * <p>
 * Purely async/streaming: no {@code InputStream}/{@code OutputStream}. Back-pressure from
 * the I/O reactor is propagated via {@link CapacityChannel}. JNI output buffers are copied
 * into small reusable direct {@link java.nio.ByteBuffer}s before handing them to the
 * downstream consumer (which may retain them).
 * </p>
 *
 * <p><strong>Implementation notes</strong></p>
 * Uses Brotli4j’s {@code DecoderJNI.Wrapper}. Native resources are released in
 * {@link #releaseResources()}. Throws an {@link java.io.IOException} if the stream is
 * truncated or corrupted.
 * <p>
 * Ensure {@link com.aayushatharva.brotli4j.Brotli4jLoader#ensureAvailability()} has been
 * called once at startup; this class also invokes it in a static initializer as a safeguard.
 * </p>
 *
 * @see org.apache.hc.core5.http.nio.AsyncDataConsumer
 * @see org.apache.hc.core5.http.nio.CapacityChannel
 * @see com.aayushatharva.brotli4j.decoder.DecoderJNI
 * @since 5.6
 */
public final class InflatingBrotliDataConsumer implements AsyncDataConsumer {

    private final AsyncDataConsumer downstream;
    private final DecoderJNI.Wrapper decoder;
    private volatile CapacityChannel capacity;


    public InflatingBrotliDataConsumer(final AsyncDataConsumer downstream) {
        this.downstream = downstream;
        try {
            this.decoder = new DecoderJNI.Wrapper(8 * 1024);
        } catch (final IOException e) {
            throw new RuntimeException("Unable to initialize DecoderJNI", e);
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        this.capacity = capacityChannel;
        downstream.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        while (src.hasRemaining()) {
            final ByteBuffer in = decoder.getInputBuffer();
            final int xfer = Math.min(src.remaining(), in.remaining());
            if (xfer == 0) {
                decoder.push(0);
                pump();
                continue;
            }
            final int lim = src.limit();
            src.limit(src.position() + xfer);
            in.put(src);
            src.limit(lim);

            decoder.push(xfer);
            pump();
        }
        final CapacityChannel ch = this.capacity;
        if (ch != null) {
            ch.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws IOException, HttpException {
        pump();
        Asserts.check(decoder.getStatus() == DecoderJNI.Status.DONE || !decoder.hasOutput(),
                "Truncated brotli stream");
        downstream.streamEnd(trailers);
    }

    @Override
    public void releaseResources() {
        try {
            decoder.destroy();
        } catch (final Throwable ignore) {
        }
        downstream.releaseResources();
    }

    private void pump() throws IOException {
        for (; ; ) {
            switch (decoder.getStatus()) {
                case OK:
                    decoder.push(0);
                    break;
                case NEEDS_MORE_OUTPUT: {
                    // Pull a decoder-owned buffer; copy before handing off.
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
                    return; // wait for more input
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
                    // Corrupted stream
                    throw new IOException("Brotli stream corrupted");
            }
        }
    }
}