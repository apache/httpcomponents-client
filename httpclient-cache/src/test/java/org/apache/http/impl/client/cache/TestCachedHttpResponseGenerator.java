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
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

public class TestCachedHttpResponseGenerator {

    @Test
    public void testResponseHasContentLength() {

        Header[] hdrs = new Header[] {};
        byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        CacheEntry entry = new CacheEntry(
                new Date(), new Date(), new ProtocolVersion("HTTP", 1, 1), hdrs,
                new ByteArrayEntity(buf), 200, "OK");

        CachedHttpResponseGenerator gen = new CachedHttpResponseGenerator();
        HttpResponse response = gen.generateResponse(entry);

        Header length = response.getFirstHeader("Content-Length");
        Assert.assertNotNull("Content-Length Header is missing", length);

        Assert.assertEquals("Content-Length does not match buffer length", buf.length, Integer
                .parseInt(length.getValue()));
    }

    @Test
    public void testContentLengthIsNotAddedWhenTransferEncodingIsPresent() {

        Header[] hdrs = new Header[] { new BasicHeader("Transfer-Encoding", "chunked") };
        byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        CacheEntry entry = new CacheEntry(
                new Date(), new Date(), new ProtocolVersion("HTTP", 1, 1), hdrs,
                new ByteArrayEntity(buf), 200, "OK");


        CachedHttpResponseGenerator gen = new CachedHttpResponseGenerator();
        HttpResponse response = gen.generateResponse(entry);

        Header length = response.getFirstHeader("Content-Length");

        Assert.assertNull(length);
    }

    @Test
    public void testResponseMatchesCacheEntry() {
        CacheEntry entry = buildEntry();

        CachedHttpResponseGenerator gen = new CachedHttpResponseGenerator();
        HttpResponse response = gen.generateResponse(entry);

        Assert.assertTrue(response.containsHeader("Content-Length"));

        Assert.assertSame("HTTP", response.getProtocolVersion().getProtocol());
        Assert.assertEquals(1, response.getProtocolVersion().getMajor());
        Assert.assertEquals(1, response.getProtocolVersion().getMinor());
    }

    @Test
    public void testResponseStatusCodeMatchesCacheEntry() {
        CacheEntry entry = buildEntry();

        CachedHttpResponseGenerator gen = new CachedHttpResponseGenerator();
        HttpResponse response = gen.generateResponse(entry);

        Assert.assertEquals(entry.getStatusCode(), response.getStatusLine().getStatusCode());
    }

    @Test
    public void testAgeHeaderIsPopulatedWithCurrentAgeOfCacheEntryIfNonZero() {
        final long currAge = 10L;

        CacheEntry entry = buildEntryWithCurrentAge(currAge);

        CachedHttpResponseGenerator gen = new CachedHttpResponseGenerator();
        HttpResponse response = gen.generateResponse(entry);

        Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNotNull(ageHdr);
        Assert.assertEquals(currAge, Long.parseLong(ageHdr.getValue()));
    }

    @Test
    public void testAgeHeaderIsNotPopulatedIfCurrentAgeOfCacheEntryIsZero() {
        final long currAge = 0L;

        CacheEntry entry = buildEntryWithCurrentAge(currAge);

        CachedHttpResponseGenerator gen = new CachedHttpResponseGenerator();
        HttpResponse response = gen.generateResponse(entry);

        Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNull(ageHdr);
    }

    @Test
    public void testAgeHeaderIsPopulatedWithMaxAgeIfCurrentAgeTooBig() {

        CacheEntry entry = buildEntryWithCurrentAge(CacheEntry.MAX_AGE + 1L);

        CachedHttpResponseGenerator gen = new CachedHttpResponseGenerator();
        HttpResponse response = gen.generateResponse(entry);

        Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNotNull(ageHdr);
        Assert.assertEquals(CacheEntry.MAX_AGE, Long.parseLong(ageHdr.getValue()));
    }

    private CacheEntry buildEntry() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date eightSecondsAgo = new Date(now.getTime() - 8 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Date tenSecondsFromNow = new Date(now.getTime() + 10 * 1000L);
        Header[] hdrs = { new BasicHeader("Date", DateUtils.formatDate(eightSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(tenSecondsFromNow)),
                new BasicHeader("Content-Length", "150") };

        return new CacheEntry(tenSecondsAgo, sixSecondsAgo, new ProtocolVersion("HTTP", 1, 1),
                hdrs, new ByteArrayEntity(new byte[] {}), 200, "OK");
    }


    private CacheEntry buildEntryWithCurrentAge(final long currAge){
                Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        Date eightSecondsAgo = new Date(now.getTime() - 8 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Date tenSecondsFromNow = new Date(now.getTime() + 10 * 1000L);
        Header[] hdrs = { new BasicHeader("Date", DateUtils.formatDate(eightSecondsAgo)),
                new BasicHeader("Expires", DateUtils.formatDate(tenSecondsFromNow)),
                new BasicHeader("Content-Length", "150") };


        return new CacheEntry(tenSecondsAgo, sixSecondsAgo, new ProtocolVersion("HTTP", 1, 1),
                hdrs, new ByteArrayEntity(new byte[] {}), 200, "OK"){

            private static final long serialVersionUID = 1L;

            @Override
            public long getCurrentAgeSecs() {
                return currAge;
            }

        };
    }
}
