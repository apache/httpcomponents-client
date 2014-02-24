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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Test;

/**
 * A suite of acceptance tests for compliance with RFC5861, which
 * describes the stale-if-error and stale-while-revalidate
 * Cache-Control extensions.
 */
public class TestRFC5861Compliance extends AbstractProtocolTest {

    /*
     * "The stale-if-error Cache-Control extension indicates that when an
     * error is encountered, a cached stale response MAY be used to satisfy
     * the request, regardless of other freshness information.When used as a
     * request Cache-Control extension, its scope of application is the request
     * it appears in; when used as a response Cache-Control extension, its
     * scope is any request applicable to the cached response in which it
     * occurs.Its value indicates the upper limit to staleness; when the cached
     * response is more stale than the indicated amount, the cached response
     * SHOULD NOT be used to satisfy the request, absent other information.
     * In this context, an error is any situation that would result in a
     * 500, 502, 503, or 504 HTTP response status code being returned."
     *
     * http://tools.ietf.org/html/rfc5861
     */
    @Test
    public void testStaleIfErrorInResponseIsTrueReturnsStaleEntryWithWarning()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testConsumesErrorResponseWhenServingStale()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp2 = HttpTestUtils.make500Response();
        final byte[] body101 = HttpTestUtils.getRandomBytes(101);
        final ByteArrayInputStream buf = new ByteArrayInputStream(body101);
        final ConsumableInputStream cis = new ConsumableInputStream(buf);
        final HttpEntity entity = new InputStreamEntity(cis, 101);
        resp2.setEntity(entity);

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        impl.execute(route, req2, context, null);
        verifyMocks();

        assertTrue(cis.wasClosed());
    }

    @Test
    public void testStaleIfErrorInResponseYieldsToMustRevalidate()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, must-revalidate");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertTrue(HttpStatus.SC_OK != result.getStatusLine().getStatusCode());
    }

    @Test
    public void testStaleIfErrorInResponseYieldsToProxyRevalidateForSharedCache()
            throws Exception{
        assertTrue(config.isSharedCache());
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, proxy-revalidate");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertTrue(HttpStatus.SC_OK != result.getStatusLine().getStatusCode());
    }

    @Test
    public void testStaleIfErrorInResponseNeedNotYieldToProxyRevalidateForPrivateCache()
            throws Exception{
        final CacheConfig configUnshared = CacheConfig.custom()
                .setSharedCache(false).build();
        impl = new CachingExec(mockBackend, new BasicHttpCache(configUnshared), configUnshared);

        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, proxy-revalidate");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testStaleIfErrorInResponseYieldsToExplicitFreshnessRequest()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        req2.setHeader("Cache-Control","min-fresh=2");
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertTrue(HttpStatus.SC_OK != result.getStatusLine().getStatusCode());
    }

    @Test
    public void testStaleIfErrorInRequestIsTrueReturnsStaleEntryWithWarning()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        req2.setHeader("Cache-Control","public, stale-if-error=60");
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testStaleIfErrorInRequestIsTrueReturnsStaleNonRevalidatableEntryWithWarning()
        throws Exception {
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        req2.setHeader("Cache-Control", "public, stale-if-error=60");
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testStaleIfErrorInResponseIsFalseReturnsError()
            throws Exception{
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=2");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                result.getStatusLine().getStatusCode());
    }

    @Test
    public void testStaleIfErrorInRequestIsFalseReturnsError()
            throws Exception{
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        final HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5");

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(HttpTestUtils.makeDefaultRequest());
        req2.setHeader("Cache-Control","stale-if-error=2");
        final HttpResponse resp2 = HttpTestUtils.make500Response();

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                result.getStatusLine().getStatusCode());
    }

    /*
     * When present in an HTTP response, the stale-while-revalidate Cache-
     * Control extension indicates that caches MAY serve the response in
     * which it appears after it becomes stale, up to the indicated number
     * of seconds.
     *
     * http://tools.ietf.org/html/rfc5861
     */
    @Test
    public void testStaleWhileRevalidateReturnsStaleEntryWithWarning()
        throws Exception {
        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setAsynchronousWorkersMax(1)
                .build();

        impl = new CachingExec(mockBackend, cache, config, new AsynchronousValidator(config));

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1).times(1,2);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(final Header h : result.getHeaders("Warning")) {
            for(final WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertTrue(warning110Found);
    }

    @Test
    public void testHTTPCLIENT1470() {
        impl = new CachingExec(mockBackend, cache, null, new AsynchronousValidator(config));
    }

    @Test
    public void testStaleWhileRevalidateReturnsStaleNonRevalidatableEntryWithWarning()
        throws Exception {
        config = CacheConfig.custom().setMaxCacheEntries(MAX_ENTRIES).setMaxObjectSize(MAX_BYTES)
            .setAsynchronousWorkersMax(1).build();

        impl = new CachingExec(mockBackend, cache, config, new AsynchronousValidator(config));

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "/",
            HttpVersion.HTTP_1_1));
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1).times(1, 2);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "/",
            HttpVersion.HTTP_1_1));

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for (final Header h : result.getHeaders("Warning")) {
            for (final WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertTrue(warning110Found);
    }

    @Test
    public void testCanAlsoServeStale304sWhileRevalidating()
        throws Exception {

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setAsynchronousWorkersMax(1)
                .setSharedCache(false)
                .build();
        impl = new CachingExec(mockBackend, cache, config, new AsynchronousValidator(config));

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Cache-Control", "private, stale-while-revalidate=15");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1).times(1,2);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        req2.setHeader("If-None-Match","\"etag\"");

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(final Header h : result.getHeaders("Warning")) {
            for(final WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertTrue(warning110Found);
    }


    @Test
    public void testStaleWhileRevalidateYieldsToMustRevalidate()
        throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setAsynchronousWorkersMax(1)
                .build();
        impl = new CachingExec(mockBackend, cache, config);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, must-revalidate");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, must-revalidate");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(final Header h : result.getHeaders("Warning")) {
            for(final WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertFalse(warning110Found);
    }

    @Test
    public void testStaleWhileRevalidateYieldsToProxyRevalidateForSharedCache()
        throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setAsynchronousWorkersMax(1)
                .setSharedCache(true)
                .build();
        impl = new CachingExec(mockBackend, cache, config);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, proxy-revalidate");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, proxy-revalidate");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(final Header h : result.getHeaders("Warning")) {
            for(final WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertFalse(warning110Found);
    }

    @Test
    public void testStaleWhileRevalidateYieldsToExplicitFreshnessRequest()
        throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setAsynchronousWorkersMax(1)
                .setSharedCache(true)
                .build();
        impl = new CachingExec(mockBackend, cache, config);

        final HttpRequestWrapper req1 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequestAndReturn(resp1);

        final HttpRequestWrapper req2 = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        req2.setHeader("Cache-Control","min-fresh=2");
        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));

        backendExpectsAnyRequestAndReturn(resp2);

        replayMocks();
        impl.execute(route, req1, context, null);
        final HttpResponse result = impl.execute(route, req2, context, null);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(final Header h : result.getHeaders("Warning")) {
            for(final WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertFalse(warning110Found);
    }

}
