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
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.easymock.classextension.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class TestCacheInvalidator {

    private static final ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP", 1, 1);

    private CacheInvalidator impl;
    private HttpCacheStorage mockStorage;
    private HttpHost host;
    private URIExtractor extractor;
    private CacheEntry mockEntry;

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com");
        mockStorage = EasyMock.createMock(HttpCacheStorage.class);
        extractor = new URIExtractor();
        mockEntry = EasyMock.createMock(CacheEntry.class);

        impl = new CacheInvalidator(extractor, mockStorage);
    }

    private void replayMocks() {
        EasyMock.replay(mockStorage);
        EasyMock.replay(mockEntry);
    }

    private void verifyMocks() {
        EasyMock.verify(mockStorage);
        EasyMock.verify(mockEntry);
    }

    // Tests
    @Test
    public void testInvalidatesRequestsThatArentGETorHEAD() throws Exception {
        HttpRequest request = new BasicHttpRequest("POST","/path", HTTP_1_1);

        final String theUri = "http://foo.example.com/path";
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        cacheReturnsEntryForUri(theUri);
        entryIsRemoved(theUri);
        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testInvalidatesUrisInContentLocationHeadersOnPUTs() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("PUT","/",HTTP_1_1);
        request.setEntity(HttpTestUtils.makeBody(128));
        request.setHeader("Content-Length","128");

        String contentLocation = "http://foo.example.com/content";
        request.setHeader("Content-Location",contentLocation);

        final String theUri = "http://foo.example.com/";
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        cacheReturnsEntryForUri(theUri);
        entryIsRemoved(theUri);
        entryIsRemoved(contentLocation);

        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testInvalidatesUrisInLocationHeadersOnPUTs() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("PUT","/",HTTP_1_1);
        request.setEntity(HttpTestUtils.makeBody(128));
        request.setHeader("Content-Length","128");

        String contentLocation = "http://foo.example.com/content";
        request.setHeader("Location",contentLocation);

        final String theUri = "http://foo.example.com/";
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        cacheReturnsEntryForUri(theUri);
        entryIsRemoved(theUri);
        entryIsRemoved(contentLocation);

        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testInvalidatesRelativeUrisInContentLocationHeadersOnPUTs() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("PUT","/",HTTP_1_1);
        request.setEntity(HttpTestUtils.makeBody(128));
        request.setHeader("Content-Length","128");

        String contentLocation = "http://foo.example.com/content";
        String relativePath = "/content";
        request.setHeader("Content-Location",relativePath);

        final String theUri = "http://foo.example.com/";
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        cacheReturnsEntryForUri(theUri);
        entryIsRemoved(theUri);
        entryIsRemoved(contentLocation);

        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testDoesNotInvalidateUrisInContentLocationHeadersOnPUTsToDifferentHosts() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("PUT","/",HTTP_1_1);
        request.setEntity(HttpTestUtils.makeBody(128));
        request.setHeader("Content-Length","128");

        String contentLocation = "http://bar.example.com/content";
        request.setHeader("Content-Location",contentLocation);

        final String theUri = "http://foo.example.com/";
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        cacheReturnsEntryForUri(theUri);
        entryIsRemoved(theUri);

        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testDoesNotInvalidateGETRequest() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET","/",HTTP_1_1);

        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testDoesNotInvalidateHEADRequest() throws Exception {
        HttpRequest request = new BasicHttpRequest("HEAD","/",HTTP_1_1);

        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testInvalidatesRequestsWithClientCacheControlHeaders() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Cache-Control","no-cache");

        final String theUri = "http://foo.example.com/";
        cacheReturnsEntryForUri(theUri);
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        entryIsRemoved(theUri);
        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testInvalidatesRequestsWithClientPragmaHeaders() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Pragma","no-cache");

        final String theUri = "http://foo.example.com/";
        cacheReturnsEntryForUri(theUri);
        Set<String> variantURIs = new HashSet<String>();
        cacheEntryHasVariantURIs(variantURIs);

        entryIsRemoved(theUri);
        replayMocks();

        impl.flushInvalidatedCacheEntries(host, request);

        verifyMocks();
    }

    @Test
    public void testVariantURIsAreFlushedAlso() throws Exception {
        HttpRequest request = new BasicHttpRequest("POST","/",HTTP_1_1);

        final String theUri = "http://foo.example.com/";
        final String variantUri = "theVariantURI";

        Set<String> listOfURIs = new HashSet<String>();
        listOfURIs.add(variantUri);

        cacheReturnsEntryForUri(theUri);
        cacheEntryHasVariantURIs(listOfURIs);

        entryIsRemoved(variantUri);
        entryIsRemoved(theUri);

        replayMocks();
        impl.flushInvalidatedCacheEntries(host, request);
        verifyMocks();
    }

    @Test(expected=IOException.class)
    public void testCacheFlushException() throws Exception {
        HttpRequest request = new BasicHttpRequest("POST","/",HTTP_1_1);
        String theURI = "http://foo.example.com/";

        cacheReturnsExceptionForUri(theURI);

        replayMocks();
        impl.flushInvalidatedCacheEntries(host, request);
    }

    // Expectations


    private void cacheEntryHasVariantURIs(Set<String> variantURIs) {
        org.easymock.EasyMock.expect(mockEntry.getVariantURIs()).andReturn(variantURIs);
    }

    private void cacheReturnsEntryForUri(String theUri) throws IOException {
        org.easymock.EasyMock.expect(mockStorage.getEntry(theUri)).andReturn(mockEntry);
    }

    private void cacheReturnsExceptionForUri(String theUri) throws IOException {
        org.easymock.EasyMock.expect(mockStorage.getEntry(theUri)).andThrow(
                new IOException("TOTAL FAIL"));
    }

    private void entryIsRemoved(String theUri) throws IOException {
        mockStorage.removeEntry(theUri);
    }

}