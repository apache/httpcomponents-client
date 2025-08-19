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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public final class SpanSamplingDemo {

    private static final URI URL_OK = URI.create("https://httpbin.org/get");
    private static final URI URL_SKIP = URI.create("https://httpbin.org/anything/deny");

    public static void main(final String[] args) throws Exception {
        final PrometheusMeterRegistry reg = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(reg);

        final ObservationRegistry obs = ObservationRegistry.create();
        final MetricConfig mc = MetricConfig.builder().build();

        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.BASIC, ObservingOptions.MetricSet.IO))
                .tagLevel(ObservingOptions.TagLevel.LOW)
                .spanSampling(uri -> {
                    // Skip any URI containing "/deny"
                    return uri == null || !uri.contains("/deny");
                })
                .build();

        final HttpClientBuilder b = HttpClients.custom();
        HttpClientObservationSupport.enable(b, obs, reg, opts, mc);

        try (final CloseableHttpClient client = b.build()) {
            final ClassicHttpResponse r1 = client.executeOpen(null, ClassicRequestBuilder.get(URL_OK).build(), null);
            r1.close();
            final ClassicHttpResponse r2 = client.executeOpen(null, ClassicRequestBuilder.get(URL_SKIP).build(), null);
            r2.close();
        }

        // Sum all response counters (only the first request should contribute)
        double total = 0.0;
        for (final Counter c : reg.find("http.client.response").counters()) {
            total += c.count();
        }

        System.out.println("Total http.client.response count (expected ~1.0) = " + total);
        System.out.println("--- scrape ---");
        System.out.println(reg.scrape());
    }

    private SpanSamplingDemo() {
    }
}
