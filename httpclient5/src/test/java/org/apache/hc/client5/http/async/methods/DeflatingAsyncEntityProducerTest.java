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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.Inflater;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.junit.jupiter.api.Test;

class DeflatingAsyncEntityProducerTest {

    /**
     * DataStreamChannel that accepts at most <code>maxChunk</code> bytes per write.
     */
    private static final class ThrottledChannel implements DataStreamChannel {
        private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        private final int maxChunk;

        ThrottledChannel(final int max) {
            this.maxChunk = max;
        }

        @Override
        public void requestOutput() {
        }

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

        byte[] data() {
            return sink.toByteArray();
        }
    }

    private static String longText() {
        final String seed = "lorem ipsum äëïöü – ";
        final StringBuilder sb = new StringBuilder(seed.length() * 3000);
        for (int i = 0; i < 3000; i++) {
            sb.append(seed);
        }
        return sb.toString();
    }

    @Test
    void roundTrip() throws Exception {
        final String text = longText();

        final AsyncEntityProducer raw =
                new StringAsyncEntityProducer(text, ContentType.TEXT_PLAIN);
        final DeflatingAsyncEntityProducer def =
                new DeflatingAsyncEntityProducer(raw);

        final ThrottledChannel ch = new ThrottledChannel(1024);

        while (def.available() > 0) {
            def.produce(ch);
        }

        final byte[] compressed = ch.data();
        assertTrue(compressed.length > 0);

        // Inflate (raw DEFLATE)
        final Inflater inflater = new Inflater(true);
        inflater.setInput(compressed);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[8192];
        while (!inflater.finished()) {
            final int n = inflater.inflate(buf);
            if (n == 0 && inflater.needsInput()) break;
            out.write(buf, 0, n);
        }
        inflater.end();

        assertEquals(text, out.toString("UTF-8"));
        assertEquals("deflate", def.getContentEncoding());
        assertTrue(def.isChunked());
        assertEquals(-1, def.getContentLength());
    }
}
