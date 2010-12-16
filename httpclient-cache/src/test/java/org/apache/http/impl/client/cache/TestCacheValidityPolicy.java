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

import static org.junit.Assert.*;

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
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
        Header[] headers = {
                new BasicHeader("Server", "MockServer/1.0")
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(2147483648L, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testApparentAgeIsResponseReceivedTimeLessDateHeader() {
        Header[] headers = new Header[] { new BasicHeader("Date", DateUtils
                .formatDate(tenSecondsAgo)) };

        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, sixSecondsAgo, headers);
        assertEquals(4, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testNegativeApparentAgeIsBroughtUpToZero() {
        Header[] headers = new Header[] { new BasicHeader("Date", DateUtils
                .formatDate(sixSecondsAgo)) };
        HttpCacheEntry entry  = HttpTestUtils.makeCacheEntry(now,tenSecondsAgo,headers);
        assertEquals(0, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testCorrectedReceivedAgeIsAgeHeaderIfLarger() {
        Header[] headers = new Header[] { new BasicHeader("Age", "10"), };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        impl = new CacheValidityPolicy() {
            @Override
            protected long getApparentAgeSecs(HttpCacheEntry entry) {
                return 6;
            }
        };
        assertEquals(10, impl.getCorrectedReceivedAgeSecs(entry));
    }

    @Test
    public void testCorrectedReceivedAgeIsApparentAgeIfLarger() {
        Header[] headers = new Header[] { new BasicHeader("Age", "6"), };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        impl = new CacheValidityPolicy() {
            @Override
            protected long getApparentAgeSecs(HttpCacheEntry entry) {
                return 10;
            }
        };
        assertEquals(10, impl.getCorrectedReceivedAgeSecs(entry));
    }

    @Test
    public void testResponseDelayIsDifferenceBetweenResponseAndRequestTimes() {
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, sixSecondsAgo);
        assertEquals(4, impl.getResponseDelaySecs(entry));
    }

    @Test
    public void testCorrectedInitialAgeIsCorrectedReceivedAgePlusResponseDelay() {
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            protected long getCorrectedReceivedAgeSecs(HttpCacheEntry entry) {
                return 7;
            }

            @Override
            protected long getResponseDelaySecs(HttpCacheEntry entry) {
                return 13;
            }
        };
        assertEquals(20, impl.getCorrectedInitialAgeSecs(entry));
    }

    @Test
    public void testResidentTimeSecondsIsTimeSinceResponseTime() {
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, sixSecondsAgo);
        impl = new CacheValidityPolicy() {
            @Override
            protected Date getCurrentDate() {
                return now;
            }
        };
        assertEquals(6, impl.getResidentTimeSecs(entry, now));
    }

    @Test
    public void testCurrentAgeIsCorrectedInitialAgePlusResidentTime() {
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            protected long getCorrectedInitialAgeSecs(HttpCacheEntry entry) {
                return 11;
            }
            @Override
            protected long getResidentTimeSecs(HttpCacheEntry entry, Date d) {
                return 17;
            }
        };
        assertEquals(28, impl.getCurrentAgeSecs(entry, new Date()));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeIfPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeIfPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
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
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeEvenIfExpiresIsPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10"),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsFromExpiresHeaderIfNoMaxAge() {
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(4, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testHeuristicFreshnessLifetime() {
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(oneSecondAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatDate(elevenSecondsAgo))
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(1, impl.getHeuristicFreshnessLifetimeSecs(entry, 0.1f, 0));
    }

    @Test
    public void testHeuristicFreshnessLifetimeDefaultsProperly() {
        long defaultFreshness = 10;
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(defaultFreshness, impl.getHeuristicFreshnessLifetimeSecs(entry, 0.1f, defaultFreshness));
    }

    @Test
    public void testHeuristicFreshnessLifetimeIsNonNegative() {
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(elevenSecondsAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatDate(oneSecondAgo))
        };

        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.getHeuristicFreshnessLifetimeSecs(entry, 0.1f, 10) >= 0);
    }

    @Test
    public void testResponseIsFreshIfFreshnessLifetimeExceedsCurrentAge() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        impl = new CacheValidityPolicy() {
            @Override
            public long getCurrentAgeSecs(HttpCacheEntry e, Date d) {
                assertSame(entry, e);
                assertEquals(now, d);
                return 6;
            }
            @Override
            public long getFreshnessLifetimeSecs(HttpCacheEntry e) {
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
            public long getCurrentAgeSecs(HttpCacheEntry e, Date d) {
                assertEquals(now, d);
                assertSame(entry, e);
                return 6;
            }
            @Override
            public long getFreshnessLifetimeSecs(HttpCacheEntry e) {
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
            public long getCurrentAgeSecs(HttpCacheEntry e, Date d) {
                assertEquals(now, d);
                assertSame(entry, e);
                return 10;
            }
            @Override
            public long getFreshnessLifetimeSecs(HttpCacheEntry e) {
                assertSame(entry, e);
                return 6;
            }
        };
        assertFalse(impl.isResponseFresh(entry, now));
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeETag() {
        Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("ETag", "somevalue")};
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.isRevalidatable(entry));
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeLastModifiedDate() {
        Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", DateUtils.formatDate(new Date())) };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.isRevalidatable(entry));
    }

    @Test
    public void testCacheEntryIsNotRevalidatableIfNoAppropriateHeaders() {
        Header[] headers =  {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("Cache-Control", "public") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertFalse(impl.isRevalidatable(entry));
    }

    @Test
    public void testMalformedDateHeaderIsIgnored() {
        Header[] headers = new Header[] { new BasicHeader("Date", "asdf") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertNull(impl.getDateValue(entry));
    }

    @Test
    public void testMalformedContentLengthReturnsNegativeOne() {
        Header[] headers = new Header[] { new BasicHeader("Content-Length", "asdf") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(-1, impl.getContentLengthValue(entry));
    }

    @Test
    public void testNegativeAgeHeaderValueReturnsMaxAge() {
        Header[] headers = new Header[] { new BasicHeader("Age", "-100") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(CacheValidityPolicy.MAX_AGE, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedAgeHeaderValueReturnsMaxAge() {
        Header[] headers = new Header[] { new BasicHeader("Age", "asdf") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(CacheValidityPolicy.MAX_AGE, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedCacheControlMaxAgeHeaderReturnsZero() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=asdf") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(0, impl.getMaxAge(entry));
    }

    @Test
    public void testMalformedExpirationDateReturnsNull() {
        Header[] headers = new Header[] { new BasicHeader("Expires", "asdf") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertNull(impl.getExpirationDate(entry));
    }

    @Test
    public void testMustRevalidateIsFalseIfDirectiveNotPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control","public") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertFalse(impl.mustRevalidate(entry));
    }

    @Test
    public void testMustRevalidateIsTrueWhenDirectiveIsPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control","public, must-revalidate") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.mustRevalidate(entry));
    }

    @Test
    public void testProxyRevalidateIsFalseIfDirectiveNotPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control","public") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertFalse(impl.proxyRevalidate(entry));
    }

    @Test
    public void testProxyRevalidateIsTrueWhenDirectiveIsPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control","public, proxy-revalidate") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertTrue(impl.proxyRevalidate(entry));
    }

    @Test
    public void testMayReturnStaleIfErrorInResponseIsTrueWithinStaleness(){
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-if-error=15")
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        HttpRequest req = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        assertTrue(impl.mayReturnStaleIfError(req, entry, now));
    }
    
    @Test
    public void testMayReturnStaleIfErrorInRequestIsTrueWithinStaleness(){
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5")
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        HttpRequest req = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","stale-if-error=15");
        assertTrue(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayNotReturnStaleIfErrorInResponseAndAfterResponseWindow(){
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-if-error=1")
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        HttpRequest req = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        assertFalse(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayNotReturnStaleIfErrorInResponseAndAfterRequestWindow(){
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5")
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        HttpRequest req = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","stale-if-error=1");
        assertFalse(impl.mayReturnStaleIfError(req, entry, now));
    }

    @Test
    public void testMayReturnStaleWhileRevalidatingIsFalseWhenDirectiveIsAbsent() {
        Date now = new Date();
        
        Header[] headers = new Header[] { new BasicHeader("Cache-control", "public") };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        
        CacheValidityPolicy impl = new CacheValidityPolicy();
        
        assertFalse(impl.mayReturnStaleWhileRevalidating(entry, now));
    }
    
    @Test
    public void testMayReturnStaleWhileRevalidatingIsTrueWhenWithinStaleness() {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=15")
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        
        CacheValidityPolicy impl = new CacheValidityPolicy();
        
        assertTrue(impl.mayReturnStaleWhileRevalidating(entry, now));
    }
    
    @Test
    public void testMayReturnStaleWhileRevalidatingIsFalseWhenPastStaleness() {
        Date now = new Date();
        Date twentyFiveSecondsAgo = new Date(now.getTime() - 25 * 1000L);
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(twentyFiveSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=15")
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        
        CacheValidityPolicy impl = new CacheValidityPolicy();
        
        assertFalse(impl.mayReturnStaleWhileRevalidating(entry, now));
    }
    
    @Test
    public void testMayReturnStaleWhileRevalidatingIsFalseWhenDirectiveEmpty() {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=5, stale-while-revalidate=")
        };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(now, now, headers);
        
        CacheValidityPolicy impl = new CacheValidityPolicy();
        
        assertFalse(impl.mayReturnStaleWhileRevalidating(entry, now));
    }
}
