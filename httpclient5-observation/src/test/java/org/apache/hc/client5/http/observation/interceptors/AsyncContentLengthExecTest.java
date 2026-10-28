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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncContentLengthExecTest {

    private static final String REQUEST = "test.request.content.length";
    private static final String RESPONSE = "test.response.content.length";

    private HttpServer server;
    private String baseUrl;
    private MeterRegistry meters;

    @BeforeEach
    void setUp() throws IOException {
        server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setListenerPort(0)
                .register("localhost", "/known", (request, response, context) -> {
                    EntityUtils.consume(request.getEntity());
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("OK!", ContentType.TEXT_PLAIN));
                })
                .register("localhost", "/chunked", (request, response, context) -> {
                    EntityUtils.consume(request.getEntity());
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new InputStreamEntity(
                            new ByteArrayInputStream("chunky".getBytes(StandardCharsets.US_ASCII)), -1,
                            ContentType.TEXT_PLAIN));
                })
                .register("localhost", "/empty", (request, response, context) ->
                        response.setCode(HttpStatus.SC_NO_CONTENT))
                .register("localhost", "/error", (request, response, context) -> {
                    EntityUtils.consume(request.getEntity());
                    response.setCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    response.setEntity(new StringEntity("Boom!!", ContentType.TEXT_PLAIN));
                })
                .create();
        server.start();
        baseUrl = "http://localhost:" + server.getLocalPort();
        meters = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close(CloseMode.GRACEFUL);
        }
    }

    private static ObservingOptions options() {
        return ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.CONTENT_LENGTH))
                .build();
    }

    private static MetricConfig config() {
        return MetricConfig.builder().prefix("test").percentiles().build();
    }

    private SimpleHttpResponse execute(final AsyncRequestProducer producer) throws Exception {
        final HttpAsyncClientBuilder builder = HttpAsyncClients.custom().disableAutomaticRetries();
        builder.addExecInterceptorFirst("content-length", new AsyncContentLengthExec(meters, options(), config()));
        try (final CloseableHttpAsyncClient client = builder.build()) {
            client.start();
            return client.execute(producer, SimpleResponseConsumer.create(), null).get(20, TimeUnit.SECONDS);
        }
    }

    private SimpleHttpResponse execute(final SimpleHttpRequest request) throws Exception {
        final HttpAsyncClientBuilder builder = HttpAsyncClients.custom().disableAutomaticRetries();
        builder.addExecInterceptorFirst("content-length", new AsyncContentLengthExec(meters, options(), config()));
        try (final CloseableHttpAsyncClient client = builder.build()) {
            client.start();
            return client.execute(request, null).get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void recordsKnownRequestAndResponseLengths() throws Exception {
        final SimpleHttpResponse response = execute(SimpleRequestBuilder.post(baseUrl + "/known")
                .setBody("HELLO", ContentType.TEXT_PLAIN)
                .build());
        assertEquals(200, response.getCode());

        final DistributionSummary req = meters.find(REQUEST).tag("method", "POST").tag("status", "200").summary();
        assertNotNull(req);
        assertEquals(1, req.count());
        assertEquals(5.0, req.totalAmount());
        assertEquals("bytes", req.getId().getBaseUnit());

        final DistributionSummary rsp = meters.find(RESPONSE).tag("method", "POST").tag("status", "200").summary();
        assertNotNull(rsp);
        assertEquals(1, rsp.count());
        assertEquals(3.0, rsp.totalAmount());
    }

    @Test
    void recordsNothingForChunkedBodies() throws Exception {
        final SimpleHttpResponse response = execute(new BasicRequestProducer(
                new BasicHttpRequest("POST", URI.create(baseUrl + "/chunked")),
                new StringAsyncEntityProducer("HELLO", ContentType.TEXT_PLAIN)));
        assertEquals(200, response.getCode());
        assertEquals("chunky", response.getBodyText());

        assertNull(meters.find(REQUEST).summary());
        assertNull(meters.find(RESPONSE).summary());
    }

    @Test
    void recordsNothingWithoutEntity() throws Exception {
        final SimpleHttpResponse response = execute(SimpleRequestBuilder.get(baseUrl + "/empty").build());
        assertEquals(204, response.getCode());

        assertNull(meters.find(REQUEST).summary());
        assertNull(meters.find(RESPONSE).summary());
    }

    @Test
    void recordsResponseOnlyForGet() throws Exception {
        execute(SimpleRequestBuilder.get(baseUrl + "/known").build());

        assertNull(meters.find(REQUEST).summary());
        final DistributionSummary rsp = meters.find(RESPONSE).tag("method", "GET").summary();
        assertNotNull(rsp);
        assertEquals(3.0, rsp.totalAmount());
    }

    @Test
    void recordsErrorResponsesWithStatusTag() throws Exception {
        final SimpleHttpResponse response = execute(SimpleRequestBuilder.post(baseUrl + "/error")
                .setBody("HELLO", ContentType.TEXT_PLAIN)
                .build());
        assertEquals(500, response.getCode());

        final DistributionSummary req = meters.find(REQUEST).tag("status", "500").summary();
        assertNotNull(req);
        assertEquals(5.0, req.totalAmount());
        final DistributionSummary rsp = meters.find(RESPONSE).tag("status", "500").summary();
        assertNotNull(rsp);
        assertEquals(6.0, rsp.totalAmount());
    }

    @Test
    void recordsNothingWhenNoResponseIsReceived() throws Exception {
        final int closedPort;
        try (final ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        assertThrows(ExecutionException.class, () -> execute(
                SimpleRequestBuilder.post("http://localhost:" + closedPort + "/known")
                        .setBody("HELLO", ContentType.TEXT_PLAIN)
                        .build()));

        assertNull(meters.find(REQUEST).summary());
        assertNull(meters.find(RESPONSE).summary());
    }

    @Test
    void enabledOnlyViaContentLengthMetricSet() throws Exception {
        final MeterRegistry defaultMeters = new SimpleMeterRegistry();
        final HttpAsyncClientBuilder defaults = HttpAsyncClients.custom();
        HttpClientObservationSupport.enable(defaults, null, defaultMeters, ObservingOptions.DEFAULT);
        try (final CloseableHttpAsyncClient client = defaults.build()) {
            client.start();
            client.execute(SimpleRequestBuilder.post(baseUrl + "/known")
                    .setBody("HELLO", ContentType.TEXT_PLAIN)
                    .build(), null).get(20, TimeUnit.SECONDS);
        }
        assertNull(defaultMeters.find("http.client.request.content.length").summary());

        final HttpAsyncClientBuilder optedIn = HttpAsyncClients.custom();
        HttpClientObservationSupport.enable(optedIn, null, meters, options());
        try (final CloseableHttpAsyncClient client = optedIn.build()) {
            client.start();
            client.execute(SimpleRequestBuilder.post(baseUrl + "/known")
                    .setBody("HELLO", ContentType.TEXT_PLAIN)
                    .build(), null).get(20, TimeUnit.SECONDS);
        }
        assertNotNull(meters.find("http.client.request.content.length").summary());
        assertNotNull(meters.find("http.client.response.content.length").summary());
    }

}
