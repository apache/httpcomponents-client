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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Date;
import java.util.Map;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestBasicHttpCache {

    private BasicHttpCache impl;
    private SimpleHttpCacheStorage backing;

    @Before
    public void setUp() throws Exception {
        backing = new SimpleHttpCacheStorage();
        impl = new BasicHttpCache(new HeapResourceFactory(), backing);
    }

    @Test
    public void testDoNotFlushCacheEntriesOnGet() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpGet("/bar");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testDoNotFlushCacheEntriesOnHead() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpHead("/bar");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testDoNotFlushCacheEntriesOnOptions() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpOptions("/bar");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testDoNotFlushCacheEntriesOnTrace() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpTrace("/bar");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testFlushContentLocationEntryIfUnSafeRequest()
            throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpPost("/foo");
        final HttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Content-Location", "/bar");
        resp.setHeader(HeaderConstants.ETAG, "\"etag\"");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, new HttpGet("/bar"));

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(new Date())),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        backing.map.put(key, entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, req, resp);

        assertNull(backing.map.get(key));
    }

    @Test
    public void testDoNotFlushContentLocationEntryIfSafeRequest()
            throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpGet("/foo");
        final HttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Content-Location", "/bar");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, new HttpGet("/bar"));

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(new Date())),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        backing.map.put(key, entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, req, resp);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testCanFlushCacheEntriesAtUri() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpDelete("/bar");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertNull(backing.map.get(key));
    }

    @Test
    public void testStoreInCachePutsNonVariantEntryInPlace() throws Exception {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertFalse(entry.hasVariants());
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpGet("http://foo.example.com/bar");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, req);

        impl.storeInCache(key, host, req, entry);
        assertSame(entry, backing.map.get(key));
    }

    @Test
    public void testGetCacheEntryReturnsNullOnCacheMiss() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        final HttpCacheEntry result = impl.getCacheEntry(host, request);
        Assert.assertNull(result);
    }

    @Test
    public void testGetCacheEntryFetchesFromCacheOnCacheHitIfNoVariants() throws Exception {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertFalse(entry.hasVariants());
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, request);

        backing.map.put(key,entry);

        final HttpCacheEntry result = impl.getCacheEntry(host, request);
        Assert.assertSame(entry, result);
    }

    @Test
    public void testGetCacheEntryReturnsNullIfNoVariantInCache() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");

        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final ByteArrayBuffer buf = HttpTestUtils.getRandomBuffer(128);
        final HttpResponse origResponse = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse.setHeader("Date", DateUtils.formatDate(new Date()));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        impl.createCacheEntry(host, origRequest, origResponse, buf, new Date(), new Date());

        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        final HttpCacheEntry result = impl.getCacheEntry(host, request);
        assertNull(result);
    }

    @Test
    public void testGetCacheEntryReturnsVariantIfPresentInCache() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");

        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final ByteArrayBuffer buf = HttpTestUtils.getRandomBuffer(128);
        final HttpResponse origResponse = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse.setHeader("Date", DateUtils.formatDate(new Date()));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        impl.createCacheEntry(host, origRequest, origResponse, buf, new Date(), new Date());

        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        request.setHeader("Accept-Encoding","gzip");
        final HttpCacheEntry result = impl.getCacheEntry(host, request);
        assertNotNull(result);
    }

    @Test
    public void testGetVariantCacheEntriesReturnsEmptySetOnNoVariants() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final Map<String,Variant> variants = impl.getVariantCacheEntriesWithEtags(host, request);

        assertNotNull(variants);
        assertEquals(0, variants.size());
    }

    @Test
    public void testGetVariantCacheEntriesReturnsAllVariants() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new HttpGet("http://foo.example.com/bar");
        req1.setHeader("Accept-Encoding", "gzip");

        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "max-age=3600, public");
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Content-Encoding","gzip");
        resp1.setHeader("Vary", "Accept-Encoding");

        final HttpRequest req2 = new HttpGet("http://foo.example.com/bar");
        req2.setHeader("Accept-Encoding", "identity");

        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatDate(new Date()));
        resp2.setHeader("Cache-Control", "max-age=3600, public");
        resp2.setHeader("ETag", "\"etag2\"");
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Content-Encoding","gzip");
        resp2.setHeader("Vary", "Accept-Encoding");

        impl.createCacheEntry(host, req1, resp1, null, new Date(), new Date());
        impl.createCacheEntry(host, req2, resp2, null, new Date(), new Date());

        final Map<String,Variant> variants = impl.getVariantCacheEntriesWithEtags(host, req1);

        assertNotNull(variants);
        assertEquals(2, variants.size());

    }

}
