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

import junit.framework.Assert;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.easymock.Capture;
import org.easymock.classextension.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class TestAsynchronousValidator {

    private AsynchronousValidator impl;

    private CachingHttpClient mockClient;
    private HttpHost target;
    private HttpRequest request;
    private HttpContext mockContext;
    private HttpCacheEntry mockCacheEntry;
    private HttpResponse mockResponse;
    private StatusLine mockStatusLine;

    private SchedulingStrategy mockSchedulingStrategy;

    @Before
    public void setUp() {
        mockClient = EasyMock.createNiceMock(CachingHttpClient.class);
        target = new HttpHost("foo.example.com");
        request = new HttpGet("/");
        mockContext = EasyMock.createNiceMock(HttpContext.class);
        mockCacheEntry = EasyMock.createNiceMock(HttpCacheEntry.class);
        mockResponse = EasyMock.createNiceMock(HttpResponse.class);
        mockStatusLine = EasyMock.createNiceMock(StatusLine.class);

        mockSchedulingStrategy = EasyMock.createNiceMock(SchedulingStrategy.class);

    }

    @Test
    public void testRevalidateCacheEntrySchedulesExecutionAndPopulatesIdentifier() {
        impl = new AsynchronousValidator(mockClient, mockSchedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        mockSchedulingStrategy.schedule(EasyMock.isA(AsynchronousValidationRequest.class));

        replayMocks();
        impl.revalidateCacheEntry(target, request, mockContext, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testMarkCompleteRemovesIdentifier() {
        impl = new AsynchronousValidator(mockClient, mockSchedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        Capture<AsynchronousValidationRequest> cap = new Capture<AsynchronousValidationRequest>();
        mockSchedulingStrategy.schedule(EasyMock.capture(cap));

        replayMocks();
        impl.revalidateCacheEntry(target, request, mockContext, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());

        impl.markComplete(cap.getValue().getIdentifier());

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testRevalidateCacheEntryDoesNotPopulateIdentifierOnRejectedExecutionException() {
        impl = new AsynchronousValidator(mockClient, mockSchedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        mockSchedulingStrategy.schedule(EasyMock.isA(AsynchronousValidationRequest.class));
        EasyMock.expectLastCall().andThrow(new RejectedExecutionException());

        replayMocks();
        impl.revalidateCacheEntry(target, request, mockContext, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testRevalidateCacheEntryProperlyCollapsesRequest() {
        impl = new AsynchronousValidator(mockClient, mockSchedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        mockSchedulingStrategy.schedule(EasyMock.isA(AsynchronousValidationRequest.class));

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);

        replayMocks();
        impl.revalidateCacheEntry(target, request, mockContext, mockCacheEntry);
        impl.revalidateCacheEntry(target, request, mockContext, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testVariantsBothRevalidated() {
        impl = new AsynchronousValidator(mockClient, mockSchedulingStrategy);

        HttpRequest req1 = new HttpGet("/");
        req1.addHeader(new BasicHeader("Accept-Encoding", "identity"));

        HttpRequest req2 = new HttpGet("/");
        req2.addHeader(new BasicHeader("Accept-Encoding", "gzip"));

        Header[] variantHeaders = new Header[] {
                new BasicHeader(HeaderConstants.VARY, "Accept-Encoding")
        };

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(true).times(2);
        EasyMock.expect(mockCacheEntry.getHeaders(HeaderConstants.VARY)).andReturn(variantHeaders).times(2);
        mockSchedulingStrategy.schedule(EasyMock.isA(AsynchronousValidationRequest.class));
        EasyMock.expectLastCall().times(2);

        replayMocks();
        impl.revalidateCacheEntry(target, req1, mockContext, mockCacheEntry);
        impl.revalidateCacheEntry(target, req2, mockContext, mockCacheEntry);
        verifyMocks();

        Assert.assertEquals(2, impl.getScheduledIdentifiers().size());

    }

    @Test
    public void testRevalidateCacheEntryEndToEnd() throws ProtocolException, IOException {
        CacheConfig config = new CacheConfig();
        config.setAsynchronousWorkersMax(1);
        config.setAsynchronousWorkersCore(1);
        ImmediateSchedulingStrategy schedulingStrategy = new ImmediateSchedulingStrategy(config);
        impl = new AsynchronousValidator(mockClient, schedulingStrategy);

        EasyMock.expect(mockCacheEntry.hasVariants()).andReturn(false);
        EasyMock.expect(mockClient.revalidateCacheEntry(target, request, mockContext, mockCacheEntry)).andReturn(mockResponse);
        EasyMock.expect(mockResponse.getStatusLine()).andReturn(mockStatusLine);
        EasyMock.expect(mockStatusLine.getStatusCode()).andReturn(200);

        replayMocks();
        impl.revalidateCacheEntry(target, request, mockContext, mockCacheEntry);

        try {
            // shut down backend executor and make sure all finishes properly, 1 second should be sufficient
            schedulingStrategy.shutdown();
            schedulingStrategy.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {

        } finally {
            verifyMocks();

            Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
        }
    }

    public void replayMocks() {
        EasyMock.replay(mockSchedulingStrategy);
        EasyMock.replay(mockClient);
        EasyMock.replay(mockContext);
        EasyMock.replay(mockCacheEntry);
        EasyMock.replay(mockResponse);
        EasyMock.replay(mockStatusLine);
    }

    public void verifyMocks() {
        EasyMock.verify(mockSchedulingStrategy);
        EasyMock.verify(mockClient);
        EasyMock.verify(mockContext);
        EasyMock.verify(mockCacheEntry);
        EasyMock.verify(mockResponse);
        EasyMock.verify(mockStatusLine);
    }
}
