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
package org.apache.hc.client5.http.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.EnumSet;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cache.CachingHttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.observation.impl.MeteredAsyncConnectionManager;
import org.apache.hc.client5.http.observation.impl.MeteredConnectionManager;
import org.apache.hc.client5.http.observation.impl.MeteredDnsResolver;
import org.apache.hc.client5.http.observation.impl.MeteredTlsStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpClientObservationSupportTest {

    private HttpServer server;

    private static final class NoopDnsResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(final String host) {
            return new InetAddress[0];
        }

        @Override
        public String resolveCanonicalHostname(final String host) {
            return host;
        }
    }

    private static final class NoopTlsStrategy implements TlsStrategy {
        @Override
        public void upgrade(final TransportSecurityLayer sessionLayer,
                            final NamedEndpoint endpoint,
                            final Object attachment,
                            final Timeout handshakeTimeout,
                            final FutureCallback<TransportSecurityLayer> callback) {
            if (callback != null) {
                callback.completed(sessionLayer);
            }
        }

        @Deprecated
        @Override
        public boolean upgrade(final TransportSecurityLayer sessionLayer,
                               final HttpHost host,
                               final SocketAddress localAddress,
                               final SocketAddress remoteAddress,
                               final Object attachment,
                               final Timeout handshakeTimeout) {
            return true;
        }
    }

    @AfterEach
    void shutDown() {
        if (this.server != null) {
            this.server.close(CloseMode.GRACEFUL);
        }
    }

    @Test
    void basicIoAndPoolMetricsRecorded() throws Exception {
        // Register handler FOR THE HOST WE'LL USE ("localhost")
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

        final MeterRegistry meters = new SimpleMeterRegistry();
        final ObservationRegistry observations = ObservationRegistry.create();

        final MetricConfig mc = MetricConfig.builder()
                .prefix("it")
                .percentiles(0.95, 0.99)
                .build();

        final HttpClientConnectionManager cm =
                PoolingHttpClientConnectionManagerBuilder.create().build();

        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(
                        ObservingOptions.MetricSet.BASIC,
                        ObservingOptions.MetricSet.IO,
                        ObservingOptions.MetricSet.CONN_POOL))
                .tagLevel(ObservingOptions.TagLevel.LOW)
                .build();

        final HttpClientBuilder b = HttpClients.custom().setConnectionManager(cm);
        HttpClientObservationSupport.enable(b, observations, meters, opts, mc);

        // IMPORTANT: scheme-first ctor + RELATIVE PATH to avoid 421
        final HttpHost target = new HttpHost("http", "localhost", port);

        try (final CloseableHttpClient client = b.build()) {
            final ClassicHttpResponse resp = client.executeOpen(
                    target,
                    ClassicRequestBuilder.get("/get").build(),
                    null);
            assertEquals(200, resp.getCode());
            resp.close();
        } finally {
            server.stop();
        }

        // BASIC
        assertNotNull(meters.find(mc.prefix + ".request").timer());
        assertNotNull(meters.find(mc.prefix + ".response").counter());
        // IO
        assertNotNull(meters.find(mc.prefix + ".response.bytes").counter());
        // POOL
        assertNotNull(meters.find(mc.prefix + ".pool.leased").gauge());
        assertNotNull(meters.find(mc.prefix + ".pool.available").gauge());
        assertNotNull(meters.find(mc.prefix + ".pool.pending").gauge());
    }

    @Test
    void meteredHelpersRespectMetricSets() {
        final MeterRegistry meters = new SimpleMeterRegistry();
        final ObservingOptions noDnsOrTls = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.BASIC))
                .build();
        final ObservingOptions dnsAndTls = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.DNS, ObservingOptions.MetricSet.TLS, ObservingOptions.MetricSet.CONN_POOL))
                .build();

        final DnsResolver dns = new NoopDnsResolver();
        final DnsResolver dnsSame = HttpClientObservationSupport.meteredDnsResolver(dns, meters, noDnsOrTls, MetricConfig.DEFAULT);
        assertSame(dns, dnsSame);

        final DnsResolver dnsWrapped = HttpClientObservationSupport.meteredDnsResolver(dns, meters, dnsAndTls, MetricConfig.DEFAULT);
        assertTrue(dnsWrapped instanceof MeteredDnsResolver);

        final TlsStrategy tls = new NoopTlsStrategy();
        final TlsStrategy tlsSame = HttpClientObservationSupport.meteredTlsStrategy(tls, meters, noDnsOrTls, MetricConfig.DEFAULT);
        assertSame(tls, tlsSame);

        final TlsStrategy tlsWrapped = HttpClientObservationSupport.meteredTlsStrategy(tls, meters, dnsAndTls, MetricConfig.DEFAULT);
        assertTrue(tlsWrapped instanceof MeteredTlsStrategy);

        final HttpClientConnectionManager rawClassic = PoolingHttpClientConnectionManagerBuilder.create().build();
        final HttpClientConnectionManager classicSame =
                HttpClientObservationSupport.meteredConnectionManager(rawClassic, meters, noDnsOrTls, MetricConfig.DEFAULT);
        assertSame(rawClassic, classicSame);

        final HttpClientConnectionManager classicWrapped =
                HttpClientObservationSupport.meteredConnectionManager(rawClassic, meters, dnsAndTls, MetricConfig.DEFAULT);
        assertTrue(classicWrapped instanceof MeteredConnectionManager);

        final AsyncClientConnectionManager rawAsync = PoolingAsyncClientConnectionManagerBuilder.create().build();
        final AsyncClientConnectionManager asyncSame =
                HttpClientObservationSupport.meteredAsyncConnectionManager(rawAsync, meters, noDnsOrTls, MetricConfig.DEFAULT);
        assertSame(rawAsync, asyncSame);

        final AsyncClientConnectionManager asyncWrapped =
                HttpClientObservationSupport.meteredAsyncConnectionManager(rawAsync, meters, dnsAndTls, MetricConfig.DEFAULT);
        assertTrue(asyncWrapped instanceof MeteredAsyncConnectionManager);
    }

    @Test
    void cachingAsyncRegistersConnPoolMeters() {
        final MeterRegistry meters = new SimpleMeterRegistry();
        final ObservationRegistry observations = ObservationRegistry.create();
        final MetricConfig mc = MetricConfig.builder().prefix("async").build();
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.CONN_POOL))
                .build();

        final AsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
        final CachingHttpAsyncClientBuilder builder = CachingHttpAsyncClientBuilder.create();
        builder.setConnectionManager(cm);

        HttpClientObservationSupport.enable(builder, observations, meters, opts, mc);

        assertNotNull(meters.find(mc.prefix + ".pool.leased").gauge());
        assertNotNull(meters.find(mc.prefix + ".pool.available").gauge());
        assertNotNull(meters.find(mc.prefix + ".pool.pending").gauge());
    }
}
