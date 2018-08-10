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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class TestDefaultCacheInvalidator {

    private DefaultCacheInvalidator impl;
    private HttpHost host;
    @Mock
    private HttpCacheEntry mockEntry;
    @Mock
    private Resolver<URI, String> cacheKeyResolver;
    @Mock
    private HttpCacheStorage mockStorage;

    private Date now;
    private Date tenSecondsAgo;

    @Before
    public void setUp() {
        now = new Date();
        tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        when(cacheKeyResolver.resolve(ArgumentMatchers.<URI>any())).thenAnswer(new Answer<String>() {

            @Override
            public String answer(final InvocationOnMock invocation) throws Throwable {
                final URI uri = invocation.getArgument(0);
                return HttpCacheSupport.normalize(uri).toASCIIString();
            }

        });

        host = new HttpHost("foo.example.com");
        impl = new DefaultCacheInvalidator();
    }

    // Tests
    @Test
    public void testInvalidatesRequestsThatArentGETorHEAD() throws Exception {
        final HttpRequest request = new BasicHttpRequest("POST","/path");
        final String key = "http://foo.example.com:80/path";
        final Map<String,String> variantMap = new HashMap<>();
        cacheEntryHasVariantMap(variantMap);

        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
    }

    @Test
    public void testInvalidatesUrisInContentLocationHeadersOnPUTs() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT","/");
        request.setHeader("Content-Length","128");

        final String contentLocation = "http://foo.example.com/content";
        request.setHeader("Content-Location", contentLocation);

        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        cacheEntryHasVariantMap(new HashMap<String,String>());

        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
        verify(mockStorage).removeEntry("http://foo.example.com:80/content");
    }

    @Test
    public void testInvalidatesUrisInLocationHeadersOnPUTs() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT","/");
        request.setHeader("Content-Length","128");

        final String contentLocation = "http://foo.example.com/content";
        request.setHeader("Location",contentLocation);

        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        cacheEntryHasVariantMap(new HashMap<String,String>());

        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
        verify(mockStorage).removeEntry("http://foo.example.com:80/content");
    }

    @Test
    public void testInvalidatesRelativeUrisInContentLocationHeadersOnPUTs() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT","/");
        request.setHeader("Content-Length","128");

        final String relativePath = "/content";
        request.setHeader("Content-Location",relativePath);

        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        cacheEntryHasVariantMap(new HashMap<String,String>());

        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
        verify(mockStorage).removeEntry("http://foo.example.com:80/content");
    }

    @Test
    public void testDoesNotInvalidateUrisInContentLocationHeadersOnPUTsToDifferentHosts() throws Exception {
        final HttpRequest request = new BasicHttpRequest("PUT","/");
        request.setHeader("Content-Length","128");

        final String contentLocation = "http://bar.example.com/content";
        request.setHeader("Content-Location",contentLocation);

        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        cacheEntryHasVariantMap(new HashMap<String,String>());

        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
    }

    @Test
    public void testDoesNotInvalidateGETRequest() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET","/");
        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry("http://foo.example.com:80/");
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateHEADRequest() throws Exception {
        final HttpRequest request = new BasicHttpRequest("HEAD","/");
        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry("http://foo.example.com:80/");
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testInvalidatesHEADCacheEntryIfSubsequentGETRequestsAreMadeToTheSameURI() throws Exception {
        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        final HttpRequest request = new BasicHttpRequest("GET", uri);

        cacheEntryisForMethod("HEAD");
        cacheEntryHasVariantMap(new HashMap<String, String>());
        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockEntry).getRequestMethod();
        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
    }

    @Test
    public void testInvalidatesVariantHEADCacheEntriesIfSubsequentGETRequestsAreMadeToTheSameURI() throws Exception {
        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        final HttpRequest request = new BasicHttpRequest("GET", uri);
        final String theVariantKey = "{Accept-Encoding=gzip%2Cdeflate&User-Agent=Apache-HttpClient}";
        final String theVariantURI = "{Accept-Encoding=gzip%2Cdeflate&User-Agent=Apache-HttpClient}http://foo.example.com:80/";
        final Map<String, String> variants = HttpTestUtils.makeDefaultVariantMap(theVariantKey, theVariantURI);

        cacheEntryisForMethod("HEAD");
        cacheEntryHasVariantMap(variants);
        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockEntry).getRequestMethod();
        verify(mockEntry).getVariantMap();
        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
        verify(mockStorage).removeEntry(theVariantURI);
    }

    @Test
    public void testDoesNotInvalidateHEADCacheEntry() throws Exception {
        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        final HttpRequest request = new BasicHttpRequest("HEAD", uri);

        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateHEADCacheEntryIfSubsequentHEADRequestsAreMadeToTheSameURI() throws Exception {
        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        final HttpRequest request = new BasicHttpRequest("HEAD", uri);

        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateGETCacheEntryIfSubsequentGETRequestsAreMadeToTheSameURI() throws Exception {
        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        final HttpRequest request = new BasicHttpRequest("GET", uri);

        cacheEntryisForMethod("GET");
        cacheReturnsEntryForUri(key);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockEntry).getRequestMethod();
        verify(mockStorage).getEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateRequestsWithClientCacheControlHeaders() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET","/");
        request.setHeader("Cache-Control","no-cache");

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry("http://foo.example.com:80/");
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testDoesNotInvalidateRequestsWithClientPragmaHeaders() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET","/");
        request.setHeader("Pragma","no-cache");

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry("http://foo.example.com:80/");
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void testVariantURIsAreFlushedAlso() throws Exception {
        final HttpRequest request = new BasicHttpRequest("POST","/");
        final URI uri = new URI("http://foo.example.com:80/");
        final String key = uri.toASCIIString();
        final String variantUri = "theVariantURI";
        final Map<String,String> mapOfURIs = HttpTestUtils.makeDefaultVariantMap(variantUri, variantUri);

        cacheReturnsEntryForUri(key);
        cacheEntryHasVariantMap(mapOfURIs);

        impl.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verify(mockEntry).getVariantMap();
        verify(mockStorage).removeEntry(variantUri);
        verify(mockStorage).removeEntry(key);
    }

    @Test
    public void doesNotFlushForResponsesWithoutContentLocation() throws Exception {
        final HttpRequest request = new BasicHttpRequest("POST","/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntryIfFresherAndSpecifiedByContentLocation() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
    }

    @Test
    public void flushesEntryIfFresherAndSpecifiedByLocation() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(201);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
    }

    @Test
    public void doesNotFlushEntryForUnsuccessfulResponse() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntryIfFresherAndSpecifiedByNonCanonicalContentLocation() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String cacheKey = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", "http://foo.example.com/bar");

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(cacheKey)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(cacheKey);
        verify(mockStorage).removeEntry(cacheKey);
    }

    @Test
    public void flushesEntryIfFresherAndSpecifiedByRelativeContentLocation() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String cacheKey = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", "/bar");

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(cacheKey)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(cacheKey);
        verify(mockStorage).removeEntry(cacheKey);
    }

    @Test
    public void doesNotFlushEntryIfContentLocationFromDifferentHost() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String cacheKey = "http://baz.example.com:80/bar";
        response.setHeader("Content-Location", cacheKey);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntrySpecifiedByContentLocationIfEtagsMatch() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"same-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"same-etag\"")
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);
        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntrySpecifiedByContentLocationIfOlder() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(now)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntryIfNotInCache() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        when(mockStorage.getEntry(key)).thenReturn(null);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntrySpecifiedByContentLocationIfResponseHasNoEtag() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.removeHeaders("ETag");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void doesNotFlushEntrySpecifiedByContentLocationIfEntryHasNoEtag() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag", "\"some-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntrySpecifiedByContentLocationIfResponseHasNoDate() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag", "\"new-etag\"");
        response.removeHeaders("Date");
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntrySpecifiedByContentLocationIfEntryHasNoDate() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("ETag", "\"old-etag\"")
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntrySpecifiedByContentLocationIfResponseHasMalformedDate() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", "blarg");
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo))
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }

    @Test
    public void flushesEntrySpecifiedByContentLocationIfEntryHasMalformedDate() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.setHeader("ETag","\"new-etag\"");
        response.setHeader("Date", DateUtils.formatDate(now));
        final String key = "http://foo.example.com:80/bar";
        response.setHeader("Content-Location", key);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
                new BasicHeader("ETag", "\"old-etag\""),
                new BasicHeader("Date", "foo")
        });

        when(mockStorage.getEntry(key)).thenReturn(entry);

        impl.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyResolver, mockStorage);

        verify(mockStorage).getEntry(key);
        verify(mockStorage).removeEntry(key);
        verifyNoMoreInteractions(mockStorage);
    }


    // Expectations
    private void cacheEntryHasVariantMap(final Map<String,String> variantMap) {
        when(mockEntry.getVariantMap()).thenReturn(variantMap);
    }

    private void cacheReturnsEntryForUri(final String key) throws IOException {
        when(mockStorage.getEntry(key)).thenReturn(mockEntry);
    }

    private void cacheEntryisForMethod(final String httpMethod) {
        when(mockEntry.getRequestMethod()).thenReturn(httpMethod);
    }
}
