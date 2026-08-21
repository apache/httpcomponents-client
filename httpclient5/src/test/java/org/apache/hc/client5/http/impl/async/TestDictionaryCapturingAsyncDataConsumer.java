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
package org.apache.hc.client5.http.impl.async;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.hc.client5.http.entity.compress.BasicCompressionDictionaryStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestDictionaryCapturingAsyncDataConsumer {

    private static final URI SOURCE = URI.create("https://example.com/resource");
    private static final Instant STORED_AT = Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2026-08-22T10:00:00Z");

    private RecordingDataConsumer downstream;
    private CompressionDictionaryStore store;

    @BeforeEach
    void setUp() {
        downstream = new RecordingDataConsumer();
        store = new BasicCompressionDictionaryStore();
    }

    private static UseAsDictionary rawDirective() throws Exception {
        return UseAsDictionary.parse("match=\"/resource\", id=\"dict-1\"");
    }

    private static UseAsDictionary unsupportedDirective() throws Exception {
        return UseAsDictionary.parse("match=\"/resource\", id=\"dict-1\", type=other");
    }

    private DictionaryCapturingAsyncDataConsumer consumer(final int maxSize) throws Exception {
        return new DictionaryCapturingAsyncDataConsumer(
                downstream, store, SOURCE, rawDirective(), STORED_AT, VALID_UNTIL, maxSize);
    }

    @Test
    void testConstructorRejectsNullDownstream() throws Exception {
        final UseAsDictionary directive = rawDirective();
        assertThrows(NullPointerException.class, () -> new DictionaryCapturingAsyncDataConsumer(
                null, store, SOURCE, directive, STORED_AT, VALID_UNTIL, 1024));
    }

    @Test
    void testConstructorRejectsNullStore() throws Exception {
        final UseAsDictionary directive = rawDirective();
        assertThrows(NullPointerException.class, () -> new DictionaryCapturingAsyncDataConsumer(
                downstream, null, SOURCE, directive, STORED_AT, VALID_UNTIL, 1024));
    }

    @Test
    void testConstructorRejectsNullSource() throws Exception {
        final UseAsDictionary directive = rawDirective();
        assertThrows(NullPointerException.class, () -> new DictionaryCapturingAsyncDataConsumer(
                downstream, store, null, directive, STORED_AT, VALID_UNTIL, 1024));
    }

    @Test
    void testConstructorRejectsNullDirective() {
        assertThrows(NullPointerException.class, () -> new DictionaryCapturingAsyncDataConsumer(
                downstream, store, SOURCE, null, STORED_AT, VALID_UNTIL, 1024));
    }

    @Test
    void testConstructorRejectsNullStoredAt() throws Exception {
        final UseAsDictionary directive = rawDirective();
        assertThrows(NullPointerException.class, () -> new DictionaryCapturingAsyncDataConsumer(
                downstream, store, SOURCE, directive, null, VALID_UNTIL, 1024));
    }

    @Test
    void testConstructorRejectsNullValidUntil() throws Exception {
        final UseAsDictionary directive = rawDirective();
        assertThrows(NullPointerException.class, () -> new DictionaryCapturingAsyncDataConsumer(
                downstream, store, SOURCE, directive, STORED_AT, null, 1024));
    }

    @Test
    void testConstructorRejectsNonPositiveMaxSize() throws Exception {
        final UseAsDictionary directive = rawDirective();
        assertThrows(IllegalArgumentException.class, () -> new DictionaryCapturingAsyncDataConsumer(
                downstream, store, SOURCE, directive, STORED_AT, VALID_UNTIL, 0));
        assertThrows(IllegalArgumentException.class, () -> new DictionaryCapturingAsyncDataConsumer(
                downstream, store, SOURCE, directive, STORED_AT, VALID_UNTIL, -1));
    }

    @Test
    void testUpdateCapacityDelegates() throws Exception {
        final AsyncDataConsumer spy = mock(AsyncDataConsumer.class);
        final DictionaryCapturingAsyncDataConsumer mocked = new DictionaryCapturingAsyncDataConsumer(
                spy, store, SOURCE, rawDirective(), STORED_AT, VALID_UNTIL, 1024);
        final CapacityChannel channel = mock(CapacityChannel.class);
        mocked.updateCapacity(channel);
        verify(spy).updateCapacity(channel);
    }

    @Test
    void testConsumeDelegatesAndCaptures() throws Exception {
        final DictionaryCapturingAsyncDataConsumer c = consumer(1024);
        final byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        final ByteBuffer src = ByteBuffer.wrap(payload);

        c.consume(src);

        assertArrayEquals(payload, downstream.consumedBytes());
        assertEquals(0, src.remaining());
    }

    @Test
    void testStreamEndStoresDictionaryMatchingInputs() throws Exception {
        final DictionaryCapturingAsyncDataConsumer c = consumer(1024);
        final byte[] part1 = "abc".getBytes(StandardCharsets.UTF_8);
        final byte[] part2 = "defgh".getBytes(StandardCharsets.UTF_8);

        c.consume(ByteBuffer.wrap(part1));
        c.consume(ByteBuffer.wrap(part2));
        c.streamEnd(Collections.<Header>emptyList());

        assertTrue(downstream.streamEnded());

        final List<CompressionDictionary> stored = store.getByOrigin(SOURCE);
        assertEquals(1, stored.size());
        final CompressionDictionary dictionary = stored.get(0);
        assertArrayEquals("abcdefgh".getBytes(StandardCharsets.UTF_8), dictionary.getContent());
        assertEquals("/resource", dictionary.getMatch());
        assertEquals("dict-1", dictionary.getId());
        assertEquals(SOURCE, dictionary.getSource());
        assertEquals(STORED_AT, dictionary.getStoredAt());
        assertEquals(VALID_UNTIL, dictionary.getValidUntil());
    }

    @Test
    void testExceedingMaxSizeDiscardsButDelegatesAllData() throws Exception {
        final DictionaryCapturingAsyncDataConsumer c = consumer(4);
        final byte[] part1 = "abc".getBytes(StandardCharsets.UTF_8);
        final byte[] part2 = "defgh".getBytes(StandardCharsets.UTF_8);

        c.consume(ByteBuffer.wrap(part1));
        c.consume(ByteBuffer.wrap(part2));
        c.streamEnd(Collections.<Header>emptyList());

        assertTrue(store.getByOrigin(SOURCE).isEmpty());
        assertArrayEquals("abcdefgh".getBytes(StandardCharsets.UTF_8), downstream.consumedBytes());
        assertTrue(downstream.streamEnded());
    }

    @Test
    void testUnsupportedDirectiveStoresNothing() throws Exception {
        final DictionaryCapturingAsyncDataConsumer c = new DictionaryCapturingAsyncDataConsumer(
                downstream, store, SOURCE, unsupportedDirective(), STORED_AT, VALID_UNTIL, 1024);
        final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);

        c.consume(ByteBuffer.wrap(payload));
        c.streamEnd(Collections.<Header>emptyList());

        assertTrue(store.getByOrigin(SOURCE).isEmpty());
        assertArrayEquals(payload, downstream.consumedBytes());
        assertTrue(downstream.streamEnded());
    }

    @Test
    void testReleaseResourcesDelegatesAndPreventsStore() throws Exception {
        final DictionaryCapturingAsyncDataConsumer c = consumer(1024);
        final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);

        c.consume(ByteBuffer.wrap(payload));
        c.releaseResources();
        c.streamEnd(Collections.<Header>emptyList());

        assertTrue(downstream.released());
        assertTrue(store.getByOrigin(SOURCE).isEmpty());
    }

    /**
     * Fake downstream that drains (advances the position of) the buffer it is given, so the
     * wrapper's {@code src.position()} delta reflects the bytes actually consumed.
     */
    private static final class RecordingDataConsumer implements AsyncDataConsumer {

        private final ByteArrayOutputStream recorded = new ByteArrayOutputStream();
        private final List<CapacityChannel> capacityChannels = new ArrayList<>();
        private boolean streamEnded;
        private boolean released;

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            capacityChannels.add(capacityChannel);
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            while (src.hasRemaining()) {
                recorded.write(src.get());
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            streamEnded = true;
        }

        @Override
        public void releaseResources() {
            released = true;
        }

        byte[] consumedBytes() {
            return recorded.toByteArray();
        }

        boolean streamEnded() {
            return streamEnded;
        }

        boolean released() {
            return released;
        }
    }
}
