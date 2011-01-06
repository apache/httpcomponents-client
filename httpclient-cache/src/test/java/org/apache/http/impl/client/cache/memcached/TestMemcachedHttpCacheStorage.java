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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import junit.framework.TestCase;
import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClientIF;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
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
    private HttpCacheEntrySerializer mockSerializer;

    @Override
    @Before
    public void setUp() throws Exception {
        mockMemcachedClient = EasyMock.createMock(MemcachedClientIF.class);
        mockSerializer = EasyMock.createMock(HttpCacheEntrySerializer.class);
        CacheConfig config = new CacheConfig();
        config.setMaxUpdateRetries(1);
        impl = new MemcachedHttpCacheStorage(mockMemcachedClient, config,
                mockSerializer);
    }

    private void replayMocks() {
        EasyMock.replay(mockMemcachedClient);
        EasyMock.replay(mockSerializer);
    }

    private void verifyMocks() {
        EasyMock.verify(mockMemcachedClient);
        EasyMock.verify(mockSerializer);
    }

    @Test
    public void testCachePut() throws IOException {
        final String url = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        mockSerializer.writeTo(EasyMock.isA(HttpCacheEntry.class), EasyMock
                .isA(OutputStream.class));
        EasyMock.expect(
                mockMemcachedClient.set(EasyMock.eq(url), EasyMock.eq(0),
                        EasyMock.aryEq(new byte[0]))).andReturn(null);
        replayMocks();
        impl.putEntry(url, value);
        verifyMocks();
    }

    @Test
    public void testCacheGet() throws UnsupportedEncodingException,
            IOException {
        final String url = "foo";
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry();
        EasyMock.expect(mockMemcachedClient.get(url)).andReturn(new byte[] {});
        EasyMock.expect(
                mockSerializer.readFrom(EasyMock.isA(InputStream.class)))
                .andReturn(cacheEntry);
        replayMocks();
        HttpCacheEntry resultingEntry = impl.getEntry(url);
        verifyMocks();
        assertSame(cacheEntry, resultingEntry);
    }

    @Test
    public void testCacheGetNullEntry() throws IOException {
        final String url = "foo";

        EasyMock.expect(mockMemcachedClient.get(url)).andReturn(null);

        replayMocks();
        HttpCacheEntry resultingEntry = impl.getEntry(url);
        verifyMocks();

        assertNull(resultingEntry);
    }

    @Test
    public void testCacheRemove() throws IOException {
        final String url = "foo";
        EasyMock.expect(mockMemcachedClient.delete(url)).andReturn(null);
        replayMocks();
        impl.removeEntry(url);
        verifyMocks();
    }

    @Test
    public void testCacheUpdateNullEntry() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        EasyMock.expect(mockMemcachedClient.gets(url)).andReturn(null);
        // EasyMock.expect(mockCache.get(key)).andReturn(null);

        // put new entry
        mockSerializer.writeTo(EasyMock.same(updatedValue), EasyMock
                .isA(OutputStream.class));
        EasyMock.expect(
                mockMemcachedClient.set(EasyMock.eq(url), EasyMock.eq(0),
                        EasyMock.aryEq(new byte[0]))).andReturn(null);

        replayMocks();
        impl.updateEntry(url, callback);
        verifyMocks();
    }

    @Test
    public void testCacheUpdate() throws IOException, HttpCacheUpdateException {
        final String url = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        CASValue<Object> v = new CASValue<Object>(1234, new byte[] {});

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };

        // get existing old entry
        EasyMock.expect(mockMemcachedClient.gets(url)).andReturn(v);
        EasyMock.expect(
                mockSerializer.readFrom(EasyMock.isA(InputStream.class)))
                .andReturn(existingValue);

        // update
        EasyMock.expect(
                mockMemcachedClient.cas(EasyMock.eq(url), EasyMock.eq(v
                        .getCas()), EasyMock.aryEq(new byte[0]))).andReturn(
                CASResponse.OK);
        mockSerializer.writeTo(EasyMock.same(updatedValue), EasyMock
                .isA(OutputStream.class));

        replayMocks();
        impl.updateEntry(url, callback);
        verifyMocks();
    }

    @Test
    public void testSingleCacheUpdateRetry() throws IOException,
            HttpCacheUpdateException {
        final String url = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        CASValue<Object> v = new CASValue<Object>(1234, new byte[] {});

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };
        // get existing old entry, will happen twice
        EasyMock.expect(mockMemcachedClient.gets(url)).andReturn(v).times(2);
        EasyMock.expect(
                mockSerializer.readFrom(EasyMock.isA(InputStream.class)))
                .andReturn(existingValue).times(2);

        // update but fail
        mockSerializer.writeTo(EasyMock.same(updatedValue), EasyMock
                .isA(OutputStream.class));
        EasyMock.expectLastCall().times(2);
        EasyMock.expect(
                mockMemcachedClient.cas(EasyMock.eq(url), EasyMock.eq(v
                        .getCas()), EasyMock.aryEq(new byte[0]))).andReturn(
                CASResponse.NOT_FOUND);

        // update again and succeed
        EasyMock.expect(
                mockMemcachedClient.cas(EasyMock.eq(url), EasyMock.eq(v
                        .getCas()), EasyMock.aryEq(new byte[0]))).andReturn(
                CASResponse.OK);

        replayMocks();
        impl.updateEntry(url, callback);
        verifyMocks();
    }

    @Test
    public void testCacheUpdateFail() throws IOException {
        final String url = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();
        CASValue<Object> v = new CASValue<Object>(1234, new byte[] {});

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {
            public HttpCacheEntry update(HttpCacheEntry old) {
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };

        // get existing old entry
        EasyMock.expect(mockMemcachedClient.gets(url)).andReturn(v).times(2);
        EasyMock.expect(
                mockSerializer.readFrom(EasyMock.isA(InputStream.class)))
                .andReturn(existingValue).times(2);

        // update but fail
        mockSerializer.writeTo(EasyMock.same(updatedValue), EasyMock
                .isA(OutputStream.class));
        EasyMock.expectLastCall().times(2);
        EasyMock.expect(
                mockMemcachedClient.cas(EasyMock.eq(url), EasyMock.eq(v
                        .getCas()), EasyMock.aryEq(new byte[0]))).andReturn(
                CASResponse.NOT_FOUND).times(2);

        replayMocks();
        try {
            impl.updateEntry(url, callback);
            fail("Expected HttpCacheUpdateException");
        } catch (HttpCacheUpdateException e) {
        }
        verifyMocks();
    }

}
