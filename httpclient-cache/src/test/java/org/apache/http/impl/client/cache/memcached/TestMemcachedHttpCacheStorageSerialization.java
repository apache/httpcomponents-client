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
package org.apache.http.impl.client.cache.memcached;

import static org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.assertEntryEquals;
import static org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.assertNoCallbacks;
import static org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.entry;
import static org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.serialize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Date;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.HeapResource;
import org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.ReadObjectProbe;
import org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.ReadObjectResource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClientIF;
import net.spy.memcached.OperationTimeoutException;

/**
 * Tests {@link MemcachedHttpCacheStorage} with real serialized cache entries and
 * a mocked {@link MemcachedClientIF}.
 * <p>
 * Covers cache misses for rejected entries, replacement and compare-and-swap
 * updates, retry limits, timeout causes, and support for application-provided
 * {@link MemcachedCacheEntryFactory} implementations.
 * </p>
 */
public class TestMemcachedHttpCacheStorageSerialization {

    /**
     * Models an application-provided serializer that retains the original format
     * and permits custom resource types for factory compatibility tests.
     */
    private static class LegacyCacheEntry implements MemcachedCacheEntry {
        private String key;
        private HttpCacheEntry entry;

        @Override
        public HttpCacheEntry getHttpCacheEntry() {
            return entry;
        }

        @Override
        public String getStorageKey() {
            return key;
        }

        @Override
        public void set(final byte[] bytes) {
            try {
                final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                try {
                    key = (String) in.readObject();
                    entry = (HttpCacheEntry) in.readObject();
                } finally {
                    in.close();
                }
            } catch (final IOException ex) {
                throw new MemcachedSerializationException(ex);
            } catch (final ClassNotFoundException ex) {
                throw new MemcachedSerializationException(ex);
            }
        }

        @Override
        public byte[] toByteArray() {
            try {
                return serialize(key, entry);
            } catch (final IOException ex) {
                throw new MemcachedSerializationException(ex);
            }
        }
    }

    private static final String URL = "http://example.test/resource";
    private MemcachedClientIF client;
    private MemcachedHttpCacheStorage storage;
    private String key;
    private HttpCacheEntry replacement;

    private HttpCacheUpdateCallback callback;

    private void assertStoredReplacement(final byte[] bytes) throws IOException {
        final MemcachedCacheEntryImpl restored = new MemcachedCacheEntryImpl();
        restored.set(bytes);
        assertEquals(URL, restored.getStorageKey());
        assertEntryEquals(replacement, restored.getHttpCacheEntry());
    }

    @Test
    public void customFactoryCanReadItsOwnResourceTypes() throws Exception {
        final MemcachedCacheEntryFactory factory = new MemcachedCacheEntryFactory() {
            @Override
            public MemcachedCacheEntry getMemcachedCacheEntry(final String key, final HttpCacheEntry entry) {
                final LegacyCacheEntry result = new LegacyCacheEntry();
                result.key = key;
                result.entry = entry;
                return result;
            }

            @Override
            public MemcachedCacheEntry getUnsetCacheEntry() {
                return new LegacyCacheEntry();
            }
        };
        storage = new MemcachedHttpCacheStorage(client, CacheConfig.DEFAULT, factory, new SHA256KeyHashingScheme());
        final HttpCacheEntry custom = entry(new ReadObjectResource());
        storage.putEntry(URL, custom);
        final ArgumentCaptor<byte[]> written = ArgumentCaptor.forClass(byte[].class);
        verify(client).set(eq(key), eq(0), written.capture());
        when(client.get(key)).thenReturn(written.getValue());
        assertEntryEquals(custom, storage.getEntry(URL));
        assertEquals(1, MemcachedSerializationTestUtils.readObjectCalls);
    }

    @Test
    public void preservesTimeoutCauseDuringCas() throws Exception {
        final OperationTimeoutException timeout = new OperationTimeoutException("test");
        when(client.gets(key)).thenReturn(new CASValue<Object>(7L, serialize(URL, entry(null))));
        when(client.cas(eq(key), eq(7L), any(byte[].class))).thenThrow(timeout);
        try {
            storage.updateEntry(URL, callback);
            fail("Expected timeout");
        } catch (final MemcachedOperationTimeoutException expected) {
            assertSame(timeout, expected.getCause());
        }
    }

    @Test
    public void readsValidEntryUsingDefaultFactory() throws Exception {
        when(client.get(key)).thenReturn(serialize(URL, replacement));
        assertEntryEquals(replacement, storage.getEntry(URL));
    }

    private byte[][] rejectedEntries() throws IOException {
        final HttpCacheEntry malformed = entry(null);
        return new byte[][] {serialize(new ReadObjectProbe(), replacement),
                serialize(URL, new ReadObjectProbe()), serialize(URL, entry(new ReadObjectResource())),
                serialize(new Date(), replacement), serialize(URL, "wrong-type"),
                serialize(URL), new byte[0], new byte[] {1, 2, 3, 4},
                MemcachedSerializationTestUtils.serializeReplacing(malformed.getStatusLine(), new Date(1L), URL, malformed),
                MemcachedSerializationTestUtils.negativeArrayLength(URL, new byte[0]),
                MemcachedSerializationTestUtils.negativeProxyInterfaceCount(URL),
                MemcachedSerializationTestUtils.nullFilePath(URL)};
    }

    @Test
    public void replacesEntryWithMismatchedKey() throws Exception {
        when(client.gets(key)).thenReturn(new CASValue<Object>(1L, serialize("different-key", replacement)));
        storage.updateEntry(URL, callback);
        verify(callback).update(null);
        final ArgumentCaptor<byte[]> written = ArgumentCaptor.forClass(byte[].class);
        verify(client).set(eq(key), eq(0), written.capture());
        assertStoredReplacement(written.getValue());
    }

    @Test
    public void replacesRejectedEntriesAsCacheMisses() throws Exception {
        final byte[][] rejected = rejectedEntries();
        for (final byte[] bytes : rejected) {
            when(client.gets(key)).thenReturn(new CASValue<Object>(1L, bytes));
            storage.updateEntry(URL, callback);
            assertNoCallbacks();
        }
        verify(callback, times(rejected.length)).update(null);
        final ArgumentCaptor<byte[]> written = ArgumentCaptor.forClass(byte[].class);
        verify(client, times(rejected.length)).set(eq(key), eq(0), written.capture());
        for (final byte[] bytes : written.getAllValues()) {
            assertStoredReplacement(bytes);
        }
        verify(client, never()).cas(eq(key), eq(1L), any(byte[].class));
    }

    @Test
    public void replacesRejectedEntryEncounteredDuringCasRetry() throws Exception {
        when(client.gets(key)).thenReturn(new CASValue<Object>(7L, serialize(URL, entry(null))),
                new CASValue<Object>(8L, serialize(URL, new ReadObjectProbe())));
        when(client.cas(eq(key), eq(7L), any(byte[].class))).thenReturn(CASResponse.EXISTS);
        storage.updateEntry(URL, callback);
        verify(callback).update(null);
        final ArgumentCaptor<byte[]> written = ArgumentCaptor.forClass(byte[].class);
        verify(client).set(eq(key), eq(0), written.capture());
        assertStoredReplacement(written.getValue());
        assertNoCallbacks();
    }

    @Test
    public void replacesSerializedNullEntry() throws Exception {
        when(client.gets(key)).thenReturn(new CASValue<Object>(1L, serialize(URL, null)));
        storage.updateEntry(URL, callback);
        verify(callback).update(null);
        final ArgumentCaptor<byte[]> written = ArgumentCaptor.forClass(byte[].class);
        verify(client).set(eq(key), eq(0), written.capture());
        assertStoredReplacement(written.getValue());
        verify(client, never()).cas(eq(key), eq(1L), any(byte[].class));
    }

    @Test
    public void retriesCasWithFreshlyDeserializedEntryAndToken() throws Exception {
        final HttpCacheEntry first = entry(null);
        final HttpCacheEntry second = entry(new HeapResource(new byte[] {1}));
        when(client.gets(key)).thenReturn(new CASValue<Object>(7L, serialize(URL, first)),
                new CASValue<Object>(8L, serialize(URL, second)));
        when(client.cas(eq(key), eq(7L), any(byte[].class))).thenReturn(CASResponse.EXISTS);
        when(client.cas(eq(key), eq(8L), any(byte[].class))).thenReturn(CASResponse.OK);
        storage.updateEntry(URL, callback);
        final ArgumentCaptor<HttpCacheEntry> seen = ArgumentCaptor.forClass(HttpCacheEntry.class);
        verify(callback, times(2)).update(seen.capture());
        assertEntryEquals(first, seen.getAllValues().get(0));
        assertEntryEquals(second, seen.getAllValues().get(1));
        verify(client, times(2)).gets(key);
        verify(client).cas(eq(key), eq(7L), any(byte[].class));
        verify(client).cas(eq(key), eq(8L), any(byte[].class));
    }

    @Before
    public void setUp() throws Exception {
        client = mock(MemcachedClientIF.class);
        storage = new MemcachedHttpCacheStorage(client, CacheConfig.custom().setMaxUpdateRetries(1).build(),
                new MemcachedCacheEntryFactoryImpl(), new SHA256KeyHashingScheme());
        key = new SHA256KeyHashingScheme().hash(URL);
        replacement = entry(new HeapResource(new byte[] {4, 5, 6}));
        callback = mock(HttpCacheUpdateCallback.class);
        when(callback.update(any(HttpCacheEntry.class))).thenReturn(replacement);
        MemcachedSerializationTestUtils.resetCallbacks();
    }

    @Test
    public void stopsAfterConfiguredCasRetries() throws Exception {
        when(client.gets(key)).thenReturn(new CASValue<Object>(7L, serialize(URL, entry(null))));
        when(client.cas(eq(key), eq(7L), any(byte[].class))).thenReturn(CASResponse.EXISTS);
        try {
            storage.updateEntry(URL, callback);
            fail("Expected exhausted retries");
        } catch (final HttpCacheUpdateException expected) {
            verify(client, times(2)).gets(key);
            verify(client, times(2)).cas(eq(key), eq(7L), any(byte[].class));
            verify(callback, times(2)).update(any(HttpCacheEntry.class));
        }
    }

    @Test
    public void treatsDifferentAndNullKeysAsCacheMisses() throws Exception {
        for (final String storedKey : new String[] {"different-key", null}) {
            when(client.get(key)).thenReturn(serialize(storedKey, replacement));
            assertNull(storage.getEntry(URL));
        }
    }

    @Test
    public void treatsRejectedEntriesAsCacheMisses() throws Exception {
        for (final byte[] bytes : rejectedEntries()) {
            when(client.get(key)).thenReturn(bytes);
            assertNull(storage.getEntry(URL));
            assertNoCallbacks();
        }
    }

    @Test
    public void treatsSerializedNullEntryAsCacheMiss() throws Exception {
        when(client.get(key)).thenReturn(serialize(URL, null));
        assertNull(storage.getEntry(URL));
    }

    @Test
    public void updatesValidEntryWithCas() throws Exception {
        final HttpCacheEntry old = entry(null);
        when(client.gets(key)).thenReturn(new CASValue<Object>(7L, serialize(URL, old)));
        when(client.cas(eq(key), eq(7L), any(byte[].class))).thenReturn(CASResponse.OK);
        storage.updateEntry(URL, callback);
        final ArgumentCaptor<HttpCacheEntry> seen = ArgumentCaptor.forClass(HttpCacheEntry.class);
        verify(callback).update(seen.capture());
        assertEntryEquals(old, seen.getValue());
        final ArgumentCaptor<byte[]> written = ArgumentCaptor.forClass(byte[].class);
        verify(client).cas(eq(key), eq(7L), written.capture());
        assertStoredReplacement(written.getValue());
    }
}
