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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.util.Args;

/**
 * Counts request / response payload bytes for <b>asynchronous</b> clients.
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
public final class AsyncIoByteCounterExec implements AsyncExecChainHandler {

    private final MeterRegistry meterRegistry;
    private final ObservingOptions opts;
    private final MetricConfig mc;

    private final Counter.Builder reqBuilder;
    private final Counter.Builder respBuilder;

    public AsyncIoByteCounterExec(final MeterRegistry meterRegistry,
                                  final ObservingOptions opts,
                                  final MetricConfig mc) {
        this.meterRegistry = Args.notNull(meterRegistry, "meterRegistry");
        this.opts = Args.notNull(opts, "observingOptions");
        this.mc = Args.notNull(mc, "metricConfig");

        this.reqBuilder = Counter.builder(mc.prefix + ".request.bytes")
                .description("HTTP request payload size")
                .baseUnit("bytes");

        this.respBuilder = Counter.builder(mc.prefix + ".response.bytes")
                .description("HTTP response payload size")
                .baseUnit("bytes");
    }

    @Override
    public void execute(final HttpRequest request,
                        final AsyncEntityProducer entityProducer,
                        final AsyncExecChain.Scope scope,
                        final AsyncExecChain chain,
                        final AsyncExecCallback callback)
            throws HttpException, IOException {

        if (!opts.spanSampling.test(request.getRequestUri())) {
            chain.proceed(request, entityProducer, scope, callback);
            return;
        }

        final long reqBytes = entityProducer != null ? entityProducer.getContentLength() : -1L;

        final AtomicReference<HttpResponse> respRef = new AtomicReference<>();
        final AtomicLong respLen = new AtomicLong(-1L);

        final AsyncExecCallback wrapped = new AsyncExecCallback() {

            @Override
            public org.apache.hc.core5.http.nio.AsyncDataConsumer handleResponse(
                    final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {

                respRef.set(response);
                if (entityDetails != null) {
                    respLen.set(entityDetails.getContentLength());
                }
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
                final HttpResponse rsp = respRef.get();
                final int status = rsp != null ? rsp.getCode() : 599;

                final String protocol = scope.route.getTargetHost().getSchemeName();
                final String target = scope.route.getTargetHost().getHostName();
                final String uri = request.getRequestUri();

                final List<Tag> tags = buildTags(request.getMethod(), status, protocol, target, uri);

                if (reqBytes >= 0) {
                    reqBuilder.tags(tags).tags(mc.commonTags).register(meterRegistry).increment(reqBytes);
                }
                final long rb = respLen.get();
                if (rb >= 0) {
                    respBuilder.tags(tags).tags(mc.commonTags).register(meterRegistry).increment(rb);
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
