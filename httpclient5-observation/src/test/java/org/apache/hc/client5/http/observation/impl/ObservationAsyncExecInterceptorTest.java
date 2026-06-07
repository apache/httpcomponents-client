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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.util.EnumSet;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservationAsyncExecInterceptorTest {

    private HttpServer server;

    private static final String TRACE_PARENT = "traceparent";

    private static final String TRACE_PARENT_VALUE =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private static final String STALE_TRACE_PARENT_VALUE = "stale";

    private static final class CountingHandler
            implements io.micrometer.observation.ObservationHandler<Observation.Context> {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();

        @Override
        public boolean supportsContext(final Observation.Context c) {
            return true;
        }

        @Override
        public void onStart(final Observation.Context c) {
            starts.incrementAndGet();
        }

        @Override
        public void onStop(final Observation.Context c) {
            stops.incrementAndGet();
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close(CloseMode.GRACEFUL);
        }
    }

    @Test
    void emitsObservationAroundAsyncCall() throws Exception {
        // 1) Bind handler to the *localhost* vhost to avoid 421
        server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setListenerPort(0)
                .register("localhost", "/get", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("{\"ok\":true}", ContentType.APPLICATION_JSON));
                })
                .create();
        server.start();
        final int port = server.getLocalPort();

        // 2) Observation registry
        final ObservationRegistry reg = ObservationRegistry.create();
        final CountingHandler h = new CountingHandler();
        reg.observationConfig().observationHandler(h);

        // 3) Options: observation only
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.noneOf(ObservingOptions.MetricSet.class))
                .build();

        // 4) Async client with interceptor
        final HttpAsyncClientBuilder b = HttpAsyncClients.custom();
        b.addExecInterceptorFirst("span", new ObservationAsyncExecInterceptor(reg, opts));

        final HttpHost target = new HttpHost("http", "localhost", port);

        try (final CloseableHttpAsyncClient c = b.build()) {
            c.start();

            // IMPORTANT: relative path + target bound (Host=localhost:<port>)
            final SimpleHttpRequest req = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/get")
                    .build();

            final Future<SimpleHttpResponse> fut = c.execute(req, null);
            final SimpleHttpResponse resp = fut.get(10, TimeUnit.SECONDS);
            assertEquals(200, resp.getCode());
        }

        assertEquals(1, h.starts.get());
        assertEquals(1, h.stops.get());
    }

    @Test
    void propagatesTraceContextAroundAsyncCall() throws Exception {
        final AtomicReference<String> receivedTraceParent = new AtomicReference<>();

        server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setListenerPort(0)
                .register("localhost", "/get", (request, response, context) -> {
                    final Header traceParent = request.getFirstHeader(TRACE_PARENT);
                    receivedTraceParent.set(traceParent != null ? traceParent.getValue() : null);
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("{\"ok\":true}", ContentType.APPLICATION_JSON));
                })
                .create();
        server.start();

        final ObservationRegistry reg = ObservationRegistry.create();
        reg.observationConfig().observationHandler(new TracePropagationHandler());

        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.noneOf(ObservingOptions.MetricSet.class))
                .build();

        final HttpAsyncClientBuilder b = HttpAsyncClients.custom();
        b.addExecInterceptorFirst("span", new ObservationAsyncExecInterceptor(reg, opts));

        final HttpHost target = new HttpHost("http", "localhost", server.getLocalPort());

        try (final CloseableHttpAsyncClient c = b.build()) {
            c.start();

            final SimpleHttpRequest request = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/get")
                    .build();
            request.setHeader(TRACE_PARENT, STALE_TRACE_PARENT_VALUE);

            final Future<SimpleHttpResponse> future = c.execute(request, null);
            final SimpleHttpResponse response = future.get(10, TimeUnit.SECONDS);

            assertEquals(HttpStatus.SC_OK, response.getCode());
        }

        assertEquals(TRACE_PARENT_VALUE, receivedTraceParent.get());
    }

    private static final class TracePropagationHandler implements io.micrometer.observation.ObservationHandler<Observation.Context> {

        @Override
        public boolean supportsContext(final Observation.Context context) {
            return context instanceof HttpClientObservationContext;
        }

        @Override
        public void onStart(final Observation.Context context) {
            final HttpClientObservationContext senderContext = (HttpClientObservationContext) context;
            senderContext.getSetter().set(senderContext.getCarrier(), TRACE_PARENT, TRACE_PARENT_VALUE);
        }

    }
}
