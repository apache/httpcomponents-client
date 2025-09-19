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

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.Metrics;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;

import org.apache.hc.client5.http.observation.impl.MeteredDnsResolver;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.core5.http.HttpHost;

public final class DnsMetricsDemo {

    public static void main(final String[] args) throws Exception {
        // 1) Prometheus registry
        final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(registry);

        // 2) Observation (no tracer here)
        final ObservationRegistry observations = ObservationRegistry.create();

        // 3) Metric knobs
        final MetricConfig mc = MetricConfig.builder()
                .prefix("demo")
                .percentiles(1)
                .build();

        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(
                        ObservingOptions.MetricSet.BASIC,
                        ObservingOptions.MetricSet.IO,
                        ObservingOptions.MetricSet.CONN_POOL,
                        ObservingOptions.MetricSet.DNS         // <-- we’ll wrap DNS
                ))
                .tagLevel(ObservingOptions.TagLevel.EXTENDED)
                .build();

        // 4) Classic client + real DNS resolver wrapped with metrics
        final MeteredDnsResolver meteredResolver =
                new MeteredDnsResolver(SystemDefaultDnsResolver.INSTANCE, registry, mc, opts);

        final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(meteredResolver)
                .build();

        final HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(cm);

        // record http timers/counters + pool gauges
        HttpClientObservationSupport.enable(builder, observations, registry, opts, mc);

        try (final CloseableHttpClient client = builder.build()) {
            // Use a target so Host header is correct and connection manager engages normally
            final HttpHost target = new HttpHost("http", "httpbin.org", 80);
            final ClassicHttpResponse rsp = client.executeOpen(
                    target,
                    ClassicRequestBuilder.get("/get").build(),
                    null);
            System.out.println("[classic DNS]   " + rsp.getCode());
            rsp.close();
        }

        System.out.println("\n--- Prometheus scrape (DNS demo) ---");
        System.out.println(registry.scrape());
    }

    private DnsMetricsDemo() { }
}
