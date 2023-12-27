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

import java.time.Instant;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestCachedResponseSuitabilityChecker {

    private Instant now;
    private Instant elevenSecondsAgo;
    private Instant tenSecondsAgo;
    private Instant nineSecondsAgo;

    private HttpRequest request;
    private HttpCacheEntry entry;
    private RequestCacheControl requestCacheControl;
    private ResponseCacheControl responseCacheControl;
    private CachedResponseSuitabilityChecker impl;

    @BeforeEach
    public void setUp() {
        now = Instant.now();
        elevenSecondsAgo = now.minusSeconds(11);
        tenSecondsAgo = now.minusSeconds(10);
        nineSecondsAgo = now.minusSeconds(9);

        request = new BasicHttpRequest("GET", "/foo");
        entry = HttpTestUtils.makeCacheEntry();
        requestCacheControl = RequestCacheControl.builder().build();
        responseCacheControl = ResponseCacheControl.builder().build();

        impl = new CachedResponseSuitabilityChecker(CacheConfig.DEFAULT);
    }

    private HttpCacheEntry makeEntry(final Instant requestDate,
                                     final Instant responseDate,
                                     final Method method,
                                     final String requestUri,
                                     final Header[] requestHeaders,
                                     final int status,
                                     final Header[] responseHeaders) {
        return HttpTestUtils.makeCacheEntry(requestDate, responseDate, method, requestUri, requestHeaders,
                status, responseHeaders, HttpTestUtils.makeNullResource());
    }

    private HttpCacheEntry makeEntry(final Header... headers) {
        return makeEntry(elevenSecondsAgo, nineSecondsAgo, Method.GET, "/foo", null, 200, headers);
    }

    private HttpCacheEntry makeEntry(final Instant requestDate,
                                     final Instant responseDate,
                                     final Header... headers) {
        return makeEntry(requestDate, responseDate, Method.GET, "/foo", null, 200, headers);
    }

    private HttpCacheEntry makeEntry(final Method method, final String requestUri, final Header... headers) {
        return makeEntry(elevenSecondsAgo, nineSecondsAgo, method, requestUri, null, 200, headers);
    }

    private HttpCacheEntry makeEntry(final Method method, final String requestUri, final Header[] requestHeaders,
                                     final int status, final Header[] responseHeaders) {
        return makeEntry(elevenSecondsAgo, nineSecondsAgo, method, requestUri, requestHeaders,
                status, responseHeaders);
    }

    @Test
    public void testRequestMethodMatch() {
        request = new BasicHttpRequest("GET", "/foo");
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertTrue(impl.requestMethodMatch(request, entry));

        request = new BasicHttpRequest("HEAD", "/foo");
        Assertions.assertTrue(impl.requestMethodMatch(request, entry));

        request = new BasicHttpRequest("POST", "/foo");
        Assertions.assertFalse(impl.requestMethodMatch(request, entry));

        request = new BasicHttpRequest("HEAD", "/foo");
        entry = makeEntry(Method.HEAD, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertTrue(impl.requestMethodMatch(request, entry));

        request = new BasicHttpRequest("GET", "/foo");
        entry = makeEntry(Method.HEAD, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertFalse(impl.requestMethodMatch(request, entry));
    }

    @Test
    public void testRequestUriMatch() {
        request = new BasicHttpRequest("GET", "/foo");
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertTrue(impl.requestUriMatch(request, entry));

        request = new BasicHttpRequest("GET", new HttpHost("some-host"), "/foo");
        entry = makeEntry(Method.GET, "http://some-host:80/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertTrue(impl.requestUriMatch(request, entry));

        request = new BasicHttpRequest("GET", new HttpHost("Some-Host"), "/foo?bar");
        entry = makeEntry(Method.GET, "http://some-host:80/foo?bar",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertTrue(impl.requestUriMatch(request, entry));

        request = new BasicHttpRequest("GET", new HttpHost("some-other-host"), "/foo");
        entry = makeEntry(Method.GET, "http://some-host:80/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertFalse(impl.requestUriMatch(request, entry));

        request = new BasicHttpRequest("GET", new HttpHost("some-host"), "/foo?huh");
        entry = makeEntry(Method.GET, "http://some-host:80/foo?bar",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertFalse(impl.requestUriMatch(request, entry));
    }

    @Test
    public void testRequestHeadersMatch() {
        request = BasicRequestBuilder.get("/foo").build();
        entry = makeEntry(
                Method.GET, "/foo",
                new Header[]{},
                200,
                new Header[]{
                        new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
                });
        Assertions.assertTrue(impl.requestHeadersMatch(request, entry));

        request = BasicRequestBuilder.get("/foo").build();
        entry = makeEntry(
                Method.GET, "/foo",
                new Header[]{},
                200,
                new Header[]{
                        new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                        new BasicHeader("Vary", "")
                });
        Assertions.assertTrue(impl.requestHeadersMatch(request, entry));

        request = BasicRequestBuilder.get("/foo")
                .addHeader("Accept-Encoding", "blah")
                .build();
        entry = makeEntry(
                Method.GET, "/foo",
                new Header[]{
                        new BasicHeader("Accept-Encoding", "blah")
                },
                200,
                new Header[]{
                        new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                        new BasicHeader("Vary", "Accept-Encoding")
                });
        Assertions.assertTrue(impl.requestHeadersMatch(request, entry));

        request = BasicRequestBuilder.get("/foo")
                .addHeader("Accept-Encoding", "gzip, deflate, deflate ,  zip, ")
                .build();
        entry = makeEntry(
                Method.GET, "/foo",
                new Header[]{
                        new BasicHeader("Accept-Encoding", " gzip, zip, deflate")
                },
                200,
                new Header[]{
                        new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                        new BasicHeader("Vary", "Accept-Encoding")
                });
        Assertions.assertTrue(impl.requestHeadersMatch(request, entry));

        request = BasicRequestBuilder.get("/foo")
                .addHeader("Accept-Encoding", "gzip, deflate, zip")
                .build();
        entry = makeEntry(
                Method.GET, "/foo",
                new Header[]{
                        new BasicHeader("Accept-Encoding", " gzip, deflate")
                },
                200,
                new Header[]{
                        new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                        new BasicHeader("Vary", "Accept-Encoding")
                });
        Assertions.assertFalse(impl.requestHeadersMatch(request, entry));
    }

    @Test
    public void testResponseNoCache() {
        entry = makeEntry(new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setNoCache(false)
                .build();

        Assertions.assertFalse(impl.isResponseNoCache(responseCacheControl, entry));

        responseCacheControl = ResponseCacheControl.builder()
                .setNoCache(true)
                .build();

        Assertions.assertTrue(impl.isResponseNoCache(responseCacheControl, entry));

        responseCacheControl = ResponseCacheControl.builder()
                .setNoCache(true)
                .setNoCacheFields("stuff", "more-stuff")
                .build();

        Assertions.assertFalse(impl.isResponseNoCache(responseCacheControl, entry));

        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("stuff", "booh"));

        Assertions.assertTrue(impl.isResponseNoCache(responseCacheControl, entry));
    }

    @Test
    public void testSuitableIfCacheEntryIsFresh() {
        entry = makeEntry(new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfCacheEntryIsNotFresh() {
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .build();
        Assertions.assertEquals(CacheSuitability.STALE, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfRequestHasNoCache() {
        requestCacheControl = RequestCacheControl.builder()
                .setNoCache(true)
                .build();
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertEquals(CacheSuitability.REVALIDATION_REQUIRED, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfAgeExceedsRequestMaxAge() {
        requestCacheControl = RequestCacheControl.builder()
                .setMaxAge(10)
                .build();
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        Assertions.assertEquals(CacheSuitability.REVALIDATION_REQUIRED, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfFreshAndAgeIsUnderRequestMaxAge() {
        requestCacheControl = RequestCacheControl.builder()
                .setMaxAge(15)
                .build();
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfFreshAndFreshnessLifetimeGreaterThanRequestMinFresh() {
        requestCacheControl = RequestCacheControl.builder()
                .setMinFresh(10)
                .build();
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfFreshnessLifetimeLessThanRequestMinFresh() {
        requestCacheControl = RequestCacheControl.builder()
                .setMinFresh(10)
                .build();
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(15)
                .build();
        Assertions.assertEquals(CacheSuitability.REVALIDATION_REQUIRED, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableEvenIfStaleButPermittedByRequestMaxStale() {
        requestCacheControl = RequestCacheControl.builder()
                .setMaxStale(10)
                .build();
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = makeEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .build();
        Assertions.assertEquals(CacheSuitability.FRESH_ENOUGH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfStaleButTooStaleForRequestMaxStale() {
        requestCacheControl = RequestCacheControl.builder()
                .setMaxStale(2)
                .build();
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .build();
        Assertions.assertEquals(CacheSuitability.REVALIDATION_REQUIRED, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsHeuristicallyFreshEnough() {
        final Instant oneSecondAgo = now.minusSeconds(1);
        final Instant twentyOneSecondsAgo = now.minusSeconds(21);

        entry = makeEntry(oneSecondAgo, oneSecondAgo,
                new BasicHeader("Date", DateUtils.formatStandardDate(oneSecondAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(twentyOneSecondsAgo)));

        final CacheConfig config = CacheConfig.custom()
            .setHeuristicCachingEnabled(true)
            .setHeuristicCoefficient(0.1f).build();
        impl = new CachedResponseSuitabilityChecker(config);

        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsHeuristicallyFreshEnoughByDefault() {
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));

        final CacheConfig config = CacheConfig.custom()
            .setHeuristicCachingEnabled(true)
            .setHeuristicDefaultLifetime(TimeValue.ofSeconds(20L))
            .build();
        impl = new CachedResponseSuitabilityChecker(config);

        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfRequestMethodisHEAD() {
        final HttpRequest headRequest = new BasicHttpRequest("HEAD", "/foo");
        entry = makeEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, headRequest, entry, now));
    }

    @Test
    public void testSuitableForGETIfEntryDoesNotSpecifyARequestMethodButContainsEntity() {
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableForGETIfHeadResponseCachingEnabledAndEntryDoesNotSpecifyARequestMethodButContains204Response() {
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableForHEADIfHeadResponseCachingEnabledAndEntryDoesNotSpecifyARequestMethod() {
        final HttpRequest headRequest = new BasicHttpRequest("HEAD", "/foo");
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        final Header[] headers = {

        };
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertEquals(CacheSuitability.FRESH, impl.assessSuitability(requestCacheControl, responseCacheControl, headRequest, entry, now));
    }

    @Test
    public void testNotSuitableIfGetRequestWithHeadCacheEntry() {
        // Prepare a cache entry with HEAD method
        entry = makeEntry(Method.HEAD, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        // Validate that the cache entry is not suitable for the GET request
        Assertions.assertEquals(CacheSuitability.MISMATCH, impl.assessSuitability(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfErrorRequestCacheControl() {
        // Prepare a cache entry with HEAD method
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .build();

        // the entry has been stale for 6 seconds

        requestCacheControl = RequestCacheControl.builder()
                .setStaleIfError(10)
                .build();
        Assertions.assertTrue(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));

        requestCacheControl = RequestCacheControl.builder()
                .setStaleIfError(5)
                .build();
        Assertions.assertFalse(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));

        requestCacheControl = RequestCacheControl.builder()
                .setStaleIfError(10)
                .setMinFresh(4) // should take precedence over stale-if-error
                .build();
        Assertions.assertFalse(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));

        requestCacheControl = RequestCacheControl.builder()
                .setStaleIfError(-1) // not set or not valid
                .build();
        Assertions.assertFalse(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));
    }

    @Test
    public void testSuitableIfErrorResponseCacheControl() {
        // Prepare a cache entry with HEAD method
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .setStaleIfError(10)
                .build();

        // the entry has been stale for 6 seconds

        Assertions.assertTrue(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));

        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .setStaleIfError(5)
                .build();
        Assertions.assertFalse(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));

        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .setStaleIfError(-1) // not set or not valid
                .build();
        Assertions.assertFalse(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));
    }

    @Test
    public void testSuitableIfErrorRequestCacheControlTakesPrecedenceOverResponseCacheControl() {
        // Prepare a cache entry with HEAD method
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .setStaleIfError(5)
                .build();

        // the entry has been stale for 6 seconds

        Assertions.assertFalse(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));

        requestCacheControl = RequestCacheControl.builder()
                .setStaleIfError(10)
                .build();
        Assertions.assertTrue(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));
    }

    @Test
    public void testSuitableIfErrorConfigDefault() {
        // Prepare a cache entry with HEAD method
        entry = makeEntry(Method.GET, "/foo",
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .build();
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom()
                .setStaleIfErrorEnabled(true)
                .build());
        Assertions.assertTrue(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));

        requestCacheControl = RequestCacheControl.builder()
                .setStaleIfError(5)
                .build();

        Assertions.assertFalse(impl.isSuitableIfError(requestCacheControl, responseCacheControl, entry, now));
    }

}
