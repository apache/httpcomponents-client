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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
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
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/*
 * This test class captures functionality required to achieve unconditional
 * compliance with the HTTP/1.1 caching protocol (SHOULD, SHOULD NOT,
 * RECOMMENDED, and NOT RECOMMENDED behaviors).
 */
public class TestProtocolRecommendations {

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
    ClassicHttpRequest request;
    ClassicHttpResponse originResponse;
    CacheConfig config;
    CachingExec impl;
    HttpCache cache;
    Instant now;
    Instant tenSecondsAgo;
    Instant twoMinutesAgo;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        host = new HttpHost("foo.example.com", 80);

        route = new HttpRoute(host);

        body = HttpTestUtils.makeBody(ENTITY_LENGTH);

        request = new BasicClassicHttpRequest("GET", "/foo");

        context = HttpClientContext.create();

        originResponse = HttpTestUtils.make200Response();

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .build();

        cache = new BasicHttpCache(config);
        impl = new CachingExec(cache, null, config);

        now = Instant.now();
        tenSecondsAgo = now.minus(10, ChronoUnit.SECONDS);
        twoMinutesAgo = now.minus(1, ChronoUnit.MINUTES);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);
    }

    public ClassicHttpResponse execute(final ClassicHttpRequest request) throws IOException, HttpException {
        return impl.execute(
                ClassicRequestBuilder.copy(request).build(),
                new ExecChain.Scope("test", route, request, mockExecRuntime, context),
                mockExecChain);
    }

    private void cacheGenerated304ForValidatorShouldNotContainEntityHeader(
            final String headerName, final String headerValue, final String validatorHeader,
            final String validator, final String conditionalHeader) throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader(validatorHeader, validator);
        resp1.setHeader(headerName, headerValue);
        resp1.setHeader("ETag", "\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader(conditionalHeader, validator);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
        assertFalse(result.containsHeader(headerName));
    }

    private void cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
            final String headerName, final String headerValue) throws Exception {
        cacheGenerated304ForValidatorShouldNotContainEntityHeader(headerName,
                headerValue, "ETag", "\"etag\"", "If-None-Match");
    }

    private void cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
            final String headerName, final String headerValue) throws Exception {
        cacheGenerated304ForValidatorShouldNotContainEntityHeader(headerName,
                headerValue, "Last-Modified", DateUtils.formatStandardDate(twoMinutesAgo),
                "If-Modified-Since");
    }

    @Test
    public void cacheGenerated304ForStrongEtagValidatorShouldNotContainAllow() throws Exception {
        cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
                "Allow", "GET,HEAD");
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainAllow() throws Exception {
        cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
                "Allow", "GET,HEAD");
    }

    @Test
    public void cacheGenerated304ForStrongEtagValidatorShouldNotContainContentEncoding() throws Exception {
        cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
                "Content-Encoding", "gzip");
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainContentEncoding() throws Exception {
        cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
                "Content-Encoding", "gzip");
    }

    @Test
    public void cacheGenerated304ForStrongEtagValidatorShouldNotContainContentLanguage() throws Exception {
        cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
                "Content-Language", "en");
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainContentLanguage() throws Exception {
        cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
                "Content-Language", "en");
    }

    @Test
    public void cacheGenerated304ForStrongValidatorShouldNotContainContentLength() throws Exception {
        cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
                "Content-Length", "128");
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainContentLength() throws Exception {
        cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
                "Content-Length", "128");
    }

    @Test
    public void cacheGenerated304ForStrongValidatorShouldNotContainContentMD5() throws Exception {
        cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
                "Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainContentMD5() throws Exception {
        cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
                "Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
    }

    @Test
    public void cacheGenerated304ForStrongEtagValidatorShouldNotContainContentType() throws Exception {
        cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
                "Content-Type", "text/html");
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainContentType() throws Exception {
        cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
                "Content-Type", "text/html");
    }

    @Test
    public void cacheGenerated304ForStrongEtagValidatorShouldNotContainLastModified() throws Exception {
        cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
                "Last-Modified", DateUtils.formatStandardDate(tenSecondsAgo));
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainLastModified() throws Exception {
        cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
                "Last-Modified", DateUtils.formatStandardDate(twoMinutesAgo));
    }

    private ClassicHttpRequest requestToPopulateStaleCacheEntry() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public,max-age=5");
        resp1.setHeader("Etag","\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        return req1;
    }

    private void testDoesNotReturnStaleResponseOnError(final ClassicHttpRequest req2) throws Exception {
        final ClassicHttpRequest req1 = requestToPopulateStaleCacheEntry();

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenThrow(new IOException());

        ClassicHttpResponse result = null;
        try {
            result = execute(req2);
        } catch (final IOException acceptable) {
        }

        if (result != null) {
            assertNotEquals(HttpStatus.SC_OK, result.getCode());
        }
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFirstHandOneWithCacheControl() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Cache-Control","no-cache");
        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFreshWithMaxAge() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Cache-Control","max-age=0");
        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlySpecifiesLargerMaxAge() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Cache-Control","max-age=20");
        testDoesNotReturnStaleResponseOnError(req);
    }


    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFreshWithMinFresh() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Cache-Control","min-fresh=2");

        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFreshWithMaxStale() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Cache-Control","max-stale=2");

        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testMayReturnStaleResponseIfClientExplicitlySpecifiesAcceptableMaxStale() throws Exception {
        final ClassicHttpRequest req1 = requestToPopulateStaleCacheEntry();
        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control","max-stale=20");

        execute(req1);

        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_OK, result.getCode());

        Mockito.verify(mockExecChain, Mockito.atMost(1)).proceed(Mockito.any(), Mockito.any());
    }

    private void testDoesNotModifyHeaderOnResponses(final String headerName) throws Exception {
        final String headerValue = HttpTestUtils
            .getCanonicalHeaderValue(originResponse, headerName);
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final ClassicHttpResponse result = execute(request);

        MatcherAssert.assertThat(result, ContainsHeaderMatcher.contains(headerName, headerValue));
    }

    private void testDoesNotModifyHeaderOnRequests(final String headerName) throws Exception {
        final String headerValue = HttpTestUtils.getCanonicalHeaderValue(request, headerName);
        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(request);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain).proceed(reqCapture.capture(), Mockito.any());

        assertEquals(headerValue, HttpTestUtils.getCanonicalHeaderValue(reqCapture.getValue(), headerName));
    }

    @Test
    public void testDoesNotModifyAcceptRangesOnResponses() throws Exception {
        final String headerName = "Accept-Ranges";
        originResponse.setHeader(headerName,"bytes");
        testDoesNotModifyHeaderOnResponses(headerName);
    }

    @Test
    public void testDoesNotModifyAuthorizationOnRequests() throws Exception {
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        testDoesNotModifyHeaderOnRequests("Authorization");
    }

    @Test
    public void testDoesNotModifyContentLengthOnRequests() throws Exception {
        final ClassicHttpRequest post = new BasicClassicHttpRequest("POST", "/");
        post.setEntity(HttpTestUtils.makeBody(128));
        post.setHeader("Content-Length","128");
        request = post;
        testDoesNotModifyHeaderOnRequests("Content-Length");
    }

    @Test
    public void testDoesNotModifyContentLengthOnResponses() throws Exception {
        originResponse.setEntity(HttpTestUtils.makeBody(128));
        originResponse.setHeader("Content-Length","128");
        testDoesNotModifyHeaderOnResponses("Content-Length");
    }

    @Test
    public void testDoesNotModifyContentMD5OnRequests() throws Exception {
        final ClassicHttpRequest post = new BasicClassicHttpRequest("POST", "/");
        post.setEntity(HttpTestUtils.makeBody(128));
        post.setHeader("Content-Length","128");
        post.setHeader("Content-MD5","Q2hlY2sgSW50ZWdyaXR5IQ==");
        request = post;
        testDoesNotModifyHeaderOnRequests("Content-MD5");
    }

    @Test
    public void testDoesNotModifyContentMD5OnResponses() throws Exception {
        originResponse.setEntity(HttpTestUtils.makeBody(128));
        originResponse.setHeader("Content-MD5","Q2hlY2sgSW50ZWdyaXR5IQ==");
        testDoesNotModifyHeaderOnResponses("Content-MD5");
    }

    @Test
    public void testDoesNotModifyContentRangeOnRequests() throws Exception {
        final ClassicHttpRequest put = new BasicClassicHttpRequest("PUT", "/");
        put.setEntity(HttpTestUtils.makeBody(128));
        put.setHeader("Content-Length","128");
        put.setHeader("Content-Range","bytes 0-127/256");
        request = put;
        testDoesNotModifyHeaderOnRequests("Content-Range");
    }

    @Test
    public void testDoesNotModifyContentRangeOnResponses() throws Exception {
        request.setHeader("Range","bytes=0-128");
        originResponse.setCode(HttpStatus.SC_PARTIAL_CONTENT);
        originResponse.setReasonPhrase("Partial Content");
        originResponse.setEntity(HttpTestUtils.makeBody(128));
        originResponse.setHeader("Content-Range","bytes 0-127/256");
        testDoesNotModifyHeaderOnResponses("Content-Range");
    }

    @Test
    public void testDoesNotModifyContentTypeOnRequests() throws Exception {
        final ClassicHttpRequest post = new BasicClassicHttpRequest("POST", "/");
        post.setEntity(HttpTestUtils.makeBody(128));
        post.setHeader("Content-Length","128");
        post.setHeader("Content-Type","application/octet-stream");
        request = post;
        testDoesNotModifyHeaderOnRequests("Content-Type");
    }

    @Test
    public void testDoesNotModifyContentTypeOnResponses() throws Exception {
        originResponse.setHeader("Content-Type","application/octet-stream");
        testDoesNotModifyHeaderOnResponses("Content-Type");
    }

    @Test
    public void testDoesNotModifyDateOnRequests() throws Exception {
        request.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        testDoesNotModifyHeaderOnRequests("Date");
    }

    @Test
    public void testDoesNotModifyDateOnResponses() throws Exception {
        originResponse.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        testDoesNotModifyHeaderOnResponses("Date");
    }

    @Test
    public void testDoesNotModifyETagOnResponses() throws Exception {
        originResponse.setHeader("ETag", "\"random-etag\"");
        testDoesNotModifyHeaderOnResponses("ETag");
    }

    @Test
    public void testDoesNotModifyExpiresOnResponses() throws Exception {
        originResponse.setHeader("Expires", DateUtils.formatStandardDate(Instant.now()));
        testDoesNotModifyHeaderOnResponses("Expires");
    }

    @Test
    public void testDoesNotModifyFromOnRequests() throws Exception {
        request.setHeader("From", "foo@example.com");
        testDoesNotModifyHeaderOnRequests("From");
    }

    @Test
    public void testDoesNotModifyIfMatchOnRequests() throws Exception {
        request = new BasicClassicHttpRequest("DELETE", "/");
        request.setHeader("If-Match", "\"etag\"");
        testDoesNotModifyHeaderOnRequests("If-Match");
    }

    @Test
    public void testDoesNotModifyIfModifiedSinceOnRequests() throws Exception {
        request.setHeader("If-Modified-Since", DateUtils.formatStandardDate(Instant.now()));
        testDoesNotModifyHeaderOnRequests("If-Modified-Since");
    }

    @Test
    public void testDoesNotModifyIfNoneMatchOnRequests() throws Exception {
        request.setHeader("If-None-Match", "\"etag\"");
        testDoesNotModifyHeaderOnRequests("If-None-Match");
    }

    @Test
    public void testDoesNotModifyIfRangeOnRequests() throws Exception {
        request.setHeader("Range","bytes=0-128");
        request.setHeader("If-Range", "\"etag\"");
        testDoesNotModifyHeaderOnRequests("If-Range");
    }

    @Test
    public void testDoesNotModifyIfUnmodifiedSinceOnRequests() throws Exception {
        request = new BasicClassicHttpRequest("DELETE", "/");
        request.setHeader("If-Unmodified-Since", DateUtils.formatStandardDate(Instant.now()));
        testDoesNotModifyHeaderOnRequests("If-Unmodified-Since");
    }

    @Test
    public void testDoesNotModifyLastModifiedOnResponses() throws Exception {
        originResponse.setHeader("Last-Modified", DateUtils.formatStandardDate(Instant.now()));
        testDoesNotModifyHeaderOnResponses("Last-Modified");
    }

    @Test
    public void testDoesNotModifyLocationOnResponses() throws Exception {
        originResponse.setCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        originResponse.setReasonPhrase("Temporary Redirect");
        originResponse.setHeader("Location", "http://foo.example.com/bar");
        testDoesNotModifyHeaderOnResponses("Location");
    }

    @Test
    public void testDoesNotModifyRangeOnRequests() throws Exception {
        request.setHeader("Range", "bytes=0-128");
        testDoesNotModifyHeaderOnRequests("Range");
    }

    @Test
    public void testDoesNotModifyRefererOnRequests() throws Exception {
        request.setHeader("Referer", "http://foo.example.com/bar");
        testDoesNotModifyHeaderOnRequests("Referer");
    }

    @Test
    public void testDoesNotModifyRetryAfterOnResponses() throws Exception {
        originResponse.setCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
        originResponse.setReasonPhrase("Service Unavailable");
        originResponse.setHeader("Retry-After", "120");
        testDoesNotModifyHeaderOnResponses("Retry-After");
    }

    @Test
    public void testDoesNotModifyServerOnResponses() throws Exception {
        originResponse.setHeader("Server", "SomeServer/1.0");
        testDoesNotModifyHeaderOnResponses("Server");
    }

    @Test
    public void testDoesNotModifyUserAgentOnRequests() throws Exception {
        request.setHeader("User-Agent", "MyClient/1.0");
        testDoesNotModifyHeaderOnRequests("User-Agent");
    }

    @Test
    public void testDoesNotModifyVaryOnResponses() throws Exception {
        request.setHeader("Accept-Encoding","identity");
        originResponse.setHeader("Vary", "Accept-Encoding");
        testDoesNotModifyHeaderOnResponses("Vary");
    }

    @Test
    public void testDoesNotModifyExtensionHeaderOnRequests() throws Exception {
        request.setHeader("X-Extension","x-value");
        testDoesNotModifyHeaderOnRequests("X-Extension");
    }

    @Test
    public void testDoesNotModifyExtensionHeaderOnResponses() throws Exception {
        originResponse.setHeader("X-Extension", "x-value");
        testDoesNotModifyHeaderOnResponses("X-Extension");
    }

    @Test
    public void testUsesLastModifiedDateForCacheConditionalRequests() throws Exception {
        final Instant twentySecondsAgo = now.plusSeconds(20);
        final String lmDate = DateUtils.formatStandardDate(twentySecondsAgo);

        final ClassicHttpRequest req1 =
            new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", lmDate);
        resp1.setHeader("Cache-Control","max-age=5");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(reqCapture.capture(), Mockito.any());

        final ClassicHttpRequest captured = reqCapture.getValue();
        MatcherAssert.assertThat(captured, ContainsHeaderMatcher.contains("If-Modified-Since", lmDate));
    }

    @Test
    public void testUsesBothLastModifiedAndETagForConditionalRequestsIfAvailable() throws Exception {
        final Instant twentySecondsAgo = now.plusSeconds(20);
        final String lmDate = DateUtils.formatStandardDate(twentySecondsAgo);
        final String etag = "\"etag\"";

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", lmDate);
        resp1.setHeader("Cache-Control","max-age=5");
        resp1.setHeader("ETag", etag);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(reqCapture.capture(), Mockito.any());

        final ClassicHttpRequest captured = reqCapture.getValue();

        MatcherAssert.assertThat(captured, ContainsHeaderMatcher.contains("If-Modified-Since", lmDate));
        MatcherAssert.assertThat(captured, ContainsHeaderMatcher.contains("If-None-Match", etag));
    }

    @Test
    public void testRevalidatesCachedResponseWithExpirationInThePast() throws Exception {
        final Instant oneSecondAgo = now.minusSeconds(1);
        final Instant oneSecondFromNow = now.plusSeconds(1);
        final Instant twoSecondsFromNow = now.plusSeconds(2);
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatStandardDate(now));
        resp1.setHeader("Expires",DateUtils.formatStandardDate(oneSecondAgo));
        resp1.setHeader("Cache-Control", "public");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpRequest revalidate = new BasicClassicHttpRequest("GET", "/");
        revalidate.setHeader("If-None-Match","\"etag\"");

        final ClassicHttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(twoSecondsFromNow));
        resp2.setHeader("Expires", DateUtils.formatStandardDate(oneSecondFromNow));
        resp2.setHeader("ETag","\"etag\"");

        Mockito.when(mockExecChain.proceed(RequestEquivalent.eq(revalidate), Mockito.any())).thenReturn(resp2);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_OK, result.getCode());
    }

    @Test
    public void testRetriesValidationThatResultsInAnOlderDated304Response() throws Exception {
        final Instant elevenSecondsAgo = now.minusSeconds(11);
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","max-age=5");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatStandardDate(elevenSecondsAgo));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain, Mockito.times(3)).proceed(reqCapture.capture(), Mockito.any());

        final ClassicHttpRequest captured = reqCapture.getValue();
        boolean hasMaxAge0 = false;
        boolean hasNoCache = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(captured, HttpHeaders.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("max-age".equals(elt.getName())) {
                try {
                    final int maxage = Integer.parseInt(elt.getValue());
                    if (maxage == 0) {
                        hasMaxAge0 = true;
                    }
                } catch (final NumberFormatException nfe) {
                    // nop
                }
            } else if ("no-cache".equals(elt.getName())) {
                hasNoCache = true;
            }
        }
        assertTrue(hasMaxAge0 || hasNoCache);
        assertFalse(captured.containsHeader("If-None-Match"));
        assertFalse(captured.containsHeader("If-Modified-Since"));
        assertFalse(captured.containsHeader("If-Range"));
        assertFalse(captured.containsHeader("If-Match"));
        assertFalse(captured.containsHeader("If-Unmodified-Since"));
    }

    @Test
    public void testSendsAllVariantEtagsInConditionalRequest() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET","/");
        req1.setHeader("User-Agent","agent1");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("Vary","User-Agent");
        resp1.setHeader("Etag","\"etag1\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET","/");
        req2.setHeader("User-Agent","agent2");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control","max-age=3600");
        resp2.setHeader("Vary","User-Agent");
        resp2.setHeader("Etag","\"etag2\"");

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET","/");
        req3.setHeader("User-Agent","agent3");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        Mockito.when(mockExecChain.proceed(Mockito.any(),Mockito.any())).thenReturn(resp3);

        execute(req3);

        final ArgumentCaptor<ClassicHttpRequest> reqCapture = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(mockExecChain, Mockito.times(3)).proceed(reqCapture.capture(), Mockito.any());

        final ClassicHttpRequest captured = reqCapture.getValue();
        boolean foundEtag1 = false;
        boolean foundEtag2 = false;
        for(final Header h : captured.getHeaders("If-None-Match")) {
            for(final String etag : h.getValue().split(",")) {
                if ("\"etag1\"".equals(etag.trim())) {
                    foundEtag1 = true;
                }
                if ("\"etag2\"".equals(etag.trim())) {
                    foundEtag2 = true;
                }
            }
        }
        assertTrue(foundEtag1 && foundEtag2);
    }

    @Test
    public void testResponseToExistingVariantsUpdatesEntry() throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("User-Agent", "agent1");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Vary", "User-Agent");
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("ETag", "\"etag1\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("User-Agent", "agent2");

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp2.setHeader("Vary", "User-Agent");
        resp2.setHeader("Cache-Control", "max-age=3600");
        resp2.setHeader("ETag", "\"etag2\"");

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");
        req3.setHeader("User-Agent", "agent3");

        final ClassicHttpResponse resp3 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp3.setHeader("Date", DateUtils.formatStandardDate(now));
        resp3.setHeader("ETag", "\"etag1\"");

        final ClassicHttpRequest req4 = new BasicClassicHttpRequest("GET", "/");
        req4.setHeader("User-Agent", "agent1");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);
        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);
        execute(req2);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp3);
        final ClassicHttpResponse result1 = execute(req3);

        final ClassicHttpResponse result2 = execute(req4);

        assertEquals(HttpStatus.SC_OK, result1.getCode());
        MatcherAssert.assertThat(result1, ContainsHeaderMatcher.contains("ETag", "\"etag1\""));
        MatcherAssert.assertThat(result1, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(now)));
        MatcherAssert.assertThat(result2, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(now)));
    }

    @Test
    public void testResponseToExistingVariantsIsCachedForFutureResponses() throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("User-Agent", "agent1");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Vary", "User-Agent");
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("ETag", "\"etag1\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("User-Agent", "agent2");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("Date", DateUtils.formatStandardDate(now));
        resp2.setHeader("ETag", "\"etag1\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");
        req3.setHeader("User-Agent", "agent2");

        execute(req3);

        Mockito.verify(mockExecChain, Mockito.times(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void shouldInvalidateNonvariantCacheEntryForUnknownMethod() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("FROB", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control","max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();
        resp3.setHeader("ETag", "\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp3);

        execute(req2);
        final ClassicHttpResponse result = execute(req3);

        assertTrue(HttpTestUtils.semanticallyTransparent(resp3, result));
    }

    @Test
    public void shouldInvalidateAllVariantsForUnknownMethod() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("User-Agent", "agent1");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("Vary", "User-Agent");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("User-Agent", "agent2");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control","max-age=3600");
        resp2.setHeader("Vary", "User-Agent");

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("FROB", "/");
        req3.setHeader("User-Agent", "agent3");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();
        resp3.setHeader("Cache-Control","max-age=3600");

        final ClassicHttpRequest req4 = new BasicClassicHttpRequest("GET", "/");
        req4.setHeader("User-Agent", "agent1");
        final ClassicHttpResponse resp4 = HttpTestUtils.make200Response();
        resp4.setHeader("ETag", "\"etag1\"");

        final ClassicHttpRequest req5 = new BasicClassicHttpRequest("GET", "/");
        req5.setHeader("User-Agent", "agent2");
        final ClassicHttpResponse resp5 = HttpTestUtils.make200Response();
        resp5.setHeader("ETag", "\"etag2\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp3);

        execute(req3);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp4);

        final ClassicHttpResponse result4 = execute(req4);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp5);

        final ClassicHttpResponse result5 = execute(req5);

        assertTrue(HttpTestUtils.semanticallyTransparent(resp4, result4));
        assertTrue(HttpTestUtils.semanticallyTransparent(resp5, result5));
    }

    @Test
    public void cacheShouldUpdateWithNewCacheableResponse() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("ETag", "\"etag1\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-age=0");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatStandardDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600");
        resp2.setHeader("ETag", "\"etag2\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpRequest req3 = HttpTestUtils.makeDefaultRequest();

        execute(req2);
        final ClassicHttpResponse result = execute(req3);

        assertTrue(HttpTestUtils.semanticallyTransparent(resp2, result));
    }

    @Test
    public void expiresEqualToDateWithNoCacheControlIsNotCacheable() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(now));
        resp1.setHeader("Expires", DateUtils.formatStandardDate(now));
        resp1.removeHeaders("Cache-Control");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-stale=1000");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"etag2\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertTrue(HttpTestUtils.semanticallyTransparent(resp2, result));
    }

    @Test
    public void expiresPriorToDateWithNoCacheControlIsNotCacheable() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(now));
        resp1.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.removeHeaders("Cache-Control");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-stale=1000");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"etag2\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertTrue(HttpTestUtils.semanticallyTransparent(resp2, result));
    }

    @Test
    public void cacheMissResultsIn504WithOnlyIfCached() throws Exception {
        final ClassicHttpRequest req = HttpTestUtils.makeDefaultRequest();
        req.setHeader("Cache-Control", "only-if-cached");

        final ClassicHttpResponse result = execute(req);

        assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, result.getCode());
    }

    @Test
    public void cacheHitOkWithOnlyIfCached() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "only-if-cached");

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, result));
    }

    @Test
    public void returns504ForStaleEntryWithOnlyIfCached() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "only-if-cached");

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, result.getCode());
    }

    @Test
    public void returnsStaleCacheEntryWithOnlyIfCachedAndMaxStale() throws Exception {

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","max-age=5");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-stale=20, only-if-cached");

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, result));
    }

    @Test
    public void issues304EvenWithWeakETag() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=300");
        resp1.setHeader("ETag","W/\"weak-sauce\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("If-None-Match","W/\"weak-sauce\"");

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
    }

}
