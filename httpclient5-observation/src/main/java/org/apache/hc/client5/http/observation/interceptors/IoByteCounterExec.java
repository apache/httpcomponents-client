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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.util.Args;

/**
 * Counts request / response payload bytes for <b>classic</b> clients.
 * <p>
 * Meters:
 * <ul>
 *   <li>{@code &lt;prefix&gt;.request.bytes} (counter, baseUnit=bytes)</li>
 *   <li>{@code &lt;prefix&gt;.response.bytes} (counter, baseUnit=bytes)</li>
 * </ul>
 * Tags: {@code method}, {@code status}, and when {@link ObservingOptions.TagLevel#EXTENDED}
 * also {@code protocol}, {@code target}. If {@link MetricConfig#perUriIo} is true, adds {@code uri}.
 * Any {@link MetricConfig#commonTags} are appended. A custom tag mutator may be provided via
 * {@code ObservingOptions.tagCustomizer}.
 *
 * @since 5.6
 */
public final class IoByteCounterExec implements ExecChainHandler {

    private final MeterRegistry meterRegistry;
    private final ObservingOptions opts;
    private final MetricConfig mc;

    public IoByteCounterExec(final MeterRegistry meterRegistry,
                             final ObservingOptions opts,
                             final MetricConfig mc) {
        this.meterRegistry = Args.notNull(meterRegistry, "meterRegistry");
        this.opts = Args.notNull(opts, "observingOptions");
        this.mc = Args.notNull(mc, "metricConfig");

        // builders are created per request to avoid tag accumulation
    }

    @Override
    public ClassicHttpResponse execute(final ClassicHttpRequest request,
                                       final ExecChain.Scope scope,
                                       final ExecChain chain) throws IOException, HttpException {

        if (!opts.spanSampling.test(request.getRequestUri())) {
            return chain.proceed(request, scope);
        }

        final long reqBytes = contentLength(request.getEntity());
        ClassicHttpResponse response = null;
        try {
            response = chain.proceed(request, scope);
            return response;
        } finally {
            final long respBytes = contentLength(response != null ? response.getEntity() : null);

            final int status = response != null ? response.getCode() : 599;
            final String protocol = scope.route.getTargetHost().getSchemeName();
            final String target = scope.route.getTargetHost().getHostName();
            final String uri = request.getRequestUri();

            final List<Tag> tags = buildTags(request.getMethod(), status, protocol, target, uri);

            if (reqBytes >= 0) {
                Counter.builder(mc.prefix + ".request.bytes")
                        .baseUnit("bytes")
                        .description("HTTP request payload size")
                        .tags(mc.commonTags)
                        .tags(tags)
                        .register(meterRegistry)
                        .increment(reqBytes);
            }
            if (respBytes >= 0) {
                Counter.builder(mc.prefix + ".response.bytes")
                        .baseUnit("bytes")
                        .description("HTTP response payload size")
                        .tags(mc.commonTags)
                        .tags(tags)
                        .register(meterRegistry)
                        .increment(respBytes);
            }
        }
    }

    private static long contentLength(final HttpEntity entity) {
        if (entity == null) {
            return -1L;
        }
        final long len = entity.getContentLength();
        return len >= 0 ? len : -1L;
    }

    private List<Tag> buildTags(final String method,
                                final int status,
                                final String protocol,
                                final String target,
                                final String uri) {
        final List<Tag> tags = new ArrayList<>(8);
        tags.add(Tag.of("method", method));
        tags.add(Tag.of("status", Integer.toString(status)));
        if (opts.tagLevel == ObservingOptions.TagLevel.EXTENDED) {
            tags.add(Tag.of("protocol", protocol));
            tags.add(Tag.of("target", target));
        }
        if (mc.perUriIo) {
            tags.add(Tag.of("uri", uri));
        }
        return opts.tagCustomizer.apply(tags, method, status, protocol, target, uri);
    }
}
