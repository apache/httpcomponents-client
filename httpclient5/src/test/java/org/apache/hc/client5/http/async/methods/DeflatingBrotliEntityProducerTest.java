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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.aayushatharva.brotli4j.encoder.Encoder;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeflatingBrotliEntityProducerTest {

    private static String longText() {
        final String seed = "lorem ipsum äëïöü – ";
        final StringBuilder sb = new StringBuilder(seed.length() * 3000);
        for (int i = 0; i < 3000; i++) {
            sb.append(seed);
        }
        return sb.toString();
    }

    /**
     * DataStreamChannel that accepts at most maxChunk bytes per write.
     */
    private static final class ThrottledChannel implements DataStreamChannel {
        private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        private final int maxChunk;
        private boolean ended;

        ThrottledChannel(final int maxChunk) {
            this.maxChunk = maxChunk;
        }

        @Override
        public void requestOutput() { /* no-op for test */ }

        @Override
        public int write(final ByteBuffer src) {
            final int len = Math.min(src.remaining(), maxChunk);
            if (len <= 0) return 0;
            final byte[] tmp = new byte[len];
            src.get(tmp);
            sink.write(tmp, 0, len);
            return len;
        }

        @Override
        public void endStream() {
            // Core interface in some versions; delegate to list variant for safety
            endStream(Collections.emptyList());
        }

        @Override
        public void endStream(final List<? extends Header> trailers) {
            ended = true;
        }

        byte[] data() {
            return sink.toByteArray();
        }

        boolean ended() {
            return ended;
        }
    }

    @BeforeAll
    static void init() {
        Brotli4jLoader.ensureAvailability();
    }

    @Test
    void roundTrip() throws Exception {
        final String text = longText();

        final byte[] payload = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final AsyncEntityProducer raw =
                new org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer(
                        payload,
                        org.apache.hc.core5.http.ContentType.TEXT_PLAIN.withCharset(java.nio.charset.StandardCharsets.UTF_8)
                );
        final DeflatingBrotliEntityProducer br =
                new DeflatingBrotliEntityProducer(raw, 5, 22, Encoder.Mode.TEXT);

        final ThrottledChannel ch = new ThrottledChannel(1024);

        // drive until producer reports no more work
        while (br.available() > 0) {
            br.produce(ch);
        }

        final byte[] compressed = ch.data();
        assertTrue(compressed.length > 0, "no compressed bytes were produced");

        // Decompress using brotli4j
        final DirectDecompress dd = Decoder.decompress(compressed);
        final String decoded = new String(dd.getDecompressedData(), StandardCharsets.UTF_8);

        assertEquals(text, decoded);
        assertEquals("br", br.getContentEncoding());
        assertTrue(br.isChunked());
        assertEquals(-1, br.getContentLength());
        assertTrue(ch.ended(), "stream was not ended");
    }
}
