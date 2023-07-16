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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * A suite of acceptance tests for compliance with RFC5861, which
 * describes the stale-if-error and stale-while-revalidate
 * Cache-Control extensions.
 */
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
    ClassicHttpRequest request;
    ClassicHttpResponse originResponse;
    CacheConfig config;
    CachingExec impl;
    HttpCache cache;
    ScheduledExecutorService executorService;

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

        executorService = new ScheduledThreadPoolExecutor(1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);
        Mockito.when(mockExecRuntime.fork(null)).thenReturn(mockExecRuntime);
    }

    @AfterEach
    public void cleanup() {
        executorService.shutdownNow();
    }

    public ClassicHttpResponse execute(final ClassicHttpRequest request) throws IOException, HttpException {
        return impl.execute(
                ClassicRequestBuilder.copy(request).build(),
                new ExecChain.Scope("test", route, request, mockExecRuntime, context),
                mockExecChain);
    }

    @Test
    public void testConsumesErrorResponseWhenServingStale()
            throws Exception{
        final Instant tenSecondsAgo = Instant.now().minusSeconds(10);
        final ClassicHttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");

        final ClassicHttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        final ClassicHttpResponse resp2 = HttpTestUtils.make500Response();
        final byte[] body101 = HttpTestUtils.makeRandomBytes(101);
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
        final Instant tenSecondsAgo = Instant.now().minusSeconds(10);
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
        final Instant tenSecondsAgo = Instant.now().minusSeconds(10);
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
    public void testStaleIfErrorInResponseYieldsToExplicitFreshnessRequest()
            throws Exception{
        final Instant tenSecondsAgo = Instant.now().minusSeconds(10);
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
    public void testStaleIfErrorInResponseIsFalseReturnsError()
            throws Exception{
        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
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
        final Instant now = Instant.now();
        final Instant tenSecondsAgo = now.minusSeconds(10);
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

}
