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

import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Date;

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
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestCachingExecChain {

    @Mock
    ExecChain mockExecChain;
    @Mock
    ExecRuntime mockExecRuntime;
    @Mock
    HttpCacheStorage mockStorage;
    @Spy
    HttpCache cache = new BasicHttpCache();

    CacheConfig config;
    HttpRoute route;
    HttpHost host;
    ClassicHttpRequest request;
    HttpCacheContext context;
    HttpCacheEntry entry;
    CachingExec impl;

    @Before
    public void setUp() {
        config = CacheConfig.DEFAULT;

        host = new HttpHost("foo.example.com", 80);
        route = new HttpRoute(host);
        request = new BasicClassicHttpRequest("GET", "/stuff");
        context = HttpCacheContext.create();
        entry = HttpTestUtils.makeCacheEntry();

        impl = new CachingExec(cache, null, CacheConfig.DEFAULT);
    }

    public ClassicHttpResponse execute(final ClassicHttpRequest request) throws IOException, HttpException {
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, mockExecRuntime, context);
        return impl.execute(ClassicRequestBuilder.copy(request).build(), scope, mockExecChain);
    }

    @Test
    public void testCacheableResponsesGoIntoCache() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "max-age=3600");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        execute(req2);

        Mockito.verify(mockExecChain).proceed(Mockito.any(), Mockito.any());
        Mockito.verify(cache).createCacheEntry(Mockito.eq(host), RequestEquivalent.eq(req1),
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testOlderCacheableResponsesDoNotGoIntoCache() throws Exception {
        final Date now = new Date();
        final Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"new-etag\"");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "no-cache");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"old-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp2.setHeader("Cache-Control", "max-age=3600");

        final ClassicHttpRequest req3 = HttpTestUtils.makeDefaultRequest();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);
        final ClassicHttpResponse result = execute(req3);

        Assert.assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }

    @Test
    public void testNewerCacheableResponsesReplaceExistingCacheEntry() throws Exception {
        final Date now = new Date();
        final Date fiveSecondsAgo = new Date(now.getTime() - 5 * 1000L);

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(fiveSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Etag", "\"old-etag\"");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-age=0");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"new-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600");

        final ClassicHttpRequest req3 = HttpTestUtils.makeDefaultRequest();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);
        final ClassicHttpResponse result = execute(req3);

        Assert.assertEquals("\"new-etag\"", result.getFirstHeader("ETag").getValue());
    }

    @Test
    public void testNonCacheableResponseIsNotCachedAndIsReturnedAsIs() throws Exception {
        final HttpCache cache = new BasicHttpCache(new HeapResourceFactory(), mockStorage);
        impl = new CachingExec(cache, null, CacheConfig.DEFAULT);

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "no-cache");

        Mockito.when(mockStorage.getEntry(Mockito.any())).thenReturn(null);
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpResponse result = execute(req1);

        Assert.assertTrue(HttpTestUtils.semanticallyTransparent(resp1, result));

        Mockito.verify(mockStorage, Mockito.never()).putEntry(Mockito.any(), Mockito.any());
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForCacheOptionsResponse() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("OPTIONS", "*");
        req.setHeader("Max-Forwards", "0");

        execute(req);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsModuleGeneratedResponseContextForFatallyNoncompliantRequest() throws Exception {
        final ClassicHttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Range", "bytes=0-50");
        req.setHeader("If-Range", "W/\"weak-etag\"");

        execute(req);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE, context.getCacheResponseStatus());
    }

    @Test
    public void testRecordsClientProtocolInViaHeaderIfRequestNotServableFromCache() throws Exception {
        final ClassicHttpRequest originalRequest = new BasicClassicHttpRequest("GET", "/");
        originalRequest.setVersion(HttpVersion.HTTP_1_0);
        final ClassicHttpRequest req = originalRequest;
        req.setHeader("Cache-Control", "no-cache");
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp);

        execute(req);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain).proceed(reqCapture.capture(), Mockito.any());

        final HttpRequest captured = reqCapture.getValue();
        final String via = captured.getFirstHeader("Via").getValue();
        final String proto = via.split("\\s+")[0];
        Assert.assertTrue("http/1.0".equalsIgnoreCase(proto) || "1.0".equalsIgnoreCase(proto));
    }

    @Test
    public void testSetsCacheMissContextIfRequestNotServableFromCache() throws Exception {
        final ClassicHttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Cache-Control", "no-cache");
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp);

        execute(req);
        Assert.assertEquals(CacheResponseStatus.CACHE_MISS, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestNotServableFromCache() throws Exception {
        final ClassicHttpRequest req = new HttpGet("http://foo.example.com/");
        req.setHeader("Cache-Control", "no-cache");
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp);

        final ClassicHttpResponse result = execute(req);
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsViaHeaderOnResponseForCacheMiss() throws Exception {
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpResponse result = execute(req1);
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsCacheHitContextIfRequestServedFromCache() throws Exception {
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        execute(req2);
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderOnResponseIfRequestServedFromCache() throws Exception {
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfModifiedSinceHeaderIfRequestServedFromCache() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
    }

    @Test
    public void testReturns304ForIfModifiedSinceHeaderIf304ResponseInCache() throws Exception {
        final Date now = new Date();
        final Date oneHourAgo = new Date(now.getTime() - 3600 * 1000L);
        final Date inTenMinutes = new Date(now.getTime() + 600 * 1000L);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        req1.addHeader("If-Modified-Since", DateUtils.formatDate(oneHourAgo));
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(oneHourAgo));

        final ClassicHttpResponse resp1 = HttpTestUtils.make304Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-control", "max-age=600");
        resp1.setHeader("Expires", DateUtils.formatDate(inTenMinutes));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Assert.assertFalse(result.containsHeader("Last-Modified"));

        Mockito.verify(mockExecChain).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsLess() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        // The variant has been modified since this date
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());
    }

    @Test
    public void testReturns200ForIfModifiedSinceDateIsInvalid() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAfter = new Date(now.getTime() + 10 * 1000L);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        // invalid date (date in the future)
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAfter));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderIfRequestServedFromCache() throws Exception {
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-None-Match", "*");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());

    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFails() throws Exception {
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "public, max-age=3600");

        req2.addHeader("If-None-Match", "\"abc\"");

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(200, result.getCode());
    }

    @Test
    public void testReturns304ForIfNoneMatchHeaderAndIfModifiedSinceIfRequestServedFromCache() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(new Date()));

        req2.addHeader("If-None-Match", "*");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
    }

    @Test
    public void testReturns200ForIfNoneMatchHeaderFailsIfModifiedSinceIgnored() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");
        req2.addHeader("If-None-Match", "\"abc\"");
        req2.addHeader("If-Modified-Since", DateUtils.formatDate(now));
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(200, result.getCode());
    }

    @Test
    public void testReturns200ForOptionsFollowedByGetIfAuthorizationHeaderAndSharedCache() throws Exception {
        impl = new CachingExec(cache, null, CacheConfig.custom().setSharedCache(true).build());
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
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"get-etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Cache-Control", "public, max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatDate(now));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);
        Assert.assertEquals(200, result.getCode());
    }

    @Test
    public void testSetsValidatedContextIfRequestWasSuccessfullyValidated() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);
        Assert.assertEquals(CacheResponseStatus.VALIDATED, context.getCacheResponseStatus());
    }

    @Test
    public void testSetsViaHeaderIfRequestWasSuccessfullyValidated() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testSetsModuleResponseContextIfValidationRequiredButFailed() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5, must-revalidate");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenThrow(new IOException());

        execute(req2);
        Assert.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE,
            context.getCacheResponseStatus());
    }

    @Test
    public void testSetsModuleResponseContextIfValidationFailsButNotRequired() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenThrow(new IOException());

        execute(req2);
        Assert.assertEquals(CacheResponseStatus.CACHE_HIT, context.getCacheResponseStatus());
    }

    @Test
    public void testSetViaHeaderIfValidationFailsButNotRequired() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenThrow(new IOException());

        final ClassicHttpResponse result = execute(req2);
        Assert.assertNotNull(result.getFirstHeader("Via"));
    }

    @Test
    public void testReturns304ForIfNoneMatchPassesIfRequestServedFromOrigin() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
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

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
    }

    @Test
    public void testReturns200ForIfNoneMatchFailsIfRequestServedFromOrigin() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-None-Match", "\"etag\"");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"newetag\"");
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());
    }

    @Test
    public void testReturns304ForIfModifiedSincePassesIfRequestServedFromOrigin() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
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

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
    }

    @Test
    public void testReturns200ForIfModifiedSinceFailsIfRequestServedFromOrigin() throws Exception {
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(HttpTestUtils.makeBody(128));
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        req2.addHeader("If-Modified-Since", DateUtils.formatDate(tenSecondsAgo));
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp2.setEntity(HttpTestUtils.makeBody(128));
        resp2.setHeader("Content-Length", "128");
        resp2.setHeader("ETag", "\"newetag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Last-Modified", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "public, max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());
    }

    @Test
    public void testVariantMissServerIfReturns304CacheReturns200() throws Exception {
        final Date now = new Date();

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com");
        req1.addHeader("Accept-Encoding", "gzip");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
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

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
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

        final ClassicHttpResponse resp3 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp3.setEntity(HttpTestUtils.makeBody(128));
        resp3.setHeader("Content-Length", "128");
        resp3.setHeader("Etag", "\"gzip_etag\"");
        resp3.setHeader("Date", DateUtils.formatDate(now));
        resp3.setHeader("Vary", "Accept-Encoding");
        resp3.setHeader("Cache-Control", "public, max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpResponse result1 = execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result2 = execute(req2);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp3);

        final ClassicHttpResponse result3 = execute(req3);

        Assert.assertEquals(HttpStatus.SC_OK, result1.getCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getCode());
        Assert.assertEquals(HttpStatus.SC_OK, result3.getCode());
    }

    @Test
    public void testVariantsMissServerReturns304CacheReturns304() throws Exception {
        final Date now = new Date();

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com");
        req1.addHeader("Accept-Encoding", "gzip");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
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

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
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

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpResponse result1 = execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result2 = execute(req2);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp4);

        final ClassicHttpResponse result4 = execute(req4);
        Assert.assertEquals(HttpStatus.SC_OK, result1.getCode());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getCode());
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result4.getCode());

    }

    @Test
    public void testSocketTimeoutExceptionIsNotSilentlyCatched() throws Exception {
        final Date now = new Date();

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com");

        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp1.setEntity(new InputStreamEntity(new InputStream() {
            private boolean closed;

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

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        Assert.assertThrows(SocketTimeoutException.class, () -> {
            final ClassicHttpResponse result1 = execute(req1);
            EntityUtils.toString(result1.getEntity());
        });
    }

    @Test
    public void testIsSharedCache() {
        Assert.assertTrue(config.isSharedCache());
    }

    @Test
    public void testTooLargeResponsesAreNotCached() throws Exception {
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

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, mockExecRuntime, context);
        impl.cacheAndReturnResponse(host, request, originResponse, scope, requestSent, responseReceived);

        Mockito.verify(cache, Mockito.never()).createCacheEntry(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testSmallEnoughResponsesAreCached() throws Exception {
        final HttpCache mockCache = Mockito.mock(HttpCache.class);
        impl = new CachingExec(mockCache, null, CacheConfig.DEFAULT);

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

        Mockito.when(mockCache.createCacheEntry(
                Mockito.eq(host),
                RequestEquivalent.eq(request),
                ResponseEquivalent.eq(response),
                Mockito.any(),
                Mockito.eq(requestSent),
                Mockito.eq(responseReceived))).thenReturn(httpCacheEntry);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, mockExecRuntime, context);
        impl.cacheAndReturnResponse(host, request, originResponse, scope, requestSent, responseReceived);

        Mockito.verify(mockCache).createCacheEntry(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any());
    }

    @Test
    public void testIfOnlyIfCachedAndNoCacheEntryBackendNotCalled() throws Exception {
        request.addHeader("Cache-Control", "only-if-cached");

        final ClassicHttpResponse resp = execute(request);

        Assert.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, resp.getCode());
    }

    @Test
    public void testSetsRouteInContextOnCacheHit() throws Exception {
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(request), Mockito.any())).thenReturn(response);

        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, ctx), mockExecChain);
        Assert.assertEquals(route, ctx.getHttpRoute());
    }

    @Test
    public void testSetsRequestInContextOnCacheHit() throws Exception {
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(request), Mockito.any())).thenReturn(response);

        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, ctx), mockExecChain);
        if (!HttpTestUtils.equivalent(request, ctx.getRequest())) {
            assertSame(request, ctx.getRequest());
        }
    }

    @Test
    public void testSetsResponseInContextOnCacheHit() throws Exception {
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(request), Mockito.any())).thenReturn(response);

        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);
        final ClassicHttpResponse result = impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, ctx), null);
        if (!HttpTestUtils.equivalent(result, ctx.getResponse())) {
            assertSame(result, ctx.getResponse());
        }
    }

    @Test
    public void testSetsRequestSentInContextOnCacheHit() throws Exception {
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(request), Mockito.any())).thenReturn(response);

        final HttpClientContext ctx = HttpClientContext.create();
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, ctx), mockExecChain);
    }

    @Test
    public void testCanCacheAResponseWithoutABody() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        response.setHeader("Date", DateUtils.formatDate(new Date()));
        response.setHeader("Cache-Control", "max-age=300");
        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(request), Mockito.any())).thenReturn(response);

        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);

        Mockito.verify(mockExecChain).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testNoEntityForIfNoneMatchRequestNotYetInCache() throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        req1.addHeader("If-None-Match", "\"etag\"");

        final ClassicHttpResponse resp1 = HttpTestUtils.make304Response();
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("ETag", "\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        final ClassicHttpResponse result = execute(req1);

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Assert.assertNull("The 304 response messages MUST NOT contain a message-body", result.getEntity());
    }

    @Test
    public void testNotModifiedResponseUpdatesCacheEntryWhenNoEntity() throws Exception {

        final Date now = new Date();

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

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpResponse result1 = execute(req1);
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result2 = execute(req2);

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getCode());
        Assert.assertEquals("etag", result1.getFirstHeader("Etag").getValue());
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result2.getCode());
        Assert.assertEquals("etag", result2.getFirstHeader("Etag").getValue());
    }

    @Test
    public void testNotModifiedResponseWithVaryUpdatesCacheEntryWhenNoEntity() throws Exception {

        final Date now = new Date();

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

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpResponse result1 = execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result2 = execute(req2);

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getCode());
        Assert.assertEquals("etag", result1.getFirstHeader("Etag").getValue());
        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result2.getCode());
        Assert.assertEquals("etag", result2.getFirstHeader("Etag").getValue());
    }

    @Test
    public void testDoesNotSend304ForNonConditionalRequest() throws Exception {

        final Date now = new Date();
        final Date inOneMinute = new Date(System.currentTimeMillis() + 60000);

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

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpResponse result1 = execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result2 = execute(req2);

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, result1.getCode());
        Assert.assertNull(result1.getEntity());
        Assert.assertEquals(HttpStatus.SC_OK, result2.getCode());
        Assert.assertNotNull(result2.getEntity());
    }

    @Test
    public void testUsesVirtualHostForCacheKey() throws Exception {
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Cache-Control", "max-age=3600");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(response);

        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);

        Mockito.verify(mockExecChain, Mockito.times(1)).proceed(Mockito.any(), Mockito.any());

        request.setAuthority(new URIAuthority("bar.example.com"));
        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());

        impl.execute(request, new ExecChain.Scope("test", route, request, mockExecRuntime, context), mockExecChain);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

}
