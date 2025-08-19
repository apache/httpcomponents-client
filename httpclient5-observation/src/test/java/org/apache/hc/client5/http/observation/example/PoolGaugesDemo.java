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
import java.util.EnumSet;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public final class PoolGaugesDemo {

    private static final URI URL = URI.create("https://httpbin.org/get");

    public static void main(final String[] args) throws Exception {
        final PrometheusMeterRegistry reg = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(reg);

        final ObservationRegistry obs = ObservationRegistry.create();

        final MetricConfig mc = MetricConfig.builder().build();
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.BASIC,
                        ObservingOptions.MetricSet.CONN_POOL))
                .tagLevel(ObservingOptions.TagLevel.LOW)
                .build();

        // Ensure a pooling manager is used so pool gauges exist
        final HttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        final HttpClientBuilder b = HttpClients.custom().setConnectionManager(cm);

        HttpClientObservationSupport.enable(b, obs, reg, opts, mc);

        try (final CloseableHttpClient client = b.build()) {
            final ClassicHttpResponse rsp = client.executeOpen(null, ClassicRequestBuilder.get(URL).build(), null);
            rsp.close();
        }

        System.out.println("pool.leased    present? " + (Search.in(reg).name("http.client.pool.leased").gauge() != null));
        System.out.println("pool.available present? " + (Search.in(reg).name("http.client.pool.available").gauge() != null));
        System.out.println("pool.pending   present? " + (Search.in(reg).name("http.client.pool.pending").gauge() != null));

        System.out.println("--- scrape ---");
        System.out.println(reg.scrape());
    }

    private PoolGaugesDemo() {
    }
}
