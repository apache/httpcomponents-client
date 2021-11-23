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
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
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

    private HttpHost host;
    private HttpRequest request;
    private HttpCacheEntry entry;
    private CachedResponseSuitabilityChecker impl;

    @BeforeEach
    public void setUp() {
        now = Instant.now();
        elevenSecondsAgo = now.minusSeconds(11);
        tenSecondsAgo = now.minusSeconds(10);
        nineSecondsAgo = now.minusSeconds(9);

        host = new HttpHost("foo.example.com");
        request = new BasicHttpRequest("GET", "/foo");
        entry = HttpTestUtils.makeCacheEntry();

        impl = new CachedResponseSuitabilityChecker(CacheConfig.DEFAULT);
    }

    private HttpCacheEntry getEntry(final Header[] headers) {
        return HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, headers);
    }

    @Test
    public void testNotSuitableIfContentLengthHeaderIsWrong() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","1")
        };
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsFresh() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfCacheEntryIsNotFresh() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfRequestHasNoCache() {
        request.addHeader("Cache-Control", "no-cache");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfAgeExceedsRequestMaxAge() {
        request.addHeader("Cache-Control", "max-age=10");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfFreshAndAgeIsUnderRequestMaxAge() {
        request.addHeader("Cache-Control", "max-age=15");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfFreshAndFreshnessLifetimeGreaterThanRequestMinFresh() {
        request.addHeader("Cache-Control", "min-fresh=10");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfFreshnessLifetimeLessThanRequestMinFresh() {
        request.addHeader("Cache-Control", "min-fresh=10");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=15"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableEvenIfStaleButPermittedByRequestMaxStale() {
        request.addHeader("Cache-Control", "max-stale=10");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfStaleButTooStaleForRequestMaxStale() {
        request.addHeader("Cache-Control", "max-stale=2");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }


    @Test
    public void testMalformedCacheControlMaxAgeRequestHeaderCausesUnsuitableEntry() {
        request.addHeader(new BasicHeader("Cache-Control", "max-age=foo"));
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testMalformedCacheControlMinFreshRequestHeaderCausesUnsuitableEntry() {
        request.addHeader(new BasicHeader("Cache-Control", "min-fresh=foo"));
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsHeuristicallyFreshEnough() {
        final Instant oneSecondAgo = now.minusSeconds(1);
        final Instant twentyOneSecondsAgo = now.minusSeconds(21);

        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(oneSecondAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(twentyOneSecondsAgo)),
                new BasicHeader("Content-Length", "128")
        };

        entry = HttpTestUtils.makeCacheEntry(oneSecondAgo, oneSecondAgo, headers);

        final CacheConfig config = CacheConfig.custom()
            .setHeuristicCachingEnabled(true)
            .setHeuristicCoefficient(0.1f).build();
        impl = new CachedResponseSuitabilityChecker(config);

        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsHeuristicallyFreshEnoughByDefault() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Content-Length", "128")
        };

        entry = getEntry(headers);

        final CacheConfig config = CacheConfig.custom()
            .setHeuristicCachingEnabled(true)
            .setHeuristicDefaultLifetime(TimeValue.ofSeconds(20L))
            .build();
        impl = new CachedResponseSuitabilityChecker(config);

        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfRequestMethodisHEAD() {
        final HttpRequest headRequest = new BasicHttpRequest("HEAD", "/foo");
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);

        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, headRequest, entry, now));
    }

    @Test
    public void testNotSuitableIfRequestMethodIsGETAndEntryResourceIsNull() {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = HttpTestUtils.makeHeadCacheEntry(headers);

        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableForGETIfEntryDoesNotSpecifyARequestMethodOrEntity() {
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = HttpTestUtils.makeCacheEntryWithNoRequestMethodOrEntity(headers);

        Assertions.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableForGETIfEntryDoesNotSpecifyARequestMethodButContainsEntity() {
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = HttpTestUtils.makeCacheEntryWithNoRequestMethod(headers);

        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableForGETIfHeadResponseCachingEnabledAndEntryDoesNotSpecifyARequestMethodButContains204Response() {
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600")
        };
        entry = HttpTestUtils.make204CacheEntryWithNoRequestMethod(headers);

        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableForHEADIfHeadResponseCachingEnabledAndEntryDoesNotSpecifyARequestMethod() {
        final HttpRequest headRequest = new BasicHttpRequest("HEAD", "/foo");
        impl = new CachedResponseSuitabilityChecker(CacheConfig.custom().build());
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = HttpTestUtils.makeHeadCacheEntryWithNoRequestMethod(headers);

        Assertions.assertTrue(impl.canCachedResponseBeUsed(host, headRequest, entry, now));
    }
}
