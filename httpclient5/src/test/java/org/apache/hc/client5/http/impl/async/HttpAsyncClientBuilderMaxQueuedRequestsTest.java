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
package org.apache.hc.client5.http.impl.async;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.EndpointInfo;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

public class HttpAsyncClientBuilderMaxQueuedRequestsTest {

    @Test
    void secondSubmissionIsRejectedWhenCapIsReached() throws Exception {
        final BlockingEndpoint endpoint = new BlockingEndpoint();
        final FakeManager manager = new FakeManager(endpoint);

        final RequestConfig rc = RequestConfig.custom().build();

        try (CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
                .setDefaultRequestConfig(rc)
                .setConnectionManager(manager)
                .setMaxQueuedRequests(1)
                .build()) {

            client.start();

            final HttpClientContext ctx = HttpClientContext.create();
            ctx.setRequestConfig(rc);

            final SimpleHttpRequest r1 = SimpleRequestBuilder.get("http://localhost/").build();
            client.execute(SimpleRequestProducer.create(r1), SimpleResponseConsumer.create(), ctx,
                    new FutureCallback<SimpleHttpResponse>() {
                        @Override
                        public void completed(final SimpleHttpResponse result) {
                        }

                        @Override
                        public void failed(final Exception ex) {
                        }

                        @Override
                        public void cancelled() {
                        }
                    });

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Exception> failure = new AtomicReference<>();

            final SimpleHttpRequest r2 = SimpleRequestBuilder.get("http://localhost/second").build();
            client.execute(SimpleRequestProducer.create(r2), SimpleResponseConsumer.create(), ctx,
                    new FutureCallback<SimpleHttpResponse>() {
                        @Override
                        public void completed(final SimpleHttpResponse result) {
                        }

                        @Override
                        public void failed(final Exception ex) {
                            failure.set(ex);
                            latch.countDown();
                        }

                        @Override
                        public void cancelled() {
                            failure.set(new CancellationException("cancelled"));
                            latch.countDown();
                        }
                    });

            // With queueing semantics, r2 is queued and must not complete until r1 is finished.
            assertTrue(!latch.await(200, TimeUnit.MILLISECONDS), "second request should be queued");

            // Finish r1, release the slot -> r2 should now execute and fail (see BlockingEndpoint.execute()).
            endpoint.failFirst(new IOException("release-slot"));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "second request should complete after slot released");
            assertInstanceOf(IOException.class, failure.get(), "Expected IOException, got: " + failure.get());
        }
    }


    private static final class FakeManager implements AsyncClientConnectionManager {
        private final AsyncConnectionEndpoint endpoint;

        FakeManager(final AsyncConnectionEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public Future<AsyncConnectionEndpoint> lease(final String id,
                                                     final org.apache.hc.client5.http.HttpRoute route,
                                                     final Object state,
                                                     final Timeout requestTimeout,
                                                     final FutureCallback<AsyncConnectionEndpoint> callback) {
            final CompletableFuture<AsyncConnectionEndpoint> cf = CompletableFuture.completedFuture(endpoint);
            if (callback != null) callback.completed(endpoint);
            return cf;
        }

        @Override
        public Future<AsyncConnectionEndpoint> connect(final AsyncConnectionEndpoint endpoint,
                                                       final ConnectionInitiator connectionInitiator,
                                                       final Timeout connectTimeout,
                                                       final Object attachment,
                                                       final HttpContext context,
                                                       final FutureCallback<AsyncConnectionEndpoint> callback) {
            ((BlockingEndpoint) this.endpoint).connected = true;
            final CompletableFuture<AsyncConnectionEndpoint> cf = CompletableFuture.completedFuture(endpoint);
            if (callback != null) callback.completed(endpoint);
            return cf;
        }

        @Override
        public void upgrade(final AsyncConnectionEndpoint endpoint,
                            final Object attachment,
                            final HttpContext context) {
        }

        @Override
        public void release(final AsyncConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }

        @Override
        public void close() {
        }
    }

    private static final class BlockingEndpoint extends AsyncConnectionEndpoint {
        volatile boolean connected = true;

        private final AtomicInteger execCount = new AtomicInteger(0);
        private final AtomicReference<AsyncClientExchangeHandler> first = new AtomicReference<>();

        @Override
        public void execute(final String id,
                            final AsyncClientExchangeHandler handler,
                            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                            final HttpContext context) {
            final int n = execCount.incrementAndGet();
            if (n == 1) {
                first.set(handler); // keep slot occupied
            } else {
                handler.failed(new IOException("queued request executed"));
            }
        }

        void failFirst(final Exception ex) {
            final AsyncClientExchangeHandler h = first.getAndSet(null);
            if (h != null) {
                h.failed(ex);
            }
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
        }

        @Override
        public void close(final CloseMode closeMode) {
            connected = false;
        }

        @Override
        public EndpointInfo getInfo() {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static final class NoopInitiator implements ConnectionInitiator {
        @Override
        public Future<IOSession> connect(final NamedEndpoint endpoint,
                                         final SocketAddress remote,
                                         final SocketAddress local,
                                         final Timeout timeout,
                                         final Object attachment,
                                         final FutureCallback<IOSession> callback) {
            final CompletableFuture<IOSession> cf = new CompletableFuture<>();
            cf.completeExceptionally(new UnsupportedOperationException());
            if (callback != null) callback.failed(new UnsupportedOperationException());
            return cf;
        }
    }
}
