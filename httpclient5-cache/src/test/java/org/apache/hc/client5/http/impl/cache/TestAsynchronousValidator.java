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

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.methods.HttpExecutionAware;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.impl.sync.RoutedHttpRequest;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHeaderIterator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestAsynchronousValidator {

    private AsynchronousValidator impl;

    private CachingExec mockClient;
    private HttpHost host;
    private HttpRoute route;
    private RoutedHttpRequest request;
    private HttpClientContext context;
    private HttpExecutionAware mockExecAware;
    private HttpCacheEntry mockCacheEntry;

    private SchedulingStrategy mockSchedulingStrategy;

    @Before
    public void setUp() {
        mockClient = mock(CachingExec.class);
        host = new HttpHost("foo.example.com", 80);
        route = new HttpRoute(host);
        request = RoutedHttpRequest.adapt(new HttpGet("/"), route);
        context = HttpClientContext.create();
        mockExecAware = mock(HttpExecutionAware.class);
        mockCacheEntry = mock(HttpCacheEntry.class);
        mockSchedulingStrategy = mock(SchedulingStrategy.class);
    }

    @Test
    public void testRevalidateCacheEntrySchedulesExecutionAndPopulatesIdentifier() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        when(mockCacheEntry.hasVariants()).thenReturn(false);

        impl.revalidateCacheEntry(mockClient, request, context, mockExecAware, mockCacheEntry);

        verify(mockCacheEntry).hasVariants();
        verify(mockSchedulingStrategy).schedule(isA(AsynchronousValidationRequest.class));

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testMarkCompleteRemovesIdentifier() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        when(mockCacheEntry.hasVariants()).thenReturn(false);

        impl.revalidateCacheEntry(mockClient, request, context, mockExecAware, mockCacheEntry);

        final ArgumentCaptor<AsynchronousValidationRequest> cap = ArgumentCaptor.forClass(AsynchronousValidationRequest.class);
        verify(mockCacheEntry).hasVariants();
        verify(mockSchedulingStrategy).schedule(cap.capture());

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());

        impl.markComplete(cap.getValue().getIdentifier());

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testRevalidateCacheEntryDoesNotPopulateIdentifierOnRejectedExecutionException() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        when(mockCacheEntry.hasVariants()).thenReturn(false);
        doThrow(new RejectedExecutionException()).when(mockSchedulingStrategy).schedule(isA(AsynchronousValidationRequest.class));

        impl.revalidateCacheEntry(mockClient, request, context, mockExecAware, mockCacheEntry);

        verify(mockCacheEntry).hasVariants();

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
        verify(mockSchedulingStrategy).schedule(isA(AsynchronousValidationRequest.class));
    }

    @Test
    public void testRevalidateCacheEntryProperlyCollapsesRequest() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        when(mockCacheEntry.hasVariants()).thenReturn(false);

        impl.revalidateCacheEntry(mockClient, request, context, mockExecAware, mockCacheEntry);
        impl.revalidateCacheEntry(mockClient, request, context, mockExecAware, mockCacheEntry);

        verify(mockCacheEntry, times(2)).hasVariants();
        verify(mockSchedulingStrategy).schedule(isA(AsynchronousValidationRequest.class));

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testVariantsBothRevalidated() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

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
        mockSchedulingStrategy.schedule(isA(AsynchronousValidationRequest.class));

        impl.revalidateCacheEntry(mockClient, RoutedHttpRequest.adapt(req1, route), context, mockExecAware, mockCacheEntry);
        impl.revalidateCacheEntry(mockClient, RoutedHttpRequest.adapt(req2, route), context, mockExecAware, mockCacheEntry);

        verify(mockCacheEntry, times(2)).hasVariants();
        verify(mockCacheEntry, times(2)).headerIterator(HeaderConstants.VARY);
        verify(mockSchedulingStrategy, times(2)).schedule(isA(AsynchronousValidationRequest.class));

        Assert.assertEquals(2, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testRevalidateCacheEntryEndToEnd() throws Exception {
        final CacheConfig config = CacheConfig.custom()
            .setAsynchronousWorkersMax(1)
            .setAsynchronousWorkersCore(1)
            .build();
        final ImmediateSchedulingStrategy schedulingStrategy = new ImmediateSchedulingStrategy(config);
        impl = new AsynchronousValidator(schedulingStrategy);

        when(mockCacheEntry.hasVariants()).thenReturn(false);
        when(mockClient.revalidateCacheEntry(
                request, context, mockExecAware, mockCacheEntry)).thenReturn(null);

        impl.revalidateCacheEntry(mockClient, request, context, mockExecAware, mockCacheEntry);

        try {
            // shut down backend executor and make sure all finishes properly, 1 second should be sufficient
            schedulingStrategy.close();
            schedulingStrategy.awaitTermination(1, TimeUnit.SECONDS);
        } catch (final InterruptedException ie) {

        } finally {
            verify(mockCacheEntry).hasVariants();
            verify(mockClient).revalidateCacheEntry(request, context, mockExecAware, mockCacheEntry);

            Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
        }
    }

    @Test
    public void testSchedulingStrategyShutdownOnClose() throws IOException {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        impl.close();

        verify(mockSchedulingStrategy).close();
    }
}
