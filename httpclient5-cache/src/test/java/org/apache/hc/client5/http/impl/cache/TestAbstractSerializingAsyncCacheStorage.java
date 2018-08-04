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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class TestAbstractSerializingAsyncCacheStorage {

    @Mock
    private Cancellable cancellable;
    @Mock
    private FutureCallback<Boolean> operationCallback;
    @Mock
    private FutureCallback<HttpCacheEntry> cacheEntryCallback;
    @Mock
    private FutureCallback<Map<String, HttpCacheEntry>> bulkCacheEntryCallback;

    private AbstractBinaryAsyncCacheStorage<String> impl;

    public static byte[] serialize(final String key, final HttpCacheEntry value) throws ResourceIOException {
        return ByteArrayCacheEntrySerializer.INSTANCE.serialize(new HttpCacheStorageEntry(key, value));
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        impl = Mockito.mock(AbstractBinaryAsyncCacheStorage.class,
                Mockito.withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS).useConstructor(3));
    }

    @Test
    public void testCachePut() throws Exception {
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.store(
                ArgumentMatchers.eq("bar"),
                ArgumentMatchers.<byte[]>any(),
                ArgumentMatchers.<FutureCallback<Boolean>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<Boolean> callback = invocation.getArgument(2);
                callback.completed(true);
                return cancellable;
            }

        });

        impl.putEntry(key, value, operationCallback);

        final ArgumentCaptor<byte[]> argumentCaptor = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(impl).store(ArgumentMatchers.eq("bar"), argumentCaptor.capture(), ArgumentMatchers.<FutureCallback<Boolean>>any());
        Assert.assertArrayEquals(serialize(key, value), argumentCaptor.getValue());
        Mockito.verify(operationCallback).completed(Boolean.TRUE);
    }

    @Test
    public void testCacheGetNullEntry() throws Exception {
        final String key = "foo";

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.restore(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<byte[]>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<byte[]> callback = invocation.getArgument(1);
                callback.completed(null);
                return cancellable;
            }

        });

        impl.getEntry(key, cacheEntryCallback);
        final ArgumentCaptor<HttpCacheEntry> argumentCaptor = ArgumentCaptor.forClass(HttpCacheEntry.class);
        Mockito.verify(cacheEntryCallback).completed(argumentCaptor.capture());
        Assert.assertThat(argumentCaptor.getValue(), CoreMatchers.nullValue());
        Mockito.verify(impl).restore(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<byte[]>>any());
    }

    @Test
    public void testCacheGet() throws Exception {
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.restore(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<byte[]>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<byte[]> callback = invocation.getArgument(1);
                callback.completed(serialize(key, value));
                return cancellable;
            }

        });

        impl.getEntry(key, cacheEntryCallback);
        final ArgumentCaptor<HttpCacheEntry> argumentCaptor = ArgumentCaptor.forClass(HttpCacheEntry.class);
        Mockito.verify(cacheEntryCallback).completed(argumentCaptor.capture());
        final HttpCacheEntry resultingEntry = argumentCaptor.getValue();
        Assert.assertThat(resultingEntry, HttpCacheEntryMatcher.equivalent(value));
        Mockito.verify(impl).restore(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<byte[]>>any());
    }

    @Test
    public void testCacheGetKeyMismatch() throws Exception {
        final String key = "foo";
        final HttpCacheEntry value = HttpTestUtils.makeCacheEntry();
        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.restore(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<byte[]>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<byte[]> callback = invocation.getArgument(1);
                callback.completed(serialize("not-foo", value));
                return cancellable;
            }

        });

        impl.getEntry(key, cacheEntryCallback);
        final ArgumentCaptor<HttpCacheEntry> argumentCaptor = ArgumentCaptor.forClass(HttpCacheEntry.class);
        Mockito.verify(cacheEntryCallback).completed(argumentCaptor.capture());
        Assert.assertThat(argumentCaptor.getValue(), CoreMatchers.nullValue());
        Mockito.verify(impl).restore(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<byte[]>>any());
    }

    @Test
    public void testCacheRemove()  throws Exception{
        final String key = "foo";

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.delete(
                ArgumentMatchers.eq("bar"),
                ArgumentMatchers.<FutureCallback<Boolean>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<Boolean> callback = invocation.getArgument(1);
                callback.completed(true);
                return cancellable;
            }

        });
        impl.removeEntry(key, operationCallback);

        Mockito.verify(impl).delete("bar", operationCallback);
        Mockito.verify(operationCallback).completed(Boolean.TRUE);
    }

    @Test
    public void testCacheUpdateNullEntry() throws Exception {
        final String key = "foo";
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<byte[]> callback = invocation.getArgument(1);
                callback.completed(null);
                return cancellable;
            }

        });
        Mockito.when(impl.store(
                ArgumentMatchers.eq("bar"),
                ArgumentMatchers.<byte[]>any(),
                ArgumentMatchers.<FutureCallback<Boolean>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<Boolean> callback = invocation.getArgument(2);
                callback.completed(true);
                return cancellable;
            }

        });

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                Assert.assertThat(existing, CoreMatchers.nullValue());
                return updatedValue;
            }

        }, operationCallback);

        Mockito.verify(impl).getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any());
        Mockito.verify(impl).store(ArgumentMatchers.eq("bar"), ArgumentMatchers.<byte[]>any(), ArgumentMatchers.<FutureCallback<Boolean>>any());
        Mockito.verify(operationCallback).completed(Boolean.TRUE);
    }

    @Test
    public void testCacheCASUpdate() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<String> callback = invocation.getArgument(1);
                callback.completed("stuff");
                return cancellable;
            }

        });
        Mockito.when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        Mockito.when(impl.updateCAS(
                ArgumentMatchers.eq("bar"),
                ArgumentMatchers.eq("stuff"),
                ArgumentMatchers.<byte[]>any(),
                ArgumentMatchers.<FutureCallback<Boolean>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<Boolean> callback = invocation.getArgument(3);
                callback.completed(true);
                return cancellable;
            }

        });

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                return updatedValue;
            }

        }, operationCallback);

        Mockito.verify(impl).getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any());
        Mockito.verify(impl).getStorageObject("stuff");
        Mockito.verify(impl).updateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any(), ArgumentMatchers.<FutureCallback<Boolean>>any());
        Mockito.verify(operationCallback).completed(Boolean.TRUE);
    }

    @Test
    public void testCacheCASUpdateKeyMismatch() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any())).thenAnswer(
                new Answer<Cancellable>() {

                    @Override
                    public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                        final FutureCallback<String> callback = invocation.getArgument(1);
                        callback.completed("stuff");
                        return cancellable;
                    }

                });
        Mockito.when(impl.getStorageObject("stuff")).thenReturn(serialize("not-foo", existingValue));
        Mockito.when(impl.store(
                ArgumentMatchers.eq("bar"),
                ArgumentMatchers.<byte[]>any(),
                ArgumentMatchers.<FutureCallback<Boolean>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<Boolean> callback = invocation.getArgument(2);
                callback.completed(true);
                return cancellable;
            }

        });

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                Assert.assertThat(existing, CoreMatchers.nullValue());
                return updatedValue;
            }

        }, operationCallback);

        Mockito.verify(impl).getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any());
        Mockito.verify(impl).getStorageObject("stuff");
        Mockito.verify(impl, Mockito.never()).updateCAS(
                ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any(), ArgumentMatchers.<FutureCallback<Boolean>>any());
        Mockito.verify(impl).store(ArgumentMatchers.eq("bar"), ArgumentMatchers.<byte[]>any(), ArgumentMatchers.<FutureCallback<Boolean>>any());
        Mockito.verify(operationCallback).completed(Boolean.TRUE);
    }

    @Test
    public void testSingleCacheUpdateRetry() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any())).thenAnswer(
                new Answer<Cancellable>() {

                    @Override
                    public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                        final FutureCallback<String> callback = invocation.getArgument(1);
                        callback.completed("stuff");
                        return cancellable;
                    }

                });
        Mockito.when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        final AtomicInteger count = new AtomicInteger(0);
        Mockito.when(impl.updateCAS(
                ArgumentMatchers.eq("bar"),
                ArgumentMatchers.eq("stuff"),
                ArgumentMatchers.<byte[]>any(),
                ArgumentMatchers.<FutureCallback<Boolean>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<Boolean> callback = invocation.getArgument(3);
                if (count.incrementAndGet() == 1) {
                    callback.completed(false);
                } else {
                    callback.completed(true);
                }
                return cancellable;
            }

        });

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                return updatedValue;
            }

        }, operationCallback);

        Mockito.verify(impl, Mockito.times(2)).getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any());
        Mockito.verify(impl, Mockito.times(2)).getStorageObject("stuff");
        Mockito.verify(impl, Mockito.times(2)).updateCAS(
                ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any(), ArgumentMatchers.<FutureCallback<Boolean>>any());
        Mockito.verify(operationCallback).completed(Boolean.TRUE);
    }

    @Test
    public void testCacheUpdateFail() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        Mockito.when(impl.digestToStorageKey(key)).thenReturn("bar");
        Mockito.when(impl.getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any())).thenAnswer(
                new Answer<Cancellable>() {

                    @Override
                    public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                        final FutureCallback<String> callback = invocation.getArgument(1);
                        callback.completed("stuff");
                        return cancellable;
                    }

                });
        Mockito.when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        final AtomicInteger count = new AtomicInteger(0);
        Mockito.when(impl.updateCAS(
                ArgumentMatchers.eq("bar"),
                ArgumentMatchers.eq("stuff"),
                ArgumentMatchers.<byte[]>any(),
                ArgumentMatchers.<FutureCallback<Boolean>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final FutureCallback<Boolean> callback = invocation.getArgument(3);
                if (count.incrementAndGet() <= 3) {
                    callback.completed(false);
                } else {
                    callback.completed(true);
                }
                return cancellable;
            }

        });

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                return updatedValue;
            }

        }, operationCallback);

        Mockito.verify(impl, Mockito.times(3)).getForUpdateCAS(ArgumentMatchers.eq("bar"), ArgumentMatchers.<FutureCallback<String>>any());
        Mockito.verify(impl, Mockito.times(3)).getStorageObject("stuff");
        Mockito.verify(impl, Mockito.times(3)).updateCAS(
                ArgumentMatchers.eq("bar"), ArgumentMatchers.eq("stuff"), ArgumentMatchers.<byte[]>any(), ArgumentMatchers.<FutureCallback<Boolean>>any());
        Mockito.verify(operationCallback).failed(ArgumentMatchers.<HttpCacheUpdateException>any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBulkGet() throws Exception {
        final String key1 = "foo this";
        final String key2 = "foo that";
        final String storageKey1 = "bar this";
        final String storageKey2 = "bar that";
        final HttpCacheEntry value1 = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry value2 = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key1)).thenReturn(storageKey1);
        when(impl.digestToStorageKey(key2)).thenReturn(storageKey2);

        when(impl.bulkRestore(
                ArgumentMatchers.<String>anyCollection(),
                ArgumentMatchers.<FutureCallback<Map<String, byte[]>>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final Collection<String> keys = invocation.getArgument(0);
                final FutureCallback<Map<String, byte[]>> callback = invocation.getArgument(1);
                final Map<String, byte[]> resultMap = new HashMap<>();
                if (keys.contains(storageKey1)) {
                    resultMap.put(storageKey1, serialize(key1, value1));
                }
                if (keys.contains(storageKey2)) {
                    resultMap.put(storageKey2, serialize(key2, value2));
                }
                callback.completed(resultMap);
                return cancellable;
            }
        });

        impl.getEntries(Arrays.asList(key1, key2), bulkCacheEntryCallback);
        final ArgumentCaptor<Map<String, HttpCacheEntry>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(bulkCacheEntryCallback).completed(argumentCaptor.capture());

        final Map<String, HttpCacheEntry> entryMap = argumentCaptor.getValue();
        Assert.assertThat(entryMap, CoreMatchers.notNullValue());
        Assert.assertThat(entryMap.get(key1), HttpCacheEntryMatcher.equivalent(value1));
        Assert.assertThat(entryMap.get(key2), HttpCacheEntryMatcher.equivalent(value2));

        verify(impl, Mockito.times(2)).digestToStorageKey(key1);
        verify(impl, Mockito.times(2)).digestToStorageKey(key2);
        verify(impl).bulkRestore(
                ArgumentMatchers.eq(Arrays.asList(storageKey1, storageKey2)),
                ArgumentMatchers.<FutureCallback<Map<String, byte[]>>>any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBulkGetKeyMismatch() throws Exception {
        final String key1 = "foo this";
        final String key2 = "foo that";
        final String storageKey1 = "bar this";
        final String storageKey2 = "bar that";
        final HttpCacheEntry value1 = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry value2 = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key1)).thenReturn(storageKey1);
        when(impl.digestToStorageKey(key2)).thenReturn(storageKey2);

        when(impl.bulkRestore(
                ArgumentMatchers.<String>anyCollection(),
                ArgumentMatchers.<FutureCallback<Map<String, byte[]>>>any())).thenAnswer(new Answer<Cancellable>() {

            @Override
            public Cancellable answer(final InvocationOnMock invocation) throws Throwable {
                final Collection<String> keys = invocation.getArgument(0);
                final FutureCallback<Map<String, byte[]>> callback = invocation.getArgument(1);
                final Map<String, byte[]> resultMap = new HashMap<>();
                if (keys.contains(storageKey1)) {
                    resultMap.put(storageKey1, serialize(key1, value1));
                }
                if (keys.contains(storageKey2)) {
                    resultMap.put(storageKey2, serialize("not foo", value2));
                }
                callback.completed(resultMap);
                return cancellable;
            }
        });

        impl.getEntries(Arrays.asList(key1, key2), bulkCacheEntryCallback);
        final ArgumentCaptor<Map<String, HttpCacheEntry>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(bulkCacheEntryCallback).completed(argumentCaptor.capture());

        final Map<String, HttpCacheEntry> entryMap = argumentCaptor.getValue();
        Assert.assertThat(entryMap, CoreMatchers.notNullValue());
        Assert.assertThat(entryMap.get(key1), HttpCacheEntryMatcher.equivalent(value1));
        Assert.assertThat(entryMap.get(key2), CoreMatchers.nullValue());

        verify(impl, Mockito.times(2)).digestToStorageKey(key1);
        verify(impl, Mockito.times(2)).digestToStorageKey(key2);
        verify(impl).bulkRestore(
                ArgumentMatchers.eq(Arrays.asList(storageKey1, storageKey2)),
                ArgumentMatchers.<FutureCallback<Map<String, byte[]>>>any());
    }

}
