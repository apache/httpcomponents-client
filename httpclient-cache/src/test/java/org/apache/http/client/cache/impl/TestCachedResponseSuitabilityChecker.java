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

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.cache.impl.CacheEntry;
import org.apache.http.client.cache.impl.CachedResponseSuitabilityChecker;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCachedResponseSuitabilityChecker {

    private CachedResponseSuitabilityChecker impl;
    private HttpHost host;
    private HttpRequest request;
    private CacheEntry mockEntry;
    private HttpRequest mockRequest;

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com");
        request = new BasicHttpRequest("GET", "/foo");
        mockEntry = EasyMock.createMock(CacheEntry.class);
        mockRequest = EasyMock.createMock(HttpRequest.class);

        impl = new CachedResponseSuitabilityChecker();
    }

    public void replayMocks() {
        EasyMock.replay(mockEntry, mockRequest);
    }

    public void verifyMocks() {
        EasyMock.verify(mockEntry, mockRequest);
    }

    @Test
    public void testNotSuitableIfContentLengthHeaderIsWrong() {
        responseIsFresh(true);
        contentLengthMatchesActualLength(false);

        replayMocks();
        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);

        verifyMocks();

        Assert.assertFalse(result);
    }

    @Test
    public void testSuitableIfContentLengthHeaderIsRight() {
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, request);

        replayMocks();
        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);

        verifyMocks();

        Assert.assertTrue(result);
    }

    @Test
    public void testSuitableIfCacheEntryIsFresh() {
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, request);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);

        verifyMocks();

        Assert.assertTrue(result);
    }

    @Test
    public void testNotSuitableIfCacheEntryIsNotFresh() {
        responseIsFresh(false);
        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);

        verifyMocks();

        Assert.assertFalse(result);
    }

    @Test
    public void testNotSuitableIfRequestHasNoCache() {
        request.addHeader("Cache-Control", "no-cache");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, request);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);
        verifyMocks();
        Assert.assertFalse(result);
    }

    @Test
    public void testNotSuitableIfAgeExceedsRequestMaxAge() {
        request.addHeader("Cache-Control", "max-age=10");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, request);

        org.easymock.EasyMock.expect(mockEntry.getCurrentAgeSecs()).andReturn(20L);
        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);
        verifyMocks();
        Assert.assertFalse(result);
    }

    @Test
    public void testSuitableIfFreshAndAgeIsUnderRequestMaxAge() {
        request.addHeader("Cache-Control", "max-age=10");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, request);

        org.easymock.EasyMock.expect(mockEntry.getCurrentAgeSecs()).andReturn(5L);
        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);
        verifyMocks();
        Assert.assertTrue(result);
    }

    @Test
    public void testSuitableIfFreshAndFreshnessLifetimeGreaterThanRequestMinFresh() {
        request.addHeader("Cache-Control", "min-fresh=10");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, request);

        org.easymock.EasyMock.expect(mockEntry.getFreshnessLifetimeSecs()).andReturn(15L);
        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);
        verifyMocks();
        Assert.assertTrue(result);
    }

    @Test
    public void testNotSuitableIfFreshnessLifetimeLessThanRequestMinFresh() {
        request.addHeader("Cache-Control", "min-fresh=10");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, request);

        org.easymock.EasyMock.expect(mockEntry.getFreshnessLifetimeSecs()).andReturn(5L);
        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);
        verifyMocks();
        Assert.assertFalse(result);
    }

    // this is compliant but possibly misses some cache hits; would
    // need to change logic to add Warning header if we allowed this
    @Test
    public void testNotSuitableEvenIfStaleButPermittedByRequestMaxStale() {
        request.addHeader("Cache-Control", "max-stale=10");
        responseIsFresh(false);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, mockEntry);
        verifyMocks();
        Assert.assertFalse(result);
    }

    @Test
    public void testMalformedCacheControlMaxAgeRequestHeaderCausesUnsuitableEntry() {

        Header[] hdrs = new Header[] { new BasicHeader("Cache-Control", "max-age=foo") };
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, mockRequest);

        org.easymock.EasyMock.expect(mockRequest.getHeaders("Cache-Control")).andReturn(hdrs);
        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, mockRequest, mockEntry);

        verifyMocks();

        Assert.assertFalse(result);
    }

    @Test
    public void testMalformedCacheControlMinFreshRequestHeaderCausesUnsuitableEntry() {

        Header[] hdrs = new Header[] { new BasicHeader("Cache-Control", "min-fresh=foo") };
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        modifiedSince(false, mockRequest);

        org.easymock.EasyMock.expect(mockRequest.getHeaders("Cache-Control")).andReturn(hdrs);
        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, mockRequest, mockEntry);

        verifyMocks();

        Assert.assertFalse(result);
    }

    private void responseIsFresh(boolean fresh) {
        org.easymock.EasyMock.expect(mockEntry.isResponseFresh()).andReturn(fresh);
    }

    private void modifiedSince(boolean modified, HttpRequest request) {
        org.easymock.EasyMock.expect(mockEntry.modifiedSince(request)).andReturn(modified);
    }

    private void contentLengthMatchesActualLength(boolean b) {
        org.easymock.EasyMock.expect(mockEntry.contentLengthHeaderMatchesActualLength()).andReturn(
                b);
    }
}