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

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.message.BasicHeader;
import org.easymock.Capture;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestAsynchronousValidator {

    private AsynchronousValidator impl;

    private CachingExec mockClient;
    private HttpRoute route;
    private HttpRequestWrapper request;
    private HttpClientContext context;
    private HttpExecutionAware mockExecAware;
    private HttpCacheEntry mockCacheEntry;

    private SchedulingStrategy mockSchedulingStrategy;

    @Before
    public void setUp() {
        mockClient = EasyMock.createNiceMock(CachingExec.class);
        route = new HttpRoute(new HttpHost("foo.example.com"));
        request = HttpRequestWrapper.wrap(new HttpGet("/"));
        context = HttpClientContext.create();
        mockExecAware = EasyMock.createNiceMock(HttpExecutionAware.class);
        mockCacheEntry = EasyMock.createNiceMock(HttpCacheEntry.class);
        mockSchedulingStrategy = EasyMock.createNiceMock(SchedulingStrategy.class);
    }

    @Test
    public void testRevalidateCacheEntrySchedulesExecutionAndPopulatesIdentifier() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        mockSchedulingStrategy.schedule(EasyMock.isA(AsynchronousValidationRequest.class));

        replayMocks();
        impl.revalidateCacheEntry(mockClient, route, request, context, mockExecAware, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testMarkCompleteRemovesIdentifier() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        final Capture<AsynchronousValidationRequest> cap = new Capture<AsynchronousValidationRequest>();
        mockSchedulingStrategy.schedule(EasyMock.capture(cap));

        replayMocks();
        impl.revalidateCacheEntry(mockClient, route, request, context, mockExecAware, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());

        impl.markComplete(cap.getValue().getIdentifier());

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testRevalidateCacheEntryDoesNotPopulateIdentifierOnRejectedExecutionException() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        mockSchedulingStrategy.schedule(EasyMock.isA(AsynchronousValidationRequest.class));
        EasyMock.expectLastCall().andThrow(new RejectedExecutionException());

        replayMocks();
        impl.revalidateCacheEntry(mockClient, route, request, context, mockExecAware, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testRevalidateCacheEntryProperlyCollapsesRequest() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        mockSchedulingStrategy.schedule(EasyMock.isA(AsynchronousValidationRequest.class));

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);

        replayMocks();
        impl.revalidateCacheEntry(mockClient, route, request, context, mockExecAware, mockCacheEntry);
        impl.revalidateCacheEntry(mockClient, route, request, context, mockExecAware, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testVariantsBothRevalidated() {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        final HttpRequest req1 = new HttpGet("/");
        req1.addHeader(new BasicHeader("Accept-Encoding", "identity"));

        final HttpRequest req2 = new HttpGet("/");
        req2.addHeader(new BasicHeader("Accept-Encoding", "gzip"));

        final Header[] variantHeaders = new Header[] {
                new BasicHeader(HeaderConstants.VARY, "Accept-Encoding")
        };

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(true).times(2);
        EasyMock.expect(mockCacheEntry.getHeaders(HeaderConstants.VARY)).andReturn(variantHeaders).times(2);
        mockSchedulingStrategy.schedule(EasyMock.isA(AsynchronousValidationRequest.class));
        EasyMock.expectLastCall().times(2);

        replayMocks();
        impl.revalidateCacheEntry(mockClient, route, HttpRequestWrapper.wrap(req1), context, mockExecAware, mockCacheEntry);
        impl.revalidateCacheEntry(mockClient, route, HttpRequestWrapper.wrap(req2), context, mockExecAware, mockCacheEntry);
        verifyMocks();

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

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        EasyMock.expect(mockClient.revalidateCacheEntry(
                route, request, context, mockExecAware, mockCacheEntry)).andReturn(null);

        replayMocks();
        impl.revalidateCacheEntry(mockClient, route, request, context, mockExecAware, mockCacheEntry);

        try {
            // shut down backend executor and make sure all finishes properly, 1 second should be sufficient
            schedulingStrategy.close();
            schedulingStrategy.awaitTermination(1, TimeUnit.SECONDS);
        } catch (final InterruptedException ie) {

        } finally {
            verifyMocks();

            Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
        }
    }

    @Test
    public void testSchedulingStrategyShutdownOnClose() throws IOException {
        impl = new AsynchronousValidator(mockSchedulingStrategy);

        mockSchedulingStrategy.close();

        replayMocks();
        impl.close();
        verifyMocks();
    }

    public void replayMocks() {
        EasyMock.replay(mockSchedulingStrategy);
        EasyMock.replay(mockClient);
        EasyMock.replay(mockExecAware);
        EasyMock.replay(mockCacheEntry);
    }

    public void verifyMocks() {
        EasyMock.verify(mockSchedulingStrategy);
        EasyMock.verify(mockClient);
        EasyMock.verify(mockExecAware);
        EasyMock.verify(mockCacheEntry);
    }
}
