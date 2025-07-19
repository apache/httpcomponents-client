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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

class TestCompressingAsyncEntityProducer {


    @Test
    void gzipRoundTrip() throws Exception {
        final String plain = "lorem ipsum äëïöü";

        final AsyncEntityProducer original =
                new StringAsyncEntityProducer(plain, ContentType.TEXT_PLAIN);

        final CompressingAsyncEntityProducer gzip =
                new CompressingAsyncEntityProducer(original, "gzip");

        final ByteArrayOutputStream wire = new ByteArrayOutputStream();
        final DataStreamChannel channel = new BufferingChannel(wire);

        while (gzip.available() > 0) {
            gzip.produce(channel);
        }

        final String roundTrip = inflateUtf8(wire.toByteArray());
        assertEquals(plain, roundTrip);

        assertEquals("gzip", gzip.getContentEncoding());   // meta-check
        final byte[] wireBytes = wire.toByteArray();              // <-- here
        assertEquals(0x1F, wireBytes[0] & 0xFF);          // magic bytes
        assertEquals(0x8B, wireBytes[1] & 0xFF);


    }

    private static String inflateUtf8(final byte[] gz) throws IOException {
        try (final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gz));
             final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            final byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = gis.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class BufferingChannel implements DataStreamChannel {
        private final ByteArrayOutputStream buf;

        BufferingChannel(final ByteArrayOutputStream buf) {
            this.buf = buf;
        }

        @Override
        public void requestOutput() {
        }

        @Override
        public int write(final ByteBuffer src) {
            final byte[] b = new byte[src.remaining()];
            src.get(b);
            buf.write(b, 0, b.length);
            return b.length;
        }

        @Override
        public void endStream() {
        }

        @Override
        public void endStream(final List<? extends Header> t) {
        }
    }
}