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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.micrometer.core.instrument.Tag;

/**
 * Tunables for Micrometer metrics: SLOs, percentiles, common tags,
 * optional high-cardinality URI tagging for I/O counters.
 *
 * @since 5.6
 */
public final class MetricConfig {

    /**
     * Metric name prefix; defaults to "http.client".
     */
    public final String prefix;

    /**
     * Service-level objective for latency histograms.
     */
    public final Duration slo;

    /**
     * Percentiles to publish (e.g., 0.95, 0.99). Empty - none.
     */
    public final double[] percentiles;

    /**
     * If true, IO counters get a "uri" tag (can be high-cardinality).
     */
    public final boolean perUriIo;

    /**
     * Tags added to every meter.
     */
    public final List<Tag> commonTags;

    private MetricConfig(final Builder b) {
        this.prefix = b.prefix;
        this.slo = b.slo;
        this.percentiles = b.percentiles != null ? b.percentiles.clone() : new double[0];
        this.perUriIo = b.perUriIo;
        this.commonTags = Collections.unmodifiableList(new ArrayList<>(b.commonTags));
    }

    public static final MetricConfig DEFAULT = builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String prefix = "http.client";
        private Duration slo = Duration.ofMillis(500);
        private double[] percentiles = new double[]{0.90, 0.99};
        private boolean perUriIo = false;
        private final List<Tag> commonTags = new ArrayList<>();

        public Builder prefix(final String p) {
            this.prefix = p;
            return this;
        }

        public Builder slo(final Duration d) {
            this.slo = d;
            return this;
        }

        public Builder percentiles(final double... p) {
            if (p != null) {
                for (final double d : p) {
                    if (d < 0.0 || d > 1.0) {
                        throw new IllegalArgumentException("percentile out of range [0..1]: " + d);
                    }
                }
                this.percentiles = p.clone();
            } else {
                this.percentiles = new double[0];
            }
            return this;
        }

        public Builder perUriIo(final boolean b) {
            this.perUriIo = b;
            return this;
        }

        public Builder addCommonTag(final String k, final String v) {
            this.commonTags.add(Tag.of(k, v));
            return this;
        }

        public Builder addCommonTags(final Iterable<Tag> tags) {
            for (final Tag t : tags) {
                this.commonTags.add(t);
            }
            return this;
        }

        public MetricConfig build() {
            return new MetricConfig(this);
        }
    }
}
