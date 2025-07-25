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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * Streaming, non-blocking consumer for responses encoded with
 * {@code Content-Encoding: gzip}.
 *
 * <p>The implementation follows the public GZIP specification:</p>
 * <ol>
 *   <li>Verifies the fixed 10-byte header (ID1 0x1F, ID2 0x8B, CM 8).</li>
 *   <li>Parses / skips optional sections signalled by the FLG bits<br>
 *       (FEXTRA, FNAME, FCOMMENT, FHCRC, FTEXT).</li>
 *   <li>Inflates the raw DEFLATE blocks (<em>nowrap=true</em>) while
 *       streaming the plain bytes to an inner consumer.</li>
 *   <li>Collects the 8-byte trailer (CRC-32 &amp; ISIZE) and validates it
 *       on {@link #streamEnd(List)}.</li>
 * </ol>
 *
 * @param <T> result type produced by the wrapped inner consumer
 * @since 5.6
 */
public final class GzipAsyncEntityConsumer<T> implements AsyncEntityConsumer<T> {

    private static final int FTEXT = 1;   // not used, informative only
    private static final int FHCRC = 2;
    private static final int FEXTRA = 4;
    private static final int FNAME = 8;
    private static final int FCOMMENT = 16;

    private static final int OUT_BUF = 8 * 1024;          // inflate buffer

    private final AsyncEntityConsumer<T> inner;
    private final Inflater inflater = new Inflater(true); // raw DEFLATE blocks
    private final CRC32 crc32 = new CRC32();

    private final byte[] out = new byte[OUT_BUF];
    private final Queue<Byte> trailerBuf = new ArrayDeque<Byte>(8);

    private final Queue<Byte> hdrFixed = new ArrayDeque<Byte>(10);
    private int flg = 0;
    private int extraRemaining = -1;
    private boolean wantHdrCrc = false;
    private int hdrCrcCalc = 0;   // incremental CRC-16

    private boolean headerDone = false;
    private long uncompressed = 0;

    /* ---------- completion plumbing ---------- */
    private FutureCallback<T> cb;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public GzipAsyncEntityConsumer(final AsyncEntityConsumer<T> inner) {
        this.inner = Args.notNull(inner, "inner");
    }

    /* ==================================================================== */
    /* life-cycle                                                           */
    /* ==================================================================== */

    @Override
    public void streamStart(final EntityDetails entityDetails,
                            final FutureCallback<T> resultCb)
            throws HttpException, IOException {

        if (entityDetails.getContentEncoding() != null
                && !"gzip".equalsIgnoreCase(entityDetails.getContentEncoding())) {
            throw new HttpException("Unsupported content-encoding: "
                    + entityDetails.getContentEncoding());
        }
        this.cb = resultCb;
        inner.streamStart(entityDetails, resultCb);
    }

    @Override
    public void updateCapacity(final CapacityChannel channel) throws IOException {
        channel.update(Integer.MAX_VALUE);
        inner.updateCapacity(channel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        try {
            while (src.hasRemaining()) {

                if (!headerDone) {
                    if (!parseHeader(src)) {
                        return;                            // need more bytes
                    }
                    continue;                             // fall through
                }

                final int n = src.remaining();
                final byte[] chunk = new byte[n];
                src.get(chunk);

                inflater.setInput(chunk);
                inflateLoop();

                for (int i = Math.max(0, n - 8); i < n; i++) {
                    trailerBuf.add(chunk[i]);
                    if (trailerBuf.size() > 8) trailerBuf.remove();
                }
            }
        } catch (final DataFormatException ex) {
            throw new IOException("Corrupt GZIP stream", ex);
        }
    }

    /* -------------------------------------------------------------------- */
    /* header parser (streaming, state-machine)                             */
    /* -------------------------------------------------------------------- */
    private boolean parseHeader(final ByteBuffer src) {

        while (src.hasRemaining() && !headerDone) {

            /* 0----- consume the mandatory 10-byte fixed header */
            if (hdrFixed.size() < 10) {
                final byte b = src.get();
                hdrFixed.add(b);
                updateHdrCrc(b);

                if (hdrFixed.size() == 10) {
                    final byte[] h = new byte[10];
                    int i = 0;
                    for (final Byte bb : hdrFixed) h[i++] = bb;

                    if (h[0] != (byte) 0x1F || h[1] != (byte) 0x8B || h[2] != 8) {
                        throw new IllegalStateException("Not a GZIP header");
                    }
                    flg = h[3] & 0xFF;
                    if ((flg & 0xE0) != 0) {              // bits 5-7 reserved
                        throw new IllegalStateException("Reserved GZIP flag bits set: " + flg);
                    }
                    wantHdrCrc = (flg & FHCRC) != 0;
                }
                continue;
            }

            if ((flg & FEXTRA) != 0) {
                if (extraRemaining < 0) {                 // read length (2 B LE)
                    if (src.remaining() < 2) {
                        return false;
                    }
                    final int lo = src.get() & 0xFF;
                    updateHdrCrc((byte) lo);
                    final int hi = src.get() & 0xFF;
                    updateHdrCrc((byte) hi);
                    extraRemaining = (hi << 8) | lo;
                    if (extraRemaining == 0) {
                        flg &= ~FEXTRA;
                    }
                    continue;
                }
                final int skip = Math.min(extraRemaining, src.remaining());
                for (int i = 0; i < skip; i++) {
                    updateHdrCrc(src.get());
                }
                extraRemaining -= skip;
                if (extraRemaining == 0) {
                    flg &= ~FEXTRA;
                }
                continue;
            }

            if ((flg & FNAME) != 0) {
                while (src.hasRemaining()) {
                    final byte b = src.get();
                    updateHdrCrc(b);
                    if (b == 0) {
                        flg &= ~FNAME;
                        break;
                    }
                }
                continue;
            }

            if ((flg & FCOMMENT) != 0) {
                while (src.hasRemaining()) {
                    final byte b = src.get();
                    updateHdrCrc(b);
                    if (b == 0) {
                        flg &= ~FCOMMENT;
                        break;
                    }
                }
                continue;
            }

            if (wantHdrCrc) {
                if (src.remaining() < 2) {
                    return false;
                }
                final byte lo = src.get();
                final byte hi = src.get();
                final int expect = ((hi & 0xFF) << 8) | (lo & 0xFF);
                if ((hdrCrcCalc & 0xFFFF) != expect) {
                    throw new IllegalStateException("Header CRC16 mismatch");
                }
                wantHdrCrc = false;                       // consumed
                continue;
            }

            /* header complete */
            headerDone = true;
        }
        return headerDone;
    }

    private void updateHdrCrc(final byte b) {
        if (!wantHdrCrc) return;
        hdrCrcCalc ^= b & 0xFF;
        for (int k = 0; k < 8; k++) {
            hdrCrcCalc = (hdrCrcCalc & 1) != 0
                    ? (hdrCrcCalc >>> 1) ^ 0xA001
                    : hdrCrcCalc >>> 1;
        }
    }


    /* -------------------------------------------------------------------- */
    /* inflate helper                                                       */
    /* -------------------------------------------------------------------- */
    private void inflateLoop() throws IOException, DataFormatException {
        int n;
        while ((n = inflater.inflate(out)) > 0) {
            crc32.update(out, 0, n);
            uncompressed += n;
            inner.consume(ByteBuffer.wrap(out, 0, n));
        }
    }

    /* ==================================================================== */
    /* end-of-stream                                                        */
    /* ==================================================================== */

    @Override
    public void streamEnd(final List<? extends Header> trailers)
            throws HttpException, IOException {

        try {
            inflateLoop();
        } catch (final DataFormatException ex) {
            throw new IOException("Corrupt GZIP stream", ex);
        }
        if (trailerBuf.size() != 8) {
            throw new IOException("Truncated GZIP trailer");
        }

        final byte[] tail = new byte[8];
        for (int i = 0; i < 8; i++) {
            tail[i] = trailerBuf.remove();
        }

        final long crcVal = ((tail[3] & 0xFFL) << 24) | ((tail[2] & 0xFFL) << 16)
                | ((tail[1] & 0xFFL) << 8) | (tail[0] & 0xFFL);
        final long isz = ((tail[7] & 0xFFL) << 24) | ((tail[6] & 0xFFL) << 16)
                | ((tail[5] & 0xFFL) << 8) | (tail[4] & 0xFFL);

        if (crcVal != crc32.getValue()) {
            throw new IOException("CRC-32 mismatch");
        }
        if (isz != (uncompressed & 0xFFFFFFFFL)) {
            throw new IOException("ISIZE mismatch");
        }

        inner.streamEnd(trailers);
        completed.set(true);
        if (cb != null) cb.completed(inner.getContent());
    }

    @Override
    public T getContent() {
        return inner.getContent();
    }

    @Override
    public void failed(final Exception cause) {
        if (completed.compareAndSet(false, true) && cb != null) {
            cb.failed(cause);
        }
        inner.failed(cause);
    }

    @Override
    public void releaseResources() {
        inner.releaseResources();
    }
}
