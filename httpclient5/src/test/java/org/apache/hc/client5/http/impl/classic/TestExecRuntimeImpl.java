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

import org.apache.hc.client5.http.CancellableAware;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.TimeValue;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SuppressWarnings({"static-access"}) // test code
public class TestExecRuntimeImpl {

    @Mock
    private Logger log;
    @Mock
    private HttpClientConnectionManager mgr;
    @Mock
    private LeaseRequest leaseRequest;
    @Mock
    private HttpRequestExecutor requestExecutor;
    @Mock
    private CancellableAware cancellableAware;
    @Mock
    private ConnectionEndpoint connectionEndpoint;

    private HttpRoute route;
    private ExecRuntimeImpl execRuntime;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        route = new HttpRoute(new HttpHost("host", 80));
        execRuntime = new ExecRuntimeImpl(log, mgr, requestExecutor, cancellableAware);
    }

    @Test
    public void testAcquireEndpoint() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(123, TimeUnit.MILLISECONDS)
                .setSocketTimeout(234, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(345, TimeUnit.MILLISECONDS)
                .build();
        context.setRequestConfig(config);
        final HttpRoute route = new HttpRoute(new HttpHost("host", 80));

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireConnection(route, null, context);

        Assert.assertTrue(execRuntime.isConnectionAcquired());
        Assert.assertSame(connectionEndpoint, execRuntime.ensureValid());
        Assert.assertFalse(execRuntime.isConnected());
        Assert.assertFalse(execRuntime.isConnectionReusable());

        Mockito.verify(leaseRequest).get(345, TimeUnit.MILLISECONDS);
        Mockito.verify(cancellableAware, Mockito.times(1)).setCancellable(leaseRequest);
        Mockito.verify(cancellableAware, Mockito.times(1)).setCancellable(execRuntime);
        Mockito.verify(cancellableAware, Mockito.times(2)).setCancellable(Mockito.<Cancellable>any());
    }

    @Test(expected = IllegalStateException.class)
    public void testAcquireEndpointAlreadyAcquired() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireConnection(route, null, context);

        Assert.assertTrue(execRuntime.isConnectionAcquired());
        Assert.assertSame(connectionEndpoint, execRuntime.ensureValid());

        execRuntime.acquireConnection(route, null, context);
    }

    @Test(expected = ConnectionRequestTimeoutException.class)
    public void testAcquireEndpointLeaseRequestTimeout() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenThrow(new TimeoutException());

        execRuntime.acquireConnection(route, null, context);
    }

    @Test(expected = RequestFailedException.class)
    public void testAcquireEndpointLeaseRequestFailure() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenThrow(new ExecutionException(new IllegalStateException()));

        execRuntime.acquireConnection(route, null, context);
    }

    @Test
    public void testAbortEndpoint() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireConnection(new HttpRoute(new HttpHost("host", 80)), null, context);
        Assert.assertTrue(execRuntime.isConnectionAcquired());
        execRuntime.discardConnection();

        Assert.assertFalse(execRuntime.isConnectionAcquired());

        Mockito.verify(connectionEndpoint).shutdown(ShutdownType.IMMEDIATE);
        Mockito.verify(mgr).release(connectionEndpoint, null, TimeValue.ZERO_MILLISECONDS);

        execRuntime.discardConnection();

        Mockito.verify(connectionEndpoint, Mockito.times(1)).shutdown(ShutdownType.IMMEDIATE);
        Mockito.verify(mgr, Mockito.times(1)).release(
                Mockito.<ConnectionEndpoint>any(),
                Mockito.any(),
                Mockito.<TimeValue>any());
    }

    @Test
    public void testCancell() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireConnection(route, null, context);
        Assert.assertTrue(execRuntime.isConnectionAcquired());

        Assert.assertTrue(execRuntime.cancel());

        Assert.assertFalse(execRuntime.isConnectionAcquired());

        Mockito.verify(connectionEndpoint).shutdown(ShutdownType.IMMEDIATE);
        Mockito.verify(mgr).release(connectionEndpoint, null, TimeValue.ZERO_MILLISECONDS);

        Assert.assertFalse(execRuntime.cancel());

        Mockito.verify(connectionEndpoint, Mockito.times(1)).shutdown(ShutdownType.IMMEDIATE);
        Mockito.verify(mgr, Mockito.times(1)).release(
                Mockito.<ConnectionEndpoint>any(),
                Mockito.any(),
                Mockito.<TimeValue>any());
    }

    @Test
    public void testReleaseEndpointReusable() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireConnection(route, null, context);
        Assert.assertTrue(execRuntime.isConnectionAcquired());

        execRuntime.setConnectionState("some state");
        execRuntime.setConnectionValidFor(TimeValue.ofMillis(100000));
        execRuntime.markConnectionReusable();

        execRuntime.releaseConnection();

        Assert.assertFalse(execRuntime.isConnectionAcquired());

        Mockito.verify(connectionEndpoint, Mockito.never()).close();
        Mockito.verify(mgr).release(connectionEndpoint, "some state", TimeValue.ofMillis(100000));

        execRuntime.releaseConnection();

        Mockito.verify(mgr, Mockito.times(1)).release(
                Mockito.<ConnectionEndpoint>any(),
                Mockito.any(),
                Mockito.<TimeValue>any());
    }

    @Test
    public void testReleaseEndpointNonReusable() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireConnection(route, null, context);
        Assert.assertTrue(execRuntime.isConnectionAcquired());

        execRuntime.setConnectionState("some state");
        execRuntime.setConnectionValidFor(TimeValue.ofMillis(100000));
        execRuntime.markConnectionNonReusable();

        execRuntime.releaseConnection();

        Assert.assertFalse(execRuntime.isConnectionAcquired());

        Mockito.verify(connectionEndpoint, Mockito.times(1)).close();
        Mockito.verify(mgr).release(connectionEndpoint, null, TimeValue.ZERO_MILLISECONDS);

        execRuntime.releaseConnection();

        Mockito.verify(mgr, Mockito.times(1)).release(
                Mockito.<ConnectionEndpoint>any(),
                Mockito.any(),
                Mockito.<TimeValue>any());
    }

    @Test
    public void testConnectEndpoint() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(123, TimeUnit.MILLISECONDS)
                .setSocketTimeout(234, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(345, TimeUnit.MILLISECONDS)
                .build();
        context.setRequestConfig(config);

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireConnection(route, null, context);
        Assert.assertTrue(execRuntime.isConnectionAcquired());

        Mockito.when(connectionEndpoint.isConnected()).thenReturn(false);
        Assert.assertFalse(execRuntime.isConnected());

        execRuntime.connect(context);

        Mockito.verify(mgr).connect(connectionEndpoint, TimeValue.ofMillis(123), context);
        Mockito.verify(connectionEndpoint).setSocketTimeout(234);
    }

    @Test
    public void testDisonnectEndpoint() throws Exception {
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(mgr.lease(route, null)).thenReturn(leaseRequest);
        Mockito.when(leaseRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(connectionEndpoint);

        execRuntime.acquireConnection(route, null, context);
        Assert.assertTrue(execRuntime.isConnectionAcquired());

        Mockito.when(connectionEndpoint.isConnected()).thenReturn(true);
        Assert.assertTrue(execRuntime.isConnected());

        execRuntime.connect(context);

        Mockito.verify(mgr, Mockito.never()).connect(
                Mockito.same(connectionEndpoint), Mockito.<TimeValue>any(), Mockito.<HttpClientContext>any());

        execRuntime.disconnect();

        Mockito.verify(connectionEndpoint).close();
    }

}
