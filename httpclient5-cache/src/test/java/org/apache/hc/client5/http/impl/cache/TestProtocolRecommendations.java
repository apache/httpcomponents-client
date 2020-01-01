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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/*
 * This test class captures functionality required to achieve unconditional
 * compliance with the HTTP/1.1 spec, i.e. all the SHOULD, SHOULD NOT,
 * RECOMMENDED, and NOT RECOMMENDED behaviors.
 */
public class TestProtocolRecommendations extends AbstractProtocolTest {

    private Date now;
    private Date tenSecondsAgo;
    private Date twoMinutesAgo;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        now = new Date();
        tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        twoMinutesAgo = new Date(now.getTime() - 2 * 60 * 1000L);
    }

    /* "identity: The default (identity) encoding; the use of no
     * transformation whatsoever. This content-coding is used only in the
     * Accept-Encoding header, and SHOULD NOT be used in the
     * Content-Encoding header."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.5
     */
    @Test
    public void testIdentityCodingIsNotUsedInContentEncodingHeader() throws Exception {
        originResponse.setHeader("Content-Encoding", "identity");
        backendExpectsAnyRequest().andReturn(originResponse);
        replayMocks();
        final ClassicHttpResponse result = execute(request);
        verifyMocks();
        boolean foundIdentity = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(result, HttpHeaders.CONTENT_ENCODING);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("identity".equalsIgnoreCase(elt.getName())) {
                foundIdentity = true;
            }
        }
        assertFalse(foundIdentity);
    }

    /*
     * "304 Not Modified. ... If the conditional GET used a strong cache
     * validator (see section 13.3.3), the response SHOULD NOT include
     * other entity-headers."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
     */
    private void cacheGenerated304ForValidatorShouldNotContainEntityHeader(
            final String headerName, final String headerValue, final String validatorHeader,
            final String validator, final String conditionalHeader) throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader(validatorHeader, validator);
        resp1.setHeader(headerName, headerValue);

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader(conditionalHeader, validator);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        if (HttpStatus.SC_NOT_MODIFIED == result.getCode()) {
            assertNull(result.getFirstHeader(headerName));
        }
    }

    private void cacheGenerated304ForStrongETagValidatorShouldNotContainEntityHeader(
            final String headerName, final String headerValue) throws Exception {
        cacheGenerated304ForValidatorShouldNotContainEntityHeader(headerName,
                headerValue, "ETag", "\"etag\"", "If-None-Match");
    }

    private void cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
            final String headerName, final String headerValue) throws Exception {
        cacheGenerated304ForValidatorShouldNotContainEntityHeader(headerName,
                headerValue, "Last-Modified", DateUtils.formatDate(twoMinutesAgo),
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

    private void cacheGenerated304ForStrongValidatorShouldNotContainContentRange(
            final String validatorHeader, final String validator, final String conditionalHeader) throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        req1.setHeader("Range","bytes=0-127");
        final ClassicHttpResponse resp1 = new BasicClassicHttpResponse(HttpStatus.SC_PARTIAL_CONTENT, "Partial Content");
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader(validatorHeader, validator);
        resp1.setHeader("Content-Range", "bytes 0-127/256");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("If-Range", validator);
        req2.setHeader("Range","bytes=0-127");
        req2.setHeader(conditionalHeader, validator);

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader(validatorHeader, validator);

        // cache module does not currently deal with byte ranges, but we want
        // this test to work even if it does some day
        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();
        EasyMock.expect(
                mockExecChain.proceed(
                        EasyMock.capture(cap),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(resp2).times(0,1);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        if (!cap.hasCaptured()
            && HttpStatus.SC_NOT_MODIFIED == result.getCode()) {
            // cache generated a 304
            assertNull(result.getFirstHeader("Content-Range"));
        }
    }

    @Test
    public void cacheGenerated304ForStrongEtagValidatorShouldNotContainContentRange() throws Exception {
        cacheGenerated304ForStrongValidatorShouldNotContainContentRange(
                "ETag", "\"etag\"", "If-None-Match");
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainContentRange() throws Exception {
        cacheGenerated304ForStrongValidatorShouldNotContainContentRange(
                "Last-Modified", DateUtils.formatDate(twoMinutesAgo), "If-Modified-Since");
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
                "Last-Modified", DateUtils.formatDate(tenSecondsAgo));
    }

    @Test
    public void cacheGenerated304ForStrongDateValidatorShouldNotContainLastModified() throws Exception {
        cacheGenerated304ForStrongDateValidatorShouldNotContainEntityHeader(
                "Last-Modified", DateUtils.formatDate(twoMinutesAgo));
    }

    private void shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
            final String entityHeader, final String entityHeaderValue) throws Exception {
        final ClassicHttpRequest req = HttpTestUtils.makeDefaultRequest();
        req.setHeader("If-None-Match", "\"etag\"");

        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp.setHeader("Date", DateUtils.formatDate(now));
        resp.setHeader("Etag", "\"etag\"");
        resp.setHeader(entityHeader, entityHeaderValue);

        backendExpectsAnyRequestAndReturn(resp);

        replayMocks();
        final ClassicHttpResponse result = execute(req);
        verifyMocks();

        assertNull(result.getFirstHeader(entityHeader));
    }

    @Test
    public void shouldStripAllowFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Allow", "GET,HEAD");
    }

    @Test
    public void shouldStripContentEncodingFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-Encoding", "gzip");
    }

    @Test
    public void shouldStripContentLanguageFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-Language", "en");
    }

    @Test
    public void shouldStripContentLengthFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-Length", "128");
    }

    @Test
    public void shouldStripContentMD5FromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
    }

    @Test
    public void shouldStripContentTypeFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-Type", "text/html;charset=utf-8");
    }

    @Test
    public void shouldStripContentRangeFromOrigin304ResponseToStringValidation() throws Exception {
        final ClassicHttpRequest req = HttpTestUtils.makeDefaultRequest();
        req.setHeader("If-Range","\"etag\"");
        req.setHeader("Range","bytes=0-127");

        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp.setHeader("Date", DateUtils.formatDate(now));
        resp.setHeader("ETag", "\"etag\"");
        resp.setHeader("Content-Range", "bytes 0-127/256");

        backendExpectsAnyRequestAndReturn(resp);

        replayMocks();
        final ClassicHttpResponse result = execute(req);
        verifyMocks();

        assertNull(result.getFirstHeader("Content-Range"));
    }

    @Test
    public void shouldStripLastModifiedFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Last-Modified", DateUtils.formatDate(twoMinutesAgo));
    }

    /*
     * "For this reason, a cache SHOULD NOT return a stale response if the
     * client explicitly requests a first-hand or fresh one, unless it is
     * impossible to comply for technical or policy reasons."
     */
    private ClassicHttpRequest requestToPopulateStaleCacheEntry() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public,max-age=5");
        resp1.setHeader("Etag","\"etag\"");

        backendExpectsAnyRequestAndReturn(resp1);
        return req1;
    }

    private void testDoesNotReturnStaleResponseOnError(final ClassicHttpRequest req2) throws Exception {
        final ClassicHttpRequest req1 = requestToPopulateStaleCacheEntry();

        backendExpectsAnyRequest().andThrow(new IOException());

        replayMocks();
        execute(req1);
        ClassicHttpResponse result = null;
        try {
            result = execute(req2);
        } catch (final IOException acceptable) {
        }
        verifyMocks();

        if (result != null) {
            assertFalse(result.getCode() == HttpStatus.SC_OK);
        }
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFirstHandOneWithCacheControl() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Cache-Control","no-cache");
        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFirstHandOneWithPragma() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
        req.setHeader("Pragma","no-cache");
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

        backendExpectsAnyRequest().andThrow(new IOException()).times(0,1);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getCode());
        assertNotNull(result.getFirstHeader("Warning"));
    }

    /*
     * "A correct cache MUST respond to a request with the most up-to-date
     * response held by the cache that is appropriate to the request
     * (see sections 13.2.5, 13.2.6, and 13.12) which meets one of the
     * following conditions:
     *
     * 1. It has been checked for equivalence with what the origin server
     * would have returned by revalidating the response with the
     * origin server (section 13.3);
     *
     * 2. It is "fresh enough" (see section 13.2). In the default case,
     * this means it meets the least restrictive freshness requirement
     * of the client, origin server, and cache (see section 14.9); if
     * the origin server so specifies, it is the freshness requirement
     * of the origin server alone.
     *
     * If a stored response is not "fresh enough" by the most
     * restrictive freshness requirement of both the client and the
     * origin server, in carefully considered circumstances the cache
     * MAY still return the response with the appropriate Warning
     * header (see section 13.1.5 and 14.46), unless such a response
     * is prohibited (e.g., by a "no-store" cache-directive, or by a
     * "no-cache" cache-request-directive; see section 14.9).
     *
     * 3. It is an appropriate 304 (Not Modified), 305 (Proxy Redirect),
     * or error (4xx or 5xx) response message.
     *
     * If the cache can not communicate with the origin server, then a
     * correct cache SHOULD respond as above if the response can be
     * correctly served from the cache..."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.1.1
     */
    @Test
    public void testReturnsCachedResponsesAppropriatelyWhenNoOriginCommunication() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");

        backendExpectsAnyRequest().andThrow(new IOException()).anyTimes();

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getCode());
        boolean warning111Found = false;
        for(final Header h : result.getHeaders("Warning")) {
            for(final WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 111) {
                    warning111Found = true;
                    break;
                }
            }
        }
        assertTrue(warning111Found);
    }

    /*
     * "If a cache receives a response (either an entire response, or a
     * 304 (Not Modified) response) that it would normally forward to the
     * requesting client, and the received response is no longer fresh,
     * the cache SHOULD forward it to the requesting client without adding
     * a new Warning (but without removing any existing Warning headers).
     * A cache SHOULD NOT attempt to revalidate a response simply because
     * that response became stale in transit; this might lead to an
     * infinite loop."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.1.1
     */
    @Test
    public void testDoesNotAddNewWarningHeaderIfResponseArrivesStale() throws Exception {
        originResponse.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        originResponse.setHeader("Cache-Control","public, max-age=5");
        originResponse.setHeader("ETag","\"etag\"");

        backendExpectsAnyRequest().andReturn(originResponse);

        replayMocks();
        final ClassicHttpResponse result = execute(request);
        verifyMocks();

        assertNull(result.getFirstHeader("Warning"));
    }

    @Test
    public void testForwardsExistingWarningHeadersOnResponseThatArrivesStale() throws Exception {
        originResponse.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        originResponse.setHeader("Cache-Control","public, max-age=5");
        originResponse.setHeader("ETag","\"etag\"");
        originResponse.addHeader("Age","10");
        final String warning = "110 fred \"Response is stale\"";
        originResponse.addHeader("Warning",warning);

        backendExpectsAnyRequest().andReturn(originResponse);

        replayMocks();
        final ClassicHttpResponse result = execute(request);
        verifyMocks();

        assertEquals(warning, result.getFirstHeader("Warning").getValue());
    }

    /*
     * "A transparent proxy SHOULD NOT modify an end-to-end header unless
     * the definition of that header requires or specifically allows that."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.5.2
     */
    private void testDoesNotModifyHeaderOnResponses(final String headerName) throws Exception {
        final String headerValue = HttpTestUtils
            .getCanonicalHeaderValue(originResponse, headerName);
        backendExpectsAnyRequest().andReturn(originResponse);
        replayMocks();
        final ClassicHttpResponse result = execute(request);
        verifyMocks();
        assertEquals(headerValue,
            result.getFirstHeader(headerName).getValue());
    }

    private void testDoesNotModifyHeaderOnRequests(final String headerName) throws Exception {
        final String headerValue = HttpTestUtils.getCanonicalHeaderValue(request, headerName);
        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();
        EasyMock.expect(
                mockExecChain.proceed(
                        EasyMock.capture(cap),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(originResponse);
        replayMocks();
        execute(request);
        verifyMocks();
        assertEquals(headerValue,
                HttpTestUtils.getCanonicalHeaderValue(cap.getValue(),
                        headerName));
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
        request.setHeader("Date", DateUtils.formatDate(new Date()));
        testDoesNotModifyHeaderOnRequests("Date");
    }

    @Test
    public void testDoesNotModifyDateOnResponses() throws Exception {
        originResponse.setHeader("Date", DateUtils.formatDate(new Date()));
        testDoesNotModifyHeaderOnResponses("Date");
    }

    @Test
    public void testDoesNotModifyETagOnResponses() throws Exception {
        originResponse.setHeader("ETag", "\"random-etag\"");
        testDoesNotModifyHeaderOnResponses("ETag");
    }

    @Test
    public void testDoesNotModifyExpiresOnResponses() throws Exception {
        originResponse.setHeader("Expires", DateUtils.formatDate(new Date()));
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
        request.setHeader("If-Modified-Since", DateUtils.formatDate(new Date()));
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
        request.setHeader("If-Unmodified-Since", DateUtils.formatDate(new Date()));
        testDoesNotModifyHeaderOnRequests("If-Unmodified-Since");
    }

    @Test
    public void testDoesNotModifyLastModifiedOnResponses() throws Exception {
        originResponse.setHeader("Last-Modified", DateUtils.formatDate(new Date()));
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


    /*
     * "[HTTP/1.1 clients], If only a Last-Modified value has been provided
     * by the origin server, SHOULD use that value in non-subrange cache-
     * conditional requests (using If-Modified-Since)."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.3.4
     */
    @Test
    public void testUsesLastModifiedDateForCacheConditionalRequests() throws Exception {
        final Date twentySecondsAgo = new Date(now.getTime() - 20 * 1000L);
        final String lmDate = DateUtils.formatDate(twentySecondsAgo);

        final ClassicHttpRequest req1 =
            new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", lmDate);
        resp1.setHeader("Cache-Control","max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);

        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();
        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        EasyMock.expect(
                mockExecChain.proceed(
                        EasyMock.capture(cap),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(resp2);

        replayMocks();
        execute(req1);
        execute(req2);
        verifyMocks();

        final ClassicHttpRequest captured = cap.getValue();
        final Header ifModifiedSince =
            captured.getFirstHeader("If-Modified-Since");
        assertEquals(lmDate, ifModifiedSince.getValue());
    }

    /*
     * "[HTTP/1.1 clients], if both an entity tag and a Last-Modified value
     * have been provided by the origin server, SHOULD use both validators
     * in cache-conditional requests. This allows both HTTP/1.0 and
     * HTTP/1.1 caches to respond appropriately."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.3.4
     */
    @Test
    public void testUsesBothLastModifiedAndETagForConditionalRequestsIfAvailable() throws Exception {
        final Date twentySecondsAgo = new Date(now.getTime() - 20 * 1000L);
        final String lmDate = DateUtils.formatDate(twentySecondsAgo);
        final String etag = "\"etag\"";

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Last-Modified", lmDate);
        resp1.setHeader("Cache-Control","max-age=5");
        resp1.setHeader("ETag", etag);

        backendExpectsAnyRequestAndReturn(resp1);

        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();
        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();

        EasyMock.expect(
                mockExecChain.proceed(
                        EasyMock.capture(cap),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(resp2);

        replayMocks();
        execute(req1);
        execute(req2);
        verifyMocks();

        final ClassicHttpRequest captured = cap.getValue();
        final Header ifModifiedSince =
            captured.getFirstHeader("If-Modified-Since");
        assertEquals(lmDate, ifModifiedSince.getValue());
        final Header ifNoneMatch =
            captured.getFirstHeader("If-None-Match");
        assertEquals(etag, ifNoneMatch.getValue());
    }

    /*
     * "If an origin server wishes to force a semantically transparent cache
     * to validate every request, it MAY assign an explicit expiration time
     * in the past. This means that the response is always stale, and so the
     * cache SHOULD validate it before using it for subsequent requests."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.2.1
     */
    @Test
    public void testRevalidatesCachedResponseWithExpirationInThePast() throws Exception {
        final Date oneSecondAgo = new Date(now.getTime() - 1 * 1000L);
        final Date oneSecondFromNow = new Date(now.getTime() + 1 * 1000L);
        final Date twoSecondsFromNow = new Date(now.getTime() + 2 * 1000L);
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Expires",DateUtils.formatDate(oneSecondAgo));
        resp1.setHeader("Cache-Control", "public");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpRequest revalidate = new BasicClassicHttpRequest("GET", "/");
        revalidate.setHeader("If-None-Match","\"etag\"");

        final ClassicHttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("Date", DateUtils.formatDate(twoSecondsFromNow));
        resp2.setHeader("Expires", DateUtils.formatDate(oneSecondFromNow));
        resp2.setHeader("ETag","\"etag\"");

        EasyMock.expect(
                mockExecChain.proceed(
                        eqRequest(revalidate),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(resp2);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK,
                result.getCode());
    }

    /* "When a client tries to revalidate a cache entry, and the response
     * it receives contains a Date header that appears to be older than the
     * one for the existing entry, then the client SHOULD repeat the
     * request unconditionally, and include
     *     Cache-Control: max-age=0
     * to force any intermediate caches to validate their copies directly
     * with the origin server, or
     *     Cache-Control: no-cache
     * to force any intermediate caches to obtain a new copy from the
     * origin server."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.2.6
     */
    @Test
    public void testRetriesValidationThatResultsInAnOlderDated304Response() throws Exception {
        final Date elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make304Response();
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(elevenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp2);

        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();
        resp3.setHeader("ETag","\"etag2\"");
        resp3.setHeader("Date", DateUtils.formatDate(now));
        resp3.setHeader("Cache-Control","max-age=5");

        EasyMock.expect(
                mockExecChain.proceed(
                        EasyMock.capture(cap),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(resp3);

        replayMocks();
        execute(req1);
        execute(req2);
        verifyMocks();

        final ClassicHttpRequest captured = cap.getValue();
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
        assertNull(captured.getFirstHeader("If-None-Match"));
        assertNull(captured.getFirstHeader("If-Modified-Since"));
        assertNull(captured.getFirstHeader("If-Range"));
        assertNull(captured.getFirstHeader("If-Match"));
        assertNull(captured.getFirstHeader("If-Unmodified-Since"));
    }

    /* "If an entity tag was assigned to a cached representation, the
     * forwarded request SHOULD be conditional and include the entity
     * tags in an If-None-Match header field from all its cache entries
     * for the resource."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.6
     */
    @Test
    public void testSendsAllVariantEtagsInConditionalRequest() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET","/");
        req1.setHeader("User-Agent","agent1");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("Vary","User-Agent");
        resp1.setHeader("Etag","\"etag1\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET","/");
        req2.setHeader("User-Agent","agent2");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control","max-age=3600");
        resp2.setHeader("Vary","User-Agent");
        resp2.setHeader("Etag","\"etag2\"");

        backendExpectsAnyRequestAndReturn(resp2);

        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();
        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET","/");
        req3.setHeader("User-Agent","agent3");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();

        EasyMock.expect(
                mockExecChain.proceed(
                        EasyMock.capture(cap),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(resp3);

        replayMocks();
        execute(req1);
        execute(req2);
        execute(req3);
        verifyMocks();

        final ClassicHttpRequest captured = cap.getValue();
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

    /* "If the entity-tag of the new response matches that of an existing
     * entry, the new response SHOULD be used to processChallenge the header fields
     * of the existing entry, and the result MUST be returned to the
     * client."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.6
     */
    @Test
    public void testResponseToExistingVariantsUpdatesEntry() throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("User-Agent", "agent1");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Vary", "User-Agent");
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("ETag", "\"etag1\"");


        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("User-Agent", "agent2");

        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp2.setHeader("Vary", "User-Agent");
        resp2.setHeader("Cache-Control", "max-age=3600");
        resp2.setHeader("ETag", "\"etag2\"");

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");
        req3.setHeader("User-Agent", "agent3");

        final ClassicHttpResponse resp3 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp3.setHeader("Date", DateUtils.formatDate(now));
        resp3.setHeader("ETag", "\"etag1\"");

        backendExpectsAnyRequestAndReturn(resp3);

        final ClassicHttpRequest req4 = new BasicClassicHttpRequest("GET", "/");
        req4.setHeader("User-Agent", "agent1");

        replayMocks();
        execute(req1);
        execute(req2);
        final ClassicHttpResponse result1 = execute(req3);
        final ClassicHttpResponse result2 = execute(req4);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result1.getCode());
        assertEquals("\"etag1\"", result1.getFirstHeader("ETag").getValue());
        assertEquals(DateUtils.formatDate(now), result1.getFirstHeader("Date").getValue());
        assertEquals(DateUtils.formatDate(now), result2.getFirstHeader("Date").getValue());
    }

    @Test
    public void testResponseToExistingVariantsIsCachedForFutureResponses() throws Exception {

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("User-Agent", "agent1");

        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Vary", "User-Agent");
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("ETag", "\"etag1\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("User-Agent", "agent2");

        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("ETag", "\"etag1\"");

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");
        req3.setHeader("User-Agent", "agent2");

        replayMocks();
        execute(req1);
        execute(req2);
        execute(req3);
        verifyMocks();
    }

    /* "If any of the existing cache entries contains only partial content
     * for the associated entity, its entity-tag SHOULD NOT be included in
     * the If-None-Match header field unless the request is for a range
     * that would be fully satisfied by that entry."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.6
     */
    @Test
    public void variantNegotiationsDoNotIncludeEtagsForPartialResponses() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        req1.setHeader("User-Agent", "agent1");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Vary", "User-Agent");
        resp1.setHeader("ETag", "\"etag1\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("User-Agent", "agent2");
        req2.setHeader("Range", "bytes=0-49");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_PARTIAL_CONTENT, "Partial Content");
        resp2.setEntity(HttpTestUtils.makeBody(50));
        resp2.setHeader("Content-Length","50");
        resp2.setHeader("Content-Range","bytes 0-49/100");
        resp2.setHeader("Vary","User-Agent");
        resp2.setHeader("ETag", "\"etag2\"");
        resp2.setHeader("Cache-Control","max-age=3600");
        resp2.setHeader("Date", DateUtils.formatDate(new Date()));

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = HttpTestUtils.makeDefaultRequest();
        req3.setHeader("User-Agent", "agent3");

        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("Vary", "User-Agent");
        resp1.setHeader("ETag", "\"etag3\"");

        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();
        EasyMock.expect(
                mockExecChain.proceed(
                        EasyMock.capture(cap),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(resp3);

        replayMocks();
        execute(req1);
        execute(req2);
        execute(req3);
        verifyMocks();

        final ClassicHttpRequest captured = cap.getValue();
        final Iterator<HeaderElement> it = MessageSupport.iterate(captured, HttpHeaders.IF_NONE_MATCH);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            assertFalse("\"etag2\"".equals(elt.toString()));
        }
    }

    /* "If a cache receives a successful response whose Content-Location
     * field matches that of an existing cache entry for the same Request-
     * URI, whose entity-tag differs from that of the existing entry, and
     * whose Date is more recent than that of the existing entry, the
     * existing entry SHOULD NOT be returned in response to future requests
     * and SHOULD be deleted from the cache.
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.6
     */
    @Test
    public void cachedEntryShouldNotBeUsedIfMoreRecentMentionInContentLocation() throws Exception {
        final ClassicHttpRequest req1 = new HttpGet("http://foo.example.com/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("ETag", "\"old-etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new HttpPost("http://foo.example.com/bar");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"new-etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Content-Location", "http://foo.example.com/");

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = new HttpGet("http://foo.example.com");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();

        backendExpectsAnyRequestAndReturn(resp3);

        replayMocks();
        execute(req1);
        execute(req2);
        execute(req3);
        verifyMocks();
    }

    /*
     * "This specifically means that responses from HTTP/1.0 servers for such
     * URIs [those containing a '?' in the rel_path part] SHOULD NOT be taken
     * from a cache."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.9
     */
    @Test
    public void responseToGetWithQueryFrom1_0OriginAndNoExpiresIsNotCached() throws Exception {
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/bar?baz=quux");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp2.setVersion(HttpVersion.HTTP_1_0);
        resp2.setEntity(HttpTestUtils.makeBody(200));
        resp2.setHeader("Content-Length","200");
        resp2.setHeader("Date", DateUtils.formatDate(now));

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req2);
        verifyMocks();
    }

    @Test
    public void responseToGetWithQueryFrom1_0OriginVia1_1ProxyAndNoExpiresIsNotCached() throws Exception {
        final ClassicHttpRequest req2 = new HttpGet("http://foo.example.com/bar?baz=quux");
        final ClassicHttpResponse resp2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        resp2.setVersion(HttpVersion.HTTP_1_0);
        resp2.setEntity(HttpTestUtils.makeBody(200));
        resp2.setHeader("Content-Length","200");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Via","1.0 someproxy");

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req2);
        verifyMocks();
    }

    /*
     * "A cache that passes through requests for methods it does not
     * understand SHOULD invalidate any entities referred to by the
     * Request-URI."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.10
     */
    @Test
    public void shouldInvalidateNonvariantCacheEntryForUnknownMethod() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("FROB", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control","max-age=3600");

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();
        resp3.setHeader("ETag", "\"etag\"");

        backendExpectsAnyRequestAndReturn(resp3);

        replayMocks();
        execute(req1);
        execute(req2);
        final ClassicHttpResponse result = execute(req3);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp3, result));
    }

    @Test
    public void shouldInvalidateAllVariantsForUnknownMethod() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        req1.setHeader("User-Agent", "agent1");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");
        resp1.setHeader("Vary", "User-Agent");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("User-Agent", "agent2");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control","max-age=3600");
        resp2.setHeader("Vary", "User-Agent");

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = new BasicClassicHttpRequest("FROB", "/");
        req3.setHeader("User-Agent", "agent3");
        final ClassicHttpResponse resp3 = HttpTestUtils.make200Response();
        resp3.setHeader("Cache-Control","max-age=3600");

        backendExpectsAnyRequestAndReturn(resp3);

        final ClassicHttpRequest req4 = new BasicClassicHttpRequest("GET", "/");
        req4.setHeader("User-Agent", "agent1");
        final ClassicHttpResponse resp4 = HttpTestUtils.make200Response();
        resp4.setHeader("ETag", "\"etag1\"");

        backendExpectsAnyRequestAndReturn(resp4);

        final ClassicHttpRequest req5 = new BasicClassicHttpRequest("GET", "/");
        req5.setHeader("User-Agent", "agent2");
        final ClassicHttpResponse resp5 = HttpTestUtils.make200Response();
        resp5.setHeader("ETag", "\"etag2\"");

        backendExpectsAnyRequestAndReturn(resp5);

        replayMocks();
        execute(req1);
        execute(req2);
        execute(req3);
        final ClassicHttpResponse result4 = execute(req4);
        final ClassicHttpResponse result5 = execute(req5);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp4, result4));
        assertTrue(HttpTestUtils.semanticallyTransparent(resp5, result5));
    }

    /*
     * "If a new cacheable response is received from a resource while any
     * existing responses for the same resource are cached, the cache
     * SHOULD use the new response to reply to the current request."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.12
     */
    @Test
    public void cacheShouldUpdateWithNewCacheableResponse() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=3600");
        resp1.setHeader("ETag", "\"etag1\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-age=0");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatDate(now));
        resp2.setHeader("Cache-Control", "max-age=3600");
        resp2.setHeader("ETag", "\"etag2\"");

        backendExpectsAnyRequestAndReturn(resp2);

        final ClassicHttpRequest req3 = HttpTestUtils.makeDefaultRequest();

        replayMocks();
        execute(req1);
        execute(req2);
        final ClassicHttpResponse result = execute(req3);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp2, result));
    }

    /*
     * "Many HTTP/1.0 cache implementations will treat an Expires value
     * that is less than or equal to the response Date value as being
     * equivalent to the Cache-Control response directive 'no-cache'.
     * If an HTTP/1.1 cache receives such a response, and the response
     * does not include a Cache-Control header field, it SHOULD consider
     * the response to be non-cacheable in order to retain compatibility
     * with HTTP/1.0 servers."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9.3
     */
    @Test
    public void expiresEqualToDateWithNoCacheControlIsNotCacheable() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Expires", DateUtils.formatDate(now));
        resp1.removeHeaders("Cache-Control");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-stale=1000");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"etag2\"");

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp2, result));
    }

    @Test
    public void expiresPriorToDateWithNoCacheControlIsNotCacheable() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(now));
        resp1.setHeader("Expires", DateUtils.formatDate(tenSecondsAgo));
        resp1.removeHeaders("Cache-Control");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-stale=1000");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("ETag", "\"etag2\"");

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp2, result));
    }

    /*
     * "If a request includes the no-cache directive, it SHOULD NOT
     * include min-fresh, max-stale, or max-age."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9.4
     */
    @Test
    public void otherFreshnessRequestDirectivesNotAllowedWithNoCache() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        req1.setHeader("Cache-Control", "min-fresh=10, no-cache");
        req1.addHeader("Cache-Control", "max-stale=0, max-age=0");


        final Capture<ClassicHttpRequest> cap = EasyMock.newCapture();
        EasyMock.expect(
                mockExecChain.proceed(
                        EasyMock.capture(cap),
                        EasyMock.isA(ExecChain.Scope.class))).andReturn(HttpTestUtils.make200Response());

        replayMocks();
        execute(req1);
        verifyMocks();

        final ClassicHttpRequest captured = cap.getValue();
        boolean foundNoCache = false;
        boolean foundDisallowedDirective = false;
        final List<String> disallowed =
            Arrays.asList("min-fresh", "max-stale", "max-age");
        final Iterator<HeaderElement> it = MessageSupport.iterate(captured, HttpHeaders.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (disallowed.contains(elt.getName())) {
                foundDisallowedDirective = true;
            }
            if ("no-cache".equals(elt.getName())) {
                foundNoCache = true;
            }
        }
        assertTrue(foundNoCache);
        assertFalse(foundDisallowedDirective);
    }

    /*
     * "To do this, the client may include the only-if-cached directive in
     * a request. If it receives this directive, a cache SHOULD either
     * respond using a cached entry that is consistent with the other
     * constraints of the request, or respond with a 504 (Gateway Timeout)
     * status."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9.4
     */
    @Test
    public void cacheMissResultsIn504WithOnlyIfCached() throws Exception {
        final ClassicHttpRequest req = HttpTestUtils.makeDefaultRequest();
        req.setHeader("Cache-Control", "only-if-cached");

        replayMocks();
        final ClassicHttpResponse result = execute(req);
        verifyMocks();

        assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT,
                result.getCode());
    }

    @Test
    public void cacheHitOkWithOnlyIfCached() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control","max-age=3600");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "only-if-cached");

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, result));
    }

    @Test
    public void returns504ForStaleEntryWithOnlyIfCached() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "only-if-cached");

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT,
                result.getCode());
    }

    @Test
    public void returnsStaleCacheEntryWithOnlyIfCachedAndMaxStale() throws Exception {

        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "max-stale=20, only-if-cached");

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertTrue(HttpTestUtils.semanticallyTransparent(resp1, result));
    }

    @Test
    public void issues304EvenWithWeakETag() throws Exception {
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "max-age=300");
        resp1.setHeader("ETag","W/\"weak-sauce\"");

        backendExpectsAnyRequestAndReturn(resp1);

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("If-None-Match","W/\"weak-sauce\"");

        replayMocks();
        execute(req1);
        final ClassicHttpResponse result = execute(req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());

    }

}
