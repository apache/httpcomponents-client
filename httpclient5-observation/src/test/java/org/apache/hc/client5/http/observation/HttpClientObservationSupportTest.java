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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cache.CachingHttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.cache.CachingHttpClientBuilder;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
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
        assertInstanceOf(MeteredAsyncConnectionManager.class, builder.getConnManager());
    }

    private static final ObservingOptions CONN_POOL_ONLY = ObservingOptions.builder()
            .metrics(EnumSet.of(ObservingOptions.MetricSet.CONN_POOL))
            .build();

    private static final MetricConfig POOL_MC = MetricConfig.builder().prefix("pool-it").build();

    private static final class CloseTrackingPoolingConnectionManager extends PoolingHttpClientConnectionManager {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            closed.set(true);
            super.close();
        }

        @Override
        public void close(final CloseMode closeMode) {
            closed.set(true);
            super.close(closeMode);
        }
    }

    private static final class CloseTrackingPoolingAsyncConnectionManager extends PoolingAsyncClientConnectionManager {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            closed.set(true);
            super.close();
        }

        @Override
        public void close(final CloseMode closeMode) {
            closed.set(true);
            super.close(closeMode);
        }
    }

    private HttpHost startServer() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setListenerPort(0)
                .register("localhost", "/get", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("{\"ok\":true}", ContentType.APPLICATION_JSON));
                })
                .create();
        server.start();
        return new HttpHost("http", "localhost", server.getLocalPort());
    }

    private static void executeClassic(final HttpClientBuilder builder, final HttpHost target) throws Exception {
        try (final CloseableHttpClient client = builder.build()) {
            try (final ClassicHttpResponse resp = client.executeOpen(target, ClassicRequestBuilder.get("/get").build(), null)) {
                assertEquals(200, resp.getCode());
            }
        }
    }

    private static void executeAsync(final HttpAsyncClientBuilder builder, final HttpHost target) throws Exception {
        try (final CloseableHttpAsyncClient client = builder.build()) {
            client.start();
            final SimpleHttpResponse resp = client.execute(
                    SimpleRequestBuilder.get().setHttpHost(target).setPath("/get").build(), null)
                    .get(30, TimeUnit.SECONDS);
            assertEquals(200, resp.getCode());
        }
    }

    private static void assertPoolMetricsRecorded(final MeterRegistry meters, final long expectedLeases) {
        final Timer lease = meters.find(POOL_MC.prefix + ".pool.lease").tag("result", "ok").timer();
        assertNotNull(lease);
        assertEquals(expectedLeases, lease.count());
        final Counter leases = meters.find(POOL_MC.prefix + ".pool.leases").tag("result", "ok").counter();
        assertNotNull(leases);
        assertEquals(expectedLeases, (long) leases.count());
        assertNotNull(meters.find(POOL_MC.prefix + ".pool.leased").gauge());
        assertNotNull(meters.find(POOL_MC.prefix + ".pool.available").gauge());
        assertNotNull(meters.find(POOL_MC.prefix + ".pool.pending").gauge());
    }

    private static void assertNoPoolMetrics(final MeterRegistry meters) {
        assertNull(meters.find(POOL_MC.prefix + ".pool.lease").timer());
        assertNull(meters.find(POOL_MC.prefix + ".pool.leases").counter());
        assertNull(meters.find(POOL_MC.prefix + ".pool.leased").gauge());
        assertNull(meters.find(POOL_MC.prefix + ".pool.available").gauge());
        assertNull(meters.find(POOL_MC.prefix + ".pool.pending").gauge());
    }

    @Test
    void classicEnableWrapsConnectionManagerAndRecordsPoolMetrics() throws Exception {
        final HttpHost target = startServer();
        final MeterRegistry meters = new SimpleMeterRegistry();
        final HttpClientBuilder b = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().build());

        HttpClientObservationSupport.enable(b, null, meters, CONN_POOL_ONLY, POOL_MC);

        assertInstanceOf(MeteredConnectionManager.class, b.getConnManager());
        executeClassic(b, target);
        assertPoolMetricsRecorded(meters, 1);
    }

    @Test
    void asyncEnableWrapsConnectionManagerAndRecordsPoolMetrics() throws Exception {
        final HttpHost target = startServer();
        final MeterRegistry meters = new SimpleMeterRegistry();
        final HttpAsyncClientBuilder b = HttpAsyncClients.custom()
                .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create().build());

        HttpClientObservationSupport.enable(b, null, meters, CONN_POOL_ONLY, POOL_MC);

        assertInstanceOf(MeteredAsyncConnectionManager.class, b.getConnManager());
        executeAsync(b, target);
        assertPoolMetricsRecorded(meters, 1);
    }

    @Test
    void cachingClassicEnableWrapsConnectionManager() {
        final MeterRegistry meters = new SimpleMeterRegistry();
        final CachingHttpClientBuilder b = CachingHttpClientBuilder.create();
        b.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().build());

        HttpClientObservationSupport.enable(b, null, meters, CONN_POOL_ONLY, POOL_MC);

        assertInstanceOf(MeteredConnectionManager.class, b.getConnManager());
        assertNotNull(meters.find(POOL_MC.prefix + ".pool.leased").gauge());
    }

    @Test
    void classicEnableTwiceDoesNotDoubleWrap() throws Exception {
        final HttpHost target = startServer();
        final MeterRegistry meters = new SimpleMeterRegistry();
        final HttpClientBuilder b = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().build());

        HttpClientObservationSupport.enable(b, null, meters, CONN_POOL_ONLY, POOL_MC);
        final HttpClientConnectionManager first = b.getConnManager();
        HttpClientObservationSupport.enable(b, null, meters, CONN_POOL_ONLY, POOL_MC);

        assertSame(first, b.getConnManager());
        executeClassic(b, target);
        assertPoolMetricsRecorded(meters, 1);
    }

    @Test
    void asyncEnableTwiceDoesNotDoubleWrap() throws Exception {
        final HttpHost target = startServer();
        final MeterRegistry meters = new SimpleMeterRegistry();
        final HttpAsyncClientBuilder b = HttpAsyncClients.custom()
                .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create().build());

        HttpClientObservationSupport.enable(b, null, meters, CONN_POOL_ONLY, POOL_MC);
        final AsyncClientConnectionManager first = b.getConnManager();
        HttpClientObservationSupport.enable(b, null, meters, CONN_POOL_ONLY, POOL_MC);

        assertSame(first, b.getConnManager());
        executeAsync(b, target);
        assertPoolMetricsRecorded(meters, 1);
    }

    @Test
    void explicitlyMeteredConnectionManagersAreKept() {
        final MeterRegistry meters = new SimpleMeterRegistry();

        final HttpClientConnectionManager classic = HttpClientObservationSupport.meteredConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create().build(), meters, CONN_POOL_ONLY, POOL_MC);
        final HttpClientBuilder classicBuilder = HttpClients.custom().setConnectionManager(classic);
        HttpClientObservationSupport.enable(classicBuilder, null, meters, CONN_POOL_ONLY, POOL_MC);
        assertSame(classic, classicBuilder.getConnManager());

        final AsyncClientConnectionManager async = HttpClientObservationSupport.meteredAsyncConnectionManager(
                PoolingAsyncClientConnectionManagerBuilder.create().build(), meters, CONN_POOL_ONLY, POOL_MC);
        final HttpAsyncClientBuilder asyncBuilder = HttpAsyncClients.custom().setConnectionManager(async);
        HttpClientObservationSupport.enable(asyncBuilder, null, meters, CONN_POOL_ONLY, POOL_MC);
        assertSame(async, asyncBuilder.getConnManager());

        assertNotNull(meters.find(POOL_MC.prefix + ".pool.leased").gauge());
    }

    @Test
    void enableWithoutConnectionManagerInstallsNoPoolMetrics() throws Exception {
        final HttpHost target = startServer();
        final MeterRegistry meters = new SimpleMeterRegistry();

        final HttpClientBuilder classicBuilder = HttpClients.custom();
        HttpClientObservationSupport.enable(classicBuilder, null, meters, CONN_POOL_ONLY, POOL_MC);
        assertNull(classicBuilder.getConnManager());
        executeClassic(classicBuilder, target);

        final HttpAsyncClientBuilder asyncBuilder = HttpAsyncClients.custom();
        HttpClientObservationSupport.enable(asyncBuilder, null, meters, CONN_POOL_ONLY, POOL_MC);
        assertNull(asyncBuilder.getConnManager());
        executeAsync(asyncBuilder, target);

        assertNoPoolMetrics(meters);
    }

    @Test
    void connPoolDisabledLeavesConnectionManagerUnchanged() throws Exception {
        final HttpHost target = startServer();
        final MeterRegistry meters = new SimpleMeterRegistry();
        final ObservingOptions basicOnly = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.BASIC))
                .build();

        final HttpClientConnectionManager classic = PoolingHttpClientConnectionManagerBuilder.create().build();
        final HttpClientBuilder classicBuilder = HttpClients.custom().setConnectionManager(classic);
        HttpClientObservationSupport.enable(classicBuilder, null, meters, basicOnly, POOL_MC);
        assertSame(classic, classicBuilder.getConnManager());
        executeClassic(classicBuilder, target);

        final AsyncClientConnectionManager async = PoolingAsyncClientConnectionManagerBuilder.create().build();
        final HttpAsyncClientBuilder asyncBuilder = HttpAsyncClients.custom().setConnectionManager(async);
        HttpClientObservationSupport.enable(asyncBuilder, null, meters, basicOnly, POOL_MC);
        assertSame(async, asyncBuilder.getConnManager());
        executeAsync(asyncBuilder, target);

        assertNoPoolMetrics(meters);
    }

    @Test
    void nonPoolingConnectionManagerIsWrappedWithoutGauges() {
        final MeterRegistry meters = new SimpleMeterRegistry();
        final HttpClientBuilder b = HttpClients.custom()
                .setConnectionManager(new BasicHttpClientConnectionManager());

        HttpClientObservationSupport.enable(b, null, meters, CONN_POOL_ONLY, POOL_MC);

        assertInstanceOf(MeteredConnectionManager.class, b.getConnManager());
        assertNull(meters.find(POOL_MC.prefix + ".pool.leased").gauge());
    }

    @Test
    void classicSharedConnectionManagerFlagIsPreserved() throws Exception {
        final HttpHost target = startServer();
        final MeterRegistry meters = new SimpleMeterRegistry();

        final CloseTrackingPoolingConnectionManager shared = new CloseTrackingPoolingConnectionManager();
        final HttpClientBuilder sharedBuilder = HttpClients.custom()
                .setConnectionManager(shared)
                .setConnectionManagerShared(true);
        HttpClientObservationSupport.enable(sharedBuilder, null, meters, CONN_POOL_ONLY, POOL_MC);
        executeClassic(sharedBuilder, target);
        assertFalse(shared.closed.get());
        shared.close();

        final CloseTrackingPoolingConnectionManager owned = new CloseTrackingPoolingConnectionManager();
        final HttpClientBuilder ownedBuilder = HttpClients.custom().setConnectionManager(owned);
        HttpClientObservationSupport.enable(ownedBuilder, null, meters, CONN_POOL_ONLY, POOL_MC);
        executeClassic(ownedBuilder, target);
        assertTrue(owned.closed.get());
    }

    @Test
    void asyncSharedConnectionManagerFlagIsPreserved() throws Exception {
        final HttpHost target = startServer();
        final MeterRegistry meters = new SimpleMeterRegistry();

        final CloseTrackingPoolingAsyncConnectionManager shared = new CloseTrackingPoolingAsyncConnectionManager();
        final HttpAsyncClientBuilder sharedBuilder = HttpAsyncClients.custom()
                .setConnectionManager(shared)
                .setConnectionManagerShared(true);
        HttpClientObservationSupport.enable(sharedBuilder, null, meters, CONN_POOL_ONLY, POOL_MC);
        executeAsync(sharedBuilder, target);
        assertFalse(shared.closed.get());
        shared.close();

        final CloseTrackingPoolingAsyncConnectionManager owned = new CloseTrackingPoolingAsyncConnectionManager();
        final HttpAsyncClientBuilder ownedBuilder = HttpAsyncClients.custom().setConnectionManager(owned);
        HttpClientObservationSupport.enable(ownedBuilder, null, meters, CONN_POOL_ONLY, POOL_MC);
        executeAsync(ownedBuilder, target);
        assertTrue(owned.closed.get());
    }
}
