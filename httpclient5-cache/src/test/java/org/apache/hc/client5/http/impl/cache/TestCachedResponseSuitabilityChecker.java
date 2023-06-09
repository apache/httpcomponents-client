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
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
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

    private HttpCacheEntry getEntry(final Header[] headers) {
        return HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, headers);
    }

    @Test
    public void testSuitableIfCacheEntryIsFresh() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfCacheEntryIsNotFresh() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .build();
        Assertions.assertFalse(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfRequestHasNoCache() {
        requestCacheControl = RequestCacheControl.builder()
                .setNoCache(true)
                .build();
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertFalse(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfAgeExceedsRequestMaxAge() {
        requestCacheControl = RequestCacheControl.builder()
                .setMaxAge(10)
                .build();
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfFreshAndAgeIsUnderRequestMaxAge() {
        requestCacheControl = RequestCacheControl.builder()
                .setMaxAge(15)
                .build();
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfFreshAndFreshnessLifetimeGreaterThanRequestMinFresh() {
        requestCacheControl = RequestCacheControl.builder()
                .setMinFresh(10)
                .build();
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfFreshnessLifetimeLessThanRequestMinFresh() {
        requestCacheControl = RequestCacheControl.builder()
                .setMinFresh(10)
                .build();
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(15)
                .build();
        Assertions.assertFalse(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableEvenIfStaleButPermittedByRequestMaxStale() {
        requestCacheControl = RequestCacheControl.builder()
                .setMaxStale(10)
                .build();
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .build();
        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testNotSuitableIfStaleButTooStaleForRequestMaxStale() {
        requestCacheControl = RequestCacheControl.builder()
                .setMaxStale(2)
                .build();
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(5)
                .build();
        Assertions.assertFalse(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsHeuristicallyFreshEnough() {
        final Instant oneSecondAgo = now.minusSeconds(1);
        final Instant twentyOneSecondsAgo = now.minusSeconds(21);

        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(oneSecondAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(twentyOneSecondsAgo))
        };

        entry = HttpTestUtils.makeCacheEntry(oneSecondAgo, oneSecondAgo, headers);

        final CacheConfig config = CacheConfig.custom()
            .setHeuristicCachingEnabled(true)
            .setHeuristicCoefficient(0.1f).build();
        impl = new CachedResponseSuitabilityChecker(config);

        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsHeuristicallyFreshEnoughByDefault() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };

        entry = getEntry(headers);

        final CacheConfig config = CacheConfig.custom()
            .setHeuristicCachingEnabled(true)
            .setHeuristicDefaultLifetime(TimeValue.ofSeconds(20L))
            .build();
        impl = new CachedResponseSuitabilityChecker(config);

        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableIfRequestMethodisHEAD() {
        final HttpRequest headRequest = new BasicHttpRequest("HEAD", "/foo");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = getEntry(headers);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, headRequest, entry, now));
    }

    @Test
    public void testNotSuitableIfRequestMethodIsGETAndEntryResourceIsNull() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, Method.HEAD, HttpStatus.SC_OK, headers, null, null);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertFalse(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableForGETIfEntryDoesNotSpecifyARequestMethodButContainsEntity() {
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, Method.GET, HttpStatus.SC_OK, headers, HttpTestUtils.getRandomBytes(128), null);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableForGETIfHeadResponseCachingEnabledAndEntryDoesNotSpecifyARequestMethodButContains204Response() {
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
        };
        entry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, Method.GET, HttpStatus.SC_OK, headers, null, null);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }

    @Test
    public void testSuitableForHEADIfHeadResponseCachingEnabledAndEntryDoesNotSpecifyARequestMethod() {
        final HttpRequest headRequest = new BasicHttpRequest("HEAD", "/foo");
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, Method.GET, HttpStatus.SC_OK, headers, null, null);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();

        Assertions.assertTrue(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, headRequest, entry, now));
    }

    @Test
    public void testNotSuitableIfGetRequestWithHeadCacheEntry() {
        // Prepare a cache entry with HEAD method
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
        };
        entry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, Method.HEAD, HttpStatus.SC_OK, headers, null, null);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        // Validate that the cache entry is not suitable for the GET request
        Assertions.assertFalse(impl.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, entry, now));
    }
}
