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

import java.util.Date;
import java.util.Random;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestResponseCachingPolicy {

    private static final ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP", 1, 1);
    private ResponseCachingPolicy policy;
    private HttpResponse response;
    private HttpRequest request;
    private final int[] acceptableCodes = new int[] { HttpStatus.SC_OK,
            HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION, HttpStatus.SC_MULTIPLE_CHOICES,
            HttpStatus.SC_MOVED_PERMANENTLY, HttpStatus.SC_GONE };
    private Date now;
    private Date tenSecondsFromNow;
    private Date sixSecondsAgo;

    @Before
    public void setUp() throws Exception {
        now = new Date();
        sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        tenSecondsFromNow = new Date(now.getTime() + 10 * 1000L);

        policy = new ResponseCachingPolicy(0, true, false, false);
        request = new BasicHttpRequest("GET","/");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader("Date", DateUtils.formatDate(new Date()));
        response.setHeader("Content-Length", "0");
    }

    @Test
    public void testIsGetCacheable() {
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadCacheable() {
        policy = new ResponseCachingPolicy(0, true, false, false);
        Assert.assertTrue(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testResponsesToRequestsWithAuthorizationHeadersAreNotCacheableBySharedCache() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        Assert.assertFalse(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testResponsesToRequestsWithAuthorizationHeadersAreCacheableByNonSharedCache() {
        policy = new ResponseCachingPolicy(0, false, false, false);
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        Assert.assertTrue(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testAuthorizedResponsesWithSMaxAgeAreCacheable() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        response.setHeader("Cache-Control","s-maxage=3600");
        Assert.assertTrue(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testAuthorizedResponsesWithMustRevalidateAreCacheable() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        response.setHeader("Cache-Control","must-revalidate");
        Assert.assertTrue(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testAuthorizedResponsesWithCacheControlPublicAreCacheable() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        response.setHeader("Cache-Control","public");
        Assert.assertTrue(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testAuthorizedResponsesWithCacheControlMaxAgeAreNotCacheable() {
        request = new BasicHttpRequest("GET","/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " dXNlcjpwYXNzd2Q=");
        response.setHeader("Cache-Control","max-age=3600");
        Assert.assertFalse(policy.isResponseCacheable(request,response));
    }

    @Test
    public void test203ResponseCodeIsCacheable() {
        response.setCode(HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION);
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test206ResponseCodeIsNotCacheable() {
        response.setCode(HttpStatus.SC_PARTIAL_CONTENT);
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test206ResponseCodeIsNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(0, true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setCode(HttpStatus.SC_PARTIAL_CONTENT);
        response.setHeader("Cache-Control", "public");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void test300ResponseCodeIsCacheable() {
        response.setCode(HttpStatus.SC_MULTIPLE_CHOICES);
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test301ResponseCodeIsCacheable() {
        response.setCode(HttpStatus.SC_MOVED_PERMANENTLY);
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test410ResponseCodeIsCacheable() {
        response.setCode(HttpStatus.SC_GONE);
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testPlain302ResponseCodeIsNotCacheable() {
        response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
        response.removeHeaders("Expires");
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testPlain303ResponseCodeIsNotCacheableUnderDefaultBehavior() {
        response.setCode(HttpStatus.SC_SEE_OTHER);
        response.removeHeaders("Expires");
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testPlain303ResponseCodeIsNotCacheableEvenIf303CachingEnabled() {
        policy = new ResponseCachingPolicy(0, true, false, true);
        response.setCode(HttpStatus.SC_SEE_OTHER);
        response.removeHeaders("Expires");
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }


    @Test
    public void testPlain307ResponseCodeIsNotCacheable() {
        response.setCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        response.removeHeaders("Expires");
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithExplicitExpiresIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.setHeader("Expires", DateUtils.formatDate(new Date()));
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithMaxAgeIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.setHeader("Cache-Control", "max-age=0");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithSMaxAgeIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.setHeader("Cache-Control", "s-maxage=0");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithMustRevalidateIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.setHeader("Cache-Control", "must-revalidate");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithProxyRevalidateIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.setHeader("Cache-Control", "proxy-revalidate");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithPublicCacheControlIsCacheable() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.setHeader("Cache-Control", "public");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithPrivateCacheControlIsNotCacheableBySharedCache() {
        final int status = getRandomStatus();
        response.setCode(status);
        response.setHeader("Cache-Control", "private");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test200ResponseWithPrivateCacheControlIsCacheableByNonSharedCache() {
        policy = new ResponseCachingPolicy(0, false, false, false);
        response.setCode(HttpStatus.SC_OK);
        response.setHeader("Cache-Control", "private");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithNoCacheCacheable() {
        response.addHeader("Cache-Control", "no-cache");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadWithNoCacheCacheable() {
        response.addHeader("Cache-Control", "no-cache");

        Assert.assertFalse(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testIsGetWithNoStoreCacheable() {
        response.addHeader("Cache-Control", "no-store");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadWithNoStoreCacheable() {
        response.addHeader("Cache-Control", "no-store");

        Assert.assertFalse(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testIsGetWithNoStoreEmbeddedInListCacheable() {
        response.addHeader("Cache-Control", "public, no-store");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadWithNoStoreEmbeddedInListCacheable() {
        response.addHeader("Cache-Control", "public, no-store");

        Assert.assertFalse(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testIsGetWithNoCacheEmbeddedInListCacheable() {
        response.addHeader("Cache-Control", "public, no-cache");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadWithNoCacheEmbeddedInListCacheable() {
        response.addHeader("Cache-Control", "public, no-cache");

        Assert.assertFalse(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testIsGetWithNoCacheEmbeddedInListAfterFirstHeaderCacheable() {
        response.addHeader("Cache-Control", "max-age=20");
        response.addHeader("Cache-Control", "public, no-cache");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadWithNoCacheEmbeddedInListAfterFirstHeaderCacheable() {
        response.addHeader("Cache-Control", "max-age=20");
        response.addHeader("Cache-Control", "public, no-cache");

        Assert.assertFalse(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testIsGetWithNoStoreEmbeddedInListAfterFirstHeaderCacheable() {
        response.addHeader("Cache-Control", "max-age=20");
        response.addHeader("Cache-Control", "public, no-store");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadWithNoStoreEmbeddedInListAfterFirstHeaderCacheable() {
        response.addHeader("Cache-Control", "max-age=20");
        response.addHeader("Cache-Control", "public, no-store");

        Assert.assertFalse(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testIsGetWithAnyCacheControlCacheable() {
        response.addHeader("Cache-Control", "max=10");

        Assert.assertTrue(policy.isResponseCacheable("GET", response));

        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader("Date", DateUtils.formatDate(new Date()));
        response.addHeader("Cache-Control", "no-transform");
        response.setHeader("Content-Length", "0");

        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadWithAnyCacheControlCacheable() {
        policy = new ResponseCachingPolicy(0, true, false, false);
        response.addHeader("Cache-Control", "max=10");

        Assert.assertTrue(policy.isResponseCacheable("HEAD", response));

        response = new BasicHttpResponse(HttpStatus.SC_OK, "");
        response.setHeader("Date", DateUtils.formatDate(new Date()));
        response.addHeader("Cache-Control", "no-transform");
        response.setHeader("Content-Length", "0");

        Assert.assertTrue(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testIsGetWithout200Cacheable() {
        HttpResponse response404 = new BasicHttpResponse(HttpStatus.SC_NOT_FOUND, "");

        Assert.assertFalse(policy.isResponseCacheable("GET", response404));

        response404 = new BasicHttpResponse(HttpStatus.SC_GATEWAY_TIMEOUT, "");

        Assert.assertFalse(policy.isResponseCacheable("GET", response404));
    }

    @Test
    public void testIsHeadWithout200Cacheable() {
        HttpResponse response404 = new BasicHttpResponse(HttpStatus.SC_NOT_FOUND, "");

        Assert.assertFalse(policy.isResponseCacheable("HEAD", response404));

        response404 = new BasicHttpResponse(HttpStatus.SC_GATEWAY_TIMEOUT, "");

        Assert.assertFalse(policy.isResponseCacheable("HEAD", response404));
    }

    @Test
    public void testVaryStarIsNotCacheable() {
        response.setHeader("Vary", "*");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testVaryStarIsNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(0, true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setHeader("Cache-Control", "public");
        response.setHeader("Vary", "*");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testIsGetWithVaryHeaderCacheable() {
        response.addHeader("Vary", "Accept-Encoding");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsHeadWithVaryHeaderCacheable() {
        policy = new ResponseCachingPolicy(0, true, false, false);
        response.addHeader("Vary", "Accept-Encoding");
        Assert.assertTrue(policy.isResponseCacheable("HEAD", response));
    }

    @Test
    public void testIsArbitraryMethodCacheable() {

        Assert.assertFalse(policy.isResponseCacheable("PUT", response));

        Assert.assertFalse(policy.isResponseCacheable("get", response));
    }

    @Test
    public void testIsArbitraryMethodCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(0, true, false, false);

        request = new HttpOptions("http://foo.example.com/");
        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setCode(HttpStatus.SC_NO_CONTENT);
        response.setHeader("Cache-Control", "public");

        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesToRequestsWithNoStoreAreNotCacheable() {
        request.setHeader("Cache-Control","no-store");
        response.setHeader("Cache-Control","public");
        Assert.assertFalse(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testResponsesWithMultipleAgeHeadersAreNotCacheable() {
        response.addHeader("Age", "3");
        response.addHeader("Age", "5");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponsesWithMultipleAgeHeadersAreNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(0, true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setHeader("Cache-Control", "public");
        response.addHeader("Age", "3");
        response.addHeader("Age", "5");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesWithMultipleDateHeadersAreNotCacheable() {
        response.addHeader("Date", DateUtils.formatDate(now));
        response.addHeader("Date", DateUtils.formatDate(sixSecondsAgo));
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponsesWithMultipleDateHeadersAreNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(0, true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setHeader("Cache-Control", "public");
        response.addHeader("Date", DateUtils.formatDate(now));
        response.addHeader("Date", DateUtils.formatDate(sixSecondsAgo));
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesWithMalformedDateHeadersAreNotCacheable() {
        response.addHeader("Date", "garbage");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponsesWithMalformedDateHeadersAreNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(0, true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setHeader("Cache-Control", "public");
        response.addHeader("Date", "garbage");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesWithMultipleExpiresHeadersAreNotCacheable() {
        response.addHeader("Expires", DateUtils.formatDate(now));
        response.addHeader("Expires", DateUtils.formatDate(sixSecondsAgo));
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponsesWithMultipleExpiresHeadersAreNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(0, true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setHeader("Cache-Control", "public");
        response.addHeader("Expires", DateUtils.formatDate(now));
        response.addHeader("Expires", DateUtils.formatDate(sixSecondsAgo));
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesWithoutDateHeadersAreNotCacheable() {
        response.removeHeaders("Date");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponseThatHasTooMuchContentIsNotCacheable() {
        response.setHeader("Content-Length", "9000");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponseThatHasTooMuchContentIsNotCacheableUsingSharedPublicCache() {
        policy = new ResponseCachingPolicy(0, true, false, false);

        request.setHeader("Authorization", StandardAuthScheme.BASIC + " QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        response.setHeader("Cache-Control", "public");
        response.setHeader("Content-Length", "9000");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesThatAreSmallEnoughAreCacheable() {
        response.setHeader("Content-Length", "0");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponsesToGETWithQueryParamsButNoExplicitCachingAreNotCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesToHEADWithQueryParamsButNoExplicitCachingAreNotCacheable() {
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesToGETWithQueryParamsButNoExplicitCachingAreNotCacheableEvenWhen1_0QueryCachingDisabled() {
        policy = new ResponseCachingPolicy(0, true, true, false);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesToHEADWithQueryParamsButNoExplicitCachingAreNotCacheableEvenWhen1_0QueryCachingDisabled() {
        policy = new ResponseCachingPolicy(0, true, true, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesToGETWithQueryParamsAndExplicitCachingAreCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesToHEADWithQueryParamsAndExplicitCachingAreCacheable() {
        policy = new ResponseCachingPolicy(0, true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesToGETWithQueryParamsAndExplicitCachingAreCacheableEvenWhen1_0QueryCachingDisabled() {
        policy = new ResponseCachingPolicy(0, true, true, false);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void testResponsesToHEADWithQueryParamsAndExplicitCachingAreCacheableEvenWhen1_0QueryCachingDisabled() {
        policy = new ResponseCachingPolicy(0, true, true, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheable() {
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheableEvenWithSetting() {
        policy = new ResponseCachingPolicy(0, true, true, false);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheableEvenWithSetting() {
        policy = new ResponseCachingPolicy(0, true, true, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersDirectlyFrom1_0OriginsAreCacheableWithExpires() {
        policy = new ResponseCachingPolicy(0, true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsCanBeNotCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(0, true, true, false);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersDirectlyFrom1_0OriginsCanBeNotCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(0, true, true, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaProxiesAreNotCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Via", "1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaProxiesAreNotCacheable() {
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Via", "1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaProxiesAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "1.0 someproxy");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaProxiesAreCacheableWithExpires() {
        policy = new ResponseCachingPolicy(0, true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "1.0 someproxy");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaProxiesCanNotBeCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(0, true, true, true);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaProxiesCanNotBeCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(0, true, true, true);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaExplicitProxiesAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "HTTP/1.0 someproxy");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaExplicitProxiesAreCacheableWithExpires() {
        policy = new ResponseCachingPolicy(0, true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "HTTP/1.0 someproxy");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaExplicitProxiesCanNotBeCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(0, true, true, true);
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "HTTP/1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_0OriginsViaExplicitProxiesCanNotBeCacheableEvenWithExpires() {
        policy = new ResponseCachingPolicy(0, true, true, true);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "HTTP/1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_1OriginsVia1_0ProxiesAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "1.1 someproxy");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void headsWithQueryParametersFrom1_1OriginsVia1_0ProxiesAreCacheableWithExpires() {
        policy = new ResponseCachingPolicy(0, true, false, false);
        request = new BasicHttpRequest("HEAD", "/foo?s=bar");
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(tenSecondsFromNow));
        response.setHeader("Via", "1.1 someproxy");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void notCacheableIfExpiresEqualsDateAndNoCacheControl() {
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(now));
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void notCacheableIfExpiresPrecedesDateAndNoCacheControl() {
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Expires", DateUtils.formatDate(sixSecondsAgo));
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void test302WithExplicitCachingHeaders() {
        response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Cache-Control","max-age=300");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void test303WithExplicitCachingHeadersUnderDefaultBehavior() {
        // RFC 2616 says: 303 should not be cached
        response.setCode(HttpStatus.SC_SEE_OTHER);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Cache-Control","max-age=300");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void test303WithExplicitCachingHeadersWhenPermittedByConfig() {
        // HTTPbis working group says ok if explicitly indicated by
        // response headers
        policy = new ResponseCachingPolicy(0, true, false, true);
        response.setCode(HttpStatus.SC_SEE_OTHER);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Cache-Control","max-age=300");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void test307WithExplicitCachingHeaders() {
        response.setCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Cache-Control","max-age=300");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void otherStatusCodesAreCacheableWithExplicitCachingHeaders() {
        response.setCode(HttpStatus.SC_NOT_FOUND);
        response.setHeader("Date", DateUtils.formatDate(now));
        response.setHeader("Cache-Control","max-age=300");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    private int getRandomStatus() {
        final int rnd = new Random().nextInt(acceptableCodes.length);

        return acceptableCodes[rnd];
    }
}
