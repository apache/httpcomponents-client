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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestBasicHttpCache {

    private BasicHttpCache impl;
    private SimpleHttpCacheStorage backing;

    @BeforeEach
    public void setUp() throws Exception {
        backing = new SimpleHttpCacheStorage();
        impl = new BasicHttpCache(new HeapResourceFactory(), backing);
    }

    @Test
    public void testFlushContentLocationEntryIfUnSafeRequest() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpPost("/foo");
        final HttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Content-Location", "/bar");
        resp.setHeader(HttpHeaders.ETAG, "\"etag\"");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, new HttpGet("/bar"));

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(Instant.now())),
                new BasicHeader("ETag", "\"old-etag\""));

        backing.map.put(key, entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, req, resp);

        assertNull(backing.map.get(key));
    }

    @Test
    public void testDoNotFlushContentLocationEntryIfSafeRequest() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpGet("/foo");
        final HttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Content-Location", "/bar");
        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, new HttpGet("/bar"));

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(Instant.now())),
                new BasicHeader("ETag", "\"old-etag\""));

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
        final HttpResponse resp = HttpTestUtils.make200Response();

        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, req);

        impl.store(req, resp, Instant.now(), Instant.now(), key, entry);
        assertSame(entry, backing.map.get(key));
    }

    @Test
    public void testGetCacheEntryReturnsNullOnCacheMiss() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        final CacheMatch result = impl.match(host, request);
        assertNull(result);
    }

    @Test
    public void testGetCacheEntryFetchesFromCacheOnCacheHitIfNoVariants() throws Exception {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertFalse(entry.hasVariants());
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, request);

        backing.map.put(key,entry);

        final CacheMatch result = impl.match(host, request);
        assertNotNull(result);
        assertNotNull(result.hit);
        assertSame(entry, result.hit.entry);
    }

    @Test
    public void testGetCacheEntryReturnsNullIfNoVariantInCache() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");

        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final ByteArrayBuffer buf = HttpTestUtils.makeRandomBuffer(128);
        final HttpResponse origResponse = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        impl.store(host, origRequest, origResponse, buf, Instant.now(), Instant.now());

        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        final CacheMatch result = impl.match(host, request);
        assertNotNull(result);
        assertNull(result.hit);
    }

    @Test
    public void testGetCacheEntryReturnsVariantIfPresentInCache() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");

        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final ByteArrayBuffer buf = HttpTestUtils.makeRandomBuffer(128);
        final HttpResponse origResponse = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        impl.store(host, origRequest, origResponse, buf, Instant.now(), Instant.now());

        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        request.setHeader("Accept-Encoding","gzip");
        final CacheMatch result = impl.match(host, request);
        assertNotNull(result);
        assertNotNull(result.hit);
    }

    @Test
    public void testGetCacheEntryReturnsVariantWithMostRecentDateHeader() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");

        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding", "gzip");

        final ByteArrayBuffer buf = HttpTestUtils.makeRandomBuffer(128);

        // Create two response variants with different Date headers
        final HttpResponse origResponse1 = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse1.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now().minusSeconds(3600)));
        origResponse1.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600, public");
        origResponse1.setHeader(HttpHeaders.ETAG, "\"etag1\"");
        origResponse1.setHeader(HttpHeaders.VARY, "Accept-Encoding");

        final HttpResponse origResponse2 = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse2.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now()));
        origResponse2.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600, public");
        origResponse2.setHeader(HttpHeaders.ETAG, "\"etag2\"");
        origResponse2.setHeader(HttpHeaders.VARY, "Accept-Encoding");

        // Store the two variants in cache
        impl.store(host, origRequest, origResponse1, buf, Instant.now(), Instant.now());
        impl.store(host, origRequest, origResponse2, buf, Instant.now(), Instant.now());

        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        request.setHeader("Accept-Encoding", "gzip");
        final CacheMatch result = impl.match(host, request);
        assertNotNull(result);
        assertNotNull(result.hit);
        final HttpCacheEntry entry = result.hit.entry;
        assertNotNull(entry);

        // Retrieve the ETag header value from the original response and assert that
        // the returned cache entry has the same ETag value
        final String expectedEtag = origResponse2.getFirstHeader(HttpHeaders.ETAG).getValue();
        final String actualEtag = entry.getFirstHeader(HttpHeaders.ETAG).getValue();

        assertEquals(expectedEtag, actualEtag);
    }

    @Test
    public void testGetVariantsRootNoVariants() throws Exception {
        final HttpCacheEntry root = HttpTestUtils.makeCacheEntry();
        final List<CacheHit> variants = impl.getVariants(new CacheHit("root-key", root));

        assertNotNull(variants);
        assertEquals(0, variants.size());
    }

    @Test
    public void testGetVariantsRootNonExistentVariants() throws Exception {
        final Map<String, String> variantMap = new HashMap<>();
        variantMap.put("variant1", "variant-key-1");
        variantMap.put("variant2", "variant-key-2");
        final HttpCacheEntry root = HttpTestUtils.makeCacheEntry(variantMap);
        final List<CacheHit> variants = impl.getVariants(new CacheHit("root-key", root));

        assertNotNull(variants);
        assertEquals(0, variants.size());
    }

    @Test
    public void testGetVariantCacheEntriesReturnsAllVariants() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new HttpGet("http://foo.example.com/bar");
        req1.setHeader("Accept-Encoding", "gzip");

        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        resp1.setHeader("Cache-Control", "max-age=3600, public");
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Content-Encoding","gzip");
        resp1.setHeader("Vary", "Accept-Encoding");

        final HttpRequest req2 = new HttpGet("http://foo.example.com/bar");
        req2.setHeader("Accept-Encoding", "identity");

        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        resp2.setHeader("Cache-Control", "max-age=3600, public");
        resp2.setHeader("ETag", "\"etag2\"");
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Content-Encoding","gzip");
        resp2.setHeader("Vary", "Accept-Encoding");

        final CacheHit hit1 = impl.store(host, req1, resp1, null, Instant.now(), Instant.now());
        final CacheHit hit2 = impl.store(host, req2, resp2, null, Instant.now(), Instant.now());

        final Map<String, String> variantMap = new HashMap<>();
        variantMap.put("variant-1", hit1.variantKey);
        variantMap.put("variant-2", hit2.variantKey);

        final Map<String, HttpCacheEntry> variants = impl.getVariants(new CacheHit(hit1.rootKey,
                HttpTestUtils.makeCacheEntry(variantMap))).stream()
                        .collect(Collectors.toMap(CacheHit::getEntryKey, e -> e.entry));

        assertNotNull(variants);
        assertEquals(2, variants.size());
        MatcherAssert.assertThat(variants.get(hit1.getEntryKey()), HttpCacheEntryMatcher.equivalent(hit1.entry));
        MatcherAssert.assertThat(variants.get(hit2.getEntryKey()), HttpCacheEntryMatcher.equivalent(hit2.entry));
    }

}
