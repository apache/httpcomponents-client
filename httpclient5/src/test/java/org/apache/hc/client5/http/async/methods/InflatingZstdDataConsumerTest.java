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


import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import com.github.luben.zstd.Zstd;

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.Test;


class InflatingZstdDataConsumerTest {

    // --- 1) zstd-jni compress -> our consumer inflates ---
    @Test
    void zstdjni_to_consumer_roundtrip() throws Exception {
        final byte[] plain = buildPayload('A', 20_000).getBytes(StandardCharsets.UTF_8);

        final byte[] compressed = Zstd.compress(plain);

        final CollectingConsumer sink = new CollectingConsumer();
        final InflatingZstdDataConsumer infl = new InflatingZstdDataConsumer(sink);

        feedInChunks(infl, compressed);
        infl.streamEnd(Collections.<Header>emptyList());
        infl.releaseResources();

        assertArrayEquals(plain, sink.toByteArray());
    }

    // --- 2) Apache Commons Compress -> our consumer inflates ---
    @Test
    void commons_to_consumer_roundtrip() throws Exception {
        final byte[] plain = buildPayload('B', 20_000).getBytes(StandardCharsets.UTF_8);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZstdCompressorOutputStream zout = new ZstdCompressorOutputStream(baos)) {
            zout.write(plain);
        }
        final byte[] compressed = baos.toByteArray();

        final CollectingConsumer sink = new CollectingConsumer();
        final InflatingZstdDataConsumer infl = new InflatingZstdDataConsumer(sink);

        feedInChunks(infl, compressed);
        infl.streamEnd(Collections.<Header>emptyList());
        infl.releaseResources();

        assertArrayEquals(plain, sink.toByteArray());
    }

    // ---- helpers ----

    private static void feedInChunks(final InflatingZstdDataConsumer infl, final byte[] data) throws Exception {
        int off = 0;
        final int[] chunks = new int[]{1, 3, 7, 19, 64, 257, 1024, 4096, 16384, Integer.MAX_VALUE};
        for (final int size : chunks) {
            if (off >= data.length) break;
            final int n = Math.min(size, data.length - off);
            infl.consume(ByteBuffer.wrap(data, off, n));
            off += n;
        }
    }

    private static String buildPayload(final char ch, final int lines) {
        final StringBuilder sb = new StringBuilder(lines * 48);
        for (int i = 0; i < lines; i++) {
            sb.append(ch).append(i).append(" The quick brown fox jumps over the lazy dog.\n");
        }
        return sb.toString();
    }

    /**
     * Downstream that collects decompressed bytes.
     */
    static final class CollectingConsumer implements AsyncDataConsumer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        @Override
        public void updateCapacity(final CapacityChannel c) { /* no-op */ }

        @Override
        public void consume(final ByteBuffer src) {
            final int n = src.remaining();
            final byte[] tmp = new byte[n];
            src.get(tmp);
            out.write(tmp, 0, n);
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) {
        }

        @Override
        public void releaseResources() {
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
