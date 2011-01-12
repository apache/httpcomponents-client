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

import java.util.Date;
import java.util.Random;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import static org.apache.http.impl.cookie.DateUtils.formatDate;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestResponseCachingPolicy {

    private static final ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP", 1, 1);
    private ResponseCachingPolicy policy;
    private HttpResponse response;
    private HttpRequest request;
    private int[] acceptableCodes = new int[] { HttpStatus.SC_OK,
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
        
        policy = new ResponseCachingPolicy(0, true);
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        response = new BasicHttpResponse(
                new BasicStatusLine(HTTP_1_1, HttpStatus.SC_OK, ""));
        response.setHeader("Date", formatDate(new Date()));
        response.setHeader("Content-Length", "0");
    }

    @Test
    public void testIsGetCacheable() {
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponsesToRequestsWithAuthorizationHeadersAreNotCacheableBySharedCache() {
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Authorization","Basic dXNlcjpwYXNzd2Q=");
        Assert.assertFalse(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testResponsesToRequestsWithAuthorizationHeadersAreCacheableByNonSharedCache() {
        policy = new ResponseCachingPolicy(0, false);
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Authorization","Basic dXNlcjpwYXNzd2Q=");
        Assert.assertTrue(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testAuthorizedResponsesWithSMaxAgeAreCacheable() {
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Authorization","Basic dXNlcjpwYXNzd2Q=");
        response.setHeader("Cache-Control","s-maxage=3600");
        Assert.assertTrue(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testAuthorizedResponsesWithMustRevalidateAreCacheable() {
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Authorization","Basic dXNlcjpwYXNzd2Q=");
        response.setHeader("Cache-Control","must-revalidate");
        Assert.assertTrue(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testAuthorizedResponsesWithCacheControlPublicAreCacheable() {
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Authorization","Basic dXNlcjpwYXNzd2Q=");
        response.setHeader("Cache-Control","public");
        Assert.assertTrue(policy.isResponseCacheable(request,response));
    }

    @Test
    public void testAuthorizedResponsesWithCacheControlMaxAgeAreNotCacheable() {
        request = new BasicHttpRequest("GET","/",HTTP_1_1);
        request.setHeader("Authorization","Basic dXNlcjpwYXNzd2Q=");
        response.setHeader("Cache-Control","max-age=3600");
        Assert.assertFalse(policy.isResponseCacheable(request,response));
    }

    @Test
    public void test203ResponseCodeIsCacheable() {
        response.setStatusCode(HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION);
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test206ResponseCodeIsNotCacheable() {
        response.setStatusCode(HttpStatus.SC_PARTIAL_CONTENT);
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test300ResponseCodeIsCacheable() {
        response.setStatusCode(HttpStatus.SC_MULTIPLE_CHOICES);
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test301ResponseCodeIsCacheable() {
        response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test410ResponseCodeIsCacheable() {
        response.setStatusCode(HttpStatus.SC_GONE);
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testPlain302ResponseCodeIsNotCacheable() {
        response.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
        response.removeHeaders("Expires");
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testPlain307ResponseCodeIsNotCacheable() {
        response.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        response.removeHeaders("Expires");
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithExplicitExpiresIsCacheable() {
        int status = getRandomStatus();
        response.setStatusCode(status);
        response.setHeader("Expires", formatDate(new Date()));
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithMaxAgeIsCacheable() {
        int status = getRandomStatus();
        response.setStatusCode(status);
        response.setHeader("Cache-Control", "max-age=0");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithSMaxAgeIsCacheable() {
        int status = getRandomStatus();
        response.setStatusCode(status);
        response.setHeader("Cache-Control", "s-maxage=0");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithMustRevalidateIsCacheable() {
        int status = getRandomStatus();
        response.setStatusCode(status);
        response.setHeader("Cache-Control", "must-revalidate");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithProxyRevalidateIsCacheable() {
        int status = getRandomStatus();
        response.setStatusCode(status);
        response.setHeader("Cache-Control", "proxy-revalidate");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithPublicCacheControlIsCacheable() {
        int status = getRandomStatus();
        response.setStatusCode(status);
        response.setHeader("Cache-Control", "public");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testNon206WithPrivateCacheControlIsNotCacheableBySharedCache() {
        int status = getRandomStatus();
        response.setStatusCode(status);
        response.setHeader("Cache-Control", "private");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void test200ResponseWithPrivateCacheControlIsCacheableByNonSharedCache() {
        policy = new ResponseCachingPolicy(0, false);
        response.setStatusCode(HttpStatus.SC_OK);
        response.setHeader("Cache-Control", "private");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithNoCacheCacheable() {
        response.addHeader("Cache-Control", "no-cache");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithNoStoreCacheable() {
        response.addHeader("Cache-Control", "no-store");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithNoStoreEmbeddedInListCacheable() {
        response.addHeader("Cache-Control", "public, no-store");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithNoCacheEmbeddedInListCacheable() {
        response.addHeader("Cache-Control", "public, no-cache");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithNoCacheEmbeddedInListAfterFirstHeaderCacheable() {
        response.addHeader("Cache-Control", "max-age=20");
        response.addHeader("Cache-Control", "public, no-cache");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithNoStoreEmbeddedInListAfterFirstHeaderCacheable() {
        response.addHeader("Cache-Control", "max-age=20");
        response.addHeader("Cache-Control", "public, no-store");

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithAnyCacheControlCacheable() {
        response.addHeader("Cache-Control", "max=10");

        Assert.assertTrue(policy.isResponseCacheable("GET", response));

        response = new BasicHttpResponse(
                new BasicStatusLine(HTTP_1_1, HttpStatus.SC_OK, ""));
        response.setHeader("Date", formatDate(new Date()));
        response.addHeader("Cache-Control", "no-transform");
        response.setHeader("Content-Length", "0");

        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithout200Cacheable() {
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(HTTP_1_1,
                HttpStatus.SC_NOT_FOUND, ""));

        Assert.assertFalse(policy.isResponseCacheable("GET", response));

        response = new BasicHttpResponse(new BasicStatusLine(HTTP_1_1,
                HttpStatus.SC_GATEWAY_TIMEOUT, ""));

        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testVaryStarIsNotCacheable() {
        response.setHeader("Vary", "*");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsGetWithVaryHeaderCacheable() {
        response.addHeader("Vary", "Accept-Encoding");
        Assert.assertTrue(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testIsArbitraryMethodCacheable() {

        Assert.assertFalse(policy.isResponseCacheable("PUT", response));

        Assert.assertFalse(policy.isResponseCacheable("get", response));
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
    public void testResponsesWithMultipleDateHeadersAreNotCacheable() {
        response.addHeader("Date", formatDate(now));
        response.addHeader("Date", formatDate(sixSecondsAgo));
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponsesWithMalformedDateHeadersAreNotCacheable() {
        response.addHeader("Date", "garbage");
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
    }

    @Test
    public void testResponsesWithMultipleExpiresHeadersAreNotCacheable() {
        Date now = new Date();
        Date sixSecondsAgo = new Date(now.getTime() - 6 * 1000L);
        response.addHeader("Expires", formatDate(now));
        response.addHeader("Expires", formatDate(sixSecondsAgo));
        Assert.assertFalse(policy.isResponseCacheable("GET", response));
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
    public void testResponsesToGETWithQueryParamsAndExplicitCachingAreCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", formatDate(now));
        response.setHeader("Expires", formatDate(tenSecondsFromNow));
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_OK, "OK");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }
    
    @Test
    public void getsWithQueryParametersDirectlyFrom1_0OriginsAreNotCacheableEvenWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_OK, "OK");
        response.setHeader("Date", formatDate(now));
        response.setHeader("Expires", formatDate(tenSecondsFromNow));
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }
    
    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaProxiesAreNotCacheable() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Via", "1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaProxiesAreNotCacheableEvenWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        Date now = new Date();
        Date tenSecondsFromNow = new Date(now.getTime() + 10 * 1000L);
        response.setHeader("Date", formatDate(now));
        response.setHeader("Expires", formatDate(tenSecondsFromNow));
        response.setHeader("Via", "1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }
    
    @Test
    public void getsWithQueryParametersFrom1_0OriginsViaExplicitProxiesAreNotCacheableEvenWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response.setHeader("Date", formatDate(now));
        response.setHeader("Expires", formatDate(tenSecondsFromNow));
        response.setHeader("Via", "HTTP/1.0 someproxy");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void getsWithQueryParametersFrom1_1OriginsVia1_0ProxiesAreCacheableWithExpires() {
        request = new BasicHttpRequest("GET", "/foo?s=bar");
        response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_OK, "OK");
        response.setHeader("Date", formatDate(now));
        response.setHeader("Expires", formatDate(tenSecondsFromNow));
        response.setHeader("Via", "1.1 someproxy");
        Assert.assertTrue(policy.isResponseCacheable(request, response));
    }
    
    @Test
    public void notCacheableIfExpiresEqualsDateAndNoCacheControl() {
        response.setHeader("Date", formatDate(now));
        response.setHeader("Expires", formatDate(now));
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    @Test
    public void notCacheableIfExpiresPrecedesDateAndNoCacheControl() {
        response.setHeader("Date", formatDate(now));
        response.setHeader("Expires", formatDate(sixSecondsAgo));
        response.removeHeaders("Cache-Control");
        Assert.assertFalse(policy.isResponseCacheable(request, response));
    }

    private int getRandomStatus() {
        int rnd = (new Random()).nextInt(acceptableCodes.length);

        return acceptableCodes[rnd];
    }
}
