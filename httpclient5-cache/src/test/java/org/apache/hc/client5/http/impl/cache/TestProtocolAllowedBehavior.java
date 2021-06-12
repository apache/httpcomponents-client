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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Date;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * This class tests behavior that is allowed (MAY) by the HTTP/1.1 protocol
 * specification and for which we have implemented the behavior in HTTP cache.
 */
@RunWith(MockitoJUnitRunner.class)
public class TestProtocolAllowedBehavior {

    static final int MAX_BYTES = 1024;
    static final int MAX_ENTRIES = 100;
    static final int ENTITY_LENGTH = 128;

    HttpHost host;
    HttpRoute route;
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

    @Before
    public void setUp() throws Exception {
        host = new HttpHost("foo.example.com", 80);

        route = new HttpRoute(host);

        request = new BasicClassicHttpRequest("GET", "/foo");

        context = HttpClientContext.create();

        originResponse = HttpTestUtils.make200Response();

        config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .setSharedCache(false)
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
    public void testNonSharedCacheReturnsStaleResponseWhenRevalidationFailsForProxyRevalidate() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET","/");
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        originResponse.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        originResponse.setHeader("Cache-Control","max-age=5,proxy-revalidate");
        originResponse.setHeader("Etag","\"etag\"");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET","/");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req1);

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenThrow(new SocketTimeoutException());

        final HttpResponse result = execute(req2);
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());

        Mockito.verifyNoInteractions(mockCache);
    }

    @Test
    public void testNonSharedCacheMayCacheResponsesWithCacheControlPrivate() throws Exception {
        final ClassicHttpRequest req1 = new BasicClassicHttpRequest("GET","/");
        originResponse.setHeader("Cache-Control","private,max-age=3600");

        final ClassicHttpRequest req2 = new BasicClassicHttpRequest("GET","/");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        execute(req1);
        final HttpResponse result = execute(req2);
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());

        Mockito.verifyNoInteractions(mockCache);
    }
}
