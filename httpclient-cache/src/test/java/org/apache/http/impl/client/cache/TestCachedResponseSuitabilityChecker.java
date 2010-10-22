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

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCachedResponseSuitabilityChecker {

    private Date now;
    private Date elevenSecondsAgo;
    private Date tenSecondsAgo;
    private Date nineSecondsAgo;

    private HttpHost host;
    private HttpRequest request;
    private HttpCacheEntry entry;
    private CacheValidityPolicy mockValidityPolicy;
    private CachedResponseSuitabilityChecker impl;

    @Before
    public void setUp() {
        now = new Date();
        elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);

        host = new HttpHost("foo.example.com");
        request = new BasicHttpRequest("GET", "/foo", HttpVersion.HTTP_1_1);
        mockValidityPolicy = EasyMock.createMock(CacheValidityPolicy.class);
        entry = HttpTestUtils.makeCacheEntry();

        impl = new CachedResponseSuitabilityChecker(new CacheConfig());
    }

    private HttpCacheEntry getEntry(Header[] headers) {
        return HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, headers);
    }

    public void replayMocks() {
        EasyMock.replay(mockValidityPolicy);
    }

    public void verifyMocks() {
        EasyMock.verify(mockValidityPolicy);
    }

    @Test
    public void testNotSuitableIfContentLengthHeaderIsWrong() {
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","1")
        };
        entry = getEntry(headers);
        Assert.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsFresh() {
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfCacheEntryIsNotFresh() {
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfRequestHasNoCache() {
        request.addHeader("Cache-Control", "no-cache");
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfAgeExceedsRequestMaxAge() {
        request.addHeader("Cache-Control", "max-age=10");
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfFreshAndAgeIsUnderRequestMaxAge() {
        request.addHeader("Cache-Control", "max-age=15");
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfFreshAndFreshnessLifetimeGreaterThanRequestMinFresh() {
        request.addHeader("Cache-Control", "min-fresh=10");
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfFreshnessLifetimeLessThanRequestMinFresh() {
        request.addHeader("Cache-Control", "min-fresh=10");
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=15"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableEvenIfStaleButPermittedByRequestMaxStale() {
        request.addHeader("Cache-Control", "max-stale=10");
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testNotSuitableIfStaleButTooStaleForRequestMaxStale() {
        request.addHeader("Cache-Control", "max-stale=2");
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }


    @Test
    public void testMalformedCacheControlMaxAgeRequestHeaderCausesUnsuitableEntry() {
        request.addHeader(new BasicHeader("Cache-Control", "max-age=foo"));
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testMalformedCacheControlMinFreshRequestHeaderCausesUnsuitableEntry() {
        request.addHeader(new BasicHeader("Cache-Control", "min-fresh=foo"));
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length","128")
        };
        entry = getEntry(headers);
        Assert.assertFalse(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsHeuristicallyFreshEnough() {
        Date oneSecondAgo = new Date(now.getTime() - 1 * 1000L);
        Date twentyOneSecondsAgo = new Date(now.getTime() - 21 * 1000L);

        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(oneSecondAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatDate(twentyOneSecondsAgo)),
                new BasicHeader("Content-Length", "128")
        };

        entry = HttpTestUtils.makeCacheEntry(oneSecondAgo, oneSecondAgo, headers);

        CacheConfig config = new CacheConfig();
        config.setHeuristicCachingEnabled(true);
        config.setHeuristicCoefficient(0.1f);
        impl = new CachedResponseSuitabilityChecker(config);

        Assert.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }

    @Test
    public void testSuitableIfCacheEntryIsHeuristicallyFreshEnoughByDefault() {
        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Content-Length", "128")
        };

        entry = getEntry(headers);

        CacheConfig config = new CacheConfig();
        config.setHeuristicCachingEnabled(true);
        config.setHeuristicDefaultLifetime(20);
        impl = new CachedResponseSuitabilityChecker(config);

        Assert.assertTrue(impl.canCachedResponseBeUsed(host, request, entry, now));
    }
}