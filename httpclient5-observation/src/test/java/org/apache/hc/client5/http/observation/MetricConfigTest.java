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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MetricConfigTest {

    @Test
    void builderSetsAllFields() {
        final MetricConfig mc = MetricConfig.builder()
                .prefix("custom")
                .slo(Duration.ofMillis(250))
                .percentiles(0.1)
                .perUriIo(true)
                .addCommonTag("app", "demo")
                .build();

        assertEquals("custom", mc.prefix);
        assertEquals(Duration.ofMillis(250), mc.slo);
        assertTrue(mc.perUriIo);
        assertFalse(mc.commonTags.isEmpty());
    }

    @Test
    void defaultsAreSane() {
        final MetricConfig mc = MetricConfig.builder().build();
        assertEquals("http.client", mc.prefix);
        assertNotNull(mc.slo);
        assertNotNull(mc.commonTags);
        // don’t assert on percentiles’ *type* (int vs double[]) here
    }
}
