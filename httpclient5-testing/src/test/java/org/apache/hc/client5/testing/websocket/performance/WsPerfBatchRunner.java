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
package org.apache.hc.client5.testing.websocket.performance;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch runner for H1/H2 WebSocket performance scenarios.
 */
public final class WsPerfBatchRunner {

    private WsPerfBatchRunner() {
    }

    public static void main(final String[] args) throws Exception {
        final List<String[]> scenarios = new ArrayList<>();

        scenarios.add(new String[]{
                "protocol=h1",
                "mode=THROUGHPUT",
                "clients=8",
                "durationSec=10",
                "bytes=512",
                "inflight=32",
                "pmce=false",
                "compressible=true"
        });
        scenarios.add(new String[]{
                "protocol=h1",
                "mode=THROUGHPUT",
                "clients=8",
                "durationSec=10",
                "bytes=512",
                "inflight=32",
                "pmce=false",
                "compressible=false"
        });
        scenarios.add(new String[]{
                "protocol=h2",
                "mode=THROUGHPUT",
                "clients=8",
                "durationSec=10",
                "bytes=512",
                "inflight=32",
                "pmce=false",
                "compressible=false"
        });
        scenarios.add(new String[]{
                "protocol=h2",
                "mode=THROUGHPUT",
                "clients=8",
                "durationSec=10",
                "bytes=512",
                "inflight=32",
                "pmce=true",
                "compressible=false"
        });
        scenarios.add(new String[]{
                "protocol=h2",
                "mode=LATENCY",
                "clients=4",
                "durationSec=10",
                "bytes=64",
                "inflight=4",
                "pmce=false",
                "compressible=false"
        });

        final int total = scenarios.size();
        for (int i = 0; i < total; i++) {
            final String[] scenario = scenarios.get(i);
            System.out.println("\n[PERF] Scenario " + (i + 1) + "/" + total + ": " + String.join(" ", scenario));
            WsPerfHarness.main(scenario);
        }
    }
}
