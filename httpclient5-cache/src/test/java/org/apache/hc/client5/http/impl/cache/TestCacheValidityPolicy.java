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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
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
        assertEquals(CacheSupport.MAX_AGE, impl.getApparentAge(entry));
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
        assertEquals(TimeValue.ofSeconds(10), impl.getCorrectedAgeValue(entry));
    }

    @Test
    public void testGetCorrectedAgeValue() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "6"), };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        assertEquals(TimeValue.ofSeconds(6), impl.getCorrectedAgeValue(entry));
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
            protected TimeValue getCorrectedAgeValue(final HttpCacheEntry ent) {
                return TimeValue.ofSeconds(7);
            }

            @Override
            protected TimeValue getResponseDelay(final HttpCacheEntry ent) {
                return TimeValue.ofSeconds(13);
            }
        };
        assertEquals(TimeValue.ofSeconds(7), impl.getCorrectedInitialAge(entry));
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
        final ResponseCacheControl cacheControl = ResponseCacheControl.builder()
                .setSharedMaxAge(10)
                .setMaxAge(5)
                .build();
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(cacheControl, entry));
    }

    @Test
    public void testSMaxAgeIsIgnoredWhenNotShared() {
        final CacheConfig cacheConfig = CacheConfig.custom()
                .setSharedCache(false)
                .build();
        impl = new CacheValidityPolicy(cacheConfig);
        final ResponseCacheControl cacheControl = ResponseCacheControl.builder()
                .setSharedMaxAge(10)
                .setMaxAge(5)
                .build();
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(TimeValue.ofSeconds(5), impl.getFreshnessLifetime(cacheControl, entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeIfPresent() {
        final ResponseCacheControl cacheControl = ResponseCacheControl.builder()
                .setMaxAge(10)
                .build();
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(cacheControl, entry));
    }

    @Test
    public void testFreshnessLifetimeUsesSharedMaxAgeInSharedCache() {
        // assuming impl represents a shared cache
        final ResponseCacheControl cacheControl = ResponseCacheControl.builder()
                .setMaxAge(10)
                .setSharedMaxAge(20)
                .build();
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(TimeValue.ofSeconds(20), impl.getFreshnessLifetime(cacheControl, entry));
    }

    @Test
    public void testFreshnessLifetimeUsesMaxAgeWhenSharedMaxAgeNotPresent() {
        // assuming impl represents a shared cache
        final ResponseCacheControl cacheControl = ResponseCacheControl.builder()
                .setMaxAge(10)
                .build();
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(cacheControl, entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeEvenIfExpiresIsPresent() {
        final ResponseCacheControl cacheControl = ResponseCacheControl.builder()
                .setMaxAge(10)
                .build();
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo)));
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(cacheControl, entry));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeEvenIfExpiresIsPresent() {
        final ResponseCacheControl cacheControl = ResponseCacheControl.builder()
                .setSharedMaxAge(10)
                .build();
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo)));
        assertEquals(TimeValue.ofSeconds(10), impl.getFreshnessLifetime(cacheControl, entry));
    }

    @Test
    public void testFreshnessLifetimeIsFromExpiresHeaderIfNoMaxAge() {
        final ResponseCacheControl cacheControl = ResponseCacheControl.builder()
                .build();
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo)));
        assertEquals(TimeValue.ofSeconds(4), impl.getFreshnessLifetime(cacheControl, entry));
    }

    @Test
    public void testHeuristicFreshnessLifetime() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(oneSecondAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(elevenSecondsAgo)));
        assertEquals(TimeValue.ofSeconds(1), impl.getHeuristicFreshnessLifetime(entry));
    }

    @Test
    public void testHeuristicFreshnessLifetimeDefaultsProperly() {
        final TimeValue defaultFreshness = TimeValue.ofSeconds(0);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(defaultFreshness, impl.getHeuristicFreshnessLifetime(entry));
    }

    @Test
    public void testHeuristicFreshnessLifetimeIsNonNegative() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(elevenSecondsAgo)),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(oneSecondAgo)));
        assertTrue(TimeValue.isNonNegative(impl.getHeuristicFreshnessLifetime(entry)));
    }

    @Test
    public void testHeuristicFreshnessLifetimeCustomProperly() {
        final CacheConfig cacheConfig = CacheConfig.custom().setHeuristicDefaultLifetime(TimeValue.ofSeconds(10))
                .setHeuristicCoefficient(0.5f).build();
        impl = new CacheValidityPolicy(cacheConfig);
        final TimeValue defaultFreshness = TimeValue.ofSeconds(10);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertEquals(defaultFreshness, impl.getHeuristicFreshnessLifetime(entry));
    }

    @Test
    public void testNegativeAgeHeaderValueReturnsZero() {
        final Header[] headers = new Header[] { new BasicHeader("Age", "-100") };
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        // in seconds
        assertEquals(0, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedAgeHeaderValueReturnsMaxAge() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Age", "asdf"));
        // in seconds
        assertEquals(0, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedAgeHeaderMultipleWellFormedAges() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Age", "123,456,789"));
        // in seconds
        assertEquals(123, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedAgeHeaderMultiplesMalformedAges() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Age", "123 456 789"));
        // in seconds
        assertEquals(0, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedAgeHeaderNegativeAge() {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Age", "-123"));
        // in seconds
        assertEquals(0, impl.getAgeValue(entry));
    }

    @Test
    public void testMalformedAgeHeaderOverflow() {
        final String reallyOldAge = "1" + Long.MAX_VALUE;
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Age", reallyOldAge));
        // Expect the age value to be 0 in case of overflow
        assertEquals(0, impl.getAgeValue(entry));
    }

}
