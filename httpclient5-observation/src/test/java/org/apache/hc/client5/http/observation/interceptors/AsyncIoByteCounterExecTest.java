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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class AsyncIoByteCounterExecTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close(CloseMode.GRACEFUL);
        }
    }

    @Test
    void countsAsyncRequestAndResponseBytes() throws Exception {
        // Local classic server providing fixed-size response
        server = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .register("localhost", "/post", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("OK!", ContentType.TEXT_PLAIN)); // known size = 3
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

        final HttpAsyncClientBuilder b = HttpAsyncClients.custom();
        // Attach the async IO byte counter interceptor under test
        b.addExecInterceptorFirst("io", new AsyncIoByteCounterExec(meters, opts, mc));

        try (final CloseableHttpAsyncClient client = b.build()) {
            client.start();

            final String url = "http://localhost:" + port + "/post";
            final SimpleHttpRequest req = SimpleRequestBuilder.post(url)
                    .setBody("HELLO", ContentType.TEXT_PLAIN) // known request size = 5
                    .build();

            final Future<SimpleHttpResponse> fut = client.execute(req, null);
            final SimpleHttpResponse rsp = fut.get(20, TimeUnit.SECONDS);
            assertEquals(200, rsp.getCode());
        } finally {
            server.stop();
        }

        assertNotNull(meters.find(mc.prefix + ".request.bytes").counter());
        assertTrue(meters.find(mc.prefix + ".request.bytes").counter().count() > 0.0);

        assertNotNull(meters.find(mc.prefix + ".response.bytes").counter());
        assertTrue(meters.find(mc.prefix + ".response.bytes").counter().count() > 0.0);
    }
}
