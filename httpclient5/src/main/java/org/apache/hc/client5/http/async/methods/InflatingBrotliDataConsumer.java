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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

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
 * <h4>Implementation notes</h4>
 * Uses Brotli4j’s {@code DecoderJNI.Wrapper}. Native resources are released in
 * {@link #releaseResources()}. Throws an {@link java.io.IOException} if the stream is
 * truncated or corrupted.
 * <p>
 * Ensure {@link com.aayushatharva.brotli4j.Brotli4jLoader#ensureAvailability()} has been
 * called once at startup; this class also invokes it in a static initializer as a safeguard.
 * </p>
 *
 * <h4>Usage</h4>
 * <pre>{@code
 * AsyncDataConsumer textConsumer = new StringAsyncEntityConsumer();
 * AsyncDataConsumer brInflating  = new InflatingBrotliDataConsumer(textConsumer);
 * client.execute(producer, new BasicResponseConsumer<>(brInflating), null);
 * }</pre>
 *
 * @see org.apache.hc.core5.http.nio.AsyncDataConsumer
 * @see org.apache.hc.core5.http.nio.CapacityChannel
 * @see com.aayushatharva.brotli4j.decoder.DecoderJNI
 * @since 5.6
 */
public final class InflatingBrotliDataConsumer implements AsyncDataConsumer {

    private final AsyncDataConsumer downstream;
    private final Object decoder; // brotli4j DecoderJNI.Wrapper (reflective)
    private volatile CapacityChannel capacity;

    public InflatingBrotliDataConsumer(final AsyncDataConsumer downstream) {
        this.downstream = downstream;
        try {
            this.decoder = AsyncBrotli.newDecoder(8 * 1024);
        } catch (final Exception e) {
            throw new RuntimeException("Brotli (brotli4j) not available", e);
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        this.capacity = capacityChannel;
        downstream.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        try {
            while (src.hasRemaining()) {
                final ByteBuffer in = AsyncBrotli.decInput(decoder);
                final int xfer = Math.min(src.remaining(), in.remaining());
                if (xfer == 0) {
                    AsyncBrotli.decPush(decoder, 0);
                    pump();
                    continue;
                }
                final int lim = src.limit();
                src.limit(src.position() + xfer);
                in.put(src);
                src.limit(lim);

                AsyncBrotli.decPush(decoder, xfer);
                pump();
            }
            final CapacityChannel ch = this.capacity;
            if (ch != null) {
                ch.update(Integer.MAX_VALUE);
            }
        } catch (final Exception ex) {
            throw new IOException("Brotli decode failed", ex);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws IOException, HttpException {
        try {
            // Bounded finish to avoid spins on truncated streams.
            for (int i = 0; i < 8; i++) {
                final String st = AsyncBrotli.decStatusName(decoder);
                if ("DONE".equals(st)) {
                    downstream.streamEnd(trailers);
                    return;
                }
                if ("NEEDS_MORE_OUTPUT".equals(st)) {
                    pump();                 // drain pending output
                    continue;
                }
                if ("OK".equals(st)) {
                    AsyncBrotli.decPush(decoder, 0); // advance without new input
                    pump();
                    continue;
                }
                if ("NEEDS_MORE_INPUT".equals(st)) {
                    // End of input reached; decoder still wants bytes -> truncated
                    throw new IOException("Truncated brotli stream");
                }
                throw new IOException("Brotli stream corrupted: " + st);
            }
            throw new IOException("Brotli stream did not reach DONE");
        } catch (final IOException | HttpException e) {
            throw e;
        } catch (final Exception ex) {
            throw new IOException("Brotli stream end failed", ex);
        }
    }


    @Override
    public void releaseResources() {
        AsyncBrotli.decDestroy(decoder);
        downstream.releaseResources();
    }

    private void pump() throws Exception {
        for (; ; ) {
            final String st = AsyncBrotli.decStatusName(decoder);
            if ("OK".equals(st)) {
                AsyncBrotli.decPush(decoder, 0);
                continue;
            }
            if ("NEEDS_MORE_OUTPUT".equals(st)) {
                final ByteBuffer nativeBuf = AsyncBrotli.decPull(decoder);
                if (nativeBuf != null && nativeBuf.hasRemaining()) {
                    final ByteBuffer copy = ByteBuffer.allocateDirect(nativeBuf.remaining());
                    copy.put(nativeBuf).flip();
                    downstream.consume(copy);
                }
                continue;
            }
            if ("NEEDS_MORE_INPUT".equals(st)) {
                if (AsyncBrotli.decHasOutput(decoder)) {
                    final ByteBuffer nativeBuf = AsyncBrotli.decPull(decoder);
                    if (nativeBuf != null && nativeBuf.hasRemaining()) {
                        final ByteBuffer copy = ByteBuffer.allocateDirect(nativeBuf.remaining());
                        copy.put(nativeBuf).flip();
                        downstream.consume(copy);
                        continue;
                    }
                }
                return; // wait for more input
            }
            if ("DONE".equals(st)) {
                if (AsyncBrotli.decHasOutput(decoder)) {
                    final ByteBuffer nativeBuf = AsyncBrotli.decPull(decoder);
                    if (nativeBuf != null && nativeBuf.hasRemaining()) {
                        final ByteBuffer copy = ByteBuffer.allocateDirect(nativeBuf.remaining());
                        copy.put(nativeBuf).flip();
                        downstream.consume(copy);
                        continue;
                    }
                }
                return;
            }
            throw new IOException("Brotli stream corrupted");
        }
    }
}