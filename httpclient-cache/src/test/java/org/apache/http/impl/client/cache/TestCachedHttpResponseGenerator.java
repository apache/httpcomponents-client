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

import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestCachedHttpResponseGenerator {

    private HttpCacheEntry entry;
    private HttpRequestWrapper request;
    private CacheValidityPolicy mockValidityPolicy;
    private CachedHttpResponseGenerator impl;

    @Before
    public void setUp() {
        entry = HttpTestUtils.makeCacheEntry(new HashMap<String, String>());
        request = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        mockValidityPolicy = mock(CacheValidityPolicy.class);
        impl = new CachedHttpResponseGenerator(mockValidityPolicy);
    }

    @Test
    public void testResponseHasContentLength() {
        final byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        final HttpCacheEntry entry1 = HttpTestUtils.makeCacheEntry(buf);

        final HttpResponse response = impl.generateResponse(request, entry1);

        final Header length = response.getFirstHeader("Content-Length");
        Assert.assertNotNull("Content-Length Header is missing", length);

        Assert.assertEquals("Content-Length does not match buffer length", buf.length, Integer
                .parseInt(length.getValue()));
    }

    @Test
    public void testContentLengthIsNotAddedWhenTransferEncodingIsPresent() {

        final Header[] hdrs = new Header[] { new BasicHeader("Transfer-Encoding", "chunked") };
        final byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        final HttpCacheEntry entry1 = HttpTestUtils.makeCacheEntry(hdrs, buf);

        final HttpResponse response = impl.generateResponse(request, entry1);

        final Header length = response.getFirstHeader("Content-Length");

        Assert.assertNull(length);
    }

    @Test
    public void testResponseMatchesCacheEntry() {
        final HttpResponse response = impl.generateResponse(request, entry);

        Assert.assertTrue(response.containsHeader("Content-Length"));

        Assert.assertSame("HTTP", response.getProtocolVersion().getProtocol());
        Assert.assertEquals(1, response.getProtocolVersion().getMajor());
        Assert.assertEquals(1, response.getProtocolVersion().getMinor());
    }

    @Test
    public void testResponseStatusCodeMatchesCacheEntry() {
        final HttpResponse response = impl.generateResponse(request, entry);

        Assert.assertEquals(entry.getStatusCode(), response.getStatusLine().getStatusCode());
    }

    @Test
    public void testAgeHeaderIsPopulatedWithCurrentAgeOfCacheEntryIfNonZero() {
        currentAge(10L);

        final HttpResponse response = impl.generateResponse(request, entry);

        verify(mockValidityPolicy).getCurrentAgeSecs(same(entry), isA(Date.class));

        final Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNotNull(ageHdr);
        Assert.assertEquals(10L, Long.parseLong(ageHdr.getValue()));
    }

    @Test
    public void testAgeHeaderIsNotPopulatedIfCurrentAgeOfCacheEntryIsZero() {
        currentAge(0L);

        final HttpResponse response = impl.generateResponse(request, entry);

        verify(mockValidityPolicy).getCurrentAgeSecs(same(entry), isA(Date.class));

        final Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNull(ageHdr);
    }

    @Test
    public void testAgeHeaderIsPopulatedWithMaxAgeIfCurrentAgeTooBig() {
        currentAge(CacheValidityPolicy.MAX_AGE + 1L);

        final HttpResponse response = impl.generateResponse(request, entry);

        verify(mockValidityPolicy).getCurrentAgeSecs(same(entry), isA(Date.class));

        final Header ageHdr = response.getFirstHeader("Age");
        Assert.assertNotNull(ageHdr);
        Assert.assertEquals(CacheValidityPolicy.MAX_AGE, Long.parseLong(ageHdr.getValue()));
    }

    private void currentAge(final long sec) {
        when(
                mockValidityPolicy.getCurrentAgeSecs(same(entry),
                        isA(Date.class))).thenReturn(sec);
    }

    @Test
    public void testResponseContainsEntityToServeGETRequestIfEntryContainsResource() throws Exception {
        final HttpResponse response = impl.generateResponse(request, entry);

        Assert.assertNotNull(response.getEntity());
    }

    @Test
    public void testResponseDoesNotContainEntityToServeHEADRequestIfEntryContainsResource() throws Exception {
        final HttpRequestWrapper headRequest = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultHEADRequest());
        final HttpResponse response = impl.generateResponse(headRequest, entry);

        Assert.assertNull(response.getEntity());
    }

}
