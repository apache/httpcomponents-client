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

import java.io.IOException;
import java.net.URI;
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
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.easymock.Capture;
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCachingHttpClient {

    private static final String GET_CURRENT_DATE = "getCurrentDate";

    private static final String HANDLE_BACKEND_RESPONSE = "handleBackendResponse";

    private static final String CALL_BACKEND = "callBackend";

    private static final String REVALIDATE_CACHE_ENTRY = "revalidateCacheEntry";

    private CachingHttpClient impl;
    private boolean mockedImpl;

    private CacheValidityPolicy mockValidityPolicy;
    private CacheableRequestPolicy mockRequestPolicy;
    private HttpClient mockBackend;
    private HttpCache mockCache;
    private HttpCacheStorage mockStorage;
    private CachedResponseSuitabilityChecker mockSuitabilityChecker;
    private ResponseCachingPolicy mockResponsePolicy;
    private HttpResponse mockBackendResponse;
    private HttpCacheEntry mockCacheEntry;
    private CachedHttpResponseGenerator mockResponseGenerator;
    private ClientConnectionManager mockConnectionManager;
    private ResponseHandler<Object> mockHandler;
    private HttpUriRequest mockUriRequest;
    private HttpResponse mockCachedResponse;
    private ConditionalRequestBuilder mockConditionalRequestBuilder;
    private HttpRequest mockConditionalRequest;
    private StatusLine mockStatusLine;
    private ResponseProtocolCompliance mockResponseProtocolCompliance;
    private RequestProtocolCompliance mockRequestProtocolCompliance;

    private Date requestDate;
    private Date responseDate;
    private HttpHost host;
    private HttpRequest request;
    private HttpContext context;
    private HttpParams params;
    private HttpCacheEntry entry;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        mockRequestPolicy = createMock(CacheableRequestPolicy.class);
        mockValidityPolicy = createMock(CacheValidityPolicy.class);
        mockBackend = createMock(HttpClient.class);
        mockCache = createMock(HttpCache.class);
        mockSuitabilityChecker = createMock(CachedResponseSuitabilityChecker.class);
        mockResponsePolicy = createMock(ResponseCachingPolicy.class);
        mockConnectionManager = createMock(ClientConnectionManager.class);
        mockHandler = createMock(ResponseHandler.class);
        mockBackendResponse = createMock(HttpResponse.class);
        mockUriRequest = createMock(HttpUriRequest.class);
        mockCacheEntry = createMock(HttpCacheEntry.class);
        mockResponseGenerator = createMock(CachedHttpResponseGenerator.class);
        mockCachedResponse = createMock(HttpResponse.class);
        mockConditionalRequestBuilder = createMock(ConditionalRequestBuilder.class);
        mockConditionalRequest = createMock(HttpRequest.class);
        mockStatusLine = createMock(StatusLine.class);
        mockResponseProtocolCompliance = createMock(ResponseProtocolCompliance.class);
        mockRequestProtocolCompliance = createMock(RequestProtocolCompliance.class);
        mockStorage = createMock(HttpCacheStorage.class);

        requestDate = new Date(System.currentTimeMillis() - 1000);
        responseDate = new Date();
        host = new HttpHost("foo.example.com");
        request = new BasicHttpRequest("GET", "/stuff", HttpVersion.HTTP_1_1);
        context = new BasicHttpContext();
        params = new BasicHttpParams();
        entry = HttpTestUtils.makeCacheEntry();
        impl = new CachingHttpClient(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance);
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
        replay(mockConnectionManager);
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
        verify(mockConnectionManager);
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
        impl = new CachingHttpClient(mockBackend);
        
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        
        expect(mockBackend.execute(isA(HttpHost.class), isA(HttpRequest.class),
                (HttpContext)isNull())).andReturn(resp1);
        
        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        
        replayMocks();
        impl.execute(host, req1);
        impl.execute(host, req2);
        verifyMocks();
    }

    @Test
    public void testOlderCacheableResponsesDoNotGoIntoCache() throws Exception {
        impl = new CachingHttpClient(mockBackend);
        Date now = new Date();
        Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);
        
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"new-etag\"");

        expect(mockBackend.execute(isA(HttpHost.class), isA(HttpRequest.class),
                (HttpContext)isNull())).andReturn(resp1);
        
        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control","no-cache");
        HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"old-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp2.setHeader("Cache-Control","max-age=3600");

        expect(mockBackend.execute(isA(HttpHost.class), isA(HttpRequest.class),
                (HttpContext)isNull())).andReturn(resp2);

        HttpRequest req3 = HttpTestUtils.makeDefaultRequest();

        replayMocks();
        impl.execute(host, req1);
        impl.execute(host, req2);
        HttpResponse result = impl.execute(host, req3);
        verifyMocks();

        assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }

    @Test
    public void testNewerCacheableResponsesReplaceExistingCacheEntry() throws Exception {
        impl = new CachingHttpClient(mockBackend);
        Date now = new Date();
        Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);
        
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"old-etag\"");

        expect(mockBackend.execute(isA(HttpHost.class), isA(HttpRequest.class),
                (HttpContext)isNull())).andReturn(resp1);
        
        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control","max-age=0");
        HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"new-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control","max-age=3600");

        expect(mockBackend.execute(isA(HttpHost.class), isA(HttpRequest.class),
                (HttpContext)isNull())).andReturn(resp2);

        HttpRequest req3 = HttpTestUtils.makeDefaultRequest();

        replayMocks();
        impl.execute(host, req1);
        impl.execute(host, req2);
        HttpResponse result = impl.execute(host, req3);
        verifyMocks();

        assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }


    @Test
    public void testRequestThatCannotBeServedFromCacheCausesBackendRequest() throws Exception {
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(false);
        mockImplMethods(CALL_BACKEND);

        callBackendReturnsResponse(mockBackendResponse);
        requestProtocolValidationIsCalled();
        requestIsFatallyNonCompliant(null);

        replayMocks();
        HttpResponse result = impl.execute(host, request, context);
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

        requestProtocolValidationIsCalled();
        requestIsFatallyNonCompliant(null);

        callBackendReturnsResponse(mockBackendResponse);

        replayMocks();
        HttpResponse result = impl.execute(host, request, context);
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
        requestProtocolValidationIsCalled();
        requestIsFatallyNonCompliant(null);

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(false);
        callBackendReturnsResponse(mockBackendResponse);

        replayMocks();
        HttpResponse result = impl.execute(host, request, context);
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
        requestProtocolValidationIsCalled();
        requestIsFatallyNonCompliant(null);

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(true);
        cacheEntryMustRevalidate(false);
        cacheEntryProxyRevalidate(false);
        mayReturnStaleWhileRevalidating(false);
        revalidateCacheEntryReturns(mockBackendResponse);

        replayMocks();
        HttpResponse result = impl.execute(host, request, context);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(1, impl.getCacheHits());
        Assert.assertEquals(0, impl.getCacheUpdates());
    }

    @Test
    public void testRevalidationCallsHandleBackEndResponseWhenNot200Or304() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);

        HttpRequest validate =
            new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse originResponse =
            new BasicHttpResponse(HttpVersion.HTTP_1_1,
                    HttpStatus.SC_NOT_FOUND, "Not Found");
        HttpResponse finalResponse = HttpTestUtils.make200Response();

        conditionalRequestBuilderReturns(validate);
        getCurrentDateReturns(requestDate);
        backendCall(validate, originResponse);
        getCurrentDateReturns(responseDate);
        expect(impl.handleBackendResponse(host, validate,
                requestDate, responseDate, originResponse))
            .andReturn(finalResponse);

        replayMocks();
        HttpResponse result =
            impl.revalidateCacheEntry(host, request, context, entry);
        verifyMocks();

        Assert.assertSame(finalResponse, result);
    }

    @Test
    public void testRevalidationUpdatesCacheEntryAndPutsItToCacheWhen304ReturningCachedResponse()
            throws Exception {

        mockImplMethods(GET_CURRENT_DATE);

        HttpRequest validate =
            new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse originResponse =
            new BasicHttpResponse(HttpVersion.HTTP_1_1,
                    HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        HttpCacheEntry updatedEntry = HttpTestUtils.makeCacheEntry();

        conditionalRequestBuilderReturns(validate);
        getCurrentDateReturns(requestDate);
        backendCall(validate, originResponse);
        getCurrentDateReturns(responseDate);
        expect(mockCache.updateCacheEntry(host, request,
                entry, originResponse, requestDate, responseDate))
            .andReturn(updatedEntry);
        expect(mockSuitabilityChecker.isConditional(request)).andReturn(false);
        responseIsGeneratedFromCache();

        replayMocks();
        impl.revalidateCacheEntry(host, request, context, entry);
        verifyMocks();
    }

    @Test
    public void testSuitableCacheEntryDoesNotCauseBackendRequest() throws Exception {
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        requestProtocolValidationIsCalled();
        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(true);
        responseIsGeneratedFromCache();
        requestIsFatallyNonCompliant(null);
        entryHasStaleness(0L);

        replayMocks();
        HttpResponse result = impl.execute(host, request, context);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, result);
    }

    @Test
    public void testCallBackendMakesBackEndRequestAndHandlesResponse() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        getCurrentDateReturns(requestDate);
        backendCallWasMade(request, resp);
        getCurrentDateReturns(responseDate);
        handleBackendResponseReturnsResponse(request, resp);

        replayMocks();

        impl.callBackend(host, request, context);

        verifyMocks();
    }

    @Test
    public void testNonCacheableResponseIsNotCachedAndIsReturnedAsIs() throws Exception {
        CacheConfig config = new CacheConfig();
        impl = new CachingHttpClient(mockBackend,
                new BasicHttpCache(new HeapResourceFactory(), mockStorage, config),
                config);
        
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","no-cache");

        expect(mockStorage.getEntry(isA(String.class))).andReturn(null).anyTimes();
        mockStorage.removeEntry(isA(String.class));
        expectLastCall().anyTimes();
        expect(mockBackend.execute(isA(HttpHost.class), isA(HttpRequest.class),
                (HttpContext)isNull())).andReturn(resp1);

        replayMocks();
        HttpResponse result = impl.execute(host, req1);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, result));
    }


    @Test
    public void testCallsSelfForExecuteOnHostRequestWithNullContext() throws Exception {
        final Counter c = new Counter();
        final HttpHost theHost = host;
        final HttpRequest theRequest = request;
        final HttpResponse theResponse = mockBackendResponse;
        impl = new CachingHttpClient(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance) {
            @Override
            public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context) {
                Assert.assertSame(theHost, target);
                Assert.assertSame(theRequest, request);
                Assert.assertNull(context);
                c.incr();
                return theResponse;
            }
        };

        replayMocks();
        HttpResponse result = impl.execute(host, request);
        verifyMocks();
        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(1, c.getCount());
    }

    @Test
    public void testCallsSelfWithDefaultContextForExecuteOnHostRequestWithHandler()
            throws Exception {

        final Counter c = new Counter();
        final HttpHost theHost = host;
        final HttpRequest theRequest = request;
        final HttpResponse theResponse = mockBackendResponse;
        final ResponseHandler<Object> theHandler = mockHandler;
        final Object value = new Object();
        impl = new CachingHttpClient(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance) {
            @Override
            public <T> T execute(HttpHost target, HttpRequest request,
                                 ResponseHandler<? extends T> rh, HttpContext context) {
                Assert.assertSame(theHost, target);
                Assert.assertSame(theRequest, request);
                Assert.assertSame(theHandler, rh);
                Assert.assertNull(context);
                c.incr();
                try {
                    return rh.handleResponse(theResponse);
                } catch (Exception wrong) {
                    throw new RuntimeException("unexpected exn", wrong);
                }
            }
        };

        expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
                value);

        replayMocks();
        Object result = impl.execute(host, request, mockHandler);
        verifyMocks();

        Assert.assertSame(value, result);
        Assert.assertEquals(1, c.getCount());
    }

    @Test
    public void testCallsSelfOnExecuteHostRequestWithHandlerAndContext() throws Exception {

        final Counter c = new Counter();
        final HttpHost theHost = host;
        final HttpRequest theRequest = request;
        final HttpResponse theResponse = mockBackendResponse;
        final HttpContext theContext = context;
        impl = new CachingHttpClient(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance) {
            @Override
            public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context) {
                Assert.assertSame(theHost, target);
                Assert.assertSame(theRequest, request);
                Assert.assertSame(theContext, context);
                c.incr();
                return theResponse;
            }
        };

        final Object theObject = new Object();

        expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
                theObject);

        replayMocks();
        Object result = impl.execute(host, request, mockHandler, context);
        verifyMocks();
        Assert.assertEquals(1, c.getCount());
        Assert.assertSame(theObject, result);
    }

    @Test
    public void testCallsSelfWithNullContextOnExecuteUriRequest() throws Exception {
        final Counter c = new Counter();
        final HttpUriRequest theRequest = mockUriRequest;
        final HttpResponse theResponse = mockBackendResponse;
        impl = new CachingHttpClient(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance) {
            @Override
            public HttpResponse execute(HttpUriRequest request, HttpContext context) {
                Assert.assertSame(theRequest, request);
                Assert.assertNull(context);
                c.incr();
                return theResponse;
            }
        };

        replayMocks();
        HttpResponse result = impl.execute(mockUriRequest);
        verifyMocks();

        Assert.assertEquals(1, c.getCount());
        Assert.assertSame(theResponse, result);
    }

    @Test
    public void testCallsSelfWithExtractedHostOnExecuteUriRequestWithContext() throws Exception {

        final URI uri = new URI("sch://host:8888");
        final Counter c = new Counter();
        final HttpRequest theRequest = mockUriRequest;
        final HttpContext theContext = context;
        final HttpResponse theResponse = mockBackendResponse;
        impl = new CachingHttpClient(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance) {
            @Override
            public HttpResponse execute(HttpHost hh, HttpRequest req, HttpContext ctx) {
                Assert.assertEquals("sch", hh.getSchemeName());
                Assert.assertEquals("host", hh.getHostName());
                Assert.assertEquals(8888, hh.getPort());
                Assert.assertSame(theRequest, req);
                Assert.assertSame(theContext, ctx);
                c.incr();
                return theResponse;
            }
        };

        expect(mockUriRequest.getURI()).andReturn(uri);

        replayMocks();
        HttpResponse result = impl.execute(mockUriRequest, context);
        verifyMocks();

        Assert.assertEquals(1, c.getCount());
        Assert.assertSame(mockBackendResponse, result);
    }

    @Test
    public void testCallsSelfWithNullContextOnExecuteUriRequestWithHandler() throws Exception {
        final Counter c = new Counter();
        final HttpUriRequest theRequest = mockUriRequest;
        final HttpResponse theResponse = mockBackendResponse;
        final Object theValue = new Object();
        impl = new CachingHttpClient(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance) {
            @Override
            public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler,
                                 HttpContext context) throws IOException {
                Assert.assertSame(theRequest, request);
                Assert.assertNull(context);
                c.incr();
                return handler.handleResponse(theResponse);
            }
        };

        expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
                theValue);

        replayMocks();
        Object result = impl.execute(mockUriRequest, mockHandler);
        verifyMocks();

        Assert.assertEquals(1, c.getCount());
        Assert.assertSame(theValue, result);
    }

    @Test
    public void testCallsSelfAndRunsHandlerOnExecuteUriRequestWithHandlerAndContext()
            throws Exception {

        final Counter c = new Counter();
        final HttpUriRequest theRequest = mockUriRequest;
        final HttpContext theContext = context;
        final HttpResponse theResponse = mockBackendResponse;
        final Object theValue = new Object();
        impl = new CachingHttpClient(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance) {
            @Override
            public HttpResponse execute(HttpUriRequest request, HttpContext context)
                    throws IOException {
                Assert.assertSame(theRequest, request);
                Assert.assertSame(theContext, context);
                c.incr();
                return theResponse;
            }
        };

        expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
                theValue);

        replayMocks();
        Object result = impl.execute(mockUriRequest, mockHandler, context);
        verifyMocks();
        Assert.assertEquals(1, c.getCount());
        Assert.assertSame(theValue, result);
    }

    @Test
    public void testUsesBackendsConnectionManager() {
        expect(mockBackend.getConnectionManager()).andReturn(
                mockConnectionManager);
        replayMocks();
        ClientConnectionManager result = impl.getConnectionManager();
        verifyMocks();
        Assert.assertSame(result, mockConnectionManager);
    }

    @Test
    public void testUsesBackendsHttpParams() {
        expect(mockBackend.getParams()).andReturn(params);
        replayMocks();
        HttpParams result = impl.getParams();
        verifyMocks();
        Assert.assertSame(params, result);
    }

    @Test
    public void testResponseIsGeneratedWhenCacheEntryIsUsable() throws Exception {

        requestIsFatallyNonCompliant(null);
        requestProtocolValidationIsCalled();
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        cacheEntrySuitable(true);
        getCacheEntryReturns(mockCacheEntry);
        responseIsGeneratedFromCache();
        entryHasStaleness(0L);

        replayMocks();
        impl.execute(host, request, context);
        verifyMocks();
    }

    @Test
    public void testNonCompliantRequestWrapsAndReThrowsProtocolException() throws Exception {

        ClientProtocolException expected = new ClientProtocolException("ouch");

        requestIsFatallyNonCompliant(null);
        requestCannotBeMadeCompliantThrows(expected);

        boolean gotException = false;
        replayMocks();
        try {
            impl.execute(host, request, context);
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
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req = new BasicHttpRequest("OPTIONS","*",HttpVersion.HTTP_1_1);
        req.setHeader("Max-Forwards","0");

        impl.execute(host, req, context);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
                context.getAttribute(CachingHttpClient.CACHE_RESPONSE_STATUS));
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForFatallyNoncompliantRequest()
        throws Exception {
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Range","bytes=0-50");
        req.setHeader("If-Range","W/\"weak-etag\"");

        impl.execute(host, req, context);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
                context.getAttribute(CachingHttpClient.CACHE_RESPONSE_STATUS));
    }

    @Test
    public void testRecordsClientProtocolInViaHeaderIfRequestNotServableFromCache()
        throws Exception {
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        req.setHeader("Cache-Control","no-cache");
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");
        Capture<HttpRequest> cap = new Capture<HttpRequest>();

        expect(mockBackend.execute(isA(HttpHost.class),
                capture(cap), isA(HttpContext.class)))
            .andReturn(resp);

        replayMocks();
        impl.execute(host, req, context);
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
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Cache-Control","no-cache");
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp);

        replayMocks();
        impl.execute(host, req, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_MISS,
                context.getAttribute(CachingHttpClient.CACHE_RESPONSE_STATUS));
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestNotServableFromCache()
            throws Exception {
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Cache-Control","no-cache");
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), (HttpContext)isNull()))
                .andReturn(resp);

        replayMocks();
        HttpResponse result = impl.execute(host, req);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsViaHeaderOnResponseForCacheMiss()
        throws Exception {
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control","public, max-age=3600");

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp1);

        replayMocks();
        HttpResponse result = impl.execute(host, req1, new BasicHttpContext());
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsCacheHitContextIfRequestServedFromCache()
        throws Exception {
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control","public, max-age=3600");

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp1);

        replayMocks();
        impl.execute(host, req1, new BasicHttpContext());
        impl.execute(host, req2, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT,
                context.getAttribute(CachingHttpClient.CACHE_RESPONSE_STATUS));
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestServedFromCache()
        throws Exception {
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control","public, max-age=3600");

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), (HttpContext)isNull()))
            .andReturn(resp1);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfModifiedSinceHeaderIfRequestServedFromCache()
            throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsLess() throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);
        expect(
                mockBackend.execute(isA(HttpHost.class),
                        isA(HttpRequest.class),
                        (HttpContext) isNull()))
                        .andReturn(resp2);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsInvalid()
            throws Exception {
        Date now = new Date();
        Date tenSecondsAfter = new Date(now.getTime() + 10 * 1000L);
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1).times(2);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderIfRequestServedFromCache()
            throws Exception {
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-None-Match", "*");
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFails() throws Exception {
        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        req2.addHeader("If-None-Match", "\"abc\"");

        HttpResponse resp2 = HttpTestUtils.make200Response();

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);
        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp2);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderAndIfModifiedSinceIfRequestServedFromCache()
            throws Exception {
        impl = new CachingHttpClient(mockBackend);
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFailsIfModifiedSinceIgnored()
            throws Exception {
        impl = new CachingHttpClient(mockBackend);
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");
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

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);
        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());

    }

    @Test
    public void testSetsValidatedContextIfRequestWasSuccessfullyValidated()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp1);
        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp2);

        replayMocks();
        impl.execute(host, req1, new BasicHttpContext());
        impl.execute(host, req2, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.VALIDATED,
                context.getAttribute(CachingHttpClient.CACHE_RESPONSE_STATUS));
    }

    @Test
    public void testSetsViaHeaderIfRequestWasSuccessfullyValidated()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp1);
        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp2);

        replayMocks();
        impl.execute(host, req1, new BasicHttpContext());
        HttpResponse result = impl.execute(host, req2, context);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }


    @Test
    public void testSetsModuleResponseContextIfValidationRequiredButFailed()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public, max-age=5, must-revalidate");

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp1);
        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andThrow(new IOException());

        replayMocks();
        impl.execute(host, req1, new BasicHttpContext());
        impl.execute(host, req2, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
                context.getAttribute(CachingHttpClient.CACHE_RESPONSE_STATUS));
    }

    @Test
    public void testSetsModuleResponseContextIfValidationFailsButNotRequired()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public, max-age=5");

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp1);
        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andThrow(new IOException());

        replayMocks();
        impl.execute(host, req1, new BasicHttpContext());
        impl.execute(host, req2, context);
        verifyMocks();
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT,
                context.getAttribute(CachingHttpClient.CACHE_RESPONSE_STATUS));
    }

    @Test
    public void testSetViaHeaderIfValidationFailsButNotRequired()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length","128");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public, max-age=5");

        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andReturn(resp1);
        expect(mockBackend.execute(isA(HttpHost.class),
                isA(HttpRequest.class), isA(HttpContext.class)))
            .andThrow(new IOException());

        replayMocks();
        impl.execute(host, req1, new BasicHttpContext());
        HttpResponse result = impl.execute(host, req2, context);
        verifyMocks();
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfNoneMatchPassesIfRequestServedFromOrigin()
            throws Exception {

        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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
        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp2);
        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns200ForIfNoneMatchFailsIfRequestServedFromOrigin()
            throws Exception {

        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        impl = new CachingHttpClient(mockBackend);
        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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
        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp2);
        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns304ForIfModifiedSincePassesIfRequestServedFromOrigin()
            throws Exception {
        impl = new CachingHttpClient(mockBackend);

        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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
        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp2);
        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testReturns200ForIfModifiedSinceFailsIfRequestServedFromOrigin()
            throws Exception {
        impl = new CachingHttpClient(mockBackend);
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        HttpRequest req1 = new HttpGet("http://foo.example.com/");
        HttpRequest req2 = new HttpGet("http://foo.example.com/");

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
        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp2);
        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
    }

    @Test
    public void testVariantMissServerIfReturns304CacheReturns200() throws Exception {
        impl = new CachingHttpClient(mockBackend);
        Date now = new Date();

        HttpRequest req1 = new HttpGet("http://foo.example.com");
        req1.addHeader("Accept-Encoding", "gzip");

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Etag", "\"gzip_etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        HttpRequest req2 = new HttpGet("http://foo.example.com");
        req2.addHeader("Accept-Encoding", "deflate");

        HttpRequest req2Server = new HttpGet("http://foo.example.com");
        req2Server.addHeader("Accept-Encoding", "deflate");
        req2Server.addHeader("If-None-Match", "\"gzip_etag\"");

        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("Etag", "\"deflate_etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Cache-Control", "public, max-age=3600");

        HttpRequest req3 = new HttpGet("http://foo.example.com");
        req3.addHeader("Accept-Encoding", "gzip,deflate");

        HttpRequest req3Server = new HttpGet("http://foo.example.com");
        req3Server.addHeader("Accept-Encoding", "gzip,deflate");
        req3Server.addHeader("If-None-Match", "\"gzip_etag\",\"deflate_etag\"");

        HttpResponse resp3 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp3.setEntity(HttpTestUtils.makeBody(128));
        resp3.setHeader("Content-Length", "128");
        resp3.setHeader("Etag", "\"gzip_etag\"");
        resp3.setHeader("Date", DateUtils.formatDate(now));
        resp3.setHeader("Vary", "Accept-Encoding");
        resp3.setHeader("Cache-Control", "public, max-age=3600");

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp2);

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp3);


        replayMocks();
        HttpResponse result1 = impl.execute(host, req1);

        HttpResponse result2 = impl.execute(host, req2);

        HttpResponse result3 = impl.execute(host, req3);

        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result1.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result3.getStatusLine().getStatusCode());
    }

    @Test
    public void testVariantsMissServerReturns304CacheReturns304() throws Exception {
        impl = new CachingHttpClient(mockBackend);
        Date now = new Date();

        HttpRequest req1 = new HttpGet("http://foo.example.com");
        req1.addHeader("Accept-Encoding", "gzip");

        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Etag", "\"gzip_etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        HttpRequest req2 = new HttpGet("http://foo.example.com");
        req2.addHeader("Accept-Encoding", "deflate");

        HttpRequest req2Server = new HttpGet("http://foo.example.com");
        req2Server.addHeader("Accept-Encoding", "deflate");
        req2Server.addHeader("If-None-Match", "\"gzip_etag\"");

        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("Etag", "\"deflate_etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Cache-Control", "public, max-age=3600");

        HttpRequest req4 = new HttpGet("http://foo.example.com");
        req4.addHeader("Accept-Encoding", "gzip,identity");
        req4.addHeader("If-None-Match", "\"gzip_etag\"");

        HttpRequest req4Server = new HttpGet("http://foo.example.com");
        req4Server.addHeader("Accept-Encoding", "gzip,identity");
        req4Server.addHeader("If-None-Match", "\"gzip_etag\"");

        HttpResponse resp4 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp4.setHeader("Etag", "\"gzip_etag\"");
        resp4.setHeader("Date", DateUtils.formatDate(now));
        resp4.setHeader("Vary", "Accept-Encoding");
        resp4.setHeader("Cache-Control", "public, max-age=3600");

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp1);

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp2);

        expect(
                mockBackend.execute(isA(HttpHost.class), 
                        isA(HttpRequest.class), (HttpContext) 
                        isNull())).andReturn(resp4);

        replayMocks();
        HttpResponse result1 = impl.execute(host, req1);

        HttpResponse result2 = impl.execute(host, req2);

        HttpResponse result4 = impl.execute(host, req4);
        verifyMocks();
        Assert.assertEquals(HttpStatus.SC_OK, result1.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getStatusLine().getStatusCode());
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result4.getStatusLine().getStatusCode());

    }

    @Test
    public void testIsSharedCache() {
        Assert.assertTrue(impl.isSharedCache());
    }

    @Test
    public void testTreatsCacheIOExceptionsAsCacheMiss()
        throws Exception {

        impl = new CachingHttpClient(mockBackend, mockCache, new CacheConfig());
        HttpResponse resp = HttpTestUtils.make200Response();

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
        expect(mockBackend.execute(same(host),
                isA(HttpRequest.class), (HttpContext)isNull()))
            .andReturn(resp);

        replayMocks();
        HttpResponse result = impl.execute(host, request);
        verifyMocks();
        Assert.assertSame(resp, result);
    }

    @Test
    public void testIfOnlyIfCachedAndNoCacheEntryBackendNotCalled() throws IOException {
        impl = new CachingHttpClient(mockBackend);

        request.addHeader("Cache-Control", "only-if-cached");

        HttpResponse resp = impl.execute(host, request);

        Assert.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, resp.getStatusLine().getStatusCode());
    }

    @Test
    public void testIfOnlyIfCachedAndEntryNotSuitableBackendNotCalled() throws Exception {

        request.setHeader("Cache-Control", "only-if-cached");

        entry = HttpTestUtils.makeCacheEntry(new Header[]{new BasicHeader("Cache-Control", "must-revalidate")});

        requestIsFatallyNonCompliant(null);
        requestProtocolValidationIsCalled();
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(entry);
        cacheEntrySuitable(false);

        replayMocks();
        HttpResponse resp = impl.execute(host, request);
        verifyMocks();

        Assert.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, resp.getStatusLine().getStatusCode());
    }

    @Test
    public void testIfOnlyIfCachedAndEntryExistsAndIsSuitableReturnsEntry() throws Exception {

        request.setHeader("Cache-Control", "only-if-cached");

        requestIsFatallyNonCompliant(null);
        requestProtocolValidationIsCalled();
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(entry);
        cacheEntrySuitable(true);
        responseIsGeneratedFromCache();
        entryHasStaleness(0);

        replayMocks();
        HttpResponse resp = impl.execute(host, request);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, resp);
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

    private void callBackendReturnsResponse(HttpResponse response) throws IOException {
        expect(impl.callBackend(
                (HttpHost)anyObject(),
                (HttpRequest)anyObject(),
                (HttpContext)anyObject())).andReturn(response);
    }

    private void revalidateCacheEntryReturns(HttpResponse response) throws IOException,
            ProtocolException {
        expect(
                impl.revalidateCacheEntry(
                        (HttpHost)anyObject(),
                        (HttpRequest)anyObject(),
                        (HttpContext)anyObject(),
                        (HttpCacheEntry)anyObject())).andReturn(response);
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

    private void conditionalRequestBuilderReturns(HttpRequest validate)
            throws Exception {
        expect(mockConditionalRequestBuilder
        .buildConditionalRequest(request, entry))
    .andReturn(validate);
}

    private void getCurrentDateReturns(Date date) {
        expect(impl.getCurrentDate()).andReturn(date);
    }

    private void requestPolicyAllowsCaching(boolean allow) {
        expect(mockRequestPolicy.isServableFromCache(
                (HttpRequest)anyObject())).andReturn(allow);
    }

    private void backendCall(HttpRequest req, HttpResponse resp)
            throws Exception {
        expect(mockBackend.execute(host, req, context))
            .andReturn(resp);
    }

    private void backendCallWasMade(HttpRequest request, HttpResponse response) throws IOException {
        expect(mockBackend.execute(
                (HttpHost)anyObject(),
                same(request),
                (HttpContext)anyObject())).andReturn(response);
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

    private void handleBackendResponseReturnsResponse(HttpRequest request, HttpResponse response)
            throws IOException {
        expect(
                impl.handleBackendResponse(
                        (HttpHost)anyObject(),
                        same(request),
                        (Date)anyObject(),
                        (Date)anyObject(),
                        (HttpResponse)anyObject())).andReturn(response);
    }

    private void requestProtocolValidationIsCalled() throws Exception {
        expect(
                mockRequestProtocolCompliance.makeRequestCompliant(
                        (HttpRequest)anyObject())).andReturn(request);
    }

    private void requestCannotBeMadeCompliantThrows(ClientProtocolException exception) throws Exception {
        expect(
                mockRequestProtocolCompliance.makeRequestCompliant(
                        (HttpRequest)anyObject())).andThrow(exception);
    }

    private void mockImplMethods(String... methods) {
        mockedImpl = true;
        impl = createMockBuilder(CachingHttpClient.class).withConstructor(
                mockBackend,
                mockValidityPolicy,
                mockResponsePolicy,
                mockCache,
                mockResponseGenerator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance).addMockedMethods(methods).createMock();
    }

}
