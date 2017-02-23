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

package org.apache.hc.client5.http.impl.integration;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@code PoolingHttpClientConnectionManager} that do require a server
 * to communicate with.
 */
public class TestConnectionManagement extends LocalServerTestBase {

    /**
     * Tests releasing and re-using a connection after a response is read.
     */
    @Test
    public void testReleaseConnection() throws Exception {

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", target, uri);
        final HttpContext context = new BasicHttpContext();

        final LeaseRequest leaseRequest1 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint1 = leaseRequest1.get(0, TimeUnit.MILLISECONDS);

        this.connManager.connect(endpoint1, 0, TimeUnit.MILLISECONDS, context);

        final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                new RequestTargetHost(), new RequestContent(), new RequestConnControl());

        final HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        try (final ClassicHttpResponse response1 = endpoint1.execute(request, exec, context)) {
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());
        }

        // check that there is no auto-release by default
        try {
            // this should fail quickly, connection has not been released
            final LeaseRequest leaseRequest2 = this.connManager.lease(route, null);
            leaseRequest2.get(10, TimeUnit.MILLISECONDS);
            Assert.fail("TimeoutException expected");
        } catch (final TimeoutException ex) {
            // expected
        }

        endpoint1.close();
        this.connManager.release(endpoint1, null, -1, null);
        final LeaseRequest leaseRequest2 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint2 = leaseRequest2.get(0, TimeUnit.MILLISECONDS);
        Assert.assertFalse(endpoint2.isConnected());

        this.connManager.connect(endpoint2, 0, TimeUnit.MILLISECONDS, context);

        try (final ClassicHttpResponse response2 = endpoint2.execute(request, exec, context)) {
            Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        }

        // release connection after marking it for re-use
        // expect the next connection obtained to be open
        this.connManager.release(endpoint2, null, -1, null);

        final LeaseRequest leaseRequest3 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint3 = leaseRequest3.get(0, TimeUnit.MILLISECONDS);
        Assert.assertTrue(endpoint3.isConnected());

        // repeat the communication, no need to prepare the request again
        try (final ClassicHttpResponse response3 = endpoint3.execute(request, exec, context)) {
            Assert.assertEquals(HttpStatus.SC_OK, response3.getCode());
        }

        this.connManager.release(endpoint3, null, -1, null);
        this.connManager.close();
    }

    /**
     * Tests releasing with time limits.
     */
    @Test
    public void testReleaseConnectionWithTimeLimits() throws Exception {

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final int      rsplen = 8;
        final String      uri = "/random/" + rsplen;

        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", target, uri);
        final HttpContext context = new BasicHttpContext();

        final LeaseRequest leaseRequest1 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint1 = leaseRequest1.get(0, TimeUnit.MILLISECONDS);
        this.connManager.connect(endpoint1, 0, TimeUnit.MILLISECONDS, context);

        final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                new RequestTargetHost(), new RequestContent(), new RequestConnControl());

        final HttpRequestExecutor exec = new HttpRequestExecutor();
        exec.preProcess(request, httpProcessor, context);
        try (final ClassicHttpResponse response1 = endpoint1.execute(request, exec, context)) {
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());
        }

        // check that there is no auto-release by default
        try {
            // this should fail quickly, connection has not been released
            final LeaseRequest leaseRequest2 = this.connManager.lease(route, null);
            leaseRequest2.get(10, TimeUnit.MILLISECONDS);
            Assert.fail("TimeoutException expected");
        } catch (final TimeoutException ex) {
            // expected
        }

        endpoint1.close();
        this.connManager.release(endpoint1, null, 100, TimeUnit.MILLISECONDS);

        final LeaseRequest leaseRequest2 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint2 = leaseRequest2.get(0, TimeUnit.MILLISECONDS);
        Assert.assertFalse(endpoint2.isConnected());

        this.connManager.connect(endpoint2, 0, TimeUnit.MILLISECONDS, context);

        try (final ClassicHttpResponse response2 = endpoint2.execute(request, exec, context)) {
            Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        }

        this.connManager.release(endpoint2, null, 100, TimeUnit.MILLISECONDS);

        final LeaseRequest leaseRequest3 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint3 = leaseRequest3.get(0, TimeUnit.MILLISECONDS);
        Assert.assertTrue(endpoint3.isConnected());

        // repeat the communication, no need to prepare the request again
        try (final ClassicHttpResponse response3 = endpoint3.execute(request, exec, context)) {
            Assert.assertEquals(HttpStatus.SC_OK, response3.getCode());
        }

        this.connManager.release(endpoint3, null, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(150);

        final LeaseRequest leaseRequest4 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint4 = leaseRequest4.get(0, TimeUnit.MILLISECONDS);
        Assert.assertFalse(endpoint4.isConnected());

        // repeat the communication, no need to prepare the request again
        this.connManager.connect(endpoint4, 0, TimeUnit.MILLISECONDS, context);

        try (final ClassicHttpResponse response4 = endpoint4.execute(request, exec, context)) {
            Assert.assertEquals(HttpStatus.SC_OK, response4.getCode());
        }

        this.connManager.close();
    }

    @Test
    public void testCloseExpiredIdleConnections() throws Exception {

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final LeaseRequest leaseRequest1 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint1 = leaseRequest1.get(0, TimeUnit.MILLISECONDS);
        this.connManager.connect(endpoint1, 0, TimeUnit.MILLISECONDS, context);

        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getLeased());
        Assert.assertEquals(1, this.connManager.getStats(route).getLeased());

        this.connManager.release(endpoint1, null, 100, TimeUnit.MILLISECONDS);

        // Released, still active.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        this.connManager.closeExpired();

        // Time has not expired yet.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        Thread.sleep(150);

        this.connManager.closeExpired();

        // Time expired now, connections are destroyed.
        Assert.assertEquals(Collections.emptySet(), this.connManager.getRoutes());
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(0, this.connManager.getStats(route).getAvailable());

        this.connManager.close();
    }

    @Test
    public void testCloseExpiredTTLConnections() throws Exception {

        this.connManager = new PoolingHttpClientConnectionManager(
                100, TimeUnit.MILLISECONDS);
        this.clientBuilder.setConnectionManager(this.connManager);

        this.connManager.setMaxTotal(1);

        final HttpHost target = start();
        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final LeaseRequest leaseRequest1 = this.connManager.lease(route, null);
        final ConnectionEndpoint endpoint1 = leaseRequest1.get(0, TimeUnit.MILLISECONDS);
        this.connManager.connect(endpoint1, 0, TimeUnit.MILLISECONDS, context);

        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getLeased());
        Assert.assertEquals(1, this.connManager.getStats(route).getLeased());
        // Release, let remain idle for forever
        this.connManager.release(endpoint1, null, 0, TimeUnit.MILLISECONDS);

        // Released, still active.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        this.connManager.closeExpired();

        // Time has not expired yet.
        Assert.assertEquals(Collections.singleton(route), this.connManager.getRoutes());
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(1, this.connManager.getStats(route).getAvailable());

        Thread.sleep(150);

        this.connManager.closeExpired();

        // TTL expired now, connections are destroyed.
        Assert.assertEquals(Collections.emptySet(), this.connManager.getRoutes());
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());
        Assert.assertEquals(0, this.connManager.getStats(route).getAvailable());

        this.connManager.close();
    }

}
