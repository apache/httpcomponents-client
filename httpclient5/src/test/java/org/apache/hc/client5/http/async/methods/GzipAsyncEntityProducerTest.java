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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.junit.jupiter.api.Test;

class GzipAsyncEntityProducerTest {

    private static final class CollectingChannel implements DataStreamChannel {
        private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        private final int maxChunk;

        CollectingChannel(final int maxChunk) {
            this.maxChunk = maxChunk;
        }

        @Override
        public void requestOutput() { /* not used in unit test */ }

        @Override
        public int write(final ByteBuffer src) {
            final int len = Math.min(src.remaining(), maxChunk);
            final byte[] tmp = new byte[len];
            src.get(tmp);
            sink.write(tmp, 0, len);
            return len;
        }

        @Override
        public void endStream() {
        }

        @Override
        public void endStream(final List<? extends Header> trailers) {
        }

        byte[] toByteArray() {
            return sink.toByteArray();
        }
    }

    private static String buildLargeText() {
        final String unit = "GZIP round-trip ✓ ";
        final StringBuilder sb = new StringBuilder(unit.length() * 2000);
        for (int i = 0; i < 2000; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }

    @Test
    void roundTrip() throws Exception {

        final String original = buildLargeText();

        final AsyncEntityProducer raw =
                new StringAsyncEntityProducer(original, ContentType.TEXT_PLAIN);
        final GzipAsyncEntityProducer gzip =
                new GzipAsyncEntityProducer(raw);

        final CollectingChannel channel = new CollectingChannel(1024);

        /* drive the producer until it reports no more data */
        while (gzip.available() > 0) {
            gzip.produce(channel);
        }

        final byte[] wire = channel.toByteArray();
        assertTrue(wire.length > 0, "producer wrote no data");

        /* inflate using JDK's GZIPInputStream to verify correctness */
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[4096];
        final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(wire));
        int n;
        while ((n = gis.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        gis.close();

        final String roundTrip = out.toString(StandardCharsets.UTF_8.name());
        assertEquals(original, roundTrip, "round-tripped text differs");
        assertEquals("gzip", gzip.getContentEncoding(), "wrong Content-Encoding");
        assertTrue(gzip.isChunked(), "producer must be chunked");
    }
}
