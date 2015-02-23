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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import junit.framework.TestCase;
import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClientIF;
import net.spy.memcached.OperationTimeoutException;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.HttpTestUtils;
import org.junit.Before;
import org.junit.Test;

public class TestMemcachedHttpCacheStorage extends TestCase {
    private MemcachedHttpCacheStorage impl;
    private MemcachedClientIF mockMemcachedClient;
    private KeyHashingScheme mockKeyHashingScheme;
    private MemcachedCacheEntryFactory mockMemcachedCacheEntryFactory;
    private MemcachedCacheEntry mockMemcachedCacheEntry;
    private MemcachedCacheEntry mockMemcachedCacheEntry2;
    private MemcachedCacheEntry mockMemcachedCacheEntry3;
    private MemcachedCacheEntry mockMemcachedCacheEntry4;

    @Override
    @Before
    public void setUp() throws Exception {
        mockMemcachedClient = mock(MemcachedClientIF.class);
        mockKeyHashingScheme = mock(KeyHashingScheme.class);
        mockMemcachedCacheEntryFactory = mock(MemcachedCacheEntryFactory.class);
        mockMemcachedCacheEntry = mock(MemcachedCacheEntry.class);
        mockMemcachedCacheEntry2 = mock(MemcachedCacheEntry.class);
        mockMemcachedCacheEntry3 = mock(MemcachedCacheEntry.class);
        mockMemcachedCacheEntry4 = mock(MemcachedCacheEntry.class);
        final CacheConfig config = CacheConfig.custom().setMaxUpdateRetries(1).build();
        impl = new MemcachedHttpCacheStorage(mockMemcachedClient, config,
                mockMemcachedCacheEntryFactory, mockKeyHashingScheme);
    }

    @Test
    public void testSuccessfulCachePut() throws IOException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        final byte[] serialized = HttpTestUtils.getRandomBytes(128);

        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, value))
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.toByteArray())
            .thenReturn(serialized);
        when(mockKeyHashingScheme.hash(url))
            .thenReturn(key);
        when(mockMemcachedClient.set(key, 0, serialized))
            .thenReturn(null);

        impl.putEntry(url, value);
        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, value);
        verify(mockMemcachedCacheEntry).toByteArray();
        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).set(key, 0, serialized);
    }

    @Test
    public void testCachePutFailsSilentlyWhenWeCannotHashAKey() throws IOException {
        final String url = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        final byte[] serialized = HttpTestUtils.getRandomBytes(128);

        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, value))
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.toByteArray())
            .thenReturn(serialized);
        when(mockKeyHashingScheme.hash(url))
            .thenThrow(new MemcachedKeyHashingException(new Exception()));

        impl.putEntry(url, value);

        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, value);
        verify(mockMemcachedCacheEntry).toByteArray();
        verify(mockKeyHashingScheme).hash(url);
    }

    public void testThrowsIOExceptionWhenMemcachedPutTimesOut() {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        final byte[] serialized = HttpTestUtils.getRandomBytes(128);

        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, value))
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.toByteArray())
            .thenReturn(serialized);
        when(mockKeyHashingScheme.hash(url))
            .thenReturn(key);
        when(mockMemcachedClient.set(key, 0, serialized))
            .thenThrow(new OperationTimeoutException("timed out"));

        try {
            impl.putEntry(url, value);
            fail("should have thrown exception");
        } catch (final IOException expected) {
        }

        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, value);
        verify(mockMemcachedCacheEntry).toByteArray();
        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).set(key, 0, serialized);
    }

    @Test
    public void testCachePutThrowsIOExceptionIfCannotSerializeEntry() {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, value))
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.toByteArray())
            .thenThrow(new MemcachedSerializationException(new Exception()));

        try {
            impl.putEntry(url, value);
            fail("should have thrown exception");
        } catch (final IOException expected) {

        }

        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, value);
        verify(mockMemcachedCacheEntry).toByteArray();
    }

    @Test
    public void testSuccessfulCacheGet() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";
        final byte[] serialized = HttpTestUtils.getRandomBytes(128);
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry();

        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.get(key)).thenReturn(serialized);
        when(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.getStorageKey()).thenReturn(url);
        when(mockMemcachedCacheEntry.getHttpCacheEntry()).thenReturn(cacheEntry);

        final HttpCacheEntry resultingEntry = impl.getEntry(url);

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).get(key);
        verify(mockMemcachedCacheEntryFactory).getUnsetCacheEntry();
        verify(mockMemcachedCacheEntry).set(serialized);
        verify(mockMemcachedCacheEntry).getStorageKey();
        verify(mockMemcachedCacheEntry).getHttpCacheEntry();

        assertSame(cacheEntry, resultingEntry);
    }

    @Test
    public void testTreatsNoneByteArrayFromMemcachedAsCacheMiss() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";

        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.get(key)).thenReturn(new Object());

        final HttpCacheEntry resultingEntry = impl.getEntry(url);

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).get(key);

        assertNull(resultingEntry);
    }

    @Test
    public void testTreatsNullFromMemcachedAsCacheMiss() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";

        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.get(key)).thenReturn(null);

        final HttpCacheEntry resultingEntry = impl.getEntry(url);

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).get(key);

        assertNull(resultingEntry);
    }

    @Test
    public void testTreatsAsCacheMissIfCannotReconstituteEntry() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";
        final byte[] serialized = HttpTestUtils.getRandomBytes(128);

        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.get(key)).thenReturn(serialized);
        when(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .thenReturn(mockMemcachedCacheEntry);
        doThrow(new MemcachedSerializationException(new Exception())).when(mockMemcachedCacheEntry).set(serialized);

        assertNull(impl.getEntry(url));

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).get(key);
        verify(mockMemcachedCacheEntryFactory).getUnsetCacheEntry();
        verify(mockMemcachedCacheEntry).set(serialized);
    }

    @Test
    public void testTreatsAsCacheMissIfCantHashStorageKey() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";

        when(mockKeyHashingScheme.hash(url)).thenThrow(new MemcachedKeyHashingException(new Exception()));

        assertNull(impl.getEntry(url));
        verify(mockKeyHashingScheme).hash(url);
    }

    @Test
    public void testThrowsIOExceptionIfMemcachedTimesOutOnGet() {
        final String url = "foo";
        final String key = "key";
        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.get(key))
            .thenThrow(new OperationTimeoutException(""));

        try {
            impl.getEntry(url);
            fail("should have thrown exception");
        } catch (final IOException expected) {
        }
        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).get(key);
    }

    @Test
    public void testCacheRemove() throws IOException {
        final String url = "foo";
        final String key = "key";
        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.delete(key)).thenReturn(null);

        impl.removeEntry(url);

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).delete(key);
    }

    @Test
    public void testCacheRemoveHandlesKeyHashingFailure() throws IOException {
        final String url = "foo";
        when(mockKeyHashingScheme.hash(url)).thenReturn(null);
        impl.removeEntry(url);
        verify(mockKeyHashingScheme).hash(url);
    }

    @Test
    public void testCacheRemoveThrowsIOExceptionOnMemcachedTimeout() {
        final String url = "foo";
        final String key = "key";
        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.delete(key))
            .thenThrow(new OperationTimeoutException(""));

        try {
            impl.removeEntry(url);
            fail("should have thrown exception");
        } catch (final IOException expected) {
        }

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).delete(key);
    }

    @Test
    public void testCacheUpdateCanUpdateNullEntry() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        final byte[] serialized = HttpTestUtils.getRandomBytes(128);

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old) {
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.gets(key)).thenReturn(null);
        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.toByteArray()).thenReturn(serialized);
        when(
                mockMemcachedClient.set(key, 0,
                        serialized)).thenReturn(null);

        impl.updateEntry(url, callback);

        verify(mockKeyHashingScheme, times(2)).hash(url);
        verify(mockMemcachedClient).gets(key);
        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, updatedValue);
        verify(mockMemcachedCacheEntry).toByteArray();
        verify(mockMemcachedClient).set(key,  0, serialized);
    }

    @Test
    public void testCacheUpdateOverwritesNonMatchingHashCollision() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        final byte[] oldBytes = HttpTestUtils.getRandomBytes(128);
        final CASValue<Object> casValue = new CASValue<Object>(-1, oldBytes);
        final byte[] newBytes = HttpTestUtils.getRandomBytes(128);

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old) {
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.gets(key)).thenReturn(casValue);
        when(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.getStorageKey()).thenReturn("not" + url);

        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .thenReturn(mockMemcachedCacheEntry2);
        when(mockMemcachedCacheEntry2.toByteArray()).thenReturn(newBytes);
        when(
                mockMemcachedClient.set(key, 0,
                        newBytes)).thenReturn(null);

        impl.updateEntry(url, callback);

        verify(mockKeyHashingScheme, times(2)).hash(url);
        verify(mockMemcachedClient).gets(key);
        verify(mockMemcachedCacheEntryFactory).getUnsetCacheEntry();
        verify(mockMemcachedCacheEntry).getStorageKey();
        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, updatedValue);
        verify(mockMemcachedCacheEntry2).toByteArray();
        verify(mockMemcachedClient).set(key,  0, newBytes);
    }

    @Test
    public void testCacheUpdateCanUpdateExistingEntry() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        final byte[] oldBytes = HttpTestUtils.getRandomBytes(128);
        final CASValue<Object> casValue = new CASValue<Object>(1, oldBytes);
        final byte[] newBytes = HttpTestUtils.getRandomBytes(128);


        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old) {
                assertSame(existingValue, old);
                return updatedValue;
            }
        };

        // get empty old entry
        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.gets(key)).thenReturn(casValue);
        when(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.getStorageKey()).thenReturn(url);
        when(mockMemcachedCacheEntry.getHttpCacheEntry()).thenReturn(existingValue);

        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .thenReturn(mockMemcachedCacheEntry2);
        when(mockMemcachedCacheEntry2.toByteArray()).thenReturn(newBytes);

        when(
                mockMemcachedClient.cas(key, casValue.getCas(),
                        newBytes)).thenReturn(CASResponse.OK);

        impl.updateEntry(url, callback);

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).gets(key);
        verify(mockMemcachedCacheEntryFactory).getUnsetCacheEntry();
        verify(mockMemcachedCacheEntry).getStorageKey();
        verify(mockMemcachedCacheEntry).getHttpCacheEntry();
        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, updatedValue);
        verify(mockMemcachedCacheEntry2).toByteArray();
        verify(mockMemcachedClient).cas(key, casValue.getCas(), newBytes);
    }

    @Test
    public void testCacheUpdateThrowsExceptionsIfCASFailsEnoughTimes() throws IOException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        final byte[] oldBytes = HttpTestUtils.getRandomBytes(128);
        final CASValue<Object> casValue = new CASValue<Object>(1, oldBytes);
        final byte[] newBytes = HttpTestUtils.getRandomBytes(128);

        final CacheConfig config = CacheConfig.custom().setMaxUpdateRetries(0).build();
        impl = new MemcachedHttpCacheStorage(mockMemcachedClient, config,
                mockMemcachedCacheEntryFactory, mockKeyHashingScheme);

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old) {
                assertSame(existingValue, old);
                return updatedValue;
            }
        };

        // get empty old entry
        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.gets(key)).thenReturn(casValue);
        when(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .thenReturn(mockMemcachedCacheEntry);
        when(mockMemcachedCacheEntry.getStorageKey()).thenReturn(url);
        when(mockMemcachedCacheEntry.getHttpCacheEntry()).thenReturn(existingValue);

        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .thenReturn(mockMemcachedCacheEntry2);
        when(mockMemcachedCacheEntry2.toByteArray()).thenReturn(newBytes);

        when(
                mockMemcachedClient.cas(key, casValue.getCas(),
                        newBytes)).thenReturn(CASResponse.EXISTS);

        try {
            impl.updateEntry(url, callback);
            fail("should have thrown exception");
        } catch (final HttpCacheUpdateException expected) {
        }

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).gets(key);
        verify(mockMemcachedCacheEntryFactory).getUnsetCacheEntry();
        verify(mockMemcachedCacheEntry).getStorageKey();
        verify(mockMemcachedCacheEntry).getHttpCacheEntry();
        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, updatedValue);
        verify(mockMemcachedCacheEntry2).toByteArray();
        verify(mockMemcachedClient).cas(key, casValue.getCas(), newBytes);
    }


    @Test
    public void testCacheUpdateCanUpdateExistingEntryWithRetry() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry existingValue2 = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue2 = HttpTestUtils.makeCacheEntry();
        final byte[] oldBytes2 = HttpTestUtils.getRandomBytes(128);
        final CASValue<Object> casValue2 = new CASValue<Object>(2, oldBytes2);
        final byte[] newBytes2 = HttpTestUtils.getRandomBytes(128);

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old) {
                if (old == existingValue) {
                    return updatedValue;
                }
                assertSame(existingValue2, old);
                return updatedValue2;
            }
        };

        when(mockKeyHashingScheme.hash(url)).thenReturn(key);

        // take two
        when(mockMemcachedClient.gets(key)).thenReturn(casValue2);
        when(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .thenReturn(mockMemcachedCacheEntry3);
        when(mockMemcachedCacheEntry3.getStorageKey()).thenReturn(url);
        when(mockMemcachedCacheEntry3.getHttpCacheEntry()).thenReturn(existingValue2);

        when(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue2))
            .thenReturn(mockMemcachedCacheEntry4);
        when(mockMemcachedCacheEntry4.toByteArray()).thenReturn(newBytes2);

        when(
                mockMemcachedClient.cas(key, casValue2.getCas(),
                        newBytes2)).thenReturn(CASResponse.OK);

        impl.updateEntry(url, callback);

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).gets(key);
        verify(mockMemcachedCacheEntryFactory).getUnsetCacheEntry();

        verify(mockMemcachedCacheEntry3).set(oldBytes2);
        verify(mockMemcachedCacheEntry3).getStorageKey();
        verify(mockMemcachedCacheEntry3).getHttpCacheEntry();
        verify(mockMemcachedCacheEntryFactory).getMemcachedCacheEntry(url, updatedValue2);
        verify(mockMemcachedCacheEntry4).toByteArray();
        verify(mockMemcachedClient).cas(key, casValue2.getCas(), newBytes2);

        verifyNoMoreInteractions(mockMemcachedClient);
        verifyNoMoreInteractions(mockKeyHashingScheme);
        verifyNoMoreInteractions(mockMemcachedCacheEntry);
        verifyNoMoreInteractions(mockMemcachedCacheEntry2);
        verifyNoMoreInteractions(mockMemcachedCacheEntry3);
        verifyNoMoreInteractions(mockMemcachedCacheEntry4);
        verifyNoMoreInteractions(mockMemcachedCacheEntryFactory);
    }


    @Test
    public void testUpdateThrowsIOExceptionIfMemcachedTimesOut() throws HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old) {
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        when(mockKeyHashingScheme.hash(url)).thenReturn(key);
        when(mockMemcachedClient.gets(key))
            .thenThrow(new OperationTimeoutException(""));

        try {
            impl.updateEntry(url, callback);
            fail("should have thrown exception");
        } catch (final IOException expected) {
        }

        verify(mockKeyHashingScheme).hash(url);
        verify(mockMemcachedClient).gets(key);
    }


    @Test(expected=HttpCacheUpdateException.class)
    public void testThrowsExceptionOnUpdateIfCannotHashStorageKey() throws Exception {
        final String url = "foo";

        when(mockKeyHashingScheme.hash(url))
            .thenThrow(new MemcachedKeyHashingException(new Exception()));

        try {
            impl.updateEntry(url, null);
            fail("should have thrown exception");
        } catch (final HttpCacheUpdateException expected) {
        }

        verify(mockKeyHashingScheme).hash(url);
    }
}
