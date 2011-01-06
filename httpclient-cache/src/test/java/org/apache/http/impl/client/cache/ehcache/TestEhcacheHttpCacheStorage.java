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
package org.apache.http.impl.client.cache.ehcache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import junit.framework.TestCase;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.HttpTestUtils;
import org.easymock.EasyMock;
import org.junit.Test;

public class TestEhcacheHttpCacheStorage extends TestCase {

    private Ehcache mockCache;
    private EhcacheHttpCacheStorage impl;
    private HttpCacheEntrySerializer mockSerializer;

    @Override
    public void setUp() {
        mockCache = EasyMock.createMock(Ehcache.class);
        CacheConfig config = new CacheConfig();
        config.setMaxUpdateRetries(1);
        mockSerializer = EasyMock.createMock(HttpCacheEntrySerializer.class);
        impl = new EhcacheHttpCacheStorage(mockCache, config, mockSerializer);
    }

    private void replayMocks(){
        EasyMock.replay(mockCache);
        EasyMock.replay(mockSerializer);
    }

    private void verifyMocks(){
        EasyMock.verify(mockCache);
        EasyMock.verify(mockSerializer);
    }

    @Test
    public void testCachePut() throws IOException {
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        Element e = new Element(key, new byte[]{});

        mockSerializer.writeTo(EasyMock.same(value), EasyMock.isA(OutputStream.class));
        mockCache.put(e);

        replayMocks();
        impl.putEntry(key, value);
        verifyMocks();
    }

    @Test
    public void testCacheGetNullEntry() throws IOException {
        final String key = "foo";

        EasyMock.expect(mockCache.get(key)).andReturn(null);

        replayMocks();
        HttpCacheEntry resultingEntry = impl.getEntry(key);
        verifyMocks();

        assertNull(resultingEntry);
    }

    @Test
    public void testCacheGet() throws IOException {
        final String key = "foo";
        final HttpCacheEntry cachedValue = HttpTestUtils.makeCacheEntry();

        Element element = new Element(key, new byte[]{});

        EasyMock.expect(mockCache.get(key))
                .andReturn(element);
        EasyMock.expect(mockSerializer.readFrom(EasyMock.isA(InputStream.class)))
                .andReturn(cachedValue);

        replayMocks();
        HttpCacheEntry resultingEntry = impl.getEntry(key);
        verifyMocks();

        assertSame(cachedValue, resultingEntry);
    }

    @Test
    public void testCacheRemove() {
        final String key = "foo";

        EasyMock.expect(mockCache.remove(key)).andReturn(true);

        replayMocks();
        impl.removeEntry(key);
        verifyMocks();
    }

    @Test
    public void testCacheUpdateNullEntry() throws IOException, HttpCacheUpdateException {
        final String key = "foo";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Element element = new Element(key, new byte[]{});

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback(){
            public HttpCacheEntry update(HttpCacheEntry old){
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        EasyMock.expect(mockCache.get(key)).andReturn(null);

        // put new entry
        mockSerializer.writeTo(EasyMock.same(updatedValue), EasyMock.isA(OutputStream.class));
        mockCache.put(element);

        replayMocks();
        impl.updateEntry(key, callback);
        verifyMocks();
    }

    @Test
    public void testCacheUpdate() throws IOException, HttpCacheUpdateException {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Element existingElement = new Element(key, new byte[]{});

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback(){
            public HttpCacheEntry update(HttpCacheEntry old){
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };

        // get existing old entry
        EasyMock.expect(mockCache.get(key)).andReturn(existingElement);
        EasyMock.expect(mockSerializer.readFrom(EasyMock.isA(InputStream.class))).andReturn(existingValue);

        // update
        mockSerializer.writeTo(EasyMock.same(updatedValue), EasyMock.isA(OutputStream.class));
        EasyMock.expect(mockCache.replace(EasyMock.same(existingElement), EasyMock.isA(Element.class))).andReturn(true);

        replayMocks();
        impl.updateEntry(key, callback);
        verifyMocks();
    }

    @Test
    public void testSingleCacheUpdateRetry() throws IOException, HttpCacheUpdateException {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Element existingElement = new Element(key, new byte[]{});

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback(){
            public HttpCacheEntry update(HttpCacheEntry old){
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };

        // get existing old entry, will happen twice
        EasyMock.expect(mockCache.get(key)).andReturn(existingElement).times(2);
        EasyMock.expect(mockSerializer.readFrom(EasyMock.isA(InputStream.class))).andReturn(existingValue).times(2);

        // update but fail
        mockSerializer.writeTo(EasyMock.same(updatedValue), EasyMock.isA(OutputStream.class));
        EasyMock.expectLastCall().times(2);
        EasyMock.expect(mockCache.replace(EasyMock.same(existingElement), EasyMock.isA(Element.class))).andReturn(false);

        // update again and succeed
        EasyMock.expect(mockCache.replace(EasyMock.same(existingElement), EasyMock.isA(Element.class))).andReturn(true);

        replayMocks();
        impl.updateEntry(key, callback);
        verifyMocks();
    }

    @Test
    public void testCacheUpdateFail() throws IOException {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Element existingElement = new Element(key, new byte[]{});

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback(){
            public HttpCacheEntry update(HttpCacheEntry old){
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };

        // get existing old entry
        EasyMock.expect(mockCache.get(key)).andReturn(existingElement).times(2);
        EasyMock.expect(mockSerializer.readFrom(EasyMock.isA(InputStream.class))).andReturn(existingValue).times(2);

        // update but fail
        mockSerializer.writeTo(EasyMock.same(updatedValue), EasyMock.isA(OutputStream.class));
        EasyMock.expectLastCall().times(2);
        EasyMock.expect(mockCache.replace(EasyMock.same(existingElement), EasyMock.isA(Element.class))).andReturn(false).times(2);

        replayMocks();
        try{
            impl.updateEntry(key, callback);
            fail("Expected HttpCacheUpdateException");
        } catch (HttpCacheUpdateException e) { }
        verifyMocks();
    }
}
