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
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
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
import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.cache.ResourceFactory;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCachingHttpClient {

    private static final String GET_CURRENT_DATE = "getCurrentDate";

    private static final String HANDLE_BACKEND_RESPONSE = "handleBackendResponse";

    private static final String CALL_BACKEND = "callBackend";

    private static final String REVALIDATE_CACHE_ENTRY = "revalidateCacheEntry";

    private static final String GET_CACHE_ENTRY = "getCacheEntry";

    private static final String STORE_IN_CACHE = "storeInCache";

    private static final String GET_RESPONSE_READER = "getResponseReader";

    private CachingHttpClient impl;
    private boolean mockedImpl;

    private ResourceFactory mockResourceFactory;
    private CacheInvalidator mockInvalidator;
    private CacheValidityPolicy mockValidityPolicy;
    private CacheableRequestPolicy mockRequestPolicy;
    private HttpClient mockBackend;
    private HttpCache mockCache;
    private CachedResponseSuitabilityChecker mockSuitabilityChecker;
    private ResponseCachingPolicy mockResponsePolicy;
    private HttpResponse mockBackendResponse;
    private CacheEntry mockCacheEntry;
    private CacheEntry mockVariantCacheEntry;
    private CacheEntry mockUpdatedCacheEntry;
    private URIExtractor mockExtractor;
    private CachedHttpResponseGenerator mockResponseGenerator;
    private SizeLimitedResponseReader mockResponseReader;
    private ClientConnectionManager mockConnectionManager;
    private ResponseHandler<Object> mockHandler;
    private HttpUriRequest mockUriRequest;
    private HttpResponse mockCachedResponse;
    private HttpResponse mockReconstructedResponse;
    private ConditionalRequestBuilder mockConditionalRequestBuilder;
    private HttpRequest mockConditionalRequest;
    private StatusLine mockStatusLine;
    private CacheEntryUpdater mockCacheEntryUpdater;
    private ResponseProtocolCompliance mockResponseProtocolCompliance;
    private RequestProtocolCompliance mockRequestProtocolCompliance;

    private Date requestDate;
    private Date responseDate;
    private HttpHost host;
    private HttpRequest request;
    private HttpContext context;
    private HttpParams params;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        mockResourceFactory = EasyMock.createMock(ResourceFactory.class);
        mockInvalidator = EasyMock.createMock(CacheInvalidator.class);
        mockRequestPolicy = EasyMock.createMock(CacheableRequestPolicy.class);
        mockValidityPolicy = EasyMock.createMock(CacheValidityPolicy.class);
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockCache = EasyMock.createMock(HttpCache.class);
        mockSuitabilityChecker = EasyMock.createMock(CachedResponseSuitabilityChecker.class);
        mockResponsePolicy = EasyMock.createMock(ResponseCachingPolicy.class);
        mockConnectionManager = EasyMock.createMock(ClientConnectionManager.class);
        mockHandler = EasyMock.createMock(ResponseHandler.class);
        mockBackendResponse = EasyMock.createMock(HttpResponse.class);
        mockUriRequest = EasyMock.createMock(HttpUriRequest.class);
        mockCacheEntry = EasyMock.createMock(CacheEntry.class);
        mockUpdatedCacheEntry = EasyMock.createMock(CacheEntry.class);
        mockVariantCacheEntry = EasyMock.createMock(CacheEntry.class);
        mockExtractor = EasyMock.createMock(URIExtractor.class);
        mockResponseGenerator = EasyMock.createMock(CachedHttpResponseGenerator.class);
        mockCachedResponse = EasyMock.createMock(HttpResponse.class);
        mockConditionalRequestBuilder = EasyMock.createMock(ConditionalRequestBuilder.class);
        mockConditionalRequest = EasyMock.createMock(HttpRequest.class);
        mockStatusLine = EasyMock.createMock(StatusLine.class);
        mockCacheEntryUpdater = EasyMock.createMock(CacheEntryUpdater.class);
        mockResponseReader = EasyMock.createMock(SizeLimitedResponseReader.class);
        mockReconstructedResponse = EasyMock.createMock(HttpResponse.class);
        mockResponseProtocolCompliance = EasyMock.createMock(ResponseProtocolCompliance.class);
        mockRequestProtocolCompliance = EasyMock.createMock(RequestProtocolCompliance.class);

        requestDate = new Date(System.currentTimeMillis() - 1000);
        responseDate = new Date();
        host = new HttpHost("foo.example.com");
        request = new BasicHttpRequest("GET", "/stuff", HttpVersion.HTTP_1_1);
        context = new BasicHttpContext();
        params = new BasicHttpParams();
        impl = new CachingHttpClient(
                mockBackend,
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance);
    }

    private void replayMocks() {
        EasyMock.replay(mockResourceFactory);
        EasyMock.replay(mockInvalidator);
        EasyMock.replay(mockRequestPolicy);
        EasyMock.replay(mockValidityPolicy);
        EasyMock.replay(mockSuitabilityChecker);
        EasyMock.replay(mockResponsePolicy);
        EasyMock.replay(mockCacheEntry);
        EasyMock.replay(mockVariantCacheEntry);
        EasyMock.replay(mockResponseGenerator);
        EasyMock.replay(mockExtractor);
        EasyMock.replay(mockBackend);
        EasyMock.replay(mockCache);
        EasyMock.replay(mockConnectionManager);
        EasyMock.replay(mockHandler);
        EasyMock.replay(mockBackendResponse);
        EasyMock.replay(mockUriRequest);
        EasyMock.replay(mockCachedResponse);
        EasyMock.replay(mockConditionalRequestBuilder);
        EasyMock.replay(mockConditionalRequest);
        EasyMock.replay(mockStatusLine);
        EasyMock.replay(mockCacheEntryUpdater);
        EasyMock.replay(mockResponseReader);
        EasyMock.replay(mockReconstructedResponse);
        EasyMock.replay(mockResponseProtocolCompliance);
        EasyMock.replay(mockRequestProtocolCompliance);
        if (mockedImpl) {
            EasyMock.replay(impl);
        }
    }

    private void verifyMocks() {
        EasyMock.verify(mockResourceFactory);
        EasyMock.verify(mockInvalidator);
        EasyMock.verify(mockRequestPolicy);
        EasyMock.verify(mockValidityPolicy);
        EasyMock.verify(mockSuitabilityChecker);
        EasyMock.verify(mockResponsePolicy);
        EasyMock.verify(mockCacheEntry);
        EasyMock.verify(mockVariantCacheEntry);
        EasyMock.verify(mockResponseGenerator);
        EasyMock.verify(mockExtractor);
        EasyMock.verify(mockBackend);
        EasyMock.verify(mockCache);
        EasyMock.verify(mockConnectionManager);
        EasyMock.verify(mockHandler);
        EasyMock.verify(mockBackendResponse);
        EasyMock.verify(mockUriRequest);
        EasyMock.verify(mockCachedResponse);
        EasyMock.verify(mockConditionalRequestBuilder);
        EasyMock.verify(mockConditionalRequest);
        EasyMock.verify(mockStatusLine);
        EasyMock.verify(mockCacheEntryUpdater);
        EasyMock.verify(mockResponseReader);
        EasyMock.verify(mockReconstructedResponse);
        EasyMock.verify(mockResponseProtocolCompliance);
        EasyMock.verify(mockRequestProtocolCompliance);
        if (mockedImpl) {
            EasyMock.verify(impl);
        }
    }

    @Test
    public void testCacheableResponsesGoIntoCache() throws Exception {
        mockImplMethods(STORE_IN_CACHE, GET_RESPONSE_READER);
        responsePolicyAllowsCaching(true);

        responseProtocolValidationIsCalled();

        getMockResponseReader();
        responseIsTooLarge(false);
        byte[] buf = responseReaderReturnsBufferOfSize(100);

        generateResource(buf);
        storeInCacheWasCalled();
        responseIsGeneratedFromCache();
        responseStatusLineIsInspectable();
        responseGetHeaders();
        responseDoesNotHaveExplicitContentLength();

        replayMocks();
        HttpResponse result = impl.handleBackendResponse(host, request, requestDate,
                                                         responseDate, mockBackendResponse);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, result);
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
        EasyMock.expect(
                mockRequestProtocolCompliance.requestIsFatallyNonCompliant(request)).andReturn(
                errors);
    }

    @Test
    public void testStoreInCachePutsNonVariantEntryInPlace() throws Exception {

        final String theURI = "theURI";

        cacheEntryHasVariants(false);
        extractTheURI(theURI);
        putInCache(theURI);

        replayMocks();
        impl.storeInCache(host, request, mockCacheEntry);
        verifyMocks();
    }

    @Test
    public void testCacheUpdateAddsVariantURIToParentEntry() throws Exception {
        final String variantURI = "variantURI";
        final CacheEntry entry = new CacheEntry();
        copyResource();
        replayMocks();
        impl.doGetUpdatedParentEntry("/stuff", null, entry, variantURI);
        verifyMocks();
    }


    @Test
    public void testCacheMissCausesBackendRequest() throws Exception {
        mockImplMethods(GET_CACHE_ENTRY, CALL_BACKEND);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(null);
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
        mockImplMethods(GET_CACHE_ENTRY, CALL_BACKEND);
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
        mockImplMethods(GET_CACHE_ENTRY, REVALIDATE_CACHE_ENTRY);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        requestProtocolValidationIsCalled();
        requestIsFatallyNonCompliant(null);

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(true);
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
    public void testRevalidationCallsHandleBackEndResponseWhenNot304() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);

        conditionalRequestBuilderCalled();
        getCurrentDateReturns(requestDate);
        backendCallWasMadeWithRequest(mockConditionalRequest);
        getCurrentDateReturns(responseDate);
        backendResponseCodeIs(HttpStatus.SC_OK);
        cacheEntryUpdaterCalled();
        cacheEntryHasVariants(false, mockUpdatedCacheEntry);
        extractTheURI("http://foo.example.com");
        putInCache("http://foo.example.com", mockUpdatedCacheEntry);
        responseIsGeneratedFromCache(mockUpdatedCacheEntry);

        replayMocks();

        HttpResponse result = impl.revalidateCacheEntry(host, request, context,
                                                        mockCacheEntry);

        verifyMocks();

        Assert.assertEquals(mockCachedResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(0, impl.getCacheHits());
        Assert.assertEquals(1, impl.getCacheUpdates());
    }

    @Test
    public void testRevalidationUpdatesCacheEntryAndPutsItToCacheWhen304ReturningCachedResponse()
            throws Exception {
        mockImplMethods(GET_CURRENT_DATE, STORE_IN_CACHE);
        conditionalRequestBuilderCalled();
        getCurrentDateReturns(requestDate);
        backendCallWasMadeWithRequest(mockConditionalRequest);
        getCurrentDateReturns(responseDate);
        backendResponseCodeIs(HttpStatus.SC_NOT_MODIFIED);

        cacheEntryUpdaterCalled();
        storeInCacheWasCalled(mockUpdatedCacheEntry);

        responseIsGeneratedFromCache(mockUpdatedCacheEntry);

        replayMocks();

        HttpResponse result = impl.revalidateCacheEntry(host, request, context, mockCacheEntry);

        verifyMocks();

        Assert.assertEquals(mockCachedResponse, result);
        Assert.assertEquals(0, impl.getCacheMisses());
        Assert.assertEquals(0, impl.getCacheHits());
        Assert.assertEquals(1, impl.getCacheUpdates());
    }

    @Test
    public void testSuitableCacheEntryDoesNotCauseBackendRequest() throws Exception {
        mockImplMethods(GET_CACHE_ENTRY);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        requestProtocolValidationIsCalled();
        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(true);
        responseIsGeneratedFromCache();
        requestIsFatallyNonCompliant(null);

        replayMocks();
        HttpResponse result = impl.execute(host, request, context);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, result);
    }

    @Test
    public void testCallBackendMakesBackEndRequestAndHandlesResponse() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);
        getCurrentDateReturns(requestDate);
        backendCallWasMadeWithRequest(request);
        getCurrentDateReturns(responseDate);
        handleBackendResponseReturnsResponse(request, mockBackendResponse);

        replayMocks();

        impl.callBackend(host, request, context);

        verifyMocks();
    }

    @Test
    public void testNonCacheableResponseIsNotCachedAndIsReturnedAsIs() throws Exception {
        final String theURI = "theURI";
        Date currentDate = new Date();
        responsePolicyAllowsCaching(false);
        responseProtocolValidationIsCalled();

        extractTheURI(theURI);
        removeFromCache(theURI);

        replayMocks();
        HttpResponse result = impl.handleBackendResponse(host, request, currentDate,
                                                         currentDate, mockBackendResponse);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
    }

    @Test
    public void testGetCacheEntryReturnsNullOnCacheMiss() throws Exception {

        final String theURI = "theURI";
        extractTheURI(theURI);
        gotCacheMiss(theURI);

        replayMocks();
        HttpCacheEntry result = impl.getCacheEntry(host, request);
        verifyMocks();
        Assert.assertNull(result);
    }

    @Test
    public void testGetCacheEntryFetchesFromCacheOnCacheHitIfNoVariants() throws Exception {

        final String theURI = "theURI";
        extractTheURI(theURI);
        gotCacheHit(theURI);
        cacheEntryHasVariants(false);

        replayMocks();
        HttpCacheEntry result = impl.getCacheEntry(host, request);
        verifyMocks();
        Assert.assertSame(mockCacheEntry, result);
    }

    @Test
    public void testGetCacheEntryReturnsNullIfNoVariantInCache() throws Exception {

        final String theURI = "theURI";
        final String variantURI = "variantURI";
        extractTheURI(theURI);
        gotCacheHit(theURI);
        cacheEntryHasVariants(true);
        extractVariantURI(variantURI);
        gotCacheMiss(variantURI);

        replayMocks();
        HttpCacheEntry result = impl.getCacheEntry(host, request);
        verifyMocks();
        Assert.assertNull(result);
    }

    @Test
    public void testGetCacheEntryReturnsVariantIfPresentInCache() throws Exception {

        final String theURI = "theURI";
        final String variantURI = "variantURI";
        extractTheURI(theURI);
        gotCacheHit(theURI, mockCacheEntry);
        cacheEntryHasVariants(true);
        extractVariantURI(variantURI);
        gotCacheHit(variantURI, mockVariantCacheEntry);

        replayMocks();
        HttpCacheEntry result = impl.getCacheEntry(host, request);
        verifyMocks();
        Assert.assertSame(mockVariantCacheEntry, result);
    }

    @Test
    public void testTooLargeResponsesAreNotCached() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, GET_RESPONSE_READER, STORE_IN_CACHE);
        getCurrentDateReturns(requestDate);
        backendCallWasMadeWithRequest(request);
        responseProtocolValidationIsCalled();

        getCurrentDateReturns(responseDate);
        responsePolicyAllowsCaching(true);
        getMockResponseReader();
        responseIsTooLarge(true);
        readerReturnsReconstructedResponse();

        replayMocks();

        impl.callBackend(host, request, context);

        verifyMocks();
    }

    @Test
    public void testSmallEnoughResponsesAreCached() throws Exception {
        requestDate = new Date();
        responseDate = new Date();
        mockImplMethods(GET_CURRENT_DATE, GET_RESPONSE_READER, STORE_IN_CACHE);
        getCurrentDateReturns(requestDate);
        responseProtocolValidationIsCalled();

        backendCallWasMadeWithRequest(request);
        getCurrentDateReturns(responseDate);
        responsePolicyAllowsCaching(true);
        getMockResponseReader();
        responseIsTooLarge(false);
        byte[] buf = responseReaderReturnsBufferOfSize(100);
        generateResource(buf);
        storeInCacheWasCalled();
        responseIsGeneratedFromCache();
        responseStatusLineIsInspectable();
        responseGetHeaders();
        responseDoesNotHaveExplicitContentLength();

        replayMocks();

        impl.callBackend(host, request, context);

        verifyMocks();
    }

    @Test
    public void testCallsSelfForExecuteOnHostRequestWithNullContext() throws Exception {
        final Counter c = new Counter();
        final HttpHost theHost = host;
        final HttpRequest theRequest = request;
        final HttpResponse theResponse = mockBackendResponse;
        impl = new CachingHttpClient(
                mockBackend,
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
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
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
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

        EasyMock.expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
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
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
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

        EasyMock.expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
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
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
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
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
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

        EasyMock.expect(mockUriRequest.getURI()).andReturn(uri);

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
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
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

        EasyMock.expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
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
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
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

        EasyMock.expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
                theValue);

        replayMocks();
        Object result = impl.execute(mockUriRequest, mockHandler, context);
        verifyMocks();
        Assert.assertEquals(1, c.getCount());
        Assert.assertSame(theValue, result);
    }

    @Test
    public void testUsesBackendsConnectionManager() {
        EasyMock.expect(mockBackend.getConnectionManager()).andReturn(
                mockConnectionManager);
        replayMocks();
        ClientConnectionManager result = impl.getConnectionManager();
        verifyMocks();
        Assert.assertSame(result, mockConnectionManager);
    }

    @Test
    public void testUsesBackendsHttpParams() {
        EasyMock.expect(mockBackend.getParams()).andReturn(params);
        replayMocks();
        HttpParams result = impl.getParams();
        verifyMocks();
        Assert.assertSame(params, result);
    }

    @Test
    public void testResponseIsGeneratedWhenCacheEntryIsUsable() throws Exception {

        final String theURI = "http://foo";

        requestIsFatallyNonCompliant(null);
        requestProtocolValidationIsCalled();
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        cacheEntrySuitable(true);
        extractTheURI(theURI);
        gotCacheHit(theURI);
        responseIsGeneratedFromCache();
        cacheEntryHasVariants(false);

        replayMocks();
        impl.execute(host, request, context);
        verifyMocks();
    }

    @Test
    public void testNonCompliantRequestWrapsAndReThrowsProtocolException() throws Exception {

        ProtocolException expected = new ProtocolException("ouch");

        requestIsFatallyNonCompliant(null);
        requestCannotBeMadeCompliantThrows(expected);

        boolean gotException = false;
        replayMocks();
        try {
            impl.execute(host, request, context);
        } catch (ClientProtocolException ex) {
            Assert.assertTrue(ex.getCause().getMessage().equals(expected.getMessage()));
            gotException = true;
        }
        verifyMocks();
        Assert.assertTrue(gotException);
    }

    @Test
    public void testCorrectIncompleteResponseDoesNotCorrectComplete200Response()
        throws Exception {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","128");

        HttpResponse result = impl.correctIncompleteResponse(resp, bytes);
        Assert.assertTrue(HttpTestUtils.semanticallyTransparent(resp, result));
    }

    @Test
    public void testCorrectIncompleteResponseDoesNotCorrectComplete206Response()
        throws Exception {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_PARTIAL_CONTENT, "Partial Content");
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","128");
        resp.setHeader("Content-Range","bytes 0-127/255");

        HttpResponse result = impl.correctIncompleteResponse(resp, bytes);
        Assert.assertTrue(HttpTestUtils.semanticallyTransparent(resp, result));
    }

    @Test
    public void testCorrectIncompleteResponseGenerates502ForIncomplete200Response()
        throws Exception {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","256");

        HttpResponse result = impl.correctIncompleteResponse(resp, bytes);
        Assert.assertTrue(HttpStatus.SC_BAD_GATEWAY == result.getStatusLine().getStatusCode());
    }

    @Test
    public void testCorrectIncompleteResponseDoesNotCorrectIncompleteNon200Or206Responses()
        throws Exception {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_FORBIDDEN, "Forbidden");
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","256");

        HttpResponse result = impl.correctIncompleteResponse(resp, bytes);
        Assert.assertTrue(HttpTestUtils.semanticallyTransparent(resp, result));
    }

    @Test
    public void testCorrectIncompleteResponseDoesNotCorrectResponsesWithoutExplicitContentLength()
        throws Exception {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setEntity(new ByteArrayEntity(bytes));

        HttpResponse result = impl.correctIncompleteResponse(resp, bytes);
        Assert.assertTrue(HttpTestUtils.semanticallyTransparent(resp, result));
    }

    @Test
    public void testCorrectIncompleteResponseDoesNotCorrectResponsesWithUnparseableContentLengthHeader()
        throws Exception {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setHeader("Content-Length","foo");
        resp.setEntity(new ByteArrayEntity(bytes));

        HttpResponse result = impl.correctIncompleteResponse(resp, bytes);
        Assert.assertTrue(HttpTestUtils.semanticallyTransparent(resp, result));
    }

    @Test
    public void testCorrectIncompleteResponseProvidesPlainTextErrorMessage()
        throws Exception {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","256");

        HttpResponse result = impl.correctIncompleteResponse(resp, bytes);
        Header ctype = result.getFirstHeader("Content-Type");
        Assert.assertEquals("text/plain;charset=UTF-8", ctype.getValue());
    }

    @Test
    public void testCorrectIncompleteResponseProvidesNonEmptyErrorMessage()
        throws Exception {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","256");

        HttpResponse result = impl.correctIncompleteResponse(resp, bytes);
        int clen = Integer.parseInt(result.getFirstHeader("Content-Length").getValue());
        Assert.assertTrue(clen > 0);
        HttpEntity body = result.getEntity();
        if (body.getContentLength() < 0) {
            InputStream is = body.getContent();
            int bytes_read = 0;
            while((is.read()) != -1) {
                bytes_read++;
            }
            is.close();
            Assert.assertEquals(clen, bytes_read);
        } else {
            Assert.assertTrue(body.getContentLength() == clen);
        }
    }

    @Test
    public void testIsSharedCache() {
        Assert.assertTrue(impl.isSharedCache());
    }

    private void cacheInvalidatorWasCalled()  throws IOException {
        mockInvalidator.flushInvalidatedCacheEntries(
                EasyMock.<HttpHost>anyObject(),
                EasyMock.<HttpRequest>anyObject());
    }

    private void callBackendReturnsResponse(HttpResponse response) throws IOException {
        EasyMock.expect(impl.callBackend(
                EasyMock.<HttpHost>anyObject(),
                EasyMock.<HttpRequest>anyObject(),
                EasyMock.<HttpContext>anyObject())).andReturn(response);
    }

    private void revalidateCacheEntryReturns(HttpResponse response) throws IOException,
            ProtocolException {
        EasyMock.expect(
                impl.revalidateCacheEntry(
                        EasyMock.<HttpHost>anyObject(),
                        EasyMock.<HttpRequest>anyObject(),
                        EasyMock.<HttpContext>anyObject(),
                        EasyMock.<HttpCacheEntry>anyObject())).andReturn(response);
    }

    private void cacheEntryValidatable(boolean b) {
        EasyMock.expect(mockValidityPolicy.isRevalidatable(
                EasyMock.<HttpCacheEntry>anyObject())).andReturn(b);
    }

    private void cacheEntryUpdaterCalled() throws IOException {
        EasyMock.expect(
                mockCacheEntryUpdater.updateCacheEntry(
                        EasyMock.<String>anyObject(),
                        EasyMock.<HttpCacheEntry>anyObject(),
                        EasyMock.<Date>anyObject(),
                        EasyMock.<Date>anyObject(),
                        EasyMock.<HttpResponse>anyObject())).andReturn(mockUpdatedCacheEntry);
    }

    private void getCacheEntryReturns(CacheEntry entry) throws IOException {
        EasyMock.expect(impl.getCacheEntry(
                EasyMock.<HttpHost>anyObject(),
                EasyMock.<HttpRequest>anyObject())).andReturn(entry);
    }

    private void backendResponseCodeIs(int code) {
        EasyMock.expect(mockBackendResponse.getStatusLine()).andReturn(mockStatusLine);
        EasyMock.expect(mockStatusLine.getStatusCode()).andReturn(code);
    }

    private void conditionalRequestBuilderCalled() throws ProtocolException {
        EasyMock.expect(
                mockConditionalRequestBuilder.buildConditionalRequest(
                        EasyMock.<HttpRequest>anyObject(),
                        EasyMock.<HttpCacheEntry>anyObject())).andReturn(mockConditionalRequest);
    }

    private void getCurrentDateReturns(Date date) {
        EasyMock.expect(impl.getCurrentDate()).andReturn(date);
    }

    private void getMockResponseReader() {
        EasyMock.expect(impl.getResponseReader(
                EasyMock.<HttpResponse>anyObject())).andReturn(mockResponseReader);
    }

    private void removeFromCache(String theURI) throws Exception {
        mockCache.removeEntry(theURI);
    }

    private void requestPolicyAllowsCaching(boolean allow) {
        EasyMock.expect(mockRequestPolicy.isServableFromCache(
                EasyMock.<HttpRequest>anyObject())).andReturn(allow);
    }

    private void responseDoesNotHaveExplicitContentLength() {
        EasyMock.expect(mockBackendResponse.getFirstHeader("Content-Length"))
            .andReturn(null).anyTimes();
    }

    private byte[] responseReaderReturnsBufferOfSize(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        EasyMock.expect(mockResponseReader.getResponseBytes()).andReturn(buffer);
        return buffer;
    }

    private void readerReturnsReconstructedResponse() {
        EasyMock.expect(mockResponseReader.getReconstructedResponse()).andReturn(
                mockReconstructedResponse);
    }

    private void responseIsTooLarge(boolean tooLarge) throws Exception {
        EasyMock.expect(mockResponseReader.isResponseTooLarge()).andReturn(tooLarge);
    }

    private void backendCallWasMadeWithRequest(HttpRequest request) throws IOException {
        EasyMock.expect(mockBackend.execute(
                EasyMock.<HttpHost>anyObject(),
                EasyMock.same(request),
                EasyMock.<HttpContext>anyObject())).andReturn(mockBackendResponse);
    }

    private void responsePolicyAllowsCaching(boolean allow) {
        EasyMock.expect(
                mockResponsePolicy.isResponseCacheable(
                        EasyMock.<HttpRequest>anyObject(),
                        EasyMock.<HttpResponse>anyObject())).andReturn(allow);
    }

    private void gotCacheMiss(String theURI) throws Exception {
        EasyMock.expect(mockCache.getEntry(theURI)).andReturn(null);
    }

    private void cacheEntrySuitable(boolean suitable) {
        EasyMock.expect(
                mockSuitabilityChecker.canCachedResponseBeUsed(
                        EasyMock.<HttpHost>anyObject(),
                        EasyMock.<HttpRequest>anyObject(),
                        EasyMock.<HttpCacheEntry>anyObject())).andReturn(suitable);
    }

    private void gotCacheHit(String theURI) throws Exception {
        EasyMock.expect(mockCache.getEntry(theURI)).andReturn(mockCacheEntry);
    }

    private void gotCacheHit(String theURI, CacheEntry entry) throws Exception {
        EasyMock.expect(mockCache.getEntry(theURI)).andReturn(entry);
    }

    private void cacheEntryHasVariants(boolean b) {
        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(b);
    }

    private void cacheEntryHasVariants(boolean b, CacheEntry entry) {
        EasyMock.expect(entry.hasVariants()).andReturn(b);
    }

    private void responseIsGeneratedFromCache() {
        EasyMock.expect(mockResponseGenerator.generateResponse(
                EasyMock.<HttpCacheEntry>anyObject())).andReturn(mockCachedResponse);
    }

    private void responseStatusLineIsInspectable() {
        EasyMock.expect(mockBackendResponse.getStatusLine()).andReturn(new OKStatus()).anyTimes();
    }

    private void responseGetHeaders() {
        EasyMock.expect(mockBackendResponse.getAllHeaders()).andReturn(new Header[] {}).anyTimes();
    }

    private void responseIsGeneratedFromCache(CacheEntry entry) {
        EasyMock.expect(mockResponseGenerator.generateResponse(entry))
                .andReturn(mockCachedResponse);
    }

    private void extractTheURI(String theURI) {
        EasyMock.expect(mockExtractor.getURI(host, request)).andReturn(theURI);
    }

    private void extractVariantURI(String variantURI) {
        extractVariantURI(variantURI,mockCacheEntry);
    }

    private void extractVariantURI(String variantURI, CacheEntry entry){
        EasyMock.expect(mockExtractor.getVariantURI(
                EasyMock.<HttpHost>anyObject(),
                EasyMock.<HttpRequest>anyObject(),
                EasyMock.same(entry))).andReturn(variantURI);
    }

    private void putInCache(String theURI) throws Exception {
        mockCache.putEntry(theURI, mockCacheEntry);
    }

    private void putInCache(String theURI, CacheEntry entry) throws Exception {
        mockCache.putEntry(theURI, entry);
    }

    private void generateResource(byte [] b) throws IOException {
        EasyMock.expect(
                mockResourceFactory.generate(
                        EasyMock.<String>anyObject(),
                        EasyMock.same(b))).andReturn(new HeapResource(b));
    }

    private void copyResource() throws IOException {
        EasyMock.expect(
                mockResourceFactory.copy(
                        EasyMock.<String>anyObject(),
                        EasyMock.<Resource>anyObject())).andReturn(new HeapResource(new byte[] {}));
    }

    private void handleBackendResponseReturnsResponse(HttpRequest request, HttpResponse response)
            throws IOException {
        EasyMock.expect(
                impl.handleBackendResponse(
                        EasyMock.<HttpHost>anyObject(),
                        EasyMock.same(request),
                        EasyMock.<Date>anyObject(),
                        EasyMock.<Date>anyObject(),
                        EasyMock.<HttpResponse>anyObject())).andReturn(response);
    }

    private void storeInCacheWasCalled() throws IOException {
        impl.storeInCache(
                EasyMock.<HttpHost>anyObject(),
                EasyMock.<HttpRequest>anyObject(),
                EasyMock.<HttpCacheEntry>anyObject());
    }

    private void storeInCacheWasCalled(CacheEntry entry) throws IOException {
        impl.storeInCache(
                EasyMock.<HttpHost>anyObject(),
                EasyMock.<HttpRequest>anyObject(),
                EasyMock.same(entry));
    }

    private void responseProtocolValidationIsCalled() throws ClientProtocolException {
        mockResponseProtocolCompliance.ensureProtocolCompliance(
                EasyMock.<HttpRequest>anyObject(),
                EasyMock.<HttpResponse>anyObject());
    }

    private void requestProtocolValidationIsCalled() throws Exception {
        EasyMock.expect(
                mockRequestProtocolCompliance.makeRequestCompliant(
                        EasyMock.<HttpRequest>anyObject())).andReturn(request);
    }

    private void requestCannotBeMadeCompliantThrows(ProtocolException exception) throws Exception {
        EasyMock.expect(
                mockRequestProtocolCompliance.makeRequestCompliant(
                        EasyMock.<HttpRequest>anyObject())).andThrow(exception);
    }

    private void mockImplMethods(String... methods) {
        mockedImpl = true;
        impl = EasyMock.createMockBuilder(CachingHttpClient.class).withConstructor(
                mockBackend,
                mockResourceFactory,
                mockValidityPolicy,
                mockResponsePolicy,
                mockExtractor,
                mockCache,
                mockResponseGenerator,
                mockInvalidator,
                mockRequestPolicy,
                mockSuitabilityChecker,
                mockConditionalRequestBuilder,
                mockCacheEntryUpdater,
                mockResponseProtocolCompliance,
                mockRequestProtocolCompliance).addMockedMethods(methods).createMock();
    }

}
