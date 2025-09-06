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
package org.apache.hc.client5.testing.async;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for hard end-to-end request timeout (query timeout / request deadline).
 * Uses millisecond delays to keep the suite fast and deterministic.
 */
class TestRequestTimeoutAsync extends AbstractIntegrationTestBase {

    TestRequestTimeoutAsync() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD);
    }

    /**
     * Async handler responding after a millisecond delay from /mdelay/{millis}.
     */
    private static final class AsyncDelayMsHandler implements AsyncServerExchangeHandler {

        private final ScheduledExecutorService sched =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    final Thread t = new Thread(r, "async-delayms-handler");
                    t.setDaemon(true);
                    return t;
                });

        @Override
        public void handleRequest(
                final HttpRequest request,
                final org.apache.hc.core5.http.EntityDetails entityDetails,
                final ResponseChannel responseChannel,
                final HttpContext context) {

            long millis = 100;
            final String path = request.getRequestUri();  // e.g. /mdelay/250
            final int idx = path.lastIndexOf('/');
            if (idx >= 0 && idx + 1 < path.length()) {
                try {
                    millis = Long.parseLong(path.substring(idx + 1));
                } catch (final Exception ignore) { /* keep default */ }
            }

            final BasicHttpResponse response = new BasicHttpResponse(200, "OK");

            // schedule without blocking I/O threads
            final long delay = Math.max(0L, millis);
            sched.schedule(() -> {
                try {
                    responseChannel.sendResponse(
                            response,
                            new StringAsyncEntityProducer("{\"ok\":true}", ContentType.APPLICATION_JSON),
                            context);
                } catch (final Exception ex) {
                    failed(ex);
                }
            }, delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            src.position(src.limit()); // discard any request body
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers)
                throws IOException {
            // no-op
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) { /* no-op */ }

        // ---- Lifecycle ----
        @Override
        public void failed(final Exception cause) { /* no-op in tests */ }

        @Override
        public void releaseResources() {
            sched.shutdownNow();
        }
    }

    @Test
    void timesOutHard() throws Exception {
        configureServer(b -> b.register("/mdelay/*", AsyncDelayMsHandler::new));
        final HttpHost target = startServer();
        final TestAsyncClient client = startClient();

        final SimpleHttpRequest req = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/mdelay/5000") // 5s server delay
                .build();
        req.setConfig(RequestConfig.custom()
                .setRequestTimeout(Timeout.ofMilliseconds(100)) // 100ms hard deadline
                .build());

        final Future<SimpleHttpResponse> f = client.execute(req, null);
        try {
            f.get(5, TimeUnit.SECONDS);
            fail("Expected ExecutionException due to hard request timeout");
        } catch (final ExecutionException ex) {
            assertTrue(ex.getCause() instanceof InterruptedIOException,
                    "Cause should be InterruptedIOException");
        } catch (final TimeoutException te) {
            fail("Request did not time out as expected (test wait timed out)");
        }
    }

    @Test
    @Disabled
    void succeedsWithinBudget() throws Exception {
        configureServer(b -> b.register("/mdelay/*", AsyncDelayMsHandler::new));
        final HttpHost target = startServer();
        final TestAsyncClient client = startClient();

        final SimpleHttpRequest req = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/mdelay/100")
                .build();

        req.setConfig(RequestConfig.custom()
                .setRequestTimeout(Timeout.ofSeconds(10))
                .build());

        final Future<SimpleHttpResponse> f = client.execute(req, null);
        final SimpleHttpResponse resp = f.get();

        assertThat(resp, notNullValue());
        assertThat(resp.getCode(), equalTo(200));
        assertThat(resp.getBodyText(), notNullValue());
    }

    @Test
    void nearImmediateExpirationFailsQuickly() throws Exception {
        configureServer(b -> b.register("/mdelay/*", AsyncDelayMsHandler::new));
        final HttpHost target = startServer();
        final TestAsyncClient client = startClient();

        final SimpleHttpRequest req = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/mdelay/5000") // 5s server delay
                .build();
        // Tiny positive timeout; deterministic & very fast failure
        req.setConfig(RequestConfig.custom()
                .setRequestTimeout(Timeout.ofMilliseconds(50))
                .build());

        final Future<SimpleHttpResponse> f = client.execute(req, null);
        try {
            f.get(3, TimeUnit.SECONDS);
            fail("Expected ExecutionException due to near-immediate hard timeout");
        } catch (final ExecutionException ex) {
            assertTrue(ex.getCause() instanceof InterruptedIOException,
                    "Cause should be InterruptedIOException");
        } catch (final TimeoutException te) {
            fail("Future did not complete promptly for near-immediate timeout");
        }
    }
}
