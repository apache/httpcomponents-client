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
package org.apache.http.client.async;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Collection of different counters used to gather metrics for {@link HttpAsyncClientWithFuture}.
 */
public class ConnectionMetrics {

    final AtomicLong activeConnections = new AtomicLong();
    final AtomicLong scheduledConnections = new AtomicLong();
    final DurationCounter successfulConnections = new DurationCounter();
    final DurationCounter failedConnections = new DurationCounter();
    final DurationCounter requests = new DurationCounter();
    final DurationCounter tasks = new DurationCounter();

    public ConnectionMetrics() {
    }

    public String metricsAsJson() {
        final StringBuilder buf = new StringBuilder();
        buf.append("{\n");
        buf.append("  \"totalConnections\":" + requests.count() + ",\n");
        buf.append("  \"failedConnections\":" + failedConnections + ",\n");
        buf.append("  \"successfulConnections\":" + successfulConnections + ",\n");
        buf.append("  \"averageRequestDuration\":" + requests.averageDuration() + ",\n");
        buf.append("  \"averageTaskDuration\":" + tasks.averageDuration() + ",\n");
        buf.append("  \"activeConnections\":" + activeConnections + ",\n");
        buf.append("  \"scheduledConnections\":" + scheduledConnections + "\n");
        buf.append("}\n");

        return buf.toString();
    }

    public long activeConnections() {
        return activeConnections.get();
    }

    public long scheduledConnections() {
        return scheduledConnections.get();
    }

    @Override
    public String toString() {
        return metricsAsJson();
    }

    /**
     * A counter that can measure duration and number of events.
     */
    public static class DurationCounter {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong cumulativeDuration = new AtomicLong(0);

        public void increment(final long startTime) {
            count.incrementAndGet();
            cumulativeDuration.addAndGet(System.currentTimeMillis() - startTime);
        }

        public long count() {
            return count.get();
        }

        public long averageDuration() {
            final long counter = count.get();
            return cumulativeDuration.get() / counter;
        }
    }

}