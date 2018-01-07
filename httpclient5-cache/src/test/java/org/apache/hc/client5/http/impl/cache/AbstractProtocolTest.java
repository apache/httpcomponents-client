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

import java.io.IOException;
import java.util.HashMap;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.impl.classic.ClassicRequestCopier;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;
import org.junit.Before;

public abstract class AbstractProtocolTest {

    protected static final int MAX_BYTES = 1024;
    protected static final int MAX_ENTRIES = 100;
    protected int entityLength = 128;
    protected HttpHost host;
    protected HttpRoute route;
    protected HttpEntity body;
    protected HttpClientContext context;
    protected ExecChain mockExecChain;
    protected ExecRuntime mockExecRuntime;
    protected HttpCache mockCache;
    protected ClassicHttpRequest request;
    protected ClassicHttpResponse originResponse;
    protected CacheConfig config;
    protected ExecChainHandler impl;
    protected HttpCache cache;

    public static ClassicHttpRequest eqRequest(final ClassicHttpRequest in) {
        EasyMock.reportMatcher(new RequestEquivalent(in));
        return null;
    }

    public static HttpResponse eqResponse(final HttpResponse in) {
        EasyMock.reportMatcher(new ResponseEquivalent(in));
        return null;
    }

    public static ClassicHttpResponse eqCloseableResponse(final ClassicHttpResponse in) {
        EasyMock.reportMatcher(new ResponseEquivalent(in));
        return null;
    }

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com", 80);

        route = new HttpRoute(host);

        body = HttpTestUtils.makeBody(entityLength);

        request = new BasicClassicHttpRequest("GET", "/foo");

        context = HttpClientContext.create();

        originResponse = HttpTestUtils.make200Response();

        config = CacheConfig.custom()
            .setMaxCacheEntries(MAX_ENTRIES)
            .setMaxObjectSize(MAX_BYTES)
            .build();

        cache = new BasicHttpCache(config);
        mockExecChain = EasyMock.createNiceMock(ExecChain.class);
        mockExecRuntime = EasyMock.createNiceMock(ExecRuntime.class);
        mockCache = EasyMock.createNiceMock(HttpCache.class);
        impl = createCachingExecChain(cache, config);
    }

    public ClassicHttpResponse execute(final ClassicHttpRequest request) throws IOException, HttpException {
        return impl.execute(ClassicRequestCopier.INSTANCE.copy(request), new ExecChain.Scope(
                "test", route, request, mockExecRuntime, context), mockExecChain);
    }

    protected ExecChainHandler createCachingExecChain(final HttpCache cache, final CacheConfig config) {
        return new CachingExec(cache, null, config);
    }

    protected boolean supportsRangeAndContentRangeHeaders(final ExecChainHandler impl) {
        return impl instanceof CachingExec && ((CachingExec) impl).supportsRangeAndContentRangeHeaders();
    }

    protected void replayMocks() {
        EasyMock.replay(mockExecChain);
        EasyMock.replay(mockCache);
    }

    protected void verifyMocks() {
        EasyMock.verify(mockExecChain);
        EasyMock.verify(mockCache);
    }

    protected IExpectationSetters<ClassicHttpResponse> backendExpectsAnyRequest() throws Exception {
        final ClassicHttpResponse resp = mockExecChain.proceed(
                EasyMock.isA(ClassicHttpRequest.class),
                EasyMock.isA(ExecChain.Scope.class));
        return EasyMock.expect(resp);
    }

    protected IExpectationSetters<ClassicHttpResponse> backendExpectsAnyRequestAndReturn(
            final ClassicHttpResponse response) throws Exception {
        final ClassicHttpResponse resp = mockExecChain.proceed(
                EasyMock.isA(ClassicHttpRequest.class),
                EasyMock.isA(ExecChain.Scope.class));
        return EasyMock.expect(resp).andReturn(response);
    }

    protected void emptyMockCacheExpectsNoPuts() throws Exception {
        mockExecChain = EasyMock.createNiceMock(ExecChain.class);
        mockCache = EasyMock.createNiceMock(HttpCache.class);

        impl = new CachingExec(mockCache, null, config);

        EasyMock.expect(mockCache.getCacheEntry(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class)))
            .andReturn(null).anyTimes();
        EasyMock.expect(mockCache.getVariantCacheEntriesWithEtags(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class)))
            .andReturn(new HashMap<String,Variant>()).anyTimes();

        mockCache.flushCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();

        mockCache.flushCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();

        mockCache.flushCacheEntriesInvalidatedByRequest(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();

        mockCache.flushCacheEntriesInvalidatedByExchange(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class), EasyMock.isA(HttpResponse.class));
        EasyMock.expectLastCall().anyTimes();
    }

    protected void behaveAsNonSharedCache() {
        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setSharedCache(false)
                .build();
        impl = new CachingExec(cache, null, config);
    }

    public AbstractProtocolTest() {
        super();
    }

}
