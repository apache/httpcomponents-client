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
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.Args;

/**
 * Records request latency and response counters for <b>asynchronous</b> clients.
 * <p>
 * Meters:
 * <ul>
 *   <li>{@code &lt;prefix&gt;.request} (timer) — latency, uses {@link MetricConfig#slo} and {@link MetricConfig#percentiles}</li>
 *   <li>{@code &lt;prefix&gt;.response} (counter) — result count</li>
 *   <li>{@code &lt;prefix&gt;.inflight} (gauge, kind=async) — in-flight request count</li>
 * </ul>
 * Tags: {@code method}, {@code status}, and when {@link ObservingOptions.TagLevel#EXTENDED}
 * also {@code protocol}, {@code target}. Any {@link MetricConfig#commonTags} are appended.
 *
 * @since 5.6
 */
public final class AsyncTimerExec implements AsyncExecChainHandler {

    private final MeterRegistry registry;
    private final ObservingOptions opts;
    private final MetricConfig mc;
    private final AtomicInteger inflight = new AtomicInteger();

    public AsyncTimerExec(final MeterRegistry reg, final ObservingOptions opts, final MetricConfig mc) {
        this.registry = Args.notNull(reg, "registry");
        this.opts = Args.notNull(opts, "options");
        this.mc = mc != null ? mc : MetricConfig.builder().build();

        // Tag-aware guard: only register once per (name + tags)
        if (registry.find(this.mc.prefix + ".inflight")
                .tags("kind", "async")
                .tags(this.mc.commonTags)
                .gauge() == null) {
            Gauge.builder(this.mc.prefix + ".inflight", inflight, AtomicInteger::doubleValue)
                    .tag("kind", "async")
                    .tags(this.mc.commonTags)
                    .register(registry);
        }
    }

    @Override
    public void execute(final HttpRequest request,
                        final org.apache.hc.core5.http.nio.AsyncEntityProducer entityProducer,
                        final AsyncExecChain.Scope scope,
                        final AsyncExecChain chain,
                        final AsyncExecCallback callback) throws HttpException, IOException {

        if (!opts.spanSampling.test(request.getRequestUri())) {
            chain.proceed(request, entityProducer, scope, callback);
            return;
        }

        inflight.incrementAndGet();
        final long start = System.nanoTime();
        final AtomicReference<HttpResponse> respRef = new AtomicReference<HttpResponse>();

        final AsyncExecCallback wrapped = new AsyncExecCallback() {
            @Override
            public org.apache.hc.core5.http.nio.AsyncDataConsumer handleResponse(
                    final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
                respRef.set(response);
                return callback.handleResponse(response, entityDetails);
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                callback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                record();
                callback.completed();
            }

            @Override
            public void failed(final Exception cause) {
                record();
                callback.failed(cause);
            }

            private void record() {
                try {
                    final long dur = System.nanoTime() - start;
                    final HttpResponse r = respRef.get();
                    final int status = r != null ? r.getCode() : 599;

                    final String protocol = scope.route.getTargetHost().getSchemeName();
                    final String target = scope.route.getTargetHost().getHostName();
                    final List<Tag> tags = buildTags(request.getMethod(), status, protocol, target, request.getRequestUri());

                    Timer.Builder tb = Timer.builder(mc.prefix + ".request")
                            .tags(tags)
                            .tags(mc.commonTags);

                    if (mc.slo != null) {
                        tb = tb.serviceLevelObjectives(mc.slo);
                    }
                    if (mc.percentiles != null && mc.percentiles.length > 0) {
                        tb = tb.publishPercentiles(mc.percentiles);
                    }

                    tb.register(registry).record(dur, TimeUnit.NANOSECONDS);

                    Counter.builder(mc.prefix + ".response")
                            .tags(tags)
                            .tags(mc.commonTags)
                            .register(registry)
                            .increment();
                } finally {
                    inflight.decrementAndGet();
                }
            }
        };

        chain.proceed(request, entityProducer, scope, wrapped);
    }

    private List<Tag> buildTags(final String method,
                                final int status,
                                final String protocol,
                                final String target,
                                final String uri) {
        final List<Tag> tags = new ArrayList<>(6);
        tags.add(Tag.of("method", method));
        tags.add(Tag.of("status", Integer.toString(status)));
        if (opts.tagLevel == ObservingOptions.TagLevel.EXTENDED) {
            tags.add(Tag.of("protocol", protocol));
            tags.add(Tag.of("target", target));
        }
        // Note: async timer does not add "uri" even if perUriIo is true (that flag is for IO counters).
        return opts.tagCustomizer.apply(tags, method, status, protocol, target, uri);
    }
}
