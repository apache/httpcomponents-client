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

import org.apache.http.client.cache.HttpCacheOperationException;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TestResponseCache {

    private BasicHttpCache cache;
    private CacheEntry mockEntry;

    @Before
    public void setUp() {
        cache = new BasicHttpCache(5);
        mockEntry = EasyMock.createMock(CacheEntry.class);
    }

    @Test
    public void testEntryRemainsInCacheWhenPutThere() {
        cache.putEntry("foo", mockEntry);

        CacheEntry cachedEntry = cache.getEntry("foo");

        Assert.assertSame(mockEntry, cachedEntry);
    }

    @Test
    public void testRemovedEntriesDoNotExistAnymore() {
        cache.putEntry("foo", mockEntry);

        cache.removeEntry("foo");

        CacheEntry nullEntry = cache.getEntry("foo");

        Assert.assertNull(nullEntry);
    }

    @Test
    public void testCacheHoldsNoMoreThanSpecifiedMaxEntries() {
        BasicHttpCache cache = new BasicHttpCache(1);

        CacheEntry entry1 = EasyMock.createMock(CacheEntry.class);
        cache.putEntry("foo", entry1);

        CacheEntry entry2 = EasyMock.createMock(CacheEntry.class);
        cache.putEntry("bar", entry2);

        CacheEntry entry3 = EasyMock.createMock(CacheEntry.class);
        cache.putEntry("baz", entry3);

        CacheEntry e1 = cache.getEntry("foo");
        Assert.assertNull("Got foo entry when we should not", e1);

        CacheEntry e2 = cache.getEntry("bar");
        Assert.assertNull("Got bar entry when we should not", e2);

        CacheEntry e3 = cache.getEntry("baz");
        Assert.assertNotNull("Did not get baz entry, but should have", e3);
    }

    @Test
    public void testSmallCacheKeepsMostRecentlyUsedEntry() {

        final int max_size = 3;
        BasicHttpCache cache = new BasicHttpCache(max_size);

        // fill the cache with entries
        for (int i = 0; i < max_size; i++) {
            CacheEntry entry = EasyMock.createMock(CacheEntry.class);
            cache.putEntry("entry" + i, entry);
        }

        // read the eldest entry to make it the MRU entry
        cache.getEntry("entry0");

        // add another entry, which kicks out the eldest (should be the 2nd one
        // created), and becomes the new MRU entry
        CacheEntry newMru = EasyMock.createMock(CacheEntry.class);
        cache.putEntry("newMru", newMru);

        // get the original second eldest
        CacheEntry gone = cache.getEntry("entry1");
        Assert.assertNull("entry1 should be gone", gone);

        CacheEntry latest = cache.getEntry("newMru");
        Assert.assertNotNull("latest entry should still be there", latest);

        CacheEntry originalEldest = cache.getEntry("entry0");
        Assert.assertNotNull("original eldest entry should still be there", originalEldest);
    }

    @Test
    public void testZeroMaxSizeCacheDoesNotStoreAnything() {
        BasicHttpCache cache = new BasicHttpCache(0);

        CacheEntry entry = EasyMock.createMock(CacheEntry.class);
        cache.putEntry("foo", entry);

        CacheEntry gone = cache.getEntry("foo");

        Assert.assertNull("This cache should not have anything in it!", gone);
    }

    @Test
    @Ignore
    public void testCacheEntryCallbackUpdatesCacheEntry() throws HttpCacheOperationException {

        final byte[] expectedArray = new byte[] { 1, 2, 3, 4, 5 };

        CacheEntry entry = EasyMock.createMock(CacheEntry.class);
        CacheEntry entry2 = EasyMock.createMock(CacheEntry.class);

        cache.putEntry("foo", entry);
        cache.putEntry("bar", entry2);

        cache.updateCacheEntry("foo", new HttpCacheUpdateCallback<CacheEntry>() {

            public CacheEntry getUpdatedEntry(CacheEntry existing) {
                CacheEntry updated = new CacheEntry(
                        existing.getRequestDate(),
                        existing.getRequestDate(),
                        existing.getProtocolVersion(),
                        existing.getAllHeaders(),
                        expectedArray,
                        existing.getStatusCode(),
                        existing.getReasonPhrase());
                cache.removeEntry("bar");
                return updated;
            }
        });

        CacheEntry afterUpdate = cache.getEntry("foo");
        CacheEntry bar = cache.getEntry("bar");

        Assert.assertNull(bar);

        Assert.assertArrayEquals(expectedArray, afterUpdate.getBody());
    }

}
