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

import java.util.Set;

import org.apache.http.Header;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

public class TestCacheEntry {

    @Test
    public void testGetHeadersReturnsCorrectHeaders() {
        Header[] headers = new Header[] { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"), new BasicHeader("bar", "barValue2") };
        CacheEntry entry = new CacheEntry(headers);
        Assert.assertEquals(2, entry.getHeaders("bar").length);
    }

    @Test
    public void testGetFirstHeaderReturnsCorrectHeader() {
        Header[] headers = new Header[] { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"), new BasicHeader("bar", "barValue2") };
        CacheEntry entry = new CacheEntry(headers);

        Assert.assertEquals("barValue1", entry.getFirstHeader("bar").getValue());
    }

    @Test
    public void testGetHeadersReturnsEmptyArrayIfNoneMatch() {
        Header[] headers = new Header[] { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"), new BasicHeader("bar", "barValue2") };

        CacheEntry entry = new CacheEntry(headers);

        Assert.assertEquals(0, entry.getHeaders("baz").length);
    }

    @Test
    public void testGetFirstHeaderReturnsNullIfNoneMatch() {
        Header[] headers = new Header[] { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"), new BasicHeader("bar", "barValue2") };
        CacheEntry entry = new CacheEntry(headers);

        Assert.assertEquals(null, entry.getFirstHeader("quux"));
    }

    @Test
    public void testCacheEntryWithNoVaryHeaderDoesNotHaveVariants() {
        Header[] headers = new Header[0];

        CacheEntry entry = new CacheEntry(headers);
        Assert.assertFalse(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithOneVaryHeaderHasVariants() {
        Header[] headers = { new BasicHeader("Vary", "User-Agent") };
        CacheEntry entry = new CacheEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithMultipleVaryHeadersHasVariants() {
        Header[] headers = { new BasicHeader("Vary", "User-Agent"),
                new BasicHeader("Vary", "Accept-Encoding") };
        CacheEntry entry = new CacheEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithVaryStarHasVariants(){
        Header[] headers = { new BasicHeader("Vary", "*") };
        CacheEntry entry = new CacheEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryCanStoreMultipleVariantUris() throws Exception {

        Header[] headers = new Header[]{};
        CacheEntry entry = new CacheEntry(headers);

        CacheEntryGenerator entryGenerator = new CacheEntryGenerator();

        HttpCacheEntry addedOne = entryGenerator.copyVariant(entry, "foo");
        HttpCacheEntry addedTwo = entryGenerator.copyVariant(addedOne, "bar");

        Set<String> variants = addedTwo.getVariantURIs();

        Assert.assertTrue(variants.contains("foo"));
        Assert.assertTrue(variants.contains("bar"));
    }

}
