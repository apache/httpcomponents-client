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
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.same;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.impl.classic.ClassicRequestCopier;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.TimeValue;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import junit.framework.AssertionFailedError;

@SuppressWarnings("boxing") // test code
public abstract class TestCachingExecChain {

    private CachingExec impl;

    protected CacheValidityPolicy mockValidityPolicy;
    protected CacheableRequestPolicy mockRequestPolicy;
    protected ExecChain mockExecChain;
    protected ExecRuntime mockEndpoint;
    protected HttpCache mockCache;
    private HttpCacheStorage mockStorage;
    protected CachedResponseSuitabilityChecker mockSuitabilityChecker;
    protected ResponseCachingPolicy mockResponsePolicy;
    protected HttpCacheEntry mockCacheEntry;
    protected CachedHttpResponseGenerator mockResponseGenerator;
    private HttpClientResponseHandler<Object> mockHandler;
    private ClassicHttpRequest mockUriRequest;
    private HttpRequest mockConditionalRequest;
    protected ResponseProtocolCompliance mockResponseProtocolCompliance;
    protected RequestProtocolCompliance mockRequestProtocolCompliance;
    protected DefaultCacheRevalidator mockCacheRevalidator;
    protected ConditionalRequestBuilder<ClassicHttpRequest> mockConditionalRequestBuilder;
    protected CacheConfig config;

    protected HttpRoute route;
    protected HttpHost host;
    protected ClassicHttpRequest request;
    protected HttpCacheContext context;
    protected HttpCacheEntry entry;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        mockRequestPolicy = createNiceMock(CacheableRequestPolicy.class);
        mockValidityPolicy = createNiceMock(CacheValidityPolicy.class);
        mockEndpoint = createNiceMock(ExecRuntime.class);
        mockExecChain = createNiceMock(ExecChain.class);
        mockCache = createNiceMock(HttpCache.class);
        mockSuitabilityChecker = createNiceMock(CachedResponseSuitabilityChecker.class);
        mockResponsePolicy = createNiceMock(ResponseCachingPolicy.class);
        mockHandler = createNiceMock(HttpClientResponseHandler.class);
        mockUriRequest = createNiceMock(ClassicHttpRequest.class);
        mockCacheEntry = createNiceMock(HttpCacheEntry.class);
        mockResponseGenerator = createNiceMock(CachedHttpResponseGenerator.class);
        mockConditionalRequest = createNiceMock(HttpRequest.class);
        mockResponseProtocolCompliance = createNiceMock(ResponseProtocolCompliance.class);
        mockRequestProtocolCompliance = createNiceMock(RequestProtocolCompliance.class);
        mockCacheRevalidator = createNiceMock(DefaultCacheRevalidator.class);
        mockConditionalRequestBuilder = createNiceMock(ConditionalRequestBuilder.class);
        mockStorage = createNiceMock(HttpCacheStorage.class);
        config = CacheConfig.DEFAULT;

        host = new HttpHost("foo.example.com", 80);
        route = new HttpRoute(host);
        request = new BasicClassicHttpRequest("GET", "/stuff");
        context = HttpCacheContext.create();
        entry = HttpTestUtils.makeCacheEntry();
        impl = createCachingExecChain(mockCache, mockValidityPolicy,
                mockResponsePolicy, mockResponseGenerator, mockRequestPolicy, mockSuitabilityChecker,
                mockResponseProtocolCompliance,mockRequestProtocolCompliance,
                mockCacheRevalidator, mockConditionalRequestBuilder, config);
    }

    public abstract CachingExec createCachingExecChain(
            HttpCache responseCache, CacheValidityPolicy validityPolicy,
            ResponseCachingPolicy responseCachingPolicy, CachedHttpResponseGenerator responseGenerator,
            CacheableRequestPolicy cacheableRequestPolicy,
            CachedResponseSuitabilityChecker suitabilityChecker,
            ResponseProtocolCompliance responseCompliance, RequestProtocolCompliance requestCompliance,
            DefaultCacheRevalidator cacheRevalidator,
            ConditionalRequestBuilder<ClassicHttpRequest> conditionalRequestBuilder,
            CacheConfig config);

    public abstract CachingExec createCachingExecChain(HttpCache cache, CacheConfig config);

    protected ClassicHttpResponse execute(final ClassicHttpRequest request) throws IOException, HttpException {
        return impl.execute(ClassicRequestCopier.INSTANCE.copy(request), new ExecChain.Scope(
                "test", route, request, mockEndpoint, context), mockExecChain);
    }

    public static ClassicHttpRequest eqRequest(final ClassicHttpRequest in) {
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
        replay(mockExecChain);
        replay(mockCache);
        replay(mockHandler);
        replay(mockUriRequest);
        replay(mockConditionalRequestBuilder);
        replay(mockConditionalRequest);
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
        verify(mockExecChain);
        verify(mockCache);
        verify(mockHandler);
        verify(mockUriRequest);
        verify(mockConditionalRequestBuilder);
        verify(mockConditionalRequest);
        verify(mockResponseProtocolCompliance);
        verify(mockRequestProtocolCompliance);
        verify(mockStorage);
    }

    @Test
    public void testCacheableResponsesGoIntoCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();

        replayMocks();
        execute(req1);
        execute(req2);
        verifyMocks();
    }

    @Test
    public void testOlderCacheableResponsesDoNotGoIntoCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"new-etag\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "no-cache");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"old-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp2.setHeader("Cache-Control", "max-age=3600");

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = HttpTestUtils.makeDefaultRequest();

        replayMocks();
        execute(req1);
        execute(req2);
        final ClassicHttpResponse result = execute(req3);
        verifyMocks();

        assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }

    @Test
    public void testNewerCacheableResponsesReplaceExistingCacheEntry() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"old-etag\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-age=0");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"new-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600");

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = HttpTestUtils.makeDefaultRequest();

        replayMocks();
        execute(req1);
        execute(req2);
        final ClassicHttpResponse result = execute(req3);
        verifyMocks();

        assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }

    protected void requestIsFatallyNonCompliant(final RequestProtocolError error) {
        final List<RequestProtocolError> errors = new ArrayList<>();
        if (error != null) {
            errors.add(error);
        }
        expect(mockRequestProtocolCompliance.requestIsFatallyNonCompliant(eqRequest(request)))
            .andReturn(errors);
    }

    @Test
    public void testSuitableCacheEntryDoesNotCauseBackendRequest() throws Exception {
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(true);
        responseIsGeneratedFromCache(SimpleHttpResponse.create(HttpStatus.SC_OK));
        requestIsFatallyNonCompliant(null);
        entryHasStaleness(TimeValue.ZERO_MILLISECONDS);

        replayMocks();
        final ClassicHttpResponse result = execute(request);
        verifyMocks();
    }

    @Test
    public void testNonCacheableResponseIsNotCachedAndIsReturnedAsIs() throws Exception {
        final CacheConfig configDefault = CacheConfig.DEFAULT;
        impl = createCachingExecChain(new BasicHttpCache(new HeapResourceFactory(),
            mockStorage), configDefault);

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "no-cache");

        expect(mockStorage.getEntry(isA(String.class))).andReturn(null).anyTimes();
        mockStorage.removeEntry(isA(String.class));
        expectLastCall().anyTimes();
        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        final ClassicHttpResponse result = execute(req1);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, result));
    }

    @Test
    public void testResponseIsGeneratedWhenCacheEntryIsUsable() throws Exception {

        requestIsFatallyNonCompliant(null);
        requestPolicyAllowsCaching(true);
        cacheEntrySuitable(true);
        getCacheEntryReturns(mockCacheEntry);
        responseIsGeneratedFromCache(SimpleHttpResponse.create(HttpStatus.SC_OK));
        entryHasStaleness(TimeValue.ZERO_MILLISECONDS);

        replayMocks();
        execute(request);
        verifyMocks();
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForCacheOptionsResponse() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req = new BasicClassicHttpRequest("OPTIONS", "*");
        req.setHeader("Max-Forwards", "0");

        execute(req);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
            context.getCacheResponseStatus());
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForFatallyNoncompliantRequest() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Range", "bytes=0-50");
        req.setHeader("If-Range", "W/\"weak-etag\"");

        execute(req);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
            context.getCacheResponseStatus());
    }

    @Test
    public void testRecordsClientProtocolInViaHeaderIfRequestNotServableFromCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest originalRequest = new BasicClassicHttpRequest("GET", "/");
        originalRequest.setVersion(HttpVersion.HTTP_1_0);
        final ClassicHttpRequest req = originalRequest;
        req.setHeader("Cache-Control", "no-cache");
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();

        backendCaptureRequestAndReturn(cap, resp);

        replayMocks();
        execute(req);
        verifyMocks();

        final HttpRequest captured = cap.getValue();
        final String via = captured.getFirstHeader("Via").getValue();
        final String proto = via.split("\\s+")[0];
        Assert.assertTrue("http/1.0".equalsIgnoreCase(proto) || "1.0".equalsIgnoreCase(proto));
    }

    @Test
    public void testSetsCacheMissContextIfRequestNotServableFromCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Cache-Control", "no-cache");
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");

        backendExpectsAnyRequestAndReturn(resp);

        replayMocks();
        execute(req);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_MISS, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestNotServableFromCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Cache-Control", "no-cache");
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");

        backendExpectsAnyRequestAndReturn(resp);

        replayMocks();
        final ClassicHttpResponse result = execute(req);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsViaHeaderOnResponseForCacheMiss() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        final ClassicHttpResponse result = execute(req1);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsCacheHitContextIfRequestServedFromCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        execute(req1);
        execute(req2);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestServedFromCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfModifiedSinceHeaderIfRequestServedFromCache() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());

    }

    @Test
    public void testReturns304ForIfModifiedSinceHeaderIf304ResponseInCache() throws Exception {
        final Date now = new Date();
        final Date oneHourAgo = new Date(now.getTime() - 3600 * 1000L);
        final Date inTenMinutes = new Date(now.getTime() + 600 * 1000L);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        req1.addHeader("If-Modified-Since", DateUtils.formatDate(oneHourAgo));
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(oneHourAgo));

        final ClassicHttpResponse resp1 = HttpTestUtils.make304Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-control", "max-age=600");
        resp1.setHeader("Expires", DateUtils.formatDate(inTenMinutes));

        expect(
            mockExecChain.proceed(isA(ClassicHttpRequest.class), isA(ExecChain.Scope.class))).andReturn(resp1).once();

        expect(
            mockExecChain.proceed(isA(ClassicHttpRequest.class), isA(ExecChain.Scope.class))).andThrow(
            new AssertionFailedError("Should have reused cached 304 response")).anyTimes();

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Assert.assertFalse(result.containsHeader("Last-Modified"));
    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsLess() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        // The variant has been modified since this date
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());

    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsInvalid() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAfter = new Date(now.getTime() + 10 * 1000L);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
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
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());

    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderIfRequestServedFromCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-None-Match", "*");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFails() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        req2.addHeader("If-None-Match", "\"abc\"");

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(200, result.getCode());

    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderAndIfModifiedSinceIfRequestServedFromCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
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
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFailsIfModifiedSinceIgnored() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-None-Match", "\"abc\"");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
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
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(200, result.getCode());

    }

    @Test
    public void testReturns200ForOptionsFollowedByGetIfAuthorizationHeaderAndSharedCache() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.custom()
            .setSharedCache(true).build());
        final Date now = new Date();
        final ClassicHttpRequest req1 = new HttpOptions("http://foo.example.com/");
        req1.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        resp1.setHeader("Content-Length", "0");
        resp1.setHeader("ETag", "\"options-etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(now));
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
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
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertEquals(200, result.getCode());
    }

    @Test
    public void testSetsValidatedContextIfRequestWasSuccessfullyValidated() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req1);
        execute(req2);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.VALIDATED, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderIfRequestWasSuccessfullyValidated() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsModuleResponseContextIfValidationRequiredButFailed() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5, must-revalidate");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        execute(req1);
        execute(req2);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
            context.getCacheResponseStatus());
    }

    @Test
    public void testSetsModuleResponseContextIfValidationFailsButNotRequired() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        execute(req1);
        execute(req2);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT, context.getCacheResponseStatus());
    }

    @Test
    public void testSetViaHeaderIfValidationFailsButNotRequired() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndThrows(new IOException());

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfNoneMatchPassesIfRequestServedFromOrigin() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-None-Match", "\"etag\"");
        final ClassicHttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
    }

    @Test
    public void testReturns200ForIfNoneMatchFailsIfRequestServedFromOrigin() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-None-Match", "\"etag\"");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"newetag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());
    }

    @Test
    public void testReturns304ForIfModifiedSincePassesIfRequestServedFromOrigin() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));
        final ClassicHttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
    }

    @Test
    public void testReturns200ForIfModifiedSinceFailsIfRequestServedFromOrigin() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
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
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());
    }

    @Test
    public void testVariantMissServerIfReturns304CacheReturns200() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com");
        req1.addHeader("Accept-Encoding", "gzip");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Etag", "\"gzip_etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com");
        req2.addHeader("Accept-Encoding", "deflate");

        final ClassicHttpRequest req2Server = new HttpGet("http://foo.example.com");
        req2Server.addHeader("Accept-Encoding", "deflate");
        req2Server.addHeader("If-None-Match", "\"gzip_etag\"");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("Etag", "\"deflate_etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Cache-Control", "public, max-age=3600");

        final ClassicHttpRequest req3 = new HttpGet("http://foo.example.com");
        req3.addHeader("Accept-Encoding", "gzip,deflate");

        final ClassicHttpRequest req3Server = new HttpGet("http://foo.example.com");
        req3Server.addHeader("Accept-Encoding", "gzip,deflate");
        req3Server.addHeader("If-None-Match", "\"gzip_etag\",\"deflate_etag\"");

        final ClassicHttpResponse resp3 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
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
        final ClassicHttpResponse result1 = execute(req1);

        final ClassicHttpResponse result2 = execute(req2);

        final ClassicHttpResponse result3 = execute(req3);

        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result1.getCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getCode());
        Assert.assertEquals(HttpStatus.SC_OK, result3.getCode());
    }

    @Test
    public void testVariantsMissServerReturns304CacheReturns304() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com");
        req1.addHeader("Accept-Encoding", "gzip");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Etag", "\"gzip_etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com");
        req2.addHeader("Accept-Encoding", "deflate");

        final ClassicHttpRequest req2Server = new HttpGet("http://foo.example.com");
        req2Server.addHeader("Accept-Encoding", "deflate");
        req2Server.addHeader("If-None-Match", "\"gzip_etag\"");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
            "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("Etag", "\"deflate_etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Cache-Control", "public, max-age=3600");

        final ClassicHttpRequest req4 = new HttpGet("http://foo.example.com");
        req4.addHeader("Accept-Encoding", "gzip,identity");
        req4.addHeader("If-None-Match", "\"gzip_etag\"");

        final ClassicHttpRequest req4Server = new HttpGet("http://foo.example.com");
        req4Server.addHeader("Accept-Encoding", "gzip,identity");
        req4Server.addHeader("If-None-Match", "\"gzip_etag\"");

        final ClassicHttpResponse resp4 = HttpTestUtils.make304Response();
        resp4.setHeader("Etag", "\"gzip_etag\"");
        resp4.setHeader("Date", DateUtils.formatDate(now));
        resp4.setHeader("Vary", "Accept-Encoding");
        resp4.setHeader("Cache-Control", "public, max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        backendExpectsAnyRequestAndReturn(resp4);

        replayMocks();
        final ClassicHttpResponse result1 = execute(req1);

        final ClassicHttpResponse result2 = execute(req2);

        final ClassicHttpResponse result4 = execute(req4);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result1.getCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getCode());
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result4.getCode());

    }

    @Test
    public void testSocketTimeoutExceptionIsNotSilentlyCatched() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final Date now = new Date();

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
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
        }, 128, null));
        resp1.setHeader("Date", DateUtils.formatDate(now));

        backendExpectsAnyRequestAndReturn(resp1);

        replayMocks();
        try {
            final ClassicHttpResponse result1 = execute(req1);
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
    public void testTooLargeResponsesAreNotCached() throws Exception {
        mockCache = EasyMock.createStrictMock(HttpCache.class);
        impl = createCachingExecChain(mockCache, mockValidityPolicy,
                mockResponsePolicy, mockResponseGenerator, mockRequestPolicy, mockSuitabilityChecker,
                mockResponseProtocolCompliance, mockRequestProtocolCompliance,
                mockCacheRevalidator, mockConditionalRequestBuilder, config);

        final HttpHost host = new HttpHost("foo.example.com");
        final ClassicHttpRequest request = new HttpGet("http://foo.example.com/bar");

        final Date now = new Date();
        final Date requestSent = new Date(now.getTime() - 3 * 1000L);
        final Date responseGenerated = new Date(now.getTime() - 2 * 1000L);
        final Date responseReceived = new Date(now.getTime() - 1 * 1000L);

        final ClassicHttpResponse originResponse = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        originResponse.setEntity(HttpTestUtils.makeBody(CacheConfig.DEFAULT_MAX_OBJECT_SIZE_BYTES + 1));
        originResponse.setHeader("Cache-Control","public, max-age=3600");
        originResponse.setHeader("Date", DateUtils.formatDate(responseGenerated));
        originResponse.setHeader("ETag", "\"etag\"");

        replayMocks();
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, mockEndpoint, context);
        impl.cacheAndReturnResponse(host, request, originResponse, scope, requestSent, responseReceived);

        verifyMocks();
    }

    @Test
    public void testSmallEnoughResponsesAreCached() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final ClassicHttpRequest request = new HttpGet("http://foo.example.com/bar");

        final Date now = new Date();
        final Date requestSent = new Date(now.getTime() - 3 * 1000L);
        final Date responseGenerated = new Date(now.getTime() - 2 * 1000L);
        final Date responseReceived = new Date(now.getTime() - 1 * 1000L);

        final ClassicHttpResponse originResponse = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        originResponse.setEntity(HttpTestUtils.makeBody(CacheConfig.DEFAULT_MAX_OBJECT_SIZE_BYTES - 1));
        originResponse.setHeader("Cache-Control","public, max-age=3600");
        originResponse.setHeader("Date", DateUtils.formatDate(responseGenerated));
        originResponse.setHeader("ETag", "\"etag\"");

        final HttpCacheEntry httpCacheEntry = HttpTestUtils.makeCacheEntry();
        final SimpleHttpResponse response = SimpleHttpResponse.create(HttpStatus.SC_OK);

        EasyMock.expect(mockCache.createCacheEntry(
                eq(host),
                same(request),
                same(originResponse),
                isA(ByteArrayBuffer.class),
                eq(requestSent),
                eq(responseReceived))).andReturn(httpCacheEntry).once();
        EasyMock.expect(mockResponseGenerator.generateResponse(
                same(request),
                same(httpCacheEntry))).andReturn(response).once();
        replayMocks();

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, mockEndpoint, context);
        impl.cacheAndReturnResponse(host, request, originResponse, scope, requestSent, responseReceived);

        verifyMocks();
    }

    @Test
    public void testIfOnlyIfCachedAndNoCacheEntryBackendNotCalled() throws Exception {
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);

        request.addHeader("Cache-Control", "only-if-cached");

        final ClassicHttpResponse resp = execute(request);

        Assert.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, resp.getCode());
    }

    @Test
    public void testIfOnlyIfCachedAndEntryNotSuitableBackendNotCalled() throws Exception {

        request.setHeader("Cache-Control", "only-if-cached");

        entry = HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("Cache-Control",
            "must-revalidate") });

        requestIsFatallyNonCompliant(null);
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(entry);
        cacheEntrySuitable(false);

        replayMocks();
        final ClassicHttpResponse resp = execute(request);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, resp.getCode());
    }

    @Test
    public void testIfOnlyIfCachedAndEntryExistsAndIsSuitableReturnsEntry() throws Exception {

        request.setHeader("Cache-Control", "only-if-cached");

        requestIsFatallyNonCompliant(null);
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(entry);
        cacheEntrySuitable(true);
        responseIsGeneratedFromCache(SimpleHttpResponse.create(HttpStatus.SC_OK));
        entryHasStaleness(TimeValue.ZERO_MILLISECONDS);

        replayMocks();
        final ClassicHttpResponse resp = execute(request);
        verifyMocks();
    }

    @Test
    public void testDoesNotSetConnectionInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, ctx), backend);
    }

    @Test
    public void testSetsTargetHostInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, ctx), backend);
    }

    @Test
    public void testSetsRouteInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, ctx), backend);
        assertEquals(route, ctx.getHttpRoute());
    }

    @Test
    public void testSetsRequestInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, ctx), backend);
        if (!HttpTestUtils.equivalent(request, ctx.getRequest())) {
            assertSame(request, ctx.getRequest());
        }
    }

    @Test
    public void testSetsResponseInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        final ClassicHttpResponse result = impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, ctx), null);
        if (!HttpTestUtils.equivalent(result, ctx.getResponse())) {
            assertSame(result, ctx.getResponse());
        }
    }

    @Test
    public void testSetsRequestSentInContextOnCacheHit() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, ctx), backend);
    }

    @Test
    public void testCanCacheAResponseWithoutABody() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        response.setHeader("Date", DateUtils.formatDate(new Date()));
        response.setHeader("Cache-Control", "max-age=300");
        final DummyBackend backend = new DummyBackend();
        backend.setResponse(response);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        assertEquals(1, backend.getExecutions());
    }

    @Test
    public void testNoEntityForIfNoneMatchRequestNotYetInCache() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        req1.addHeader("If-None-Match", "\"etag\"");

        final ClassicHttpResponse resp1 = HttpTestUtils.make304Response();
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);
        replayMocks();
        final ClassicHttpResponse result = execute(req1);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        assertNull("The 304 response messages MUST NOT contain a message-body", result.getEntity());
    }

    @Test
    public void testNotModifiedResponseUpdatesCacheEntryWhenNoEntity() throws Exception {

        final Date now = new Date();

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        req1.addHeader("If-None-Match", "etag");

        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-None-Match", "etag");

        final ClassicHttpResponse resp1 = HttpTestUtils.make304Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=0");
        resp1.setHeader("Etag", "etag");

        final ClassicHttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "max-age=0");
        resp1.setHeader("Etag", "etag");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        replayMocks();
        final ClassicHttpResponse result1 = execute(req1);
        final ClassicHttpResponse result2 = execute(req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getCode());
        assertEquals("etag", result1.getFirstHeader("Etag").getValue());
        assertEquals(HttpStatus.SC_NOT_MODIFIED, result2.getCode());
        assertEquals("etag", result2.getFirstHeader("Etag").getValue());
    }

    @Test
    public void testNotModifiedResponseWithVaryUpdatesCacheEntryWhenNoEntity() throws Exception {

        final Date now = new Date();

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        req1.addHeader("If-None-Match", "etag");

        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-None-Match", "etag");

        final ClassicHttpResponse resp1 = HttpTestUtils.make304Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=0");
        resp1.setHeader("Etag", "etag");
        resp1.setHeader("Vary", "Accept-Encoding");

        final ClassicHttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "max-age=0");
        resp1.setHeader("Etag", "etag");
        resp1.setHeader("Vary", "Accept-Encoding");

        backendExpectsAnyRequestAndReturn(resp1);
        backendExpectsAnyRequestAndReturn(resp2);
        replayMocks();
        final ClassicHttpResponse result1 = execute(req1);
        final ClassicHttpResponse result2 = execute(req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getCode());
        assertEquals("etag", result1.getFirstHeader("Etag").getValue());
        assertEquals(HttpStatus.SC_NOT_MODIFIED, result2.getCode());
        assertEquals("etag", result2.getFirstHeader("Etag").getValue());
    }

    @Test
    public void testDoesNotSend304ForNonConditionalRequest() throws Exception {

        final Date now = new Date();
        final Date inOneMinute = new Date(System.currentTimeMillis() + 60000);

        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        req1.addHeader("If-None-Match", "etag");

        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = HttpTestUtils.make304Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "public, max-age=60");
        resp1.setHeader("Expires", DateUtils.formatDate(inOneMinute));
        resp1.setHeader("Etag", "etag");
        resp1.setHeader("Vary", "Accept-Encoding");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK,
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
        final ClassicHttpResponse result1 = execute(req1);
        final ClassicHttpResponse result2 = execute(req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getCode());
        assertNull(result1.getEntity());
        assertEquals(HttpStatus.SC_OK, result2.getCode());
        Assert.assertNotNull(result2.getEntity());
    }

    @Test
    public void testUsesVirtualHostForCacheKey() throws Exception {
        final DummyBackend backend = new DummyBackend();
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        backend.setResponse(response);
        impl = createCachingExecChain(new BasicHttpCache(), CacheConfig.DEFAULT);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        assertEquals(1, backend.getExecutions());
        request.setAuthority(new URIAuthority("bar.example.com"));
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        assertEquals(2, backend.getExecutions());
        impl.execute(request, new ExecChain.Scope("test", route, request, mockEndpoint, context), backend);
        assertEquals(2, backend.getExecutions());
    }

    protected IExpectationSetters<ClassicHttpResponse> backendExpectsRequestAndReturn(
            final ClassicHttpRequest request, final ClassicHttpResponse response) throws Exception {
        final ClassicHttpResponse resp = mockExecChain.proceed(
                EasyMock.eq(request), EasyMock.isA(ExecChain.Scope.class));
        return EasyMock.expect(resp).andReturn(response);
    }

    private IExpectationSetters<ClassicHttpResponse> backendExpectsAnyRequestAndReturn(
        final ClassicHttpResponse response) throws Exception {
        final ClassicHttpResponse resp = mockExecChain.proceed(
            EasyMock.isA(ClassicHttpRequest.class), EasyMock.isA(ExecChain.Scope.class));
        return EasyMock.expect(resp).andReturn(response);
    }

    protected IExpectationSetters<ClassicHttpResponse> backendExpectsAnyRequestAndThrows(
        final Throwable throwable) throws Exception {
        final ClassicHttpResponse resp = mockExecChain.proceed(
            EasyMock.isA(ClassicHttpRequest.class), EasyMock.isA(ExecChain.Scope.class));
        return EasyMock.expect(resp).andThrow(throwable);
    }

    protected IExpectationSetters<ClassicHttpResponse> backendCaptureRequestAndReturn(
            final Capture<ClassicHttpRequest> cap, final ClassicHttpResponse response) throws Exception {
        final ClassicHttpResponse resp = mockExecChain.proceed(
            EasyMock.capture(cap), EasyMock.isA(ExecChain.Scope.class));
        return EasyMock.expect(resp).andReturn(response);
    }

    protected void getCacheEntryReturns(final HttpCacheEntry result) throws IOException {
        expect(mockCache.getCacheEntry(eq(host), eqRequest(request))).andReturn(result);
    }

    private void cacheInvalidatorWasCalled() throws IOException {
        mockCache.flushCacheEntriesInvalidatedByRequest((HttpHost) anyObject(), (HttpRequest) anyObject());
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

    protected void conditionalRequestBuilderReturns(final ClassicHttpRequest validate) throws Exception {
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

    private void entryHasStaleness(final TimeValue staleness) {
        expect(
            mockValidityPolicy.getStaleness((HttpCacheEntry) anyObject(), (Date) anyObject()))
            .andReturn(staleness);
    }

    protected void responseIsGeneratedFromCache(final SimpleHttpResponse cachedResponse) throws IOException {
        expect(
            mockResponseGenerator.generateResponse(
                    (ClassicHttpRequest) anyObject(),
                    (HttpCacheEntry) anyObject())).andReturn(cachedResponse);
    }

    protected void doesNotFlushCache() throws IOException {
        mockCache.flushCacheEntriesInvalidatedByRequest(isA(HttpHost.class), isA(HttpRequest.class));
        EasyMock.expectLastCall().andThrow(new AssertionError("flushCacheEntriesInvalidByResponse should not have been called")).anyTimes();
    }

}
