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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@code AsyncEntityProducer} that streams the output of another producer
 * through the raw DEFLATE compression algorithm.
 *
 * <p>The delegate’s bytes are read in small chunks, compressed with
 * {@link java.util.zip.Deflater} and written immediately to the HTTP I/O
 * layer.  Memory use is therefore bounded even for very large request
 * entities.</p>
 *
 * @since 5.6
 */
public final class DeflatingAsyncEntityProducer implements AsyncEntityProducer {

    /**
     * inbound copy‐buffer
     */
    private static final int IN_BUF = 8 * 1024;
    /**
     * outbound staging buffer
     */
    private static final int OUT_BUF = 8 * 1024;

    private final AsyncEntityProducer delegate;
    private final String contentType;
    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, /*nowrap=*/true);

    /**
     * holds compressed bytes not yet sent downstream
     */
    private final ByteBuffer pending = ByteBuffer.allocate(OUT_BUF);
    private final byte[] in = new byte[IN_BUF];

    private final AtomicBoolean delegateEnded = new AtomicBoolean(false);
    private boolean finished = false;

    public DeflatingAsyncEntityProducer(final AsyncEntityProducer delegate) {
        this.delegate = Args.notNull(delegate, "delegate");
        this.contentType = delegate.getContentType();
        /* place pending into “read-mode” with no data */
        pending.flip();
    }

    // ------------------------------------------------------------------ metadata

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public long getContentLength() {
        return -1;
    }     // unknown

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getContentEncoding() {
        return "deflate";
    }

    @Override
    public boolean isChunked() {
        return true;
    }

    @Override
    public Set<String> getTrailerNames() {
        return Collections.emptySet();
    }

    @Override
    public int available() {
        if (pending.hasRemaining()) {
            return pending.remaining();
        }
        return delegate.available();
    }

    // ------------------------------------------------------------------ core

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        /* 1 — flush any leftover compressed bytes first */
        if (flushPending(channel)) {
            return;  // back-pressure: outer channel could not accept more
        }

        /* 2 — pull more data from delegate */
        delegate.produce(new InnerChannel(channel));

        /* 3 — if delegate ended, finish the deflater */
        if (delegateEnded.get() && !finished) {
            deflater.finish();
            deflateToPending();
            flushPending(channel);
            if (!pending.hasRemaining()) {
                finished = true;
                channel.endStream();
            }
        }
    }

    /**
     * copy as much as possible from {@link #pending} to the wire
     */
    private boolean flushPending(final DataStreamChannel ch) throws IOException {
        while (pending.hasRemaining()) {
            final int written = ch.write(pending);
            if (written == 0) {
                return true; // back-pressure
            }
        }
        pending.clear().flip();              // no data left → empty read-mode
        return false;
    }

    /**
     * drain {@link #deflater} into {@link #pending}
     */
    private void deflateToPending() {
        /* switch pending to write-mode */
        pending.compact();
        final byte[] out = pending.array();
        int total;
        do {
            total = deflater.deflate(out, pending.position(), pending.remaining(),
                    Deflater.NO_FLUSH);
            pending.position(pending.position() + total);
            if (!pending.hasRemaining() && total > 0) {
                /* buffer full: grow to the next power of two */
                final ByteBuffer bigger = ByteBuffer.allocate(pending.capacity() * 2);
                pending.flip();
                bigger.put(pending);
                pending.clear();
                pending.put(bigger);
            }
        } while (total > 0);
        pending.flip(); // back to read-mode
    }

    // ------------------------------------------------------------------ inner channel that receives raw bytes

    private final class InnerChannel implements DataStreamChannel {
        private final DataStreamChannel outer;

        InnerChannel(final DataStreamChannel outer) {
            this.outer = outer;
        }

        @Override
        public void requestOutput() {
            outer.requestOutput();
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            int consumed = 0;
            while (src.hasRemaining()) {
                final int chunk = Math.min(src.remaining(), in.length);
                src.get(in, 0, chunk);
                deflater.setInput(in, 0, chunk);
                consumed += chunk;
                deflateToPending();
                if (flushPending(outer)) {    // honour back-pressure
                    break;
                }
            }
            return consumed;
        }

        @Override
        public void endStream() {
            delegateEnded.set(true);
        }

        @Override
        public void endStream(final List<? extends Header> trailers) {
            endStream();
        }
    }

    // ------------------------------------------------------------------ error / cleanup

    @Override
    public void failed(final Exception cause) {
        delegate.failed(cause);
    }

    @Override
    public void releaseResources() {
        delegate.releaseResources();
        deflater.end();
    }
}
