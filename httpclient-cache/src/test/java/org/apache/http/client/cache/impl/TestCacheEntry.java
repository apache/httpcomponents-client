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
package org.apache.http.client.cache.impl;

import static junit.framework.Assert.assertFalse;

import java.util.Date;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.ProtocolVersion;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

public class TestCacheEntry {

    private static ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP",1,1);

    @Test
    public void testGetHeadersReturnsCorrectHeaders() {
        Header[] headers = new Header[] { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"), new BasicHeader("bar", "barValue2") };
        CacheEntry entry = getEntry(headers);
        Assert.assertEquals(2, entry.getHeaders("bar").length);
    }

    private CacheEntry getEntry(Header[] headers) {
        return getEntry(new Date(), new Date(), headers);
    }

    @Test
    public void testGetFirstHeaderReturnsCorrectHeader() {
        Header[] headers = new Header[] { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"), new BasicHeader("bar", "barValue2") };
        CacheEntry entry = getEntry(headers);

        Assert.assertEquals("barValue1", entry.getFirstHeader("bar").getValue());
    }

    @Test
    public void testGetHeadersReturnsEmptyArrayIfNoneMatch() {
        Header[] headers = new Header[] { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"), new BasicHeader("bar", "barValue2") };


        CacheEntry entry = getEntry(headers);

        Assert.assertEquals(0, entry.getHeaders("baz").length);
    }

    @Test
    public void testGetFirstHeaderReturnsNullIfNoneMatch() {
        Header[] headers = new Header[] { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"), new BasicHeader("bar", "barValue2") };
        CacheEntry entry = getEntry(headers);


        Assert.assertEquals(null, entry.getFirstHeader("quux"));
    }

    @Test
    public void testApparentAgeIsMaxIntIfDateHeaderNotPresent() {
        Header[] headers = new Header[0];
        CacheEntry entry = getEntry(headers);
        Assert.assertEquals(2147483648L, entry.getApparentAgeSecs());
    }

    @Test
    public void testApparentAgeIsResponseReceivedTimeLessDateHeader() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        Header[] headers = new Header[] { new BasicHeader("Date", DateUtils
                .formatDate(tenSecondsAgo)) };



        CacheEntry entry = getEntry(now, sixSecondsAgo, headers);

        Assert.assertEquals(4, entry.getApparentAgeSecs());
    }

    private CacheEntry getEntry(Date requestDate, Date responseDate, Header[] headers) {
        return new CacheEntry(requestDate,responseDate,HTTP_1_1,headers,new byte[]{},200,"OK");
    }

    @Test
    public void testNegativeApparentAgeIsBroughtUpToZero() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        Header[] headers = new Header[] { new BasicHeader("Date", DateUtils
                .formatDate(sixSecondsAgo)) };

        CacheEntry entry  = getEntry(now,tenSecondsAgo,headers);
        Assert.assertEquals(0, entry.getApparentAgeSecs());
    }

    @Test
    public void testCorrectedReceivedAgeIsAgeHeaderIfLarger() {
        Header[] headers = new Header[] { new BasicHeader("Age", "10"), };
        CacheEntry entry = new CacheEntry(new Date(),new Date(),HTTP_1_1,headers, new byte[]{},200,"OK") {
            private static final long serialVersionUID = 1L;

            @Override
            protected long getApparentAgeSecs() {
                return 6;
            }
        };


        Assert.assertEquals(10, entry.getCorrectedReceivedAgeSecs());
    }

    @Test
    public void testCorrectedReceivedAgeIsApparentAgeIfLarger() {
        Header[] headers = new Header[] { new BasicHeader("Age", "6"), };
        CacheEntry entry = new CacheEntry(new Date(),new Date(),HTTP_1_1,headers, new byte[]{},200,"OK") {
            private static final long serialVersionUID = 1L;

            @Override
            protected long getApparentAgeSecs() {
                return 10;
            }
        };

        Assert.assertEquals(10, entry.getCorrectedReceivedAgeSecs());
    }

    @Test
    public void testResponseDelayIsDifferenceBetweenResponseAndRequestTimes() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        Header[] headers = new Header[]{};

        CacheEntry entry = new CacheEntry(tenSecondsAgo,sixSecondsAgo,new ProtocolVersion("HTTP",1,1),headers,new byte[]{},200,"OK");


        Assert.assertEquals(4, entry.getResponseDelaySecs());
    }

    @Test
    public void testCorrectedInitialAgeIsCorrectedReceivedAgePlusResponseDelay() {
        CacheEntry entry = new CacheEntry(new Date(),new Date(),HTTP_1_1,new Header[]{}, new byte[]{},200,"OK") {
            private static final long serialVersionUID = 1L;

            @Override
            protected long getCorrectedReceivedAgeSecs() {
                return 7;
            }

            @Override
            protected long getResponseDelaySecs() {
                return 13;
            }
        };
        Assert.assertEquals(20, entry.getCorrectedInitialAgeSecs());
    }

    @Test
    public void testResidentTimeSecondsIsTimeSinceResponseTime() {
        final Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);

        CacheEntry entry = new CacheEntry(new Date(),sixSecondsAgo,HTTP_1_1,new Header[]{}, new byte[]{},200,"OK") {
            private static final long serialVersionUID = 1L;

            @Override
            protected Date getCurrentDate() {
                return now;
            }
        };

        Assert.assertEquals(6, entry.getResidentTimeSecs());
    }

    @Test
    public void testCurrentAgeIsCorrectedInitialAgePlusResidentTime() {
        CacheEntry entry = new CacheEntry(new Date(),new Date(),HTTP_1_1,new Header[]{}, new byte[]{},200,"OK") {
            private static final long serialVersionUID = 1L;

            @Override
            protected long getCorrectedInitialAgeSecs() {
                return 11;
            }

            @Override
            protected long getResidentTimeSecs() {
                return 17;
            }
        };
        Assert.assertEquals(28, entry.getCurrentAgeSecs());
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeIfPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10") };
        CacheEntry entry = getEntry(headers);
        Assert.assertEquals(10, entry.getFreshnessLifetimeSecs());
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeIfPresent() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10") };
        CacheEntry entry = getEntry(headers);
        Assert.assertEquals(10, entry.getFreshnessLifetimeSecs());
    }

    @Test
    public void testFreshnessLifetimeIsMostRestrictiveOfMaxAgeAndSMaxAge() {
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Cache-Control", "s-maxage=20") };
        CacheEntry entry = getEntry(headers);
        Assert.assertEquals(10, entry.getFreshnessLifetimeSecs());

        headers = new Header[] { new BasicHeader("Cache-Control", "max-age=20"),
                new BasicHeader("Cache-Control", "s-maxage=10") };
        entry = getEntry(headers);
        Assert.assertEquals(10, entry.getFreshnessLifetimeSecs());
    }

    @Test
    public void testFreshnessLifetimeIsMaxAgeEvenIfExpiresIsPresent() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=10"),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        CacheEntry entry = getEntry(headers);
        Assert.assertEquals(10, entry.getFreshnessLifetimeSecs());
    }

    @Test
    public void testFreshnessLifetimeIsSMaxAgeEvenIfExpiresIsPresent() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "s-maxage=10"),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        CacheEntry entry = getEntry(headers);
        Assert.assertEquals(10, entry.getFreshnessLifetimeSecs());
    }

    @Test
    public void testFreshnessLifetimeIsFromExpiresHeaderIfNoMaxAge() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(sixSecondsAgo)) };

        CacheEntry entry = getEntry(headers);
        Assert.assertEquals(4, entry.getFreshnessLifetimeSecs());
    }

    @Test
    public void testResponseIsFreshIfFreshnessLifetimeExceedsCurrentAge() {
        CacheEntry entry = new CacheEntry(new Date(),new Date(),HTTP_1_1,new Header[]{}, new byte[]{},200,"OK") {
            private static final long serialVersionUID = 1L;

            @Override
            public long getCurrentAgeSecs() {
                return 6;
            }

            @Override
            public long getFreshnessLifetimeSecs() {
                return 10;
            }
        };

        Assert.assertTrue(entry.isResponseFresh());
    }

    @Test
    public void testResponseIsNotFreshIfFreshnessLifetimeEqualsCurrentAge() {
        CacheEntry entry = new CacheEntry(new Date(),new Date(),HTTP_1_1,new Header[]{}, new byte[]{},200,"OK") {
            private static final long serialVersionUID = 1L;

            @Override
            public long getCurrentAgeSecs() {
                return 6;
            }

            @Override
            public long getFreshnessLifetimeSecs() {
                return 6;
            }
        };

        Assert.assertFalse(entry.isResponseFresh());
    }

    @Test
    public void testResponseIsNotFreshIfCurrentAgeExceedsFreshnessLifetime() {
        CacheEntry entry = new CacheEntry(new Date(),new Date(),HTTP_1_1,new Header[]{}, new byte[]{},200,"OK") {
            private static final long serialVersionUID = 1L;

            @Override
            public long getCurrentAgeSecs() {
                return 10;
            }

            @Override
            public long getFreshnessLifetimeSecs() {
                return 6;
            }
        };

        Assert.assertFalse(entry.isResponseFresh());
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeETag() {

        Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("ETag", "somevalue")};

        CacheEntry entry = getEntry(headers);

        Assert.assertTrue(entry.isRevalidatable());
    }

    @Test
    public void testCacheEntryIsRevalidatableIfHeadersIncludeLastModifiedDate() {

        Header[] headers = {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", DateUtils.formatDate(new Date())) };

        CacheEntry entry = getEntry(headers);
        Assert.assertTrue(entry.isRevalidatable());
    }

    @Test
    public void testCacheEntryIsNotRevalidatableIfNoAppropriateHeaders() {

        Header[] headers =  {
                new BasicHeader("Expires", DateUtils.formatDate(new Date())),
                new BasicHeader("Cache-Control", "public") };

        CacheEntry entry = getEntry(headers);

        assertFalse(entry.isRevalidatable());
    }



    @Test
    public void testCacheEntryWithNoVaryHeaderDoesNotHaveVariants() {
        Header[] headers = new Header[0];

        CacheEntry entry = getEntry(headers);
        Assert.assertFalse(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithOneVaryHeaderHasVariants() {
        Header[] headers = { new BasicHeader("Vary", "User-Agent") };
        CacheEntry entry = getEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithMultipleVaryHeadersHasVariants() {
        Header[] headers = { new BasicHeader("Vary", "User-Agent"),
                new BasicHeader("Vary", "Accept-Encoding") };
        CacheEntry entry = getEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithVaryStarHasVariants(){
        Header[] headers = { new BasicHeader("Vary", "*") };
        CacheEntry entry = getEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryCanStoreMultipleVariantUris() {

        Header[] headers = new Header[]{};
        CacheEntry entry = getEntry(headers);

        CacheEntry addedOne = entry.addVariantURI("foo");
        CacheEntry addedTwo = addedOne.addVariantURI("bar");

        Set<String> variants = addedTwo.getVariantURIs();

        Assert.assertTrue(variants.contains("foo"));
        Assert.assertTrue(variants.contains("bar"));
    }

    @Test
    public void testMalformedDateHeaderIsIgnored() {

        Header[] headers = new Header[] { new BasicHeader("Date", "asdf") };
        CacheEntry entry = getEntry(headers);

        Date d = entry.getDateValue();

        Assert.assertNull(d);
    }

    @Test
    public void testMalformedContentLengthReturnsNegativeOne() {

        Header[] headers = new Header[] { new BasicHeader("Content-Length", "asdf") };
        CacheEntry entry = getEntry(headers);

        long length = entry.getContentLengthValue();

        Assert.assertEquals(-1, length);
    }

    @Test
    public void testNegativeAgeHeaderValueReturnsMaxAge() {

        Header[] headers = new Header[] { new BasicHeader("Age", "-100") };
        CacheEntry entry = getEntry(headers);

        long length = entry.getAgeValue();

        Assert.assertEquals(CacheEntry.MAX_AGE, length);
    }

    @Test
    public void testMalformedAgeHeaderValueReturnsMaxAge() {

        Header[] headers = new Header[] { new BasicHeader("Age", "asdf") };
        CacheEntry entry = getEntry(headers);

        long length = entry.getAgeValue();

        Assert.assertEquals(CacheEntry.MAX_AGE, length);
    }

    @Test
    public void testMalformedCacheControlMaxAgeHeaderReturnsZero() {

        Header[] headers = new Header[] { new BasicHeader("Cache-Control", "max-age=asdf") };
        CacheEntry entry = getEntry(headers);

        long maxage = entry.getMaxAge();

        Assert.assertEquals(0, maxage);
    }

    @Test
    public void testMalformedExpirationDateReturnsNull() {
        Header[] headers = new Header[] { new BasicHeader("Expires", "asdf") };
        CacheEntry entry = getEntry(headers);

        Date expirationDate = entry.getExpirationDate();

        Assert.assertNull(expirationDate);
    }


}
