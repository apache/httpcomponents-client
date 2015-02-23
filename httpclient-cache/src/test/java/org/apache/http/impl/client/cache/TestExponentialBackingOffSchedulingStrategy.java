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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TestExponentialBackingOffSchedulingStrategy {

    private ScheduledExecutorService mockExecutor;
    private ExponentialBackOffSchedulingStrategy impl;

    @Before
    public void setUp() {
        mockExecutor = mock(ScheduledExecutorService.class);

        impl = new ExponentialBackOffSchedulingStrategy(
                mockExecutor,
                ExponentialBackOffSchedulingStrategy.DEFAULT_BACK_OFF_RATE,
                ExponentialBackOffSchedulingStrategy.DEFAULT_INITIAL_EXPIRY_IN_MILLIS,
                ExponentialBackOffSchedulingStrategy.DEFAULT_MAX_EXPIRY_IN_MILLIS
        );
    }

    @Test
    public void testScheduleWithoutPreviousError() {
        final AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(0));

        expectRequestScheduledWithoutDelay(request);

        impl.schedule(request);

        verify(mockExecutor).schedule(request, 0, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testScheduleWithOneFailedAttempt() {
        final AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(1));

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(6));

        impl.schedule(request);

        verify(mockExecutor).schedule(request, 6000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testScheduleWithTwoFailedAttempts() {
        final AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(2));

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(60));

        impl.schedule(request);

        verify(mockExecutor).schedule(request, 60000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testScheduleWithThreeFailedAttempts() {
        final AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(3));

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(600));

        impl.schedule(request);

        verify(mockExecutor).schedule(request, 600000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testScheduleWithFourFailedAttempts() {
        final AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(4));

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(6000));

        impl.schedule(request);

        verify(mockExecutor).schedule(request, 6000000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testScheduleWithFiveFailedAttempts() {
        final AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(5));

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(60000));

        impl.schedule(request);

        verify(mockExecutor).schedule(request, 60000000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testScheduleWithSixFailedAttempts() {
        final AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(6));

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(86400));

        impl.schedule(request);

        verify(mockExecutor).schedule(request, 86400000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testScheduleWithMaxNumberOfFailedAttempts() {
        final AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(Integer.MAX_VALUE));

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(86400));

        impl.schedule(request);

        verify(mockExecutor).schedule(request, 86400000, TimeUnit.MILLISECONDS);
    }

    private void expectRequestScheduledWithoutDelay(final AsynchronousValidationRequest request) {
        expectRequestScheduledWithDelay(request, 0);
    }

    private void expectRequestScheduledWithDelay(final AsynchronousValidationRequest request, final long delayInMillis) {
        when(mockExecutor.schedule(request, delayInMillis, TimeUnit.MILLISECONDS)).thenReturn(null);
    }

    private AsynchronousValidationRequest createAsynchronousValidationRequest(final int errorCount) {
        final ClientExecChain clientExecChain = mock(ClientExecChain.class);
        final CachingExec cachingHttpClient = new CachingExec(clientExecChain);
        final AsynchronousValidator mockValidator = new AsynchronousValidator(impl);
        final HttpRoute httpRoute = new HttpRoute(new HttpHost("foo.example.com", 80));
        final HttpRequestWrapper httpRequestWrapper = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "/"));
        final HttpClientContext httpClientContext = new HttpClientContext();
        return new AsynchronousValidationRequest(mockValidator, cachingHttpClient, httpRoute, httpRequestWrapper,
                httpClientContext, null, null, "identifier", errorCount);
    }

    private static int withErrorCount(final int errorCount) {
        return errorCount;
    }
}
