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

import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static junit.framework.Assert.assertTrue;

public class TestCachingHttpClient {

    private static final ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP",1,1);

    private static final String GET_CURRENT_DATE = "getCurrentDate";

    private static final String HANDLE_BACKEND_RESPONSE = "handleBackendResponse";

    private static final String CALL_BACKEND = "callBackend";

    private static final String REVALIDATE_CACHE_ENTRY = "revalidateCacheEntry";

    private static final String GET_CACHE_ENTRY = "getCacheEntry";

    private static final String STORE_IN_CACHE = "storeInCache";

    private static final String GET_RESPONSE_READER = "getResponseReader";

    private CachingHttpClient impl;

    private CacheInvalidator mockInvalidator;
    private CacheableRequestPolicy mockRequestPolicy;
    private HttpClient mockBackend;
    private HttpCache<CacheEntry> mockCache;
    private CachedResponseSuitabilityChecker mockSuitabilityChecker;
    private ResponseCachingPolicy mockResponsePolicy;
    private HttpRequest mockRequest;
    private HttpResponse mockBackendResponse;
    private CacheEntry mockCacheEntry;
    private CacheEntry mockVariantCacheEntry;
    private CacheEntry mockUpdatedCacheEntry;
    private URIExtractor mockExtractor;
    private CacheEntryGenerator mockEntryGenerator;
    private CachedHttpResponseGenerator mockResponseGenerator;

    private SizeLimitedResponseReader mockResponseReader;
    private HttpHost host;
    private ClientConnectionManager mockConnectionManager;
    private HttpContext mockContext;
    private ResponseHandler<Object> mockHandler;
    private HttpParams mockParams;
    private HttpUriRequest mockUriRequest;
    private HttpResponse mockCachedResponse;
    private HttpResponse mockReconstructedResponse;

    private ConditionalRequestBuilder mockConditionalRequestBuilder;

    private HttpRequest mockConditionalRequest;

    private StatusLine mockStatusLine;
    private Date requestDate;
    private Date responseDate;

    private boolean mockedImpl;

    private CacheEntryUpdater mockCacheEntryUpdater;
    private ResponseProtocolCompliance mockResponseProtocolCompliance;
    private RequestProtocolCompliance mockRequestProtocolCompliance;
    private RequestLine mockRequestLine;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        mockInvalidator = EasyMock.createMock(CacheInvalidator.class);
        mockRequestPolicy = EasyMock.createMock(CacheableRequestPolicy.class);
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockCache = EasyMock.createMock(HttpCache.class);
        mockSuitabilityChecker = EasyMock.createMock(CachedResponseSuitabilityChecker.class);
        mockResponsePolicy = EasyMock.createMock(ResponseCachingPolicy.class);
        mockConnectionManager = EasyMock.createMock(ClientConnectionManager.class);
        mockContext = EasyMock.createMock(HttpContext.class);
        mockHandler = EasyMock.createMock(ResponseHandler.class);
        mockParams = EasyMock.createMock(HttpParams.class);
        mockRequest = EasyMock.createMock(HttpRequest.class);
        mockBackendResponse = EasyMock.createMock(HttpResponse.class);
        mockUriRequest = EasyMock.createMock(HttpUriRequest.class);
        mockCacheEntry = EasyMock.createMock(CacheEntry.class);
        mockUpdatedCacheEntry = EasyMock.createMock(CacheEntry.class);
        mockVariantCacheEntry = EasyMock.createMock(CacheEntry.class);
        mockExtractor = EasyMock.createMock(URIExtractor.class);
        mockEntryGenerator = EasyMock.createMock(CacheEntryGenerator.class);
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
        mockRequestLine = EasyMock.createMock(RequestLine.class);


        requestDate = new Date(System.currentTimeMillis() - 1000);
        responseDate = new Date();
        host = new HttpHost("foo.example.com");
        impl = new CachingHttpClient(mockBackend, mockResponsePolicy, mockEntryGenerator,
                                     mockExtractor, mockCache, mockResponseGenerator, mockInvalidator,
                                     mockRequestPolicy, mockSuitabilityChecker, mockConditionalRequestBuilder,
                                     mockCacheEntryUpdater, mockResponseProtocolCompliance,
                                     mockRequestProtocolCompliance);
    }

    private void replayMocks() {

        EasyMock.replay(mockInvalidator);
        EasyMock.replay(mockRequestPolicy);
        EasyMock.replay(mockSuitabilityChecker);
        EasyMock.replay(mockResponsePolicy);
        EasyMock.replay(mockCacheEntry);
        EasyMock.replay(mockVariantCacheEntry);
        EasyMock.replay(mockEntryGenerator);
        EasyMock.replay(mockResponseGenerator);
        EasyMock.replay(mockExtractor);
        EasyMock.replay(mockBackend);
        EasyMock.replay(mockCache);
        EasyMock.replay(mockConnectionManager);
        EasyMock.replay(mockContext);
        EasyMock.replay(mockHandler);
        EasyMock.replay(mockParams);
        EasyMock.replay(mockRequest);
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
        EasyMock.verify(mockInvalidator);
        EasyMock.verify(mockRequestPolicy);
        EasyMock.verify(mockSuitabilityChecker);
        EasyMock.verify(mockResponsePolicy);
        EasyMock.verify(mockCacheEntry);
        EasyMock.verify(mockVariantCacheEntry);
        EasyMock.verify(mockEntryGenerator);
        EasyMock.verify(mockResponseGenerator);
        EasyMock.verify(mockExtractor);
        EasyMock.verify(mockBackend);
        EasyMock.verify(mockCache);
        EasyMock.verify(mockConnectionManager);
        EasyMock.verify(mockContext);
        EasyMock.verify(mockHandler);
        EasyMock.verify(mockParams);
        EasyMock.verify(mockRequest);
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

        generateCacheEntry(requestDate, responseDate, buf);
        storeInCacheWasCalled();
        responseIsGeneratedFromCache();

        replayMocks();
        HttpResponse result = impl.handleBackendResponse(host, mockRequest, requestDate,
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
        requestInspectsRequestLine();

        replayMocks();
        HttpResponse result = impl.execute(host, mockRequest, mockContext);
        verifyMocks();

        Assert.assertSame(mockBackendResponse, result);
    }

    private void requestInspectsRequestLine() {
        org.easymock.EasyMock.expect(mockRequest.getRequestLine()).andReturn(mockRequestLine);
    }

    private void requestIsFatallyNonCompliant(RequestProtocolError error) {
        List<RequestProtocolError> errors = new ArrayList<RequestProtocolError>();
        if (error != null) {
            errors.add(error);
        }
        org.easymock.EasyMock.expect(
                mockRequestProtocolCompliance.requestIsFatallyNonCompliant(mockRequest)).andReturn(
                errors);
    }

    @Test
    public void testStoreInCachePutsNonVariantEntryInPlace() throws Exception {

        final String theURI = "theURI";

        cacheEntryHasVariants(false);
        extractTheURI(theURI);
        putInCache(theURI);

        replayMocks();
        impl.storeInCache(host, mockRequest, mockCacheEntry);
        verifyMocks();
    }

    @Test
    public void testCacheUpdateAddsVariantURIToParentEntry() throws Exception {

        final String variantURI = "variantURI";

        final CacheEntry entry = new CacheEntry(new Date(), new Date(), HTTP_1_1,
                new Header[] {}, new ByteArrayEntity(new byte[] {}), 200, "OK");

        extractVariantURI(variantURI, entry);
        putInCache(variantURI, entry);

        replayMocks();

        CacheEntry updatedEntry = impl.doGetUpdatedParentEntry(null, host, mockRequest, entry);

        verifyMocks();

        assertTrue(updatedEntry.getVariantURIs().contains(variantURI));
    }


    @Test
    public void testCacheMissCausesBackendRequest() throws Exception {
        mockImplMethods(GET_CACHE_ENTRY, CALL_BACKEND);
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        getCacheEntryReturns(null);
        requestProtocolValidationIsCalled();
        requestIsFatallyNonCompliant(null);
        requestInspectsRequestLine();

        callBackendReturnsResponse(mockBackendResponse);

        replayMocks();
        HttpResponse result = impl.execute(host, mockRequest, mockContext);
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
        requestInspectsRequestLine();

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(false);
        callBackendReturnsResponse(mockBackendResponse);

        replayMocks();
        HttpResponse result = impl.execute(host, mockRequest, mockContext);
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
        requestInspectsRequestLine();

        getCacheEntryReturns(mockCacheEntry);
        cacheEntrySuitable(false);
        cacheEntryValidatable(true);
        revalidateCacheEntryReturns(mockBackendResponse);

        replayMocks();
        HttpResponse result = impl.execute(host, mockRequest, mockContext);
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

        HttpResponse result = impl.revalidateCacheEntry(host, mockRequest, mockContext,
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

        HttpResponse result = impl.revalidateCacheEntry(host, mockRequest, mockContext, mockCacheEntry);

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
        requestInspectsRequestLine();

        replayMocks();
        HttpResponse result = impl.execute(host, mockRequest, mockContext);
        verifyMocks();

        Assert.assertSame(mockCachedResponse, result);
    }

    @Test
    public void testCallBackendMakesBackEndRequestAndHandlesResponse() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, HANDLE_BACKEND_RESPONSE);
        getCurrentDateReturns(requestDate);
        backendCallWasMadeWithRequest(mockRequest);
        getCurrentDateReturns(responseDate);
        handleBackendResponseReturnsResponse(mockRequest, mockBackendResponse);

        replayMocks();

        impl.callBackend(host, mockRequest, mockContext);

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
        HttpResponse result = impl.handleBackendResponse(host, mockRequest, currentDate,
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
        CacheEntry result = impl.getCacheEntry(host, mockRequest);
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
        CacheEntry result = impl.getCacheEntry(host, mockRequest);
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
        CacheEntry result = impl.getCacheEntry(host, mockRequest);
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
        CacheEntry result = impl.getCacheEntry(host, mockRequest);
        verifyMocks();
        Assert.assertSame(mockVariantCacheEntry, result);
    }

    @Test
    public void testTooLargeResponsesAreNotCached() throws Exception {
        mockImplMethods(GET_CURRENT_DATE, GET_RESPONSE_READER, STORE_IN_CACHE);
        getCurrentDateReturns(requestDate);
        backendCallWasMadeWithRequest(mockRequest);
        responseProtocolValidationIsCalled();

        getCurrentDateReturns(responseDate);
        responsePolicyAllowsCaching(true);
        getMockResponseReader();
        responseIsTooLarge(true);
        readerReturnsReconstructedResponse();

        replayMocks();

        impl.callBackend(host, mockRequest, mockContext);

        verifyMocks();
    }

    @Test
    public void testSmallEnoughResponsesAreCached() throws Exception {
        requestDate = new Date();
        responseDate = new Date();
        mockImplMethods(GET_CURRENT_DATE, GET_RESPONSE_READER, STORE_IN_CACHE);
        getCurrentDateReturns(requestDate);
        responseProtocolValidationIsCalled();

        backendCallWasMadeWithRequest(mockRequest);
        getCurrentDateReturns(responseDate);
        responsePolicyAllowsCaching(true);
        getMockResponseReader();
        responseIsTooLarge(false);
        byte[] buf = responseReaderReturnsBufferOfSize(100);
        generateCacheEntry(requestDate, responseDate, buf);
        storeInCacheWasCalled();
        responseIsGeneratedFromCache();

        replayMocks();

        impl.callBackend(host, mockRequest, mockContext);

        verifyMocks();
    }

    @Test
    public void testCallsSelfForExecuteOnHostRequestWithNullContext() throws Exception {
        final Counter c = new Counter();
        final HttpHost theHost = host;
        final HttpRequest theRequest = mockRequest;
        final HttpResponse theResponse = mockBackendResponse;
        impl = new CachingHttpClient(mockBackend, mockResponsePolicy, mockEntryGenerator,
                                     mockExtractor, mockCache, mockResponseGenerator, mockInvalidator,
                                     mockRequestPolicy, mockSuitabilityChecker, mockConditionalRequestBuilder,
                                     mockCacheEntryUpdater, mockResponseProtocolCompliance,
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
        HttpResponse result = impl.execute(host, mockRequest);
        verifyMocks();
        Assert.assertSame(mockBackendResponse, result);
        Assert.assertEquals(1, c.getCount());
    }

    @Test
    public void testCallsSelfWithDefaultContextForExecuteOnHostRequestWithHandler()
            throws Exception {

        final Counter c = new Counter();
        final HttpHost theHost = host;
        final HttpRequest theRequest = mockRequest;
        final HttpResponse theResponse = mockBackendResponse;
        final ResponseHandler<Object> theHandler = mockHandler;
        final Object value = new Object();
        impl = new CachingHttpClient(mockBackend, mockResponsePolicy, mockEntryGenerator,
                                     mockExtractor, mockCache, mockResponseGenerator, mockInvalidator,
                                     mockRequestPolicy, mockSuitabilityChecker, mockConditionalRequestBuilder,
                                     mockCacheEntryUpdater, mockResponseProtocolCompliance,
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

        org.easymock.EasyMock.expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
                value);

        replayMocks();
        Object result = impl.execute(host, mockRequest, mockHandler);
        verifyMocks();

        Assert.assertSame(value, result);
        Assert.assertEquals(1, c.getCount());
    }

    @Test
    public void testCallsSelfOnExecuteHostRequestWithHandlerAndContext() throws Exception {

        final Counter c = new Counter();
        final HttpHost theHost = host;
        final HttpRequest theRequest = mockRequest;
        final HttpResponse theResponse = mockBackendResponse;
        final HttpContext theContext = mockContext;
        impl = new CachingHttpClient(mockBackend, mockResponsePolicy, mockEntryGenerator,
                                     mockExtractor, mockCache, mockResponseGenerator, mockInvalidator,
                                     mockRequestPolicy, mockSuitabilityChecker, mockConditionalRequestBuilder,
                                     mockCacheEntryUpdater, mockResponseProtocolCompliance,
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

        org.easymock.EasyMock.expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
                theObject);

        replayMocks();
        Object result = impl.execute(host, mockRequest, mockHandler, mockContext);
        verifyMocks();
        Assert.assertEquals(1, c.getCount());
        Assert.assertSame(theObject, result);
    }

    @Test
    public void testCallsSelfWithNullContextOnExecuteUriRequest() throws Exception {
        final Counter c = new Counter();
        final HttpUriRequest theRequest = mockUriRequest;
        final HttpResponse theResponse = mockBackendResponse;
        impl = new CachingHttpClient(mockBackend, mockResponsePolicy, mockEntryGenerator,
                                     mockExtractor, mockCache, mockResponseGenerator, mockInvalidator,
                                     mockRequestPolicy, mockSuitabilityChecker, mockConditionalRequestBuilder,
                                     mockCacheEntryUpdater, mockResponseProtocolCompliance,
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
        final HttpContext theContext = mockContext;
        final HttpResponse theResponse = mockBackendResponse;
        impl = new CachingHttpClient(mockBackend, mockResponsePolicy, mockEntryGenerator,
                                     mockExtractor, mockCache, mockResponseGenerator, mockInvalidator,
                                     mockRequestPolicy, mockSuitabilityChecker, mockConditionalRequestBuilder,
                                     mockCacheEntryUpdater, mockResponseProtocolCompliance,
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

        org.easymock.EasyMock.expect(mockUriRequest.getURI()).andReturn(uri);

        replayMocks();
        HttpResponse result = impl.execute(mockUriRequest, mockContext);
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
        impl = new CachingHttpClient(mockBackend, mockResponsePolicy, mockEntryGenerator,
                                     mockExtractor, mockCache, mockResponseGenerator, mockInvalidator,
                                     mockRequestPolicy, mockSuitabilityChecker, mockConditionalRequestBuilder,
                                     mockCacheEntryUpdater, mockResponseProtocolCompliance,
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

        org.easymock.EasyMock.expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
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
        final HttpContext theContext = mockContext;
        final HttpResponse theResponse = mockBackendResponse;
        final Object theValue = new Object();
        impl = new CachingHttpClient(mockBackend, mockResponsePolicy, mockEntryGenerator,
                                     mockExtractor, mockCache, mockResponseGenerator, mockInvalidator,
                                     mockRequestPolicy, mockSuitabilityChecker, mockConditionalRequestBuilder,
                                     mockCacheEntryUpdater, mockResponseProtocolCompliance,
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

        org.easymock.EasyMock.expect(mockHandler.handleResponse(mockBackendResponse)).andReturn(
                theValue);

        replayMocks();
        Object result = impl.execute(mockUriRequest, mockHandler, mockContext);
        verifyMocks();
        Assert.assertEquals(1, c.getCount());
        Assert.assertSame(theValue, result);
    }

    @Test
    public void testUsesBackendsConnectionManager() {
        org.easymock.EasyMock.expect(mockBackend.getConnectionManager()).andReturn(
                mockConnectionManager);
        replayMocks();
        ClientConnectionManager result = impl.getConnectionManager();
        verifyMocks();
        Assert.assertSame(result, mockConnectionManager);
    }

    @Test
    public void testUsesBackendsHttpParams() {
        org.easymock.EasyMock.expect(mockBackend.getParams()).andReturn(mockParams);
        replayMocks();
        HttpParams result = impl.getParams();
        verifyMocks();
        Assert.assertSame(mockParams, result);
    }

    @Test
    @Ignore
    public void testRealResultsMatch() throws IOException {

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
        schemeRegistry.register(new Scheme("https", 443, SSLSocketFactory.getSocketFactory()));

        ClientConnectionManager cm = new ThreadSafeClientConnManager(schemeRegistry);
        HttpClient httpClient = new DefaultHttpClient(cm);

        HttpCache<CacheEntry> cacheImpl = new BasicHttpCache(100);

        CachingHttpClient cachingClient = new CachingHttpClient(httpClient, cacheImpl, 8192);

        HttpUriRequest request = new HttpGet("http://www.fancast.com/static-28262/styles/base.css");

        HttpClient baseClient = new DefaultHttpClient();

        HttpResponse cachedResponse = cachingClient.execute(request);
        HttpResponse realResponse = baseClient.execute(request);

        byte[] cached = readResponse(cachedResponse);
        byte[] real = readResponse(realResponse);

        Assert.assertArrayEquals(cached, real);
    }

    @Test
    public void testResponseIsGeneratedWhenCacheEntryIsUsable() throws Exception {

        final String theURI = "http://foo";

        requestIsFatallyNonCompliant(null);
        requestInspectsRequestLine();
        requestProtocolValidationIsCalled();
        cacheInvalidatorWasCalled();
        requestPolicyAllowsCaching(true);
        cacheEntrySuitable(true);
        extractTheURI(theURI);
        gotCacheHit(theURI);
        responseIsGeneratedFromCache();
        cacheEntryHasVariants(false);

        replayMocks();
        impl.execute(host, mockRequest, mockContext);
        verifyMocks();
    }

    @Test
    public void testNonCompliantRequestWrapsAndReThrowsProtocolException() throws Exception {

        ProtocolException expected = new ProtocolException("ouch");

        requestInspectsRequestLine();
        requestIsFatallyNonCompliant(null);
        requestCannotBeMadeCompliantThrows(expected);

        boolean gotException = false;
        replayMocks();
        try {
            impl.execute(host, mockRequest, mockContext);
        } catch (ClientProtocolException ex) {
            Assert.assertTrue(ex.getCause().getMessage().equals(expected.getMessage()));
            gotException = true;
        }
        verifyMocks();
        Assert.assertTrue(gotException);
    }

    private byte[] readResponse(HttpResponse response) {
        try {
            ByteArrayOutputStream s1 = new ByteArrayOutputStream();
            response.getEntity().writeTo(s1);
            return s1.toByteArray();
        } catch (Exception ex) {
            return new byte[]{};

        }

    }

    private void cacheInvalidatorWasCalled() {
        mockInvalidator.flushInvalidatedCacheEntries(host, mockRequest);
    }

    private void callBackendReturnsResponse(HttpResponse response) throws IOException {
        org.easymock.EasyMock.expect(impl.callBackend(host, mockRequest, mockContext)).andReturn(
                response);
    }

    private void revalidateCacheEntryReturns(HttpResponse response) throws IOException,
            ProtocolException {
        org.easymock.EasyMock.expect(
                impl.revalidateCacheEntry(host, mockRequest, mockContext, mockCacheEntry))
                .andReturn(response);
    }

    private void cacheEntryValidatable(boolean b) {
        org.easymock.EasyMock.expect(mockCacheEntry.isRevalidatable()).andReturn(b);
    }

    private void cacheEntryUpdaterCalled() throws IOException {
        EasyMock.expect(
                mockCacheEntryUpdater.updateCacheEntry(mockCacheEntry, requestDate, responseDate,
                                                       mockBackendResponse)).andReturn(mockUpdatedCacheEntry);
    }

    private void getCacheEntryReturns(CacheEntry entry) {
        org.easymock.EasyMock.expect(impl.getCacheEntry(host, mockRequest)).andReturn(entry);
    }

    private void backendResponseCodeIs(int code) {
        org.easymock.EasyMock.expect(mockBackendResponse.getStatusLine()).andReturn(mockStatusLine);
        org.easymock.EasyMock.expect(mockStatusLine.getStatusCode()).andReturn(code);
    }

    private void conditionalRequestBuilderCalled() throws ProtocolException {
        org.easymock.EasyMock.expect(
                mockConditionalRequestBuilder.buildConditionalRequest(mockRequest, mockCacheEntry))
                .andReturn(mockConditionalRequest);
    }

    private void getCurrentDateReturns(Date date) {
        org.easymock.EasyMock.expect(impl.getCurrentDate()).andReturn(date);
    }

    private void getMockResponseReader() {
        org.easymock.EasyMock.expect(impl.getResponseReader(mockBackendResponse)).andReturn(
                mockResponseReader);
    }

    private void removeFromCache(String theURI) throws Exception {
        mockCache.removeEntry(theURI);
    }

    private void requestPolicyAllowsCaching(boolean allow) {
        org.easymock.EasyMock.expect(mockRequestPolicy.isServableFromCache(mockRequest)).andReturn(
                allow);
    }

    private byte[] responseReaderReturnsBufferOfSize(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        org.easymock.EasyMock.expect(mockResponseReader.getResponseBytes()).andReturn(buffer);
        return buffer;
    }

    private void readerReturnsReconstructedResponse() {
        org.easymock.EasyMock.expect(mockResponseReader.getReconstructedResponse()).andReturn(
                mockReconstructedResponse);
    }

    private void responseIsTooLarge(boolean tooLarge) throws Exception {
        org.easymock.EasyMock.expect(mockResponseReader.isResponseTooLarge()).andReturn(tooLarge);
    }

    private void backendCallWasMadeWithRequest(HttpRequest request) throws IOException {
        org.easymock.EasyMock.expect(mockBackend.execute(host, request, mockContext)).andReturn(
                mockBackendResponse);
    }

    private void responsePolicyAllowsCaching(boolean allow) {
        org.easymock.EasyMock.expect(
                mockResponsePolicy.isResponseCacheable(mockRequest, mockBackendResponse))
                .andReturn(allow);
    }

    private void gotCacheMiss(String theURI) throws Exception {
        org.easymock.EasyMock.expect(mockCache.getEntry(theURI)).andReturn(null);
    }

    private void cacheEntrySuitable(boolean suitable) {
        org.easymock.EasyMock.expect(
                mockSuitabilityChecker.canCachedResponseBeUsed(host, mockRequest, mockCacheEntry))
                .andReturn(suitable);
    }

    private void gotCacheHit(String theURI) throws Exception {
        org.easymock.EasyMock.expect(mockCache.getEntry(theURI)).andReturn(mockCacheEntry);
    }

    private void gotCacheHit(String theURI, CacheEntry entry) throws Exception {
        org.easymock.EasyMock.expect(mockCache.getEntry(theURI)).andReturn(entry);
    }

    private void cacheEntryHasVariants(boolean b) {
        org.easymock.EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(b);
    }

    private void cacheEntryHasVariants(boolean b, CacheEntry entry) {
        EasyMock.expect(entry.hasVariants()).andReturn(b);
    }

    private void responseIsGeneratedFromCache() {
        org.easymock.EasyMock.expect(mockResponseGenerator.generateResponse(mockCacheEntry))
                .andReturn(mockCachedResponse);
    }

    private void responseIsGeneratedFromCache(CacheEntry entry) {
        org.easymock.EasyMock.expect(mockResponseGenerator.generateResponse(entry))
                .andReturn(mockCachedResponse);
    }

    private void extractTheURI(String theURI) {
        org.easymock.EasyMock.expect(mockExtractor.getURI(host, mockRequest)).andReturn(theURI);
    }

    private void extractVariantURI(String variantURI) {
        extractVariantURI(variantURI,mockCacheEntry);
    }

    private void extractVariantURI(String variantURI, CacheEntry entry){
        org.easymock.EasyMock
                .expect(mockExtractor.getVariantURI(host, mockRequest, entry)).andReturn(
                variantURI);
    }

    private void putInCache(String theURI) throws Exception {
        mockCache.putEntry(theURI, mockCacheEntry);
    }

    private void putInCache(String theURI, CacheEntry entry) throws Exception {
        mockCache.putEntry(theURI, entry);
    }

    private void generateCacheEntry(Date requestDate, Date responseDate, byte[] bytes) {
        org.easymock.EasyMock.expect(
                mockEntryGenerator.generateEntry(requestDate, responseDate, mockBackendResponse,
                                                 bytes)).andReturn(mockCacheEntry);
    }

    private void handleBackendResponseReturnsResponse(HttpRequest request, HttpResponse response)
            throws IOException {
        org.easymock.EasyMock.expect(
                impl.handleBackendResponse(host, request, requestDate, responseDate,
                                           mockBackendResponse)).andReturn(response);
    }

    private void storeInCacheWasCalled() {
        impl.storeInCache(host, mockRequest, mockCacheEntry);
    }

    private void storeInCacheWasCalled(CacheEntry entry) {
        impl.storeInCache(host, mockRequest, entry);
    }

    private void responseProtocolValidationIsCalled() throws ClientProtocolException {
        mockResponseProtocolCompliance.ensureProtocolCompliance(mockRequest, mockBackendResponse);
    }

    private void requestProtocolValidationIsCalled() throws Exception {
        org.easymock.EasyMock.expect(
                mockRequestProtocolCompliance.makeRequestCompliant(mockRequest)).andReturn(
                mockRequest);
    }

    private void requestCannotBeMadeCompliantThrows(ProtocolException exception) throws Exception {
        org.easymock.EasyMock.expect(
                mockRequestProtocolCompliance.makeRequestCompliant(mockRequest))
                .andThrow(exception);
    }

    private void mockImplMethods(String... methods) {
        mockedImpl = true;
        impl = EasyMock.createMockBuilder(CachingHttpClient.class).withConstructor(mockBackend,
                                                                                   mockResponsePolicy, mockEntryGenerator, mockExtractor, mockCache,
                                                                                   mockResponseGenerator, mockInvalidator, mockRequestPolicy, mockSuitabilityChecker,
                                                                                   mockConditionalRequestBuilder, mockCacheEntryUpdater,
                                                                                   mockResponseProtocolCompliance, mockRequestProtocolCompliance).addMockedMethods(
                methods).createMock();
    }
}
