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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.impl.schedule.ImmediateSchedulingStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * A suite of acceptance tests for compliance with RFC5861, which
 * describes the stale-if-error and stale-while-revalidate
 * Cache-Control extensions.
 */
@RunWith(MockitoJUnitRunner.class)
public class TestRFC5861Compliance {

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
    ScheduledExecutorService executorService;

    @Before
    public void setUp() throws Exception {
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

        executorService = new ScheduledThreadPoolExecutor(1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);
        Mockito.when(mockExecRuntime.fork(null)).thenReturn(mockExecRuntime);
    }

    @After
    public void cleanup() {
        executorService.shutdownNow();
    }

    public ClassicHttpResponse execute(final ClassicHttpRequest request) throws IOException, HttpException {
        return impl.execute(
                ClassicRequestBuilder.copy(request).build(),
                new ExecChain.Scope("test", route, request, mockExecRuntime, context),
                mockExecChain);
    }

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
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testConsumesErrorResponseWhenServingStale()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();
        final byte[] body101 = HttpTestUtils.getRandomBytes(101);
        final ByteArrayInputStream buf = new ByteArrayInputStream(body101);
        final ConsumableInputStream cis = new ConsumableInputStream(buf);
        final HttpEntity entity = new InputStreamEntity(cis, 101, null);
        resp2.setEntity(entity);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        execute(req2);

        assertTrue(cis.wasClosed());
    }

    @Test
    public void testStaleIfErrorInResponseYieldsToMustRevalidate()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, must-revalidate");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertTrue(HttpStatus.SC_OK != result.getCode());
    }

    @Test
    public void testStaleIfErrorInResponseYieldsToProxyRevalidateForSharedCache()
            throws Exception{
        assertTrue(config.isSharedCache());
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, proxy-revalidate");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertTrue(HttpStatus.SC_OK != result.getCode());
    }

    @Test
    public void testStaleIfErrorInResponseNeedNotYieldToProxyRevalidateForPrivateCache()
            throws Exception{
        final CacheConfig configUnshared = CacheConfig.custom()
                .setSharedCache(false).build();
        impl = new CachingExec(new BasicHttpCache(configUnshared), null, configUnshared);

        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, proxy-revalidate");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testStaleIfErrorInResponseYieldsToExplicitFreshnessRequest()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control","min-fresh=2");
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertTrue(HttpStatus.SC_OK != result.getCode());
    }

    @Test
    public void testStaleIfErrorInRequestIsTrueReturnsStaleEntryWithWarning()
            throws Exception{
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control","public, stale-if-error=60");
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testStaleIfErrorInRequestIsTrueReturnsStaleNonRevalidatableEntryWithWarning()
        throws Exception {
        final Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control", "public, max-age=5");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control", "public, stale-if-error=60");
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testStaleIfErrorInResponseIsFalseReturnsError()
            throws Exception{
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=2");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                result.getCode());
    }

    @Test
    public void testStaleIfErrorInRequestIsFalseReturnsError()
            throws Exception{
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control","stale-if-error=2");
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, result.getCode());
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
                .setAsynchronousWorkers(1)
                .build();

        impl = new CachingExec(cache, executorService, ImmediateSchedulingStrategy.INSTANCE, config);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_OK, result.getCode());
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

        Mockito.verify(mockExecChain, Mockito.atLeastOnce()).proceed(Mockito.any(), Mockito.any());
        Mockito.verify(mockExecChain, Mockito.atMost(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testStaleWhileRevalidateReturnsStaleNonRevalidatableEntryWithWarning()
        throws Exception {
        config = CacheConfig.custom().setMaxCacheEntries(MAX_ENTRIES).setMaxObjectSize(MAX_BYTES)
            .setAsynchronousWorkers(1).build();

        impl = new CachingExec(cache, executorService, ImmediateSchedulingStrategy.INSTANCE, config);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_OK, result.getCode());
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

        Mockito.verify(mockExecChain, Mockito.atLeastOnce()).proceed(Mockito.any(), Mockito.any());
        Mockito.verify(mockExecChain, Mockito.atMost(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testCanAlsoServeStale304sWhileRevalidating()
        throws Exception {

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setAsynchronousWorkers(1)
                .setSharedCache(false)
                .build();
        impl = new CachingExec(cache, executorService, ImmediateSchedulingStrategy.INSTANCE, config);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Cache-Control", "private, stale-while-revalidate=15");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("If-None-Match","\"etag\"");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);
        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_NOT_MODIFIED, result.getCode());
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

        Mockito.verify(mockExecChain, Mockito.atLeastOnce()).proceed(Mockito.any(), Mockito.any());
        Mockito.verify(mockExecChain, Mockito.atMost(2)).proceed(Mockito.any(), Mockito.any());
    }

    @Test
    public void testStaleWhileRevalidateYieldsToMustRevalidate()
        throws Exception {

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setAsynchronousWorkers(1)
                .build();
        impl = new CachingExec(cache, null, config);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, must-revalidate");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, must-revalidate");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_OK, result.getCode());
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
                .setAsynchronousWorkers(1)
                .setSharedCache(true)
                .build();
        impl = new CachingExec(cache, null, config);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, proxy-revalidate");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, proxy-revalidate");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_OK, result.getCode());
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
                .setAsynchronousWorkers(1)
                .setSharedCache(true)
                .build();
        impl = new CachingExec(cache, null, config);

        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET", "/");
        req2.setHeader("Cache-Control","min-fresh=2");
        final ClassicHttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp1);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(resp2);

        final ClassicHttpResponse result = execute(req2);

        assertEquals(HttpStatus.SC_OK, result.getCode());
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
