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
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentLengthExecTest {

    private static final String REQUEST = "test.request.content.length";
    private static final String RESPONSE = "test.response.content.length";

    private HttpServer server;
    private HttpHost target;
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
        target = new HttpHost("http", "localhost", server.getLocalPort());
        meters = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close(CloseMode.GRACEFUL);
        }
    }

    private static ObservingOptions options(final ObservingOptions.TagLevel tagLevel) {
        return ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.CONTENT_LENGTH))
                .tagLevel(tagLevel)
                .build();
    }

    private static MetricConfig config() {
        return MetricConfig.builder().prefix("test").percentiles().build();
    }

    private void execute(final ObservingOptions opts, final MetricConfig mc,
                         final HttpHost host, final ClassicHttpRequest request) throws IOException {
        final HttpClientBuilder builder = HttpClients.custom().disableAutomaticRetries();
        builder.addExecInterceptorFirst("content-length", new ContentLengthExec(meters, opts, mc));
        try (final CloseableHttpClient client = builder.build()) {
            client.execute(host, request, response -> {
                EntityUtils.consume(response.getEntity());
                return null;
            });
        }
    }

    @Test
    void recordsKnownRequestAndResponseLengths() throws Exception {
        execute(options(ObservingOptions.TagLevel.LOW), config(), target,
                ClassicRequestBuilder.post("/known")
                        .setEntity(new StringEntity("HELLO", ContentType.TEXT_PLAIN))
                        .build());

        final DistributionSummary req = meters.find(REQUEST).tag("method", "POST").tag("status", "200").summary();
        assertNotNull(req);
        assertEquals(1, req.count());
        assertEquals(5.0, req.totalAmount());
        assertEquals("bytes", req.getId().getBaseUnit());

        final DistributionSummary rsp = meters.find(RESPONSE).tag("method", "POST").tag("status", "200").summary();
        assertNotNull(rsp);
        assertEquals(1, rsp.count());
        assertEquals(3.0, rsp.totalAmount());

        assertNull(meters.find(REQUEST).tagKeys("protocol").summary());
        assertNull(meters.find(REQUEST).tagKeys("uri").summary());
    }

    @Test
    void recordsNothingForChunkedBodies() throws Exception {
        execute(options(ObservingOptions.TagLevel.LOW), config(), target,
                ClassicRequestBuilder.post("/chunked")
                        .setEntity(new InputStreamEntity(
                                new ByteArrayInputStream("HELLO".getBytes(StandardCharsets.US_ASCII)), -1,
                                ContentType.TEXT_PLAIN))
                        .build());

        assertNull(meters.find(REQUEST).summary());
        assertNull(meters.find(RESPONSE).summary());
    }

    @Test
    void recordsNothingWithoutEntity() throws Exception {
        execute(options(ObservingOptions.TagLevel.LOW), config(), target,
                ClassicRequestBuilder.get("/empty").build());

        assertNull(meters.find(REQUEST).summary());
        assertNull(meters.find(RESPONSE).summary());
    }

    @Test
    void recordsResponseOnlyForGet() throws Exception {
        execute(options(ObservingOptions.TagLevel.LOW), config(), target,
                ClassicRequestBuilder.get("/known").build());

        assertNull(meters.find(REQUEST).summary());
        final DistributionSummary rsp = meters.find(RESPONSE).tag("method", "GET").summary();
        assertNotNull(rsp);
        assertEquals(3.0, rsp.totalAmount());
    }

    @Test
    void recordsErrorResponsesWithStatusTag() throws Exception {
        execute(options(ObservingOptions.TagLevel.LOW), config(), target,
                ClassicRequestBuilder.post("/error")
                        .setEntity(new StringEntity("HELLO", ContentType.TEXT_PLAIN))
                        .build());

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
        final HttpHost unreachable = new HttpHost("http", "localhost", closedPort);

        assertThrows(IOException.class, () -> execute(options(ObservingOptions.TagLevel.LOW), config(), unreachable,
                ClassicRequestBuilder.post("/known")
                        .setEntity(new StringEntity("HELLO", ContentType.TEXT_PLAIN))
                        .build()));

        assertNull(meters.find(REQUEST).summary());
        assertNull(meters.find(RESPONSE).summary());
    }

    @Test
    void extendedTagsAndCommonTags() throws Exception {
        final MetricConfig mc = MetricConfig.builder().prefix("test").addCommonTag("app", "demo").build();
        execute(options(ObservingOptions.TagLevel.EXTENDED), mc, target,
                ClassicRequestBuilder.post("/known")
                        .setEntity(new StringEntity("HELLO", ContentType.TEXT_PLAIN))
                        .build());

        final DistributionSummary req = meters.find(REQUEST)
                .tag("protocol", "http")
                .tag("target", "localhost")
                .tag("app", "demo")
                .summary();
        assertNotNull(req);
        assertEquals(5.0, req.totalAmount());
    }

    @Test
    void skipsUnsampledRequests() throws Exception {
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.CONTENT_LENGTH))
                .spanSampling(uri -> false)
                .build();
        execute(opts, config(), target,
                ClassicRequestBuilder.post("/known")
                        .setEntity(new StringEntity("HELLO", ContentType.TEXT_PLAIN))
                        .build());

        assertNull(meters.find(REQUEST).summary());
        assertNull(meters.find(RESPONSE).summary());
    }

    @Test
    void enabledOnlyViaContentLengthMetricSet() throws Exception {
        final MeterRegistry defaultMeters = new SimpleMeterRegistry();
        final HttpClientBuilder defaults = HttpClients.custom();
        HttpClientObservationSupport.enable(defaults, null, defaultMeters, ObservingOptions.DEFAULT);
        try (final CloseableHttpClient client = defaults.build()) {
            client.execute(target, ClassicRequestBuilder.post("/known")
                    .setEntity(new StringEntity("HELLO", ContentType.TEXT_PLAIN))
                    .build(), response -> null);
        }
        assertNull(defaultMeters.find("http.client.request.content.length").summary());

        final HttpClientBuilder optedIn = HttpClients.custom();
        HttpClientObservationSupport.enable(optedIn, null, meters, options(ObservingOptions.TagLevel.LOW));
        try (final CloseableHttpClient client = optedIn.build()) {
            client.execute(target, ClassicRequestBuilder.post("/known")
                    .setEntity(new StringEntity("HELLO", ContentType.TEXT_PLAIN))
                    .build(), response -> null);
        }
        assertNotNull(meters.find("http.client.request.content.length").summary());
        assertNotNull(meters.find("http.client.response.content.length").summary());
        assertNull(meters.find("http.client.request.bytes").counter());
    }

}
