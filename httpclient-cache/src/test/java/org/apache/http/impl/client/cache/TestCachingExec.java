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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.same;
import static org.easymock.classextension.EasyMock.createMockBuilder;
import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;
import org.easymock.IExpectationSetters;
import org.easymock.EasyMock;
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

    private CloseableHttpResponse mockBackendResponse;

    private Date requestDate;
    private Date responseDate;

    @Override
    @Before
    public void setUp() {
        super.setUp();

        mockBackendResponse = createNiceMock(CloseableHttpResponse.class);

        requestDate = new Date(System.currentTimeMillis() - 1000);
        responseDate = new Date();
    }

    @Override
    public CachingExec createCachingExecChain(final ClientExecChain mockBackend,
            final HttpCache mockCache, final CacheValidityPolicy mockValidityPolicy,
            final ResponseCachingPolicy mockResponsePolicy,
            final CachedHttpResponseGenerator mockResponseGenerator,
            final CacheableRequestPolicy mockRequestPolicy,
            final CachedResponseSuitabilityChecker mockSuitabilityChecker,
            final ConditionalRequestBuilder mockConditionalRequestBuilder,
            final ResponseProtocolCompliance mockResponseProtocolCompliance,
            final RequestProtocolCompliance mockRequestProtocolCompliance,
            final CacheConfig config, final AsynchronousValidator asyncValidator) {
        return impl = new CachingExec(
                mockBackend,
                mockCache,
                mockValidityPolicy,
                mockResponsePolicy,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance,
                config,
                asyncValidator);
    }

    @Override
    public CachingExec createCachingExecChain(final ClientExecChain backend,
            final HttpCache cache, final CacheConfig config) {
        return impl = new CachingExec(backend, cache, config);
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
        final HttpResponse result = impl.execute(route, request, context);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
    }

    @Test
    public void testCacheMissCausesBackendRequest() throws Exception {
        mockImplMethods(CALL_BACKEND);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(null);
        getVariantCacheEntriesReturns(new HashMap<String,Variant>());

        requestIsFatallyNonCompliant(null);

        implExpectsAnyRequestAndReturn(mockBackendResponse);

        replayMocks();
        final HttpResponse result = impl.execute(route, request, context);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(1, impl.getCacheMisses());
        Assert.assertEquals(0, impl.getCacheHits());
        Assert.assertEquals(0, impl.getCacheUpdates());
    }

    @Test
    public void testUnsuitableUnvalidatableCacheEntryCausesBackendRequest() throws Exception {
        mockImplMethods(CALL_BACKEND);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        requestIsFatallyNonCompliant(null);

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(false);
        expect(mockConditionalRequestBuilder.buildConditionalRequest(request, mockCacheEntry))
            .andReturn(request);
        backendExpectsRequestAndReturn(request, mockBackendResponse);
        expect(mockBackendResponse.getProtocolVersion()).andReturn(HttpVersion.HTTP_1_1);
        expect(mockBackendResponse.getStatusLine()).andReturn(
            new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "Ok"));

        replayMocks();
        final HttpResponse result = impl.execute(route, request, context);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(1, impl.getCacheHits());
        Assert.assertEquals(1, impl.getCacheUpdates());
    }

    @Test
    public void testUnsuitableValidatableCacheEntryCausesRevalidation() throws Exception {
        mockImplMethods(REVALIDATE_CACHE_ENTRY);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        requestIsFatallyNonCompliant(null);

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(true);
        cacheEntryMustRevalidate(false);
        cacheEntryProxyRevalidate(false);
        mayReturnStaleWhileRevalidating(false);

        expect(impl.revalidateCacheEntry(
                isA(HttpRoute.class),
                isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) isNull(),
                isA(HttpCacheEntry.class))).andReturn(mockBackendResponse);

        replayMocks();
        final HttpResponse result = impl.execute(route, request, context);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(1, impl.getCacheHits());
        Assert.assertEquals(0, impl.getCacheUpdates());
    }

    @Test
    public void testRevalidationCallsHandleBackEndResponseWhenNot200Or304() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);

        final HttpRequestWrapper validate = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final CloseableHttpResponse originResponse = Proxies.enhanceResponse(
                new BasicHttpResponse(HttpVersion.HTTP_1_1,  HttpStatus.SC_NOT_FOUND, "Not Found"));
        final CloseableHttpResponse finalResponse = Proxies.enhanceResponse(
                HttpTestUtils.make200Response());

        conditionalRequestBuilderReturns(validate);
        getCurrentDateReturns(requestDate);
        backendExpectsRequestAndReturn(validate, originResponse);
        getCurrentDateReturns(responseDate);
        expect(impl.handleBackendResponse(
                same(validate),
                same(context),
                eq(requestDate),
                eq(responseDate),
                same(originResponse))).andReturn(finalResponse);

        replayMocks();
        final HttpResponse result =
            impl.revalidateCacheEntry(route, request, context, null, entry);
        verifyMocks();

        Assert.assertSame(finalResponse, result);
    }

    @Test
    public void testRevalidationUpdatesCacheEntryAndPutsItToCacheWhen304ReturningCachedResponse()
            throws Exception {

        mockImplMethods(GET_CURRENT_DATE);

        final HttpRequestWrapper validate = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final HttpResponse originResponse = Proxies.enhanceResponse(
            new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_MODIFIED, "Not Modified"));
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
        responseIsGeneratedFromCache();

        replayMocks();
        impl.revalidateCacheEntry(route, request, context, null, entry);
        verifyMocks();
    }

    @Test
    public void testRevalidationRewritesAbsoluteUri() throws Exception {

        mockImplMethods(GET_CURRENT_DATE);

        // Fail on an unexpected request, rather than causing a later NPE
        EasyMock.resetToStrict(mockBackend);

        final HttpRequestWrapper validate = HttpRequestWrapper.wrap(
                new HttpGet("http://foo.example.com/resource"));
        final HttpRequestWrapper relativeValidate = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/resource", HttpVersion.HTTP_1_1));
        final CloseableHttpResponse originResponse = Proxies.enhanceResponse(
            new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "Okay"));

        conditionalRequestBuilderReturns(validate);
        getCurrentDateReturns(requestDate);

        final CloseableHttpResponse resp = mockBackend.execute(EasyMock.isA(HttpRoute.class),
                eqRequest(relativeValidate), EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware> isNull());
        expect(resp).andReturn(originResponse);

        getCurrentDateReturns(responseDate);

        replayMocks();
        impl.revalidateCacheEntry(route, request, context, null, entry);
        verifyMocks();
    }

    @Test
    public void testEndlessResponsesArePassedThrough() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Server", "MockOrigin/1.0");
        resp1.setHeader(HTTP.TRANSFER_ENCODING, HTTP.CHUNK_CODING);

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
        }, -1));

        final CloseableHttpResponse resp = mockBackend.execute(
                EasyMock.isA(HttpRoute.class),
                EasyMock.isA(HttpRequestWrapper.class),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        EasyMock.expect(resp).andReturn(Proxies.enhanceResponse(resp1));

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());

        replayMocks();
        final CloseableHttpResponse resp2 = impl.execute(route, req1, context, null);
        maxlength.set(size.get() * 2);
        verifyMocks();
        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, resp2));
    }

    @Test
    public void testCallBackendMakesBackEndRequestAndHandlesResponse() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        getCurrentDateReturns(requestDate);
        backendExpectsRequestAndReturn(request, resp);
        getCurrentDateReturns(responseDate);
        handleBackendResponseReturnsResponse(request, resp);

        replayMocks();

        impl.callBackend(route, request, context, null);

        verifyMocks();
    }

    private IExpectationSetters<CloseableHttpResponse> implExpectsAnyRequestAndReturn(
            final CloseableHttpResponse response) throws Exception {
        final CloseableHttpResponse resp = impl.callBackend(
                EasyMock.isA(HttpRoute.class),
                EasyMock.isA(HttpRequestWrapper.class),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp).andReturn(response);
    }

    private void getVariantCacheEntriesReturns(final Map<String,Variant> result) throws IOException {
        expect(mockCache.getVariantCacheEntriesWithEtags(host, request)).andReturn(result);
    }

    private void cacheInvalidatorWasCalled()  throws IOException {
        mockCache.flushInvalidatedCacheEntriesFor(
                (HttpHost)anyObject(),
                (HttpRequest)anyObject());
    }

    private void getCurrentDateReturns(final Date date) {
        expect(impl.getCurrentDate()).andReturn(date);
    }

    private void handleBackendResponseReturnsResponse(final HttpRequestWrapper request, final HttpResponse response)
            throws IOException {
        expect(
                impl.handleBackendResponse(
                        same(request),
                        isA(HttpClientContext.class),
                        isA(Date.class),
                        isA(Date.class),
                        isA(CloseableHttpResponse.class))).andReturn(
                                Proxies.enhanceResponse(response));
    }

    private void mockImplMethods(final String... methods) {
        mockedImpl = true;
        impl = createMockBuilder(CachingExec.class).withConstructor(
                mockBackend,
                mockCache,
                mockValidityPolicy,
                mockResponsePolicy,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance,
                config,
                asyncValidator).addMockedMethods(methods).createNiceMock();
    }

}
