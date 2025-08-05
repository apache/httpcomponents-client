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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for GZIP inflate / deflate helpers
 * that compile and run on plain Java 8.
 */
public class GzipRoundTripTest {

    private static final String TEXT =
            "Hello GZIP 🚀 – こんにちは世界 – Bonjour le monde!";

    /* ---------------- collector that just stores all bytes ------------- */

    private static final class Collector implements AsyncDataConsumer {

        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        @Override
        public void updateCapacity(final CapacityChannel c) throws IOException {
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            final byte[] tmp = new byte[src.remaining()];
            src.get(tmp);
            buf.write(tmp);
        }

        @Override
        public void streamEnd(final List<? extends Header> t) throws IOException {
        }


        @Override
        public void releaseResources() {
        }

        byte[] toByteArray() {
            return buf.toByteArray();
        }
    }

    /* ------------------------------------------------------------------ */

    @Test
    void gzipDecompress() throws Exception {

        /* compressed reference data */
        final ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (final GZIPOutputStream gos = new GZIPOutputStream(gz)) {
            gos.write(TEXT.getBytes(StandardCharsets.UTF_8));
        }

        final Collector inner = new Collector();
        final InflatingGzipDataConsumer gunzip = new InflatingGzipDataConsumer(inner);

        /* feed entire stream in one go */
        gunzip.consume(ByteBuffer.wrap(gz.toByteArray()));
        gunzip.streamEnd(Collections.<Header>emptyList());   // notify EOF

        final String out = new String(inner.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(TEXT, out);
    }

    /* ------------------------------------------------------------------ */

    @Test
    void gzipCompress() throws Exception {

        final AsyncEntityProducer json =
                new StringAsyncEntityProducer(
                        "\"" + TEXT + "\"",
                        ContentType.APPLICATION_JSON);

        final AsyncEntityProducer gzipProd = new DeflatingGzipEntityProducer(json);

        /* collect bytes “on the wire” */
        final ByteArrayOutputStream wire = new ByteArrayOutputStream();
        final CountDownLatch done = new CountDownLatch(1);

        gzipProd.produce(new DataStreamChannel() {
            @Override
            public void requestOutput() {
            }

            @Override
            public int write(final ByteBuffer src) {
                final byte[] tmp = new byte[src.remaining()];
                src.get(tmp);
                wire.write(tmp, 0, tmp.length);
                return tmp.length;
            }

            @Override
            public void endStream() {
                done.countDown();
            }

            @Override
            public void endStream(final List<? extends Header> t) {
                endStream();
            }
        });

        if (!done.await(2, TimeUnit.SECONDS)) {
            fail("producer timed-out");
        }

        /* verify round-trip */
        final ByteArrayInputStream bin = new ByteArrayInputStream(wire.toByteArray());
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final byte[] buf = new byte[4096];
        try (final GZIPInputStream gin = new GZIPInputStream(bin)) {
            int n;
            while ((n = gin.read(buf)) != -1) {
                bout.write(buf, 0, n);
            }
        }
        final String roundTrip = bout.toString(StandardCharsets.UTF_8.name());
        assertEquals("\"" + TEXT + "\"", roundTrip);
    }
}
