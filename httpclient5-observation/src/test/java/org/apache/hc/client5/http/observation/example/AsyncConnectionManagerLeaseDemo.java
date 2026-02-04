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
import io.micrometer.core.instrument.search.Search;
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
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.client5.http.observation.impl.MeteredAsyncConnectionManager;

public final class AsyncConnectionManagerLeaseDemo {

    public static void main(final String[] args) throws Exception {
        final PrometheusMeterRegistry reg = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(reg);

        final ObservationRegistry obs = ObservationRegistry.create();
        final MetricConfig mc = MetricConfig.builder().prefix("demo").build();
        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(EnumSet.of(ObservingOptions.MetricSet.BASIC, ObservingOptions.MetricSet.CONN_POOL))
                .build();

        final AsyncClientConnectionManager rawCm = PoolingAsyncClientConnectionManagerBuilder.create().build();
        final AsyncClientConnectionManager cm = new MeteredAsyncConnectionManager(rawCm, reg, mc, opts);

        final HttpAsyncClientBuilder builder = HttpAsyncClients.custom().setConnectionManager(cm);
        HttpClientObservationSupport.enable(builder, obs, reg, opts, mc);

        try (final CloseableHttpAsyncClient client = builder.build()) {
            client.start();

            final SimpleHttpRequest req = SimpleRequestBuilder.get("http://httpbin.org/get").build();
            final Future<SimpleHttpResponse> fut = client.execute(req, null);
            final SimpleHttpResponse rsp = fut.get(20, TimeUnit.SECONDS);
            System.out.println("status=" + rsp.getCode());
        }

        System.out.println("pool.lease  present? " + (Search.in(reg).name("demo.pool.lease").timer() != null));
        System.out.println("pool.leases present? " + (Search.in(reg).name("demo.pool.leases").counter() != null));
        System.out.println("--- scrape ---");
        System.out.println(reg.scrape());
    }

    private AsyncConnectionManagerLeaseDemo() {
    }
}
