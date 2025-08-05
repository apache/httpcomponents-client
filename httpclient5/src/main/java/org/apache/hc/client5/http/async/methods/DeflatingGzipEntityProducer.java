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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * Streams an {@link AsyncEntityProducer} through raw DEFLATE
 * and wraps the result in a valid GZIP container.
 * <p>
 * Memory usage is bounded (8 KiB buffers) and back-pressure
 * from the I/O reactor is honoured.
 *
 * @since 5.6
 */
public final class DeflatingGzipEntityProducer implements AsyncEntityProducer {

    /* ---------------- constants & buffers --------------------------- */

    private static final int IN_BUF = 8 * 1024;
    private static final int OUT_BUF = 8 * 1024;

    private final AsyncEntityProducer delegate;
    private final CRC32 crc = new CRC32();
    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private final byte[] in = new byte[IN_BUF];
    private final ByteBuffer outBuf = ByteBuffer.allocate(OUT_BUF);

    private boolean headerSent = false;
    private boolean finished = false;
    private long uncompressed = 0;

    private final AtomicBoolean released = new AtomicBoolean(false);

    public DeflatingGzipEntityProducer(final AsyncEntityProducer delegate) {
        this.delegate = Args.notNull(delegate, "delegate");
        outBuf.flip(); // start in “read mode” with no data
    }

    /* ------------------- metadata ------------------- */

    @Override
    public boolean isRepeatable() {
        return delegate.isRepeatable();
    }

    @Override
    public long getContentLength() {
        return -1;
    } // unknown

    @Override
    public String getContentType() {
        return delegate.getContentType();
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
        return outBuf.hasRemaining() ? outBuf.remaining() : delegate.available();
    }

    /* ------------------- core ----------------------- */

    @Override
    public void produce(final DataStreamChannel chan) throws IOException {

        flushOut(chan);                    // 1) flush any pending data

        if (finished) {
            return;                        // already done
        }

        delegate.produce(new InnerChannel(chan)); // 2) pull more input

        /* 3) when delegate is done → finish deflater, drain, trailer */
        if (delegate.available() == 0 && !finished) {

            deflater.finish();             // signal EOF to compressor
            while (!deflater.finished()) { // drain *everything*
                deflateToOut();
                flushOut(chan);
            }

            /* ---------------- little-endian trailer ---------------- */
            final int crcVal = (int) crc.getValue();
            final int size = (int) uncompressed;

            final byte[] trailer = {
                    (byte) crcVal, (byte) (crcVal >>> 8),
                    (byte) (crcVal >>> 16), (byte) (crcVal >>> 24),
                    (byte) size, (byte) (size >>> 8),
                    (byte) (size >>> 16), (byte) (size >>> 24)
            };
            chan.write(ByteBuffer.wrap(trailer));

            finished = true;
            chan.endStream();
        }
    }

    /* copy all currently available bytes from deflater into outBuf */
    private void deflateToOut() {
        outBuf.compact();                  // switch to “write mode”
        byte[] arr = outBuf.array();
        int pos = outBuf.position();
        int lim = outBuf.limit();
        int n;
        while ((n = deflater.deflate(arr, pos, lim - pos, Deflater.NO_FLUSH)) > 0) {
            pos += n;
            if (pos == lim) {              // buffer full → grow 2×
                final ByteBuffer bigger = ByteBuffer.allocate(arr.length * 2);
                outBuf.flip();
                bigger.put(outBuf);
                outBuf.clear();
                outBuf.put(bigger);
                arr = outBuf.array();
                lim = outBuf.limit();
                pos = outBuf.position();
            }
        }
        outBuf.position(pos);
        outBuf.flip();                     // back to “read mode”
    }

    /* send as much of outBuf as the channel will accept */
    private void flushOut(final DataStreamChannel chan) throws IOException {
        while (outBuf.hasRemaining()) {
            final int written = chan.write(outBuf);
            if (written == 0) {
                break; // back-pressure
            }
        }
    }

    /* --------------- inner channel feeding deflater ---------------- */

    private final class InnerChannel implements DataStreamChannel {
        private final DataStreamChannel chan;

        InnerChannel(final DataStreamChannel chan) {
            this.chan = chan;
        }

        @Override
        public void requestOutput() {
            chan.requestOutput();
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {

            if (!headerSent) {             // write 10-byte GZIP header
                chan.write(ByteBuffer.wrap(new byte[]{
                        0x1f, (byte) 0x8b, 8, 0, 0, 0, 0, 0, 0, 0
                }));
                headerSent = true;
            }

            int consumed = 0;
            while (src.hasRemaining()) {
                final int chunk = Math.min(src.remaining(), in.length);
                src.get(in, 0, chunk);

                crc.update(in, 0, chunk);
                uncompressed += chunk;

                deflater.setInput(in, 0, chunk);
                consumed += chunk;

                deflateToOut();
                flushOut(chan);
            }
            return consumed;
        }

        @Override
        public void endStream() { /* delegate.available()==0 is our signal */ }

        @Override
        public void endStream(final List<? extends Header> t) {
            endStream();
        }
    }

    /* ---------------- failure / cleanup ---------------------------- */

    @Override
    public void failed(final Exception cause) {
        delegate.failed(cause);
    }

    @Override
    public void releaseResources() {
        if (released.compareAndSet(false, true)) {
            deflater.end();
            delegate.releaseResources();
        }
    }
}
