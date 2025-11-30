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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.sse.impl.SseParser;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;

final class DefaultEventSourceIntegrationTest {

    @Test
    void openStreamReceivesEventAndCloses() throws Exception {
        final CapturingClient fake = new CapturingClient();
        final SseExecutor exec = SseExecutor.newInstance(fake);

        final CountDownLatch opened = new CountDownLatch(1);
        final CountDownLatch got = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);

        final EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onOpen() {
                opened.countDown();
            }

            @Override
            public void onEvent(final String id, final String type, final String data) {
                if ("1".equals(id) && "ping".equals(type) && "hello".equals(data)) {
                    got.countDown();
                }
            }

            @Override
            public void onClosed() {
                closed.countDown();
            }
        };

        final EventSource es = exec.open(
                new URI("http://example.org/sse"),
                Collections.emptyMap(),
                listener,
                EventSourceConfig.DEFAULT,
                SseParser.BYTE,
                null,
                null);

        es.start();

        final AsyncResponseConsumer<Void> c = fake.lastConsumer;
        assertNotNull(c, "consumer captured");

        c.consumeResponse(
                new BasicHttpResponse(HttpStatus.SC_OK, "OK"),
                new TestEntityDetails("text/event-stream"),
                new BasicHttpContext(),                                   // FIX: concrete HttpContext
                new FutureCallback<Void>() {
                    @Override
                    public void completed(final Void result) {
                    }

                    @Override
                    public void failed(final Exception ex) {
                    }

                    @Override
                    public void cancelled() {
                    }
                });

        c.consume(ByteBuffer.wrap("id: 1\nevent: ping\n".getBytes(StandardCharsets.UTF_8)));
        c.consume(ByteBuffer.wrap("data: hello\n\n".getBytes(StandardCharsets.UTF_8)));
        c.streamEnd(null);

        assertTrue(opened.await(1, TimeUnit.SECONDS), "opened");
        assertTrue(got.await(1, TimeUnit.SECONDS), "event received");

        es.cancel();
        assertTrue(closed.await(1, TimeUnit.SECONDS), "closed");

        exec.close();
    }

    // ---- fake async client that captures the consumer & callback via doExecute() ----
    static final class CapturingClient extends CloseableHttpAsyncClient {
        volatile AsyncResponseConsumer<Void> lastConsumer;
        volatile FutureCallback<Void> lastCallback;
        volatile boolean closed;

        @Override
        public void start() { /* no-op */ }

        @Override
        public IOReactorStatus getStatus() {
            return closed ? IOReactorStatus.SHUT_DOWN : IOReactorStatus.ACTIVE;
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) throws InterruptedException { /* no-op */ }

        @Override
        public void initiateShutdown() { /* no-op */ }

        @Override
        public void close(final CloseMode closeMode) {
            closed = true;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        protected <T> Future<T> doExecute(
                final HttpHost target,
                final AsyncRequestProducer requestProducer,
                final AsyncResponseConsumer<T> responseConsumer,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final HttpContext context,
                final FutureCallback<T> callback) {

            @SuppressWarnings("unchecked") final AsyncResponseConsumer<Void> c = (AsyncResponseConsumer<Void>) responseConsumer;
            this.lastConsumer = c;

            @SuppressWarnings("unchecked") final FutureCallback<Void> cb = (FutureCallback<Void>) callback;
            this.lastCallback = cb;

            return new CompletableFuture<>(); // never completed; fine for this test
        }

        @Override
        @Deprecated
        public void register(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
            // deprecated; not used
        }
    }

    // Minimal EntityDetails stub
    static final class TestEntityDetails implements EntityDetails {
        private final String ct;

        TestEntityDetails(final String ct) {
            this.ct = ct;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public String getContentType() {
            return ct;
        }

        @Override
        public String getContentEncoding() {
            return null;
        }

        @Override
        public boolean isChunked() {
            return true;
        }

        @Override
        public Set<String> getTrailerNames() {
            return Collections.<String>emptySet();
        }
    }
}
