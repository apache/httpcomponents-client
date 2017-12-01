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

package org.apache.http.client.utils;

import com.codahale.metrics.MetricRegistry;

/**
 * utilities for {@link com.codahale.metrics.MetricRegistry}
 *
 * @since 4.5.5
 */
public class MetricUtils {
    /**
     * increase counter
     * @param metricRegistry
     * @param itemName
     * @param value
     */
    public static void inc(final MetricRegistry metricRegistry, final String itemName, final long value) {
        if (metricRegistry != null) {
            try {
                metricRegistry.counter(itemName).inc(value);
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    /**
     * decrease counter
     * @param metricRegistry
     * @param itemName
     * @param value
     */
    public static void dec(final MetricRegistry metricRegistry, final String itemName, final long value) {
        if (metricRegistry != null) {
            try {
                metricRegistry.counter(itemName).dec(value);
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    /**
     * mark meter
     * @param metricRegistry
     * @param itemName
     * @param value
     */
    public static void mark(final MetricRegistry metricRegistry, final String itemName, final long value) {
        if (metricRegistry != null) {
            try {
                metricRegistry.meter(itemName).mark(value);
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    /**
     * update histogram
     * @param metricRegistry
     * @param itemName
     * @param value
     */
    public static void update(final MetricRegistry metricRegistry, final String itemName, final long value) {
        if (metricRegistry != null) {
            try {
                metricRegistry.histogram(itemName).update(value);
            } catch (Exception e) {
                // do nothing
            }
        }
    }
}
