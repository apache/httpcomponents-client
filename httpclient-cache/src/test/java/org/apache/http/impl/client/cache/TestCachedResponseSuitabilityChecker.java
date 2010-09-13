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

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCachedResponseSuitabilityChecker {

    private HttpHost host;
    private HttpRequest request;
    private CacheEntry entry;
    private CacheValidityPolicy mockValidityPolicy;
    private CachedResponseSuitabilityChecker impl;

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com");
        request = new BasicHttpRequest("GET", "/foo");
        mockValidityPolicy = EasyMock.createMock(CacheValidityPolicy.class);
        entry = new CacheEntry();

        impl = new CachedResponseSuitabilityChecker(mockValidityPolicy);
    }

    public void replayMocks() {
        EasyMock.replay(mockValidityPolicy);
    }

    public void verifyMocks() {
        EasyMock.verify(mockValidityPolicy);
    }

    @Test
    public void testNotSuitableIfContentLengthHeaderIsWrong() {
        responseIsFresh(true);
        contentLengthMatchesActualLength(false);

        replayMocks();
        boolean result = impl.canCachedResponseBeUsed(host, request, entry);

        verifyMocks();

        Assert.assertFalse(result);
    }

    @Test
    public void testSuitableIfContentLengthHeaderIsRight() {
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);

        replayMocks();
        boolean result = impl.canCachedResponseBeUsed(host, request, entry);

        verifyMocks();

        Assert.assertTrue(result);
    }

    @Test
    public void testSuitableIfCacheEntryIsFresh() {
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);

        verifyMocks();

        Assert.assertTrue(result);
    }

    @Test
    public void testNotSuitableIfCacheEntryIsNotFresh() {
        responseIsFresh(false);
        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);

        verifyMocks();

        Assert.assertFalse(result);
    }

    @Test
    public void testNotSuitableIfRequestHasNoCache() {
        request.addHeader("Cache-Control", "no-cache");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);
        verifyMocks();
        Assert.assertFalse(result);
    }

    @Test
    public void testNotSuitableIfAgeExceedsRequestMaxAge() {
        request.addHeader("Cache-Control", "max-age=10");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        currentAge(20L);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);
        verifyMocks();
        Assert.assertFalse(result);
    }

    @Test
    public void testSuitableIfFreshAndAgeIsUnderRequestMaxAge() {
        request.addHeader("Cache-Control", "max-age=10");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        currentAge(5L);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);
        verifyMocks();
        Assert.assertTrue(result);
    }

    @Test
    public void testSuitableIfFreshAndFreshnessLifetimeGreaterThanRequestMinFresh() {
        request.addHeader("Cache-Control", "min-fresh=10");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        freshnessLifetime(15L);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);
        verifyMocks();
        Assert.assertTrue(result);
    }

    @Test
    public void testNotSuitableIfFreshnessLifetimeLessThanRequestMinFresh() {
        request.addHeader("Cache-Control", "min-fresh=10");
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);
        freshnessLifetime(5L);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);
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

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);
        verifyMocks();
        Assert.assertFalse(result);
    }

    @Test
    public void testMalformedCacheControlMaxAgeRequestHeaderCausesUnsuitableEntry() {
        request.addHeader(new BasicHeader("Cache-Control", "max-age=foo"));
        responseIsFresh(true);
        contentLengthMatchesActualLength(true);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);

        verifyMocks();

        Assert.assertFalse(result);
    }

    @Test
    public void testMalformedCacheControlMinFreshRequestHeaderCausesUnsuitableEntry() {
        request.addHeader(new BasicHeader("Cache-Control", "min-fresh=foo"));

        responseIsFresh(true);
        contentLengthMatchesActualLength(true);

        replayMocks();

        boolean result = impl.canCachedResponseBeUsed(host, request, entry);

        verifyMocks();

        Assert.assertFalse(result);
    }

    private void currentAge(long sec) {
        EasyMock.expect(
                mockValidityPolicy.getCurrentAgeSecs(entry)).andReturn(sec);
    }

    private void freshnessLifetime(long sec) {
        EasyMock.expect(
                mockValidityPolicy.getFreshnessLifetimeSecs(entry)).andReturn(sec);
    }

    private void responseIsFresh(boolean fresh) {
        EasyMock.expect(
                mockValidityPolicy.isResponseFresh(entry)).andReturn(fresh);
    }

    private void contentLengthMatchesActualLength(boolean b) {
        EasyMock.expect(
                mockValidityPolicy.contentLengthHeaderMatchesActualLength(entry)).andReturn(b);
    }
}