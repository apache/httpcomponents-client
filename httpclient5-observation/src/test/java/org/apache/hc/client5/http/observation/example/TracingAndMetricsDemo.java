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
import java.util.ArrayList;
import java.util.EnumSet;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.client5.http.observation.impl.ObservationClassicExecInterceptor;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

/**
 * Single-file demo: classic client + tracing + metrics.
 */
public final class TracingAndMetricsDemo {

    private static final URI URL = URI.create("https://httpbin.org/get");

    public static void main(final String[] args) throws Exception {

        /* ----------------------------------------------------------------
         * 1)  Micrometer metrics bootstrap
         * ---------------------------------------------------------------- */
        final PrometheusMeterRegistry meters = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(meters);          // make it the global one

        /* ----------------------------------------------------------------
         * 2)  OpenTelemetry bootstrap (in-memory exporter so nothing is sent
         *     over the wire)
         * ---------------------------------------------------------------- */
        final InMemorySpanExporter spans = InMemorySpanExporter.create();

        final SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spans))
                .setResource(Resource.empty())
                .build();

        final OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .setPropagators(ContextPropagators.noop())
                .build();

        final OtelCurrentTraceContext ctx = new OtelCurrentTraceContext();
        final OtelTracer tracer = new OtelTracer(
                otel.getTracer("demo"),
                ctx,
                event -> {
                },                      // no-op event bus
                new OtelBaggageManager(ctx, new ArrayList<>(), new ArrayList<>()));

        /* Micrometer ObservationRegistry that delegates to the tracer */
        final ObservationRegistry observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));

        /* ----------------------------------------------------------------
         * 3)  Build classic client
         * ---------------------------------------------------------------- */
        final HttpClientBuilder builder = HttpClients.custom();

        final ObservingOptions obs = ObservingOptions.builder()
                .metrics(EnumSet.allOf(ObservingOptions.MetricSet.class))
                .tagLevel(ObservingOptions.TagLevel.EXTENDED)
                .build();

        // (A) span interceptor FIRST
        builder.addExecInterceptorFirst("span", new ObservationClassicExecInterceptor(observations, obs));

        // (B) metric interceptors
        HttpClientObservationSupport.enable(
                builder,
                observations,
                meters,
                obs);

        /* ----------------------------------------------------------------
         * 4)  Run one request
         * ---------------------------------------------------------------- */
        try (final CloseableHttpClient client = builder.build()) {
            final ClassicHttpResponse rsp = client.executeOpen(
                    null, ClassicRequestBuilder.get(URL).build(), null);
            System.out.println("[classic]        " + rsp.getCode());
            rsp.close();
        }

        /* ----------------------------------------------------------------
         * 5)  Inspect results
         * ---------------------------------------------------------------- */
        final double responses = meters.find("http.client.response").counter().count();
        final double latencySamples = meters.find("http.client.request").timer().count();

        System.out.println("responses      = " + responses);
        System.out.println("latencySamples = " + latencySamples);

        System.out.println("\n--- Exported span ---");
        System.out.println(spans.getFinishedSpanItems().get(0));

        // scrape all metrics (optional)
        System.out.println("\n--- Prometheus scrape ---");
        System.out.println(meters.scrape());
    }

}
