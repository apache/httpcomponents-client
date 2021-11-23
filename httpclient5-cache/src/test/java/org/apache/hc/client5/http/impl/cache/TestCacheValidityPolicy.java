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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestCacheValidityPolicy {

    private CacheValidityPolicy impl;
    private Instant now;
    private Instant oneSecondAgo;
    private Instant sixSecondsAgo;
    private Instant tenSecondsAgo;
    private Instant elevenSecondsAgo;

    @BeforeEach
    public void setUp() {
        impl = new CacheValidityPolicy();
        now = Instant.now();
        oneSecondAgo = now.minusSeconds(1);
        sixSecondsAgo = now.minusSeconds(6);
        tenSecondsAgo = now.minusSeconds(10);
        elevenSecondsAgo = now.minusSeconds(11);
    }

    @Test
    public void testApparentAgeIsMaxIntIfDateHeaderNotPresent() {
        final Header[] headers = {
                new BasicHeader("Server", "MockServer/1.0")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(CacheValidityPolicy.MAX_AGE, impl.getApparentAge(entry));
    }

    @Test
    public void testApparentAgeIsResponseReceivedTimeLessDateHeader() {
        final Header[] headers = new Header[] { new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)) };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, sixSecondsAgo, headers);
        assertEquals(TimeValue.ofSeconds(4), impl.getApparentAge(entry));
    }

    @Test
    public void testNegativeApparentAgeIsBroughtUpToZero() {
        final Header[] headers = new Header[] { new BasicHeader("Date", DateUtils.formatStandardDate(sixSecondsAgo)) };
        final HttpCacheEntry entry  = HttpTestUtils.makeCacheEntry(now, tenSecondsAgo, headers);
        assertEquals(TimeValue.ofSeconds(0), impl.getApparentAge(entry));
    }

    @Test
    public void testCorrectedReceivedAgeIsAgeHeaderIfLarger() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "10"), };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        impl = new CacheValidityPolicy() {
            @Override
            protected TimeValue getApparentAge(final HttpCacheEntry ent) {
                return TimeValue.ofSeconds(6);
            }
        };
        assertEquals(TimeValue.ofSeconds(10), impl.getCorrectedReceivedAge(entry));
    }

    @Test
    public void testCorrectedReceivedAgeIsApparentAgeIfLarger() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "6"), };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        impl = new CacheValidityPolicy() {
            @Override
            protected TimeValue getApparentAge(final HttpCacheEntry ent) {
                return TimeValue.ofSeconds(10);
            }
        };
        assertEquals(TimeValue.ofSeconds(10), impl.getCorrectedReceivedAge(entry));
    }

    @Test
    public void testResponseDelayIsDifferenceBetweenResponseAndRequestTimes() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, sixSecondsAgo);
        assertEquals(TimeValue.ofSeconds(4), impl.getResponseDelay(entry));
    }

    @Test
    public void testCorrectedInitialAgeIsCorrectedReceivedAgePlusResponseDelay() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            protected TimeValue getCorrectedReceivedAge(final HttpCacheEntry ent) {
                return TimeValue.ofSeconds(7);
            }

            @Override
            protected TimeValue getResponseDelay(final HttpCacheEntry ent) {
                return TimeValue.ofSeconds(13);
            }
        };
        assertEquals(TimeValue.ofSeconds(20), impl.getCorrectedInitialAge(entry));
    }

    @Test
    public void testResidentTimeSecondsIsTimeSinceResponseTime() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, sixSecondsAgo);
        assertEquals(TimeValue.ofSeconds(6), impl.getResidentTime(entry, now));
    }

    @Test
    public void testCurrentAgeIsCorrectedInitialAgePlusResidentTime() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            protected TimeValue getCorrectedInitialAge(final HttpCacheEntry ent) {
                return TimeValue.ofSeconds(11);
            }
            @Override
            protected TimeValue getResidentTime(final HttpCacheEntry ent, final Instant d) {
                return TimeValue.ofSeconds(17);
            }
        };
        assertEquals(TimeValue.ofSeconds(28), impl.getCurrentAge(entry, Instant.now()));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeIfPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeIfPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMostRestrictiveOfMaxAgeAndSMaxAge() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Cache-Control", "s-maxage=20") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(entry));

        headers = new Header[] { new BasicHeader("Cache-Control", "max-age=20"),
                new BasicHeader("Cache-Control", "s-maxage=10") };
        entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeEvenIfExpiresIsPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo)) };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(entry));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeEvenIfExpiresIsPresent() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10"),
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo)) };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(entry));
    }

    @Test
    public void testFreshnessLifetimeIsFromExpiresHeaderIfNoMaxAge() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo)) };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(4), impl.getFreshnessLifetime(entry));
    }

    @Test
    public void testHeuristicFreshnessLifetime() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(oneSecondAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(elevenSecondsAgo))
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(1), impl.getHeuristicFreshnessLifetime(entry, 0.1f, TimeValue.ZERO_MILLISECONDS));
    }

    @Test
    public void testHeuristicFreshnessLifetimeDefaultsProperly() {
        final TimeValue defaultFreshness = TimeValue.ofSeconds(10);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(defaultFreshness, impl.getHeuristicFreshnessLifetime(entry, 0.1f, defaultFreshness));
    }

    @Test
    public void testHeuristicFreshnessLifetimeIsNonNegative() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(elevenSecondsAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(oneSecondAgo))
        };

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(TimeValue.isNonNegative(impl.getHeuristicFreshnessLifetime(entry, 0.1f, TimeValue.ofSeconds(10))));
    }

    @Test
    public void testResponseIsFreshIfFreshnessLifetimeExceedsCurrentAge() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            public TimeValue getCurrentAge(final HttpCacheEntry e, final Instant d) {
                assertSame(entry, e);
                assertEquals(now, d);
                return TimeValue.ofSeconds(6);
            }
            @Override
            public TimeValue getFreshnessLifetime(final HttpCacheEntry e) {
                assertSame(entry, e);
                return TimeValue.ofSeconds(10);
            }
        };
        assertTrue(impl.isResponseFresh(entry, now));
    }

    @Test
    public void testResponseIsNotFreshIfFreshnessLifetimeEqualsCurrentAge() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            public TimeValue getCurrentAge(final HttpCacheEntry e, final Instant d) {
                assertEquals(now, d);
                assertSame(entry, e);
                return TimeValue.ofSeconds(6);
            }
            @Override
            public TimeValue getFreshnessLifetime(final HttpCacheEntry e) {
                assertSame(entry, e);
                return TimeValue.ofSeconds(6);
            }
        };
        assertFalse(impl.isResponseFresh(entry, now));
    }

    @Test
    public void testResponseIsNotFreshIfCurrentAgeExceedsFreshnessLifetime() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            public TimeValue getCurrentAge(final HttpCacheEntry e, final Instant d) {
                assertEquals(now, d);
                assertSame(entry, e);
                return TimeValue.ofSeconds(10);
            }
            @Override
            public TimeValue getFreshnessLifetime(final HttpCacheEntry e) {
                assertSame(entry, e);
                return TimeValue.ofSeconds(6);
            }
        };
        assertFalse(impl.isResponseFresh(entry, now));
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeETag() {
        final Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatStandardDate(Instant.now())),
                new BasicHeader("ETag", "somevalue")};
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.isRevalidatable(entry));
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeLastModifiedDate() {
        final Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatStandardDate(Instant.now())),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(Instant.now())) };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.isRevalidatable(entry));
    }

    @Test
    public void testCacheEntryIsNotRevalidatableIfNoAppropriateHeaders() {
        final Header[] headers =  {
                new BasicHeader("Expires", DateUtils.formatStandardDate(Instant.now())),
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
        final Header[] headers = { new BasicHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(contentLength)) };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers, HttpTestUtils.getRandomBytes(contentLength));
        assertTrue(impl.contentLengthHeaderMatchesActualLength(entry));
    }

    @Test
    public void testWrongContentLengthInvalidatesEntry() {
        final int contentLength = 128;
        final Header[] headers = {new BasicHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(contentLength+1))};
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers, HttpTestUtils.getRandomBytes(contentLength));
        assertFalse(impl.contentLengthHeaderMatchesActualLength(entry));
    }

    @Test
    public void testNullResourceInvalidatesEntry() {
        final int contentLength = 128;
        final Header[] headers = {new BasicHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(contentLength))};
        final HttpCacheEntry entry = HttpTestUtils.makeHeadCacheEntry(headers);
        assertFalse(impl.contentLengthHeaderMatchesActualLength(entry));
    }

    @Test
    public void testNegativeAgeHeaderValueReturnsMaxAge() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "-100") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        // in seconds
        assertEquals(CacheValidityPolicy.MAX_AGE.toSeconds(), impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedAgeHeaderValueReturnsMaxAge() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "asdf") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        // in seconds
        assertEquals(CacheValidityPolicy.MAX_AGE.toSeconds(), impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedCacheControlMaxAgeHeaderReturnsZero() {
        final Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=asdf") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        // in seconds
        assertEquals(0L, impl.getMaxAge(entry));
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
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-if-error=15")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        final HttpRequest req = new BasicHttpRequest("GET","/");
        assertTrue(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayReturnStaleIfErrorInRequestIsTrueWithinStaleness(){
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        final HttpRequest req = new BasicHttpRequest("GET","/");
        req.setHeader("Cache-Control","stale-if-error=15");
        assertTrue(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayNotReturnStaleIfErrorInResponseAndAfterResponseWindow(){
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-if-error=1")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        final HttpRequest req = new BasicHttpRequest("GET","/");
        assertFalse(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayNotReturnStaleIfErrorInResponseAndAfterRequestWindow(){
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        final HttpRequest req = new BasicHttpRequest("GET","/");
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
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=15")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);

        assertTrue(impl.mayReturnStaleWhileRevalidating(entry, now));
    }

    @Test
    public void testMayReturnStaleWhileRevalidatingIsFalseWhenPastStaleness() {
        final Instant twentyFiveSecondsAgo = now.minusSeconds(25);
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(twentyFiveSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=15")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);

        assertFalse(impl.mayReturnStaleWhileRevalidating(entry, now));
    }

    @Test
    public void testMayReturnStaleWhileRevalidatingIsFalseWhenDirectiveEmpty() {
        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=")
        };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);

        assertFalse(impl.mayReturnStaleWhileRevalidating(entry, now));
    }
}
