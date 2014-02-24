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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.client.cache.DefaultHttpCacheEntrySerializer;
import org.apache.http.impl.client.cache.HttpTestUtils;
import org.junit.Before;
import org.junit.Test;


public class TestMemcachedCacheEntryImpl {

    private MemcachedCacheEntryImpl impl;
    private HttpCacheEntry entry;

    @Before
    public void setUp() {
        entry = HttpTestUtils.makeCacheEntry();
        impl = new MemcachedCacheEntryImpl("foo", entry);
    }

    @Test
    public void canBeCreatedEmpty() {
        impl = new MemcachedCacheEntryImpl();
        assertNull(impl.getStorageKey());
        assertNull(impl.getHttpCacheEntry());
    }

    @Test
    public void canBeSerialized() {
        final byte[] bytes = impl.toByteArray();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    public void knowsItsCacheKey() {
        assertEquals("foo", impl.getStorageKey());
    }

    @Test
    public void knowsItsCacheEntry() {
        assertEquals(entry, impl.getHttpCacheEntry());
    }

    @Test
    public void canBeReconstitutedFromByteArray() throws Exception {
        final String key = impl.getStorageKey();
        final HttpCacheEntry entry1 = impl.getHttpCacheEntry();
        final byte[] bytes = impl.toByteArray();
        impl = new MemcachedCacheEntryImpl();
        impl.set(bytes);

        assertEquals(key, impl.getStorageKey());
        assertEquivalent(entry1, impl.getHttpCacheEntry());
    }

    @Test(expected=MemcachedSerializationException.class)
    public void cannotReconstituteFromGarbage() {
        impl = new MemcachedCacheEntryImpl();
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        impl.set(bytes);
    }

    private void assertEquivalent(final HttpCacheEntry entry,
            final HttpCacheEntry resultEntry) throws IOException {
        /* Ugh. Implementing HttpCacheEntry#equals is problematic
         * due to the Resource response body (may cause unexpected
         * I/O to users). Checking that two entries
         * serialize to the same bytes seems simpler, on the whole,
         * (while still making for a somewhat yucky test). At
         * least we encapsulate it off here in its own method so
         * the test that uses it remains clear.
         */
        final DefaultHttpCacheEntrySerializer ser = new DefaultHttpCacheEntrySerializer();
        final ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        ser.writeTo(entry, bos1);
        final byte[] bytes1 = bos1.toByteArray();
        final ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        ser.writeTo(resultEntry, bos2);
        final byte[] bytes2 = bos2.toByteArray();
        assertEquals(bytes1.length, bytes2.length);
        for(int i = 0; i < bytes1.length; i++) {
            assertEquals(bytes1[i], bytes2[i]);
        }
    }
}
