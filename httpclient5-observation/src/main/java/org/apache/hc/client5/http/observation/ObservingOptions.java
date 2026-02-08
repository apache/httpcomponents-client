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
package org.apache.hc.client5.http.observation;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import io.micrometer.core.instrument.Tag;
import io.micrometer.observation.ObservationPredicate;
import org.apache.hc.core5.util.Args;

/**
 * Immutable container with all user–tunable knobs for metrics/tracing.
 *
 * @since 5.6
 */
public final class ObservingOptions {

    /**
     * Which metric groups to enable.
     * <p>
     * {@link MetricSet#TLS} and {@link MetricSet#DNS} are applied when using
     * {@link org.apache.hc.client5.http.observation.HttpClientObservationSupport#meteredTlsStrategy}
     * and {@link org.apache.hc.client5.http.observation.HttpClientObservationSupport#meteredDnsResolver}
     * to wrap the underlying TLS strategy or DNS resolver.
     */
    public enum MetricSet { BASIC, IO, CONN_POOL, TLS, DNS }

    /**
     * How many tags each metric/trace should get.
     */
    public enum TagLevel { LOW, EXTENDED }

    /**
     * Per-request tag customization hook.
     */
    @FunctionalInterface
    public interface TagCustomizer {
        List<Tag> apply(List<Tag> base,
                        String method,
                        int status,
                        String protocol,
                        String target,
                        String uri);
    }

    /**
     * Convenience: turn on all metric groups.
     */
    public static EnumSet<MetricSet> allMetricSets() {
        return EnumSet.allOf(MetricSet.class);
    }

    public final EnumSet<MetricSet> metricSets;
    public final TagLevel tagLevel;
    public final ObservationPredicate micrometerFilter;
    public final Predicate<String> spanSampling;
    public final TagCustomizer tagCustomizer;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private EnumSet<MetricSet> sets = EnumSet.of(MetricSet.BASIC);
        private TagLevel tag = TagLevel.LOW;
        private ObservationPredicate obs = (n, c) -> true;
        private Predicate<String> span = uri -> true;
        private TagCustomizer customizer = (base, m, s, p, t, u) -> {
            return base; // identity by default
        };

        public Builder metrics(final EnumSet<MetricSet> s) {
            sets = EnumSet.copyOf(s);
            return this;
        }

        public Builder tagLevel(final TagLevel t) {
            tag = Args.notNull(t, "tag");
            return this;
        }

        public Builder micrometerFilter(final ObservationPredicate p) {
            obs = Args.notNull(p, "pred");
            return this;
        }

        public Builder spanSampling(final Predicate<String> p) {
            span = Args.notNull(p, "pred");
            return this;
        }

        public Builder tagCustomizer(final TagCustomizer c) {
            customizer = Args.notNull(c, "tagCustomizer");
            return this;
        }

        public ObservingOptions build() {
            return new ObservingOptions(this);
        }
    }

    public static final ObservingOptions DEFAULT = builder().build();

    private ObservingOptions(final Builder b) {
        metricSets = b.sets;
        tagLevel = b.tag;
        micrometerFilter = b.obs;
        spanSampling = b.span;
        tagCustomizer = b.customizer;
    }
}
