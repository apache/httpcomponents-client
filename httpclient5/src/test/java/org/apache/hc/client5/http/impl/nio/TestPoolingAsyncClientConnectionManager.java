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

package org.apache.hc.client5.http.impl.nio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestPoolingAsyncClientConnectionManager {

    private PoolingAsyncClientConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setSocketTimeout(Timeout.ofSeconds(10))
                        .build())
                .build();
    }

    @Test
    void testWarmUpConnections() {
        final HttpHost targetHost = new HttpHost("http", "httpbin.org", 80);
        final CompletableFuture<Void> warmUpFuture = new CompletableFuture<>();

        connectionManager.warmUp(targetHost, Timeout.ofSeconds(10), new FutureCallback() {
            @Override
            public void completed(final Object o) {
                warmUpFuture.complete(null);
            }

            @Override
            public void failed(final Exception ex) {
                warmUpFuture.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                warmUpFuture.cancel(true);
            }
        });

        // Assert that the warm-up completes within a reasonable time
        assertDoesNotThrow(() -> warmUpFuture.get(15, TimeUnit.SECONDS), "Warm-up should complete without exceptions.");
        assertTrue(warmUpFuture.isDone() && !warmUpFuture.isCompletedExceptionally(), "Warm-up should complete successfully.");
    }
}