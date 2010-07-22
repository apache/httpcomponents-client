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
package org.apache.http.impl.client.cache;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheOperationException;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class TestResponseCache {

    private BasicHttpCache cache;
    private HttpCacheEntry mockEntry;

    @Before
    public void setUp() {
        cache = new BasicHttpCache(5);
        mockEntry = EasyMock.createMock(HttpCacheEntry.class);
    }

    @Test
    public void testEntryRemainsInCacheWhenPutThere() {
        cache.putEntry("foo", mockEntry);

        HttpCacheEntry cachedEntry = cache.getEntry("foo");

        Assert.assertSame(mockEntry, cachedEntry);
    }

    @Test
    public void testRemovedEntriesDoNotExistAnymore() {
        cache.putEntry("foo", mockEntry);

        cache.removeEntry("foo");

        HttpCacheEntry nullEntry = cache.getEntry("foo");

        Assert.assertNull(nullEntry);
    }

    @Test
    public void testCacheHoldsNoMoreThanSpecifiedMaxEntries() {
        BasicHttpCache cache = new BasicHttpCache(1);

        HttpCacheEntry entry1 = EasyMock.createMock(HttpCacheEntry.class);
        cache.putEntry("foo", entry1);

        HttpCacheEntry entry2 = EasyMock.createMock(HttpCacheEntry.class);
        cache.putEntry("bar", entry2);

        HttpCacheEntry entry3 = EasyMock.createMock(HttpCacheEntry.class);
        cache.putEntry("baz", entry3);

        HttpCacheEntry e1 = cache.getEntry("foo");
        Assert.assertNull("Got foo entry when we should not", e1);

        HttpCacheEntry e2 = cache.getEntry("bar");
        Assert.assertNull("Got bar entry when we should not", e2);

        HttpCacheEntry e3 = cache.getEntry("baz");
        Assert.assertNotNull("Did not get baz entry, but should have", e3);
    }

    @Test
    public void testSmallCacheKeepsMostRecentlyUsedEntry() {

        final int max_size = 3;
        BasicHttpCache cache = new BasicHttpCache(max_size);

        // fill the cache with entries
        for (int i = 0; i < max_size; i++) {
            HttpCacheEntry entry = EasyMock.createMock(HttpCacheEntry.class);
            cache.putEntry("entry" + i, entry);
        }

        // read the eldest entry to make it the MRU entry
        cache.getEntry("entry0");

        // add another entry, which kicks out the eldest (should be the 2nd one
        // created), and becomes the new MRU entry
        HttpCacheEntry newMru = EasyMock.createMock(HttpCacheEntry.class);
        cache.putEntry("newMru", newMru);

        // get the original second eldest
        HttpCacheEntry gone = cache.getEntry("entry1");
        Assert.assertNull("entry1 should be gone", gone);

        HttpCacheEntry latest = cache.getEntry("newMru");
        Assert.assertNotNull("latest entry should still be there", latest);

        HttpCacheEntry originalEldest = cache.getEntry("entry0");
        Assert.assertNotNull("original eldest entry should still be there", originalEldest);
    }

    @Test
    public void testZeroMaxSizeCacheDoesNotStoreAnything() {
        BasicHttpCache cache = new BasicHttpCache(0);

        HttpCacheEntry entry = EasyMock.createMock(HttpCacheEntry.class);
        cache.putEntry("foo", entry);

        HttpCacheEntry gone = cache.getEntry("foo");

        Assert.assertNull("This cache should not have anything in it!", gone);
    }

    @Test
    @Ignore
    public void testCacheEntryCallbackUpdatesCacheEntry() throws HttpCacheOperationException, IOException {

        final byte[] expectedArray = new byte[] { 1, 2, 3, 4, 5 };

        HttpCacheEntry entry = EasyMock.createMock(HttpCacheEntry.class);
        HttpCacheEntry entry2 = EasyMock.createMock(HttpCacheEntry.class);

        cache.putEntry("foo", entry);
        cache.putEntry("bar", entry2);

        cache.updateEntry("foo", new HttpCacheUpdateCallback() {

            public HttpCacheEntry update(HttpCacheEntry existing) {
                HttpCacheEntry updated = new HttpCacheEntry(
                        existing.getRequestDate(),
                        existing.getRequestDate(),
                        existing.getStatusLine(),
                        existing.getAllHeaders(),
                        new ByteArrayEntity(expectedArray),
                        null);
                cache.removeEntry("bar");
                return updated;
            }
        });

        HttpCacheEntry afterUpdate = cache.getEntry("foo");
        HttpCacheEntry bar = cache.getEntry("bar");

        Assert.assertNull(bar);

        byte[] bytes;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        afterUpdate.getBody().writeTo(stream);
        bytes = stream.toByteArray();

        Assert.assertArrayEquals(expectedArray,bytes);
    }

}
