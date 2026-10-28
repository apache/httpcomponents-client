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

import java.util.ArrayList;
import java.util.List;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.HttpHost;

/**
 * Records declared request / response entity lengths, shared by
 * {@link ContentLengthExec} and {@link AsyncContentLengthExec}.
 */
final class ContentLengthRecorder {

    static final String REQUEST_SUFFIX = ".request.content.length";
    static final String RESPONSE_SUFFIX = ".response.content.length";

    private final MeterRegistry meterRegistry;
    private final ObservingOptions opts;
    private final MetricConfig mc;
    private final String requestName;
    private final String responseName;

    ContentLengthRecorder(final MeterRegistry meterRegistry,
                          final ObservingOptions opts,
                          final MetricConfig mc) {
        this.meterRegistry = meterRegistry;
        this.opts = opts;
        this.mc = mc;
        this.requestName = mc.prefix + REQUEST_SUFFIX;
        this.responseName = mc.prefix + RESPONSE_SUFFIX;
    }

    /**
     * Records the given lengths of an exchange that received a response.
     * Negative lengths (unknown length or no entity) are skipped.
     */
    void record(final String method,
                final int status,
                final HttpHost targetHost,
                final String uri,
                final long requestContentLength,
                final long responseContentLength) {
        if (requestContentLength < 0 && responseContentLength < 0) {
            return;
        }
        final List<Tag> tags = buildTags(method, status, targetHost, uri);
        if (requestContentLength >= 0) {
            summary(requestName, "Declared HTTP client request entity length", tags).record(requestContentLength);
        }
        if (responseContentLength >= 0) {
            summary(responseName, "Declared HTTP client response entity length", tags).record(responseContentLength);
        }
    }

    private DistributionSummary summary(final String name, final String description, final List<Tag> tags) {
        final DistributionSummary.Builder builder = DistributionSummary.builder(name)
                .description(description)
                .baseUnit(BaseUnits.BYTES)
                .tags(mc.commonTags)
                .tags(tags);
        if (mc.percentiles != null && mc.percentiles.length > 0) {
            builder.publishPercentiles(mc.percentiles);
        }
        return builder.register(meterRegistry);
    }

    private List<Tag> buildTags(final String method,
                                final int status,
                                final HttpHost targetHost,
                                final String uri) {
        final String protocol = targetHost != null ? targetHost.getSchemeName() : null;
        final String target = targetHost != null ? targetHost.getHostName() : null;
        final List<Tag> tags = new ArrayList<>(4);
        tags.add(Tag.of("method", method));
        tags.add(Tag.of("status", Integer.toString(status)));
        if (opts.tagLevel == ObservingOptions.TagLevel.EXTENDED && targetHost != null) {
            tags.add(Tag.of("protocol", protocol));
            tags.add(Tag.of("target", target));
        }
        return opts.tagCustomizer.apply(tags, method, status, protocol, target, uri);
    }

    static long normalize(final long contentLength) {
        return contentLength >= 0 ? contentLength : -1L;
    }

}
