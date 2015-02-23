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

import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.Test;

@SuppressWarnings("boxing") // test code
public class TestEhcacheHttpCacheStorage extends TestCase {

    private Ehcache mockCache;
    private EhcacheHttpCacheStorage impl;
    private HttpCacheEntrySerializer mockSerializer;

    @Override
    public void setUp() {
        mockCache = mock(Ehcache.class);
        final CacheConfig config = CacheConfig.custom().setMaxUpdateRetries(1).build();
        mockSerializer = mock(HttpCacheEntrySerializer.class);
        impl = new EhcacheHttpCacheStorage(mockCache, config, mockSerializer);
    }

    @Test
    public void testCachePut() throws IOException {
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        final Element e = new Element(key, new byte[]{});

        impl.putEntry(key, value);

        verify(mockSerializer).writeTo(same(value), isA(OutputStream.class));
        verify(mockCache).put(e);;
    }

    @Test
    public void testCacheGetNullEntry() throws IOException {
        final String key = "foo";

        when(mockCache.get(key)).thenReturn(null);

        final HttpCacheEntry resultingEntry = impl.getEntry(key);

        verify(mockCache).get(key);

        assertNull(resultingEntry);
    }

    @Test
    public void testCacheGet() throws IOException {
        final String key = "foo";
        final HttpCacheEntry cachedValue = HttpTestUtils.makeCacheEntry();

        final Element element = new Element(key, new byte[]{});

        when(mockCache.get(key))
                .thenReturn(element);
        when(mockSerializer.readFrom(isA(InputStream.class)))
                .thenReturn(cachedValue);

        final HttpCacheEntry resultingEntry = impl.getEntry(key);

        verify(mockCache).get(key);
        verify(mockSerializer).readFrom(isA(InputStream.class));

        assertSame(cachedValue, resultingEntry);
    }

    @Test
    public void testCacheRemove() {
        final String key = "foo";

        when(mockCache.remove(key)).thenReturn(true);

        impl.removeEntry(key);
        verify(mockCache).remove(key);
    }

    @Test
    public void testCacheUpdateNullEntry() throws IOException, HttpCacheUpdateException {
        final String key = "foo";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        final Element element = new Element(key, new byte[]{});

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback(){
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old){
                assertNull(old);
                return updatedValue;
            }
        };

        // get empty old entry
        when(mockCache.get(key)).thenReturn(null);

        // put new entry
        mockSerializer.writeTo(same(updatedValue), isA(OutputStream.class));

        impl.updateEntry(key, callback);

        verify(mockCache).get(key);
        verify(mockSerializer).writeTo(same(updatedValue), isA(OutputStream.class));
        verify(mockCache).put(element);
    }

    @Test
    public void testCacheUpdate() throws IOException, HttpCacheUpdateException {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        final Element existingElement = new Element(key, new byte[]{});

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback(){
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old){
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };

        // get existing old entry
        when(mockCache.get(key)).thenReturn(existingElement);
        when(mockSerializer.readFrom(isA(InputStream.class))).thenReturn(existingValue);

        // update
        mockSerializer.writeTo(same(updatedValue), isA(OutputStream.class));
        when(mockCache.replace(same(existingElement), isA(Element.class))).thenReturn(true);

        impl.updateEntry(key, callback);

        verify(mockCache).get(key);
        verify(mockSerializer).readFrom(isA(InputStream.class));
        verify(mockSerializer).writeTo(same(updatedValue), isA(OutputStream.class));
        verify(mockCache).replace(same(existingElement), isA(Element.class));
    }

    @Test
    public void testSingleCacheUpdateRetry() throws IOException, HttpCacheUpdateException {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        final Element existingElement = new Element(key, new byte[]{});

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback(){
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old){
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };

        // get existing old entry, will happen twice
        when(mockCache.get(key)).thenReturn(existingElement);
        when(mockSerializer.readFrom(isA(InputStream.class))).thenReturn(existingValue);

        // Fail first and then succeed
        when(mockCache.replace(same(existingElement), isA(Element.class))).thenReturn(false).thenReturn(true);

        impl.updateEntry(key, callback);

        verify(mockCache, times(2)).get(key);
        verify(mockSerializer, times(2)).readFrom(isA(InputStream.class));
        verify(mockSerializer, times(2)).writeTo(same(updatedValue), isA(OutputStream.class));
        verify(mockCache, times(2)).replace(same(existingElement), isA(Element.class));
    }

    @Test
    public void testCacheUpdateFail() throws IOException {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        final Element existingElement = new Element(key, new byte[]{});

        final HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback(){
            @Override
            public HttpCacheEntry update(final HttpCacheEntry old){
                assertEquals(existingValue, old);
                return updatedValue;
            }
        };

        // get existing old entry
        when(mockCache.get(key)).thenReturn(existingElement);
        when(mockSerializer.readFrom(isA(InputStream.class))).thenReturn(existingValue);

        // update but fail
        when(mockCache.replace(same(existingElement), isA(Element.class))).thenReturn(false);

        try{
            impl.updateEntry(key, callback);
            fail("Expected HttpCacheUpdateException");
        } catch (final HttpCacheUpdateException e) { }

        verify(mockCache, times(2)).get(key);
        verify(mockSerializer, times(2)).readFrom(isA(InputStream.class));
        verify(mockSerializer, times(2)).writeTo(same(updatedValue), isA(OutputStream.class));
        verify(mockCache, times(2)).replace(same(existingElement), isA(Element.class));
    }
}
