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
import org.apache.http.HttpResponse;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCachedHttpResponseGenerator {

    private HttpCacheEntry entry;
    private CacheValidityPolicy mockValidityPolicy;
    private CachedHttpResponseGenerator impl;
    private Date now;

    @Before
    public void setUp() {
        now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date eightSecondsAgo = new Date(now.getTime() - 8 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Date tenSecondsFromNow = new Date(now.getTime() + 10 * 1000L);
        Header[] hdrs = { new BasicHeader("Date", DateUtils.formatDate(eightSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(tenSecondsFromNow)),
                new BasicHeader("Content-Length", "150") };

        entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, sixSecondsAgo, hdrs);
        mockValidityPolicy = EasyMock.createMock(CacheValidityPolicy.class);
        impl = new CachedHttpResponseGenerator(mockValidityPolicy);
    }

    public void replayMocks() {
        EasyMock.replay(mockValidityPolicy);
    }

    @Test
    public void testResponseHasContentLength() {
        byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(buf);

        HttpResponse response = impl.generateResponse(entry);

        Header length = response.getFirstHeader("Content-Length");
        Assert.assertNotNull("Content-Length Header is missing", length);

        Assert.assertEquals("Content-Length does not match buffer length", buf.length, Integer
                .parseInt(length.getValue()));
    }

    @Test
    public void testContentLengthIsNotAddedWhenTransferEncodingIsPresent() {

        Header[] hdrs = new Header[] { new BasicHeader("Transfer-Encoding", "chunked") };
        byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(hdrs, buf);

        HttpResponse response = impl.generateResponse(entry);

        Header length = response.getFirstHeader("Content-Length");

        Assert.assertNull(length);
    }

    @Test
    public void testResponseMatchesCacheEntry() {
        HttpResponse response = impl.generateResponse(entry);

        Assert.assertTrue(response.containsHeader("Content-Length"));

        Assert.assertSame("HTTP", response.getProtocolVersion().getProtocol());
        Assert.assertEquals(1, response.getProtocolVersion().getMajor());
        Assert.assertEquals(1, response.getProtocolVersion().getMinor());
    }

    @Test
    public void testResponseStatusCodeMatchesCacheEntry() {
        HttpResponse response = impl.generateResponse(entry);

        Assert.assertEquals(entry.getStatusCode(), response.getStatusLine().getStatusCode());
    }

    @Test
    public void testAgeHeaderIsPopulatedWithCurrentAgeOfCacheEntryIfNonZero() {
        currentAge(10L);
        replayMocks();

        HttpResponse response = impl.generateResponse(entry);

        Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNotNull(ageHdr);
        Assert.assertEquals(10L, Long.parseLong(ageHdr.getValue()));
    }

    @Test
    public void testAgeHeaderIsNotPopulatedIfCurrentAgeOfCacheEntryIsZero() {
        currentAge(0L);
        replayMocks();

        HttpResponse response = impl.generateResponse(entry);

        Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNull(ageHdr);
    }

    @Test
    public void testAgeHeaderIsPopulatedWithMaxAgeIfCurrentAgeTooBig() {
        currentAge(CacheValidityPolicy.MAX_AGE + 1L);
        replayMocks();

        HttpResponse response = impl.generateResponse(entry);

        Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNotNull(ageHdr);
        Assert.assertEquals(CacheValidityPolicy.MAX_AGE, Long.parseLong(ageHdr.getValue()));
    }

    private void currentAge(long sec) {
        EasyMock.expect(
                mockValidityPolicy.getCurrentAgeSecs(EasyMock.same(entry),
                        EasyMock.isA(Date.class))).andReturn(sec);
    }

}
