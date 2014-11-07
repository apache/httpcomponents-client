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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HTTP;
import org.junit.Before;
import org.junit.Test;

public class TestCacheValidityPolicy {

    private CacheValidityPolicy impl;
    private Date now;
    private Date oneSecondAgo;
    private Date sixSecondsAgo;
    private Date tenSecondsAgo;
    private Date elevenSecondsAgo;

    @Before
    public void setUp() {
        impl = new CacheValidityPolicy();
        now = new Date();
        oneSecondAgo = new Date(now.getTime() - 1 * 1000L);
        sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
    }

    @Test
    public void testApparentAgeIsMaxIntIfDateHeaderNotPresent() {
        final Header[] headers = {
                new BasicHeader("Server", "MockServer/1.0")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(2147483648L, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testApparentAgeIsResponseReceivedTimeLessDateHeader() {
        final Header[] headers = new Header[] { new BasicHeader("Date", DateUtils
                .formatDate(tenSecondsAgo)) };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, sixSecondsAgo, headers);
        assertEquals(4, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testNegativeApparentAgeIsBroughtUpToZero() {
        final Header[] headers = new Header[] { new BasicHeader("Date", DateUtils
                .formatDate(sixSecondsAgo)) };
        final HttpCacheEntry entry  = HttpTestUtils.makeCacheEntry(now,tenSecondsAgo,headers);
        assertEquals(0, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testCorrectedReceivedAgeIsAgeHeaderIfLarger() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "10"), };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        impl = new CacheValidityPolicy() {
            @Override
            protected long getApparentAgeSecs(final HttpCacheEntry ent) {
                return 6;
            }
        };
        assertEquals(10, impl.getCorrectedReceivedAgeSecs(entry));
    }

    @Test
    public void testCorrectedReceivedAgeIsApparentAgeIfLarger() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "6"), };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        impl = new CacheValidityPolicy() {
            @Override
            protected long getApparentAgeSecs(final HttpCacheEntry ent) {
                return 10;
            }
        };
        assertEquals(10, impl.getCorrectedReceivedAgeSecs(entry));
    }

    @Test
    public void testResponseDelayIsDifferenceBetweenResponseAndRequestTimes() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, sixSecondsAgo);
        assertEquals(4, impl.getResponseDelaySecs(entry));
    }

    @Test
    public void testCorrectedInitialAgeIsCorrectedReceivedAgePlusResponseDelay() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            protected long getCorrectedReceivedAgeSecs(final HttpCacheEntry ent) {
                return 7;
            }

            @Override
            protected long getResponseDelaySecs(final HttpCacheEntry ent) {
                return 13;
            }
        };
        assertEquals(20, impl.getCorrectedInitialAgeSecs(entry));
    }

    @Test
    public void testResidentTimeSecondsIsTimeSinceResponseTime() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, sixSecondsAgo);
        assertEquals(6, impl.getResidentTimeSecs(entry, now));
    }

    @Test
    public void testCurrentAgeIsCorrectedInitialAgePlusResidentTime() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            protected long getCorrectedInitialAgeSecs(final HttpCacheEntry ent) {
                return 11;
            }
            @Override
            protected long getResidentTimeSecs(final HttpCacheEntry ent, final Date d) {
                return 17;
            }
        };
        assertEquals(28, impl.getCurrentAgeSecs(entry, new Date()));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeIfPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeIfPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMostRestrictiveOfMaxAgeAndSMaxAge() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Cache-Control", "s-maxage=20") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));

        headers = new Header[] { new BasicHeader("Cache-Control", "max-age=20"),
                new BasicHeader("Cache-Control", "s-maxage=10") };
        entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeEvenIfExpiresIsPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeEvenIfExpiresIsPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10"),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsFromExpiresHeaderIfNoMaxAge() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(4, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testHeuristicFreshnessLifetime() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(oneSecondAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatDate(elevenSecondsAgo))
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(1, impl.getHeuristicFreshnessLifetimeSecs(entry, 0.1f, 0));
    }

    @Test
    public void testHeuristicFreshnessLifetimeDefaultsProperly() {
        final long defaultFreshness = 10;
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(defaultFreshness, impl.getHeuristicFreshnessLifetimeSecs(entry, 0.1f, defaultFreshness));
    }

    @Test
    public void testHeuristicFreshnessLifetimeIsNonNegative() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(elevenSecondsAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatDate(oneSecondAgo))
        };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.getHeuristicFreshnessLifetimeSecs(entry, 0.1f, 10) >= 0);
    }

    @Test
    public void testResponseIsFreshIfFreshnessLifetimeExceedsCurrentAge() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            public long getCurrentAgeSecs(final HttpCacheEntry e, final Date d) {
                assertSame(entry, e);
                assertEquals(now, d);
                return 6;
            }
            @Override
            public long getFreshnessLifetimeSecs(final HttpCacheEntry e) {
                assertSame(entry, e);
                return 10;
            }
        };
        assertTrue(impl.isResponseFresh(entry, now));
    }

    @Test
    public void testResponseIsNotFreshIfFreshnessLifetimeEqualsCurrentAge() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            public long getCurrentAgeSecs(final HttpCacheEntry e, final Date d) {
                assertEquals(now, d);
                assertSame(entry, e);
                return 6;
            }
            @Override
            public long getFreshnessLifetimeSecs(final HttpCacheEntry e) {
                assertSame(entry, e);
                return 6;
            }
        };
        assertFalse(impl.isResponseFresh(entry, now));
    }

    @Test
    public void testResponseIsNotFreshIfCurrentAgeExceedsFreshnessLifetime() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            public long getCurrentAgeSecs(final HttpCacheEntry e, final Date d) {
                assertEquals(now, d);
                assertSame(entry, e);
                return 10;
            }
            @Override
            public long getFreshnessLifetimeSecs(final HttpCacheEntry e) {
                assertSame(entry, e);
                return 6;
            }
        };
        assertFalse(impl.isResponseFresh(entry, now));
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeETag() {
        final Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("ETag", "somevalue")};
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.isRevalidatable(entry));
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeLastModifiedDate() {
        final Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", DateUtils.formatDate(new Date())) };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.isRevalidatable(entry));
    }

    @Test
    public void testCacheEntryIsNotRevalidatableIfNoAppropriateHeaders() {
        final Header[] headers =  {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("Cache-Control", "public") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertFalse(impl.isRevalidatable(entry));
    }

    @Test
    public void testMissingContentLengthDoesntInvalidateEntry() {
        final int contentLength = 128;
        final Header[] headers = {}; // no Content-Length header
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers, HttpTestUtils.getRandomBytes(contentLength));
        assertTrue(impl.contentLengthHeaderMatchesActualLength(entry));
    }

    @Test
    public void testCorrectContentLengthDoesntInvalidateEntry() {
        final int contentLength = 128;
        final Header[] headers = { new BasicHeader(HTTP.CONTENT_LEN, Integer.toString(contentLength)) };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers, HttpTestUtils.getRandomBytes(contentLength));
        assertTrue(impl.contentLengthHeaderMatchesActualLength(entry));
    }

    @Test
    public void testWrongContentLengthInvalidatesEntry() {
        final int contentLength = 128;
        final Header[] headers = {new BasicHeader(HTTP.CONTENT_LEN, Integer.toString(contentLength+1))};
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers, HttpTestUtils.getRandomBytes(contentLength));
        assertFalse(impl.contentLengthHeaderMatchesActualLength(entry));
    }

    @Test
    public void testNullResourceInvalidatesEntry() {
        final int contentLength = 128;
        final Header[] headers = {new BasicHeader(HTTP.CONTENT_LEN, Integer.toString(contentLength))};
        final HttpCacheEntry entry = HttpTestUtils.makeHeadCacheEntry(headers);
        assertFalse(impl.contentLengthHeaderMatchesActualLength(entry));
    }

    @Test
    public void testMalformedContentLengthReturnsNegativeOne() {
        final Header[] headers = new Header[] { new BasicHeader("Content-Length", "asdf") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(-1, impl.getContentLengthValue(entry));
    }

    @Test
    public void testNegativeAgeHeaderValueReturnsMaxAge() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "-100") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(CacheValidityPolicy.MAX_AGE, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedAgeHeaderValueReturnsMaxAge() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "asdf") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(CacheValidityPolicy.MAX_AGE, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedCacheControlMaxAgeHeaderReturnsZero() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=asdf") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(0, impl.getMaxAge(entry));
    }

    @Test
    public void testMalformedExpirationDateReturnsNull() {
        final Header[] headers = new Header[] { new BasicHeader("Expires", "asdf") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertNull(impl.getExpirationDate(entry));
    }

    @Test
    public void testMustRevalidateIsFalseIfDirectiveNotPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control","public") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertFalse(impl.mustRevalidate(entry));
    }

    @Test
    public void testMustRevalidateIsTrueWhenDirectiveIsPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control","public, must-revalidate") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.mustRevalidate(entry));
    }

    @Test
    public void testProxyRevalidateIsFalseIfDirectiveNotPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control","public") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertFalse(impl.proxyRevalidate(entry));
    }

    @Test
    public void testProxyRevalidateIsTrueWhenDirectiveIsPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control","public, proxy-revalidate") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.proxyRevalidate(entry));
    }

    @Test
    public void testMayReturnStaleIfErrorInResponseIsTrueWithinStaleness(){
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-if-error=15")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        final HttpRequest req = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        assertTrue(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayReturnStaleIfErrorInRequestIsTrueWithinStaleness(){
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        final HttpRequest req = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","stale-if-error=15");
        assertTrue(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayNotReturnStaleIfErrorInResponseAndAfterResponseWindow(){
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-if-error=1")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        final HttpRequest req = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        assertFalse(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayNotReturnStaleIfErrorInResponseAndAfterRequestWindow(){
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        final HttpRequest req = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","stale-if-error=1");
        assertFalse(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayReturnStaleWhileRevalidatingIsFalseWhenDirectiveIsAbsent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-control", "public") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);

        assertFalse(impl.mayReturnStaleWhileRevalidating(entry, now));
    }

    @Test
    public void testMayReturnStaleWhileRevalidatingIsTrueWhenWithinStaleness() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=15")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);

        assertTrue(impl.mayReturnStaleWhileRevalidating(entry, now));
    }

    @Test
    public void testMayReturnStaleWhileRevalidatingIsFalseWhenPastStaleness() {
        final Date twentyFiveSecondsAgo = new Date(now.getTime() - 25 * 1000L);
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(twentyFiveSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=15")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);

        assertFalse(impl.mayReturnStaleWhileRevalidating(entry, now));
    }

    @Test
    public void testMayReturnStaleWhileRevalidatingIsFalseWhenDirectiveEmpty() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);

        assertFalse(impl.mayReturnStaleWhileRevalidating(entry, now));
    }
}
