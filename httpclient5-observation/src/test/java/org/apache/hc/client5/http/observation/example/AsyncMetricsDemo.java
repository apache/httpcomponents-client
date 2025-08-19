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

import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;

public final class AsyncMetricsDemo {

    // Use delay endpoints so inflight is > 0 while requests are running
    private static final List<URI> URLS = Arrays.asList(
            URI.create("https://httpbin.org/delay/1"),
            URI.create("https://httpbin.org/delay/1")
    );

    public static void main(final String[] args) throws Exception {
        final PrometheusMeterRegistry reg = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(reg);

        final ObservationRegistry obs = ObservationRegistry.create();

        final MetricConfig mc = MetricConfig.builder().build();
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.allOf(ObservingOptions.MetricSet.class))
                .tagLevel(ObservingOptions.TagLevel.EXTENDED)
                .build();

        final HttpAsyncClientBuilder b = HttpAsyncClients.custom();
        HttpClientObservationSupport.enable(b, obs, reg, opts, mc);

        final CloseableHttpAsyncClient client = b.build();
        client.start();

        // Fire two requests concurrently
        final Future<SimpleHttpResponse> f1 = client.execute(SimpleRequestBuilder.get(URLS.get(0)).build(), null);
        final Future<SimpleHttpResponse> f2 = client.execute(SimpleRequestBuilder.get(URLS.get(1)).build(), null);

        // Briefly wait to ensure they are inflight
        TimeUnit.MILLISECONDS.sleep(150);

        // Try to locate inflight gauge (value may be > 0 while requests are active)
        final Gauge inflight = Search.in(reg).name("http.client.inflight").gauge();
        System.out.println("inflight gauge present? " + (inflight != null));
        if (inflight != null) {
            System.out.println("inflight value       : " + inflight.value());
        }

        // Wait for results
        System.out.println("R1: " + f1.get().getCode());
        System.out.println("R2: " + f2.get().getCode());

        client.close();

        System.out.println("--- scrape ---");
        System.out.println(reg.scrape());
    }

    private AsyncMetricsDemo() {
    }
}
