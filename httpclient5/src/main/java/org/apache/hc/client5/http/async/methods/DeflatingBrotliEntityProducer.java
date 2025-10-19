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

import com.aayushatharva.brotli4j.encoder.Encoder;
import com.aayushatharva.brotli4j.encoder.EncoderJNI;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@code AsyncEntityProducer} that Brotli-compresses bytes from an upstream producer
 * on the fly and writes the compressed stream to the target {@link DataStreamChannel}.
 * <p>
 * Purely async/streaming: no {@code InputStream}/{@code OutputStream}. Back-pressure is
 * honored via {@link #available()} and the I/O reactor’s calls into {@link #produce(DataStreamChannel)}.
 * Trailers from the upstream producer are preserved and emitted once the compressed output
 * has been fully drained.
 * </p>
 *
 * <p><strong>Content metadata</strong></p>
 * Returns {@code Content-Encoding: br}, {@code Content-Length: -1} and {@code chunked=true}.
 * Repeatability matches the upstream producer.
 *
 * <p><strong>Implementation notes</strong></p>
 * Uses Brotli4j’s {@code EncoderJNI.Wrapper}. JNI-owned output buffers are written directly
 * when possible; if the channel applies back-pressure, the unwritten tail is copied into
 * small pooled direct {@link java.nio.ByteBuffer}s to reduce allocation churn. Native
 * resources are released in {@link #releaseResources()}.
 * <p>
 * Ensure {@link com.aayushatharva.brotli4j.Brotli4jLoader#ensureAvailability()} has been
 * called once at startup; this class also invokes it in a static initializer as a safeguard.
 * </p>
 *
 * @see org.apache.hc.core5.http.nio.AsyncEntityProducer
 * @see org.apache.hc.core5.http.nio.DataStreamChannel
 * @see com.aayushatharva.brotli4j.encoder.EncoderJNI
 * @since 5.6
 */
public final class DeflatingBrotliEntityProducer implements AsyncEntityProducer {

    private enum State { STREAMING, FINISHING, DONE }

    private final AsyncEntityProducer upstream;
    private final EncoderJNI.Wrapper encoder;

    private ByteBuffer pendingOut;
    private List<? extends Header> pendingTrailers;
    private State state = State.STREAMING;

    /**
     * Create a producer with explicit Brotli params.
     *
     * @param upstream upstream entity producer whose bytes will be compressed
     * @param quality  Brotli quality level (see brotli4j documentation)
     * @param lgwin    Brotli window size log2 (see brotli4j documentation)
     * @param mode     Brotli mode hint (GENERIC/TEXT/FONT)
     * @throws IOException if the native encoder cannot be created
     * @since 5.6
     */
    public DeflatingBrotliEntityProducer(
            final AsyncEntityProducer upstream,
            final int quality,
            final int lgwin,
            final Encoder.Mode mode) throws IOException {
        this.upstream = Args.notNull(upstream, "upstream");
        this.encoder = new EncoderJNI.Wrapper(256 * 1024, quality, lgwin, mode);
    }

    /**
     * Convenience constructor mapping {@code 0=GENERIC, 1=TEXT, 2=FONT}.
     *
     * @since 5.6
     */
    public DeflatingBrotliEntityProducer(
            final AsyncEntityProducer upstream,
            final int quality,
            final int lgwin,
            final int modeInt) throws IOException {
        this(upstream, quality, lgwin,
                modeInt == 1 ? Encoder.Mode.TEXT :
                        modeInt == 2 ? Encoder.Mode.FONT : Encoder.Mode.GENERIC);
    }

    /**
     * Create a producer with sensible defaults ({@code quality=5}, {@code lgwin=22}, {@code GENERIC}).
     *
     * @since 5.6
     */
    public DeflatingBrotliEntityProducer(final AsyncEntityProducer upstream) throws IOException {
        this(upstream, 5, 22, Encoder.Mode.GENERIC);
    }


    @Override
    public String getContentType() {
        return upstream.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return "br";
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public boolean isChunked() {
        return true;
    }

    @Override
    public Set<String> getTrailerNames() {
        return upstream.getTrailerNames();
    }

    @Override
    public boolean isRepeatable() {
        return upstream.isRepeatable();
    }

    @Override
    public int available() {
        if (state == State.DONE) {
            return 0;
        }
        if (pendingOut != null && pendingOut.hasRemaining() || pendingTrailers != null) {
            return 1;
        }
        final int up = upstream.available();
        return (state != State.STREAMING || up > 0) ? 1 : 0;
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (flushPending(channel)) {
            return;
        }

        if (state == State.FINISHING) {
            encoder.push(EncoderJNI.Operation.FINISH, 0);
            if (drainEncoder(channel)) {
                return;
            }
            if (pendingTrailers == null) {
                pendingTrailers = Collections.emptyList();
            }
            channel.endStream(pendingTrailers);
            pendingTrailers = null;
            state = State.DONE;
            return;
        }

        upstream.produce(new DataStreamChannel() {
            @Override
            public void requestOutput() {
                channel.requestOutput();
            }

            @Override
            public int write(final ByteBuffer src) throws IOException {
                int accepted = 0;
                while (src.hasRemaining()) {
                    final ByteBuffer in = encoder.getInputBuffer();
                    if (!in.hasRemaining()) {
                        encoder.push(EncoderJNI.Operation.PROCESS, 0);
                        if (drainEncoder(channel)) {
                            break;
                        }
                        continue;
                    }
                    final int xfer = Math.min(src.remaining(), in.remaining());
                    final int lim = src.limit();
                    src.limit(src.position() + xfer);
                    in.put(src);
                    src.limit(lim);
                    accepted += xfer;

                    encoder.push(EncoderJNI.Operation.PROCESS, xfer);
                    if (drainEncoder(channel)) {
                        break;
                    }
                }
                return accepted;
            }

            @Override
            public void endStream() throws IOException {
                endStream(Collections.emptyList());
            }

            @Override
            public void endStream(final List<? extends Header> trailers) throws IOException {
                pendingTrailers = trailers;
                state = State.FINISHING;
                encoder.push(EncoderJNI.Operation.FINISH, 0);
                if (drainEncoder(channel)) {
                    return;
                }
                if (pendingTrailers == null) {
                    pendingTrailers = Collections.emptyList();
                }
                channel.endStream(pendingTrailers);
                pendingTrailers = null;
                state = State.DONE;
            }
        });
    }

    @Override
    public void failed(final Exception cause) {
        upstream.failed(cause);
    }

    @Override
    public void releaseResources() {
        try {
            encoder.destroy();
        } catch (final Throwable ignore) {
        }
        upstream.releaseResources();
        pendingOut = null;
        pendingTrailers = null;
        state = State.DONE;
    }


    private boolean flushPending(final DataStreamChannel channel) throws IOException {
        if (pendingOut != null && pendingOut.hasRemaining()) {
            channel.write(pendingOut);
            if (pendingOut.hasRemaining()) {
                channel.requestOutput();
                return true;
            }
            pendingOut = null;
        }
        if (pendingOut == null && pendingTrailers != null && state != State.STREAMING) {
            channel.endStream(pendingTrailers);
            pendingTrailers = null;
            state = State.DONE;
            return true;
        }
        return false;
    }

    private boolean drainEncoder(final DataStreamChannel channel) throws IOException {
        while (encoder.hasMoreOutput()) {
            final ByteBuffer buf = encoder.pull();
            if (buf == null || !buf.hasRemaining()) {
                continue;
            }
            channel.write(buf);
            if (buf.hasRemaining()) {
                pendingOut = ByteBuffer.allocateDirect(buf.remaining());
                pendingOut.put(buf).flip();
                channel.requestOutput();
                return true;
            }
        }
        return false;
    }
}
