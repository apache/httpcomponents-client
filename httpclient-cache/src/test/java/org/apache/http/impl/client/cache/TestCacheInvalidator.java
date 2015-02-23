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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;

public class TestCacheInvalidator {

    private static final ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP", 1, 1);

    private CacheInvalidator impl;
    private HttpCacheStorage mockStorage;
    private HttpHost host;
    private CacheKeyGenerator cacheKeyGenerator;
    private HttpCacheEntry mockEntry;
    private HttpRequest request;
    private HttpResponse response;

    private Date now;
    private Date tenSecondsAgo;

    @Before
    public void setUp() {
        now = new Date();
        tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        host = new HttpHost("foo.example.com");
        mockStorage = mock(HttpCacheStorage.class);
        cacheKeyGenerator = new CacheKeyGenerator();
        mockEntry = mock(HttpCacheEntry.class);
        request = HttpTestUtils.makeDefaultRequest();
        response = HttpTestUtils.make200Response();

        impl = new CacheInvalidator(cacheKeyGenerator, mockStorage);
    }

    // Tests
    @Test
    public void testInvalidatesRequestsThatArentGETorHEAD() throws Exception {
        request = new BasicHttpRequest("POST","/path", HTTP_1_1);
        final String theUri = "http://foo.example.com:80/path";
        final Map<String,String> variantMap = new HashMap<String,String>();
        cacheEntryHasVariantMap(variantMap);

        cacheReturnsEntryForUri(theUri);

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(theUri);
        verify(mockStorage).removeEntry(theUri);
    }

    @Test
    public void testInvalidatesUrisInContentLocationHeadersOnPUTs() throws Exception {
        final HttpEntityEnclosingRequest putRequest = new BasicHttpEntityEnclosingRequest("PUT","/",HTTP_1_1);
        putRequest.setEntity(HttpTestUtils.makeBody(128));
        putRequest.setHeader("Content-Length","128");

        final String contentLocation = "http://foo.example.com/content";
        putRequest.setHeader("Content-Location", contentLocation);

        final String theUri = "http://foo.example.com:80/";
        cacheEntryHasVariantMap(new HashMap<String,String>());

        cacheReturnsEntryForUri(theUri);

        impl.flushInvalidatedCacheEntries(host, putRequest);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(theUri);
        verify(mockStorage).removeEntry(theUri);
        verify(mockStorage).removeEntry("http://foo.example.com:80/content");
    }

    @Test
    public void testInvalidatesUrisInLocationHeadersOnPUTs() throws Exception {
        final HttpEntityEnclosingRequest putRequest = new BasicHttpEntityEnclosingRequest("PUT","/",HTTP_1_1);
        putRequest.setEntity(HttpTestUtils.makeBody(128));
        putRequest.setHeader("Content-Length","128");

        final String contentLocation = "http://foo.example.com/content";
        putRequest.setHeader("Location",contentLocation);

        final String theUri = "http://foo.example.com:80/";
        cacheEntryHasVariantMap(new HashMap<String,String>());

        cacheReturnsEntryForUri(theUri);

        impl.flushInvalidatedCacheEntries(host, putRequest);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(theUri);
        verify(mockStorage).removeEntry(theUri);
        verify(mockStorage).removeEntry(cacheKeyGenerator.canonicalizeUri(contentLocation));
    }

    @Test
    public void testInvalidatesRelativeUrisInContentLocationHeadersOnPUTs() throws Exception {
        final HttpEntityEnclosingRequest putRequest = new BasicHttpEntityEnclosingRequest("PUT","/",HTTP_1_1);
        putRequest.setEntity(HttpTestUtils.makeBody(128));
        putRequest.setHeader("Content-Length","128");

        final String relativePath = "/content";
        putRequest.setHeader("Content-Location",relativePath);

        final String theUri = "http://foo.example.com:80/";
        cacheEntryHasVariantMap(new HashMap<String,String>());

        cacheReturnsEntryForUri(theUri);

        impl.flushInvalidatedCacheEntries(host, putRequest);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(theUri);
        verify(mockStorage).removeEntry(theUri);
        verify(mockStorage).removeEntry("http://foo.example.com:80/content");
    }

    @Test
    public void testDoesNotInvalidateUrisInContentLocationHeadersOnPUTsToDifferentHosts() throws Exception {
        final HttpEntityEnclosingRequest putRequest = new BasicHttpEntityEnclosingRequest("PUT","/",HTTP_1_1);
        putRequest.setEntity(HttpTestUtils.makeBody(128));
        putRequest.setHeader("Content-Length","128");

        final String contentLocation = "http://bar.example.com/content";
        putRequest.setHeader("Content-Location",contentLocation);

        final String theUri = "http://foo.example.com:80/";
        cacheEntryHasVariantMap(new HashMap<String,String>());

        cacheReturnsEntryForUri(theUri);

        impl.flushInvalidatedCacheEntries(host, putRequest);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(theUri);
        verify(mockStorage).removeEntry(theUri);
    }

    @Test
    public void testDoesNotInvalidateGETRequest() throws Exception {
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockStorage).getEntry("http://foo.example.com:80/");
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateHEADRequest() throws Exception {
        request = new BasicHttpRequest("HEAD","/",HTTP_1_1);
        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockStorage).getEntry("http://foo.example.com:80/");
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testInvalidatesHEADCacheEntryIfSubsequentGETRequestsAreMadeToTheSameURI() throws Exception {
        impl = new CacheInvalidator(cacheKeyGenerator, mockStorage);
        final String theURI = "http://foo.example.com:80/";
        request = new BasicHttpRequest("GET", theURI,HTTP_1_1);

        cacheEntryisForMethod("HEAD");
        cacheEntryHasVariantMap(new HashMap<String, String>());
        cacheReturnsEntryForUri(theURI);

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockEntry).getRequestMethod();
        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(theURI);
        verify(mockStorage).removeEntry(theURI);
    }

    @Test
    public void testInvalidatesVariantHEADCacheEntriesIfSubsequentGETRequestsAreMadeToTheSameURI() throws Exception {
        impl = new CacheInvalidator(cacheKeyGenerator, mockStorage);
        final String theURI = "http://foo.example.com:80/";
        request = new BasicHttpRequest("GET", theURI,HTTP_1_1);
        final String theVariantKey = "{Accept-Encoding=gzip%2Cdeflate&User-Agent=Apache-HttpClient}";
        final String theVariantURI = "{Accept-Encoding=gzip%2Cdeflate&User-Agent=Apache-HttpClient}http://foo.example.com:80/";
        final Map<String, String> variants = HttpTestUtils.makeDefaultVariantMap(theVariantKey, theVariantURI);

        cacheEntryisForMethod("HEAD");
        cacheEntryHasVariantMap(variants);
        cacheReturnsEntryForUri(theURI);

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockEntry).getRequestMethod();
        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(theURI);
        verify(mockStorage).removeEntry(theURI);
        verify(mockStorage).removeEntry(theVariantURI);
    }

    @Test
    public void testDoesNotInvalidateHEADCacheEntry() throws Exception {
        final String theURI = "http://foo.example.com:80/";
        request = new BasicHttpRequest("HEAD", theURI,HTTP_1_1);

        cacheReturnsEntryForUri(theURI);

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateHEADCacheEntryIfSubsequentHEADRequestsAreMadeToTheSameURI() throws Exception {
        impl = new CacheInvalidator(cacheKeyGenerator, mockStorage);
        final String theURI = "http://foo.example.com:80/";
        request = new BasicHttpRequest("HEAD", theURI,HTTP_1_1);

        cacheReturnsEntryForUri(theURI);

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateGETCacheEntryIfSubsequentGETRequestsAreMadeToTheSameURI() throws Exception {
        impl = new CacheInvalidator(cacheKeyGenerator, mockStorage);
        final String theURI = "http://foo.example.com:80/";
        request = new BasicHttpRequest("GET", theURI,HTTP_1_1);

        cacheEntryisForMethod("GET");
        cacheReturnsEntryForUri(theURI);

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockEntry).getRequestMethod();
        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateRequestsWithClientCacheControlHeaders() throws Exception {
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Cache-Control","no-cache");

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockStorage).getEntry("http://foo.example.com:80/");
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateRequestsWithClientPragmaHeaders() throws Exception {
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Pragma","no-cache");

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockStorage).getEntry("http://foo.example.com:80/");
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testVariantURIsAreFlushedAlso() throws Exception {
        request = new BasicHttpRequest("POST","/",HTTP_1_1);
        final String theUri = "http://foo.example.com:80/";
        final String variantUri = "theVariantURI";
        final Map<String,String> mapOfURIs = HttpTestUtils.makeDefaultVariantMap(variantUri, variantUri);

        cacheReturnsEntryForUri(theUri);
        cacheEntryHasVariantMap(mapOfURIs);

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockStorage).getEntry(theUri);
        verify(mockEntry).getVariantMap();
        verify(mockStorage).removeEntry(variantUri);
        verify(mockStorage).removeEntry(theUri);
    }

    @Test
    public void testCacheFlushException() throws Exception {
        request = new BasicHttpRequest("POST","/",HTTP_1_1);
        final String theURI = "http://foo.example.com:80/";

        cacheReturnsExceptionForUri(theURI);

        impl.flushInvalidatedCacheEntries(host, request);

        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushForResponsesWithoutContentLocation()
            throws Exception {
        impl.flushInvalidatedCacheEntries(host, request, response);

        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntryIfFresherAndSpecifiedByContentLocation()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verify(mockStorage).removeEntry(theURI);
    }

    @Test
    public void flushesEntryIfFresherAndSpecifiedByLocation()
            throws Exception {
        response.setStatusCode(201);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verify(mockStorage).removeEntry(theURI);
    }

    @Test
    public void doesNotFlushEntryForUnsuccessfulResponse()
            throws Exception {
        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST, "Bad Request");
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntryIfFresherAndSpecifiedByNonCanonicalContentLocation()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String cacheKey = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", "http://foo.example.com/bar");

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(cacheKey)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(cacheKey);
        verify(mockStorage).removeEntry(cacheKey);
    }

    @Test
    public void flushesEntryIfFresherAndSpecifiedByRelativeContentLocation()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String cacheKey = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", "/bar");

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(cacheKey)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(cacheKey);
        verify(mockStorage).removeEntry(cacheKey);
    }

    @Test
    public void doesNotFlushEntryIfContentLocationFromDifferentHost()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String cacheKey = "http://baz.example.com:80/bar";
        response.setHeader("Content-Location", cacheKey);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(cacheKey)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(cacheKey);
        verifyNoMoreInteractions(mockStorage);
    }



    @Test
    public void doesNotFlushEntrySpecifiedByContentLocationIfEtagsMatch()
            throws Exception {
        response.setHeader("ETag","\"same-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"same-etag\"")
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);
        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntrySpecifiedByContentLocationIfOlder()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(now)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntryIfNotInCache()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        when(mockStorage.getEntry(theURI)).thenReturn(null);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntrySpecifiedByContentLocationIfResponseHasNoEtag()
            throws Exception {
        response.removeHeaders("ETag");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntrySpecifiedByContentLocationIfEntryHasNoEtag()
            throws Exception {
        response.setHeader("ETag", "\"some-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntrySpecifiedByContentLocationIfResponseHasNoDate()
            throws Exception {
        response.setHeader("ETag", "\"new-etag\"");
        response.removeHeaders("Date");
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verify(mockStorage).removeEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntrySpecifiedByContentLocationIfEntryHasNoDate()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verify(mockStorage).removeEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntrySpecifiedByContentLocationIfResponseHasMalformedDate()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", "blarg");
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo))
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verify(mockStorage).removeEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntrySpecifiedByContentLocationIfEntryHasMalformedDate()
            throws Exception {
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String theURI = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", theURI);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", "foo")
        });

        when(mockStorage.getEntry(theURI)).thenReturn(entry);

        impl.flushInvalidatedCacheEntries(host, request, response);

        verify(mockStorage).getEntry(theURI);
        verify(mockStorage).removeEntry(theURI);
        verifyNoMoreInteractions(mockStorage);
    }


    // Expectations
    private void cacheEntryHasVariantMap(final Map<String,String> variantMap) {
        when(mockEntry.getVariantMap()).thenReturn(variantMap);
    }

    private void cacheReturnsEntryForUri(final String theUri) throws IOException {
        when(mockStorage.getEntry(theUri)).thenReturn(mockEntry);
    }

    private void cacheReturnsExceptionForUri(final String theUri) throws IOException {
        when(mockStorage.getEntry(theUri)).thenThrow(
                new IOException("TOTAL FAIL"));
    }

    private void cacheEntryisForMethod(final String httpMethod) {
        when(mockEntry.getRequestMethod()).thenReturn(httpMethod);
    }
}
