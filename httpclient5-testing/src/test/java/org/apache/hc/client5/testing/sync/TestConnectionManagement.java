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

package org.apache.hc.client5.testing.sync;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests for {@code PoolingHttpClientConnectionManager} that do require a server
 * to communicate with.
 */
public class TestConnectionManagement {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources = new TestClientResources(URIScheme.HTTP, TIMEOUT);

    ConnectionEndpoint.RequestExecutor exec;

    @BeforeEach
    public void setup() {
        exec = new ConnectionEndpoint.RequestExecutor() {

            final HttpRequestExecutor requestExecutor = new HttpRequestExecutor();
            final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                    new RequestTargetHost(), new RequestContent(), new RequestConnControl());
            @Override
            public ClassicHttpResponse execute(final ClassicHttpRequest request,
                                               final HttpClientConnection conn,
                                               final HttpContext context) throws IOException, HttpException {
                requestExecutor.preProcess(request, httpProcessor, context);
                final ClassicHttpResponse response = requestExecutor.execute(request, conn, context);
                requestExecutor.postProcess(response, httpProcessor, context);
                return response;
            }

        };
    }

    public ClassicTestServer startServer() throws IOException {
        return testResources.startServer(null, null, null);
    }

    public CloseableHttpClient startClient() throws Exception {
        return testResources.startClient(b -> {}, b -> {});
    }

    public HttpHost targetHost() {
        return testResources.targetHost();
    }

    /**
     * Tests releasing and re-using a connection after a response is read.
     */
    @Test
    public void testReleaseConnection() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        startClient();

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();
        connManager.setMaxTotal(1);

        final HttpRoute route = new HttpRoute(target, null, false);
        final int rsplen = 8;
        final String uri = "/random/" + rsplen;

        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", target, uri);
        final HttpContext context = new BasicHttpContext();

        final LeaseRequest leaseRequest1 = connManager.lease("id1", route, null);
        final ConnectionEndpoint endpoint1 = leaseRequest1.get(Timeout.ZERO_MILLISECONDS);

        connManager.connect(endpoint1, null, context);

        try (final ClassicHttpResponse response1 = endpoint1.execute("id1", request, exec, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());
        }

        // check that there is no auto-release by default
        // this should fail quickly, connection has not been released
        final LeaseRequest leaseRequest2 = connManager.lease("id2", route, null);
        Assertions.assertThrows(TimeoutException.class, () -> leaseRequest2.get(Timeout.ofMilliseconds(10)));

        endpoint1.close();
        connManager.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);
        final LeaseRequest leaseRequest3 = connManager.lease("id2", route, null);
        final ConnectionEndpoint endpoint2 = leaseRequest3.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertFalse(endpoint2.isConnected());

        connManager.connect(endpoint2, null, context);

        try (final ClassicHttpResponse response2 = endpoint2.execute("id2", request, exec, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
        }

        // release connection after marking it for re-use
        // expect the next connection obtained to be open
        connManager.release(endpoint2, null, TimeValue.NEG_ONE_MILLISECOND);

        final LeaseRequest leaseRequest4 = connManager.lease("id3", route, null);
        final ConnectionEndpoint endpoint3 = leaseRequest4.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertTrue(endpoint3.isConnected());

        // repeat the communication, no need to prepare the request again
        try (final ClassicHttpResponse response3 = endpoint3.execute("id3", request, exec, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response3.getCode());
        }

        connManager.release(endpoint3, null, TimeValue.NEG_ONE_MILLISECOND);
        connManager.close();
    }

    /**
     * Tests releasing with time limits.
     */
    @Test
    public void testReleaseConnectionWithTimeLimits() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        startClient();

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();
        connManager.setMaxTotal(1);

        final HttpRoute route = new HttpRoute(target, null, false);
        final int rsplen = 8;
        final String uri = "/random/" + rsplen;

        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", target, uri);
        final HttpContext context = new BasicHttpContext();

        final LeaseRequest leaseRequest1 = connManager.lease("id1", route, null);
        final ConnectionEndpoint endpoint1 = leaseRequest1.get(Timeout.ZERO_MILLISECONDS);
        connManager.connect(endpoint1, null, context);

        try (final ClassicHttpResponse response1 = endpoint1.execute("id1", request, exec, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());
        }

        // check that there is no auto-release by default
        final LeaseRequest leaseRequest2 = connManager.lease("id2", route, null);
        // this should fail quickly, connection has not been released
        Assertions.assertThrows(TimeoutException.class, () -> leaseRequest2.get(Timeout.ofMilliseconds(10)));

        endpoint1.close();
        connManager.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        final LeaseRequest leaseRequest3 = connManager.lease("id2", route, null);
        final ConnectionEndpoint endpoint2 = leaseRequest3.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertFalse(endpoint2.isConnected());

        connManager.connect(endpoint2, null, context);

        try (final ClassicHttpResponse response2 = endpoint2.execute("id2", request, exec, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
        }

        connManager.release(endpoint2, null, TimeValue.ofMilliseconds(100));

        final LeaseRequest leaseRequest4 = connManager.lease("id3", route, null);
        final ConnectionEndpoint endpoint3 = leaseRequest4.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertTrue(endpoint3.isConnected());

        // repeat the communication, no need to prepare the request again
        try (final ClassicHttpResponse response3 = endpoint3.execute("id3", request, exec, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response3.getCode());
        }

        connManager.release(endpoint3, null, TimeValue.ofMilliseconds(100));
        Thread.sleep(150);

        final LeaseRequest leaseRequest5 = connManager.lease("id4", route, null);
        final ConnectionEndpoint endpoint4 = leaseRequest5.get(Timeout.ZERO_MILLISECONDS);
        Assertions.assertFalse(endpoint4.isConnected());

        // repeat the communication, no need to prepare the request again
        connManager.connect(endpoint4, null, context);

        try (final ClassicHttpResponse response4 = endpoint4.execute("id4", request, exec, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response4.getCode());
        }

        connManager.close();
    }

    @Test
    public void testCloseExpiredIdleConnections() throws Exception {
        startServer();
        final HttpHost target = targetHost();
        startClient();

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();
        connManager.setMaxTotal(1);

        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final LeaseRequest leaseRequest1 = connManager.lease("id1", route, null);
        final ConnectionEndpoint endpoint1 = leaseRequest1.get(Timeout.ZERO_MILLISECONDS);
        connManager.connect(endpoint1, null, context);

        Assertions.assertEquals(1, connManager.getTotalStats().getLeased());
        Assertions.assertEquals(1, connManager.getStats(route).getLeased());

        connManager.release(endpoint1, null, TimeValue.ofMilliseconds(100));

        // Released, still active.
        Assertions.assertEquals(1, connManager.getTotalStats().getAvailable());
        Assertions.assertEquals(1, connManager.getStats(route).getAvailable());

        connManager.closeExpired();

        // Time has not expired yet.
        Assertions.assertEquals(1, connManager.getTotalStats().getAvailable());
        Assertions.assertEquals(1, connManager.getStats(route).getAvailable());

        Thread.sleep(150);

        connManager.closeExpired();

        // Time expired now, connections are destroyed.
        Assertions.assertEquals(0, connManager.getTotalStats().getAvailable());
        Assertions.assertEquals(0, connManager.getStats(route).getAvailable());

        connManager.close();
    }

    @Test
    public void testCloseExpiredTTLConnections() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        testResources.startClient(
                builder -> builder
                        .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                        .setConnPoolPolicy(PoolReusePolicy.LIFO)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setTimeToLive(TimeValue.ofMilliseconds(100))
                                .build())
                        .setMaxConnTotal(1),
                builder -> {}
        );

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();
        connManager.setMaxTotal(1);

        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpContext context = new BasicHttpContext();

        final LeaseRequest leaseRequest1 = connManager.lease("id1", route, null);
        final ConnectionEndpoint endpoint1 = leaseRequest1.get(Timeout.ZERO_MILLISECONDS);
        connManager.connect(endpoint1, null, context);

        Assertions.assertEquals(1, connManager.getTotalStats().getLeased());
        Assertions.assertEquals(1, connManager.getStats(route).getLeased());
        // Release, let remain idle for forever
        connManager.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        // Released, still active.
        Assertions.assertEquals(1, connManager.getTotalStats().getAvailable());
        Assertions.assertEquals(1, connManager.getStats(route).getAvailable());

        connManager.closeExpired();

        // Time has not expired yet.
        Assertions.assertEquals(1, connManager.getTotalStats().getAvailable());
        Assertions.assertEquals(1, connManager.getStats(route).getAvailable());

        Thread.sleep(150);

        connManager.closeExpired();

        // TTL expired now, connections are destroyed.
        Assertions.assertEquals(0, connManager.getTotalStats().getAvailable());
        Assertions.assertEquals(0, connManager.getStats(route).getAvailable());

        connManager.close();
    }

}
