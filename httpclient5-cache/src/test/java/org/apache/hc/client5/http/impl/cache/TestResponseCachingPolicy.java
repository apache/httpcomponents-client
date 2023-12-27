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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestResponseCachingPolicy {

    private ResponseCachingPolicy policy;
    private HttpResponse response;
    private HttpRequest request;
    private final int[] acceptableCodes = new int[] { HttpStatus.SC_OK,
            HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION, HttpStatus.SC_MULTIPLE_CHOICES,
            HttpStatus.SC_MOVED_PERMANENTLY, HttpStatus.SC_GONE };
    private Instant now;
    private Instant tenSecondsFromNow;
    private Instant sixSecondsAgo;
    private ResponseCacheControl responseCacheControl;

    @BeforeEach
    public void setUp() throws Exception {
        now = Instant.now();
        sixSecondsAgo = now.minusSeconds(6);
        tenSecondsFromNow = now.plusSeconds(10);

        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("GET","/");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        response.setHeader("Content-Length", "0");
        responseCacheControl = ResponseCacheControl.builder().build();
    }

    @Test
    public void testGetCacheable() {
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest(Method.GET, "/");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testHeadCacheable() {
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest(Method.HEAD, "/");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testArbitraryMethodNotCacheable() {
        request = new BasicHttpRequest("PUT", "/");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));

        request = new BasicHttpRequest("huh", "/");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToRequestsWithAuthorizationHeadersAreNotCacheableBySharedCache() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToRequestsWithAuthorizationHeadersAreCacheableByNonSharedCache() {
        policy = new ResponseCachingPolicy(false, false, false);
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testAuthorizedResponsesWithSMaxAgeAreCacheable() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        responseCacheControl = ResponseCacheControl.builder()
                .setSharedMaxAge(3600)
                .build();

        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testAuthorizedResponsesWithCacheControlPublicAreCacheable() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testAuthorizedResponsesWithCacheControlMaxAgeAreNotCacheable() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(3600)
                .build();
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test203ResponseCodeIsCacheable() {
        response.setCode(HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION);
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test206ResponseCodeIsNotCacheable() {
        response.setCode(HttpStatus.SC_PARTIAL_CONTENT);
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test300ResponseCodeIsCacheable() {
        response.setCode(HttpStatus.SC_MULTIPLE_CHOICES);
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test301ResponseCodeIsCacheable() {
        response.setCode(HttpStatus.SC_MOVED_PERMANENTLY);
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test410ResponseCodeIsCacheable() {
        response.setCode(HttpStatus.SC_GONE);
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testPlain302ResponseCodeIsNotCacheable() {
        response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
        response.removeHeaders("Expires");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testPlain303ResponseCodeIsNotCacheableUnderDefaultBehavior() {
        response.setCode(HttpStatus.SC_SEE_OTHER);
        response.removeHeaders("Expires");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testPlain303ResponseCodeIsNotCacheableEvenIf303CachingEnabled() {
        policy = new ResponseCachingPolicy(true, false, true);
        response.setCode(HttpStatus.SC_SEE_OTHER);
        response.removeHeaders("Expires");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }


    @Test
    public void testPlain307ResponseCodeIsNotCacheable() {
        response.setCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        response.removeHeaders("Expires");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testNon206WithExplicitExpiresIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.setHeader("Expires", DateUtils.formatStandardDate(Instant.now().plus(1, ChronoUnit.HOURS)));
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testNon206WithMaxAgeIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(0)
                .build();
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testMissingCacheControlHeader() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.removeHeaders(HttpHeaders.CACHE_CONTROL);
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testNon206WithSMaxAgeIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        responseCacheControl = ResponseCacheControl.builder()
                .setSharedMaxAge(1)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testNon206WithMustRevalidateIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        responseCacheControl = ResponseCacheControl.builder()
                .setMustRevalidate(true)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testNon206WithProxyRevalidateIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        responseCacheControl = ResponseCacheControl.builder()
                .setProxyRevalidate(true)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testNon206WithPublicCacheControlIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testNon206WithPrivateCacheControlIsNotCacheableBySharedCache() {
        final int status = getRandomStatus();
        response.setCode(status);
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePrivate(true)
                .build();
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test200ResponseWithPrivateCacheControlIsCacheableByNonSharedCache() {
        policy = new ResponseCachingPolicy(false, false, false);
        response.setCode(HttpStatus.SC_OK);
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePrivate(true)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testControlNoCacheCacheable() {
        responseCacheControl = ResponseCacheControl.builder()
                .setNoCache(true)
                .build();

        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testControlNoStoreNotCacheable() {
        responseCacheControl = ResponseCacheControl.builder()
                .setNoStore(true)
                .build();

        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testControlNoStoreEmbeddedInListCacheable() {
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .setNoStore(true)
                .build();

        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testControlNoCacheEmbeddedInListCacheable() {
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .setNoCache(true)
                .build();

        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testControlNoCacheEmbeddedInListAfterFirstHeaderCacheable() {
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(20)
                .setCachePublic(true)
                .setNoCache(true)
                .build();

        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testControlNoStoreEmbeddedInListAfterFirstHeaderCacheable() {
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(20)
                .setCachePublic(true)
                .setNoStore(true)
                .build();

        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testControlAnyCacheControlCacheable() {
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(10)
                .build();

        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));

        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        response.setHeader("Content-Length", "0");
        responseCacheControl = ResponseCacheControl.builder()
                .build();

        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testControlWithout200Cacheable() {
        HttpResponse response404 = new BasicHttpResponse(HttpStatus.SC_NOT_FOUND, "");

        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response404));

        response404 = new BasicHttpResponse(HttpStatus.SC_GATEWAY_TIMEOUT, "");

        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response404));
    }

    @Test
    public void testVaryStarIsNotCacheable() {
        response.setHeader("Vary", "*");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testVaryStarIsNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setHeader("Vary", "*");
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .build();
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testRequestWithVaryHeaderCacheable() {
        response.addHeader("Vary", "Accept-Encoding");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testIsArbitraryMethodCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(true, false, false);

        request = new HttpOptions("http://foo.example.com/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setCode(HttpStatus.SC_NO_CONTENT);
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .build();

        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesWithMultipleAgeHeadersAreCacheable() {
        response.addHeader("Age", "3");
        response.addHeader("Age", "5");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesWithMultipleAgeHeadersAreNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.addHeader("Age", "3");
        response.addHeader("Age", "5");
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesWithMultipleDateHeadersAreNotCacheable() {
        response.addHeader("Date", DateUtils.formatStandardDate(now));
        response.addHeader("Date", DateUtils.formatStandardDate(sixSecondsAgo));
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesWithMultipleDateHeadersAreNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.addHeader("Date", DateUtils.formatStandardDate(now));
        response.addHeader("Date", DateUtils.formatStandardDate(sixSecondsAgo));
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .build();
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesWithMalformedDateHeadersAreNotCacheable() {
        response.addHeader("Date", "garbage");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesWithMalformedDateHeadersAreNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.addHeader("Date", "garbage");
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .build();
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesWithMultipleExpiresHeadersAreNotCacheable() {
        response.addHeader("Expires", DateUtils.formatStandardDate(now));
        response.addHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo));
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesWithMultipleExpiresHeadersAreNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.addHeader("Expires", DateUtils.formatStandardDate(now));
        response.addHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo));
        responseCacheControl = ResponseCacheControl.builder()
                .setCachePublic(true)
                .build();
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesThatAreSmallEnoughAreCacheable() {
        response.setHeader("Content-Length", "0");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToGETWithQueryParamsButNoExplicitCachingAreNotCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToHEADWithQueryParamsButNoExplicitCachingAreNotCacheable() {
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToGETWithQueryParamsButNoExplicitCachingAreNotCacheableEvenWhen1_0QueryCachingDisabled() {
        policy = new ResponseCachingPolicy(true, true, false);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToHEADWithQueryParamsButNoExplicitCachingAreNotCacheableEvenWhen1_0QueryCachingDisabled() {
        policy = new ResponseCachingPolicy(true, true, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToGETWithQueryParamsAndExplicitCachingAreCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToHEADWithQueryParamsAndExplicitCachingAreCacheable() {
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToGETWithQueryParamsAndExplicitCachingAreCacheableEvenWhen1_0QueryCachingDisabled() {
        policy = new ResponseCachingPolicy(true, true, false);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testResponsesToHEADWithQueryParamsAndExplicitCachingAreCacheableEvenWhen1_0QueryCachingDisabled() {
        policy = new ResponseCachingPolicy(true, true, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheable() {
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheableEvenWithSetting() {
        policy = new ResponseCachingPolicy(true, true, false);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheableEvenWithSetting() {
        policy = new ResponseCachingPolicy(true, true, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersDirectlyFrom1_0OriginsAreCacheableWithExpires() {
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsCanBeNotCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(true, true, false);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersDirectlyFrom1_0OriginsCanBeNotCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(true, true, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaProxiesAreNotCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader(HttpHeaders.VIA, "1.0 someproxy");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaProxiesAreNotCacheable() {
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader(HttpHeaders.VIA, "1.0 someproxy");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaProxiesAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "1.0 someproxy");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaProxiesAreCacheableWithExpires() {
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "1.0 someproxy");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaProxiesCanNotBeCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(true, true, true);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "1.0 someproxy");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaProxiesCanNotBeCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(true, true, true);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "1.0 someproxy");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaExplicitProxiesAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "HTTP/1.0 someproxy");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaExplicitProxiesAreCacheableWithExpires() {
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "HTTP/1.0 someproxy");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaExplicitProxiesCanNotBeCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(true, true, true);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "HTTP/1.0 someproxy");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaExplicitProxiesCanNotBeCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(true, true, true);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "HTTP/1.0 someproxy");
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_1OriginsVia1_0ProxiesAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "1.1 someproxy");
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_1OriginsVia1_0ProxiesAreCacheableWithExpires() {
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(tenSecondsFromNow));
        response.setHeader(HttpHeaders.VIA, "1.1 someproxy");

        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void notCacheableIfExpiresEqualsDateAndNoCacheControl() {
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(now));
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void notCacheableIfExpiresPrecedesDateAndNoCacheControl() {
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        response.setHeader("Expires", DateUtils.formatStandardDate(sixSecondsAgo));
        Assertions.assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test302WithExplicitCachingHeaders() {
        response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(300)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test303WithExplicitCachingHeadersWhenPermittedByConfig() {
        // HTTPbis working group says ok if explicitly indicated by
        // response headers
        policy = new ResponseCachingPolicy(true, false, true);
        response.setCode(HttpStatus.SC_SEE_OTHER);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(300)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void test307WithExplicitCachingHeaders() {
        response.setCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(300)
                .build();
        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void otherStatusCodesAreCacheableWithExplicitCachingHeaders() {
        response.setCode(HttpStatus.SC_NOT_FOUND);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(300)
                .build();
        assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    void testIsResponseCacheableNullCacheControl() {

        // Set up test data
        final Duration tenSecondsFromNow = Duration.ofSeconds(10);

        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now()));
        response.setHeader(HttpHeaders.EXPIRES, DateUtils.formatStandardDate(Instant.now().plus(tenSecondsFromNow)));


        // Create ResponseCachingPolicy instance and test the method
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("GET", "/foo");
        assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }


    @Test
    void testIsResponseCacheableNotNullCacheControlSmaxAge60() {

        // Set up test data
        final Duration tenSecondsFromNow = Duration.ofSeconds(10);

        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now()));
        response.setHeader(HttpHeaders.EXPIRES, DateUtils.formatStandardDate(Instant.now().plus(tenSecondsFromNow)));


        // Create ResponseCachingPolicy instance and test the method
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("GET", "/foo");
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(60)
                .build();
        assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    void testIsResponseCacheableNotNullCacheControlMaxAge60() {

        // Set up test data
        final Duration tenSecondsFromNow = Duration.ofSeconds(10);

        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now()));
        response.setHeader(HttpHeaders.EXPIRES, DateUtils.formatStandardDate(Instant.now().plus(tenSecondsFromNow)));


        // Create ResponseCachingPolicy instance and test the method
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("GET", "/foo");
        responseCacheControl = ResponseCacheControl.builder()
                .setMaxAge(60)
                .build();
        assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    void testIsResponseCacheableNotExsiresAndDate() {

        // Set up test data
        final Duration tenSecondsFromNow = Duration.ofSeconds(10);

        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now()));
        response.setHeader(HttpHeaders.EXPIRES, DateUtils.formatStandardDate(Instant.now().plus(tenSecondsFromNow)));


        // Create ResponseCachingPolicy instance and test the method
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("GET", "/foo");
        assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    private int getRandomStatus() {
        final int rnd = new Random().nextInt(acceptableCodes.length);

        return acceptableCodes[rnd];
    }

    @Test
    public void testIsResponseCacheable() {
        request = new BasicHttpRequest("GET","/foo?s=bar");
        // HTTPbis working group says ok if explicitly indicated by
        // response headers
        policy = new ResponseCachingPolicy(true, false, true);
        response.setCode(HttpStatus.SC_OK);
        response.setHeader("Date", DateUtils.formatStandardDate(now));
        assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    void testIsResponseCacheableNoCache() {
        // Set up test data
        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now()));

        // Create ResponseCachingPolicy instance and test the method
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("GET", "/foo");
        responseCacheControl = ResponseCacheControl.builder()
                .setNoCache(true)
                .build();
        assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    void testIsResponseCacheableNoStore() {
        // Set up test data
        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now()));

        // Create ResponseCachingPolicy instance and test the method
        policy = new ResponseCachingPolicy(true, false, false);
        request = new BasicHttpRequest("GET", "/foo");
        responseCacheControl = ResponseCacheControl.builder()
                .setNoStore(true)
                .build();
        assertFalse(policy.isResponseCacheable(responseCacheControl, request, response));
    }

    @Test
    public void testImmutableAndFreshResponseIsCacheable() {
        responseCacheControl = ResponseCacheControl.builder()
                .setImmutable(true)
                .setMaxAge(3600) // set this to a value that ensures the response is still fresh
                .build();

        Assertions.assertTrue(policy.isResponseCacheable(responseCacheControl, request, response));
    }
}