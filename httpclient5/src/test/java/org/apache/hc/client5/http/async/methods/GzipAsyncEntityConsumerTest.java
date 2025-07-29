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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GzipAsyncEntityConsumerTest {

    private static String ORIGINAL;

    @BeforeAll
    static void build() {
        final String seed = "inflate me 🎉 ";
        final StringBuilder sb = new StringBuilder(seed.length() * 3000);
        for (int i = 0; i < 3000; i++) {
            sb.append(seed);
        }
        ORIGINAL = sb.toString();
    }

    private static byte[] gzip(final byte[] data) throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final GZIPOutputStream gos = new GZIPOutputStream(out);
        gos.write(data);
        gos.close();
        return out.toByteArray();
    }

    /**
     * Build a gzip frame with optional FEXTRA / FNAME / FCOMMENT / FHCRC flags.
     */
    private static byte[] customGzip(final byte[] data,
                                     final boolean extra,
                                     final boolean name,
                                     final boolean comment,
                                     final boolean hcrc) throws Exception {

        /* --------- compress payload (raw deflate) --------- */
        final Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        def.setInput(data);
        def.finish();
        final byte[] buf = new byte[8192];
        final java.io.ByteArrayOutputStream deflated = new java.io.ByteArrayOutputStream();
        while (!def.finished()) {
            final int n = def.deflate(buf);
            deflated.write(buf, 0, n);
        }
        def.end();
        final byte[] deflatedBytes = deflated.toByteArray();

        /* --------- construct header --------- */
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);            // trailer CRC-32

        final java.io.ByteArrayOutputStream hdr = new java.io.ByteArrayOutputStream();
        hdr.write(0x1F);
        hdr.write(0x8B);               // ID1, ID2
        hdr.write(8);                                   // CM = deflate
        int flg = 0;
        if (extra) {
            flg |= 4;
        }
        if (name) {
            flg |= 8;
        }
        if (comment) {
            flg |= 16;
        }
        if (hcrc) {
            flg |= 2;
        }
        hdr.write(flg);                                 // FLG
        hdr.write(new byte[]{0, 0, 0, 0});                // MTIME
        hdr.write(0);                                   // XFL
        hdr.write(0xFF);                                // OS

        if (extra) {
            hdr.write(4);
            hdr.write(0);                 // XLEN = 4
            hdr.write(new byte[]{1, 2, 3, 4});
        }
        if (name) {
            hdr.write("file.txt".getBytes(StandardCharsets.ISO_8859_1));
            hdr.write(0);
        }
        if (comment) {
            hdr.write("comment".getBytes(StandardCharsets.ISO_8859_1));
            hdr.write(0);
        }
        byte[] hdrBytes = hdr.toByteArray();

        if (hcrc) {
            /* ---------- CRC-16 over *optional* part only (bytes 10 .. n-1) ---------- */
            int crc16 = 0;
            for (int i = 10; i < hdrBytes.length; i++) {    // skip fixed 10-byte header
                final int b = hdrBytes[i] & 0xFF;
                crc16 ^= b;
                for (int k = 0; k < 8; k++) {
                    crc16 = (crc16 & 1) != 0 ? (crc16 >>> 1) ^ 0xA001 : (crc16 >>> 1);
                }
            }
            hdr.write(crc16 & 0xFF);
            hdr.write((crc16 >>> 8) & 0xFF);
            hdrBytes = hdr.toByteArray();                   // final header incl. CRC-16
        }

        out.write(hdrBytes);
        out.write(deflatedBytes);

        /* --------- trailer --------- */
        writeIntLE(out, (int) crc32.getValue());
        writeIntLE(out, data.length);

        return out.toByteArray();
    }

    private static void writeIntLE(final java.io.ByteArrayOutputStream out, final int v) {
        out.write(v);
        out.write(v >>> 8);
        out.write(v >>> 16);
        out.write(v >>> 24);
    }

    private static class BytesCollector implements AsyncEntityConsumer<String> {
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();

        @Override
        public void streamStart(final EntityDetails ed, final FutureCallback<String> cb) {
        }

        @Override
        public void updateCapacity(final CapacityChannel c) {
        }

        @Override
        public void consume(final ByteBuffer src) {
            final byte[] b = new byte[src.remaining()];
            src.get(b);
            buf.write(b, 0, b.length);
        }

        @Override
        public void streamEnd(final List<? extends Header> t) {
        }

        @Override
        public void failed(final Exception cause) {
            throw new RuntimeException(cause);
        }

        @Override
        public void releaseResources() {
        }

        @Override
        public String getContent() {
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void runInflateTest(final byte[] wire) throws Exception {
        final BytesCollector inner = new BytesCollector();
        final GzipAsyncEntityConsumer<String> inflating = new GzipAsyncEntityConsumer<>(inner);

        final CountDownLatch done = new CountDownLatch(1);
        final FutureCallback<String> cb = new FutureCallback<String>() {
            @Override
            public void completed(final String r) {
                done.countDown();
            }

            @Override
            public void failed(final Exception ex) {
                fail(ex);
            }

            @Override
            public void cancelled() {
                fail("cancelled");
            }
        };

        inflating.streamStart(new EntityDetails() {
            public long getContentLength() {
                return wire.length;
            }

            public String getContentType() {
                return "text/plain";
            }

            public String getContentEncoding() {
                return "gzip";
            }

            public boolean isChunked() {
                return false;
            }

            public Set<String> getTrailerNames() {
                return new HashSet<>();
            }
        }, cb);

        for (int off = 0; off < wire.length; off += 777) {
            final int n = Math.min(777, wire.length - off);
            inflating.consume(ByteBuffer.wrap(wire, off, n));
        }
        inflating.streamEnd(Collections.<Header>emptyList());

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(ORIGINAL, inner.getContent());
    }

    @Test
    void fullInflate() throws Exception {
        runInflateTest(gzip(ORIGINAL.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void headerExtra() throws Exception {
        runInflateTest(customGzip(ORIGINAL.getBytes(StandardCharsets.UTF_8),
                true, false, false, false));
    }

    @Test
    void headerName() throws Exception {
        runInflateTest(customGzip(ORIGINAL.getBytes(StandardCharsets.UTF_8),
                false, true, false, false));
    }

    @Test
    void headerComment() throws Exception {
        runInflateTest(customGzip(ORIGINAL.getBytes(StandardCharsets.UTF_8),
                false, false, true, false));
    }

    @Test
    void headerCrc16() throws Exception {
        runInflateTest(customGzip(ORIGINAL.getBytes(StandardCharsets.UTF_8),
                true, true, true, true));
    }
}
