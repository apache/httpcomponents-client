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
package org.apache.http.client.cache.impl;

import java.util.HashSet;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.client.cache.HttpCacheOperationException;
import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.cache.impl.CacheEntry;
import org.apache.http.client.cache.impl.CacheInvalidator;
import org.apache.http.client.cache.impl.URIExtractor;
import org.easymock.classextension.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class TestCacheInvalidator {

    private CacheInvalidator impl;
    private HttpCache<CacheEntry> mockCache;
    private Header mockHeader;
    private Header[] mockHeaderArray = new Header[1];
    private HttpHost host;
    private HttpRequest mockRequest;
    private RequestLine mockRequestLine;
    private URIExtractor mockExtractor;
    private CacheEntry mockEntry;

    private boolean mockedImpl;
    private HeaderElement mockElement;
    private HeaderElement[] mockElementArray = new HeaderElement[1];

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com");
        mockCache = EasyMock.createMock(HttpCache.class);
        mockExtractor = EasyMock.createMock(URIExtractor.class);
        mockHeader = EasyMock.createMock(Header.class);
        mockElement = EasyMock.createMock(HeaderElement.class);
        mockRequest = EasyMock.createMock(HttpRequest.class);
        mockRequestLine = EasyMock.createMock(RequestLine.class);
        mockEntry = EasyMock.createMock(CacheEntry.class);
        mockHeaderArray[0] = mockHeader;
        mockElementArray[0] = mockElement;

        impl = new CacheInvalidator(mockExtractor, mockCache);
    }

    private void mockImplMethods(String... methods) {
        mockedImpl = true;
        impl = EasyMock.createMockBuilder(CacheInvalidator.class).withConstructor(mockExtractor,
                mockCache).addMockedMethods(methods).createMock();
    }

    private void replayMocks() {
        EasyMock.replay(mockCache);
        EasyMock.replay(mockExtractor);
        EasyMock.replay(mockHeader);
        EasyMock.replay(mockRequest);
        EasyMock.replay(mockRequestLine);
        EasyMock.replay(mockEntry);
        EasyMock.replay(mockElement);

        if (mockedImpl)
            EasyMock.replay(impl);
    }

    private void verifyMocks() {
        EasyMock.verify(mockCache);
        EasyMock.verify(mockExtractor);
        EasyMock.verify(mockHeader);
        EasyMock.verify(mockRequest);
        EasyMock.verify(mockRequestLine);
        EasyMock.verify(mockEntry);
        EasyMock.verify(mockElement);

        if (mockedImpl)
            EasyMock.verify(impl);
    }

    // Tests
    @Test
    public void testInvalidatesRequestsThatArentGETorHEAD() throws Exception {
        final String theUri = "theUri";
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        cacheReturnsEntryForUri(theUri);
        requestLineIsRead();
        requestMethodIs("POST");
        extractorReturns(theUri);
        entryIsRemoved(theUri);
        replayMocks();

        impl.flushInvalidatedCacheEntries(host, mockRequest);

        verifyMocks();
    }

    @Test
    public void testDoesNotInvalidateGETRequest() {

        requestLineIsRead();
        requestMethodIs("GET");
        requestContainsCacheControlHeader(null);
        requestContainsPragmaHeader(null);
        replayMocks();

        impl.flushInvalidatedCacheEntries(host, mockRequest);

        verifyMocks();
    }

    @Test
    public void testDoesNotInvalidateHEADRequest() {

        requestLineIsRead();
        requestMethodIs("HEAD");
        requestContainsCacheControlHeader(null);
        requestContainsPragmaHeader(null);
        replayMocks();

        impl.flushInvalidatedCacheEntries(host, mockRequest);

        verifyMocks();
    }

    @Test
    public void testInvalidatesRequestsWithClientCacheControlHeaders() throws Exception {
        final String theUri = "theUri";
        extractorReturns(theUri);
        cacheReturnsEntryForUri(theUri);
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        requestLineIsRead();
        requestMethodIs("GET");
        requestContainsCacheControlHeader(mockHeaderArray);

        org.easymock.EasyMock.expect(mockHeader.getElements()).andReturn(mockElementArray);
        org.easymock.EasyMock.expect(mockElement.getName()).andReturn("no-cache").anyTimes();

        entryIsRemoved(theUri);
        replayMocks();

        impl.flushInvalidatedCacheEntries(host, mockRequest);

        verifyMocks();
    }

    @Test
    public void testInvalidatesRequestsWithClientPragmaHeaders() throws Exception {
        final String theUri = "theUri";
        extractorReturns(theUri);
        cacheReturnsEntryForUri(theUri);
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        requestLineIsRead();
        requestMethodIs("GET");
        requestContainsCacheControlHeader(null);
        requestContainsPragmaHeader(mockHeader);
        entryIsRemoved(theUri);
        replayMocks();

        impl.flushInvalidatedCacheEntries(host, mockRequest);

        verifyMocks();
    }

    @Test
    public void testVariantURIsAreFlushedAlso() throws HttpCacheOperationException {
        final String theUri = "theUri";
        final String variantUri = "theVariantURI";

        Set<String> listOfURIs = new HashSet<String>();
        listOfURIs.add(variantUri);

        extractorReturns(theUri);
        cacheReturnsEntryForUri(theUri);
        cacheEntryHasVariantURIs(listOfURIs);

        entryIsRemoved(variantUri);
        entryIsRemoved(theUri);

        mockImplMethods("requestShouldNotBeCached");
        org.easymock.EasyMock.expect(impl.requestShouldNotBeCached(mockRequest)).andReturn(true);

        replayMocks();
        impl.flushInvalidatedCacheEntries(host, mockRequest);
        verifyMocks();
    }

    @Test
    public void testCacheFlushException() throws Exception {
        String theURI = "theURI";

        mockImplMethods("requestShouldNotBeCached");
        org.easymock.EasyMock.expect(impl.requestShouldNotBeCached(mockRequest)).andReturn(true);

        extractorReturns(theURI);
        cacheReturnsExceptionForUri(theURI);

        replayMocks();
        impl.flushInvalidatedCacheEntries(host, mockRequest);
        verifyMocks();
    }

    // Expectations
    private void requestContainsPragmaHeader(Header header) {
        org.easymock.EasyMock.expect(mockRequest.getFirstHeader("Pragma")).andReturn(header);
    }

    private void requestMethodIs(String s) {
        org.easymock.EasyMock.expect(mockRequestLine.getMethod()).andReturn(s);
    }

    private void cacheEntryHasVariantURIs(Set<String> variantURIs) {
        org.easymock.EasyMock.expect(mockEntry.getVariantURIs()).andReturn(variantURIs);
    }

    private void cacheReturnsEntryForUri(String theUri) throws HttpCacheOperationException {
        org.easymock.EasyMock.expect(mockCache.getEntry(theUri)).andReturn(mockEntry);
    }

    private void cacheReturnsExceptionForUri(String theUri) throws HttpCacheOperationException {
        org.easymock.EasyMock.expect(mockCache.getEntry(theUri)).andThrow(
                new HttpCacheOperationException("TOTAL FAIL"));
    }

    private void extractorReturns(String theUri) {
        org.easymock.EasyMock.expect(mockExtractor.getURI(host, mockRequest)).andReturn(theUri);
    }

    private void entryIsRemoved(String theUri) throws HttpCacheOperationException {
        mockCache.removeEntry(theUri);
    }

    private void requestLineIsRead() {
        org.easymock.EasyMock.expect(mockRequest.getRequestLine()).andReturn(mockRequestLine);
    }

    private void requestContainsCacheControlHeader(Header[] header) {
        org.easymock.EasyMock.expect(mockRequest.getHeaders("Cache-Control")).andReturn(header);
    }
}