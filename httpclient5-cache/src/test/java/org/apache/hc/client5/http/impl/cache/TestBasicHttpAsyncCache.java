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
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TestBasicHttpAsyncCache {

    private HttpHost host;
    private Instant now;
    private Instant tenSecondsAgo;
    private SimpleHttpAsyncCacheStorage mockStorage;
    private BasicHttpAsyncCache impl;

    @BeforeEach
    public void setUp() {
        host = new HttpHost("foo.example.com");
        now = Instant.now();
        tenSecondsAgo = now.minusSeconds(10);
        mockStorage = Mockito.spy(new SimpleHttpAsyncCacheStorage());
        impl = new BasicHttpAsyncCache(HeapResourceFactory.INSTANCE, mockStorage);
    }

    // Tests
    @Test
    public void testInvalidatesUnsafeRequests() throws Exception {
        final HttpRequest request = new BasicHttpRequest("POST", "/path");
        final HttpResponse response = HttpTestUtils.make200Response();

        final String key = CacheKeyGenerator.INSTANCE.generateKey(host, request);

        mockStorage.putEntry(key, HttpTestUtils.makeCacheEntry());

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(key), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(key), Mockito.any());
        Assertions.assertNull(mockStorage.getEntry(key));
    }

    @Test
    public void testDoesNotInvalidateSafeRequests() throws Exception {
        final HttpRequest request1 = new BasicHttpRequest("GET", "/");
        final HttpResponse response1 = HttpTestUtils.make200Response();
        final CountDownLatch latch1 = new CountDownLatch(1);

        impl.evictInvalidatedEntries(host, request1, response1, HttpTestUtils.countDown(latch1));

        latch1.await();

        verifyNoMoreInteractions(mockStorage);

        final HttpRequest request2 = new BasicHttpRequest("HEAD", "/");
        final HttpResponse response2 = HttpTestUtils.make200Response();
        final CountDownLatch latch2 = new CountDownLatch(1);

        impl.evictInvalidatedEntries(host, request2, response2, HttpTestUtils.countDown(latch2));

        latch2.await();

        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testInvalidatesUnsafeRequestsWithVariants() throws Exception {
        final HttpRequest request = new BasicHttpRequest("POST", "/path");
        final String rootKey = CacheKeyGenerator.INSTANCE.generateKey(host, request);
        final String variantKey1 = "{var1}" + rootKey;
        final String variantKey2 = "{var2}" + rootKey;
        final Map<String, String> variantMap = new HashMap<>();
        variantMap.put("{var1}", variantKey1);
        variantMap.put("{var2}", variantKey2);

        final HttpResponse response = HttpTestUtils.make200Response();

        mockStorage.putEntry(rootKey, HttpTestUtils.makeCacheEntry(variantMap));
        mockStorage.putEntry(variantKey1, HttpTestUtils.makeCacheEntry());
        mockStorage.putEntry(variantKey2, HttpTestUtils.makeCacheEntry());

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(variantKey1), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(variantKey2), Mockito.any());

        Assertions.assertNull(mockStorage.getEntry(rootKey));
        Assertions.assertNull(mockStorage.getEntry(variantKey1));
        Assertions.assertNull(mockStorage.getEntry(variantKey2));
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

        mockStorage.putEntry(rootKey, HttpTestUtils.makeCacheEntry());
        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")
        ));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(rootKey, HttpTestUtils.makeCacheEntry());
        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")
        ));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        verifyNoMoreInteractions(mockStorage);
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

        mockStorage.putEntry(rootKey, HttpTestUtils.makeCacheEntry());

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(contentKey), Mockito.any());
        Assertions.assertNull(mockStorage.getEntry(rootKey));
        Assertions.assertNull(mockStorage.getEntry(contentKey));
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

        mockStorage.putEntry(rootKey, HttpTestUtils.makeCacheEntry());

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(rootKey), Mockito.any());
        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(contentKey), Mockito.any());
        Assertions.assertNull(mockStorage.getEntry(rootKey));
        Assertions.assertNull(mockStorage.getEntry(contentKey));
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry());

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage, Mockito.never()).getEntry(contentKey);
        verify(mockStorage, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"same-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(now)),
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage, Mockito.never()).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("ETag", "\"old-etag\"")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(contentKey), Mockito.any());
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

        mockStorage.putEntry(contentKey, HttpTestUtils.makeCacheEntry(
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", "huh?")));

        final CountDownLatch latch = new CountDownLatch(1);
        impl.evictInvalidatedEntries(host, request, response, HttpTestUtils.countDown(latch));

        latch.await();

        verify(mockStorage).getEntry(Mockito.eq(contentKey), Mockito.any());
        verify(mockStorage).removeEntry(Mockito.eq(contentKey), Mockito.any());
    }

}
