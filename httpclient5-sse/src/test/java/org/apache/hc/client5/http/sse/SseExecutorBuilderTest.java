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
package org.apache.hc.client5.http.sse;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;

final class SseExecutorBuilderTest {

    @Test
    void defaultParserIsCharAndBuilds() {
        final CloseableHttpAsyncClient client = new NoopAsyncClient(IOReactorStatus.ACTIVE);
        final SseExecutor exec = SseExecutor.custom()
                .setHttpClient(client)
                .build();

        assertNotNull(exec);
        final EventSource es = exec.open(URI.create("http://example.org/"), (id, type, data) -> {
        });
        assertNotNull(es);
    }

    // ---- Minimal fake client that satisfies CloseableHttpAsyncClient ----
    static final class NoopAsyncClient extends CloseableHttpAsyncClient {
        private final IOReactorStatus status;

        NoopAsyncClient(final IOReactorStatus status) {
            this.status = status != null ? status : IOReactorStatus.ACTIVE;
        }

        @Override
        public void start() { /* no-op */ }

        @Override
        public IOReactorStatus getStatus() {
            return status;
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) throws InterruptedException { /* no-op */ }

        @Override
        public void initiateShutdown() { /* no-op */ }

        @Override
        public void close(final CloseMode closeMode) { /* no-op */ }

        @Override
        public void close() { /* no-op */ }

        @Override
        protected <T> Future<T> doExecute(
                final HttpHost target,
                final AsyncRequestProducer requestProducer,
                final AsyncResponseConsumer<T> responseConsumer,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final HttpContext context,
                final FutureCallback<T> callback) {
            // We don't actually run anything here in this unit test.
            return new CompletableFuture<>();
        }

        @Override
        @Deprecated
        public void register(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
            // deprecated; not used in tests
        }
    }
}
