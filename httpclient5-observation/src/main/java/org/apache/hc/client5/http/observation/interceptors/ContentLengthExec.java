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
 * Records the <b>declared</b> request / response entity lengths for <b>classic</b>
 * clients as {@link DistributionSummary distribution summaries}.
 * <p>
 * Meters:
 * <ul>
 *   <li>{@code <prefix>.request.content.length} (distribution summary, baseUnit=bytes)</li>
 *   <li>{@code <prefix>.response.content.length} (distribution summary, baseUnit=bytes)</li>
 * </ul>
 * <p>
 * Tags are the same as for the request timer: {@code method}, {@code status},
 * and when {@link ObservingOptions.TagLevel#EXTENDED} also {@code protocol} and
 * {@code target}. {@link ObservingOptions#tagCustomizer} is applied and
 * {@link MetricConfig#commonTags} are appended. {@link MetricConfig#percentiles}
 * are published if configured.
 * <p>
 * <b>What is measured.</b> The values are taken from {@link HttpEntity#getContentLength()}
 * of the request and response entities as the application sees them: before the
 * client applies any content coding to the request and after the client
 * transparently decodes the response. They are the declared entity lengths, not
 * the number of bytes transferred: they exclude headers and transfer framing,
 * and they are recorded even if the body is not (fully) transmitted, for example
 * when the server rejects a request before reading its body.
 * <p>
 * The declared length is the uncompressed length only if no content coding is
 * visible to the application. It is the encoded length if the application sends
 * an entity that is already encoded (for example with its own
 * {@code Content-Encoding: gzip}) or if content compression is disabled and the
 * response is not decoded. When the client handles content coding, this metric
 * is useful where limits apply to the decoded size, for example servers that
 * reject requests whose decoded body exceeds a maximum content length.
 * <p>
 * <b>What is not measured.</b> Nothing is recorded if no response was received
 * (for example the connection could not be established), for an entity whose
 * length is not known up front (for example {@code Transfer-Encoding: chunked}
 * or a response the client decodes transparently), or for a message without
 * an entity. Entities are never wrapped or buffered, so the instrumentation adds
 * no I/O overhead.
 * <p>
 * Enabled by {@link ObservingOptions.MetricSet#CONTENT_LENGTH}.
 *
 * @see AsyncContentLengthExec
 * @since 5.7
 */
public final class ContentLengthExec implements ExecChainHandler {

    private final ObservingOptions opts;
    private final ContentLengthRecorder recorder;

    /**
     * Creates a new instance.
     *
     * @param meterRegistry meter registry to register meters with
     * @param opts          observation/metric options
     * @param mc            metric configuration
     */
    public ContentLengthExec(final MeterRegistry meterRegistry,
                             final ObservingOptions opts,
                             final MetricConfig mc) {
        Args.notNull(meterRegistry, "meterRegistry");
        this.opts = Args.notNull(opts, "observingOptions");
        Args.notNull(mc, "metricConfig");
        this.recorder = new ContentLengthRecorder(meterRegistry, opts, mc);
    }

    @Override
    public ClassicHttpResponse execute(final ClassicHttpRequest request,
                                       final ExecChain.Scope scope,
                                       final ExecChain chain) throws IOException, HttpException {

        if (!opts.spanSampling.test(request.getRequestUri())) {
            return chain.proceed(request, scope);
        }

        final long requestContentLength = contentLength(request.getEntity());
        final ClassicHttpResponse response = chain.proceed(request, scope);
        recorder.record(request.getMethod(), response.getCode(), scope.route.getTargetHost(),
                request.getRequestUri(), requestContentLength, contentLength(response.getEntity()));
        return response;
    }

    private static long contentLength(final HttpEntity entity) {
        return entity != null ? ContentLengthRecorder.normalize(entity.getContentLength()) : -1L;
    }

}
