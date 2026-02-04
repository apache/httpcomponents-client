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
package org.apache.hc.client5.http.observation.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

class MeteredConnectionManagerTest {

    private static final class DummyEndpoint extends ConnectionEndpoint {
        @Deprecated
        @Override
        public org.apache.hc.core5.http.ClassicHttpResponse execute(
                final String id,
                final org.apache.hc.core5.http.ClassicHttpRequest request,
                final org.apache.hc.core5.http.impl.io.HttpRequestExecutor executor,
                final HttpContext context) {
            return null;
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
        }

        @Override
        public void close() {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }
    }

    private static final class DummyConnectionManager implements HttpClientConnectionManager {
        @Override
        public LeaseRequest lease(final String id, final HttpRoute route, final Timeout requestTimeout, final Object state) {
            return new LeaseRequest() {
                @Override
                public ConnectionEndpoint get(final Timeout timeout) {
                    return new DummyEndpoint();
                }

                @Override
                public boolean cancel() {
                    return false;
                }
            };
        }

        @Override
        public void release(final ConnectionEndpoint endpoint, final Object newState, final TimeValue validDuration) {
        }

        @Override
        public void connect(final ConnectionEndpoint endpoint, final TimeValue connectTimeout, final HttpContext context) {
        }

        @Override
        public void upgrade(final ConnectionEndpoint endpoint, final HttpContext context) {
        }

        @Override
        public void close() {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }
    }

    private static final class DummyAsyncEndpoint extends AsyncConnectionEndpoint {
        @Override
        public void execute(final String id,
                            final org.apache.hc.core5.http.nio.AsyncClientExchangeHandler exchangeHandler,
                            final org.apache.hc.core5.http.nio.HandlerFactory<org.apache.hc.core5.http.nio.AsyncPushConsumer> pushHandlerFactory,
                            final HttpContext context) {
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }
    }

    private static final class DummyAsyncConnectionManager implements AsyncClientConnectionManager {
        @Override
        public java.util.concurrent.Future<AsyncConnectionEndpoint> lease(final String id,
                                                                          final HttpRoute route,
                                                                          final Object state,
                                                                          final Timeout requestTimeout,
                                                                          final FutureCallback<AsyncConnectionEndpoint> callback) {
            final BasicFuture<AsyncConnectionEndpoint> future = new BasicFuture<>(callback);
            future.completed(new DummyAsyncEndpoint());
            return future;
        }

        @Override
        public void release(final AsyncConnectionEndpoint endpoint, final Object newState, final TimeValue validDuration) {
        }

        @Override
        public java.util.concurrent.Future<AsyncConnectionEndpoint> connect(final AsyncConnectionEndpoint endpoint,
                                                                            final ConnectionInitiator connectionInitiator,
                                                                            final Timeout connectTimeout,
                                                                            final Object attachment,
                                                                            final HttpContext context,
                                                                            final FutureCallback<AsyncConnectionEndpoint> callback) {
            final BasicFuture<AsyncConnectionEndpoint> future = new BasicFuture<>(callback);
            future.completed(endpoint);
            return future;
        }

        @Override
        public void upgrade(final AsyncConnectionEndpoint endpoint, final Object attachment, final HttpContext context) {
        }

        @Override
        public void close() {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }
    }

    @Test
    void recordsClassicLeaseTime() throws Exception {
        final MeterRegistry registry = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("classic").build();
        final ObservingOptions opts = ObservingOptions.DEFAULT;

        final HttpClientConnectionManager metered =
                new MeteredConnectionManager(new DummyConnectionManager(), registry, mc, opts);

        final HttpRoute route = new HttpRoute(new HttpHost("http", "example.com", 80));
        try {
            metered.lease("id", route, Timeout.ofSeconds(1), null).get(Timeout.ofSeconds(1));
        } finally {
            metered.close();
        }

        assertNotNull(registry.find("classic.pool.lease").timer());
        assertTrue(registry.find("classic.pool.lease").timer().count() >= 1L);
        assertNotNull(registry.find("classic.pool.leases").counter());
        assertTrue(registry.find("classic.pool.leases").counter().count() >= 1.0d);
    }

    @Test
    void recordsAsyncLeaseTime() throws InterruptedException, ExecutionException, TimeoutException, java.io.IOException {
        final MeterRegistry registry = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("async").build();
        final ObservingOptions opts = ObservingOptions.DEFAULT;

        final AsyncClientConnectionManager metered =
                new MeteredAsyncConnectionManager(new DummyAsyncConnectionManager(), registry, mc, opts);

        final HttpRoute route = new HttpRoute(new HttpHost("http", "example.com", 80));
        try {
            metered.lease("id", route, null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        } finally {
            metered.close();
        }

        assertNotNull(registry.find("async.pool.lease").timer());
        assertTrue(registry.find("async.pool.lease").timer().count() >= 1L);
        assertNotNull(registry.find("async.pool.leases").counter());
        assertTrue(registry.find("async.pool.leases").counter().count() >= 1.0d);
    }
}
