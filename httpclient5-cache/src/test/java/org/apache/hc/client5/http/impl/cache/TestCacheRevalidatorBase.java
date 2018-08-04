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

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestCacheRevalidatorBase {

    @Mock
    private SchedulingStrategy mockSchedulingStrategy;
    @Mock
    private CacheRevalidatorBase.ScheduledExecutor mockScheduledExecutor;
    @Mock
    private Runnable mockOperation;

    private CacheRevalidatorBase impl;


    @Before
    public void setUp() {
        impl = new CacheRevalidatorBase(mockScheduledExecutor, mockSchedulingStrategy);
    }

    @Test
    public void testRevalidateCacheEntrySchedulesExecutionAndPopulatesIdentifier() {
        when(mockSchedulingStrategy.schedule(ArgumentMatchers.anyInt())).thenReturn(TimeValue.ofSeconds(1));

        final String cacheKey = "blah";
        impl.scheduleRevalidation(cacheKey, mockOperation);

        verify(mockSchedulingStrategy).schedule(0);
        verify(mockScheduledExecutor).schedule(ArgumentMatchers.same(mockOperation), ArgumentMatchers.eq(TimeValue.ofSeconds(1)));

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testMarkCompleteRemovesIdentifier() {
        when(mockSchedulingStrategy.schedule(ArgumentMatchers.anyInt())).thenReturn(TimeValue.ofSeconds(3));

        final String cacheKey = "blah";
        impl.scheduleRevalidation(cacheKey, mockOperation);

        verify(mockSchedulingStrategy).schedule(0);
        verify(mockScheduledExecutor).schedule(ArgumentMatchers.<Runnable>any(), ArgumentMatchers.eq(TimeValue.ofSeconds(3)));

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
        Assert.assertTrue(impl.getScheduledIdentifiers().contains(cacheKey));

        impl.jobSuccessful(cacheKey);

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testRevalidateCacheEntryDoesNotPopulateIdentifierOnRejectedExecutionException() {
        when(mockSchedulingStrategy.schedule(ArgumentMatchers.anyInt())).thenReturn(TimeValue.ofSeconds(2));
        doThrow(new RejectedExecutionException()).when(mockScheduledExecutor).schedule(ArgumentMatchers.<Runnable>any(), ArgumentMatchers.<TimeValue>any());

        final String cacheKey = "blah";
        impl.scheduleRevalidation(cacheKey, mockOperation);

        Assert.assertEquals(0, impl.getScheduledIdentifiers().size());
        verify(mockScheduledExecutor).schedule(ArgumentMatchers.<Runnable>any(), ArgumentMatchers.eq(TimeValue.ofSeconds(2)));
    }

    @Test
    public void testRevalidateCacheEntryProperlyCollapsesRequest() {
        when(mockSchedulingStrategy.schedule(ArgumentMatchers.anyInt())).thenReturn(TimeValue.ofSeconds(2));

        final String cacheKey = "blah";
        impl.scheduleRevalidation(cacheKey, mockOperation);
        impl.scheduleRevalidation(cacheKey, mockOperation);
        impl.scheduleRevalidation(cacheKey, mockOperation);

        verify(mockSchedulingStrategy).schedule(ArgumentMatchers.anyInt());
        verify(mockScheduledExecutor).schedule(ArgumentMatchers.<Runnable>any(), ArgumentMatchers.eq(TimeValue.ofSeconds(2)));

        Assert.assertEquals(1, impl.getScheduledIdentifiers().size());
    }

    @Test
    public void testStaleResponse() {
        final HttpResponse response1 = new BasicHttpResponse(HttpStatus.SC_OK);
        response1.addHeader(HeaderConstants.WARNING, "110 localhost \"Response is stale\"");
        Assert.assertThat(impl.isStale(response1), CoreMatchers.equalTo(true));

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_OK);
        response2.addHeader(HeaderConstants.WARNING, "111 localhost \"Revalidation failed\"");
        Assert.assertThat(impl.isStale(response2), CoreMatchers.equalTo(true));

        final HttpResponse response3 = new BasicHttpResponse(HttpStatus.SC_OK);
        response3.addHeader(HeaderConstants.WARNING, "xxx localhost \"Huh?\"");
        Assert.assertThat(impl.isStale(response3), CoreMatchers.equalTo(false));

        final HttpResponse response4 = new BasicHttpResponse(HttpStatus.SC_OK);
        Assert.assertThat(impl.isStale(response4), CoreMatchers.equalTo(false));
    }

    @Test
    public void testShutdown() throws Exception {
        impl.close();
        impl.awaitTermination(Timeout.ofMinutes(2));

        verify(mockScheduledExecutor).shutdown();
        verify(mockScheduledExecutor).awaitTermination(Timeout.ofMinutes(2));
    }

}
