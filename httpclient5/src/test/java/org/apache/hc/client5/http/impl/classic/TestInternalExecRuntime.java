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
package org.apache.hc.client5.http.impl.classic;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

@SuppressWarnings({"static-access"}) // test code
public class TestInternalExecRuntime {

    @Mock
    private Logger log;
    @Mock
    private HttpClientConnectionManager mgr;
    @Mock
    private LeaseRequest leaseRequest;
    @Mock
    private HttpRequestExecutor requestExecutor;
    @Mock
    private CancellableDependency cancellableDependency;
    @Mock
    private ConnectionEndpoint connectionEndpoint;

    private HttpRoute route;
    private InternalExecRuntime execRuntime;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        route = new HttpRoute(new HttpHost("host", 80));
        execRuntime = new InternalExecRuntime(log, mgr, requestExecutor, cancellableDependency);
    }

    @Test
    public void testAcquireEndpoint() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(345, TimeUnit.MILLISECONDS)
                .setConnectTimeout(123, TimeUnit.MILLISECONDS)
                .build();
        context.setRequestConfig(config);
        final HttpRoute route = new HttpRoute(new HttpHost("host", 80));

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireEndpoint("some-id", route, null, context);

        Assert.assertTrue(execRuntime.isEndpointAcquired());
        Assert.assertSame(connectionEndpoint, execRuntime.ensureValid());
        Assert.assertFalse(execRuntime.isEndpointConnected());
        Assert.assertFalse(execRuntime.isConnectionReusable());

        Mockito.verify(leaseRequest).get(Timeout.ofMilliseconds(345));
        Mockito.verify(cancellableDependency, Mockito.times(1)).setDependency(leaseRequest);
        Mockito.verify(cancellableDependency, Mockito.times(1)).setDependency(execRuntime);
        Mockito.verify(cancellableDependency, Mockito.times(2)).setDependency(Mockito.<Cancellable>any());
    }

    @Test(expected = IllegalStateException.class)
    public void testAcquireEndpointAlreadyAcquired() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireEndpoint("some-id", route, null, context);

        Assert.assertTrue(execRuntime.isEndpointAcquired());
        Assert.assertSame(connectionEndpoint, execRuntime.ensureValid());

        execRuntime.acquireEndpoint("some-id", route, null, context);
    }

    @Test(expected = ConnectionRequestTimeoutException.class)
    public void testAcquireEndpointLeaseRequestTimeout() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenThrow(new TimeoutException("timeout"));

        execRuntime.acquireEndpoint("some-id", route, null, context);
    }

    @Test(expected = RequestFailedException.class)
    public void testAcquireEndpointLeaseRequestFailure() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenThrow(new ExecutionException(new IllegalStateException()));

        execRuntime.acquireEndpoint("some-id", route, null, context);
    }

    @Test
    public void testAbortEndpoint() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireEndpoint("some-id", new HttpRoute(new HttpHost("host", 80)), null, context);
        Assert.assertTrue(execRuntime.isEndpointAcquired());
        execRuntime.discardEndpoint();

        Assert.assertFalse(execRuntime.isEndpointAcquired());

        Mockito.verify(connectionEndpoint).close(CloseMode.IMMEDIATE);
        Mockito.verify(mgr).release(connectionEndpoint, null, TimeValue.ZERO_MILLISECONDS);

        execRuntime.discardEndpoint();

        Mockito.verify(connectionEndpoint, Mockito.times(1)).close(CloseMode.IMMEDIATE);
        Mockito.verify(mgr, Mockito.times(1)).release(
                Mockito.<ConnectionEndpoint>any(),
                Mockito.any(),
                Mockito.<TimeValue>any());
    }

    @Test
    public void testCancell() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireEndpoint("some-id", route, null, context);
        Assert.assertTrue(execRuntime.isEndpointAcquired());

        Assert.assertTrue(execRuntime.cancel());

        Assert.assertFalse(execRuntime.isEndpointAcquired());

        Mockito.verify(connectionEndpoint).close(CloseMode.IMMEDIATE);
        Mockito.verify(mgr).release(connectionEndpoint, null, TimeValue.ZERO_MILLISECONDS);

        Assert.assertFalse(execRuntime.cancel());

        Mockito.verify(connectionEndpoint, Mockito.times(1)).close(CloseMode.IMMEDIATE);
        Mockito.verify(mgr, Mockito.times(1)).release(
                Mockito.<ConnectionEndpoint>any(),
                Mockito.any(),
                Mockito.<TimeValue>any());
    }

    @Test
    public void testReleaseEndpointReusable() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireEndpoint("some-id", route, null, context);
        Assert.assertTrue(execRuntime.isEndpointAcquired());

        execRuntime.markConnectionReusable("some state", TimeValue.ofMilliseconds(100000));

        execRuntime.releaseEndpoint();

        Assert.assertFalse(execRuntime.isEndpointAcquired());

        Mockito.verify(connectionEndpoint, Mockito.never()).close();
        Mockito.verify(mgr).release(connectionEndpoint, "some state", TimeValue.ofMilliseconds(100000));

        execRuntime.releaseEndpoint();

        Mockito.verify(mgr, Mockito.times(1)).release(
                Mockito.<ConnectionEndpoint>any(),
                Mockito.any(),
                Mockito.<TimeValue>any());
    }

    @Test
    public void testReleaseEndpointNonReusable() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireEndpoint("some-id", route, null, context);
        Assert.assertTrue(execRuntime.isEndpointAcquired());

        execRuntime.markConnectionReusable("some state", TimeValue.ofMilliseconds(100000));
        execRuntime.markConnectionNonReusable();

        execRuntime.releaseEndpoint();

        Assert.assertFalse(execRuntime.isEndpointAcquired());

        Mockito.verify(connectionEndpoint, Mockito.times(1)).close(CloseMode.IMMEDIATE);
        Mockito.verify(mgr).release(connectionEndpoint, null, TimeValue.ZERO_MILLISECONDS);

        execRuntime.releaseEndpoint();

        Mockito.verify(mgr, Mockito.times(1)).release(
                Mockito.<ConnectionEndpoint>any(),
                Mockito.any(),
                Mockito.<TimeValue>any());
    }

    @Test
    public void testConnectEndpoint() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(345, TimeUnit.MILLISECONDS)
                .setConnectTimeout(123, TimeUnit.MILLISECONDS)
                .build();
        context.setRequestConfig(config);

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireEndpoint("some-id", route, null, context);
        Assert.assertTrue(execRuntime.isEndpointAcquired());

        Mockito.when(connectionEndpoint.isConnected()).thenReturn(false);
        Assert.assertFalse(execRuntime.isEndpointConnected());

        execRuntime.connectEndpoint(context);

        Mockito.verify(mgr).connect(connectionEndpoint, Timeout.ofMilliseconds(123), context);
    }

    @Test
    public void testDisonnectEndpoint() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(Mockito.eq("some-id"), Mockito.eq(route), Mockito.<Timeout>any(), Mockito.any()))
                .thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(Mockito.<Timeout>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireEndpoint("some-id", route, null, context);
        Assert.assertTrue(execRuntime.isEndpointAcquired());

        Mockito.when(connectionEndpoint.isConnected()).thenReturn(true);
        Assert.assertTrue(execRuntime.isEndpointConnected());

        execRuntime.connectEndpoint(context);

        Mockito.verify(mgr, Mockito.never()).connect(
                Mockito.same(connectionEndpoint), Mockito.<TimeValue>any(), Mockito.<HttpClientContext>any());

        execRuntime.disconnectEndpoint();

        Mockito.verify(connectionEndpoint).close();
    }

}
