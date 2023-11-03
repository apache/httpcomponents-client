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

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.MessageSupport;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/*
 * This test class captures functionality required to achieve conditional
 * compliance with the HTTP/1.1 caching protocol (MUST and MUST NOT behaviors).
 */
public class TestProtocolRequirements {

    static final int MAX_BYTES = 1024;
    static final int MAX_ENTRIES = 100;
    static final int ENTITY_LENGTH = 128;

    HttpHost host;
    HttpRoute route;
    HttpEntity body;
    HttpClientContext context;
    @Mock
    ExecChain mockExecChain;
    @Mock
    ExecRuntime mockExecRuntime;
    @Mock
    HttpCache mockCache;
    ClassicHttpRequest request;
    ClassicHttpResponse originResponse;
    CacheConfig config;
    CachingExec impl;
    HttpCache cache;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        host = new HttpHost("foo.example.com", 80);

        route = new HttpRoute(host);

        body = HttpTestUtils.makeBody(ENTITY_LENGTH);

        request = new BasicClassicHttpRequest("GET", "/");

        context = HttpClientContext.create();

        originResponse = HttpTestUtils.make200Response();

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .build();

        cache = new BasicHttpCache(config);
        impl = new CachingExec(cache, null, config);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);
    }

    public ClassicHttpResponse execute(final ClassicHttpRequest request) throws IOException, HttpException {
        return impl.execute(
                ClassicRequestBuilder.copy(request).build(),
                new ExecChain.Scope("test", route, request, mockExecRuntime, context),
                mockExecChain);
    }

    @Test
    public void testCacheMissOnGETUsesOriginResponse() throws Exception {

        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(request), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);

        Assertions.assertTrue(HttpTestUtils.semanticallyTransparent(originResponse, result));
    }

    private void testOrderOfMultipleHeadersIsPreservedOnResponses(final String h) throws Exception {
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(HttpTestUtils.getCanonicalHeaderValue(originResponse, h), HttpTestUtils
                .getCanonicalHeaderValue(result, h));

    }

    @Test
    public void testOrderOfMultipleAllowHeadersIsPreservedOnResponses() throws Exception {
        originResponse = new BasicClassicHttpResponse(405, "Method Not Allowed");
        originResponse.addHeader("Allow", "HEAD");
        originResponse.addHeader("Allow", "DELETE");
        testOrderOfMultipleHeadersIsPreservedOnResponses("Allow");
    }

    @Test
    public void testOrderOfMultipleCacheControlHeadersIsPreservedOnResponses() throws Exception {
        originResponse.addHeader("Cache-Control", "max-age=0");
        originResponse.addHeader("Cache-Control", "no-store, must-revalidate");
        testOrderOfMultipleHeadersIsPreservedOnResponses("Cache-Control");
    }

    @Test
    public void testOrderOfMultipleContentEncodingHeadersIsPreservedOnResponses() throws Exception {
        originResponse.addHeader("Content-Encoding", "gzip");
        originResponse.addHeader("Content-Encoding", "compress");
        testOrderOfMultipleHeadersIsPreservedOnResponses("Content-Encoding");
    }

    @Test
    public void testOrderOfMultipleContentLanguageHeadersIsPreservedOnResponses() throws Exception {
        originResponse.addHeader("Content-Language", "mi");
        originResponse.addHeader("Content-Language", "en");
        testOrderOfMultipleHeadersIsPreservedOnResponses("Content-Language");
    }

    @Test
    public void testOrderOfMultipleViaHeadersIsPreservedOnResponses() throws Exception {
        originResponse.addHeader(HttpHeaders.VIA, "1.0 fred, 1.1 nowhere.com (Apache/1.1)");
        originResponse.addHeader(HttpHeaders.VIA, "1.0 ricky, 1.1 mertz, 1.0 lucy");
        testOrderOfMultipleHeadersIsPreservedOnResponses(HttpHeaders.VIA);
    }

    @Test
    public void testOrderOfMultipleWWWAuthenticateHeadersIsPreservedOnResponses() throws Exception {
        originResponse.addHeader("WWW-Authenticate", "x-challenge-1");
        originResponse.addHeader("WWW-Authenticate", "x-challenge-2");
        testOrderOfMultipleHeadersIsPreservedOnResponses("WWW-Authenticate");
    }

    private void testUnknownResponseStatusCodeIsNotCached(final int code) throws Exception {

        originResponse = new BasicClassicHttpResponse(code, "Moo");
        originResponse.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        originResponse.setHeader("Server", "MockOrigin/1.0");
        originResponse.setHeader("Cache-Control", "max-age=3600");
        originResponse.setEntity(body);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        // in particular, there were no storage calls on the cache
        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testUnknownResponseStatusCodesAreNotCached() throws Exception {
        for (int i = 100; i <= 199; i++) {
            testUnknownResponseStatusCodeIsNotCached(i);
        }
        for (int i = 207; i <= 299; i++) {
            testUnknownResponseStatusCodeIsNotCached(i);
        }
        for (int i = 308; i <= 399; i++) {
            testUnknownResponseStatusCodeIsNotCached(i);
        }
        for (int i = 418; i <= 499; i++) {
            testUnknownResponseStatusCodeIsNotCached(i);
        }
        for (int i = 506; i <= 999; i++) {
            testUnknownResponseStatusCodeIsNotCached(i);
        }
    }

    @Test
    public void testUnknownHeadersOnRequestsAreForwarded() throws Exception {
        request.addHeader("X-Unknown-Header", "blahblah");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain).proceed(reqCapture.capture(), Mockito.any());
        final ClassicHttpRequest forwarded = reqCapture.getValue();
        MatcherAssert.assertThat(forwarded, ContainsHeaderMatcher.contains("X-Unknown-Header", "blahblah"));
    }

    @Test
    public void testUnknownHeadersOnResponsesAreForwarded() throws Exception {
        originResponse.addHeader("X-Unknown-Header", "blahblah");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);
        MatcherAssert.assertThat(result, ContainsHeaderMatcher.contains("X-Unknown-Header", "blahblah"));
    }

    @Test
    public void testResponsesToOPTIONSAreNotCacheable() throws Exception {
        request = new BasicClassicHttpRequest("OPTIONS", "/");
        originResponse.addHeader("Cache-Control", "max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testResponsesToPOSTWithoutCacheControlOrExpiresAreNotCached() throws Exception {

        final BasicClassicHttpRequest post = new BasicClassicHttpRequest("POST", "/");
        post.setHeader("Content-Length", "128");
        post.setEntity(HttpTestUtils.makeBody(128));

        originResponse.removeHeaders("Cache-Control");
        originResponse.removeHeaders("Expires");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(post);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testResponsesToPUTsAreNotCached() throws Exception {

        final BasicClassicHttpRequest put = new BasicClassicHttpRequest("PUT", "/");
        put.setEntity(HttpTestUtils.makeBody(128));
        put.addHeader("Content-Length", "128");

        originResponse.setHeader("Cache-Control", "max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(put);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testResponsesToDELETEsAreNotCached() throws Exception {

        request = new BasicClassicHttpRequest("DELETE", "/");
        originResponse.setHeader("Cache-Control", "max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testResponsesToTRACEsAreNotCached() throws Exception {

        request = new BasicClassicHttpRequest("TRACE", "/");
        originResponse.setHeader("Cache-Control", "max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void test304ResponseGeneratedFromCacheIncludesDateHeader() throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        originResponse.setHeader("Cache-Control", "max-age=3600");
        originResponse.setHeader("ETag", "\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("If-None-Match", "\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        Assertions.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Assertions.assertNotNull(result.getFirstHeader("Date"));
        Mockito.verify(mockExecChain, Mockito.times(1)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void test304ResponseGeneratedFromCacheIncludesEtagIfOriginResponseDid() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        originResponse.setHeader("Cache-Control", "max-age=3600");
        originResponse.setHeader("ETag", "\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("If-None-Match", "\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        Assertions.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Assertions.assertNotNull(result.getFirstHeader("ETag"));
        Mockito.verify(mockExecChain, Mockito.times(1)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void test304ResponseGeneratedFromCacheIncludesContentLocationIfOriginResponseDid() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        originResponse.setHeader("Cache-Control", "max-age=3600");
        originResponse.setHeader("Content-Location", "http://foo.example.com/other");
        originResponse.setHeader("ETag", "\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("If-None-Match", "\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        Assertions.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Assertions.assertNotNull(result.getFirstHeader("Content-Location"));
        Mockito.verify(mockExecChain, Mockito.times(1)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void test304ResponseGeneratedFromCacheIncludesExpiresCacheControlAndOrVaryIfResponseMightDiffer() throws Exception {

        final Instant now = Instant.now();
        final Instant inTwoHours = now.plus(2, ChronoUnit.HOURS);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("Accept-Encoding", "gzip");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag", "\"v1\"");
        resp1.setHeader("Cache-Control", "max-age=7200");
        resp1.setHeader("Expires", DateUtils.formatStandardDate(inTwoHours));
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setEntity(HttpTestUtils.makeBody(ENTITY_LENGTH));

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Accept-Encoding", "gzip");
        req2.setHeader("Cache-Control", "no-cache");

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"v2\"");
        resp2.setHeader("Cache-Control", "max-age=3600");
        resp2.setHeader("Expires", DateUtils.formatStandardDate(inTwoHours));
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setEntity(HttpTestUtils.makeBody(ENTITY_LENGTH));

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");
        req3.setHeader("Accept-Encoding", "gzip");
        req3.setHeader("If-None-Match", "\"v2\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);
        execute(req2);

        final ClassicHttpResponse result = execute(req3);

        Assertions.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Assertions.assertNotNull(result.getFirstHeader("Expires"));
        Assertions.assertNotNull(result.getFirstHeader("Cache-Control"));
        Assertions.assertNotNull(result.getFirstHeader("Vary"));
        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void test304GeneratedFromCacheOnWeakValidatorDoesNotIncludeOtherEntityHeaders() throws Exception {

        final Instant now = Instant.now();
        final Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag", "W/\"v1\"");
        resp1.setHeader("Allow", "GET,HEAD");
        resp1.setHeader("Content-Encoding", "x-coding");
        resp1.setHeader("Content-Language", "en");
        resp1.setHeader("Content-Length", "128");
        resp1.setHeader("Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
        resp1.setHeader("Content-Type", "application/octet-stream");
        resp1.setHeader("Last-Modified", DateUtils.formatStandardDate(oneHourAgo));
        resp1.setHeader("Cache-Control", "max-age=7200");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("If-None-Match", "W/\"v1\"");

        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(req1), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        Assertions.assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Assertions.assertNull(result.getFirstHeader("Allow"));
        Assertions.assertNull(result.getFirstHeader("Content-Encoding"));
        Assertions.assertNull(result.getFirstHeader("Content-Length"));
        Assertions.assertNull(result.getFirstHeader("Content-MD5"));
        Assertions.assertNull(result.getFirstHeader("Content-Type"));
        Assertions.assertNull(result.getFirstHeader("Last-Modified"));
        Mockito.verify(mockExecChain, Mockito.times(1)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testNotModifiedOfNonCachedEntityShouldRevalidateWithUnconditionalGET() throws Exception {

        final Instant now = Instant.now();

        // load cache with cacheable entry
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Cache-Control", "max-age=3600");

        // force a revalidation
        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control", "max-age=0,max-stale=0");

        // unconditional validation doesn't use If-None-Match
        final ClassicHttpRequest unconditionalValidation = new BasicClassicHttpRequest("GET", "/");
        // new response to unconditional validation provides new body
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag", "\"etag2\"");
        resp1.setHeader("Cache-Control", "max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        // this next one will happen once if the cache tries to
        // conditionally validate, zero if it goes full revalidation

        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(unconditionalValidation), Mockito.any())).thenReturn(resp2);

        execute(req1);
        execute(req2);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testCacheEntryIsUpdatedWithNewFieldValuesIn304Response() throws Exception {

        final Instant now = Instant.now();
        final Instant inFiveSeconds = now.plusSeconds(5);

        final ClassicHttpRequest initialRequest = new BasicClassicHttpRequest("GET", "/");

        final ClassicHttpResponse cachedResponse = HttpTestUtils.make200Response();
        cachedResponse.setHeader("Cache-Control", "max-age=3600");
        cachedResponse.setHeader("ETag", "\"etag\"");

        final ClassicHttpRequest secondRequest = new BasicClassicHttpRequest("GET", "/");
        secondRequest.setHeader("Cache-Control", "max-age=0,max-stale=0");

        final ClassicHttpRequest conditionalValidationRequest = new BasicClassicHttpRequest("GET", "/");
        conditionalValidationRequest.setHeader("If-None-Match", "\"etag\"");

        // to be used if the cache generates a conditional validation
        final ClassicHttpResponse conditionalResponse = HttpTestUtils.make304Response();
        conditionalResponse.setHeader("Date", DateUtils.formatStandardDate(inFiveSeconds));
        conditionalResponse.setHeader("Server", "MockUtils/1.0");
        conditionalResponse.setHeader("ETag", "\"etag\"");
        conditionalResponse.setHeader("X-Extra", "junk");

        // to be used if the cache generates an unconditional validation
        final ClassicHttpResponse unconditionalResponse = HttpTestUtils.make200Response();
        unconditionalResponse.setHeader("Date", DateUtils.formatStandardDate(inFiveSeconds));
        unconditionalResponse.setHeader("ETag", "\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(cachedResponse);
        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(conditionalValidationRequest), Mockito.any())).thenReturn(conditionalResponse);

        execute(initialRequest);
        final ClassicHttpResponse result = execute(secondRequest);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());

        Assertions.assertEquals(DateUtils.formatStandardDate(inFiveSeconds), result.getFirstHeader("Date").getValue());
        Assertions.assertEquals("junk", result.getFirstHeader("X-Extra").getValue());
    }

    @Test
    public void testMustReturnACacheEntryIfItCanRevalidateIt() throws Exception {

        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
        final Instant nineSecondsAgo = now.minusSeconds(9);
        final Instant eightSecondsAgo = now.minusSeconds(8);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo,
                Method.GET, "/thing", null,
                200, new Header[] {
                        new BasicHeader("Date", DateUtils.formatStandardDate(nineSecondsAgo)),
                        new BasicHeader("ETag", "\"etag\"")
                }, HttpTestUtils.makeNullResource());

        impl = new CachingExec(mockCache, null, config);

        request = new BasicClassicHttpRequest("GET", "/thing");

        final ClassicHttpRequest validate = new BasicClassicHttpRequest("GET", "/thing");
        validate.setHeader("If-None-Match", "\"etag\"");

        final ClassicHttpResponse notModified = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        notModified.setHeader("Date", DateUtils.formatStandardDate(now));
        notModified.setHeader("ETag", "\"etag\"");

        Mockito.when(mockCache.match(Mockito.eq(host), RequestEquivalent.eq(request))).thenReturn(
                new CacheMatch(new CacheHit("key", entry), null));
        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(validate), Mockito.any())).thenReturn(notModified);
        final HttpCacheEntry updated = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo,
                Method.GET, "/thing", null,
                200, new Header[] {
                        new BasicHeader("Date", DateUtils.formatStandardDate(now)),
                        new BasicHeader("ETag", "\"etag\"")
                }, HttpTestUtils.makeNullResource());
        Mockito.when(mockCache.update(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(new CacheHit("key", updated));

        execute(request);

        Mockito.verify(mockCache).update(
                Mockito.any(),
                Mockito.eq(host),
                RequestEquivalent.eq(request),
                ResponseEquivalent.eq(notModified),
                Mockito.any(),
                Mockito.any());
    }

    @Test
    public void testMustReturnAFreshEnoughCacheEntryIfItHasIt() throws Exception {

        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
        final Instant nineSecondsAgo = now.plusSeconds(9);
        final Instant eightSecondsAgo = now.plusSeconds(8);

        final Header[] hdrs = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(nineSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length", "128")
        };

        final byte[] bytes = new byte[128];
        new Random().nextBytes(bytes);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo, hdrs, bytes);

        impl = new CachingExec(mockCache, null, config);
        request = new BasicClassicHttpRequest("GET", "/thing");

        Mockito.when(mockCache.match(Mockito.eq(host), RequestEquivalent.eq(request))).thenReturn(
                new CacheMatch(new CacheHit("key", entry), null));

        final ClassicHttpResponse result = execute(request);

        Assertions.assertEquals(200, result.getCode());
    }

    @Test
    public void testAgeHeaderPopulatedFromCacheEntryCurrentAge() throws Exception {

        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
        final Instant nineSecondsAgo = now.minusSeconds(9);
        final Instant eightSecondsAgo = now.minusSeconds(8);

        final Header[] hdrs = new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(nineSecondsAgo)),
                new BasicHeader("Cache-Control", "max-age=3600"),
                new BasicHeader("Content-Length", "128")
        };

        final byte[] bytes = new byte[128];
        new Random().nextBytes(bytes);

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo, hdrs, bytes);

        impl = new CachingExec(mockCache, null, config);
        request = new BasicClassicHttpRequest("GET", "/");

        Mockito.when(mockCache.match(Mockito.eq(host), RequestEquivalent.eq(request))).thenReturn(
                new CacheMatch(new CacheHit("key", entry), null));

        final ClassicHttpResponse result = execute(request);

        Assertions.assertEquals(200, result.getCode());
        // We calculate the age of the cache entry as per RFC 9111:
        // We first find the "corrected_initial_age" which is the maximum of "apparentAge" and "correctedReceivedAge".
        // In this case, max(1, 2) = 2 seconds.
        // We then add the "residentTime" which is "now - responseTime",
        // which is the current time minus the time the cache entry was created. In this case, that is 8 seconds.
        // So, the total age is "corrected_initial_age" + "residentTime" = 2 + 8 = 10 seconds.
        assertThat(result, ContainsHeaderMatcher.contains("Age", "10"));
    }

    @Test
    public void testKeepsMostRecentDateHeaderForFreshResponse() throws Exception {

        final Instant now = Instant.now();
        final Instant inFiveSecond = now.plusSeconds(5);

        // put an entry in the cache
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(inFiveSecond));
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Content-Length", "128");

        // force another origin hit
        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control", "no-cache");

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(now)); // older
        resp2.setHeader("ETag", "\"etag2\"");
        resp2.setHeader("Cache-Control", "max-age=3600");
        resp2.setHeader("Content-Length", "128");

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);
        final ClassicHttpResponse result = execute(req3);
        Assertions.assertEquals("\"etag1\"", result.getFirstHeader("ETag").getValue());
    }

    @Test
    public void testValidationMustUseETagIfProvidedByOriginServer() throws Exception {

        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("ETag", "W/\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control", "max-age=0,max-stale=0");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        execute(req2);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(reqCapture.capture(), Mockito.any());

        final List<ClassicHttpRequest> allRequests = reqCapture.getAllValues();
        Assertions.assertEquals(2, allRequests.size());
        final ClassicHttpRequest validation = allRequests.get(1);
        boolean foundETag = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(validation, HttpHeaders.IF_MATCH);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("W/\"etag\"".equals(elt.getName())) {
                foundETag = true;
            }
        }
        final Iterator<HeaderElement> it2 = MessageSupport.iterate(validation, HttpHeaders.IF_NONE_MATCH);
        while (it2.hasNext()) {
            final HeaderElement elt = it2.next();
            if ("W/\"etag\"".equals(elt.getName())) {
                foundETag = true;
            }
        }
        Assertions.assertTrue(foundETag);
    }

    @Test
    public void testConditionalRequestWhereNotAllValidatorsMatchCannotBeServedFromCache() throws Exception {
        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
        final Instant twentySecondsAgo = now.plusSeconds(20);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("ETag", "W/\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("If-None-Match", "W/\"etag\"");
        req2.setHeader("If-Modified-Since", DateUtils.formatStandardDate(twentySecondsAgo));

        // must hit the origin again for the second request
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        Assertions.assertNotEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testConditionalRequestWhereAllValidatorsMatchMayBeServedFromCache() throws Exception {
        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(now));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Last-Modified", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("ETag", "W/\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("If-None-Match", "W/\"etag\"");
        req2.setHeader("If-Modified-Since", DateUtils.formatStandardDate(tenSecondsAgo));

        // may hit the origin again for the second request
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        execute(req2);

        Mockito.verify(mockExecChain, Mockito.atLeastOnce()).proceed(Mockito.any(), Mockito.any());
        Mockito.verify(mockExecChain, Mockito.atMost(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testCacheWithoutSupportForRangeAndContentRangeHeadersDoesNotCacheA206Response() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Range", "bytes=0-50");

        final ClassicHttpResponse resp = new BasicClassicHttpResponse(206, "Partial Content");
        resp.setHeader("Content-Range", "bytes 0-50/128");
        resp.setHeader("ETag", "\"etag\"");
        resp.setHeader("Cache-Control", "max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(),Mockito.any())).thenReturn(resp);

        execute(req);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void test302ResponseWithoutExplicitCacheabilityIsNotReturnedFromCache() throws Exception {
        originResponse = new BasicClassicHttpResponse(302, "Temporary Redirect");
        originResponse.setHeader("Location", "http://foo.example.com/other");
        originResponse.removeHeaders("Expires");
        originResponse.removeHeaders("Cache-Control");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);
        execute(request);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    private void testDoesNotModifyHeaderFromOrigin(final String header, final String value) throws Exception {
        originResponse = HttpTestUtils.make200Response();
        originResponse.setHeader(header, value);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);

        Assertions.assertEquals(value, result.getFirstHeader(header).getValue());
    }

    @Test
    public void testDoesNotModifyContentLocationHeaderFromOrigin() throws Exception {

        final String url = "http://foo.example.com/other";
        testDoesNotModifyHeaderFromOrigin("Content-Location", url);
    }

    @Test
    public void testDoesNotModifyContentMD5HeaderFromOrigin() throws Exception {
        testDoesNotModifyHeaderFromOrigin("Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
    }

    @Test
    public void testDoesNotModifyEtagHeaderFromOrigin() throws Exception {
        testDoesNotModifyHeaderFromOrigin("Etag", "\"the-etag\"");
    }

    @Test
    public void testDoesNotModifyLastModifiedHeaderFromOrigin() throws Exception {
        final String lm = DateUtils.formatStandardDate(Instant.now());
        testDoesNotModifyHeaderFromOrigin("Last-Modified", lm);
    }

    private void testDoesNotAddHeaderToOriginResponse(final String header) throws Exception {
        originResponse.removeHeaders(header);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);

        Assertions.assertNull(result.getFirstHeader(header));
    }

    @Test
    public void testDoesNotAddContentLocationToOriginResponse() throws Exception {
        testDoesNotAddHeaderToOriginResponse("Content-Location");
    }

    @Test
    public void testDoesNotAddContentMD5ToOriginResponse() throws Exception {
        testDoesNotAddHeaderToOriginResponse("Content-MD5");
    }

    @Test
    public void testDoesNotAddEtagToOriginResponse() throws Exception {
        testDoesNotAddHeaderToOriginResponse("ETag");
    }

    @Test
    public void testDoesNotAddLastModifiedToOriginResponse() throws Exception {
        testDoesNotAddHeaderToOriginResponse("Last-Modified");
    }

    private void testDoesNotModifyHeaderFromOriginOnCacheHit(final String header, final String value) throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");

        originResponse = HttpTestUtils.make200Response();
        originResponse.setHeader("Cache-Control", "max-age=3600");
        originResponse.setHeader(header, value);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        Assertions.assertEquals(value, result.getFirstHeader(header).getValue());
    }

    @Test
    public void testDoesNotModifyContentLocationFromOriginOnCacheHit() throws Exception {
        final String url = "http://foo.example.com/other";
        testDoesNotModifyHeaderFromOriginOnCacheHit("Content-Location", url);
    }

    @Test
    public void testDoesNotModifyContentMD5FromOriginOnCacheHit() throws Exception {
        testDoesNotModifyHeaderFromOriginOnCacheHit("Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
    }

    @Test
    public void testDoesNotModifyEtagFromOriginOnCacheHit() throws Exception {
        testDoesNotModifyHeaderFromOriginOnCacheHit("Etag", "\"the-etag\"");
    }

    @Test
    public void testDoesNotModifyLastModifiedFromOriginOnCacheHit() throws Exception {
        final Instant tenSecondsAgo = Instant.now().minusSeconds(10);
        testDoesNotModifyHeaderFromOriginOnCacheHit("Last-Modified", DateUtils.formatStandardDate(tenSecondsAgo));
    }

    private void testDoesNotAddHeaderOnCacheHit(final String header) throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");

        originResponse.addHeader("Cache-Control", "max-age=3600");
        originResponse.removeHeaders(header);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        Assertions.assertNull(result.getFirstHeader(header));
    }

    @Test
    public void testDoesNotAddContentLocationHeaderOnCacheHit() throws Exception {
        testDoesNotAddHeaderOnCacheHit("Content-Location");
    }

    @Test
    public void testDoesNotAddContentMD5HeaderOnCacheHit() throws Exception {
        testDoesNotAddHeaderOnCacheHit("Content-MD5");
    }

    @Test
    public void testDoesNotAddETagHeaderOnCacheHit() throws Exception {
        testDoesNotAddHeaderOnCacheHit("ETag");
    }

    @Test
    public void testDoesNotAddLastModifiedHeaderOnCacheHit() throws Exception {
        testDoesNotAddHeaderOnCacheHit("Last-Modified");
    }

    private void testDoesNotModifyHeaderOnRequest(final String header, final String value) throws Exception {
        final BasicClassicHttpRequest req = new BasicClassicHttpRequest("POST","/");
        req.setEntity(HttpTestUtils.makeBody(128));
        req.setHeader("Content-Length","128");
        req.setHeader(header,value);

        execute(req);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain).proceed(reqCapture.capture(), Mockito.any());

        final ClassicHttpRequest captured = reqCapture.getValue();
        Assertions.assertEquals(value, captured.getFirstHeader(header).getValue());
    }

    @Test
    public void testDoesNotModifyContentLocationHeaderOnRequest() throws Exception {
        final String url = "http://foo.example.com/other";
        testDoesNotModifyHeaderOnRequest("Content-Location",url);
    }

    @Test
    public void testDoesNotModifyContentMD5HeaderOnRequest() throws Exception {
        testDoesNotModifyHeaderOnRequest("Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
    }

    @Test
    public void testDoesNotModifyETagHeaderOnRequest() throws Exception {
        testDoesNotModifyHeaderOnRequest("ETag","\"etag\"");
    }

    @Test
    public void testDoesNotModifyLastModifiedHeaderOnRequest() throws Exception {
        final Instant tenSecondsAgo = Instant.now().minusSeconds(10);
        testDoesNotModifyHeaderOnRequest("Last-Modified", DateUtils.formatStandardDate(tenSecondsAgo));
    }

    private void testDoesNotAddHeaderToRequestIfNotPresent(final String header) throws Exception {
        final BasicClassicHttpRequest req = new BasicClassicHttpRequest("POST","/");
        req.setEntity(HttpTestUtils.makeBody(128));
        req.setHeader("Content-Length","128");
        req.removeHeaders(header);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain).proceed(reqCapture.capture(), Mockito.any());

        final ClassicHttpRequest captured = reqCapture.getValue();
        Assertions.assertNull(captured.getFirstHeader(header));
    }

    @Test
    public void testDoesNotAddContentLocationToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Content-Location");
    }

    @Test
    public void testDoesNotAddContentMD5ToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Content-MD5");
    }

    @Test
    public void testDoesNotAddETagToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("ETag");
    }

    @Test
    public void testDoesNotAddLastModifiedToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Last-Modified");
    }

    @Test
    public void testDoesNotModifyExpiresHeaderFromOrigin() throws Exception {
        final Instant tenSecondsAgo = Instant.now().minusSeconds(10);
        testDoesNotModifyHeaderFromOrigin("Expires", DateUtils.formatStandardDate(tenSecondsAgo));
    }

    @Test
    public void testDoesNotModifyExpiresHeaderFromOriginOnCacheHit() throws Exception {
        final Instant inTenSeconds = Instant.now().plusSeconds(10);
        testDoesNotModifyHeaderFromOriginOnCacheHit("Expires", DateUtils.formatStandardDate(inTenSeconds));
    }

    @Test
    public void testExpiresHeaderMatchesDateIfAddedToOriginResponse() throws Exception {
        originResponse.removeHeaders("Expires");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);

        final Header expHdr = result.getFirstHeader("Expires");
        if (expHdr != null) {
            Assertions.assertEquals(result.getFirstHeader("Date").getValue(),
                                expHdr.getValue());
        }
    }

    private void testDoesNotModifyHeaderFromOriginResponseWithNoTransform(final String header, final String value) throws Exception {
        originResponse.addHeader("Cache-Control","no-transform");
        originResponse.setHeader(header, value);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);

        Assertions.assertEquals(value, result.getFirstHeader(header).getValue());
    }

    @Test
    public void testDoesNotModifyContentEncodingHeaderFromOriginResponseWithNoTransform() throws Exception {
        testDoesNotModifyHeaderFromOriginResponseWithNoTransform("Content-Encoding","gzip");
    }

    @Test
    public void testDoesNotModifyContentRangeHeaderFromOriginResponseWithNoTransform() throws Exception {
        request.setHeader("If-Range","\"etag\"");
        request.setHeader("Range","bytes=0-49");

        originResponse = new BasicClassicHttpResponse(206, "Partial Content");
        originResponse.setEntity(HttpTestUtils.makeBody(50));
        testDoesNotModifyHeaderFromOriginResponseWithNoTransform("Content-Range","bytes 0-49/128");
    }

    @Test
    public void testDoesNotModifyContentTypeHeaderFromOriginResponseWithNoTransform() throws Exception {
        testDoesNotModifyHeaderFromOriginResponseWithNoTransform("Content-Type","text/html;charset=utf-8");
    }

    private void testDoesNotModifyHeaderOnCachedResponseWithNoTransform(final String header, final String value) throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");

        originResponse.addHeader("Cache-Control","max-age=3600, no-transform");
        originResponse.setHeader(header, value);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        Assertions.assertEquals(value, result.getFirstHeader(header).getValue());
    }

    @Test
    public void testDoesNotModifyContentEncodingHeaderOnCachedResponseWithNoTransform() throws Exception {
        testDoesNotModifyHeaderOnCachedResponseWithNoTransform("Content-Encoding","gzip");
    }

    @Test
    public void testDoesNotModifyContentTypeHeaderOnCachedResponseWithNoTransform() throws Exception {
        testDoesNotModifyHeaderOnCachedResponseWithNoTransform("Content-Type","text/html;charset=utf-8");
    }

    @Test
    public void testDoesNotAddContentEncodingHeaderToOriginResponseWithNoTransformIfNotPresent() throws Exception {
        originResponse.addHeader("Cache-Control","no-transform");
        testDoesNotAddHeaderToOriginResponse("Content-Encoding");
    }

    @Test
    public void testDoesNotAddContentRangeHeaderToOriginResponseWithNoTransformIfNotPresent() throws Exception {
        originResponse.addHeader("Cache-Control","no-transform");
        testDoesNotAddHeaderToOriginResponse("Content-Range");
    }

    @Test
    public void testDoesNotAddContentTypeHeaderToOriginResponseWithNoTransformIfNotPresent() throws Exception {
        originResponse.addHeader("Cache-Control","no-transform");
        testDoesNotAddHeaderToOriginResponse("Content-Type");
    }

    /* no add on cache hit with no-transform */
    @Test
    public void testDoesNotAddContentEncodingHeaderToCachedResponseWithNoTransformIfNotPresent() throws Exception {
        originResponse.addHeader("Cache-Control","no-transform");
        testDoesNotAddHeaderOnCacheHit("Content-Encoding");
    }

    @Test
    public void testDoesNotAddContentRangeHeaderToCachedResponseWithNoTransformIfNotPresent() throws Exception {
        originResponse.addHeader("Cache-Control","no-transform");
        testDoesNotAddHeaderOnCacheHit("Content-Range");
    }

    @Test
    public void testDoesNotAddContentTypeHeaderToCachedResponseWithNoTransformIfNotPresent() throws Exception {
        originResponse.addHeader("Cache-Control","no-transform");
        testDoesNotAddHeaderOnCacheHit("Content-Type");
    }

    /* no modify on request */
    @Test
    public void testDoesNotAddContentEncodingToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Content-Encoding");
    }

    @Test
    public void testDoesNotAddContentRangeToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Content-Range");
    }

    @Test
    public void testDoesNotAddContentTypeToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Content-Type");
    }

    @Test
    public void testDoesNotAddContentEncodingHeaderToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Content-Encoding");
    }

    @Test
    public void testDoesNotAddContentRangeHeaderToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Content-Range");
    }

    @Test
    public void testDoesNotAddContentTypeHeaderToRequestIfNotPresent() throws Exception {
        testDoesNotAddHeaderToRequestIfNotPresent("Content-Type");
    }

    @Test
    public void testCachedEntityBodyIsUsedForResponseAfter304Validation() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("ETag","\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control","max-age=0, max-stale=0");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        try (final InputStream i1 = resp1.getEntity().getContent();
             final InputStream i2 = result.getEntity().getContent()) {
            int b1, b2;
            while((b1 = i1.read()) != -1) {
                b2 = i2.read();
                Assertions.assertEquals(b1, b2);
            }
            b2 = i2.read();
            Assertions.assertEquals(-1, b2);
        }
    }

    private void decorateWithEndToEndHeaders(final ClassicHttpResponse r) {
        r.setHeader("Allow","GET");
        r.setHeader("Content-Encoding","gzip");
        r.setHeader("Content-Language","en");
        r.setHeader("Content-Length", "128");
        r.setHeader("Content-Location","http://foo.example.com/other");
        r.setHeader("Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
        r.setHeader("Content-Type", "text/html;charset=utf-8");
        r.setHeader("Expires", DateUtils.formatStandardDate(Instant.now().plusSeconds(10)));
        r.setHeader("Last-Modified", DateUtils.formatStandardDate(Instant.now().minusSeconds(10)));
        r.setHeader("Location", "http://foo.example.com/other2");
        r.setHeader("Retry-After","180");
    }

    @Test
    public void testResponseIncludesCacheEntryEndToEndHeadersForResponseAfter304Validation() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("ETag","\"etag\"");
        decorateWithEndToEndHeaders(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control", "max-age=0, max-stale=0");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        resp2.setHeader("Server", "MockServer/1.0");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(req2), Mockito.any())).thenReturn(resp2);
        final ClassicHttpResponse result = execute(req2);

        final String[] endToEndHeaders = {
            "Cache-Control", "ETag", "Allow", "Content-Encoding",
            "Content-Language", "Content-Length", "Content-Location",
            "Content-MD5", "Content-Type", "Expires", "Last-Modified",
            "Location", "Retry-After"
        };
        for(final String h : endToEndHeaders) {
            Assertions.assertEquals(HttpTestUtils.getCanonicalHeaderValue(resp1, h),
                                HttpTestUtils.getCanonicalHeaderValue(result, h));
        }
    }

    @Test
    public void testUpdatedEndToEndHeadersFrom304ArePassedOnResponseAndUpdatedInCacheEntry() throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("ETag","\"etag\"");
        decorateWithEndToEndHeaders(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control", "max-age=0, max-stale=0");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("Cache-Control", "max-age=1800");
        resp2.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        resp2.setHeader("Server", "MockServer/1.0");
        resp2.setHeader("Allow", "GET,HEAD");
        resp2.setHeader("Content-Language", "en,en-us");
        resp2.setHeader("Content-Location", "http://foo.example.com/new");
        resp2.setHeader("Content-Type","text/html");
        resp2.setHeader("Expires", DateUtils.formatStandardDate(Instant.now().plusSeconds(5)));
        resp2.setHeader("Location", "http://foo.example.com/new2");
        resp2.setHeader("Retry-After","120");

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);
        final ClassicHttpResponse result1 = execute(req2);
        final ClassicHttpResponse result2 = execute(req3);

        final String[] endToEndHeaders = {
            "Date", "Cache-Control", "Allow", "Content-Language",
            "Content-Location", "Content-Type", "Expires", "Location",
            "Retry-After"
        };
        for(final String h : endToEndHeaders) {
            Assertions.assertEquals(HttpTestUtils.getCanonicalHeaderValue(resp2, h),
                                HttpTestUtils.getCanonicalHeaderValue(result1, h));
            Assertions.assertEquals(HttpTestUtils.getCanonicalHeaderValue(resp2, h),
                                HttpTestUtils.getCanonicalHeaderValue(result2, h));
        }
    }

    @Test
    public void testMultiHeadersAreSuccessfullyReplacedOn304Validation() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.addHeader("Cache-Control","max-age=3600");
        resp1.addHeader("Cache-Control","public");
        resp1.setHeader("ETag","\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control", "max-age=0, max-stale=0");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("Cache-Control", "max-age=1800");

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result1 = execute(req2);
        final ClassicHttpResponse result2 = execute(req3);

        final String h = "Cache-Control";
        Assertions.assertEquals(HttpTestUtils.getCanonicalHeaderValue(resp2, h),
                            HttpTestUtils.getCanonicalHeaderValue(result1, h));
        Assertions.assertEquals(HttpTestUtils.getCanonicalHeaderValue(resp2, h),
                            HttpTestUtils.getCanonicalHeaderValue(result2, h));
    }

    @Test
    public void testCannotUseVariantCacheEntryIfNotAllSelectingRequestHeadersMatch() throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("Accept-Encoding","gzip");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag1\"");
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("Vary","Accept-Encoding");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.removeHeaders("Accept-Encoding");

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag","\"etag1\"");
        resp2.setHeader("Cache-Control","max-age=3600");

        // not allowed to have a cache hit; must forward request
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testCannotServeFromCacheForVaryStar() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag1\"");
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("Vary","*");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag","\"etag1\"");
        resp2.setHeader("Cache-Control","max-age=3600");

        // not allowed to have a cache hit; must forward request
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testNonMatchingVariantCannotBeServedFromCacheUnlessConditionallyValidated() throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("User-Agent","MyBrowser/1.0");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag1\"");
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("Vary","User-Agent");
        resp1.setHeader("Content-Type","application/octet-stream");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("User-Agent","MyBrowser/1.5");

        final ClassicHttpResponse resp200 = HttpTestUtils.make200Response();
        resp200.setHeader("ETag","\"etag1\"");
        resp200.setHeader("Vary","User-Agent");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(req2), Mockito.any())).thenReturn(resp200);

        final ClassicHttpResponse result = execute(req2);

        Assertions.assertEquals(HttpStatus.SC_OK, result.getCode());

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());

        Assertions.assertTrue(HttpTestUtils.semanticallyTransparent(resp200, result));
    }

    protected void testUnsafeOperationInvalidatesCacheForThatUri(
            final ClassicHttpRequest unsafeReq) throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","public, max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(unsafeReq);

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();
        resp3.setHeader("Cache-Control","public, max-age=3600");

        // this origin request MUST happen due to invalidation
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp3);

        execute(req3);
    }

    protected ClassicHttpRequest makeRequestWithBody(final String method, final String requestUri) {
        final ClassicHttpRequest req = new BasicClassicHttpRequest(method, requestUri);
        final int nbytes = 128;
        req.setEntity(HttpTestUtils.makeBody(nbytes));
        req.setHeader("Content-Length", Long.toString(nbytes));
        return req;
    }

    @Test
    public void testPutToUriInvalidatesCacheForThatUri() throws Exception {
        final ClassicHttpRequest req = makeRequestWithBody("PUT","/");
        testUnsafeOperationInvalidatesCacheForThatUri(req);
    }

    @Test
    public void testDeleteToUriInvalidatesCacheForThatUri() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("DELETE","/");
        testUnsafeOperationInvalidatesCacheForThatUri(req);
    }

    @Test
    public void testPostToUriInvalidatesCacheForThatUri() throws Exception {
        final ClassicHttpRequest req = makeRequestWithBody("POST","/");
        testUnsafeOperationInvalidatesCacheForThatUri(req);
    }

    protected void testUnsafeMethodInvalidatesCacheForHeaderUri(
            final ClassicHttpRequest unsafeReq) throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/content");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","public, max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(unsafeReq);

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/content");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();
        resp3.setHeader("Cache-Control","public, max-age=3600");

        // this origin request MUST happen due to invalidation
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp3);

        execute(req3);
    }

    protected void testUnsafeMethodInvalidatesCacheForUriInContentLocationHeader(
            final ClassicHttpRequest unsafeReq) throws Exception {
        unsafeReq.setHeader("Content-Location","http://foo.example.com/content");
        testUnsafeMethodInvalidatesCacheForHeaderUri(unsafeReq);
    }

    protected void testUnsafeMethodInvalidatesCacheForRelativeUriInContentLocationHeader(
            final ClassicHttpRequest unsafeReq) throws Exception {
        unsafeReq.setHeader("Content-Location","/content");
        testUnsafeMethodInvalidatesCacheForHeaderUri(unsafeReq);
    }

    protected void testUnsafeMethodInvalidatesCacheForUriInLocationHeader(
            final ClassicHttpRequest unsafeReq) throws Exception {
        unsafeReq.setHeader("Location","http://foo.example.com/content");
        testUnsafeMethodInvalidatesCacheForHeaderUri(unsafeReq);
    }

    @Test
    public void testPutInvalidatesCacheForThatUriInContentLocationHeader() throws Exception {
        final ClassicHttpRequest req2 = makeRequestWithBody("PUT","/");
        testUnsafeMethodInvalidatesCacheForUriInContentLocationHeader(req2);
    }

    @Test
    public void testPutInvalidatesCacheForThatUriInLocationHeader() throws Exception {
        final ClassicHttpRequest req = makeRequestWithBody("PUT","/");
        testUnsafeMethodInvalidatesCacheForUriInLocationHeader(req);
    }

    @Test
    public void testPutInvalidatesCacheForThatUriInRelativeContentLocationHeader() throws Exception {
        final ClassicHttpRequest req = makeRequestWithBody("PUT","/");
        testUnsafeMethodInvalidatesCacheForRelativeUriInContentLocationHeader(req);
    }

    @Test
    public void testDeleteInvalidatesCacheForThatUriInContentLocationHeader() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("DELETE", "/");
        testUnsafeMethodInvalidatesCacheForUriInContentLocationHeader(req);
    }

    @Test
    public void testDeleteInvalidatesCacheForThatUriInRelativeContentLocationHeader() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("DELETE", "/");
        testUnsafeMethodInvalidatesCacheForRelativeUriInContentLocationHeader(req);
    }

    @Test
    public void testDeleteInvalidatesCacheForThatUriInLocationHeader() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("DELETE", "/");
        testUnsafeMethodInvalidatesCacheForUriInLocationHeader(req);
    }

    @Test
    public void testPostInvalidatesCacheForThatUriInContentLocationHeader() throws Exception {
        final ClassicHttpRequest req = makeRequestWithBody("POST","/");
        testUnsafeMethodInvalidatesCacheForUriInContentLocationHeader(req);
    }

    @Test
    public void testPostInvalidatesCacheForThatUriInLocationHeader() throws Exception {
        final ClassicHttpRequest req = makeRequestWithBody("POST","/");
        testUnsafeMethodInvalidatesCacheForUriInLocationHeader(req);
    }

    @Test
    public void testPostInvalidatesCacheForRelativeUriInContentLocationHeader() throws Exception {
        final ClassicHttpRequest req = makeRequestWithBody("POST","/");
        testUnsafeMethodInvalidatesCacheForRelativeUriInContentLocationHeader(req);
    }

    private void testRequestIsWrittenThroughToOrigin(final ClassicHttpRequest req) throws Exception {
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        final ClassicHttpRequest wrapper = req;
        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(wrapper), Mockito.any())).thenReturn(resp);

        execute(wrapper);
    }

    @Test
    public void testOPTIONSRequestsAreWrittenThroughToOrigin() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("OPTIONS","*");
        testRequestIsWrittenThroughToOrigin(req);
    }

    @Test
    public void testPOSTRequestsAreWrittenThroughToOrigin() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("POST","/");
        req.setEntity(HttpTestUtils.makeBody(128));
        req.setHeader("Content-Length","128");
        testRequestIsWrittenThroughToOrigin(req);
    }

    @Test
    public void testPUTRequestsAreWrittenThroughToOrigin() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("PUT","/");
        req.setEntity(HttpTestUtils.makeBody(128));
        req.setHeader("Content-Length","128");
        testRequestIsWrittenThroughToOrigin(req);
    }

    @Test
    public void testDELETERequestsAreWrittenThroughToOrigin() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("DELETE", "/");
        testRequestIsWrittenThroughToOrigin(req);
    }

    @Test
    public void testTRACERequestsAreWrittenThroughToOrigin() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("TRACE","/");
        testRequestIsWrittenThroughToOrigin(req);
    }

    @Test
    public void testCONNECTRequestsAreWrittenThroughToOrigin() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("CONNECT","/");
        testRequestIsWrittenThroughToOrigin(req);
    }

    @Test
    public void testUnknownMethodRequestsAreWrittenThroughToOrigin() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("UNKNOWN","/");
        testRequestIsWrittenThroughToOrigin(req);
    }

    @Test
    public void testTransmitsAgeHeaderIfIncomingAgeHeaderTooBig() throws Exception {
        final String reallyOldAge = "1" + Long.MAX_VALUE;
        originResponse.setHeader("Age",reallyOldAge);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);

        Assertions.assertEquals(reallyOldAge,
                            result.getFirstHeader("Age").getValue());
    }

    @Test
    public void testDoesNotModifyAllowHeaderWithUnknownMethods() throws Exception {
        final String allowHeaderValue = "GET, HEAD, FOOBAR";
        originResponse.setHeader("Allow",allowHeaderValue);
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);
        final ClassicHttpResponse result = execute(request);
        Assertions.assertEquals(HttpTestUtils.getCanonicalHeaderValue(originResponse,"Allow"),
                            HttpTestUtils.getCanonicalHeaderValue(result, "Allow"));
    }

    protected void testSharedCacheRevalidatesAuthorizedResponse(
            final ClassicHttpResponse authorizedResponse, final int minTimes, final int maxTimes) throws Exception {
        if (config.isSharedCache()) {
            final String authorization = StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=";
            final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
            req1.setHeader("Authorization",authorization);

            final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
            final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
            resp2.setHeader("Cache-Control","max-age=3600");

            Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(authorizedResponse);

            execute(req1);

            Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

            execute(req2);

            Mockito.verify(mockExecChain, Mockito.atLeast(1 + minTimes)).proceed(Mockito.any(), Mockito.any());
            Mockito.verify(mockExecChain, Mockito.atMost(1 + maxTimes)).proceed(Mockito.any(), Mockito.any());
        }
    }

    @Test
    public void testSharedCacheMustNotNormallyCacheAuthorizedResponses() throws Exception {
        final ClassicHttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Cache-Control","max-age=3600");
        resp.setHeader("ETag","\"etag\"");
        testSharedCacheRevalidatesAuthorizedResponse(resp, 1, 1);
    }

    @Test
    public void testSharedCacheMayCacheAuthorizedResponsesWithSMaxAgeHeader() throws Exception {
        final ClassicHttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Cache-Control","s-maxage=3600");
        resp.setHeader("ETag","\"etag\"");
        testSharedCacheRevalidatesAuthorizedResponse(resp, 0, 1);
    }

    @Test
    public void testSharedCacheMustRevalidateAuthorizedResponsesWhenSMaxAgeIsZero() throws Exception {
        final ClassicHttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Cache-Control","s-maxage=0");
        resp.setHeader("ETag","\"etag\"");
        testSharedCacheRevalidatesAuthorizedResponse(resp, 1, 1);
    }

    @Test
    public void testSharedCacheMayCacheAuthorizedResponsesWithMustRevalidate() throws Exception {
        final ClassicHttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Cache-Control","must-revalidate");
        resp.setHeader("ETag","\"etag\"");
        testSharedCacheRevalidatesAuthorizedResponse(resp, 0, 1);
    }

    @Test
    public void testSharedCacheMayCacheAuthorizedResponsesWithCacheControlPublic() throws Exception {
        final ClassicHttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Cache-Control","public");
        testSharedCacheRevalidatesAuthorizedResponse(resp, 0, 1);
    }

    protected void testSharedCacheMustUseNewRequestHeadersWhenRevalidatingAuthorizedResponse(
            final ClassicHttpResponse authorizedResponse) throws Exception {
        if (config.isSharedCache()) {
            final String authorization1 = StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=";
            final String authorization2 = StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Qy";

            final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
            req1.setHeader("Authorization",authorization1);

            final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
            req2.setHeader("Authorization",authorization2);

            final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

            Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(authorizedResponse);

            execute(req1);

            Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

            execute(req2);

            final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
            Mockito.verify(mockExecChain, Mockito.times(2)).proceed(reqCapture.capture(), Mockito.any());

            final List<ClassicHttpRequest> allRequests = reqCapture.getAllValues();
            Assertions.assertEquals(2, allRequests.size());

            final ClassicHttpRequest captured = allRequests.get(1);
            Assertions.assertEquals(HttpTestUtils.getCanonicalHeaderValue(req2, "Authorization"),
                    HttpTestUtils.getCanonicalHeaderValue(captured, "Authorization"));
        }
    }

    @Test
    public void testSharedCacheMustUseNewRequestHeadersWhenRevalidatingAuthorizedResponsesWithSMaxAge() throws Exception {
        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date",DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Cache-Control","s-maxage=5");

        testSharedCacheMustUseNewRequestHeadersWhenRevalidatingAuthorizedResponse(resp1);
    }

    @Test
    public void testSharedCacheMustUseNewRequestHeadersWhenRevalidatingAuthorizedResponsesWithMustRevalidate() throws Exception {
        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date",DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Cache-Control","maxage=5, must-revalidate");

        testSharedCacheMustUseNewRequestHeadersWhenRevalidatingAuthorizedResponse(resp1);
    }

    protected void testCacheIsNotUsedWhenRespondingToRequest(final ClassicHttpRequest req) throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Etag","\"etag\"");
        resp1.setHeader("Cache-Control","max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Etag","\"etag2\"");
        resp2.setHeader("Cache-Control","max-age=1200");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req);

        Assertions.assertTrue(HttpTestUtils.semanticallyTransparent(resp2, result));

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(reqCapture.capture(), Mockito.any());

        final ClassicHttpRequest captured = reqCapture.getValue();
        Assertions.assertTrue(HttpTestUtils.equivalent(req, captured));
    }

    @Test
    public void testCacheIsNotUsedWhenRespondingToRequestWithCacheControlNoCache() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Cache-Control","no-cache");
        testCacheIsNotUsedWhenRespondingToRequest(req);
    }

    protected void testStaleCacheResponseMustBeRevalidatedWithOrigin(
            final ClassicHttpResponse staleResponse) throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control","max-stale=3600");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag","\"etag2\"");
        resp2.setHeader("Cache-Control","max-age=5, must-revalidate");

        // this request MUST happen
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(staleResponse);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(reqCapture.capture(), Mockito.any());

        final ClassicHttpRequest reval = reqCapture.getValue();
        boolean foundMaxAge0 = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(reval, HttpHeaders.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("max-age".equalsIgnoreCase(elt.getName())
                    && "0".equals(elt.getValue())) {
                foundMaxAge0 = true;
            }
        }
        Assertions.assertTrue(foundMaxAge0);
    }

    @Test
    public void testStaleEntryWithMustRevalidateIsNotUsedWithoutRevalidatingWithOrigin() throws Exception {
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
        response.setHeader("Date",DateUtils.formatStandardDate(tenSecondsAgo));
        response.setHeader("ETag","\"etag1\"");
        response.setHeader("Cache-Control","max-age=5, must-revalidate");

        testStaleCacheResponseMustBeRevalidatedWithOrigin(response);
    }

    protected void testGenerates504IfCannotRevalidateStaleResponse(
            final ClassicHttpResponse staleResponse) throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(staleResponse);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenThrow(new SocketTimeoutException());

        final ClassicHttpResponse result = execute(req2);

        Assertions.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT,
                            result.getCode());
    }

    @Test
    public void testGenerates504IfCannotRevalidateAMustRevalidateEntry() throws Exception {
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","max-age=5,must-revalidate");

        testGenerates504IfCannotRevalidateStaleResponse(resp1);
    }

    @Test
    public void testStaleEntryWithProxyRevalidateOnSharedCacheIsNotUsedWithoutRevalidatingWithOrigin() throws Exception {
        if (config.isSharedCache()) {
            final ClassicHttpResponse response = HttpTestUtils.make200Response();
            final Instant now = Instant.now();
            final Instant tenSecondsAgo = now.minusSeconds(10);
            response.setHeader("Date",DateUtils.formatStandardDate(tenSecondsAgo));
            response.setHeader("ETag","\"etag1\"");
            response.setHeader("Cache-Control","max-age=5, proxy-revalidate");

            testStaleCacheResponseMustBeRevalidatedWithOrigin(response);
        }
    }

    @Test
    public void testGenerates504IfSharedCacheCannotRevalidateAProxyRevalidateEntry() throws Exception {
        if (config.isSharedCache()) {
            final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
            final Instant now = Instant.now();
            final Instant tenSecondsAgo = now.minusSeconds(10);
            resp1.setHeader("ETag","\"etag\"");
            resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
            resp1.setHeader("Cache-Control","max-age=5,proxy-revalidate");

            testGenerates504IfCannotRevalidateStaleResponse(resp1);
        }
    }

    @Test
    public void testCacheControlPrivateIsNotCacheableBySharedCache() throws Exception {
        if (config.isSharedCache()) {
            final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
            final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
            resp1.setHeader("Cache-Control", "private,max-age=3600");

            Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

            final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
            final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
            // this backend request MUST happen
            Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

            execute(req1);
            execute(req2);
        }
    }

    @Test
    public void testCacheControlPrivateOnFieldIsNotReturnedBySharedCache() throws Exception {
        if (config.isSharedCache()) {
            final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
            final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
            resp1.setHeader("X-Personal", "stuff");
            resp1.setHeader("Cache-Control", "private=\"X-Personal\",s-maxage=3600");

            Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

            final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
            final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

            // this backend request MAY happen
            Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

            execute(req1);
            final ClassicHttpResponse result = execute(req2);
            Assertions.assertNull(result.getFirstHeader("X-Personal"));

            Mockito.verify(mockExecChain, Mockito.atLeastOnce()).proceed(Mockito.any(), Mockito.any());
            Mockito.verify(mockExecChain, Mockito.atMost(2)).proceed(Mockito.any(), Mockito.any());
        }
    }

    @Test
    public void testNoCacheCannotSatisfyASubsequentRequestWithoutRevalidation() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Cache-Control","no-cache");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        // this MUST happen
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testNoCacheCannotSatisfyASubsequentRequestWithoutRevalidationEvenWithContraryIndications() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Cache-Control","no-cache,s-maxage=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control","max-stale=7200");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        // this MUST happen
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);
    }

    @Test
    public void testNoCacheOnFieldIsNotReturnedWithoutRevalidation() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("X-Stuff","things");
        resp1.setHeader("Cache-Control","no-cache=\"X-Stuff\", max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("X-Stuff","things");
        resp2.setHeader("Cache-Control","no-cache=\"X-Stuff\",max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain, Mockito.atMost(2)).proceed(reqCapture.capture(), Mockito.any());

        final List<ClassicHttpRequest> allRequests = reqCapture.getAllValues();
        if (allRequests.isEmpty()) {
            Assertions.assertNull(result.getFirstHeader("X-Stuff"));
        }
    }

    @Test
    public void testNoStoreOnRequestIsNotStoredInCache() throws Exception {
        request.setHeader("Cache-Control","no-store");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testNoStoreOnRequestIsNotStoredInCacheEvenIfResponseMarkedCacheable() throws Exception {
        request.setHeader("Cache-Control","no-store");
        originResponse.setHeader("Cache-Control","max-age=3600");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testNoStoreOnResponseIsNotStoredInCache() throws Exception {
        originResponse.setHeader("Cache-Control","no-store");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testNoStoreOnResponseIsNotStoredInCacheEvenWithContraryIndicators() throws Exception {
        originResponse.setHeader("Cache-Control","no-store,max-age=3600");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testOrderOfMultipleContentEncodingHeaderValuesIsPreserved() throws Exception {
        originResponse.addHeader("Content-Encoding","gzip");
        originResponse.addHeader("Content-Encoding","deflate");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);
        int total_encodings = 0;
        final Iterator<HeaderElement> it = MessageSupport.iterate(result, HttpHeaders.CONTENT_ENCODING);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            switch(total_encodings) {
                case 0:
                    Assertions.assertEquals("gzip", elt.getName());
                    break;
                case 1:
                    Assertions.assertEquals("deflate", elt.getName());
                    break;
                default:
                    Assertions.fail("too many encodings");
            }
            total_encodings++;
        }
        Assertions.assertEquals(2, total_encodings);
    }

    @Test
    public void testOrderOfMultipleParametersInContentEncodingHeaderIsPreserved() throws Exception {
        originResponse.addHeader("Content-Encoding","gzip,deflate");
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);
        int total_encodings = 0;
        final Iterator<HeaderElement> it = MessageSupport.iterate(result, HttpHeaders.CONTENT_ENCODING);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            switch(total_encodings) {
                case 0:
                    Assertions.assertEquals("gzip", elt.getName());
                    break;
                case 1:
                    Assertions.assertEquals("deflate", elt.getName());
                    break;
                default:
                    Assertions.fail("too many encodings");
            }
            total_encodings++;
        }
        Assertions.assertEquals(2, total_encodings);
    }

    @Test
    public void testCacheDoesNotAssumeContentLocationHeaderIndicatesAnotherCacheableResource() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/foo");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","public,max-age=3600");
        resp1.setHeader("Etag","\"etag\"");
        resp1.setHeader("Content-Location","http://foo.example.com/bar");

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/bar");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control","public,max-age=3600");
        resp2.setHeader("Etag","\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);
    }

    @Test
    public void testCachedResponsesWithMissingDateHeadersShouldBeAssignedOne() throws Exception {
        originResponse.removeHeaders("Date");
        originResponse.setHeader("Cache-Control","public");
        originResponse.setHeader("ETag","\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);
        Assertions.assertNotNull(result.getFirstHeader("Date"));
    }

    private void testInvalidExpiresHeaderIsTreatedAsStale(
            final String expiresHeader) throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","public");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Expires", expiresHeader);

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        // second request to origin MUST happen
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);
    }

    @Test
    public void testMalformedExpiresHeaderIsTreatedAsStale() throws Exception {
        testInvalidExpiresHeaderIsTreatedAsStale("garbage");
    }

    @Test
    public void testExpiresZeroHeaderIsTreatedAsStale() throws Exception {
        testInvalidExpiresHeaderIsTreatedAsStale("0");
    }

    @Test
    public void testExpiresHeaderEqualToDateHeaderIsTreatedAsStale() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","public");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Expires", resp1.getFirstHeader("Date").getValue());

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        // second request to origin MUST happen
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);
    }

    @Test
    public void testDoesNotModifyServerResponseHeader() throws Exception {
        final String server = "MockServer/1.0";
        originResponse.setHeader("Server", server);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);
        Assertions.assertEquals(server, result.getFirstHeader("Server").getValue());
    }

}
