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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;

import org.apache.hc.client5.http.entity.compress.BasicCompressionDictionaryStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.junit.jupiter.api.Test;

/**
 * Native-free unit tests for {@link InflatingDictionaryBrotliDataConsumer}.
 * <p>
 * These tests exercise only the header-parsing and dictionary-lookup logic,
 * all of which runs before the lazy Brotli {@code DecoderJNI.Wrapper} is
 * created, so no native Brotli library is required.
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
        final CompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, store);

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
        final CompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, store);

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
    void unknownDictionaryThrowsFromConsume() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, store);

        // Valid MAGIC, but the store is empty so getByHash returns null.
        final byte[] hash = new byte[HASH_LENGTH];
        for (int i = 0; i < HASH_LENGTH; i++) {
            hash[i] = (byte) i;
        }
        final ByteBuffer src = ByteBuffer.wrap(header(MAGIC, hash));

        final IOException ex = assertThrows(IOException.class, () -> consumer.consume(src));
        assertTrue(ex.getMessage().contains("Compression dictionary not available"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void unknownDictionaryThrowsWhenHeaderSplitAcrossTwoBuffers() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, store);

        final byte[] hash = new byte[HASH_LENGTH];
        final byte[] full = header(MAGIC, hash);

        final ByteBuffer first = ByteBuffer.wrap(full, 0, 20).slice();
        assertDoesNotThrowConsume(consumer, first);

        final ByteBuffer second = ByteBuffer.wrap(full, 20, full.length - 20).slice();
        final IOException ex = assertThrows(IOException.class, () -> consumer.consume(second));
        assertTrue(ex.getMessage().contains("Compression dictionary not available"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void knownHashPresentAndMatchingPassesLookupAndReachesDecoderInit() {
        // A real store keyed by the dictionary's own SHA-256; the header hash
        // matches, so the lookup succeeds and control proceeds to lazy decoder
        // creation. Without the native library that step throws, but the lookup
        // guard itself must be cleared (message must NOT be about availability).
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary dict = dictionary(new byte[]{'h', 'e', 'l', 'l', 'o'});
        store.add(dict);

        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, store);

        final ByteBuffer src = ByteBuffer.wrap(header(MAGIC, dict.getSha256()));

        try {
            consumer.consume(src);
            // If the native library is present, header consumed without error.
        } catch (final IOException ex) {
            // If native init fails, it must not be the availability guard.
            assertTrue(!ex.getMessage().contains("Compression dictionary not available"), ex.getMessage());
        } catch (final Throwable linkError) {
            // UnsatisfiedLinkError / NoClassDefFoundError when native lib absent.
            assertTrue(true);
        }
    }

    @Test
    void hashPresentButNotMatchingThrows() {
        // Store returns a dictionary for any hash, but its SHA-256 differs from
        // the header hash, so matchesHash() fails.
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionary dict = dictionary(new byte[]{1, 2, 3, 4});
        final CompressionDictionaryStore store = mock(CompressionDictionaryStore.class);
        when(store.getByHash(org.mockito.ArgumentMatchers.any())).thenReturn(dict);

        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, store);

        // Header hash of all-zeros will not equal the dictionary's real SHA-256.
        final byte[] hash = new byte[HASH_LENGTH];
        final ByteBuffer src = ByteBuffer.wrap(header(MAGIC, hash));

        final IOException ex = assertThrows(IOException.class, () -> consumer.consume(src));
        assertTrue(ex.getMessage().contains("Compression dictionary not available"), ex.getMessage());
        verifyNoInteractions(downstream);
    }

    @Test
    void truncatedHeaderThenStreamEndThrows() {
        final AsyncDataConsumer downstream = mock(AsyncDataConsumer.class);
        final CompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, store);

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
        final CompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final InflatingDictionaryBrotliDataConsumer consumer =
                new InflatingDictionaryBrotliDataConsumer(downstream, store);

        final IOException ex = assertThrows(IOException.class,
                () -> consumer.streamEnd(Collections.emptyList()));
        assertTrue(ex.getMessage().contains("Truncated DCB stream header"), ex.getMessage());
        verifyNoInteractions(downstream);
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
