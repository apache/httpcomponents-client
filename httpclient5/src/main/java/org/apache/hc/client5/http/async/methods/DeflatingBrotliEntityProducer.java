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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * Async Brotli deflater (reflection-based brotli4j). No compile-time dep.
 *
 * @since 5.6
 */
public final class DeflatingBrotliEntityProducer implements AsyncEntityProducer {

    private enum State { STREAMING, FINISHING, DONE }

    private final AsyncEntityProducer upstream;
    // Reflective encoder instance (brotli4j EncoderJNI.Wrapper)
    private final Object encoder;

    private ByteBuffer pendingOut;
    private List<? extends Header> pendingTrailers;
    private State state = State.STREAMING;

    public enum BrotliMode {
        GENERIC,
        TEXT,
        FONT;
    }

    /**
     * Defaults: quality=5, lgwin=22, mode=GENERIC.
     */
    public DeflatingBrotliEntityProducer(final AsyncEntityProducer upstream) throws IOException {
        this(upstream, 5, 22, BrotliMode.GENERIC);
    }

    /**
     * Convenience: modeInt 0=GENERIC, 1=TEXT, 2=FONT.
     */
    public DeflatingBrotliEntityProducer(
            final AsyncEntityProducer upstream,
            final int quality,
            final int lgwin,
            final int modeInt) throws IOException {
        this(upstream, quality, lgwin, modeInt == 1 ? BrotliMode.TEXT.name() : (modeInt == 2 ? BrotliMode.FONT.name() : BrotliMode.GENERIC.name()));
    }

    /**
     * Kept for compatibility with existing code/tests.
     * Uses reflection under the hood; no EncoderJNI references here.
     */
    public DeflatingBrotliEntityProducer(
            final AsyncEntityProducer upstream,
            final int quality,
            final int lgwin,
            final BrotliMode mode) throws java.io.IOException {
        this(upstream, quality, lgwin, mode != null ? mode.name() : "GENERIC");
    }

    /**
     * Fully reflective constructor used by the others.
     */
    public DeflatingBrotliEntityProducer(
            final AsyncEntityProducer upstream,
            final int quality,
            final int lgwin,
            final String modeName) throws IOException {
        this.upstream = Args.notNull(upstream, "upstream");
        try {
            this.encoder = AsyncBrotli.newEncoder(256 * 1024, quality, lgwin, modeName);
        } catch (final Exception e) {
            throw new IOException("Brotli (brotli4j) not available", e);
        }
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
        try {
            if (flushPending(channel)) {
                return;
            }

            if (state == State.FINISHING) {
                AsyncBrotli.encPushFinish(encoder);
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
                    try {
                        while (src.hasRemaining()) {
                            final ByteBuffer in = AsyncBrotli.encInput(encoder);
                            if (!in.hasRemaining()) {
                                AsyncBrotli.encPushProcess(encoder, 0);
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

                            AsyncBrotli.encPushProcess(encoder, xfer);
                            if (drainEncoder(channel)) {
                                break;
                            }
                        }
                        return accepted;
                    } catch (final Exception ex) {
                        throw new IOException("Brotli encode failed", ex);
                    }
                }

                @Override
                public void endStream() throws IOException {
                    endStream(Collections.emptyList());
                }

                @Override
                public void endStream(final List<? extends Header> trailers) throws IOException {
                    try {
                        pendingTrailers = trailers;
                        state = State.FINISHING;
                        AsyncBrotli.encPushFinish(encoder);
                        if (drainEncoder(channel)) {
                            return;
                        }
                        if (pendingTrailers == null) {
                            pendingTrailers = Collections.<Header>emptyList();
                        }
                        channel.endStream(pendingTrailers);
                        pendingTrailers = null;
                        state = State.DONE;
                    } catch (final Exception ex) {
                        throw new IOException("Brotli finalize failed", ex);
                    }
                }
            });
        } catch (final IOException ioe) {
            throw ioe;
        } catch (final Exception ex) {
            throw new IOException("Brotli encode failed", ex);
        }
    }

    @Override
    public void failed(final Exception cause) {
        upstream.failed(cause);
    }

    @Override
    public void releaseResources() {
        try {
            AsyncBrotli.encDestroy(encoder);
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

    private boolean drainEncoder(final DataStreamChannel channel) throws Exception {
        while (AsyncBrotli.encHasMoreOutput(encoder)) {
            final ByteBuffer buf = AsyncBrotli.encPull(encoder);
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
