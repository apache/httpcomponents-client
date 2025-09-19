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
package org.apache.hc.client5.http.observation.interceptors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.util.Args;

/**
 * Classic (blocking) interceptor that records a Micrometer {@link Timer}
 * per request and a {@link Counter} for response codes. Also tracks in-flight
 * requests via a {@link Gauge} tagged as {@code kind=classic}.
 * <p>
 * Meters:
 * <ul>
 *   <li>{@code &lt;prefix&gt;.request} (timer) — latency, uses {@link MetricConfig#slo} and {@link MetricConfig#percentiles}</li>
 *   <li>{@code &lt;prefix&gt;.response} (counter) — result count</li>
 *   <li>{@code &lt;prefix&gt;.inflight} (gauge, kind=classic) — in-flight request count</li>
 * </ul>
 * Tags: {@code method}, {@code status}, and when {@link ObservingOptions.TagLevel#EXTENDED}
 * also {@code protocol}, {@code target}. Any {@link MetricConfig#commonTags} are appended.
 *
 * @since 5.6
 */
public final class TimerExec implements ExecChainHandler {

    private final MeterRegistry registry;
    private final ObservingOptions cfg;
    private final MetricConfig mc;

    private final Timer.Builder timerBuilder;
    private final Counter.Builder counterBuilder;

    private final AtomicInteger inflight = new AtomicInteger(0);

    /**
     * Back-compat: two-arg ctor.
     */
    public TimerExec(final MeterRegistry reg, final ObservingOptions cfg) {
        this(reg, cfg, null);
    }

    /**
     * Preferred: honors {@link MetricConfig}.
     */
    public TimerExec(final MeterRegistry reg, final ObservingOptions cfg, final MetricConfig mc) {
        this.registry = Args.notNull(reg, "registry");
        this.cfg = Args.notNull(cfg, "config");
        this.mc = mc != null ? mc : MetricConfig.builder().build();

        final String base = this.mc.prefix + ".";
        this.timerBuilder = Timer.builder(base + "request").tags(this.mc.commonTags);
        if (this.mc.percentiles != null && this.mc.percentiles.length > 0) {
            this.timerBuilder.publishPercentiles(this.mc.percentiles);
        }
        if (this.mc.slo != null) {
            this.timerBuilder.serviceLevelObjectives(this.mc.slo);
        }

        this.counterBuilder = Counter.builder(base + "response").tags(this.mc.commonTags);

        // Tag-aware guard: only register once per (name + tags)
        if (registry.find(this.mc.prefix + ".inflight")
                .tags("kind", "classic")
                .tags(this.mc.commonTags)
                .gauge() == null) {
            Gauge.builder(this.mc.prefix + ".inflight", inflight, AtomicInteger::doubleValue)
                    .tag("kind", "classic")
                    .tags(this.mc.commonTags)
                    .register(registry);
        }
    }

    @Override
    public ClassicHttpResponse execute(final ClassicHttpRequest request,
                                       final ExecChain.Scope scope,
                                       final ExecChain chain)
            throws IOException, HttpException {

        if (!cfg.spanSampling.test(request.getRequestUri())) {
            return chain.proceed(request, scope);   // fast-path
        }

        inflight.incrementAndGet();
        final long start = System.nanoTime();
        ClassicHttpResponse response = null;
        try {
            response = chain.proceed(request, scope);
            return response;
        } finally {
            try {
                final long durNanos = System.nanoTime() - start;
                final int status = response != null ? response.getCode() : 599;

                final List<Tag> tags = new ArrayList<Tag>(4);
                tags.add(Tag.of("method", request.getMethod()));
                tags.add(Tag.of("status", Integer.toString(status)));

                if (cfg.tagLevel == ObservingOptions.TagLevel.EXTENDED) {
                    tags.add(Tag.of("protocol", scope.route.getTargetHost().getSchemeName()));
                    tags.add(Tag.of("target", scope.route.getTargetHost().getHostName()));
                }

                timerBuilder.tags(tags)
                        .register(registry)
                        .record(durNanos, TimeUnit.NANOSECONDS);

                counterBuilder.tags(tags)
                        .register(registry)
                        .increment();
            } finally {
                inflight.decrementAndGet();
            }
        }
    }
}
