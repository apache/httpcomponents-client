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
package org.apache.hc.client5.http.entity.compress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class TestBasicCompressionDictionaryStore {

    private static final Instant STORED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2030-01-01T00:00:00Z");

    private static CompressionDictionary dictionary(final byte[] content, final URI source) {
        return new CompressionDictionary(content, source, "/path", "id", STORED_AT, VALID_UNTIL);
    }

    private static CompressionDictionary dictionary(final int n) {
        return dictionary(new byte[]{(byte) n}, URI.create("https://example.com/"));
    }

    @Test
    void addNullThrows() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        assertThrows(NullPointerException.class, () -> store.add(null));
    }

    @Test
    void getByHashNullThrows() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        assertThrows(NullPointerException.class, () -> store.getByHash(null));
    }

    @Test
    void getByOriginNullThrows() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        assertThrows(NullPointerException.class, () -> store.getByOrigin(null));
    }

    @Test
    void nonPositiveMaxEntriesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new BasicCompressionDictionaryStore(0));
        assertThrows(IllegalArgumentException.class, () -> new BasicCompressionDictionaryStore(-1));
    }

    @Test
    void defaultConstructorWorks() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary dict = dictionary(1);
        store.add(dict);
        assertSame(dict, store.getByHash(dict.getSha256()));
    }

    @Test
    void addThenGetByHashReturnsDictionary() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary dict = dictionary(42);
        store.add(dict);
        assertSame(dict, store.getByHash(dict.getSha256()));
    }

    @Test
    void getByHashUnknownReturnsNull() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        store.add(dictionary(1));
        final CompressionDictionary other = dictionary(99);
        assertNull(store.getByHash(other.getSha256()));
    }

    @Test
    void sameHashTwiceKeepsSingleEntry() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final URI source = URI.create("https://example.com/");
        final CompressionDictionary first = dictionary(new byte[]{7}, source);
        final CompressionDictionary second = dictionary(new byte[]{7}, source);
        store.add(first);
        store.add(second);
        assertSame(second, store.getByHash(first.getSha256()));
        assertEquals(1, store.getByOrigin(source).size());
    }

    @Test
    void evictionRemovesOldest() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore(2);
        final CompressionDictionary first = dictionary(1);
        final CompressionDictionary second = dictionary(2);
        final CompressionDictionary third = dictionary(3);
        store.add(first);
        store.add(second);
        store.add(third);
        assertNull(store.getByHash(first.getSha256()));
        assertSame(second, store.getByHash(second.getSha256()));
        assertSame(third, store.getByHash(third.getSha256()));
    }

    @Test
    void getByOriginFiltersByScheme() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary https = dictionary(new byte[]{1}, URI.create("https://example.com/"));
        final CompressionDictionary http = dictionary(new byte[]{2}, URI.create("http://example.com/"));
        store.add(https);
        store.add(http);
        final List<CompressionDictionary> result = store.getByOrigin(URI.create("https://example.com/other"));
        assertEquals(1, result.size());
        assertSame(https, result.get(0));
    }

    @Test
    void getByOriginFiltersByHost() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary a = dictionary(new byte[]{1}, URI.create("https://a.example.com/"));
        final CompressionDictionary b = dictionary(new byte[]{2}, URI.create("https://b.example.com/"));
        store.add(a);
        store.add(b);
        final List<CompressionDictionary> result = store.getByOrigin(URI.create("https://b.example.com/"));
        assertEquals(1, result.size());
        assertSame(b, result.get(0));
    }

    @Test
    void getByOriginTreatsHttpsDefaultPortAsEqual() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary implicit = dictionary(new byte[]{1}, URI.create("https://example.com/"));
        store.add(implicit);
        final List<CompressionDictionary> result = store.getByOrigin(URI.create("https://example.com:443/"));
        assertEquals(1, result.size());
        assertSame(implicit, result.get(0));
    }

    @Test
    void getByOriginTreatsHttpDefaultPortAsEqual() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary explicit = dictionary(new byte[]{1}, URI.create("http://example.com:80/"));
        store.add(explicit);
        final List<CompressionDictionary> result = store.getByOrigin(URI.create("http://example.com/"));
        assertEquals(1, result.size());
        assertSame(explicit, result.get(0));
    }

    @Test
    void getByOriginDistinguishesNonDefaultPort() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary def = dictionary(new byte[]{1}, URI.create("https://example.com/"));
        final CompressionDictionary alt = dictionary(new byte[]{2}, URI.create("https://example.com:8443/"));
        store.add(def);
        store.add(alt);
        assertEquals(1, store.getByOrigin(URI.create("https://example.com:8443/")).size());
        assertSame(alt, store.getByOrigin(URI.create("https://example.com:8443/")).get(0));
        assertSame(def, store.getByOrigin(URI.create("https://example.com/")).get(0));
    }

    @Test
    void getByOriginNoMatchReturnsEmpty() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        store.add(dictionary(new byte[]{1}, URI.create("https://example.com/")));
        final List<CompressionDictionary> result = store.getByOrigin(URI.create("https://other.example.com/"));
        assertTrue(result.isEmpty());
    }

    @Test
    void clearEmptiesStore() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary dict = dictionary(1);
        store.add(dict);
        store.clear();
        assertNull(store.getByHash(dict.getSha256()));
        assertTrue(store.getByOrigin(URI.create("https://example.com/")).isEmpty());
    }

    @Test
    void concurrentAddsStayWithinMaxEntries() throws InterruptedException {
        final int maxEntries = 16;
        final int threads = 32;
        final int perThread = 50;
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore(maxEntries);
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            for (int t = 0; t < threads; t++) {
                final int base = t;
                executor.execute(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            final byte[] content = new byte[]{(byte) base, (byte) i};
                            store.add(dictionary(content, URI.create("https://example.com/")));
                        }
                    } catch (final Throwable ex) {
                        failure.compareAndSet(null, ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "threads did not finish in time");
        } finally {
            executor.shutdownNow();
        }
        assertNull(failure.get(), "concurrent add threw: " + failure.get());
        assertTrue(store.getByOrigin(URI.create("https://example.com/")).size() <= maxEntries);
    }

    @Test
    void getByHashReturnsNonNullAfterAdd() {
        final BasicCompressionDictionaryStore store = new BasicCompressionDictionaryStore();
        final CompressionDictionary dict = dictionary(5);
        store.add(dict);
        assertNotNull(store.getByHash(dict.getSha256()));
    }
}
