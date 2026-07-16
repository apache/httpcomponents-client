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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestAsyncCachingExecChain {

    @Mock
    private AsyncExecChain chain;
    @Mock
    private AsyncExecCallback callback;
    @Mock
    private AsyncExecRuntime execRuntime;
    @Mock
    private CancellableDependency dependency;

    private HttpCacheContext context;
    private AsyncCachingExec impl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        final HttpAsyncCache cache = new BasicHttpAsyncCache(
                HeapResourceFactory.INSTANCE, new SimpleHttpAsyncCacheStorage());
        impl = new AsyncCachingExec(cache, null, CacheConfig.DEFAULT);
        context = HttpCacheContext.create();
    }

    @Test
    void testOnlyIfCachedAndNoCacheEntryBackendNotCalled() throws Exception {
        final HttpHost target = new HttpHost("foo.example.com", 80);
        final HttpRequest request = new BasicHttpRequest(Method.GET, target, "/");
        request.addHeader(HttpHeaders.CACHE_CONTROL, "only-if-cached");
        final AsyncExecChain.Scope scope = new AsyncExecChain.Scope(
                "test",
                new HttpRoute(target),
                request,
                dependency,
                context,
                execRuntime,
                null,
                new AtomicInteger());

        impl.execute(request, null, scope, chain, callback);

        Mockito.verify(chain, Mockito.never()).proceed(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        final ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        Mockito.verify(callback).handleResponse(responseCaptor.capture(), Mockito.any());
        Assertions.assertEquals(HttpStatus.SC_GATEWAY_TIMEOUT, responseCaptor.getValue().getCode());
        Mockito.verify(callback).completed();
        Assertions.assertEquals(CacheResponseStatus.CACHE_MODULE_RESPONSE, context.getCacheResponseStatus());
    }

}
