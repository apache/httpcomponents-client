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
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

public class TestCacheValidityPolicy {

    @Test
    public void testApparentAgeIsMaxIntIfDateHeaderNotPresent() {
        CacheEntry entry = new CacheEntry();
        CacheValidityPolicy impl = new CacheValidityPolicy();
        Assert.assertEquals(2147483648L, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testApparentAgeIsResponseReceivedTimeLessDateHeader() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        Header[] headers = new Header[] { new BasicHeader("Date", DateUtils
                .formatDate(tenSecondsAgo)) };

        CacheEntry entry = new CacheEntry(now, sixSecondsAgo, headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertEquals(4, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testNegativeApparentAgeIsBroughtUpToZero() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        Header[] headers = new Header[] { new BasicHeader("Date", DateUtils
                .formatDate(sixSecondsAgo)) };

        CacheEntry entry  = new CacheEntry(now,tenSecondsAgo,headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();
        Assert.assertEquals(0, impl.getApparentAgeSecs(entry));
    }

    @Test
    public void testCorrectedReceivedAgeIsAgeHeaderIfLarger() {
        Header[] headers = new Header[] { new BasicHeader("Age", "10"), };
        CacheEntry entry = new CacheEntry(headers);

        CacheValidityPolicy impl = new CacheValidityPolicy() {

            @Override
            protected long getApparentAgeSecs(HttpCacheEntry entry) {
                return 6;
            }

        };

        Assert.assertEquals(10, impl.getCorrectedReceivedAgeSecs(entry));
    }

    @Test
    public void testCorrectedReceivedAgeIsApparentAgeIfLarger() {
        Header[] headers = new Header[] { new BasicHeader("Age", "6"), };
        CacheEntry entry = new CacheEntry(headers);

        CacheValidityPolicy impl = new CacheValidityPolicy() {

            @Override
            protected long getApparentAgeSecs(HttpCacheEntry entry) {
                return 10;
            }

        };

        Assert.assertEquals(10, impl.getCorrectedReceivedAgeSecs(entry));
    }

    @Test
    public void testResponseDelayIsDifferenceBetweenResponseAndRequestTimes() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        CacheEntry entry = new CacheEntry(tenSecondsAgo, sixSecondsAgo);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertEquals(4, impl.getResponseDelaySecs(entry));
    }

    @Test
    public void testCorrectedInitialAgeIsCorrectedReceivedAgePlusResponseDelay() {
        CacheEntry entry = new CacheEntry();
        CacheValidityPolicy impl = new CacheValidityPolicy() {

            @Override
            protected long getCorrectedReceivedAgeSecs(HttpCacheEntry entry) {
                return 7;
            }

            @Override
            protected long getResponseDelaySecs(HttpCacheEntry entry) {
                return 13;
            }

        };
        Assert.assertEquals(20, impl.getCorrectedInitialAgeSecs(entry));
    }

    @Test
    public void testResidentTimeSecondsIsTimeSinceResponseTime() {
        final Date now = new Date();
        final Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);

        CacheEntry entry = new CacheEntry(now, sixSecondsAgo);

        CacheValidityPolicy impl = new CacheValidityPolicy() {

            @Override
            protected Date getCurrentDate() {
                return now;
            }

        };

        Assert.assertEquals(6, impl.getResidentTimeSecs(entry));
    }

    @Test
    public void testCurrentAgeIsCorrectedInitialAgePlusResidentTime() {
        CacheEntry entry = new CacheEntry();
        CacheValidityPolicy impl = new CacheValidityPolicy() {

            @Override
            protected long getCorrectedInitialAgeSecs(HttpCacheEntry entry) {
                return 11;
            }

            @Override
            protected long getResidentTimeSecs(HttpCacheEntry entry) {
                return 17;
            }
        };
        Assert.assertEquals(28, impl.getCurrentAgeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeIfPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10") };
        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();
        Assert.assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeIfPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10") };
        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();
        Assert.assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMostRestrictiveOfMaxAgeAndSMaxAge() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Cache-Control", "s-maxage=20") };
        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();
        Assert.assertEquals(10, impl.getFreshnessLifetimeSecs(entry));

        headers = new Header[] { new BasicHeader("Cache-Control", "max-age=20"),
                new BasicHeader("Cache-Control", "s-maxage=10") };
        entry = new CacheEntry(headers);
        Assert.assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeEvenIfExpiresIsPresent() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();
        Assert.assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeEvenIfExpiresIsPresent() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10"),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();
        Assert.assertEquals(10, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testFreshnessLifetimeIsFromExpiresHeaderIfNoMaxAge() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();
        Assert.assertEquals(4, impl.getFreshnessLifetimeSecs(entry));
    }

    @Test
    public void testResponseIsFreshIfFreshnessLifetimeExceedsCurrentAge() {
        CacheEntry entry = new CacheEntry();
        CacheValidityPolicy impl = new CacheValidityPolicy() {

            @Override
            public long getCurrentAgeSecs(HttpCacheEntry entry) {
                return 6;
            }

            @Override
            public long getFreshnessLifetimeSecs(HttpCacheEntry entry) {
                return 10;
            }
        };

        Assert.assertTrue(impl.isResponseFresh(entry));
    }

    @Test
    public void testResponseIsNotFreshIfFreshnessLifetimeEqualsCurrentAge() {
        CacheEntry entry = new CacheEntry();
        CacheValidityPolicy impl = new CacheValidityPolicy() {

            @Override
            public long getCurrentAgeSecs(HttpCacheEntry entry) {
                return 6;
            }

            @Override
            public long getFreshnessLifetimeSecs(HttpCacheEntry entry) {
                return 6;
            }
        };

        Assert.assertFalse(impl.isResponseFresh(entry));
    }

    @Test
    public void testResponseIsNotFreshIfCurrentAgeExceedsFreshnessLifetime() {
        CacheEntry entry = new CacheEntry();
        CacheValidityPolicy impl = new CacheValidityPolicy() {

            @Override
            public long getCurrentAgeSecs(HttpCacheEntry entry) {
                return 10;
            }

            @Override
            public long getFreshnessLifetimeSecs(HttpCacheEntry entry) {
                return 6;
            }
        };

        Assert.assertFalse(impl.isResponseFresh(entry));
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeETag() {

        Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("ETag", "somevalue")};

        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertTrue(impl.isRevalidatable(entry));
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeLastModifiedDate() {

        Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", DateUtils.formatDate(new Date())) };

        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertTrue(impl.isRevalidatable(entry));
    }

    @Test
    public void testCacheEntryIsNotRevalidatableIfNoAppropriateHeaders() {

        Header[] headers =  {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("Cache-Control", "public") };

        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertFalse(impl.isRevalidatable(entry));
    }

    @Test
    public void testMalformedDateHeaderIsIgnored() {

        Header[] headers = new Header[] { new BasicHeader("Date", "asdf") };
        CacheEntry entry = new CacheEntry(headers);

        CacheValidityPolicy impl = new CacheValidityPolicy();
        Date d = impl.getDateValue(entry);

        Assert.assertNull(d);
    }

    @Test
    public void testMalformedContentLengthReturnsNegativeOne() {

        Header[] headers = new Header[] { new BasicHeader("Content-Length", "asdf") };
        CacheEntry entry = new CacheEntry(headers);

        CacheValidityPolicy impl = new CacheValidityPolicy();
        long length = impl.getContentLengthValue(entry);

        Assert.assertEquals(-1, length);
    }

    @Test
    public void testNegativeAgeHeaderValueReturnsMaxAge() {

        Header[] headers = new Header[] { new BasicHeader("Age", "-100") };
        CacheEntry entry = new CacheEntry(headers);

        CacheValidityPolicy impl = new CacheValidityPolicy();
        long length = impl.getAgeValue(entry);

        Assert.assertEquals(CacheEntry.MAX_AGE, length);
    }

    @Test
    public void testMalformedAgeHeaderValueReturnsMaxAge() {

        Header[] headers = new Header[] { new BasicHeader("Age", "asdf") };
        CacheEntry entry = new CacheEntry(headers);

        CacheValidityPolicy impl = new CacheValidityPolicy();
        long length = impl.getAgeValue(entry);

        Assert.assertEquals(CacheEntry.MAX_AGE, length);
    }

    @Test
    public void testMalformedCacheControlMaxAgeHeaderReturnsZero() {

        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=asdf") };
        CacheEntry entry = new CacheEntry(headers);

        CacheValidityPolicy impl = new CacheValidityPolicy();
        long maxage = impl.getMaxAge(entry);

        Assert.assertEquals(0, maxage);
    }

    @Test
    public void testMalformedExpirationDateReturnsNull() {
        Header[] headers = new Header[] { new BasicHeader("Expires", "asdf") };
        CacheEntry entry = new CacheEntry(headers);

        CacheValidityPolicy impl = new CacheValidityPolicy();
        Date expirationDate = impl.getExpirationDate(entry);

        Assert.assertNull(expirationDate);
    }

    @Test
    public void testMustRevalidateIsFalseIfDirectiveNotPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control","public") };
        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertFalse(impl.mustRevalidate(entry));
    }

    @Test
    public void testMustRevalidateIsTrueWhenDirectiveIsPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control","public, must-revalidate") };
        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertTrue(impl.mustRevalidate(entry));
    }

    @Test
    public void testProxyRevalidateIsFalseIfDirectiveNotPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control","public") };
        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertFalse(impl.proxyRevalidate(entry));
    }

    @Test
    public void testProxyRevalidateIsTrueWhenDirectiveIsPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control","public, proxy-revalidate") };
        CacheEntry entry = new CacheEntry(headers);
        CacheValidityPolicy impl = new CacheValidityPolicy();

        Assert.assertTrue(impl.proxyRevalidate(entry));
    }

}
