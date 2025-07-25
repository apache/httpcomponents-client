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
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * Streams the output of another {@link AsyncEntityProducer} through GZIP
 * compression without blocking I/O classes.  A minimal 10-byte header is
 * emitted first, deflate blocks follow, then an 8-byte trailer carrying the
 * CRC-32 and the uncompressed byte count.
 *
 * <p>The producer honours back-pressure: if {@code DataStreamChannel.write()}
 * returns 0, compression stops until the reactor calls {@code requestOutput()}
 * again.</p>
 *
 * @since 5.6
 */
public final class GzipAsyncEntityProducer implements AsyncEntityProducer {

    private static final int IN_BUF = 8 * 1024;
    private static final int OUT_BUF = 8 * 1024;

    private static final byte[] GZIP_HEADER = {
            (byte) 0x1F, (byte) 0x8B,   // ID1-ID2
            8,                          // CM = deflate
            0,                          // FLG = no extras
            0, 0, 0, 0,                    // MTIME = 0
            0,                          // XFL
            (byte) 255                  // OS  = unknown
    };

    private final AsyncEntityProducer delegate;
    private final ContentType contentType;

    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private final CRC32 crc = new CRC32();

    private final byte[] inBuf = new byte[IN_BUF];
    private final byte[] outBuf = new byte[OUT_BUF];
    private final ByteBuffer pending = ByteBuffer.allocate(OUT_BUF * 2);

    private boolean headerSent = false;
    private boolean trailerSent = false;
    private long uncompressed = 0;
    private final AtomicBoolean delegateEnded = new AtomicBoolean(false);

    public GzipAsyncEntityProducer(final AsyncEntityProducer delegate) {
        this.delegate = Args.notNull(delegate, "delegate");
        this.contentType = ContentType.parse(delegate.getContentType());
        pending.flip();                // empty read-mode
    }


    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public String getContentType() {
        return contentType.toString();
    }

    @Override
    public String getContentEncoding() {
        return "gzip";
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
        return pending.hasRemaining() ? pending.remaining() : delegate.available();
    }

    @Override
    public void produce(final DataStreamChannel ch) throws IOException {

        if (flushPending(ch)) {
            return;
        }

        if (!headerSent) {
            writeBytes(GZIP_HEADER);
            headerSent = true;
            if (flushPending(ch)) {
                return;
            }
        }

        delegate.produce(new InnerChannel(ch));

        if (delegateEnded.get() && !trailerSent) {

            deflater.finish();

            while (!deflater.finished()) {
                deflateToPending(Deflater.NO_FLUSH);
                if (flushPending(ch)) {
                    return;
                }
            }
            writeTrailer();
            trailerSent = true;
            flushPending(ch);

            if (!pending.hasRemaining()) {
                ch.endStream();
            }
        }
    }


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
                final int len = Math.min(src.remaining(), inBuf.length);
                src.get(inBuf, 0, len);
                crc.update(inBuf, 0, len);
                uncompressed += len;

                deflater.setInput(inBuf, 0, len);
                /* SYNC_FLUSH closes the current block so no stray bits linger */
                deflateToPending(Deflater.SYNC_FLUSH);

                if (flushPending(outer)) {
                    return consumed + len;
                }
                consumed += len;
            }
            return consumed;
        }

        @Override
        public void endStream() {
            delegateEnded.set(true);
        }

        @Override
        public void endStream(final List<? extends Header> t) {
            endStream();
        }
    }


    private void deflateToPending(final int flushMode) {
        pending.compact();
        while (true) {
            final int n = deflater.deflate(outBuf, 0, outBuf.length, flushMode);
            if (n == 0) {
                break;
            }
            pending.put(outBuf, 0, n);
        }
        pending.flip();
    }

    private void writeBytes(final byte[] src) {
        pending.compact();
        pending.put(src);
        pending.flip();
    }

    private void writeTrailer() {
        pending.compact();
        writeIntLE((int) crc.getValue());
        writeIntLE((int) (uncompressed & 0xFFFFFFFFL));
        pending.flip();
    }

    private void writeIntLE(final int v) {
        pending.put((byte) v);
        pending.put((byte) (v >> 8));
        pending.put((byte) (v >> 16));
        pending.put((byte) (v >> 24));
    }

    /**
     * @return {@code true} if transport is full and caller must stop.
     */
    private boolean flushPending(final DataStreamChannel ch) throws IOException {
        while (pending.hasRemaining()) {
            if (ch.write(pending) == 0) {
                return true; // back-pressure
            }
        }
        return false;
    }

    /* ---------- boiler-plate ---------- */

    @Override
    public void failed(final Exception ex) {
        delegate.failed(ex);
    }

    @Override
    public void releaseResources() {
        delegate.releaseResources();
        deflater.end();
    }
}
