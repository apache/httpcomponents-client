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
package org.apache.hc.client5.testing.async;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.ConnectionHolder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestAsyncConnectionManagement extends AbstractIntegrationTestBase {

    static final Timeout TIMEOUT = Timeout.ofSeconds(30);
    static final Timeout LEASE_TIMEOUT = Timeout.ofSeconds(5);

    public TestAsyncConnectionManagement() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD);
    }

    @BeforeEach
    void setup() {
        configureServer(bootstrap -> bootstrap.register("*", () -> new AbstractSimpleServerExchangeHandler() {

            @Override
            protected SimpleHttpResponse handle(
                    final SimpleHttpRequest request,
                    final HttpCoreContext context) {
                final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                response.setBody("Whatever", ContentType.TEXT_PLAIN);
                return response;
            }
        }));
    }

    /**
     * Tests releasing and re-using a connection after a response is read.
     */
    @Test
    void testReleaseConnection() throws Exception {
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        connManager.setMaxTotal(1);

        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpClientContext context = HttpClientContext.create();

        final Future<AsyncConnectionEndpoint> endpointFuture1 = connManager.lease("id1", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint endpoint1 = endpointFuture1.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());

        final Future<AsyncConnectionEndpoint> connectFuture1 = connManager.connect(endpoint1, client.getImplementation(), TIMEOUT, null, context, null);
        final AsyncConnectionEndpoint openEndpoint1 = connectFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final SimpleHttpRequest request = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .addHeader(HttpHeaders.HOST, target.toHostString())
                .build();

        final Future<SimpleHttpResponse> responseFuture1 = openEndpoint1.execute("ex-1", SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), null);
        final SimpleHttpResponse response1 = responseFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());

        // this should fail quickly, connection has not been released
        final Future<AsyncConnectionEndpoint> endpointFuture2 = connManager.lease("id2", route, null, TIMEOUT, null);
        Assertions.assertThrows(TimeoutException.class, () -> endpointFuture2.get(10, TimeUnit.MILLISECONDS));
        endpointFuture2.cancel(true);

        // close and release the connection
        // expect the next connection obtained to be closed
        openEndpoint1.close();
        connManager.release(openEndpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        final Future<AsyncConnectionEndpoint> endpointFuture3 = connManager.lease("id3", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint endpoint2 = endpointFuture3.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());
        Assertions.assertFalse(endpoint2.isConnected());

        final Future<AsyncConnectionEndpoint> connectFuture2 = connManager.connect(endpoint2, client.getImplementation(), TIMEOUT, null, context, null);
        final AsyncConnectionEndpoint openEndpoint2 = connectFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Future<SimpleHttpResponse> responseFuture2 = openEndpoint2.execute("ex-2", SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), null);
        final SimpleHttpResponse response2 = responseFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());

        // release connection keeping it open
        // expect the next connection obtained to be open
        connManager.release(openEndpoint2, null, TimeValue.NEG_ONE_MILLISECOND);

        final Future<AsyncConnectionEndpoint> endpointFuture4 = connManager.lease("id4", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint openEndpoint3 = endpointFuture4.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());
        Assertions.assertTrue(openEndpoint3.isConnected());

        final Future<SimpleHttpResponse> responseFuture3 = openEndpoint3.execute("ex-3", SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), null);
        final SimpleHttpResponse response3 = responseFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response3.getCode());

        connManager.release(openEndpoint3, null, TimeValue.NEG_ONE_MILLISECOND);
        connManager.close();
    }

    /**
     * Tests releasing with time limits.
     */
    @Test
    void testReleaseConnectionWithTimeLimits() throws Exception {
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        connManager.setMaxTotal(1);

        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpClientContext context = HttpClientContext.create();

        final Future<AsyncConnectionEndpoint> endpointFuture1 = connManager.lease("id1", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint endpoint1 = endpointFuture1.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());

        final Future<AsyncConnectionEndpoint> connectFuture1 = connManager.connect(endpoint1, client.getImplementation(), TIMEOUT, null, context, null);
        final AsyncConnectionEndpoint openEndpoint1 = connectFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final SimpleHttpRequest request = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .addHeader(HttpHeaders.HOST, target.toHostString())
                .build();

        final Future<SimpleHttpResponse> responseFuture1 = openEndpoint1.execute("ex-1", SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), null);
        final SimpleHttpResponse response1 = responseFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());

        final Future<AsyncConnectionEndpoint> endpointFuture2 = connManager.lease("id2", route, null, TIMEOUT, null);
        Assertions.assertThrows(TimeoutException.class, () -> endpointFuture2.get(10, TimeUnit.MILLISECONDS));
        endpointFuture2.cancel(true);

        openEndpoint1.close();
        connManager.release(openEndpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        final Future<AsyncConnectionEndpoint> endpointFuture3 = connManager.lease("id3", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint endpoint2 = endpointFuture3.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());
        Assertions.assertFalse(endpoint2.isConnected());

        final Future<AsyncConnectionEndpoint> connectFuture2 = connManager.connect(endpoint2, client.getImplementation(), TIMEOUT, null, context, null);
        final AsyncConnectionEndpoint openEndpoint2 = connectFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Future<SimpleHttpResponse> responseFuture2 = openEndpoint2.execute("ex-2", SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), null);
        final SimpleHttpResponse response2 = responseFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());

        connManager.release(openEndpoint2, null, TimeValue.ofMilliseconds(100));
        Thread.sleep(150);

        final Future<AsyncConnectionEndpoint> endpointFuture4 = connManager.lease("id4", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint openEndpoint3 = endpointFuture4.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());
        Assertions.assertFalse(openEndpoint3.isConnected());
        connManager.release(openEndpoint3, null, TimeValue.ofMilliseconds(100));

        connManager.close();
    }

    @Test
    void testCloseExpiredIdleConnections() throws Exception {
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        connManager.setMaxTotal(1);

        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpClientContext context = HttpClientContext.create();

        final Future<AsyncConnectionEndpoint> endpointFuture1 = connManager.lease("id1", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint endpoint1 = endpointFuture1.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());

        connManager.connect(endpoint1, client.getImplementation(), TIMEOUT, null, context, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

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
    void testCloseExpiredTTLConnections() throws Exception {
        final HttpHost target = startServer();

        final PoolingAsyncClientConnectionManager connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setTimeToLive(TimeValue.ofMilliseconds(100))
                        .build())
                .build();
        configureClient(builder -> builder.setConnectionManager(connManager));

        final TestAsyncClient client = startClient();

        connManager.setMaxTotal(1);

        final HttpRoute route = new HttpRoute(target, null, false);
        final HttpClientContext context = HttpClientContext.create();

        final Future<AsyncConnectionEndpoint> endpointFuture1 = connManager.lease("id1", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint endpoint1 = endpointFuture1.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());

        connManager.connect(endpoint1, client.getImplementation(), TIMEOUT, null, context, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

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

    @Test
    void testConnectionTimeoutSetting() throws Exception {
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final Timeout connectionSocketTimeout = Timeout.ofMinutes(5);

        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        connManager.setMaxTotal(1);
        connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(connectionSocketTimeout)
                .build());

        final HttpRoute route = new HttpRoute(target, null, false);

        final SimpleHttpRequest request = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .addHeader(HttpHeaders.HOST, target.toHostString())
                .build();
        final HttpClientContext context = HttpClientContext.create();

        final Future<AsyncConnectionEndpoint> endpointFuture1 = connManager.lease("id1", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint endpoint1 = endpointFuture1.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());

        final Future<AsyncConnectionEndpoint> connectFuture1 = connManager.connect(endpoint1, client.getImplementation(), TIMEOUT, null, context, null);
        final AsyncConnectionEndpoint openEndpoint1 = connectFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        // Modify socket timeout of the endpoint
        endpoint1.setSocketTimeout(Timeout.ofSeconds(30));

        final Future<SimpleHttpResponse> responseFuture1 = openEndpoint1.execute("ex-1", SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), null);
        final SimpleHttpResponse response1 = responseFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());

        connManager.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        final Future<AsyncConnectionEndpoint> endpointFuture2 = connManager.lease("id2", route, null, TIMEOUT, null);
        final AsyncConnectionEndpoint endpoint2 = endpointFuture2.get(LEASE_TIMEOUT.getDuration(), LEASE_TIMEOUT.getTimeUnit());
        Assertions.assertTrue(endpoint2.isConnected());

        final HttpConnection connection = ((ConnectionHolder) endpoint2).get();
        Assertions.assertEquals(connectionSocketTimeout, connection.getSocketTimeout());

        connManager.close();
    }

}
