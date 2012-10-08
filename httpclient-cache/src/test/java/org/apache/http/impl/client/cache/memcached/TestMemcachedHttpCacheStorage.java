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
import org.easymock.EasyMock;
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
        mockMemcachedClient = EasyMock.createNiceMock(MemcachedClientIF.class);
        mockKeyHashingScheme = EasyMock.createNiceMock(KeyHashingScheme.class);
        mockMemcachedCacheEntryFactory = EasyMock.createNiceMock(MemcachedCacheEntryFactory.class);
        mockMemcachedCacheEntry = EasyMock.createNiceMock(MemcachedCacheEntry.class);
        mockMemcachedCacheEntry2 = EasyMock.createNiceMock(MemcachedCacheEntry.class);
        mockMemcachedCacheEntry3 = EasyMock.createNiceMock(MemcachedCacheEntry.class);
        mockMemcachedCacheEntry4 = EasyMock.createNiceMock(MemcachedCacheEntry.class);
        CacheConfig config = new CacheConfig();
        config.setMaxUpdateRetries(1);
        impl = new MemcachedHttpCacheStorage(mockMemcachedClient, config,
                mockMemcachedCacheEntryFactory, mockKeyHashingScheme);
    }

    private void replayMocks() {
        EasyMock.replay(mockMemcachedClient);
        EasyMock.replay(mockKeyHashingScheme);
        EasyMock.replay(mockMemcachedCacheEntry);
        EasyMock.replay(mockMemcachedCacheEntry2);
        EasyMock.replay(mockMemcachedCacheEntry3);
        EasyMock.replay(mockMemcachedCacheEntry4);
        EasyMock.replay(mockMemcachedCacheEntryFactory);
    }

    private void verifyMocks() {
        EasyMock.verify(mockMemcachedClient);
        EasyMock.verify(mockKeyHashingScheme);
        EasyMock.verify(mockMemcachedCacheEntry);
        EasyMock.verify(mockMemcachedCacheEntry2);
        EasyMock.verify(mockMemcachedCacheEntry3);
        EasyMock.verify(mockMemcachedCacheEntry4);
        EasyMock.verify(mockMemcachedCacheEntryFactory);
    }

    @Test
    public void testSuccessfulCachePut() throws IOException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        byte[] serialized = HttpTestUtils.getRandomBytes(128);

        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, value))
            .andReturn(mockMemcachedCacheEntry);
        EasyMock.expect(mockMemcachedCacheEntry.toByteArray())
            .andReturn(serialized);
        EasyMock.expect(mockKeyHashingScheme.hash(url))
            .andReturn(key);
        EasyMock.expect(mockMemcachedClient.set(key, 0, serialized))
            .andReturn(null);

        replayMocks();
        impl.putEntry(url, value);
        verifyMocks();
    }
    
    @Test
    public void testCachePutFailsSilentlyWhenWeCannotHashAKey() throws IOException {
        final String url = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        byte[] serialized = HttpTestUtils.getRandomBytes(128);

        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, value))
            .andReturn(mockMemcachedCacheEntry).times(0,1);
        EasyMock.expect(mockMemcachedCacheEntry.toByteArray())
            .andReturn(serialized).times(0,1);
        EasyMock.expect(mockKeyHashingScheme.hash(url))
            .andThrow(new MemcachedKeyHashingException(new Exception()));

        replayMocks();
        impl.putEntry(url, value);
        verifyMocks();
    }
    
    public void testThrowsIOExceptionWhenMemcachedPutTimesOut() throws IOException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        byte[] serialized = HttpTestUtils.getRandomBytes(128);

        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, value))
            .andReturn(mockMemcachedCacheEntry);
        EasyMock.expect(mockMemcachedCacheEntry.toByteArray())
            .andReturn(serialized);
        EasyMock.expect(mockKeyHashingScheme.hash(url))
            .andReturn(key);
        EasyMock.expect(mockMemcachedClient.set(key, 0, serialized))
            .andThrow(new OperationTimeoutException("timed out"));

        replayMocks();
        try {
            impl.putEntry(url, value);
            fail("should have thrown exception");
        } catch (IOException expected) {
        }
        verifyMocks();
    }

    @Test
    public void testCachePutThrowsIOExceptionIfCannotSerializeEntry() throws IOException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, value))
            .andReturn(mockMemcachedCacheEntry);
        EasyMock.expect(mockMemcachedCacheEntry.toByteArray())
            .andThrow(new MemcachedSerializationException(new Exception()));
        EasyMock.expect(mockKeyHashingScheme.hash(url))
            .andReturn(key).times(0,1);

        replayMocks();
        try {
            impl.putEntry(url, value);
            fail("should have thrown exception");
        } catch (IOException expected) {
            
        }
        verifyMocks();
    }
    
    @Test
    public void testSuccessfulCacheGet() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";
        byte[] serialized = HttpTestUtils.getRandomBytes(128);
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry();
        
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key);
        EasyMock.expect(mockMemcachedClient.get(key)).andReturn(serialized);
        EasyMock.expect(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .andReturn(mockMemcachedCacheEntry);
        mockMemcachedCacheEntry.set(serialized);
        EasyMock.expect(mockMemcachedCacheEntry.getStorageKey()).andReturn(url);
        EasyMock.expect(mockMemcachedCacheEntry.getHttpCacheEntry()).andReturn(cacheEntry);
        
        replayMocks();
        HttpCacheEntry resultingEntry = impl.getEntry(url);
        verifyMocks();
        assertSame(cacheEntry, resultingEntry);
    }
    
    @Test
    public void testTreatsNoneByteArrayFromMemcachedAsCacheMiss() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";
        
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key);
        EasyMock.expect(mockMemcachedClient.get(key)).andReturn(new Object());
        
        replayMocks();
        HttpCacheEntry resultingEntry = impl.getEntry(url);
        verifyMocks();
        assertNull(resultingEntry);
    }
    
    @Test
    public void testTreatsNullFromMemcachedAsCacheMiss() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";
        
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key);
        EasyMock.expect(mockMemcachedClient.get(key)).andReturn(null);
        
        replayMocks();
        HttpCacheEntry resultingEntry = impl.getEntry(url);
        verifyMocks();
        assertNull(resultingEntry);
    }
    
    @Test
    public void testTreatsAsCacheMissIfCannotReconstituteEntry() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";
        byte[] serialized = HttpTestUtils.getRandomBytes(128);
        
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key);
        EasyMock.expect(mockMemcachedClient.get(key)).andReturn(serialized);
        EasyMock.expect(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .andReturn(mockMemcachedCacheEntry);
        mockMemcachedCacheEntry.set(serialized);
        EasyMock.expectLastCall().andThrow(new MemcachedSerializationException(new Exception()));
        
        replayMocks();
        assertNull(impl.getEntry(url));
        verifyMocks();
    }

    @Test
    public void testTreatsAsCacheMissIfCantHashStorageKey() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andThrow(new MemcachedKeyHashingException(new Exception()));
        
        replayMocks();
        assertNull(impl.getEntry(url));
        verifyMocks();
    }

    @Test
    public void testThrowsIOExceptionIfMemcachedTimesOutOnGet() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final String key = "key";
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key);
        EasyMock.expect(mockMemcachedClient.get(key))
            .andThrow(new OperationTimeoutException(""));
        
        replayMocks();
        try {
            impl.getEntry(url);
            fail("should have thrown exception");
        } catch (IOException expected) {
        }
        verifyMocks();
    }

    @Test
    public void testCacheRemove() throws IOException {
        final String url = "foo";
        final String key = "key";
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key);
        EasyMock.expect(mockMemcachedClient.delete(key)).andReturn(null);
        replayMocks();
        impl.removeEntry(url);
        verifyMocks();
    }
    
    @Test
    public void testCacheRemoveHandlesKeyHashingFailure() throws IOException {
        final String url = "foo";
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(null);
        replayMocks();
        impl.removeEntry(url);
        verifyMocks();
    }

    @Test
    public void testCacheRemoveThrowsIOExceptionOnMemcachedTimeout() throws IOException {
        final String url = "foo";
        final String key = "key";
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key);
        EasyMock.expect(mockMemcachedClient.delete(key))
            .andThrow(new OperationTimeoutException(""));
        
        replayMocks();
        try {
            impl.removeEntry(url);
            fail("should have thrown exception");
        } catch (IOException expected) {
        }
        verifyMocks();
    }

    @Test
    public void testCacheUpdateCanUpdateNullEntry() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        final byte[] serialized = HttpTestUtils.getRandomBytes(128);

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key).anyTimes();
        EasyMock.expect(mockMemcachedClient.gets(key)).andReturn(null);
        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .andReturn(mockMemcachedCacheEntry);
        EasyMock.expect(mockMemcachedCacheEntry.toByteArray()).andReturn(serialized);
        EasyMock.expect(
                mockMemcachedClient.set(EasyMock.eq(key), EasyMock.eq(0),
                        EasyMock.aryEq(serialized))).andReturn(null);

        replayMocks();
        impl.updateEntry(url, callback);
        verifyMocks();
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

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key).anyTimes();
        EasyMock.expect(mockMemcachedClient.gets(key)).andReturn(casValue);
        EasyMock.expect(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .andReturn(mockMemcachedCacheEntry);
        mockMemcachedCacheEntry.set(oldBytes);
        EasyMock.expect(mockMemcachedCacheEntry.getStorageKey()).andReturn("not" + url).anyTimes();
        
        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .andReturn(mockMemcachedCacheEntry2);
        EasyMock.expect(mockMemcachedCacheEntry2.toByteArray()).andReturn(newBytes);
        EasyMock.expect(
                mockMemcachedClient.set(EasyMock.eq(key), EasyMock.eq(0),
                        EasyMock.aryEq(newBytes))).andReturn(null);

        replayMocks();
        impl.updateEntry(url, callback);
        verifyMocks();
    }

    @Test
    public void testCacheUpdateCanUpdateExistingEntry() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        final byte[] oldBytes = HttpTestUtils.getRandomBytes(128);
        CASValue<Object> casValue = new CASValue<Object>(1, oldBytes);
        final byte[] newBytes = HttpTestUtils.getRandomBytes(128);
        

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertSame(existingValue, old);
                return updatedValue;
            }
        };

        // get empty old entry
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key).anyTimes();
        EasyMock.expect(mockMemcachedClient.gets(key)).andReturn(casValue);
        EasyMock.expect(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .andReturn(mockMemcachedCacheEntry);
        mockMemcachedCacheEntry.set(oldBytes);
        EasyMock.expect(mockMemcachedCacheEntry.getStorageKey()).andReturn(url);
        EasyMock.expect(mockMemcachedCacheEntry.getHttpCacheEntry()).andReturn(existingValue);
        
        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .andReturn(mockMemcachedCacheEntry2);
        EasyMock.expect(mockMemcachedCacheEntry2.toByteArray()).andReturn(newBytes);

        EasyMock.expect(
                mockMemcachedClient.cas(EasyMock.eq(key), EasyMock.eq(casValue.getCas()),
                        EasyMock.aryEq(newBytes))).andReturn(CASResponse.OK);

        replayMocks();
        impl.updateEntry(url, callback);
        verifyMocks();
    }
    
    @Test
    public void testCacheUpdateThrowsExceptionsIfCASFailsEnoughTimes() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        final byte[] oldBytes = HttpTestUtils.getRandomBytes(128);
        CASValue<Object> casValue = new CASValue<Object>(1, oldBytes);
        final byte[] newBytes = HttpTestUtils.getRandomBytes(128);
        
        CacheConfig config = new CacheConfig();
        config.setMaxUpdateRetries(0);
        impl = new MemcachedHttpCacheStorage(mockMemcachedClient, config,
                mockMemcachedCacheEntryFactory, mockKeyHashingScheme);

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertSame(existingValue, old);
                return updatedValue;
            }
        };

        // get empty old entry
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key).anyTimes();
        EasyMock.expect(mockMemcachedClient.gets(key)).andReturn(casValue);
        EasyMock.expect(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .andReturn(mockMemcachedCacheEntry);
        mockMemcachedCacheEntry.set(oldBytes);
        EasyMock.expect(mockMemcachedCacheEntry.getStorageKey()).andReturn(url);
        EasyMock.expect(mockMemcachedCacheEntry.getHttpCacheEntry()).andReturn(existingValue);
        
        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .andReturn(mockMemcachedCacheEntry2);
        EasyMock.expect(mockMemcachedCacheEntry2.toByteArray()).andReturn(newBytes);

        EasyMock.expect(
                mockMemcachedClient.cas(EasyMock.eq(key), EasyMock.eq(casValue.getCas()),
                        EasyMock.aryEq(newBytes))).andReturn(CASResponse.EXISTS);

        replayMocks();
        try {
            impl.updateEntry(url, callback);
            fail("should have thrown exception");
        } catch (HttpCacheUpdateException expected) {
        }
        verifyMocks();
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
        final byte[] oldBytes = HttpTestUtils.getRandomBytes(128);
        final byte[] oldBytes2 = HttpTestUtils.getRandomBytes(128);
        CASValue<Object> casValue = new CASValue<Object>(1, oldBytes);
        CASValue<Object> casValue2 = new CASValue<Object>(2, oldBytes2);
        final byte[] newBytes = HttpTestUtils.getRandomBytes(128);
        final byte[] newBytes2 = HttpTestUtils.getRandomBytes(128);

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                if (old == existingValue) return updatedValue;
                assertSame(existingValue2, old);
                return updatedValue2;
            }
        };

        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key).anyTimes();
        EasyMock.expect(mockMemcachedClient.gets(key)).andReturn(casValue);
        EasyMock.expect(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .andReturn(mockMemcachedCacheEntry);
        mockMemcachedCacheEntry.set(oldBytes);
        EasyMock.expect(mockMemcachedCacheEntry.getStorageKey()).andReturn(url);
        EasyMock.expect(mockMemcachedCacheEntry.getHttpCacheEntry()).andReturn(existingValue);
        
        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue))
            .andReturn(mockMemcachedCacheEntry2);
        EasyMock.expect(mockMemcachedCacheEntry2.toByteArray()).andReturn(newBytes);

        EasyMock.expect(
                mockMemcachedClient.cas(EasyMock.eq(key), EasyMock.eq(casValue.getCas()),
                        EasyMock.aryEq(newBytes))).andReturn(CASResponse.EXISTS);

        // take two
        EasyMock.expect(mockMemcachedClient.gets(key)).andReturn(casValue2);
        EasyMock.expect(mockMemcachedCacheEntryFactory.getUnsetCacheEntry())
            .andReturn(mockMemcachedCacheEntry3);
        mockMemcachedCacheEntry3.set(oldBytes2);
        EasyMock.expect(mockMemcachedCacheEntry3.getStorageKey()).andReturn(url);
        EasyMock.expect(mockMemcachedCacheEntry3.getHttpCacheEntry()).andReturn(existingValue2);
        
        EasyMock.expect(mockMemcachedCacheEntryFactory.getMemcachedCacheEntry(url, updatedValue2))
            .andReturn(mockMemcachedCacheEntry4);
        EasyMock.expect(mockMemcachedCacheEntry4.toByteArray()).andReturn(newBytes2);

        EasyMock.expect(
                mockMemcachedClient.cas(EasyMock.eq(key), EasyMock.eq(casValue2.getCas()),
                        EasyMock.aryEq(newBytes2))).andReturn(CASResponse.OK);
        
        replayMocks();
        impl.updateEntry(url, callback);
        verifyMocks();
    }


    @Test
    public void testUpdateThrowsIOExceptionIfMemcachedTimesOut() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final String key = "key";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        EasyMock.expect(mockKeyHashingScheme.hash(url)).andReturn(key).anyTimes();
        EasyMock.expect(mockMemcachedClient.gets(key))
            .andThrow(new OperationTimeoutException(""));

        replayMocks();
        try {
            impl.updateEntry(url, callback);
            fail("should have thrown exception");
        } catch (IOException expected) {
        }
        verifyMocks();
    }

    
    @Test(expected=HttpCacheUpdateException.class)
    public void testThrowsExceptionOnUpdateIfCannotHashStorageKey() throws Exception {
        final String url = "foo";

        EasyMock.expect(mockKeyHashingScheme.hash(url))
            .andThrow(new MemcachedKeyHashingException(new Exception()));

        replayMocks();
        try {
            impl.updateEntry(url, null);
            fail("should have thrown exception");
        } catch (HttpCacheUpdateException expected) {
        }
        verifyMocks();
    }
}
