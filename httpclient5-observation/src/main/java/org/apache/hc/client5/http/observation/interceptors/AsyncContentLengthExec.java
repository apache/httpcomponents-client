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

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.util.Args;

/**
 * Records the <b>declared</b> request / response entity lengths for
 * <b>asynchronous</b> clients as {@link DistributionSummary distribution summaries}.
 * <p>
 * Meters, tags and semantics are the same as for {@link ContentLengthExec}. The
 * request length is taken from {@link AsyncEntityProducer#getContentLength()} and
 * the response length from {@link EntityDetails#getContentLength()}. They are the
 * declared entity lengths as the application sees them, not the number of bytes
 * transferred, and they are uncompressed only if no content coding is visible to
 * the application. Nothing is recorded if no response was received, for entities
 * of unknown length (for example chunked) or for messages without an entity. The
 * data stream is never wrapped or buffered.
 * <p>
 * Over HTTP/2, HttpCore reports the response entity length as unknown even if
 * a {@code Content-Length} header is present, so no response length is recorded
 * for HTTP/2 exchanges.
 * <p>
 * Enabled by {@link ObservingOptions.MetricSet#CONTENT_LENGTH}.
 *
 * @see ContentLengthExec
 * @since 5.7
 */
public final class AsyncContentLengthExec implements AsyncExecChainHandler {

    private final ObservingOptions opts;
    private final ContentLengthRecorder recorder;

    /**
     * Creates a new instance.
     *
     * @param meterRegistry meter registry to register meters with
     * @param opts          observation/metric options
     * @param mc            metric configuration
     */
    public AsyncContentLengthExec(final MeterRegistry meterRegistry,
                                  final ObservingOptions opts,
                                  final MetricConfig mc) {
        Args.notNull(meterRegistry, "meterRegistry");
        this.opts = Args.notNull(opts, "observingOptions");
        Args.notNull(mc, "metricConfig");
        this.recorder = new ContentLengthRecorder(meterRegistry, opts, mc);
    }

    @Override
    public void execute(final HttpRequest request,
                        final AsyncEntityProducer entityProducer,
                        final AsyncExecChain.Scope scope,
                        final AsyncExecChain chain,
                        final AsyncExecCallback callback) throws HttpException, IOException {

        if (!opts.spanSampling.test(request.getRequestUri())) {
            chain.proceed(request, entityProducer, scope, callback);
            return;
        }

        final long requestContentLength = entityProducer != null
                ? ContentLengthRecorder.normalize(entityProducer.getContentLength())
                : -1L;

        final AsyncExecCallback wrapped = new AsyncExecCallback() {

            private volatile HttpResponse response;
            private volatile long responseContentLength = -1L;

            @Override
            public AsyncDataConsumer handleResponse(final HttpResponse response,
                                                    final EntityDetails entityDetails) throws HttpException, IOException {
                this.response = response;
                if (entityDetails != null) {
                    responseContentLength = ContentLengthRecorder.normalize(entityDetails.getContentLength());
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
                final HttpResponse rsp = response;
                if (rsp != null) {
                    recorder.record(request.getMethod(), rsp.getCode(), scope.route.getTargetHost(),
                            request.getRequestUri(), requestContentLength, responseContentLength);
                }
            }
        };

        chain.proceed(request, entityProducer, scope, wrapped);
    }

}
