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
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.message.BasicHttpRequest;
import org.easymock.IExpectationSetters;
import org.easymock.EasyMock;
import org.junit.Before;

public abstract class AbstractProtocolTest {

    protected static final int MAX_BYTES = 1024;
    protected static final int MAX_ENTRIES = 100;
    protected int entityLength = 128;
    protected HttpHost host;
    protected HttpRoute route;
    protected HttpEntity body;
    protected ClientExecChain mockBackend;
    protected HttpCache mockCache;
    protected HttpRequestWrapper request;
    protected HttpCacheContext context;
    protected CloseableHttpResponse originResponse;
    protected CacheConfig config;
    protected ClientExecChain impl;
    protected HttpCache cache;

    public static HttpRequestWrapper eqRequest(final HttpRequestWrapper in) {
        EasyMock.reportMatcher(new RequestEquivalent(in));
        return null;
    }

    public static HttpResponse eqResponse(final HttpResponse in) {
        EasyMock.reportMatcher(new ResponseEquivalent(in));
        return null;
    }

    public static CloseableHttpResponse eqCloseableResponse(final CloseableHttpResponse in) {
        EasyMock.reportMatcher(new ResponseEquivalent(in));
        return null;
    }

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com", 80);

        route = new HttpRoute(host);

        body = HttpTestUtils.makeBody(entityLength);

        request = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "/foo", HttpVersion.HTTP_1_1));

        context = HttpCacheContext.create();
        context.setTargetHost(host);

        originResponse = Proxies.enhanceResponse(HttpTestUtils.make200Response());

        config = CacheConfig.custom()
            .setMaxCacheEntries(MAX_ENTRIES)
            .setMaxObjectSize(MAX_BYTES)
            .build();

        cache = new BasicHttpCache(config);
        mockBackend = EasyMock.createNiceMock(ClientExecChain.class);
        mockCache = EasyMock.createNiceMock(HttpCache.class);
        impl = createCachingExecChain(mockBackend, cache, config);
    }

    protected ClientExecChain createCachingExecChain(final ClientExecChain backend,
            final HttpCache cache, final CacheConfig config) {
        return new CachingExec(backend, cache, config);
    }

    protected boolean supportsRangeAndContentRangeHeaders(final ClientExecChain impl) {
        return impl instanceof CachingExec && ((CachingExec) impl).supportsRangeAndContentRangeHeaders();
    }

    protected void replayMocks() {
        EasyMock.replay(mockBackend);
        EasyMock.replay(mockCache);
    }

    protected void verifyMocks() {
        EasyMock.verify(mockBackend);
        EasyMock.verify(mockCache);
    }

    protected IExpectationSetters<CloseableHttpResponse> backendExpectsAnyRequest() throws Exception {
        final CloseableHttpResponse resp = mockBackend.execute(
                EasyMock.isA(HttpRoute.class),
                EasyMock.isA(HttpRequestWrapper.class),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp);
    }

    protected IExpectationSetters<CloseableHttpResponse> backendExpectsAnyRequestAndReturn(
            final HttpResponse reponse) throws Exception {
        final CloseableHttpResponse resp = mockBackend.execute(
                EasyMock.isA(HttpRoute.class),
                EasyMock.isA(HttpRequestWrapper.class),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp).andReturn(Proxies.enhanceResponse(reponse));
    }

    protected void emptyMockCacheExpectsNoPuts() throws Exception {
        mockBackend = EasyMock.createNiceMock(ClientExecChain.class);
        mockCache = EasyMock.createNiceMock(HttpCache.class);

        impl = new CachingExec(mockBackend, mockCache, config);

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
        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setSharedCache(false)
                .build();
        impl = new CachingExec(mockBackend, cache, config);
    }

    public AbstractProtocolTest() {
        super();
    }

}
