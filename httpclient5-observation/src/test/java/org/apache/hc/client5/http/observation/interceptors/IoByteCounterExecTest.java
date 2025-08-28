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
package org.apache.hc.client5.http.observation.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.observation.MetricConfig;
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

public class IoByteCounterExecTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close(CloseMode.GRACEFUL);
        }
    }

    @Test
    void countsRequestAndResponseBytes() throws Exception {
        // Echo-like handler with known response size
        server = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .register("localhost", "/echo", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    // Fixed-size body so response length is known
                    response.setEntity(new StringEntity("ACK", ContentType.TEXT_PLAIN));
                })
                .create();
        server.start();
        final int port = server.getLocalPort();

        final MeterRegistry meters = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("test").perUriIo(true).build();
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.IO))
                .tagLevel(ObservingOptions.TagLevel.LOW)
                .build();

        final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create().build();
        final HttpClientBuilder b = HttpClients.custom().setConnectionManager(cm);

        // Attach the IO byte counter interceptor under test
        b.addExecInterceptorFirst("io", new IoByteCounterExec(meters, opts, mc));

        final HttpHost target = new HttpHost("http", "localhost", port);
        try (final CloseableHttpClient client = b.build()) {
            // Send a known-size request body (so request.bytes can increment)
            final StringEntity body = new StringEntity("HELLO", ContentType.TEXT_PLAIN);
            final ClassicHttpResponse resp = client.executeOpen(
                    target, ClassicRequestBuilder.post("/echo").setEntity(body).build(), null);
            assertEquals(200, resp.getCode());
            resp.close();
        } finally {
            server.stop();
        }

        assertNotNull(meters.find(mc.prefix + ".request.bytes").counter());
        assertTrue(meters.find(mc.prefix + ".request.bytes").counter().count() > 0.0);

        assertNotNull(meters.find(mc.prefix + ".response.bytes").counter());
        assertTrue(meters.find(mc.prefix + ".response.bytes").counter().count() > 0.0);
    }
}
