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
package org.apache.hc.client5.http.impl.cache;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@SuppressWarnings("boxing") // test code
public class TestAbstractSerializingCacheStorage {

    public static byte[] serialize(final String key, final HttpCacheEntry value) throws ResourceIOException {
        return ByteArrayCacheEntrySerializer.INSTANCE.serialize(new HttpCacheStorageEntry(key, value));
    }

    private AbstractBinaryCacheStorage<String> impl;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        impl = Mockito.mock(AbstractBinaryCacheStorage.class,
                Mockito.withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS).useConstructor(3));
    }

    @Test
    public void testCachePut() throws Exception {
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");

        impl.putEntry(key, value);

        final ArgumentCaptor<byte[]> argumentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(impl).store(eq("bar"), argumentCaptor.capture());
        Assert.assertArrayEquals(serialize(key, value), argumentCaptor.getValue());
    }

    @Test
    public void testCacheGetNullEntry() throws Exception {
        final String key = "foo";

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.restore("bar")).thenReturn(null);

        final HttpCacheEntry resultingEntry = impl.getEntry(key);

        verify(impl).restore("bar");

        Assert.assertThat(resultingEntry, CoreMatchers.nullValue());
    }

    @Test
    public void testCacheGet() throws Exception {
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.restore("bar")).thenReturn(serialize(key, value));

        final HttpCacheEntry resultingEntry = impl.getEntry(key);

        verify(impl).restore("bar");

        Assert.assertThat(resultingEntry, HttpCacheEntryMatcher.equivalent(value));
    }

    @Test
    public void testCacheGetKeyMismatch() throws Exception {
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.restore("bar")).thenReturn(serialize("not-foo", value));

        final HttpCacheEntry resultingEntry = impl.getEntry(key);

        verify(impl).restore("bar");

        Assert.assertThat(resultingEntry, CoreMatchers.nullValue());
    }

    @Test
    public void testCacheRemove()  throws Exception{
        final String key = "foo";

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        impl.removeEntry(key);

        verify(impl).delete("bar");
    }

    @Test
    public void testCacheUpdateNullEntry() throws Exception {
        final String key = "foo";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn(null);

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                Assert.assertThat(existing, CoreMatchers.nullValue());
                return updatedValue;
            }

        });

        verify(impl).getForUpdateCAS("bar");
        verify(impl).store(ArgumentMatchers.eq("bar"), ArgumentMatchers.<byte[]>any());
    }

    @Test
    public void testCacheCASUpdate() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn("stuff");
        when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        when(impl.updateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any())).thenReturn(true);

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                return updatedValue;
            }

        });

        verify(impl).getForUpdateCAS("bar");
        verify(impl).getStorageObject("stuff");
        verify(impl).updateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any());
    }

    @Test
    public void testCacheCASUpdateKeyMismatch() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn("stuff");
        when(impl.getStorageObject("stuff")).thenReturn(serialize("not-foo", existingValue));
        when(impl.updateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any())).thenReturn(true);

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                Assert.assertThat(existing, CoreMatchers.nullValue());
                return updatedValue;
            }

        });

        verify(impl).getForUpdateCAS("bar");
        verify(impl).getStorageObject("stuff");
        verify(impl).store(ArgumentMatchers.eq("bar"), ArgumentMatchers.<byte[]>any());
    }

    @Test
    public void testSingleCacheUpdateRetry() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn("stuff");
        when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        when(impl.updateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any())).thenReturn(false, true);

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                return updatedValue;
            }

        });

        verify(impl, Mockito.times(2)).getForUpdateCAS("bar");
        verify(impl, Mockito.times(2)).getStorageObject("stuff");
        verify(impl, Mockito.times(2)).updateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any());
    }

    @Test
    public void testCacheUpdateFail() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn("stuff");
        when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        when(impl.updateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any())).thenReturn(false, false, false, true);

        try {
            impl.updateEntry(key, new HttpCacheCASOperation() {

                @Override
                public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                    return updatedValue;
                }

            });
            Assert.fail("HttpCacheUpdateException expected");
        } catch (final HttpCacheUpdateException ignore) {
        }

        verify(impl, Mockito.times(3)).getForUpdateCAS("bar");
        verify(impl, Mockito.times(3)).getStorageObject("stuff");
        verify(impl, Mockito.times(3)).updateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any());
    }

    @Test
    public void testBulkGet() throws Exception {
        final String key1 = "foo this";
        final String key2 = "foo that";
        final String storageKey1 = "bar this";
        final String storageKey2 = "bar that";
        final HttpCacheEntry value1 = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry value2 = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key1)).thenReturn(storageKey1);
        when(impl.digestToStorageKey(key2)).thenReturn(storageKey2);

        when(impl.bulkRestore(ArgumentMatchers.<String>anyCollection())).thenAnswer(new Answer<Map<String, byte[]>>() {

            @Override
            public Map<String, byte[]> answer(final InvocationOnMock invocation) throws Throwable {
                final Collection<String> keys = invocation.getArgument(0);
                final Map<String, byte[]> resultMap = new HashMap<>();
                if (keys.contains(storageKey1)) {
                    resultMap.put(storageKey1, serialize(key1, value1));
                }
                if (keys.contains(storageKey2)) {
                    resultMap.put(storageKey2, serialize(key2, value2));
                }
                return resultMap;
            }
        });

        final Map<String, HttpCacheEntry> entryMap = impl.getEntries(Arrays.asList(key1, key2));
        Assert.assertThat(entryMap, CoreMatchers.notNullValue());
        Assert.assertThat(entryMap.get(key1), HttpCacheEntryMatcher.equivalent(value1));
        Assert.assertThat(entryMap.get(key2), HttpCacheEntryMatcher.equivalent(value2));

        verify(impl, Mockito.times(2)).digestToStorageKey(key1);
        verify(impl, Mockito.times(2)).digestToStorageKey(key2);
        verify(impl).bulkRestore(Arrays.asList(storageKey1, storageKey2));
    }

    @Test
    public void testBulkGetKeyMismatch() throws Exception {
        final String key1 = "foo this";
        final String key2 = "foo that";
        final String storageKey1 = "bar this";
        final String storageKey2 = "bar that";
        final HttpCacheEntry value1 = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry value2 = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key1)).thenReturn(storageKey1);
        when(impl.digestToStorageKey(key2)).thenReturn(storageKey2);

        when(impl.bulkRestore(ArgumentMatchers.<String>anyCollection())).thenAnswer(new Answer<Map<String, byte[]>>() {

            @Override
            public Map<String, byte[]> answer(final InvocationOnMock invocation) throws Throwable {
                final Collection<String> keys = invocation.getArgument(0);
                final Map<String, byte[]> resultMap = new HashMap<>();
                if (keys.contains(storageKey1)) {
                    resultMap.put(storageKey1, serialize(key1, value1));
                }
                if (keys.contains(storageKey2)) {
                    resultMap.put(storageKey2, serialize("not foo", value2));
                }
                return resultMap;
            }
        });

        final Map<String, HttpCacheEntry> entryMap = impl.getEntries(Arrays.asList(key1, key2));
        Assert.assertThat(entryMap, CoreMatchers.notNullValue());
        Assert.assertThat(entryMap.get(key1), HttpCacheEntryMatcher.equivalent(value1));
        Assert.assertThat(entryMap.get(key2), CoreMatchers.nullValue());

        verify(impl, Mockito.times(2)).digestToStorageKey(key1);
        verify(impl, Mockito.times(2)).digestToStorageKey(key2);
        verify(impl).bulkRestore(Arrays.asList(storageKey1, storageKey2));
    }

}
