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

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservationClassicExecInterceptorTest {

    private HttpServer server;

    private static final class CountingHandler implements io.micrometer.observation.ObservationHandler<Observation.Context> {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();

        @Override
        public boolean supportsContext(final Observation.Context context) {
            return true;
        }

        @Override
        public void onStart(final Observation.Context context) {
            starts.incrementAndGet();
        }

        @Override
        public void onStop(final Observation.Context context) {
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
    void emitsObservationAroundClassicCall() throws Exception {
        // Start an in-process HTTP server and register handler for the exact host we’ll use: "localhost"
        server = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .register("localhost", "/get", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("{\"ok\":true}", ContentType.APPLICATION_JSON));
                })
                .create();
        server.start();
        final int port = server.getLocalPort();

        // Micrometer ObservationRegistry with a counting handler
        final ObservationRegistry reg = ObservationRegistry.create();
        final CountingHandler h = new CountingHandler();
        reg.observationConfig().observationHandler(h);

        // No metrics here; we only test observation start/stop
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.noneOf(ObservingOptions.MetricSet.class))
                .build();

        // Build classic client with the observation interceptor FIRST
        final HttpClientBuilder b = HttpClients.custom();
        b.addExecInterceptorFirst("span", new ObservationClassicExecInterceptor(reg, opts));

        final HttpHost target = new HttpHost("http", "localhost", port);

        try (final CloseableHttpClient c = b.build()) {
            final ClassicHttpResponse resp = c.executeOpen(
                    target,
                    ClassicRequestBuilder.get("/get").build(),
                    null);
            assertEquals(200, resp.getCode());
            resp.close();
        }

        // Exactly one observation around the request
        assertEquals(1, h.starts.get(), "observation should start once");
        assertEquals(1, h.stops.get(), "observation should stop once");
    }
}
