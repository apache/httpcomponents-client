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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;

class ObservingOptionsTest {

    @Test
    void builderWiresFields() {
        final AtomicBoolean predCalled = new AtomicBoolean(false);
        final ObservationPredicate micrometerFilter = (name, ctx) -> {
            predCalled.set(true);
            return true;
        };

        final ObservingOptions opts = ObservingOptions.builder()
                .metrics(ObservingOptions.allMetricSets())
                .tagLevel(ObservingOptions.TagLevel.EXTENDED)
                .micrometerFilter(micrometerFilter)
                .spanSampling(uri -> uri != null && uri.contains("httpbin"))
                .build();

        assertTrue(opts.metricSets.containsAll(EnumSet.allOf(ObservingOptions.MetricSet.class)));
        assertEquals(ObservingOptions.TagLevel.EXTENDED, opts.tagLevel);
        assertTrue(opts.micrometerFilter.test("x", new io.micrometer.observation.Observation.Context()));
        assertTrue(predCalled.get());
        assertTrue(opts.spanSampling.test("https://httpbin.org/get"));
        assertFalse(opts.spanSampling.test("https://example.org/"));
    }

    @Test
    void defaultIsBasicLow() {
        final ObservingOptions d = ObservingOptions.DEFAULT;
        assertTrue(d.metricSets.contains(ObservingOptions.MetricSet.BASIC));
        assertEquals(ObservingOptions.TagLevel.LOW, d.tagLevel);
    }
}
