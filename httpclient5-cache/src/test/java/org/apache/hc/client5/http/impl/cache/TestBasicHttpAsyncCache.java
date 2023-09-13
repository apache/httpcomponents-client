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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.HeadersMatcher;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TestBasicHttpAsyncCache {

    private HttpHost host;
    private Instant now;
    private Instant tenSecondsAgo;
    private SimpleHttpAsyncCacheStorage backing;
    private BasicHttpAsyncCache impl;

    @BeforeEach
    public void setUp() {
        host = new HttpHost("foo.example.com");
        now = Instant.now();
        tenSecondsAgo = now.minusSeconds(10);
        backing = Mockito.spy(new SimpleHttpAsyncCacheStorage());
        impl = new BasicHttpAsyncCache(HeapResourceFactory.INSTANCE, backing);
    }

    @Test
    public void testGetCacheEntryReturnsNullOnCacheMiss() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final CountDownLatch latch1 = new CountDownLatch(1);
        final AtomicReference<CacheMatch> resultRef = new AtomicReference<>();

        impl.match(host, request, HttpTestUtils.countDown(latch1, resultRef::set));

        latch1.await();

        assertNull(resultRef.get());
    }

    @Test
    public void testGetCacheEntryFetchesFromCacheOnCacheHitIfNoVariants() throws Exception {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertFalse(entry.hasVariants());
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, request);

        backing.map.put(key,entry);

        final CountDownLatch latch1 = new CountDownLatch(1);
        final AtomicReference<CacheMatch> resultRef = new AtomicReference<>();

        impl.match(host, request, HttpTestUtils.countDown(latch1, resultRef::set));

        latch1.await();
        final CacheMatch result = resultRef.get();

        assertNotNull(result);
        assertNotNull(result.hit);
        assertSame(entry, result.hit.entry);
    }

    @Test
    public void testGetCacheEntryReturnsNullIfNoVariantInCache() throws Exception {
        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final ByteArrayBuffer buf = HttpTestUtils.makeRandomBuffer(128);
        final HttpResponse origResponse = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse.setHeader("Date", DateUtils.formatStandardDate(now));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        final CountDownLatch latch1 = new CountDownLatch(1);

        impl.store(host, origRequest, origResponse, buf, now, now, HttpTestUtils.countDown(latch1));

        latch1.await();

        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicReference<CacheMatch> resultRef = new AtomicReference<>();
        impl.match(host, request, HttpTestUtils.countDown(latch2, resultRef::set));

        latch2.await();
        final CacheMatch result = resultRef.get();

        assertNotNull(result);
        assertNull(result.hit);
    }

    @Test
    public void testGetCacheEntryReturnsVariantIfPresentInCache() throws Exception {
        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final ByteArrayBuffer buf = HttpTestUtils.makeRandomBuffer(128);
        final HttpResponse origResponse = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse.setHeader("Date", DateUtils.formatStandardDate(now));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        final CountDownLatch latch1 = new CountDownLatch(1);

        impl.store(host, origRequest, origResponse, buf, now, now, HttpTestUtils.countDown(latch1));

        latch1.await();

        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        request.setHeader("Accept-Encoding","gzip");

        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicReference<CacheMatch> resultRef = new AtomicReference<>();
        impl.match(host, request, HttpTestUtils.countDown(latch2, resultRef::set));

        latch2.await();
        final CacheMatch result = resultRef.get();

        assertNotNull(result);
        assertNotNull(result.hit);
    }

    @Test
    public void testGetCacheEntryReturnsVariantWithMostRecentDateHeader() throws Exception {
        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding", "gzip");

        final ByteArrayBuffer buf = HttpTestUtils.makeRandomBuffer(128);

        // Create two response variants with different Date headers
        final HttpResponse origResponse1 = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse1.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(now.minusSeconds(3600)));
        origResponse1.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600, public");
        origResponse1.setHeader(HttpHeaders.ETAG, "\"etag1\"");
        origResponse1.setHeader(HttpHeaders.VARY, "Accept-Encoding");

        final HttpResponse origResponse2 = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        origResponse2.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(now));
        origResponse2.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600, public");
        origResponse2.setHeader(HttpHeaders.ETAG, "\"etag2\"");
        origResponse2.setHeader(HttpHeaders.VARY, "Accept-Encoding");

        // Store the two variants in cache
        final CountDownLatch latch1 = new CountDownLatch(2);

        impl.store(host, origRequest, origResponse1, buf, now, now, HttpTestUtils.countDown(latch1));
        impl.store(host, origRequest, origResponse2, buf, now, now, HttpTestUtils.countDown(latch1));

        latch1.await();

        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        request.setHeader("Accept-Encoding", "gzip");

        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicReference<CacheMatch> resultRef = new AtomicReference<>();
        impl.match(host, request, HttpTestUtils.countDown(latch2, resultRef::set));

        latch2.await();
        final CacheMatch result = resultRef.get();

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

        final CountDownLatch latch1 = new CountDownLatch(1);
        final AtomicReference<Collection<CacheHit>> resultRef = new AtomicReference<>();
        impl.getVariants(new CacheHit("root-key", root), HttpTestUtils.countDown(latch1, resultRef::set));

        latch1.await();
        final Collection<CacheHit> variants = resultRef.get();

        assertNotNull(variants);
        assertEquals(0, variants.size());
    }

    @Test
    public void testGetVariantsRootNonExistentVariants() throws Exception {
        final Set<String> varinats = new HashSet<>();
        varinats.add("variant1");
        varinats.add("variant2");
        final HttpCacheEntry root = HttpTestUtils.makeCacheEntry(varinats);

        final CountDownLatch latch1 = new CountDownLatch(1);
        final AtomicReference<Collection<CacheHit>> resultRef = new AtomicReference<>();
        impl.getVariants(new CacheHit("root-key", root), HttpTestUtils.countDown(latch1, resultRef::set));

        latch1.await();
        final Collection<CacheHit> variants = resultRef.get();

        assertNotNull(variants);
        assertEquals(0, variants.size());
    }

    @Test
    public void testGetVariantCacheEntriesReturnsAllVariants() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final URI uri = new URI("http://foo.example.com/bar");
        final HttpRequest req1 = new HttpGet(uri);
        req1.setHeader("Accept-Encoding", "gzip");

        final String rootKey = CacheKeyGenerator.INSTANCE.generateKey(uri);

        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600, public");
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Content-Encoding","gzip");

        final HttpRequest req2 = new HttpGet(uri);
        req2.setHeader("Accept-Encoding", "identity");

        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600, public");
        resp2.setHeader("ETag", "\"etag2\"");
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Content-Encoding","gzip");

        final CountDownLatch latch1 = new CountDownLatch(2);

        final AtomicReference<CacheHit> resultRef1 = new AtomicReference<>();
        final AtomicReference<CacheHit> resultRef2 = new AtomicReference<>();

        impl.store(host, req1, resp1, null, now, now, HttpTestUtils.countDown(latch1, resultRef1::set));
        impl.store(host, req2, resp2, null, now, now, HttpTestUtils.countDown(latch1, resultRef2::set));

        latch1.await();

        final CacheHit hit1 = resultRef1.get();
        final CacheHit hit2 = resultRef2.get();

        final Set<String> variants = new HashSet<>();
        variants.add("{accept-encoding=gzip}");
        variants.add("{accept-encoding=identity}");

        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicReference<Collection<CacheHit>> resultRef3 = new AtomicReference<>();

        impl.getVariants(new CacheHit(hit1.rootKey, HttpTestUtils.makeCacheEntry(variants)),
                HttpTestUtils.countDown(latch2, resultRef3::set));

        latch2.await();

        final Map<String, HttpCacheEntry> variantMap = resultRef3.get().stream()
                .collect(Collectors.toMap(CacheHit::getEntryKey, e -> e.entry));

                assertNotNull(variantMap);
        assertEquals(2, variantMap.size());
        MatcherAssert.assertThat(variantMap.get("{accept-encoding=gzip}" + rootKey),
                HttpCacheEntryMatcher.equivalent(hit1.entry));
        MatcherAssert.assertThat(variantMap.get("{accept-encoding=identity}" + rootKey),
                HttpCacheEntryMatcher.equivalent(hit2.entry));
    }

    @Test
    public void testUpdateCacheEntry() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final URI uri = new URI("http://foo.example.com/bar");
        final HttpRequest req1 = new HttpGet(uri);

        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600, public");
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Content-Encoding","gzip");

        final HttpRequest revalidate = new HttpGet(uri);
        revalidate.setHeader("If-None-Match","\"etag1\"");

        final HttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600, public");

        final CountDownLatch latch1 = new CountDownLatch(1);

        final AtomicReference<CacheHit> resultRef1 = new AtomicReference<>();
        impl.store(host, req1, resp1, null, now, now, HttpTestUtils.countDown(latch1, resultRef1::set));

        latch1.await();
        final CacheHit hit1 = resultRef1.get();

        Assertions.assertNotNull(hit1);
        Assertions.assertEquals(1, backing.map.size());
        Assertions.assertSame(hit1.entry, backing.map.get(hit1.getEntryKey()));

        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<CacheHit> resultRef2 = new AtomicReference<>();
        impl.update(hit1, host, req1, resp2, now, now, HttpTestUtils.countDown(latch2, resultRef2::set));

        latch2.await();
        final CacheHit updated = resultRef2.get();

        Assertions.assertNotNull(updated);
        Assertions.assertEquals(1, backing.map.size());
        Assertions.assertSame(updated.entry, backing.map.get(hit1.getEntryKey()));

        MatcherAssert.assertThat(
                updated.entry.getHeaders(),
                HeadersMatcher.same(
                        new BasicHeader("Server", "MockOrigin/1.0"),
                        new BasicHeader("ETag", "\"etag1\""),
                        new BasicHeader("Content-Encoding","gzip"),
                        new BasicHeader("Date", DateUtils.formatStandardDate(now)),
                        new BasicHeader("Cache-Control", "max-age=3600, public")
                ));
    }

    @Test
    public void testUpdateVariantCacheEntry() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final URI uri = new URI("http://foo.example.com/bar");
        final HttpRequest req1 = new HttpGet(uri);
        req1.setHeader("User-Agent", "agent1");

        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600, public");
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Content-Encoding","gzip");
        resp1.setHeader("Vary", "User-Agent");

        final HttpRequest revalidate = new HttpGet(uri);
        revalidate.setHeader("If-None-Match","\"etag1\"");

        final HttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600, public");

        final CountDownLatch latch1 = new CountDownLatch(1);

        final AtomicReference<CacheHit> resultRef1 = new AtomicReference<>();
        impl.store(host, req1, resp1, null, now, now, HttpTestUtils.countDown(latch1, resultRef1::set));

        latch1.await();
        final CacheHit hit1 = resultRef1.get();

        Assertions.assertNotNull(hit1);
        Assertions.assertEquals(2, backing.map.size());
        Assertions.assertSame(hit1.entry, backing.map.get(hit1.getEntryKey()));

        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<CacheHit> resultRef2 = new AtomicReference<>();
        impl.update(hit1, host, req1, resp2, now, now, HttpTestUtils.countDown(latch2, resultRef2::set));

        latch2.await();
        final CacheHit updated = resultRef2.get();

        Assertions.assertNotNull(updated);
        Assertions.assertEquals(2, backing.map.size());
        Assertions.assertSame(updated.entry, backing.map.get(hit1.getEntryKey()));

        MatcherAssert.assertThat(
                updated.entry.getHeaders(),
                HeadersMatcher.same(
                        new BasicHeader("Server", "MockOrigin/1.0"),
                        new BasicHeader("ETag", "\"etag1\""),
                        new BasicHeader("Content-Encoding","gzip"),
                        new BasicHeader("Vary","User-Agent"),
                        new BasicHeader("Date", DateUtils.formatStandardDate(now)),
                        new BasicHeader("Cache-Control", "max-age=3600, public")
                ));
    }

    @Test
    public void testUpdateCacheEntryTurnsVariant() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final URI uri = new URI("http://foo.example.com/bar");
        final HttpRequest req1 = new HttpGet(uri);
        req1.setHeader("User-Agent", "agent1");

        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600, public");
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Content-Encoding","gzip");

        final HttpRequest revalidate = new HttpGet(uri);
        revalidate.setHeader("If-None-Match","\"etag1\"");

        final HttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600, public");
        resp2.setHeader("Vary", "User-Agent");

        final CountDownLatch latch1 = new CountDownLatch(1);

        final AtomicReference<CacheHit> resultRef1 = new AtomicReference<>();
        impl.store(host, req1, resp1, null, now, now, HttpTestUtils.countDown(latch1, resultRef1::set));

        latch1.await();
        final CacheHit hit1 = resultRef1.get();

        Assertions.assertNotNull(hit1);
        Assertions.assertEquals(1, backing.map.size());
        Assertions.assertSame(hit1.entry, backing.map.get(hit1.getEntryKey()));

        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<CacheHit> resultRef2 = new AtomicReference<>();
        impl.update(hit1, host, req1, resp2, now, now, HttpTestUtils.countDown(latch2, resultRef2::set));

        latch2.await();
        final CacheHit updated = resultRef2.get();

        Assertions.assertNotNull(updated);
        Assertions.assertEquals(2, backing.map.size());

        MatcherAssert.assertThat(
                updated.entry.getHeaders(),
                HeadersMatcher.same(
                        new BasicHeader("Server", "MockOrigin/1.0"),
                        new BasicHeader("ETag", "\"etag1\""),
                        new BasicHeader("Content-Encoding","gzip"),
                        new BasicHeader("Date", DateUtils.formatStandardDate(now)),
                        new BasicHeader("Cache-Control", "max-age=3600, public"),
                        new BasicHeader("Vary","User-Agent")));
    }

    @Test
    public void testStoreFromNegotiatedVariant() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final URI uri = new URI("http://foo.example.com/bar");
        final HttpRequest req1 = new HttpGet(uri);
        req1.setHeader("User-Agent", "agent1");

        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600, public");
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Content-Encoding","gzip");
        resp1.setHeader("Vary", "User-Agent");

        final CountDownLatch latch1 = new CountDownLatch(1);

        final AtomicReference<CacheHit> resultRef1 = new AtomicReference<>();
        impl.store(host, req1, resp1, null, now, now, HttpTestUtils.countDown(latch1, resultRef1::set));

        latch1.await();
        final CacheHit hit1 = resultRef1.get();

        Assertions.assertNotNull(hit1);
        Assertions.assertEquals(2, backing.map.size());
        Assertions.assertSame(hit1.entry, backing.map.get(hit1.getEntryKey()));

        final HttpRequest req2 = new HttpGet(uri);
        req2.setHeader("User-Agent", "agent2");

        final HttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600, public");

        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<CacheHit> resultRef2 = new AtomicReference<>();
        impl.storeFromNegotiated(hit1, host, req2, resp2, now, now, HttpTestUtils.countDown(latch2, resultRef2::set));

        final CacheHit hit2 = resultRef2.get();

        Assertions.assertNotNull(hit2);
        Assertions.assertEquals(3, backing.map.size());

        MatcherAssert.assertThat(
                hit2.entry.getHeaders(),
                HeadersMatcher.same(
                        new BasicHeader("Server", "MockOrigin/1.0"),
                        new BasicHeader("ETag", "\"etag1\""),
                        new BasicHeader("Content-Encoding","gzip"),
                        new BasicHeader("Vary","User-Agent"),
                        new BasicHeader("Date", DateUtils.formatStandardDate(now)),
                        new BasicHeader("Cache-Control", "max-age=3600, public")));
    }

    @Test
    public void testInvalidatesUnsafeRequests() throws Exception {
        final HttpRequest request = new BasicHttpRequest("POST", "/path");
        final HttpResponse response = HttpTestUtils.make200Response();

        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, request);

        backing.putEntry(key, HttpTestUtils.makeCacheEntry());

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(key), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(key), Mockito.any());
        Assertions.assertNull(backing.getEntry(key));
    }

    @Test
    public void testDoesNotInvalidateSafeRequests() throws Exception {
        final HttpRequest request1 = new BasicHttpRequest("GET", "/");
        final HttpResponse response1 = HttpTestUtils.make200Response();
        final CountDownLatch latch1 = new CountDownLatch(1);

        impl.evictInvalidatedEntries(host, request1, response1, HttpTestUtils.countDown(latch1));

        latch1.await();

        verifyNoMoreInteractions(backing);

        final HttpRequest request2 = new BasicHttpRequest("HEAD", "/");
        final HttpResponse response2 = HttpTestUtils.make200Response();
        final CountDownLatch latch2 = new CountDownLatch(1);

        impl.evictInvalidatedEntries(host, request2, response2, HttpTestUtils.countDown(latch2));

        latch2.await();

        verifyNoMoreInteractions(backing);
    }

    @Test
    public void testInvalidatesUnsafeRequestsWithVariants() throws Exception {
        final HttpRequest request = new BasicHttpRequest("POST", "/path");
        final String rootKey = CacheKeyGenerator.INSTANCE.generateKey(host, request);
        final Set<String> variants = new HashSet<>();
        variants.add("{var1}");
        variants.add("{var2}");
        final String variantKey1 = "{var1}" + rootKey;
        final String variantKey2 = "{var2}" + rootKey;

        final HttpResponse response = HttpTestUtils.make200Response();

        backing.putEntry(rootKey, HttpTestUtils.makeCacheEntry(variants));
        backing.putEntry(variantKey1, HttpTestUtils.makeCacheEntry());
        backing.putEntry(variantKey2, HttpTestUtils.makeCacheEntry());

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(variantKey1), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(variantKey2), Mockito.any());

        Assertions.assertNull(backing.getEntry(rootKey));
        Assertions.assertNull(backing.getEntry(variantKey1));
        Assertions.assertNull(backing.getEntry(variantKey2));
    }

    @Test
    public void testInvalidateUriSpecifiedByContentLocationAndFresher() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final String rootKey = CacheKeyGenerator.INSTANCE.generateKey(host, request);
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(rootKey, HttpTestUtils.makeCacheEntry());
        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")
        ));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testInvalidateUriSpecifiedByLocationAndFresher() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final String rootKey = CacheKeyGenerator.INSTANCE.generateKey(host, request);
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Location", contentUri.toASCIIString());

        backing.putEntry(rootKey, HttpTestUtils.makeCacheEntry());
        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")
        ));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testDoesNotInvalidateForUnsuccessfulResponse() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final HttpResponse response = HttpTestUtils.make500Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verifyNoMoreInteractions(backing);
    }

    @Test
    public void testInvalidateUriSpecifiedByContentLocationNonCanonical() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final String rootKey = CacheKeyGenerator.INSTANCE.generateKey(host, request);
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));

        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(rootKey, HttpTestUtils.makeCacheEntry());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(contentKey), Mockito.any());
        Assertions.assertNull(backing.getEntry(rootKey));
        Assertions.assertNull(backing.getEntry(contentKey));
    }

    @Test
    public void testInvalidateUriSpecifiedByContentLocationRelative() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final String rootKey = CacheKeyGenerator.INSTANCE.generateKey(host, request);
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));

        response.setHeader("Content-Location", "/bar");

        backing.putEntry(rootKey, HttpTestUtils.makeCacheEntry());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(contentKey), Mockito.any());
        Assertions.assertNull(backing.getEntry(rootKey));
        Assertions.assertNull(backing.getEntry(contentKey));
    }

    @Test
    public void testDoesNotInvalidateUriSpecifiedByContentLocationOtherOrigin() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/");
        final URI contentUri = new URIBuilder()
                .setHost("bar.example.com")
                .setPath("/")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry());

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing, Mockito.never()).getEntry(contentKey);
        verify(backing, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testDoesNotInvalidateUriSpecifiedByContentLocationIfEtagsMatch() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"same-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"same-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testDoesNotInvalidateUriSpecifiedByContentLocationIfOlder() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(now)),
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testDoesNotInvalidateUriSpecifiedByContentLocationIfResponseHasNoEtag() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.removeHeaders("ETag");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testDoesNotInvalidateUriSpecifiedByContentLocationIfEntryHasNoEtag() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag", "\"some-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testInvalidatesUriSpecifiedByContentLocationIfResponseHasNoDate() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag", "\"new-etag\"");
        response.removeHeaders("Date");
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testInvalidatesUriSpecifiedByContentLocationIfEntryHasNoDate() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testInvalidatesUriSpecifiedByContentLocationIfResponseHasMalformedDate() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", "huh?");
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

    @Test
    public void testInvalidatesUriSpecifiedByContentLocationIfEntryHasMalformedDate() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT", "/foo");
        final URI contentUri = new URIBuilder()
                .setHttpHost(host)
                .setPath("/bar")
                .build();
        final String contentKey = CacheKeyGenerator.INSTANCE.generateKey(contentUri);

        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Content-Location", contentUri.toASCIIString());

        backing.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", "huh?")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(backing).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(backing).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

}
