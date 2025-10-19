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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.luben.zstd.ZstdDirectBufferCompressingStream;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@code AsyncEntityProducer} that compresses the bytes produced by a delegate entity
 * into a single <a href="https://www.rfc-editor.org/rfc/rfc8878">Zstandard</a> (zstd) frame
 * on the fly.
 *
 * <p>This producer wraps a {@link org.apache.hc.core5.http.nio.AsyncEntityProducer} and
 * performs streaming, ByteBuffer-to-ByteBuffer compression as the delegate writes to the
 * provided {@link org.apache.hc.core5.http.nio.DataStreamChannel}. No {@code InputStream}
 * is used in the client pipeline.</p>
 *
 * <p>Metadata reported by this producer:</p>
 * <ul>
 *   <li>{@link #getContentEncoding()} returns {@code "zstd"}.</li>
 *   <li>{@link #getContentLength()} returns {@code -1} (unknown after compression).</li>
 *   <li>{@link #isChunked()} returns {@code true} (requests are typically sent chunked).</li>
 * </ul>
 *
 * <p><strong>Behavior</strong></p>
 * <ul>
 *   <li><b>Streaming &amp; back-pressure:</b> compressed output is staged in direct
 *       {@link java.nio.ByteBuffer}s and written only when the channel accepts bytes.
 *       When {@code DataStreamChannel.write(...)} returns {@code 0}, the producer pauses and
 *       requests another output turn.</li>
 *   <li><b>Finalization:</b> after the delegate signals {@code endStream()}, this producer emits
 *       the zstd frame epilogue and then calls {@code DataStreamChannel.endStream()}.</li>
 *   <li><b>Repeatability:</b> repeatable only if the delegate is repeatable.</li>
 *   <li><b>Headers:</b> callers are responsible for sending {@code Content-Encoding: zstd} on
 *       the request if required by the server. Content length is not known in advance.</li>
 *   <li><b>Resources:</b> invoke {@link #releaseResources()} to free native compressor resources.</li>
 * </ul>
 *
 * <p><strong>Constructors</strong></p>
 * <ul>
 *   <li>{@code DeflatingZstdEntityProducer(delegate)} – uses a default compression level.</li>
 *   <li>{@code DeflatingZstdEntityProducer(delegate, level)} – explicitly sets the zstd level.</li>
 * </ul>
 *
 * <p><strong>Thread-safety</strong></p>
 * <p>Not thread-safe; one instance per message exchange.</p>
 *
 * <p><strong>Runtime dependency</strong></p>
 * <p>Requires {@code com.github.luben:zstd-jni} on the classpath.</p>
 *
 * @see org.apache.hc.client5.http.async.methods.InflatingZstdDataConsumer
 * @see org.apache.hc.core5.http.nio.support.BasicRequestProducer
 * @see org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer
 * @see org.apache.hc.client5.http.impl.async.ContentCompressionAsyncExec
 * @since 5.6
 */
public final class DeflatingZstdEntityProducer implements AsyncEntityProducer {

    private static final int IN_BUF = 64 * 1024;
    private static final int OUT_BUF_DEFAULT = 128 * 1024;

    private final AsyncEntityProducer delegate;

    /**
     * Direct staging for heap inputs.
     */
    private final ByteBuffer inDirect = ByteBuffer.allocateDirect(IN_BUF);

    /**
     * Pending compressed output buffers, ready to write (pos=0..limit).
     */
    private final Deque<ByteBuffer> pending = new ArrayDeque<>();

    /**
     * Current output buffer owned by zstd; replaced when it overflows or flushes.
     */
    private ByteBuffer outBuf;

    /**
     * Zstd compressor stream.
     */
    private ZstdDirectBufferCompressingStream zstream;

    private volatile boolean upstreamEnded = false;
    private volatile boolean finished = false;
    private final AtomicBoolean released = new AtomicBoolean(false);

    private final int level;
    private final int outCap;

    public DeflatingZstdEntityProducer(final AsyncEntityProducer delegate) {
        this(delegate, 3); // default compression level
    }

    public DeflatingZstdEntityProducer(final AsyncEntityProducer delegate, final int level) {
        this.delegate = Args.notNull(delegate, "delegate");
        this.level = level;
        inDirect.limit(0);

        // Pick a sensible out buffer size (at least the recommended size).
        final int rec = ZstdDirectBufferCompressingStream.recommendedOutputBufferSize();
        this.outCap = Math.max(OUT_BUF_DEFAULT, rec);
        outBuf = ByteBuffer.allocateDirect(outCap);
    }

    @Override
    public boolean isRepeatable() {
        return delegate.isRepeatable();
    }

    @Override
    public long getContentLength() {
        return -1;
    } // unknown after compression

    @Override
    public String getContentType() {
        return delegate.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return "zstd";
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
        if (!pending.isEmpty()) {
            final ByteBuffer head = pending.peekFirst();
            return head != null ? head.remaining() : 1;
        }
        // Delegate ended but we still must write zstd frame epilogue (produced on close()).
        if (upstreamEnded && !finished) {
            // Return a positive value to keep the reactor calling produce().
            return OUT_BUF_DEFAULT;
        }
        return delegate.available();
    }

    @Override
    public void produce(final DataStreamChannel chan) throws IOException {
        ensureStreamInitialized();

        // 1) flush anything already compressed
        if (!flushPending(chan)) {
            return; // back-pressure; we'll be called again
        }
        if (finished) {
            return;
        }

        // 2) pull more input from delegate (this will drive compression via Inner.write)
        delegate.produce(new Inner(chan));

        // 3) If upstream ended, finish the frame and drain everything
        if (upstreamEnded && !finished) {
            try {
                zstream.close(); // triggers flushBuffer for remaining + frame trailer
            } finally {
                // fall through
            }

            if (!flushPending(chan)) {
                // trailer not fully sent yet; wait for next turn
                return;
            }
            finished = true;
            chan.endStream();
        }
    }

    private void ensureStreamInitialized() throws IOException {
        if (zstream != null) {
            return;
        }
        // Create the compressor; override flushBuffer to queue full buffers.
        zstream = new ZstdDirectBufferCompressingStream(outBuf, level) {
            @Override
            protected ByteBuffer flushBuffer(final ByteBuffer toFlush) throws IOException {
                toFlush.flip();
                if (toFlush.hasRemaining()) {
                    pending.addLast(toFlush); // queue for network write
                }
                // hand a fresh direct buffer back to the compressor
                outBuf = ByteBuffer.allocateDirect(outCap);
                return outBuf;
            }
        };
    }

    /**
     * Try to write as much of the pending compressed data as the channel accepts.
     */
    private boolean flushPending(final DataStreamChannel chan) throws IOException {
        while (!pending.isEmpty()) {
            final ByteBuffer head = pending.peekFirst();
            while (head.hasRemaining()) {
                final int n = chan.write(head);
                if (n == 0) {
                    // back-pressure: ask to be called again
                    chan.requestOutput();
                    return false;
                }
            }
            pending.removeFirst(); // this buffer fully sent
        }
        return true;
    }

    /**
     * Compress the bytes in {@code src} (may be heap or direct).
     */
    private int compressFrom(final ByteBuffer src) throws IOException {
        final int before = src.remaining();
        if (src.isDirect()) {
            zstream.compress(src);
        } else {
            // Stage heap → direct in chunks
            while (src.hasRemaining()) {
                inDirect.compact();
                final int take = Math.min(inDirect.remaining(), src.remaining());
                final int oldLimit = src.limit();
                src.limit(src.position() + take);
                inDirect.put(src);
                src.limit(oldLimit);
                inDirect.flip();
                zstream.compress(inDirect);
            }
        }
        // The compressor calls flushBuffer() as needed; new buffers are queued in 'pending'.
        return before - src.remaining();
    }

    private final class Inner implements DataStreamChannel {
        private final DataStreamChannel downstream;

        Inner(final DataStreamChannel downstream) {
            this.downstream = downstream;
        }

        @Override
        public void requestOutput() {
            downstream.requestOutput();
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final int consumed = compressFrom(src);
            // Try to flush any buffers the compressor just queued
            if (!flushPending(downstream)) {
                // Not all data could be written now; ensure we get another callback
                downstream.requestOutput();
            }
            return consumed;
        }

        @Override
        public void endStream() {
            upstreamEnded = true;
            // We will finalize and flush in the outer produce(); make sure it runs again soon.
            downstream.requestOutput();
        }

        @Override
        public void endStream(final java.util.List<? extends Header> trailers) {
            endStream();
        }
    }

    @Override
    public void failed(final Exception cause) {
        delegate.failed(cause);
    }

    @Override
    public void releaseResources() {
        if (released.compareAndSet(false, true)) {
            try {
                try {
                    if (zstream != null) {
                        zstream.close();
                    }
                } catch (final IOException ignore) {
                }
            } finally {
                delegate.releaseResources();
            }
        }
    }
}
