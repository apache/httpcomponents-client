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
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.same;
import static org.easymock.EasyMock.eq;
import static org.easymock.classextension.EasyMock.createMockBuilder;
import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.execchain.ClientExecChain;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.easymock.Capture;
import org.easymock.IExpectationSetters;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCachingExec {

    private static final String GET_CURRENT_DATE = "getCurrentDate";

    private static final String HANDLE_BACKEND_RESPONSE = "handleBackendResponse";

    private static final String CALL_BACKEND = "callBackend";

    private static final String REVALIDATE_CACHE_ENTRY = "revalidateCacheEntry";

    private CachingExec impl;
    private boolean mockedImpl;

    private CacheValidityPolicy mockValidityPolicy;
    private CacheableRequestPolicy mockRequestPolicy;
    private ClientExecChain mockBackend;
    private HttpCache mockCache;
    private HttpCacheStorage mockStorage;
    private CachedResponseSuitabilityChecker mockSuitabilityChecker;
    private ResponseCachingPolicy mockResponsePolicy;
    private CloseableHttpResponse mockBackendResponse;
    private HttpCacheEntry mockCacheEntry;
    private CachedHttpResponseGenerator mockResponseGenerator;
    private ResponseHandler<Object> mockHandler;
    private HttpUriRequest mockUriRequest;
    private CloseableHttpResponse mockCachedResponse;
    private ConditionalRequestBuilder mockConditionalRequestBuilder;
    private HttpRequest mockConditionalRequest;
    private StatusLine mockStatusLine;
    private ResponseProtocolCompliance mockResponseProtocolCompliance;
    private RequestProtocolCompliance mockRequestProtocolCompliance;
    private CacheConfig config;

    private Date requestDate;
    private Date responseDate;
    private HttpRoute route;
    private HttpHost host;
    private HttpRequestWrapper request;
    private HttpCacheContext context;
    private HttpCacheEntry entry;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        mockRequestPolicy = createNiceMock(CacheableRequestPolicy.class);
        mockValidityPolicy = createNiceMock(CacheValidityPolicy.class);
        mockBackend = createNiceMock(ClientExecChain.class);
        mockCache = createNiceMock(HttpCache.class);
        mockSuitabilityChecker = createNiceMock(CachedResponseSuitabilityChecker.class);
        mockResponsePolicy = createNiceMock(ResponseCachingPolicy.class);
        mockHandler = createNiceMock(ResponseHandler.class);
        mockBackendResponse = createNiceMock(CloseableHttpResponse.class);
        mockUriRequest = createNiceMock(HttpUriRequest.class);
        mockCacheEntry = createNiceMock(HttpCacheEntry.class);
        mockResponseGenerator = createNiceMock(CachedHttpResponseGenerator.class);
        mockCachedResponse = createNiceMock(CloseableHttpResponse.class);
        mockConditionalRequestBuilder = createNiceMock(ConditionalRequestBuilder.class);
        mockConditionalRequest = createNiceMock(HttpRequest.class);
        mockStatusLine = createNiceMock(StatusLine.class);
        mockResponseProtocolCompliance = createNiceMock(ResponseProtocolCompliance.class);
        mockRequestProtocolCompliance = createNiceMock(RequestProtocolCompliance.class);
        mockStorage = createNiceMock(HttpCacheStorage.class);
        config = CacheConfig.DEFAULT;

        requestDate = new Date(System.currentTimeMillis() - 1000);
        responseDate = new Date();
        host = new HttpHost("foo.example.com");
        route = new HttpRoute(host);
        request = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/stuff", HttpVersion.HTTP_1_1));
        context = HttpCacheContext.create();
        entry = HttpTestUtils.makeCacheEntry();
        impl = new CachingExec(
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
                config);
    }

    private void replayMocks() {
        replay(mockRequestPolicy);
        replay(mockValidityPolicy);
        replay(mockSuitabilityChecker);
        replay(mockResponsePolicy);
        replay(mockCacheEntry);
        replay(mockResponseGenerator);
        replay(mockBackend);
        replay(mockCache);
        replay(mockHandler);
        replay(mockBackendResponse);
        replay(mockUriRequest);
        replay(mockCachedResponse);
        replay(mockConditionalRequestBuilder);
        replay(mockConditionalRequest);
        replay(mockStatusLine);
        replay(mockResponseProtocolCompliance);
        replay(mockRequestProtocolCompliance);
        replay(mockStorage);
        if (mockedImpl) {
            replay(impl);
        }
    }

    private void verifyMocks() {
        verify(mockRequestPolicy);
        verify(mockValidityPolicy);
        verify(mockSuitabilityChecker);
        verify(mockResponsePolicy);
        verify(mockCacheEntry);
        verify(mockResponseGenerator);
        verify(mockBackend);
        verify(mockCache);
        verify(mockHandler);
        verify(mockBackendResponse);
        verify(mockUriRequest);
        verify(mockCachedResponse);
        verify(mockConditionalRequestBuilder);
        verify(mockConditionalRequest);
        verify(mockStatusLine);
        verify(mockResponseProtocolCompliance);
        verify(mockRequestProtocolCompliance);
        verify(mockStorage);
        if (mockedImpl) {
            verify(impl);
        }
    }

    @Test
    public void testCacheableResponsesGoIntoCache() throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());

        replayMocks();
        impl.execute(route, req1);
        impl.execute(route, req2);
        verifyMocks();
    }

    @Test
    public void testOlderCacheableResponsesDoNotGoIntoCache() throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        Date now = new Date();
        Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);

        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"new-etag\"");

        backendExpectsAnyRequestAndReturn(resp1);

        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        req2.setHeader("Cache-Control","no-cache");
        HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"old-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp2.setHeader("Cache-Control","max-age=3600");

        backendExpectsAnyRequestAndReturn(resp2);

        HttpRequestWrapper req3 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());

        replayMocks();
        impl.execute(route, req1);
        impl.execute(route, req2);
        HttpResponse result = impl.execute(route, req3);
        verifyMocks();

        assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }

    @Test
    public void testNewerCacheableResponsesReplaceExistingCacheEntry() throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        Date now = new Date();
        Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);

        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"old-etag\"");

        backendExpectsAnyRequestAndReturn(resp1);

        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        req2.setHeader("Cache-Control","max-age=0");
        HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"new-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control","max-age=3600");

        backendExpectsAnyRequestAndReturn(resp2);

        HttpRequestWrapper req3 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());

        replayMocks();
        impl.execute(route, req1);
        impl.execute(route, req2);
        HttpResponse result = impl.execute(route, req3);
        verifyMocks();

        assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }


    @Test
    public void testRequestThatCannotBeServedFromCacheCausesBackendRequest() throws Exception {
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(false);
        mockImplMethods(CALL_BACKEND);

        implExpectsAnyRequestAndReturn(mockBackendResponse);
        requestIsFatallyNonCompliant(null);

        replayMocks();
        HttpResponse result = impl.execute(route, request, context);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
    }

    private void requestIsFatallyNonCompliant(RequestProtocolError error) {
        List<RequestProtocolError> errors = new ArrayList<RequestProtocolError>();
        if (error != null) {
            errors.add(error);
        }
        expect(
                mockRequestProtocolCompliance.requestIsFatallyNonCompliant(request)).andReturn(
                errors);
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
        HttpResponse result = impl.execute(route, request, context);
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
        implExpectsAnyRequestAndReturn(mockBackendResponse);

        replayMocks();
        HttpResponse result = impl.execute(route, request, context);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(1, impl.getCacheHits());
        Assert.assertEquals(0, impl.getCacheUpdates());
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
        HttpResponse result = impl.execute(route, request, context);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(1, impl.getCacheHits());
        Assert.assertEquals(0, impl.getCacheUpdates());
    }

    @Test
    public void testRevalidationCallsHandleBackEndResponseWhenNot200Or304() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);

        HttpRequestWrapper validate = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        CloseableHttpResponse originResponse = Proxies.enhanceResponse(
                new BasicHttpResponse(HttpVersion.HTTP_1_1,  HttpStatus.SC_NOT_FOUND, "Not Found"));
        CloseableHttpResponse finalResponse = Proxies.enhanceResponse(
                HttpTestUtils.make200Response());

        conditionalRequestBuilderReturns(validate);
        getCurrentDateReturns(requestDate);
        backendExpectsRequestAndReturn(validate, originResponse);
        getCurrentDateReturns(responseDate);
        expect(impl.handleBackendResponse(
                eq(route),
                same(validate),
                same(context),
                (HttpExecutionAware) isNull(),
                eq(requestDate),
                eq(responseDate),
                same(originResponse))).andReturn(finalResponse);

        replayMocks();
        HttpResponse result =
            impl.revalidateCacheEntry(route, request, context, null, entry);
        verifyMocks();

        Assert.assertSame(finalResponse, result);
    }

    @Test
    public void testRevalidationUpdatesCacheEntryAndPutsItToCacheWhen304ReturningCachedResponse()
            throws Exception {

        mockImplMethods(GET_CURRENT_DATE);

        HttpRequestWrapper validate = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        HttpResponse originResponse = Proxies.enhanceResponse(
            new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_MODIFIED, "Not Modified"));
        HttpCacheEntry updatedEntry = HttpTestUtils.makeCacheEntry();

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
    public void testSuitableCacheEntryDoesNotCauseBackendRequest() throws Exception {
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(true);
        responseIsGeneratedFromCache();
        requestIsFatallyNonCompliant(null);
        entryHasStaleness(0L);

        replayMocks();
        HttpResponse result = impl.execute(route, request, context);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, result);
    }

    @Test
    public void testCallBackendMakesBackEndRequestAndHandlesResponse() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        getCurrentDateReturns(requestDate);
        backendExpectsRequestAndReturn(request, resp);
        getCurrentDateReturns(responseDate);
        handleBackendResponseReturnsResponse(request, resp);

        replayMocks();

        impl.callBackend(route, request, context, null);

        verifyMocks();
    }

    @Test
    public void testNonCacheableResponseIsNotCachedAndIsReturnedAsIs() throws Exception {
        CacheConfig config = CacheConfig.DEFAULT;
        impl = new CachingExec(mockBackend,
                new BasicHttpCache(new HeapResourceFactory(), mockStorage, config),
                config);

        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","no-cache");

        expect(mockStorage.getEntry(isA(String.class))).andReturn(null).anyTimes();
        mockStorage.removeEntry(isA(String.class));
        expectLastCall().anyTimes();
        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        HttpResponse result = impl.execute(route, req1);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, result));
    }

    @Test
    public void testResponseIsGeneratedWhenCacheEntryIsUsable() throws Exception {

        requestIsFatallyNonCompliant(null);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        cacheEntrySuitable(true);
        getCacheEntryReturns(mockCacheEntry);
        responseIsGeneratedFromCache();
        entryHasStaleness(0L);

        replayMocks();
        impl.execute(route, request, context);
        verifyMocks();
    }

    @Test
    public void testNonCompliantRequestWrapsAndReThrowsProtocolException() throws Exception {

        ClientProtocolException expected = new ClientProtocolException("ouch");

        requestIsFatallyNonCompliant(null);
        mockRequestProtocolCompliance.makeRequestCompliant((HttpRequestWrapper)anyObject());
        expectLastCall().andThrow(expected);

        boolean gotException = false;
        replayMocks();
        try {
            impl.execute(route, request, context);
        } catch (ClientProtocolException ex) {
            Assert.assertSame(expected, ex);
            gotException = true;
        }
        verifyMocks();
        Assert.assertTrue(gotException);
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForCacheOptionsResponse()
        throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req = HttpRequestWrapper.wrap(new BasicHttpRequest("OPTIONS","*",HttpVersion.HTTP_1_1));
        req.setHeader("Max-Forwards","0");

        impl.execute(route, req, context);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
                context.getCacheResponseStatus());
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForFatallyNoncompliantRequest()
        throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        req.setHeader("Range","bytes=0-50");
        req.setHeader("If-Range","W/\"weak-etag\"");

        impl.execute(route, req, context);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
                context.getCacheResponseStatus());
    }

    @Test
    public void testRecordsClientProtocolInViaHeaderIfRequestNotServableFromCache()
        throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0));
        req.setHeader("Cache-Control","no-cache");
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");
        Capture<HttpRequestWrapper> cap = new Capture<HttpRequestWrapper>();

        backendCaptureRequestAndReturn(cap, resp);

        replayMocks();
        impl.execute(route, req, context);
        verifyMocks();

        HttpRequest captured = cap.getValue();
        String via = captured.getFirstHeader("Via").getValue();
        String proto = via.split("\\s+")[0];
        Assert.assertTrue("http/1.0".equalsIgnoreCase(proto) ||
                "1.0".equalsIgnoreCase(proto));
    }

    @Test
    public void testSetsCacheMissContextIfRequestNotServableFromCache()
        throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        req.setHeader("Cache-Control","no-cache");
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");

        backendExpectsAnyRequestAndReturn(resp);

        replayMocks();
        impl.execute(route, req, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_MISS,
                context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestNotServableFromCache()
            throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        req.setHeader("Cache-Control","no-cache");
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");

        backendExpectsAnyRequestAndReturn(resp);

        replayMocks();
        HttpResponse result = impl.execute(route, req);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsViaHeaderOnResponseForCacheMiss()
        throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control","public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        HttpResponse result = impl.execute(route, req1);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsCacheHitContextIfRequestServedFromCache()
        throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control","public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1);
        impl.execute(route, req2, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT,
                context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestServedFromCache()
        throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control","public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfModifiedSinceHeaderIfRequestServedFromCache()
            throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsLess() throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        // The variant has been modified since this date
        req2.addHeader("If-Modified-Since", DateUtils
                        .formatDate(tenSecondsAgo));

        HttpResponse resp2 = HttpTestUtils.make200Response();

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsInvalid()
            throws Exception {
        Date now = new Date();
        Date tenSecondsAfter = new Date(now.getTime() + 10 * 1000L);
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        // invalid date (date in the future)
        req2.addHeader("If-Modified-Since", DateUtils
                .formatDate(tenSecondsAfter));

        backendExpectsAnyRequestAndReturn(resp1).times(2);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderIfRequestServedFromCache()
            throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        req2.addHeader("If-None-Match", "*");
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFails() throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        req2.addHeader("If-None-Match", "\"abc\"");

        HttpResponse resp2 = HttpTestUtils.make200Response();

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderAndIfModifiedSinceIfRequestServedFromCache()
            throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        req2.addHeader("If-None-Match", "*");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFailsIfModifiedSinceIgnored()
            throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        req2.addHeader("If-None-Match", "\"abc\"");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testSetsValidatedContextIfRequestWasSuccessfullyValidated()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public, max-age=5");

        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length","128");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control","public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1);
        impl.execute(route, req2, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.VALIDATED,
                context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderIfRequestWasSuccessfullyValidated()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public, max-age=5");

        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length","128");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control","public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2, context);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }


    @Test
    public void testSetsModuleResponseContextIfValidationRequiredButFailed()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public, max-age=5, must-revalidate");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        impl.execute(route, req1);
        impl.execute(route, req2, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
                context.getCacheResponseStatus());
    }

    @Test
    public void testSetsModuleResponseContextIfValidationFailsButNotRequired()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        impl.execute(route, req1);
        impl.execute(route, req2, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT,
                context.getCacheResponseStatus());
    }

    @Test
    public void testSetViaHeaderIfValidationFailsButNotRequired()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2, context);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfNoneMatchPassesIfRequestServedFromOrigin()
            throws Exception {

        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-None-Match", "\"etag\"");
        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns200ForIfNoneMatchFailsIfRequestServedFromOrigin()
            throws Exception {

        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-None-Match", "\"etag\"");
        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"newetag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns304ForIfModifiedSincePassesIfRequestServedFromOrigin()
            throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));
        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns200ForIfModifiedSinceFailsIfRequestServedFromOrigin()
            throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));
        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com/"));

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));
        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"newetag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1);
        HttpResponse result = impl.execute(route, req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testVariantMissServerIfReturns304CacheReturns200() throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        Date now = new Date();

        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req1.addHeader("Accept-Encoding", "gzip");

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Etag", "\"gzip_etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req2.addHeader("Accept-Encoding", "deflate");

        HttpRequestWrapper req2Server = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req2Server.addHeader("Accept-Encoding", "deflate");
        req2Server.addHeader("If-None-Match", "\"gzip_etag\"");

        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("Etag", "\"deflate_etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Cache-Control", "public, max-age=3600");

        HttpRequestWrapper req3 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req3.addHeader("Accept-Encoding", "gzip,deflate");

        HttpRequestWrapper req3Server = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req3Server.addHeader("Accept-Encoding", "gzip,deflate");
        req3Server.addHeader("If-None-Match", "\"gzip_etag\",\"deflate_etag\"");

        HttpResponse resp3 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp3.setEntity(HttpTestUtils.makeBody(128));
        resp3.setHeader("Content-Length", "128");
        resp3.setHeader("Etag", "\"gzip_etag\"");
        resp3.setHeader("Date", DateUtils.formatDate(now));
        resp3.setHeader("Vary", "Accept-Encoding");
        resp3.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        backendExpectsAnyRequestAndReturn(resp3);

        replayMocks();
        HttpResponse result1 = impl.execute(route, req1);

        HttpResponse result2 = impl.execute(route, req2);

        HttpResponse result3 = impl.execute(route, req3);

        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result1.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result3.getStatusLine().getStatusCode());
    }

    @Test
    public void testVariantsMissServerReturns304CacheReturns304() throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        Date now = new Date();

        HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req1.addHeader("Accept-Encoding", "gzip");

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Etag", "\"gzip_etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req2.addHeader("Accept-Encoding", "deflate");

        HttpRequestWrapper req2Server = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req2Server.addHeader("Accept-Encoding", "deflate");
        req2Server.addHeader("If-None-Match", "\"gzip_etag\"");

        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("Etag", "\"deflate_etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Cache-Control", "public, max-age=3600");

        HttpRequestWrapper req4 = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req4.addHeader("Accept-Encoding", "gzip,identity");
        req4.addHeader("If-None-Match", "\"gzip_etag\"");

        HttpRequestWrapper req4Server = HttpRequestWrapper.wrap(new HttpGet("http://foo.example.com"));
        req4Server.addHeader("Accept-Encoding", "gzip,identity");
        req4Server.addHeader("If-None-Match", "\"gzip_etag\"");

        HttpResponse resp4 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp4.setHeader("Etag", "\"gzip_etag\"");
        resp4.setHeader("Date", DateUtils.formatDate(now));
        resp4.setHeader("Vary", "Accept-Encoding");
        resp4.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        backendExpectsAnyRequestAndReturn(resp4);

        replayMocks();
        HttpResponse result1 = impl.execute(route, req1);

        HttpResponse result2 = impl.execute(route, req2);

        HttpResponse result4 = impl.execute(route, req4);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result1.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result4.getStatusLine().getStatusCode());

    }

    @Test
    public void testIsSharedCache() {
        Assert.assertTrue(config.isSharedCache());
    }

    @Test
    public void testTreatsCacheIOExceptionsAsCacheMiss()
        throws Exception {

        impl = new CachingExec(mockBackend, mockCache, CacheConfig.DEFAULT);
        CloseableHttpResponse resp = Proxies.enhanceResponse(HttpTestUtils.make200Response());

        mockCache.flushInvalidatedCacheEntriesFor(host, request);
        expectLastCall().andThrow(new IOException()).anyTimes();
        mockCache.flushInvalidatedCacheEntriesFor(isA(HttpHost.class), isA(HttpRequest.class), isA(HttpResponse.class));
        expectLastCall().anyTimes();
        expect(mockCache.getCacheEntry(same(host),
                isA(HttpRequest.class)))
            .andThrow(new IOException()).anyTimes();
        expect(mockCache.getVariantCacheEntriesWithEtags(same(host),
                isA(HttpRequest.class)))
            .andThrow(new IOException()).anyTimes();
        expect(mockCache.cacheAndReturnResponse(same(host),
                isA(HttpRequest.class), isA(HttpResponse.class),
                isA(Date.class), isA(Date.class)))
            .andThrow(new IOException()).anyTimes();
        expect(mockBackend.execute(
                same(route),
                isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) isNull())).andReturn(resp);

        replayMocks();
        HttpResponse result = impl.execute(route, request);
        verifyMocks();
        Assert.assertSame(resp, result);
    }

    @Test
    public void testIfOnlyIfCachedAndNoCacheEntryBackendNotCalled() throws Exception {
        impl = new CachingExec(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        request.addHeader("Cache-Control", "only-if-cached");

        HttpResponse resp = impl.execute(route, request);

        Assert.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, resp.getStatusLine().getStatusCode());
    }

    @Test
    public void testIfOnlyIfCachedAndEntryNotSuitableBackendNotCalled() throws Exception {

        request.setHeader("Cache-Control", "only-if-cached");

        entry = HttpTestUtils.makeCacheEntry(new Header[]{new BasicHeader("Cache-Control", "must-revalidate")});

        requestIsFatallyNonCompliant(null);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(entry);
        cacheEntrySuitable(false);

        replayMocks();
        HttpResponse resp = impl.execute(route, request);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, resp.getStatusLine().getStatusCode());
    }

    @Test
    public void testIfOnlyIfCachedAndEntryExistsAndIsSuitableReturnsEntry() throws Exception {

        request.setHeader("Cache-Control", "only-if-cached");

        requestIsFatallyNonCompliant(null);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(entry);
        cacheEntrySuitable(true);
        responseIsGeneratedFromCache();
        entryHasStaleness(0);

        replayMocks();
        HttpResponse resp = impl.execute(route, request);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, resp);
    }

    @Test
    public void testDoesNotSetConnectionInContextOnCacheHit() throws Exception {
        DummyBackend backend = new DummyBackend();
        HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = new CachingExec(backend);
        HttpClientContext ctx = HttpClientContext.create();
        impl.execute(route, request);
        impl.execute(route, request, ctx);
        assertNull(ctx.getConnection());
    }

    @Test
    public void testSetsTargetHostInContextOnCacheHit() throws Exception {
        DummyBackend backend = new DummyBackend();
        HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = new CachingExec(backend);
        HttpClientContext ctx = HttpClientContext.create();
        impl.execute(route, request);
        impl.execute(route, request, ctx);
        assertSame(host, ctx.getTargetHost());
    }

    @Test
    public void testSetsRouteInContextOnCacheHit() throws Exception {
        DummyBackend backend = new DummyBackend();
        HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = new CachingExec(backend);
        HttpClientContext ctx = HttpClientContext.create();
        impl.execute(route, request);
        impl.execute(route, request, ctx);
        assertSame(route, ctx.getHttpRoute());
    }

    @Test
    public void testSetsRequestInContextOnCacheHit() throws Exception {
        DummyBackend backend = new DummyBackend();
        HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = new CachingExec(backend);
        HttpClientContext ctx = HttpClientContext.create();
        impl.execute(route, request);
        impl.execute(route, request, ctx);
        assertSame(request, ctx.getRequest());
    }

    @Test
    public void testSetsResponseInContextOnCacheHit() throws Exception {
        DummyBackend backend = new DummyBackend();
        HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = new CachingExec(backend);
        HttpClientContext ctx = HttpClientContext.create();
        impl.execute(route, request);
        HttpResponse result = impl.execute(route, request, ctx);
        assertSame(result, ctx.getResponse());
    }

    @Test
    public void testSetsRequestSentInContextOnCacheHit() throws Exception {
        DummyBackend backend = new DummyBackend();
        HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = new CachingExec(backend);
        HttpClientContext ctx = HttpClientContext.create();
        impl.execute(route, request);
        impl.execute(route, request, ctx);
        assertTrue(ctx.isRequestSent());
    }

    @Test
    public void testCanCacheAResponseWithoutABody() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");
        response.setHeader("Date", DateUtils.formatDate(new Date()));
        response.setHeader("Cache-Control","max-age=300");
        DummyBackend backend = new DummyBackend();
        backend.setResponse(response);
        impl = new CachingExec(backend);
        impl.execute(route, request);
        impl.execute(route, request);
        assertEquals(1, backend.getExecutions());
    }

    private IExpectationSetters<CloseableHttpResponse> backendExpectsAnyRequestAndReturn(
            HttpResponse response) throws Exception {
        CloseableHttpResponse resp = mockBackend.execute(
                EasyMock.isA(HttpRoute.class),
                EasyMock.isA(HttpRequestWrapper.class),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp).andReturn(Proxies.enhanceResponse(response));
    }

    private IExpectationSetters<CloseableHttpResponse> implExpectsAnyRequestAndReturn(
            CloseableHttpResponse response) throws Exception {
        CloseableHttpResponse resp = impl.callBackend(
                EasyMock.isA(HttpRoute.class),
                EasyMock.isA(HttpRequestWrapper.class),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp).andReturn(response);
    }

    private IExpectationSetters<CloseableHttpResponse> backendExpectsRequestAndReturn(
            HttpRequestWrapper request, HttpResponse response) throws Exception {
        CloseableHttpResponse resp = mockBackend.execute(
                EasyMock.isA(HttpRoute.class),
                EasyMock.eq(request),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp).andReturn(Proxies.enhanceResponse(response));
    }

    private IExpectationSetters<CloseableHttpResponse> backendExpectsRequestAndReturn(
            HttpRequestWrapper request, CloseableHttpResponse response) throws Exception {
        CloseableHttpResponse resp = mockBackend.execute(
                EasyMock.isA(HttpRoute.class),
                EasyMock.eq(request),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp).andReturn(response);
    }

    private IExpectationSetters<CloseableHttpResponse> backendExpectsAnyRequestAndThrows(
            Throwable throwable) throws Exception {
        CloseableHttpResponse resp = mockBackend.execute(
                EasyMock.isA(HttpRoute.class),
                EasyMock.isA(HttpRequestWrapper.class),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp).andThrow(throwable);
    }

    private IExpectationSetters<CloseableHttpResponse> backendCaptureRequestAndReturn(
            Capture<HttpRequestWrapper> cap, HttpResponse response) throws Exception {
        CloseableHttpResponse resp = mockBackend.execute(
                EasyMock.isA(HttpRoute.class),
                EasyMock.capture(cap),
                EasyMock.isA(HttpClientContext.class),
                EasyMock.<HttpExecutionAware>isNull());
        return EasyMock.expect(resp).andReturn(Proxies.enhanceResponse(response));
    }

    private void getCacheEntryReturns(HttpCacheEntry result) throws IOException {
        expect(mockCache.getCacheEntry(host, request)).andReturn(result);
    }

    private void getVariantCacheEntriesReturns(Map<String,Variant> result) throws IOException {
        expect(mockCache.getVariantCacheEntriesWithEtags(host, request)).andReturn(result);
    }

    private void cacheInvalidatorWasCalled()  throws IOException {
        mockCache.flushInvalidatedCacheEntriesFor(
                (HttpHost)anyObject(),
                (HttpRequest)anyObject());
    }

    private void cacheEntryValidatable(boolean b) {
        expect(mockValidityPolicy.isRevalidatable(
                (HttpCacheEntry)anyObject())).andReturn(b);
    }

    private void cacheEntryMustRevalidate(boolean b) {
        expect(mockValidityPolicy.mustRevalidate(mockCacheEntry))
            .andReturn(b);
    }

    private void cacheEntryProxyRevalidate(boolean b) {
        expect(mockValidityPolicy.proxyRevalidate(mockCacheEntry))
            .andReturn(b);
    }

    private void mayReturnStaleWhileRevalidating(boolean b) {
        expect(mockValidityPolicy.mayReturnStaleWhileRevalidating(
                (HttpCacheEntry)anyObject(), (Date)anyObject())).andReturn(b);
    }

    private void conditionalRequestBuilderReturns(HttpRequestWrapper validate)
            throws Exception {
        expect(mockConditionalRequestBuilder.buildConditionalRequest(
                request, entry)).andReturn(validate);
}

    private void getCurrentDateReturns(Date date) {
        expect(impl.getCurrentDate()).andReturn(date);
    }

    private void requestPolicyAllowsCaching(boolean allow) {
        expect(mockRequestPolicy.isServableFromCache(
                (HttpRequest)anyObject())).andReturn(allow);
    }

    private void cacheEntrySuitable(boolean suitable) {
        expect(
                mockSuitabilityChecker.canCachedResponseBeUsed(
                        (HttpHost)anyObject(),
                        (HttpRequest)anyObject(),
                        (HttpCacheEntry)anyObject(),
                        (Date)anyObject())).andReturn(suitable);
    }

    private void entryHasStaleness(long staleness) {
        expect(mockValidityPolicy.getStalenessSecs(
                (HttpCacheEntry)anyObject(),
                (Date)anyObject()
                )).andReturn(staleness);
    }

    private void responseIsGeneratedFromCache() {
        expect(mockResponseGenerator.generateResponse(
                (HttpCacheEntry)anyObject())).andReturn(mockCachedResponse);
    }

    private void handleBackendResponseReturnsResponse(HttpRequestWrapper request, HttpResponse response)
            throws IOException {
        expect(
                impl.handleBackendResponse(
                        isA(HttpRoute.class),
                        same(request),
                        isA(HttpClientContext.class),
                        (HttpExecutionAware) isNull(),
                        isA(Date.class),
                        isA(Date.class),
                        isA(CloseableHttpResponse.class))).andReturn(
                                Proxies.enhanceResponse(response));
    }

    private void mockImplMethods(String... methods) {
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
                config).addMockedMethods(methods).createNiceMock();
    }

}
