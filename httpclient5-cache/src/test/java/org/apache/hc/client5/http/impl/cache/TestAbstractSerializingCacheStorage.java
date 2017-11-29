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

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
        verify(impl).store(Mockito.eq("bar"), Mockito.<byte[]>any());
    }

    @Test
    public void testCacheCASUpdate() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn("stuff");
        when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        when(impl.updateCAS(Mockito.eq("bar"), Mockito.eq("stuff"), Mockito.<byte[]>any())).thenReturn(true);

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                return updatedValue;
            }

        });

        verify(impl).getForUpdateCAS("bar");
        verify(impl).getStorageObject("stuff");
        verify(impl).updateCAS(Mockito.eq("bar"), Mockito.eq("stuff"), Mockito.<byte[]>any());
    }

    @Test
    public void testCacheCASUpdateKeyMismatch() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn("stuff");
        when(impl.getStorageObject("stuff")).thenReturn(serialize("not-foo", existingValue));
        when(impl.updateCAS(Mockito.eq("bar"), Mockito.eq("stuff"), Mockito.<byte[]>any())).thenReturn(true);

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                Assert.assertThat(existing, CoreMatchers.nullValue());
                return updatedValue;
            }

        });

        verify(impl).getForUpdateCAS("bar");
        verify(impl).getStorageObject("stuff");
        verify(impl).store(Mockito.eq("bar"), Mockito.<byte[]>any());
    }

    @Test
    public void testSingleCacheUpdateRetry() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn("stuff");
        when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        when(impl.updateCAS(Mockito.eq("bar"), Mockito.eq("stuff"), Mockito.<byte[]>any())).thenReturn(false, true);

        impl.updateEntry(key, new HttpCacheCASOperation() {

            @Override
            public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                return updatedValue;
            }

        });

        verify(impl, Mockito.times(2)).getForUpdateCAS("bar");
        verify(impl, Mockito.times(2)).getStorageObject("stuff");
        verify(impl, Mockito.times(2)).updateCAS(Mockito.eq("bar"), Mockito.eq("stuff"), Mockito.<byte[]>any());
    }

    @Test
    public void testCacheUpdateFail() throws Exception {
        final String key = "foo";
        final HttpCacheEntry existingValue = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry updatedValue = HttpTestUtils.makeCacheEntry();

        when(impl.digestToStorageKey(key)).thenReturn("bar");
        when(impl.getForUpdateCAS("bar")).thenReturn("stuff");
        when(impl.getStorageObject("stuff")).thenReturn(serialize(key, existingValue));
        when(impl.updateCAS(Mockito.eq("bar"), Mockito.eq("stuff"), Mockito.<byte[]>any())).thenReturn(false, false, false, true);

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
        verify(impl, Mockito.times(3)).updateCAS(Mockito.eq("bar"), Mockito.eq("stuff"), Mockito.<byte[]>any());
    }
}
