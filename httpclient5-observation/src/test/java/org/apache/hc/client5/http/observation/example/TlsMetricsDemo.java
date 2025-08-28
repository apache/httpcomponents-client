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
package org.apache.hc.client5.http.observation.example;

import java.util.EnumSet;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.client5.http.observation.impl.MeteredTlsStrategy;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;

public final class TlsMetricsDemo {

    public static void main(final String[] args) throws Exception {
        // 1) Prometheus registry
        final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(registry);

        // 2) Observation (plain – add tracing bridge if you want spans)
        final ObservationRegistry observations = ObservationRegistry.create();

        // 3) Metric knobs
        final MetricConfig mc = MetricConfig.builder()
                .prefix("demo")
                .percentiles(2) // p90 + p99
                .build();

        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(
                        ObservingOptions.MetricSet.BASIC,
                        ObservingOptions.MetricSet.IO,
                        ObservingOptions.MetricSet.TLS      // we will record TLS starts/failures
                ))
                .tagLevel(ObservingOptions.TagLevel.EXTENDED)
                .build();

        // 4) Build a CM with a metered TLS strategy, then give it to the async builder
        final TlsStrategy realTls = ClientTlsStrategyBuilder.create().buildAsync();
        final TlsStrategy meteredTls = new MeteredTlsStrategy(realTls, registry, mc, opts);

        // TLS strategy goes on the *connection manager* (not on the builder)
        final org.apache.hc.client5.http.nio.AsyncClientConnectionManager cm =
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .setTlsStrategy(meteredTls)
                        .build();

        final HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                .setConnectionManager(cm);

        // Enable HTTP metrics (timers/counters, IO, etc.)
        HttpClientObservationSupport.enable(builder, observations, registry, opts, mc);

        // 5) Run a real HTTPS request
        try (final CloseableHttpAsyncClient client = builder.build()) {
            client.start();

            final SimpleHttpRequest req = SimpleRequestBuilder.get("https://httpbin.org/get").build();
            final Future<SimpleHttpResponse> fut = client.execute(req, null);
            final SimpleHttpResponse rsp = fut.get(30, TimeUnit.SECONDS);

            System.out.println("[async TLS]     " + rsp.getCode());
        }

        System.out.println("\n--- Prometheus scrape (TLS demo) ---");
        System.out.println(registry.scrape());
    }

    private TlsMetricsDemo() {
    }
}
