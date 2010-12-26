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

import java.util.HashMap;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.easymock.IExpectationSetters;
import org.easymock.classextension.EasyMock;
import org.junit.Before;

public abstract class AbstractProtocolTest {

    protected static final int MAX_BYTES = 1024;
    protected static final int MAX_ENTRIES = 100;
    protected int entityLength = 128;
    protected HttpHost host;
    protected HttpEntity body;
    protected HttpClient mockBackend;
    protected HttpCache mockCache;
    protected HttpRequest request;
    protected HttpResponse originResponse;
    protected CacheConfig params;
    protected CachingHttpClient impl;
    protected HttpCache cache;

    public static HttpRequest eqRequest(HttpRequest in) {
        EasyMock.reportMatcher(new RequestEquivalent(in));
        return null;
    }

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com");

        body = HttpTestUtils.makeBody(entityLength);

        request = new BasicHttpRequest("GET", "/foo", HttpVersion.HTTP_1_1);

        originResponse = HttpTestUtils.make200Response();

        params = new CacheConfig();
        params.setMaxCacheEntries(MAX_ENTRIES);
        params.setMaxObjectSizeBytes(MAX_BYTES);
        cache = new BasicHttpCache(params);
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockCache = EasyMock.createMock(HttpCache.class);
        impl = new CachingHttpClient(mockBackend, cache, params);
    }

    protected void replayMocks() {
        EasyMock.replay(mockBackend);
        EasyMock.replay(mockCache);
    }

    protected void verifyMocks() {
        EasyMock.verify(mockBackend);
        EasyMock.verify(mockCache);
    }

    protected IExpectationSetters<HttpResponse> backendExpectsAnyRequest() throws Exception {
        HttpResponse resp = mockBackend.execute(EasyMock.isA(HttpHost.class), EasyMock
                .isA(HttpRequest.class), (HttpContext) EasyMock.isNull());
        return EasyMock.expect(resp);
    }

    protected void emptyMockCacheExpectsNoPuts() throws Exception {
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockCache = EasyMock.createMock(HttpCache.class);

        impl = new CachingHttpClient(mockBackend, mockCache, params);

        EasyMock.expect(mockCache.getCacheEntry(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class)))
            .andReturn(null).anyTimes();
        EasyMock.expect(mockCache.getVariantCacheEntriesWithEtags(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class)))
            .andReturn(new HashMap<String,Variant>()).anyTimes();

        mockCache.flushCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();

        mockCache.flushCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();

        mockCache.flushInvalidatedCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();
        
        mockCache.flushInvalidatedCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class), EasyMock.isA(HttpResponse.class));
        EasyMock.expectLastCall().anyTimes();
    }

    protected void behaveAsNonSharedCache() {
        params.setSharedCache(false);
        impl = new CachingHttpClient(mockBackend, cache, params);
    }

    public AbstractProtocolTest() {
        super();
    }

}