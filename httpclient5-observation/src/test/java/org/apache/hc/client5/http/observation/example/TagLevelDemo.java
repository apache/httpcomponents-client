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

/**
 * Demonstrates LOW vs EXTENDED tag levels without Prometheus tag-key conflicts.
 * We use two separate registries and two different metric prefixes.
 */
public final class TagLevelDemo {

    private static final URI URL = URI.create("https://httpbin.org/get");

    public static void main(final String[] args) throws Exception {
        final ObservationRegistry observations = ObservationRegistry.create();

        // --------- LOW tag level (uses prefix "hc_low") ----------
        final PrometheusMeterRegistry regLow = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        final MetricConfig mcLow = MetricConfig.builder()
                .prefix("hc_low")               // different name -> no clash
                .build();
        final ObservingOptions low = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.BASIC))
                .tagLevel(ObservingOptions.TagLevel.LOW)
                .build();

        final HttpClientBuilder b1 = HttpClients.custom();
        HttpClientObservationSupport.enable(b1, observations, regLow, low, mcLow);
        try (final CloseableHttpClient c1 = b1.build()) {
            final ClassicHttpResponse r1 = c1.executeOpen(null, ClassicRequestBuilder.get(URL).build(), null);
            r1.close();
        }
        System.out.println("--- LOW scrape ---");
        System.out.println(regLow.scrape());

        // --------- EXTENDED tag level (uses prefix "hc_ext") ----------
        final PrometheusMeterRegistry regExt = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        final MetricConfig mcExt = MetricConfig.builder()
                .prefix("hc_ext")               // different name -> no clash
                .build();
        final ObservingOptions ext = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.BASIC))
                .tagLevel(ObservingOptions.TagLevel.EXTENDED)
                .build();

        final HttpClientBuilder b2 = HttpClients.custom();
        HttpClientObservationSupport.enable(b2, observations, regExt, ext, mcExt);
        try (final CloseableHttpClient c2 = b2.build()) {
            final ClassicHttpResponse r2 = c2.executeOpen(null, ClassicRequestBuilder.get(URL).build(), null);
            r2.close();
        }
        System.out.println("--- EXTENDED scrape ---");
        System.out.println(regExt.scrape());
    }

    private TagLevelDemo() {
    }
}
