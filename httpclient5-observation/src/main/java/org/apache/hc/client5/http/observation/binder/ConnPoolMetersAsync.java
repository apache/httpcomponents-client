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
package org.apache.hc.client5.http.observation.binder;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;

/**
 * Registers connection-pool gauges for an {@link HttpAsyncClientBuilder}.
 * <p>
 * Exposes:
 * <ul>
 *   <li>{@code &lt;prefix&gt;.pool.leased} – number of leased connections</li>
 *   <li>{@code &lt;prefix&gt;.pool.available} – number of available (idle) connections</li>
 *   <li>{@code &lt;prefix&gt;.pool.pending} – number of pending connection requests</li>
 * </ul>
 * The {@code prefix} and any common tags come from {@link MetricConfig}.
 *
 * <p><strong>Usage</strong></p>
 * <pre>{@code
 * MeterRegistry meters = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
 * MetricConfig mc = MetricConfig.builder().prefix("http_client").build();
 *
 * HttpAsyncClientBuilder b = HttpAsyncClients.custom()
 *     .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create().build());
 *
 * // After the async connection manager is attached to the builder:
 * ConnPoolMetersAsync.bindTo(b, meters, mc);
 * }</pre>
 *
 * <p><strong>Note:</strong> This binder reads the async connection manager from
 * the {@link HttpAsyncClientBuilder}. If the builder has no manager attached or
 * it does not implement {@link ConnPoolControl}, this method is a no-op.</p>
 *
 * @since 5.6
 */
public final class ConnPoolMetersAsync implements MeterBinder {

    private final ConnPoolControl<?> pool;
    private final MetricConfig mc;

    private ConnPoolMetersAsync(final ConnPoolControl<?> pool, final MetricConfig mc) {
        this.pool = pool;
        this.mc = mc;
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        Args.notNull(registry, "registry");
        Gauge.builder(mc.prefix + ".pool.leased", pool, p -> p.getTotalStats().getLeased())
                .tags(mc.commonTags)
                .register(registry);
        Gauge.builder(mc.prefix + ".pool.available", pool, p -> p.getTotalStats().getAvailable())
                .tags(mc.commonTags)
                .register(registry);
        Gauge.builder(mc.prefix + ".pool.pending", pool, p -> p.getTotalStats().getPending())
                .tags(mc.commonTags)
                .register(registry);
    }

    /**
     * Binds pool gauges for the async connection manager currently attached to the builder.
     * If the builder has no connection manager or it does not implement {@link ConnPoolControl},
     * this method is a no-op.
     *
     * @param builder  async client builder
     * @param registry meter registry
     * @param mc       metric configuration (prefix, common tags)
     * @since 5.6
     */
    public static void bindTo(final HttpAsyncClientBuilder builder,
                              final MeterRegistry registry,
                              final MetricConfig mc) {
        Args.notNull(builder, "builder");
        Args.notNull(registry, "registry");
        Args.notNull(mc, "metricConfig");

        final AsyncClientConnectionManager cm = builder.getConnManager();
        if (cm instanceof ConnPoolControl) {
            new ConnPoolMetersAsync((ConnPoolControl<?>) cm, mc).bindTo(registry);
        }
    }

    /**
     * Binds pool gauges using {@link MetricConfig#DEFAULT}.
     *
     * @param builder  async client builder
     * @param registry meter registry
     * @since 5.6
     */
    public static void bindTo(final HttpAsyncClientBuilder builder,
                              final MeterRegistry registry) {
        bindTo(builder, registry, MetricConfig.DEFAULT);
    }

    /**
     * No instantiation outside helpers.
     */
    private ConnPoolMetersAsync() {
        this.pool = null;
        this.mc = null;
    }
}
