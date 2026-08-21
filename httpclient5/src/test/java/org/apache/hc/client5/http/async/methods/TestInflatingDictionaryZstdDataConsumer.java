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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.github.luben.zstd.ZstdCompressCtx;

import org.apache.hc.client5.http.entity.compress.BasicCompressionDictionaryStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.impl.ZstdRuntime;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TestInflatingDictionaryZstdDataConsumer {

    private static final byte[] MAGIC = {
            0x5e, 0x2a, 0x4d, 0x18, 0x20, 0x00, 0x00, 0x00
    };

    private static final int HASH_LENGTH = 32;

    /**
     * Fake downstream consumer that accumulates all consumed bytes.
     */
    private static final class AccumulatingConsumer implements AsyncDataConsumer {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private boolean ended;

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            while (src.hasRemaining()) {
                out.write(src.get());
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            ended = true;
        }

        @Override
        public void releaseResources() {
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }

        boolean isEnded() {
            return ended;
        }
    }

    private static CompressionDictionary dictionaryOf(final byte[] content) {
        return new CompressionDictionary(
                content,
                URI.create("https://example.com/"),
                "/*",
                "dict-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2099-01-01T00:00:00Z"));
    }

    private static byte[] concat(final byte[]... parts) {
        int total = 0;
        for (final byte[] part : parts) {
            total += part.length;
        }
        final byte[] result = new byte[total];
        int offset = 0;
        for (final byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static byte[] compress(final byte[] dictionaryContent, final byte[] payload) {
        try (ZstdCompressCtx cctx = new ZstdCompressCtx()) {
            cctx.loadDict(dictionaryContent);
            return cctx.compress(payload);
        }
    }

    @Test
    void invalidMagicRaisesIOException() {
        Assumptions.assumeTrue(ZstdRuntime.available());

        final AccumulatingConsumer downstream = new AccumulatingConsumer();
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryZstdDataConsumer consumer =
                new InflatingDictionaryZstdDataConsumer(downstream, store);

        final byte[] header = new byte[MAGIC.length + HASH_LENGTH];
        // deliberately wrong first byte
        header[0] = 0x00;

        final ByteBuffer src = ByteBuffer.wrap(header);
        final IOException ex = Assertions.assertThrows(IOException.class, () -> consumer.consume(src));
        Assertions.assertTrue(ex.getMessage().contains("Invalid DCZ stream header"), ex.getMessage());

        consumer.releaseResources();
    }

    @Test
    void unknownDictionaryHashRaisesIOException() {
        Assumptions.assumeTrue(ZstdRuntime.available());

        final AccumulatingConsumer downstream = new AccumulatingConsumer();
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryZstdDataConsumer consumer =
                new InflatingDictionaryZstdDataConsumer(downstream, store);

        final byte[] hash = new byte[HASH_LENGTH];
        for (int i = 0; i < hash.length; i++) {
            hash[i] = (byte) i;
        }
        final byte[] header = concat(MAGIC, hash);

        final ByteBuffer src = ByteBuffer.wrap(header);
        final IOException ex = Assertions.assertThrows(IOException.class, () -> consumer.consume(src));
        Assertions.assertEquals("Compression dictionary not available", ex.getMessage());

        consumer.releaseResources();
    }

    @Test
    void truncatedHeaderThenStreamEndRaisesIOException() {
        Assumptions.assumeTrue(ZstdRuntime.available());

        final AccumulatingConsumer downstream = new AccumulatingConsumer();
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryZstdDataConsumer consumer =
                new InflatingDictionaryZstdDataConsumer(downstream, store);

        // feed only part of the magic; header stays incomplete, so not initialized
        final ByteBuffer src = ByteBuffer.wrap(new byte[] {MAGIC[0], MAGIC[1], MAGIC[2]});
        Assertions.assertDoesNotThrow(() -> consumer.consume(src));

        final IOException ex = Assertions.assertThrows(IOException.class,
                () -> consumer.streamEnd(Collections.<Header>emptyList()));
        Assertions.assertEquals("Truncated DCZ stream header", ex.getMessage());

        consumer.releaseResources();
    }

    @Test
    void headerSplitAcrossBuffersRaisesUnknownDictionary() {
        Assumptions.assumeTrue(ZstdRuntime.available());

        final AccumulatingConsumer downstream = new AccumulatingConsumer();
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryZstdDataConsumer consumer =
                new InflatingDictionaryZstdDataConsumer(downstream, store);

        final byte[] hash = new byte[HASH_LENGTH];
        final byte[] header = concat(MAGIC, hash);

        // split header across two ByteBuffers; the valid MAGIC still validates,
        // then the (all-zero, absent) dictionary hash is rejected.
        final ByteBuffer first = ByteBuffer.wrap(header, 0, 5);
        final ByteBuffer second = ByteBuffer.wrap(header, 5, header.length - 5);

        Assertions.assertDoesNotThrow(() -> consumer.consume(first));
        final IOException ex = Assertions.assertThrows(IOException.class, () -> consumer.consume(second));
        Assertions.assertEquals("Compression dictionary not available", ex.getMessage());

        consumer.releaseResources();
    }

    @Test
    void roundTripSingleBuffer() throws Exception {
        Assumptions.assumeTrue(ZstdRuntime.available());

        final byte[] dictionaryContent = "the quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        final byte[] payload = ("the quick brown fox jumps over the lazy dog "
                + "and then the quick brown fox runs away").getBytes(StandardCharsets.UTF_8);

        final CompressionDictionary dictionary = dictionaryOf(dictionaryContent);
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        store.add(dictionary);

        final byte[] frame = compress(dictionaryContent, payload);
        final byte[] stream = concat(MAGIC, dictionary.getSha256(), frame);

        final AccumulatingConsumer downstream = new AccumulatingConsumer();
        final InflatingDictionaryZstdDataConsumer consumer =
                new InflatingDictionaryZstdDataConsumer(downstream, store);

        consumer.consume(ByteBuffer.wrap(stream));
        consumer.streamEnd(Collections.<Header>emptyList());

        Assertions.assertArrayEquals(payload, downstream.toByteArray());
        Assertions.assertTrue(downstream.isEnded());

        consumer.releaseResources();
    }

    @Test
    void roundTripSplitAcrossManyBuffers() throws Exception {
        Assumptions.assumeTrue(ZstdRuntime.available());

        final byte[] dictionaryContent = "dictionary payload sample content".getBytes(StandardCharsets.UTF_8);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("dictionary payload sample content line ").append(i).append('\n');
        }
        final byte[] payload = sb.toString().getBytes(StandardCharsets.UTF_8);

        final CompressionDictionary dictionary = dictionaryOf(dictionaryContent);
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        store.add(dictionary);

        final byte[] frame = compress(dictionaryContent, payload);
        final byte[] stream = concat(MAGIC, dictionary.getSha256(), frame);

        final AccumulatingConsumer downstream = new AccumulatingConsumer();
        final InflatingDictionaryZstdDataConsumer consumer =
                new InflatingDictionaryZstdDataConsumer(downstream, store);

        // feed one byte at a time to exercise header/frame splitting
        for (int i = 0; i < stream.length; i++) {
            consumer.consume(ByteBuffer.wrap(stream, i, 1));
        }
        consumer.streamEnd(Collections.<Header>emptyList());

        Assertions.assertArrayEquals(payload, downstream.toByteArray());
        Assertions.assertTrue(downstream.isEnded());

        consumer.releaseResources();
    }
}
