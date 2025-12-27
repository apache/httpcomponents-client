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
package org.apache.hc.client5.http.examples;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;

/**
 * Demonstrates capping the number of queued / in-flight request executions within the internal
 * async execution pipeline using {@link org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder#setMaxQueuedRequests(int)}.
 * <p>
 * When the cap is reached, submissions may fail fast with {@link java.util.concurrent.RejectedExecutionException}.
 */
public class AsyncClientQueueCap {

    public static void main(final String[] args) throws Exception {

        final int maxQueuedRequests = 2;

        final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(1)
                .setMaxConnPerRoute(1)
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                        .build())
                .setMessageMultiplexing(true)
                .build();

        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setMaxQueuedRequests(maxQueuedRequests)
                .setIOReactorConfig(IOReactorConfig.DEFAULT)
                .setConnectionManager(connectionManager)
                .setH2Config(H2Config.DEFAULT)
                .build();

        client.start();

        final HttpHost target = new HttpHost("https", "httpbingo.org");

        final SimpleHttpRequest warmup = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/get")
                .build();

        final CountDownLatch warmupLatch = new CountDownLatch(1);

        System.out.println("Executing warm-up request " + warmup);
        client.execute(
                SimpleRequestProducer.create(warmup),
                SimpleResponseConsumer.create(),
                new FutureCallback<SimpleHttpResponse>() {

                    @Override
                    public void completed(final SimpleHttpResponse response) {
                        System.out.println(warmup + "->" + new StatusLine(response));
                        warmupLatch.countDown();
                    }

                    @Override
                    public void failed(final Exception ex) {
                        System.out.println(warmup + "->" + ex);
                        warmupLatch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        System.out.println(warmup + " cancelled");
                        warmupLatch.countDown();
                    }

                });

        warmupLatch.await();

        final List<SimpleHttpRequest> batch1 = new ArrayList<>();
        batch1.add(SimpleRequestBuilder.get().setHttpHost(target)
                .setPath("/drip?numbytes=100&duration=8&delay=0&code=200&i=0").build());
        batch1.add(SimpleRequestBuilder.get().setHttpHost(target)
                .setPath("/drip?numbytes=100&duration=8&delay=0&code=200&i=1").build());

        final List<SimpleHttpRequest> batch2 = new ArrayList<>();
        batch2.add(SimpleRequestBuilder.get().setHttpHost(target)
                .setPath("/drip?numbytes=100&duration=8&delay=0&code=200&i=2").build());
        batch2.add(SimpleRequestBuilder.get().setHttpHost(target)
                .setPath("/drip?numbytes=100&duration=8&delay=0&code=200&i=3").build());

        final CountDownLatch latch1 = new CountDownLatch(batch1.size());
        final CountDownLatch latchAttempt2 = new CountDownLatch(batch2.size());
        final AtomicInteger rejected = new AtomicInteger(0);

        System.out.println("Submitting first batch (expected to execute)");
        for (final SimpleHttpRequest request : batch1) {
            System.out.println("Executing request " + request);
            client.execute(
                    SimpleRequestProducer.create(request),
                    SimpleResponseConsumer.create(),
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(request + "->" + new StatusLine(response));
                            latch1.countDown();
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(request + "->" + ex);
                            latch1.countDown();
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(request + " cancelled");
                            latch1.countDown();
                        }

                    });
        }

        System.out.println("Submitting second batch immediately (may reject)");
        for (final SimpleHttpRequest request : batch2) {
            System.out.println("Executing request " + request);
            client.execute(
                    SimpleRequestProducer.create(request),
                    SimpleResponseConsumer.create(),
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(request + "->" + new StatusLine(response));
                            latchAttempt2.countDown();
                        }

                        @Override
                        public void failed(final Exception ex) {
                            if (ex instanceof RejectedExecutionException) {
                                rejected.incrementAndGet();
                                System.out.println(request + "-> rejected: " + ex.getMessage());
                            } else {
                                System.out.println(request + "->" + ex);
                            }
                            latchAttempt2.countDown();
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(request + " cancelled");
                            latchAttempt2.countDown();
                        }

                    });
        }

        System.out.println("Waiting for first batch to complete");
        latch1.await();

        System.out.println("Waiting for second batch completion");
        latchAttempt2.await();

        if (rejected.get() == batch2.size()) {
            System.out.println("Re-submitting second batch after completion (should execute now)");
            final CountDownLatch latch2 = new CountDownLatch(batch2.size());
            for (final SimpleHttpRequest request : batch2) {
                System.out.println("Executing request " + request);
                client.execute(
                        SimpleRequestProducer.create(request),
                        SimpleResponseConsumer.create(),
                        new FutureCallback<SimpleHttpResponse>() {

                            @Override
                            public void completed(final SimpleHttpResponse response) {
                                System.out.println(request + "->" + new StatusLine(response));
                                latch2.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                System.out.println(request + "->" + ex);
                                latch2.countDown();
                            }

                            @Override
                            public void cancelled() {
                                System.out.println(request + " cancelled");
                                latch2.countDown();
                            }

                        });
            }
            latch2.await();
        }

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
    }

}
