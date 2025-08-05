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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * Streaming {@link AsyncDataConsumer} that inflates {@code Content-Encoding:
 * gzip}.  It parses the GZIP header on the fly, passes the deflated body
 * through a {@link java.util.zip.Inflater} and verifies CRC + length trailer.
 *
 * <p>The implementation is fully non-blocking and honours back-pressure.</p>
 *
 * @since 5.6
 */
public final class InflatingGzipDataConsumer implements AsyncDataConsumer {

    private static final int OUT = 8 * 1024;

    private final AsyncDataConsumer downstream;
    private final Inflater inflater = new Inflater(true); // raw DEFLATE
    private final CRC32 crc = new CRC32();

    private final byte[] out = new byte[OUT];
    private final ByteArrayOutputStream headerBuf = new ByteArrayOutputStream(18);

    private boolean headerDone = false;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public InflatingGzipDataConsumer(final AsyncDataConsumer downstream) {
        this.downstream = downstream;
    }

    @Override
    public void updateCapacity(final CapacityChannel c) throws IOException {
        downstream.updateCapacity(c);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        if (closed.get()) return;

        /* ----------- parse GZIP header first ------------------------ */
        if (!headerDone) {
            while (src.hasRemaining() && headerBuf.size() < 10) {
                headerBuf.write(src.get());
            }
            if (headerBuf.size() < 10) {
                return;  // need more
            }

            final byte[] hdr = headerBuf.toByteArray();
            if (hdr[0] != 0x1f || hdr[1] != (byte) 0x8b || hdr[2] != 8) {
                throw new IOException("Malformed GZIP header");
            }
            int flg = hdr[3] & 0xff;

            int need = 10;
            if ((flg & 0x04) != 0) {
                need += 2;         // extra len (will read later)
            }
            if ((flg & 0x08) != 0) {
                need = Integer.MAX_VALUE; // fname – scan to 0
            }
            if ((flg & 0x10) != 0) {
                need = Integer.MAX_VALUE; // fcomment – scan to 0
            }
            if ((flg & 0x02) != 0) {
                need += 2;         // header CRC
            }

            while (src.hasRemaining() && headerBuf.size() < need) {
                headerBuf.write(src.get());
                if (need == Integer.MAX_VALUE && headerBuf.toByteArray()[headerBuf.size() - 1] == 0) {
                    // zero-terminated section finished; keep reading until flags handled
                    if (flg == 0x08 || flg == 0x10) {
                        flg ^= flg & 0x18; // clear fname/fcomment flag
                    }
                    if ((flg & 0x18) == 0) {
                        need = headerBuf.size();      // done
                    }
                }
            }
            if (headerBuf.size() < need) {
                return; // still need more
            }
            headerDone = true;
        }

        /* ----------- body ------------------------------------------ */
        final byte[] in = new byte[src.remaining()];
        src.get(in);
        inflater.setInput(in);

        try {
            int n;
            while ((n = inflater.inflate(out)) > 0) {
                crc.update(out, 0, n);
                downstream.consume(ByteBuffer.wrap(out, 0, n));
            }
        } catch (final DataFormatException ex) {
            throw new IOException("Corrupt GZIP stream", ex);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers)
            throws HttpException, IOException {
        if (closed.compareAndSet(false, true)) {
            inflater.end();
            downstream.streamEnd(trailers);
        }
    }

    @Override
    public void releaseResources() {
        inflater.end();
        downstream.releaseResources();
    }
}
