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
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.isNull;
import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.AssertionFailedError;

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
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.easymock.Capture;
import org.easymock.IExpectationSetters;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("boxing") // test code
public abstract class TestCachingExecChain {

    private ClientExecChain impl;

    protected CacheValidityPolicy mockValidityPolicy;
    protected CacheableRequestPolicy mockRequestPolicy;
    protected ClientExecChain mockBackend;
    protected HttpCache mockCache;
    private HttpCacheStorage mockStorage;
    protected CachedResponseSuitabilityChecker mockSuitabilityChecker;
    protected ResponseCachingPolicy mockResponsePolicy;
    protected HttpCacheEntry mockCacheEntry;
    protected CachedHttpResponseGenerator mockResponseGenerator;
    private ResponseHandler<Object> mockHandler;
    private HttpUriRequest mockUriRequest;
    private CloseableHttpResponse mockCachedResponse;
    protected ConditionalRequestBuilder mockConditionalRequestBuilder;
    private HttpRequest mockConditionalRequest;
    private StatusLine mockStatusLine;
    protected ResponseProtocolCompliance mockResponseProtocolCompliance;
    protected RequestProtocolCompliance mockRequestProtocolCompliance;
    protected CacheConfig config;
    protected AsynchronousValidator asyncValidator;

    protected HttpRoute route;
    protected HttpHost host;
    protected HttpRequestWrapper request;
    protected HttpCacheContext context;
    protected HttpCacheEntry entry;

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
        asyncValidator = new AsynchronousValidator(config);

        host = new HttpHost("foo.example.com", 80);
        route = new HttpRoute(host);
        request = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "/stuff",
            HttpVersion.HTTP_1_1));
        context = HttpCacheContext.create();
        context.setTargetHost(host);
        entry = HttpTestUtils.makeCacheEntry();
        impl = createCachingExecChain(mockBackend, mockCache, mockValidityPolicy,
            mockResponsePolicy, mockResponseGenerator, mockRequestPolicy, mockSuitabilityChecker,
            mockConditionalRequestBuilder, mockResponseProtocolCompliance,
            mockRequestProtocolCompliance, config, asyncValidator);
    }

    public abstract ClientExecChain createCachingExecChain(ClientExecChain backend,
        HttpCache responseCache, CacheValidityPolicy validityPolicy,
        ResponseCachingPolicy responseCachingPolicy, CachedHttpResponseGenerator responseGenerator,
        CacheableRequestPolicy cacheableRequestPolicy,
        CachedResponseSuitabilityChecker suitabilityChecker,
        ConditionalRequestBuilder conditionalRequestBuilder,
        ResponseProtocolCompliance responseCompliance, RequestProtocolCompliance requestCompliance,
        CacheConfig config, AsynchronousValidator asynchRevalidator);

    public abstract ClientExecChain createCachingExecChain(ClientExecChain backend,
        HttpCache cache, CacheConfig config);

    public static HttpRequestWrapper eqRequest(final HttpRequestWrapper in) {
        EasyMock.reportMatcher(new RequestEquivalent(in));
        return null;
    }

    public static <R extends HttpResponse> R eqResponse(final R in) {
        EasyMock.reportMatcher(new ResponseEquivalent(in));
        return null;
    }

    protected void replayMocks() {
        replay(mockRequestPolicy);
        replay(mockValidityPolicy);
        replay(mockSuitabilityChecker);
        replay(mockResponsePolicy);
        replay(mockCacheEntry);
        replay(mockResponseGenerator);
        replay(mockBackend);
        replay(mockCache);
        replay(mockHandler);
        replay(mockUriRequest);
        replay(mockCachedResponse);
        replay(mockConditionalRequestBuilder);
        replay(mockConditionalRequest);
        replay(mockStatusLine);
        replay(mockResponseProtocolCompliance);
        replay(mockRequestProtocolCompliance);
        replay(mockStorage);
    }

    protected void verifyMocks() {
        verify(mockRequestPolicy);
        verify(mockValidityPolicy);
        verify(mockSuitabilityChecker);
        verify(mockResponsePolicy);
        verify(mockCacheEntry);
        verify(mockResponseGenerator);
        verify(mockBackend);
        verify(mockCache);
        verify(mockHandler);
        verify(mockUriRequest);
        verify(mockCachedResponse);
        verify(mockConditionalRequestBuilder);
        verify(mockConditionalRequest);
        verify(mockStatusLine);
        verify(mockResponseProtocolCompliance);
        verify(mockRequestProtocolCompliance);
        verify(mockStorage);
    }

    @Test
    public void testCacheableResponsesGoIntoCache() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());

        replayMocks();
        impl.execute(route, req1, context, null);
        impl.execute(route, req2, context, null);
        verifyMocks();
    }

    @Test
    public void testOlderCacheableResponsesDoNotGoIntoCache() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"new-etag\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        req2.setHeader("Cache-Control", "no-cache");
        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"old-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp2.setHeader("Cache-Control", "max-age=3600");

        backendExpectsAnyRequestAndReturn(resp2);

        final HttpRequestWrapper req3 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());

        replayMocks();
        impl.execute(route, req1, context, null);
        impl.execute(route, req2, context, null);
        final HttpResponse result = impl.execute(route, req3, context, null);
        verifyMocks();

        assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }

    @Test
    public void testNewerCacheableResponsesReplaceExistingCacheEntry() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"old-etag\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        req2.setHeader("Cache-Control", "max-age=0");
        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"new-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600");

        backendExpectsAnyRequestAndReturn(resp2);

        final HttpRequestWrapper req3 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());

        replayMocks();
        impl.execute(route, req1, context, null);
        impl.execute(route, req2, context, null);
        final HttpResponse result = impl.execute(route, req3, context, null);
        verifyMocks();

        assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }

    protected void requestIsFatallyNonCompliant(final RequestProtocolError error) {
        final List<RequestProtocolError> errors = new ArrayList<RequestProtocolError>();
        if (error != null) {
            errors.add(error);
        }
        expect(mockRequestProtocolCompliance.requestIsFatallyNonCompliant(eqRequest(request)))
            .andReturn(errors);
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
        final HttpResponse result = impl.execute(route, request, context, null);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, result);
    }

    @Test
    public void testNonCacheableResponseIsNotCachedAndIsReturnedAsIs() throws Exception {
        final CacheConfig configDefault = CacheConfig.DEFAULT;
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(new HeapResourceFactory(),
            mockStorage, configDefault), configDefault);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "no-cache");

        expect(mockStorage.getEntry(isA(String.class))).andReturn(null).anyTimes();
        mockStorage.removeEntry(isA(String.class));
        expectLastCall().anyTimes();
        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        final HttpResponse result = impl.execute(route, req1, context, null);
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
        impl.execute(route, request, context, null);
        verifyMocks();
    }

    @Test
    public void testNonCompliantRequestWrapsAndReThrowsProtocolException() throws Exception {

        final ClientProtocolException expected = new ClientProtocolException("ouch");

        requestIsFatallyNonCompliant(null);
        mockRequestProtocolCompliance.makeRequestCompliant((HttpRequestWrapper) anyObject());
        expectLastCall().andThrow(expected);

        boolean gotException = false;
        replayMocks();
        try {
            impl.execute(route, request, context, null);
        } catch (final ClientProtocolException ex) {
            Assert.assertSame(expected, ex);
            gotException = true;
        }
        verifyMocks();
        Assert.assertTrue(gotException);
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForCacheOptionsResponse() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req = HttpRequestWrapper.wrap(new BasicHttpRequest("OPTIONS", "*",
            HttpVersion.HTTP_1_1));
        req.setHeader("Max-Forwards", "0");

        impl.execute(route, req, context, null);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
            context.getCacheResponseStatus());
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForFatallyNoncompliantRequest()
        throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req.setHeader("Range", "bytes=0-50");
        req.setHeader("If-Range", "W/\"weak-etag\"");

        impl.execute(route, req, context, null);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
            context.getCacheResponseStatus());
    }

    @Test
    public void testRecordsClientProtocolInViaHeaderIfRequestNotServableFromCache()
        throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "/",
            HttpVersion.HTTP_1_0));
        req.setHeader("Cache-Control", "no-cache");
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NO_CONTENT, "No Content");
        final Capture<HttpRequestWrapper> cap = new Capture<HttpRequestWrapper>();

        backendCaptureRequestAndReturn(cap, resp);

        replayMocks();
        impl.execute(route, req, context, null);
        verifyMocks();

        final HttpRequest captured = cap.getValue();
        final String via = captured.getFirstHeader("Via").getValue();
        final String proto = via.split("\\s+")[0];
        Assert.assertTrue("http/1.0".equalsIgnoreCase(proto) || "1.0".equalsIgnoreCase(proto));
    }

    @Test
    public void testSetsCacheMissContextIfRequestNotServableFromCache() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req.setHeader("Cache-Control", "no-cache");
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NO_CONTENT, "No Content");

        backendExpectsAnyRequestAndReturn(resp);

        replayMocks();
        impl.execute(route, req, context, null);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_MISS, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestNotServableFromCache() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req.setHeader("Cache-Control", "no-cache");
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NO_CONTENT, "No Content");

        backendExpectsAnyRequestAndReturn(resp);

        replayMocks();
        final HttpResponse result = impl.execute(route, req, context, null);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsViaHeaderOnResponseForCacheMiss() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        final HttpResponse result = impl.execute(route, req1, context, null);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsCacheHitContextIfRequestServedFromCache() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1, context, null);
        impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestServedFromCache() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfModifiedSinceHeaderIfRequestServedFromCache() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns304ForIfModifiedSinceHeaderIf304ResponseInCache() throws Exception {
        final Date now = new Date();
        final Date oneHourAgo = new Date(now.getTime() - 3600 * 1000L);
        final Date inTenMinutes = new Date(now.getTime() + 600 * 1000L);
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req1.addHeader("If-Modified-Since", DateUtils.formatDate(oneHourAgo));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(oneHourAgo));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not modified");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-control", "max-age=600");
        resp1.setHeader("Expires", DateUtils.formatDate(inTenMinutes));

        expect(
            mockBackend.execute(eq(route), isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class), (HttpExecutionAware) isNull())).andReturn(
            Proxies.enhanceResponse(resp1)).once();

        expect(
            mockBackend.execute(eq(route), isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class), (HttpExecutionAware) isNull())).andThrow(
            new AssertionFailedError("Should have reused cached 304 response")).anyTimes();

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
        Assert.assertFalse(result.containsHeader("Last-Modified"));
    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsLess() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        // The variant has been modified since this date
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));

        final HttpResponse resp2 = HttpTestUtils.make200Response();

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsInvalid() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAfter = new Date(now.getTime() + 10 * 1000L);
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        // invalid date (date in the future)
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAfter));

        backendExpectsAnyRequestAndReturn(resp1).times(2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderIfRequestServedFromCache() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req2.addHeader("If-None-Match", "*");
        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFails() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        req2.addHeader("If-None-Match", "\"abc\"");

        final HttpResponse resp2 = HttpTestUtils.make200Response();

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderAndIfModifiedSinceIfRequestServedFromCache()
        throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
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
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFailsIfModifiedSinceIgnored() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req2.addHeader("If-None-Match", "\"abc\"");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForOptionsFollowedByGetIfAuthorizationHeaderAndSharedCache()
        throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.custom()
            .setSharedCache(true).build());
        final Date now = new Date();
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpOptions(
            "http://foo.example.com/"));
        req1.setHeader("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req2.setHeader("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NO_CONTENT, "No Content");
        resp1.setHeader("Content-Length", "0");
        resp1.setHeader("ETag", "\"options-etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(now));
        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"get-etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(now));

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testSetsValidatedContextIfRequestWasSuccessfullyValidated() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.VALIDATED, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderIfRequestWasSuccessfullyValidated() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsModuleResponseContextIfValidationRequiredButFailed() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5, must-revalidate");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        impl.execute(route, req1, context, null);
        impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
            context.getCacheResponseStatus());
    }

    @Test
    public void testSetsModuleResponseContextIfValidationFailsButNotRequired() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        impl.execute(route, req1, context, null);
        impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT, context.getCacheResponseStatus());
    }

    @Test
    public void testSetViaHeaderIfValidationFailsButNotRequired() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfNoneMatchPassesIfRequestServedFromOrigin() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-None-Match", "\"etag\"");
        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns200ForIfNoneMatchFailsIfRequestServedFromOrigin() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-None-Match", "\"etag\"");
        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"newetag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns304ForIfModifiedSincePassesIfRequestServedFromOrigin() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));
        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns200ForIfModifiedSinceFailsIfRequestServedFromOrigin() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));
        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"newetag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testVariantMissServerIfReturns304CacheReturns200() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req1.addHeader("Accept-Encoding", "gzip");

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Etag", "\"gzip_etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req2.addHeader("Accept-Encoding", "deflate");

        final HttpRequestWrapper req2Server = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req2Server.addHeader("Accept-Encoding", "deflate");
        req2Server.addHeader("If-None-Match", "\"gzip_etag\"");

        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("Etag", "\"deflate_etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Cache-Control", "public, max-age=3600");

        final HttpRequestWrapper req3 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req3.addHeader("Accept-Encoding", "gzip,deflate");

        final HttpRequestWrapper req3Server = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req3Server.addHeader("Accept-Encoding", "gzip,deflate");
        req3Server.addHeader("If-None-Match", "\"gzip_etag\",\"deflate_etag\"");

        final HttpResponse resp3 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
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
        final HttpResponse result1 = impl.execute(route, req1, context, null);

        final HttpResponse result2 = impl.execute(route, req2, context, null);

        final HttpResponse result3 = impl.execute(route, req3, context, null);

        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result1.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result3.getStatusLine().getStatusCode());
    }

    @Test
    public void testVariantsMissServerReturns304CacheReturns304() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req1.addHeader("Accept-Encoding", "gzip");

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Etag", "\"gzip_etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req2.addHeader("Accept-Encoding", "deflate");

        final HttpRequestWrapper req2Server = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req2Server.addHeader("Accept-Encoding", "deflate");
        req2Server.addHeader("If-None-Match", "\"gzip_etag\"");

        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("Etag", "\"deflate_etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Cache-Control", "public, max-age=3600");

        final HttpRequestWrapper req4 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req4.addHeader("Accept-Encoding", "gzip,identity");
        req4.addHeader("If-None-Match", "\"gzip_etag\"");

        final HttpRequestWrapper req4Server = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));
        req4Server.addHeader("Accept-Encoding", "gzip,identity");
        req4Server.addHeader("If-None-Match", "\"gzip_etag\"");

        final HttpResponse resp4 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp4.setHeader("Etag", "\"gzip_etag\"");
        resp4.setHeader("Date", DateUtils.formatDate(now));
        resp4.setHeader("Vary", "Accept-Encoding");
        resp4.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        backendExpectsAnyRequestAndReturn(resp4);

        replayMocks();
        final HttpResponse result1 = impl.execute(route, req1, context, null);

        final HttpResponse result2 = impl.execute(route, req2, context, null);

        final HttpResponse result4 = impl.execute(route, req4, context, null);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result1.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result4.getStatusLine().getStatusCode());

    }

    @Test
    public void testSocketTimeoutExceptionIsNotSilentlyCatched() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(new InputStreamEntity(new InputStream() {
            private boolean closed = false;

            @Override
            public void close() throws IOException {
                closed = true;
            }

            @Override
            public int read() throws IOException {
                if (closed) {
                    throw new SocketException("Socket closed");
                }
                throw new SocketTimeoutException("Read timed out");
            }
        }, 128));
        resp1.setHeader("Date", DateUtils.formatDate(now));

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        try {
            final HttpResponse result1 = impl.execute(route, req1, context, null);
            EntityUtils.toString(result1.getEntity());
            Assert.fail("We should have had a SocketTimeoutException");
        } catch (final SocketTimeoutException e) {
        }
        verifyMocks();

    }

    @Test
    public void testIsSharedCache() {
        Assert.assertTrue(config.isSharedCache());
    }

    @Test
    public void testTreatsCacheIOExceptionsAsCacheMiss() throws Exception {

        impl = createCachingExecChain(mockBackend, mockCache, CacheConfig.DEFAULT);
        final CloseableHttpResponse resp = Proxies.enhanceResponse(HttpTestUtils.make200Response());

        mockCache.flushInvalidatedCacheEntriesFor(host, request);
        expectLastCall().andThrow(new IOException()).anyTimes();
        mockCache.flushInvalidatedCacheEntriesFor(isA(HttpHost.class), isA(HttpRequest.class),
            isA(HttpResponse.class));
        expectLastCall().anyTimes();
        expect(mockCache.getCacheEntry(eq(host), isA(HttpRequest.class))).andThrow(
            new IOException()).anyTimes();
        expect(mockCache.getVariantCacheEntriesWithEtags(eq(host), isA(HttpRequest.class)))
            .andThrow(new IOException()).anyTimes();
        expect(
            mockCache.cacheAndReturnResponse(eq(host), isA(HttpRequest.class),
                isA(CloseableHttpResponse.class), isA(Date.class), isA(Date.class)))
            .andReturn(resp).anyTimes();
        expect(
            mockBackend.execute(eq(route), isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class), (HttpExecutionAware) isNull())).andReturn(resp);

        replayMocks();
        final HttpResponse result = impl.execute(route, request, context, null);
        verifyMocks();
        Assert.assertSame(resp, result);
    }

    @Test
    public void testIfOnlyIfCachedAndNoCacheEntryBackendNotCalled() throws Exception {
        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        request.addHeader("Cache-Control", "only-if-cached");

        final HttpResponse resp = impl.execute(route, request, context, null);

        Assert.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, resp.getStatusLine().getStatusCode());
    }

    @Test
    public void testIfOnlyIfCachedAndEntryNotSuitableBackendNotCalled() throws Exception {

        request.setHeader("Cache-Control", "only-if-cached");

        entry = HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("Cache-Control",
            "must-revalidate") });

        requestIsFatallyNonCompliant(null);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(entry);
        cacheEntrySuitable(false);

        replayMocks();
        final HttpResponse resp = impl.execute(route, request, context, null);
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
        final HttpResponse resp = impl.execute(route, request, context, null);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, resp);
    }

    @Test
    public void testDoesNotSetConnectionInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setTargetHost(host);
        impl.execute(route, request, context, null);
        impl.execute(route, request, ctx, null);
        assertNull(ctx.getConnection());
    }

    @Test
    public void testSetsTargetHostInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setTargetHost(host);
        impl.execute(route, request, context, null);
        impl.execute(route, request, ctx, null);
        assertSame(host, ctx.getTargetHost());
    }

    @Test
    public void testSetsRouteInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setTargetHost(host);
        impl.execute(route, request, context, null);
        impl.execute(route, request, ctx, null);
        assertEquals(route, ctx.getHttpRoute());
    }

    @Test
    public void testSetsRequestInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setTargetHost(host);
        impl.execute(route, request, context, null);
        impl.execute(route, request, ctx, null);
        if (!HttpTestUtils.equivalent(request, ctx.getRequest())) {
            assertSame(request, ctx.getRequest());
        }
    }

    @Test
    public void testSetsResponseInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setTargetHost(host);
        impl.execute(route, request, context, null);
        final HttpResponse result = impl.execute(route, request, ctx, null);
        if (!HttpTestUtils.equivalent(result, ctx.getResponse())) {
            assertSame(result, ctx.getResponse());
        }
    }

    @Test
    public void testSetsRequestSentInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setTargetHost(host);
        impl.execute(route, request, context, null);
        impl.execute(route, request, ctx, null);
        assertTrue(ctx.isRequestSent());
    }

    @Test
    public void testCanCacheAResponseWithoutABody() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NO_CONTENT, "No Content");
        response.setHeader("Date", DateUtils.formatDate(new Date()));
        response.setHeader("Cache-Control", "max-age=300");
        final DummyBackend backend = new DummyBackend();
        backend.setResponse(response);
        impl = createCachingExecChain(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
        impl.execute(route, request, context, null);
        impl.execute(route, request, context, null);
        assertEquals(1, backend.getExecutions());
    }

    @Test
    public void testNoEntityForIfNoneMatchRequestNotYetInCache() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req1.addHeader("If-None-Match", "\"etag\"");

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not modified");
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        replayMocks();
        final HttpResponse result = impl.execute(route, req1, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
        assertNull("The 304 response messages MUST NOT contain a message-body", result.getEntity());
    }

    @Test
    public void testNotModifiedResponseUpdatesCacheEntryWhenNoEntity() throws Exception {

        final Date now = new Date();

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req1.addHeader("If-None-Match", "etag");

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req2.addHeader("If-None-Match", "etag");

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not modified");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=0");
        resp1.setHeader("Etag", "etag");

        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not modified");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "max-age=0");
        resp1.setHeader("Etag", "etag");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        replayMocks();
        final HttpResponse result1 = impl.execute(route, req1, context, null);
        final HttpResponse result2 = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getStatusLine().getStatusCode());
        assertEquals("etag", result1.getFirstHeader("Etag").getValue());
        assertEquals(HttpStatus.SC_NOT_MODIFIED, result2.getStatusLine().getStatusCode());
        assertEquals("etag", result2.getFirstHeader("Etag").getValue());
    }

    @Test
    public void testNotModifiedResponseWithVaryUpdatesCacheEntryWhenNoEntity() throws Exception {

        final Date now = new Date();

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req1.addHeader("If-None-Match", "etag");

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req2.addHeader("If-None-Match", "etag");

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not modified");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=0");
        resp1.setHeader("Etag", "etag");
        resp1.setHeader("Vary", "Accept-Encoding");

        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not modified");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "max-age=0");
        resp1.setHeader("Etag", "etag");
        resp1.setHeader("Vary", "Accept-Encoding");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        replayMocks();
        final HttpResponse result1 = impl.execute(route, req1, context, null);
        final HttpResponse result2 = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getStatusLine().getStatusCode());
        assertEquals("etag", result1.getFirstHeader("Etag").getValue());
        assertEquals(HttpStatus.SC_NOT_MODIFIED, result2.getStatusLine().getStatusCode());
        assertEquals("etag", result2.getFirstHeader("Etag").getValue());
    }

    @Test
    public void testDoesNotSend304ForNonConditionalRequest() throws Exception {

        final Date now = new Date();
        final Date inOneMinute = new Date(System.currentTimeMillis() + 60000);

        impl = createCachingExecChain(mockBackend, new BasicHttpCache(), CacheConfig.DEFAULT);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));
        req1.addHeader("If-None-Match", "etag");

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new HttpGet(
            "http://foo.example.com/"));

        final HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_MODIFIED, "Not modified");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "public, max-age=60");
        resp1.setHeader("Expires", DateUtils.formatDate(inOneMinute));
        resp1.setHeader("Etag", "etag");
        resp1.setHeader("Vary", "Accept-Encoding");

        final HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,
            "Ok");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "public, max-age=60");
        resp2.setHeader("Expires", DateUtils.formatDate(inOneMinute));
        resp2.setHeader("Etag", "etag");
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setEntity(HttpTestUtils.makeBody(128));

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2).anyTimes();
        replayMocks();
        final HttpResponse result1 = impl.execute(route, req1, context, null);
        final HttpResponse result2 = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getStatusLine().getStatusCode());
        assertNull(result1.getEntity());
        assertEquals(HttpStatus.SC_OK, result2.getStatusLine().getStatusCode());
        Assert.assertNotNull(result2.getEntity());
    }

    @Test
    public void testUsesVirtualHostForCacheKey() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final HttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
        impl.execute(route, request, context, null);
        assertEquals(1, backend.getExecutions());
        context.setTargetHost(new HttpHost("bar.example.com"));
        impl.execute(route, request, context, null);
        assertEquals(2, backend.getExecutions());
        impl.execute(route, request, context, null);
        assertEquals(2, backend.getExecutions());
    }

    private IExpectationSetters<CloseableHttpResponse> backendExpectsAnyRequestAndReturn(
        final HttpResponse response) throws Exception {
        final CloseableHttpResponse resp = mockBackend.execute(EasyMock.isA(HttpRoute.class),
            EasyMock.isA(HttpRequestWrapper.class), EasyMock.isA(HttpClientContext.class),
            EasyMock.<HttpExecutionAware> isNull());
        return EasyMock.expect(resp).andReturn(Proxies.enhanceResponse(response));
    }

    protected IExpectationSetters<CloseableHttpResponse> backendExpectsRequestAndReturn(
        final HttpRequestWrapper request, final HttpResponse response) throws Exception {
        final CloseableHttpResponse resp = mockBackend.execute(EasyMock.isA(HttpRoute.class),
            EasyMock.eq(request), EasyMock.isA(HttpClientContext.class),
            EasyMock.<HttpExecutionAware> isNull());
        return EasyMock.expect(resp).andReturn(Proxies.enhanceResponse(response));
    }

    protected IExpectationSetters<CloseableHttpResponse> backendExpectsRequestAndReturn(
        final HttpRequestWrapper request, final CloseableHttpResponse response) throws Exception {
        final CloseableHttpResponse resp = mockBackend.execute(EasyMock.isA(HttpRoute.class),
            EasyMock.eq(request), EasyMock.isA(HttpClientContext.class),
            EasyMock.<HttpExecutionAware> isNull());
        return EasyMock.expect(resp).andReturn(response);
    }

    protected IExpectationSetters<CloseableHttpResponse> backendExpectsAnyRequestAndThrows(
        final Throwable throwable) throws Exception {
        final CloseableHttpResponse resp = mockBackend.execute(EasyMock.isA(HttpRoute.class),
            EasyMock.isA(HttpRequestWrapper.class), EasyMock.isA(HttpClientContext.class),
            EasyMock.<HttpExecutionAware> isNull());
        return EasyMock.expect(resp).andThrow(throwable);
    }

    protected IExpectationSetters<CloseableHttpResponse> backendCaptureRequestAndReturn(
        final Capture<HttpRequestWrapper> cap, final HttpResponse response) throws Exception {
        final CloseableHttpResponse resp = mockBackend.execute(EasyMock.isA(HttpRoute.class),
            EasyMock.capture(cap), EasyMock.isA(HttpClientContext.class),
            EasyMock.<HttpExecutionAware> isNull());
        return EasyMock.expect(resp).andReturn(Proxies.enhanceResponse(response));
    }

    protected void getCacheEntryReturns(final HttpCacheEntry result) throws IOException {
        expect(mockCache.getCacheEntry(eq(host), eqRequest(request))).andReturn(result);
    }

    private void cacheInvalidatorWasCalled() throws IOException {
        mockCache
            .flushInvalidatedCacheEntriesFor((HttpHost) anyObject(), (HttpRequest) anyObject());
    }

    protected void cacheEntryValidatable(final boolean b) {
        expect(mockValidityPolicy.isRevalidatable((HttpCacheEntry) anyObject())).andReturn(b)
            .anyTimes();
    }

    protected void cacheEntryMustRevalidate(final boolean b) {
        expect(mockValidityPolicy.mustRevalidate(mockCacheEntry)).andReturn(b);
    }

    protected void cacheEntryProxyRevalidate(final boolean b) {
        expect(mockValidityPolicy.proxyRevalidate(mockCacheEntry)).andReturn(b);
    }

    protected void mayReturnStaleWhileRevalidating(final boolean b) {
        expect(
            mockValidityPolicy.mayReturnStaleWhileRevalidating((HttpCacheEntry) anyObject(),
                (Date) anyObject())).andReturn(b);
    }

    protected void conditionalRequestBuilderReturns(final HttpRequestWrapper validate)
        throws Exception {
        expect(mockConditionalRequestBuilder.buildConditionalRequest(request, entry)).andReturn(
            validate);
    }

    protected void requestPolicyAllowsCaching(final boolean allow) {
        expect(mockRequestPolicy.isServableFromCache((HttpRequest) anyObject())).andReturn(allow);
    }

    protected void cacheEntrySuitable(final boolean suitable) {
        expect(
            mockSuitabilityChecker.canCachedResponseBeUsed((HttpHost) anyObject(),
                (HttpRequest) anyObject(), (HttpCacheEntry) anyObject(), (Date) anyObject()))
            .andReturn(suitable);
    }

    private void entryHasStaleness(final long staleness) {
        expect(
            mockValidityPolicy.getStalenessSecs((HttpCacheEntry) anyObject(), (Date) anyObject()))
            .andReturn(staleness);
    }

    protected void responseIsGeneratedFromCache() {
        expect(
            mockResponseGenerator.generateResponse((HttpRequestWrapper) anyObject(), (HttpCacheEntry) anyObject()))
            .andReturn(mockCachedResponse);
    }

}
