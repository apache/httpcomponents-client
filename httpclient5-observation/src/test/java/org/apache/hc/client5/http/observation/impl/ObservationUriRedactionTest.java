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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.client5.http.observation.interceptors.AsyncIoByteCounterExec;
import org.apache.hc.client5.http.observation.interceptors.IoByteCounterExec;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservationUriRedactionTest {

    private static final String SECRET_URI = "user:secret@127.0.0.1:%d/path?token=SECRET#frag";

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))
                .setListenerPort(0)
                .register("127.0.0.1", "/path", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("ACK", ContentType.TEXT_PLAIN));
                })
                .create();
        server.start();
        port = server.getLocalPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close(CloseMode.GRACEFUL);
        }
    }

    private static final class NameCapture implements ObservationHandler<Observation.Context> {
        final AtomicReference<String> name = new AtomicReference<>();

        @Override
        public boolean supportsContext(final Observation.Context context) {
            return true;
        }

        @Override
        public void onStart(final Observation.Context context) {
            name.set(context.getContextualName());
        }
    }

    private static ObservingOptions spanOnly() {
        return ObservingOptions.builder()
                .metrics(EnumSet.noneOf(ObservingOptions.MetricSet.class))
                .build();
    }

    private String classicSpanName(final String uri, final boolean rejected) throws Exception {
        final ObservationRegistry reg = ObservationRegistry.create();
        final NameCapture capture = new NameCapture();
        reg.observationConfig().observationHandler(capture);
        try (final CloseableHttpClient client = HttpClients.custom()
                .addExecInterceptorFirst("span", new ObservationClassicExecInterceptor(reg, spanOnly()))
                .build()) {
            execute(client, new HttpGet(uri), rejected);
        }
        return capture.name.get();
    }

    private String asyncSpanName(final String uri, final boolean rejected) throws Exception {
        final ObservationRegistry reg = ObservationRegistry.create();
        final NameCapture capture = new NameCapture();
        reg.observationConfig().observationHandler(capture);
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .addExecInterceptorFirst("span", new ObservationAsyncExecInterceptor(reg, spanOnly()))
                .build()) {
            client.start();
            final SimpleHttpRequest req = SimpleHttpRequest.create("GET", URI.create(uri));
            execute(client, req, rejected);
        }
        return capture.name.get();
    }

    private String classicUriTag(final String uri) throws Exception {
        final MeterRegistry meters = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("test").perUriIo(true).build();
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.IO))
                .tagLevel(ObservingOptions.TagLevel.LOW)
                .build();
        try (final CloseableHttpClient client = HttpClients.custom()
                .addExecInterceptorFirst("io",
                        new IoByteCounterExec(meters, opts, mc))
                .build()) {
            execute(client, ClassicRequestBuilder.post(uri)
                    .setEntity(new StringEntity("HELLO", ContentType.TEXT_PLAIN)).build(), false);
        }
        return uriTag(meters, mc);
    }

    private String asyncUriTag(final String uri) throws Exception {
        final MeterRegistry meters = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("test").perUriIo(true).build();
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.IO))
                .tagLevel(ObservingOptions.TagLevel.LOW)
                .build();
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .addExecInterceptorFirst("io",
                        new AsyncIoByteCounterExec(meters, opts, mc))
                .build()) {
            client.start();
            execute(client, SimpleRequestBuilder.post(uri).setBody("HELLO", ContentType.TEXT_PLAIN).build(), false);
        }
        return uriTag(meters, mc);
    }

    // The client rejects userinfo downstream of the interceptors; the observation has already started by then.
    private static void execute(
            final CloseableHttpClient client, final ClassicHttpRequest request, final boolean rejected) throws Exception {
        if (rejected) {
            assertThrows(ClientProtocolException.class, () -> client.executeOpen(null, request, null).close());
        } else {
            try (final ClassicHttpResponse rsp = client.executeOpen(null, request, null)) {
                assertEquals(HttpStatus.SC_OK, rsp.getCode());
            }
        }
    }

    private static void execute(
            final CloseableHttpAsyncClient client, final SimpleHttpRequest request, final boolean rejected) throws Exception {
        if (rejected) {
            final ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> client.execute(request, null).get(10, TimeUnit.SECONDS));
            assertInstanceOf(ProtocolException.class, ex.getCause());
        } else {
            assertEquals(HttpStatus.SC_OK, client.execute(request, null).get(10, TimeUnit.SECONDS).getCode());
        }
    }

    private static String uriTag(final MeterRegistry meters, final MetricConfig mc) {
        final Counter counter = meters.find(mc.prefix + ".request.bytes").counter();
        assertNotNull(counter);
        final String tag = counter.getId().getTag("uri");
        assertNotNull(tag);
        return tag;
    }

    @Test
    void classicSpanNameExcludesUserInfoQueryAndFragment() throws Exception {
        assertEquals("GET http://127.0.0.1:" + port + "/path",
                classicSpanName("http://" + String.format(SECRET_URI, port), true));
    }

    @Test
    void asyncSpanNameExcludesUserInfoQueryAndFragment() throws Exception {
        assertEquals("GET /path", asyncSpanName("http://" + String.format(SECRET_URI, port), true));
    }

    @Test
    void classicUriTagExcludesQuery() throws Exception {
        assertEquals("/path", classicUriTag("http://127.0.0.1:" + port + "/path?token=SECRET#frag"));
    }

    @Test
    void asyncUriTagExcludesQuery() throws Exception {
        assertEquals("/path", asyncUriTag("http://127.0.0.1:" + port + "/path?token=SECRET#frag"));
    }

    @Test
    void plainUrlKeepsNameAndTag() throws Exception {
        final String url = "http://127.0.0.1:" + port + "/path";
        assertEquals("GET http://127.0.0.1:" + port + "/path", classicSpanName(url, false));
        assertEquals("GET /path", asyncSpanName(url, false));
        assertEquals("/path", classicUriTag(url));
        assertEquals("/path", asyncUriTag(url));
    }
}
