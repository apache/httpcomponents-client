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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.aayushatharva.brotli4j.encoder.PreparedDictionary;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InflatingDictionaryBrotliDataConsumer}.
 */
class TestInflatingDictionaryBrotliDataConsumer {

    private static final byte[] MAGIC = {
            (byte) 0xff, 0x44, 0x43, 0x42
    };

    private static final int HASH_LENGTH = 32;
    private static final int HEADER_LENGTH = MAGIC.length + HASH_LENGTH;

    private static CompressionDictionary dictionary(final byte[] content) {
        final Instant storedAt = Instant.parse("2020-01-01T00:00:00Z");
        final Instant validUntil = Instant.parse("2030-01-01T00:00:00Z");
        return new CompressionDictionary(
                content,
                URI.create("https://example.com/dict"),
                "/*",
                "dict-1",
                storedAt,
                validUntil);
    }

    private static byte[] header(final byte[] magic, final byte[] hash) {
        final byte[] out = new byte[HEADER_LENGTH];
        System.arraycopy(magic, 0, out, 0, magic.length);
        System.arraycopy(hash, 0, out, magic.length, HASH_LENGTH);
        return out;
    }

    @Test
    void invalidMagicThrowsFromConsumeSingleBuffer() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dictionary = dictionary(new byte[]{1});
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dictionary);

        final byte[] bogusMagic = {0x00, 0x44, 0x43, 0x42};
        final byte[] hash = new byte[HASH_LENGTH];
        final ByteBuffer src = ByteBuffer.wrap(header(bogusMagic, hash));

        final IOException ex = assertThrows(IOException.class, () -> consumer.consume(src));
        assertTrue(ex.getMessage().contains("Invalid DCB stream header"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void invalidMagicThrowsWhenHeaderSplitAcrossTwoBuffers() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dictionary = dictionary(new byte[]{1});
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dictionary);

        final byte[] bogusMagic = {0x00, 0x44, 0x43, 0x42};
        final byte[] hash = new byte[HASH_LENGTH];
        final byte[] full = header(bogusMagic, hash);

        // First buffer only carries a partial header; must not throw yet.
        final ByteBuffer first = ByteBuffer.wrap(full, 0, 2).slice();
        assertDoesNotThrowConsume(consumer, first);

        // Completing the header triggers validation.
        final ByteBuffer second = ByteBuffer.wrap(full, 2, full.length - 2).slice();
        final IOException ex = assertThrows(IOException.class, () -> consumer.consume(second));
        assertTrue(ex.getMessage().contains("Invalid DCB stream header"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void differentDictionaryHashThrowsFromConsume() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dictionary = dictionary(new byte[]{1});
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dictionary);

        final byte[] hash = new byte[HASH_LENGTH];
        for (int i = 0; i < HASH_LENGTH; i++) {
            hash[i] = (byte) i;
        }
        final ByteBuffer src = ByteBuffer.wrap(header(MAGIC, hash));

        final IOException ex = assertThrows(IOException.class, () -> consumer.consume(src));
        assertTrue(ex.getMessage().contains("does not use the negotiated dictionary"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void differentDictionaryHashThrowsWhenHeaderSplitAcrossTwoBuffers() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dictionary = dictionary(new byte[]{1});
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dictionary);

        final byte[] hash = new byte[HASH_LENGTH];
        final byte[] full = header(MAGIC, hash);

        final ByteBuffer first = ByteBuffer.wrap(full, 0, 20).slice();
        assertDoesNotThrowConsume(consumer, first);

        final ByteBuffer second = ByteBuffer.wrap(full, 20, full.length - 20).slice();
        final IOException ex = assertThrows(IOException.class, () -> consumer.consume(second));
        assertTrue(ex.getMessage().contains("does not use the negotiated dictionary"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void knownHashPresentAndMatchingReachesDecoderInit() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dict = dictionary(new byte[]{'h', 'e', 'l', 'l', 'o'});

        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dict);

        final ByteBuffer src = ByteBuffer.wrap(header(MAGIC, dict.getSha256()));

        try {
            consumer.consume(src);
            // If the native library is present, header consumed without error.
        } catch (final IOException ex) {
            assertTrue(!ex.getMessage().contains("does not use the negotiated dictionary"), ex.getMessage());
        } catch (final Throwable linkError) {
            // UnsatisfiedLinkError / NoClassDefFoundError when native lib absent.
            assertTrue(true);
        }
    }

    @Test
    void hashPresentButNotMatchingThrows() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dict = dictionary(new byte[]{1, 2, 3, 4});

        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dict);

        // Header hash of all-zeros will not equal the dictionary's real SHA-256.
        final byte[] hash = new byte[HASH_LENGTH];
        final ByteBuffer src = ByteBuffer.wrap(header(MAGIC, hash));

        final IOException ex = assertThrows(IOException.class, () -> consumer.consume(src));
        assertTrue(ex.getMessage().contains("does not use the negotiated dictionary"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void truncatedHeaderThenStreamEndThrows() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dictionary = dictionary(new byte[]{1});
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dictionary);

        // Fewer than 36 bytes: header stays incomplete, decoder stays null.
        final byte[] partial = new byte[10];
        System.arraycopy(MAGIC, 0, partial, 0, MAGIC.length);
        final ByteBuffer src = ByteBuffer.wrap(partial);
        assertDoesNotThrowConsume(consumer, src);

        final IOException ex = assertThrows(IOException.class,
                () -> consumer.streamEnd(Collections.emptyList()));
        assertTrue(ex.getMessage().contains("Truncated DCB stream header"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void streamEndWithNoDataThrowsTruncatedHeader() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dictionary = dictionary(new byte[]{1});
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dictionary);

        final IOException ex = assertThrows(IOException.class,
                () -> consumer.streamEnd(Collections.emptyList()));
        assertTrue(ex.getMessage().contains("Truncated DCB stream header"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void roundTripWithSharedDictionary() throws Exception {
        Assumptions.assumeTrue(brotliAvailable(), "Brotli native runtime is unavailable");

        final byte[] dictionaryContent =
                "the quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        final byte[] payload = ("the quick brown fox jumps over the lazy dog "
                + "and then the quick brown fox runs away").getBytes(StandardCharsets.UTF_8);
        final CompressionDictionary dictionary = dictionary(dictionaryContent);
        final byte[] stream = concat(MAGIC, dictionary.getSha256(), compress(dictionaryContent, payload));
        final AccumulatingConsumer downstream = new AccumulatingConsumer();
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, dictionary);

        for (int i = 0; i < stream.length; i++) {
            consumer.consume(ByteBuffer.wrap(stream, i, 1));
        }
        consumer.streamEnd(Collections.<Header>emptyList());

        assertArrayEquals(payload, downstream.toByteArray());
        assertTrue(downstream.ended);
        consumer.releaseResources();
    }

    private static byte[] compress(final byte[] dictionary, final byte[] payload) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(dictionary.length);
        buffer.put(dictionary).flip();
        final PreparedDictionary prepared = Encoder.prepareDictionary(buffer, 0);
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try {
            final Encoder.Parameters parameters = Encoder.Parameters.create(6, 24, Encoder.Mode.TEXT);
            try (BrotliOutputStream out = new BrotliOutputStream(compressed, parameters)) {
                out.attachDictionary(prepared);
                out.write(payload);
            }
            return compressed.toByteArray();
        } finally {
            if (prepared instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) prepared).close();
                } catch (final Exception ex) {
                    throw new IOException("Unable to release Brotli dictionary", ex);
                }
            }
        }
    }

    private static byte[] concat(final byte[]... parts) {
        int length = 0;
        for (final byte[] part : parts) {
            length += part.length;
        }
        final byte[] result = new byte[length];
        int offset = 0;
        for (final byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static boolean brotliAvailable() {
        try {
            Brotli4jLoader.ensureAvailability();
            return true;
        } catch (final Throwable ex) {
            return false;
        }
    }

    private static final class AccumulatingConsumer implements AsyncDataConsumer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private boolean ended;

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(final ByteBuffer src) {
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
    }

    private static void assertDoesNotThrowConsume(
            final InflatingDictionaryBrotliDataConsumer consumer, final ByteBuffer src) {
        try {
            consumer.consume(src);
        } catch (final IOException ex) {
            throw new AssertionError("Unexpected IOException on partial header", ex);
        }
    }
}
