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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHeaderIterator;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestAsynchronousValidator {

    @Mock
    private CachingExec mockClient;
    @Mock
    private ExecChain mockExecChain;
    @Mock
    private ExecRuntime mockEndpoint;
    @Mock
    private HttpCacheEntry mockCacheEntry;
    @Mock
    private SchedulingStrategy mockSchedulingStrategy;
    @Mock
    ScheduledExecutorService mockExecutorService;

    private HttpHost host;
    private HttpRoute route;
    private ClassicHttpRequest request;
    private HttpClientContext context;
    private ExecChain.Scope scope;
    private AsynchronousValidator impl;


    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com", 80);
        route = new HttpRoute(host);
        request = new HttpGet("/");
        context = HttpClientContext.create();
        scope = new ExecChain.Scope("test", route, request, mockEndpoint, context);
        impl = new AsynchronousValidator(mockExecutorService, mockSchedulingStrategy);
    }

    @Test
    public void testRevalidateCacheEntrySchedulesExecutionAndPopulatesIdentifier() {
        when(mockCacheEntry.hasVariants()).thenReturn(false);
        when(mockSchedulingStrategy.schedule(Mockito.anyInt())).thenReturn(TimeValue.ofSeconds(1));

        impl.revalidateCacheEntry(mockClient, host, request, scope, mockExecChain, mockCacheEntry);

        verify(mockCacheEntry).hasVariants();
        verify(mockSchedulingStrategy).schedule(0);
        verify(mockExecutorService).schedule(Mockito.<Runnable>any(), Mockito.eq(1L), Mockito.eq(TimeUnit.SECONDS));

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testMarkCompleteRemovesIdentifier() {
        when(mockCacheEntry.hasVariants()).thenReturn(false);
        when(mockSchedulingStrategy.schedule(Mockito.anyInt())).thenReturn(TimeValue.ofSeconds(3));

        impl.revalidateCacheEntry(mockClient, host, request, scope, mockExecChain, mockCacheEntry);

        verify(mockCacheEntry).hasVariants();
        verify(mockSchedulingStrategy).schedule(0);
        verify(mockExecutorService).schedule(Mockito.<Runnable>any(), Mockito.eq(3L), Mockito.eq(TimeUnit.SECONDS));

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
        final String cacheKey = CacheKeyGenerator.INSTANCE.generateVariantURI(host, request, mockCacheEntry);
        Assert.assertTrue(impl.getScheduledIdentifiers().contains(cacheKey));

        impl.markComplete(cacheKey);

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testRevalidateCacheEntryDoesNotPopulateIdentifierOnRejectedExecutionException() {
        when(mockCacheEntry.hasVariants()).thenReturn(false);
        when(mockSchedulingStrategy.schedule(Mockito.anyInt())).thenReturn(TimeValue.ofSeconds(2));
        doThrow(new RejectedExecutionException()).when(mockExecutorService).schedule(Mockito.<Runnable>any(), Mockito.anyLong(), Mockito.<TimeUnit>any());

        impl.revalidateCacheEntry(mockClient, host, request, scope, mockExecChain, mockCacheEntry);

        verify(mockCacheEntry).hasVariants();

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
        verify(mockExecutorService).schedule(Mockito.<Runnable>any(), Mockito.eq(2L), Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    public void testRevalidateCacheEntryProperlyCollapsesRequest() {
        when(mockCacheEntry.hasVariants()).thenReturn(false);
        when(mockSchedulingStrategy.schedule(Mockito.anyInt())).thenReturn(TimeValue.ofSeconds(2));

        impl.revalidateCacheEntry(mockClient, host, request, scope, mockExecChain, mockCacheEntry);
        impl.revalidateCacheEntry(mockClient, host, request, scope, mockExecChain, mockCacheEntry);

        verify(mockCacheEntry, Mockito.times(2)).hasVariants();
        verify(mockSchedulingStrategy).schedule(Mockito.anyInt());
        verify(mockExecutorService).schedule(Mockito.<Runnable>any(), Mockito.eq(2L), Mockito.eq(TimeUnit.SECONDS));

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testVariantsBothRevalidated() {
        final ClassicHttpRequest req1 = new HttpGet("/");
        req1.addHeader(new BasicHeader("Accept-Encoding", "identity"));

        final ClassicHttpRequest req2 = new HttpGet("/");
        req2.addHeader(new BasicHeader("Accept-Encoding", "gzip"));

        final Header[] variantHeaders = new Header[] {
                new BasicHeader(HeaderConstants.VARY, "Accept-Encoding")
        };

        when(mockCacheEntry.hasVariants()).thenReturn(true);
        when(mockCacheEntry.headerIterator(HeaderConstants.VARY)).thenReturn(
                new BasicHeaderIterator(variantHeaders, HeaderConstants.VARY));
        when(mockSchedulingStrategy.schedule(Mockito.anyInt())).thenReturn(TimeValue.ofSeconds(2));

        impl.revalidateCacheEntry(mockClient, host, req1, new ExecChain.Scope("test", route, req1, mockEndpoint, context),
                mockExecChain, mockCacheEntry);
        impl.revalidateCacheEntry(mockClient, host, req2, new ExecChain.Scope("test", route, req2, mockEndpoint, context),
                mockExecChain, mockCacheEntry);

        verify(mockCacheEntry, Mockito.times(2)).hasVariants();
        verify(mockCacheEntry, Mockito.times(2)).headerIterator(HeaderConstants.VARY);
        verify(mockSchedulingStrategy, Mockito.times(2)).schedule(Mockito.anyInt());
        verify(mockExecutorService, Mockito.times(2)).schedule(Mockito.<Runnable>any(), Mockito.eq(2L), Mockito.eq(TimeUnit.SECONDS));

        Assert.assertEquals(2, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testShutdown() throws Exception {
        impl.close();
        impl.awaitTermination(Timeout.ofMinutes(2));

        verify(mockExecutorService).shutdown();
        verify(mockExecutorService).awaitTermination(2L, TimeUnit.MINUTES);
    }

}
