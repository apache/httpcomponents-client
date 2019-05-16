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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.same;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("boxing") // test code
public class TestCachingExec extends TestCachingExecChain {

    private static final String GET_CURRENT_DATE = "getCurrentDate";

    private static final String HANDLE_BACKEND_RESPONSE = "handleBackendResponse";

    private static final String CALL_BACKEND = "callBackend";

    private static final String REVALIDATE_CACHE_ENTRY = "revalidateCacheEntry";

    private CachingExec impl;
    private boolean mockedImpl;

    private ExecChain.Scope scope;
    private ClassicHttpResponse mockBackendResponse;

    private Date requestDate;
    private Date responseDate;

    @Override
    @Before
    public void setUp() {
        super.setUp();

        scope = new ExecChain.Scope("test", route, request, mockEndpoint, context);
        mockBackendResponse = createNiceMock(ClassicHttpResponse.class);

        requestDate = new Date(System.currentTimeMillis() - 1000);
        responseDate = new Date();
    }

    @Override
    public CachingExec createCachingExecChain(
            final HttpCache mockCache, final CacheValidityPolicy mockValidityPolicy,
            final ResponseCachingPolicy mockResponsePolicy,
            final CachedHttpResponseGenerator mockResponseGenerator,
            final CacheableRequestPolicy mockRequestPolicy,
            final CachedResponseSuitabilityChecker mockSuitabilityChecker,
            final ResponseProtocolCompliance mockResponseProtocolCompliance,
            final RequestProtocolCompliance mockRequestProtocolCompliance,
            final DefaultCacheRevalidator mockCacheRevalidator,
            final ConditionalRequestBuilder<ClassicHttpRequest> mockConditionalRequestBuilder,
            final CacheConfig config) {
        return impl = new CachingExec(
                mockCache,
                mockValidityPolicy,
                mockResponsePolicy,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance,
                mockCacheRevalidator,
                mockConditionalRequestBuilder,
                config);
    }

    @Override
    public CachingExec createCachingExecChain(final HttpCache cache, final CacheConfig config) {
        return impl = new CachingExec(cache, null, config);
    }

    @Override
    protected void replayMocks() {
        super.replayMocks();
        replay(mockBackendResponse);
        if (mockedImpl) {
            replay(impl);
        }
    }

    @Override
    protected void verifyMocks() {
        super.verifyMocks();
        verify(mockBackendResponse);
        if (mockedImpl) {
            verify(impl);
        }
    }


    @Test
    public void testRequestThatCannotBeServedFromCacheCausesBackendRequest() throws Exception {
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(false);
        mockImplMethods(CALL_BACKEND);

        implExpectsAnyRequestAndReturn(mockBackendResponse);
        requestIsFatallyNonCompliant(null);

        replayMocks();
        final HttpResponse result = impl.execute(request, scope, mockExecChain);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
    }

    @Test
    public void testCacheMissCausesBackendRequest() throws Exception {
        mockImplMethods(CALL_BACKEND);
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(null);
        getVariantCacheEntriesReturns(new HashMap<String,Variant>());

        requestIsFatallyNonCompliant(null);

        implExpectsAnyRequestAndReturn(mockBackendResponse);

        replayMocks();
        final HttpResponse result = impl.execute(request, scope, mockExecChain);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(1, impl.getCacheMisses());
        Assert.assertEquals(0, impl.getCacheHits());
        Assert.assertEquals(0, impl.getCacheUpdates());
    }

    @Test
    public void testUnsuitableUnvalidatableCacheEntryCausesBackendRequest() throws Exception {
        mockImplMethods(CALL_BACKEND);
        requestPolicyAllowsCaching(true);
        requestIsFatallyNonCompliant(null);

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(false);
        expect(mockConditionalRequestBuilder.buildConditionalRequest(request, mockCacheEntry))
            .andReturn(request);
        backendExpectsRequestAndReturn(request, mockBackendResponse);
        expect(mockBackendResponse.getVersion()).andReturn(HttpVersion.HTTP_1_1).anyTimes();
        expect(mockBackendResponse.getCode()).andReturn(200);

        replayMocks();
        final HttpResponse result = impl.execute(request, scope, mockExecChain);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(1, impl.getCacheHits());
        Assert.assertEquals(1, impl.getCacheUpdates());
    }

    @Test
    public void testUnsuitableValidatableCacheEntryCausesRevalidation() throws Exception {
        mockImplMethods(REVALIDATE_CACHE_ENTRY);
        requestPolicyAllowsCaching(true);
        requestIsFatallyNonCompliant(null);

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(true);
        cacheEntryMustRevalidate(false);
        cacheEntryProxyRevalidate(false);
        mayReturnStaleWhileRevalidating(false);

        expect(impl.revalidateCacheEntry(
                isA(HttpHost.class),
                isA(ClassicHttpRequest.class),
                isA(ExecChain.Scope.class),
                isA(ExecChain.class),
                isA(HttpCacheEntry.class))).andReturn(mockBackendResponse);

        replayMocks();
        final HttpResponse result = impl.execute(request, scope, mockExecChain);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(1, impl.getCacheHits());
        Assert.assertEquals(0, impl.getCacheUpdates());
    }

    @Test
    public void testRevalidationCallsHandleBackEndResponseWhenNot200Or304() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);

        final ClassicHttpRequest validate = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse originResponse = new BasicClassicHttpResponse(HttpStatus.SC_NOT_FOUND, "Not Found");
        final ClassicHttpResponse finalResponse =  HttpTestUtils.make200Response();

        conditionalRequestBuilderReturns(validate);
        getCurrentDateReturns(requestDate);
        backendExpectsRequestAndReturn(validate, originResponse);
        getCurrentDateReturns(responseDate);
        expect(impl.handleBackendResponse(
                same(host),
                same(validate),
                same(scope),
                eq(requestDate),
                eq(responseDate),
                same(originResponse))).andReturn(finalResponse);

        replayMocks();
        final HttpResponse result =
            impl.revalidateCacheEntry(host, request, scope, mockExecChain, entry);
        verifyMocks();

        Assert.assertSame(finalResponse, result);
    }

    @Test
    public void testRevalidationUpdatesCacheEntryAndPutsItToCacheWhen304ReturningCachedResponse()
            throws Exception {

        mockImplMethods(GET_CURRENT_DATE);

        final ClassicHttpRequest validate = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse originResponse = HttpTestUtils.make304Response();
        final HttpCacheEntry updatedEntry = HttpTestUtils.makeCacheEntry();

        conditionalRequestBuilderReturns(validate);
        getCurrentDateReturns(requestDate);
        backendExpectsRequestAndReturn(validate, originResponse);
        getCurrentDateReturns(responseDate);
        expect(mockCache.updateCacheEntry(
                eq(host),
                same(request),
                same(entry),
                same(originResponse),
                eq(requestDate),
                eq(responseDate)))
            .andReturn(updatedEntry);
        expect(mockSuitabilityChecker.isConditional(request)).andReturn(false);
        responseIsGeneratedFromCache(SimpleHttpResponse.create(HttpStatus.SC_OK));

        replayMocks();
        impl.revalidateCacheEntry(host, request, scope, mockExecChain, entry);
        verifyMocks();
    }

    @Test
    public void testRevalidationRewritesAbsoluteUri() throws Exception {

        mockImplMethods(GET_CURRENT_DATE);

        // Fail on an unexpected request, rather than causing a later NPE
        EasyMock.resetToStrict(mockExecChain);

        final ClassicHttpRequest validate = new HttpGet("http://foo.example.com/resource");
        final ClassicHttpRequest relativeValidate = new BasicClassicHttpRequest("GET", "/resource");
        final ClassicHttpResponse originResponse = new BasicClassicHttpResponse(HttpStatus.SC_OK, "Okay");

        conditionalRequestBuilderReturns(validate);
        getCurrentDateReturns(requestDate);

        final ClassicHttpResponse resp = mockExecChain.proceed(
                eqRequest(relativeValidate), isA(ExecChain.Scope.class));
        expect(resp).andReturn(originResponse);

        getCurrentDateReturns(responseDate);

        replayMocks();
        impl.revalidateCacheEntry(host, request, scope, mockExecChain, entry);
        verifyMocks();
    }

    @Test
    public void testEndlessResponsesArePassedThrough() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Server", "MockOrigin/1.0");
        resp1.setHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING);

        final AtomicInteger size = new AtomicInteger();
        final AtomicInteger maxlength = new AtomicInteger(Integer.MAX_VALUE);
        resp1.setEntity(new InputStreamEntity(new InputStream() {
            private Throwable closed;

            @Override
            public void close() throws IOException {
                closed = new Throwable();
                super.close();
            }

            @Override
            public int read() throws IOException {
                Thread.yield();
                if (closed != null) {
                    throw new IOException("Response has been closed");

                }
                if (size.incrementAndGet() > maxlength.get()) {
                    return -1;
                }
                return 'y';
            }
        }, -1, null));

        final ClassicHttpResponse resp = mockExecChain.proceed(
                isA(ClassicHttpRequest.class), isA(ExecChain.Scope.class));
        EasyMock.expect(resp).andReturn(resp1);

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();

        replayMocks();
        final ClassicHttpResponse resp2 = impl.execute(req1, scope, mockExecChain);
        maxlength.set(size.get() * 2);
        verifyMocks();
        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, resp2));
    }

    @Test
    public void testCallBackendMakesBackEndRequestAndHandlesResponse() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        getCurrentDateReturns(requestDate);
        backendExpectsRequestAndReturn(request, resp);
        getCurrentDateReturns(responseDate);
        handleBackendResponseReturnsResponse(request, resp);

        replayMocks();

        impl.callBackend(host, request, scope, mockExecChain);

        verifyMocks();
    }

    @Test
    public void testDoesNotFlushCachesOnCacheHit() throws Exception {
        requestPolicyAllowsCaching(true);
        requestIsFatallyNonCompliant(null);

        getCacheEntryReturns(mockCacheEntry);
        doesNotFlushCache();
        cacheEntrySuitable(true);
        cacheEntryValidatable(true);

        responseIsGeneratedFromCache(SimpleHttpResponse.create(HttpStatus.SC_OK));

        replayMocks();
        impl.execute(request, scope, mockExecChain);
        verifyMocks();
    }

    private IExpectationSetters<ClassicHttpResponse> implExpectsAnyRequestAndReturn(
            final ClassicHttpResponse response) throws Exception {
        final ClassicHttpResponse resp = impl.callBackend(
                same(host),
                isA(ClassicHttpRequest.class),
                isA(ExecChain.Scope.class),
                isA(ExecChain.class));
        return EasyMock.expect(resp).andReturn(response);
    }

    private void getVariantCacheEntriesReturns(final Map<String,Variant> result) {
        expect(mockCache.getVariantCacheEntriesWithEtags(host, request)).andReturn(result);
    }

    private void cacheInvalidatorWasCalled() {
        mockCache.flushCacheEntriesInvalidatedByRequest((HttpHost)anyObject(), (HttpRequest)anyObject());
    }

    private void getCurrentDateReturns(final Date date) {
        expect(impl.getCurrentDate()).andReturn(date);
    }

    private void handleBackendResponseReturnsResponse(final ClassicHttpRequest request, final ClassicHttpResponse response)
            throws IOException {
        expect(
                impl.handleBackendResponse(
                        same(host),
                        same(request),
                        same(scope),
                        isA(Date.class),
                        isA(Date.class),
                        isA(ClassicHttpResponse.class))).andReturn(response);
    }

    private void mockImplMethods(final String... methods) {
        mockedImpl = true;
        impl = createMockBuilder(CachingExec.class).withConstructor(
                mockCache,
                mockValidityPolicy,
                mockResponsePolicy,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance,
                mockCacheRevalidator,
                mockConditionalRequestBuilder,
                config).addMockedMethods(methods).createNiceMock();
    }

}
