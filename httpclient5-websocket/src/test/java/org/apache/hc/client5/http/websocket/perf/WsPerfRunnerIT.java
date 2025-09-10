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
package org.apache.hc.client5.http.websocket.perf;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WsPerfRunnerIT {

    private static JettyEchoServer srv;

    @BeforeAll
    static void up() throws Exception {
        srv = new JettyEchoServer();
        srv.start();
    }

    @AfterAll
    static void down() throws Exception {
        srv.stop();
    }

    @Test
    void throughput_sample() throws Exception {
        WsPerfRunner.main(new String[]{
                "mode=THROUGHPUT",
                "uri=" + srv.uri(),     // e.g., ws://127.0.0.1:PORT/
                "clients=12",
                "durationSec=10",
                "bytes=512",
                "inflight=32",
                "pmce=true",
                "compressible=true"
        });
    }


    @Test
    void latency_sample() throws Exception {
        WsPerfRunner.main(new String[]{
                "mode=LATENCY",
                "uri=" + srv.uri(),
                "clients=4",
                "durationSec=10",
                "bytes=64",
                "inflight=4",
                "pmce=false",
                "compressible=false"
        });
    }

    @Test
    void throughput_non_compressible_sample() throws Exception {
        WsPerfRunner.main(new String[]{
                "mode=THROUGHPUT",
                "uri=" + srv.uri(),
                "clients=12",
                "durationSec=10",
                "bytes=512",
                "inflight=32",
                // PMCE negotiated, but payload is high-entropy (random-ish)
                "pmce=true",
                "compressible=false"
        });
    }
}