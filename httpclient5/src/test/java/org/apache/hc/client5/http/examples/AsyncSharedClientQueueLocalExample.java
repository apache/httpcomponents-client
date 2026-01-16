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

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;

/**
 * Demonstrates client-level request throttling for the async client using a shared FIFO queue.
 * <p>
 * The example configures {@code setMaxQueuedRequests(int)} to cap the number of concurrently executing requests per
 * client instance. Requests submitted beyond that limit are queued in FIFO order and executed when the number of
 * in-flight requests drops below the configured maximum.
 *
 * @since 5.7
 */
public final class AsyncSharedClientQueueLocalExample {

    public static void main(final String[] args) throws Exception {
        final int maxConcurrentPerClient = 2; // shared per-client cap
        final int totalRequests = 50;

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(1)
                .build();

        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        final AtomicInteger serverInFlight = new AtomicInteger(0);
        final AtomicInteger serverMaxInFlight = new AtomicInteger(0);

        final HttpAsyncServer server = AsyncServerBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setCanonicalHostName("127.0.0.1")
                .register("*", new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {

                    @Override
                    public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final HttpContext context) {
                        return new BasicRequestConsumer<>(
                                entityDetails != null ? new DiscardingEntityConsumer<>() : null);
                    }

                    @Override
                    public void handle(
                            final Message<HttpRequest, Void> message,
                            final ResponseTrigger responseTrigger,
                            final HttpContext localContext) {

                        final HttpCoreContext context = HttpCoreContext.cast(localContext);

                        final int cur = serverInFlight.incrementAndGet();
                        serverMaxInFlight.updateAndGet(prev -> Math.max(prev, cur));

                        scheduler.schedule(() -> {
                            try {
                                responseTrigger.submitResponse(
                                        AsyncResponseBuilder.create(200)
                                                .setEntity("ok\n", ContentType.TEXT_PLAIN)
                                                .build(),
                                        context);
                            } catch (final Exception ex) {
                                try {
                                    responseTrigger.submitResponse(
                                            AsyncResponseBuilder.create(500)
                                                    .setEntity(ex.toString(), ContentType.TEXT_PLAIN)
                                                    .build(),
                                            context);
                                } catch (final Exception ignore) {
                                    // ignore
                                }
                            } finally {
                                serverInFlight.decrementAndGet();
                            }
                        }, 200, TimeUnit.MILLISECONDS);
                    }

                })
                .create();

        server.start();
        final ListenerEndpoint ep = server.listen(new InetSocketAddress("127.0.0.1", 0), URIScheme.HTTP).get();
        final int port = ((InetSocketAddress) ep.getAddress()).getPort();
        System.out.println("server on 127.0.0.1:" + port);

        final PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(100)
                .build();

        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setIOReactorConfig(ioReactorConfig)
                .setConnectionManager(cm)
                // This is the knob you’re implementing as "max concurrent per client + shared FIFO queue".
                .setMaxQueuedRequests(maxConcurrentPerClient)
                .build();

        client.start();

        // warmup
        final SimpleHttpRequest warmup = SimpleRequestBuilder.get("http://127.0.0.1:" + port + "/warmup").build();
        final CountDownLatch warmupLatch = new CountDownLatch(1);
        client.execute(
                SimpleRequestProducer.create(warmup),
                SimpleResponseConsumer.create(),
                new FutureCallback<SimpleHttpResponse>() {
                    @Override
                    public void completed(final SimpleHttpResponse result) {
                        System.out.println("warmup -> " + new StatusLine(result));
                        warmupLatch.countDown();
                    }

                    @Override
                    public void failed(final Exception ex) {
                        System.out.println("warmup failed -> " + ex);
                        warmupLatch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("warmup cancelled");
                        warmupLatch.countDown();
                    }
                });
        warmupLatch.await();

        final AtomicInteger ok = new AtomicInteger(0);
        final AtomicInteger failed = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(totalRequests);

        final ExecutorService exec = Executors.newFixedThreadPool(Math.min(16, totalRequests));
        final long t0 = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int id = i;
            exec.execute(() -> {
                final SimpleHttpRequest req = SimpleRequestBuilder
                        .get("http://127.0.0.1:" + port + "/slow?i=" + id)
                        .build();

                client.execute(
                        SimpleRequestProducer.create(req),
                        SimpleResponseConsumer.create(),
                        new FutureCallback<SimpleHttpResponse>() {

                            @Override
                            public void completed(final SimpleHttpResponse result) {
                                if (result.getCode() == 200) {
                                    ok.incrementAndGet();
                                } else {
                                    failed.incrementAndGet();
                                    System.out.println("FAILED i=" + id + " -> " + new StatusLine(result));
                                }
                                latch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                failed.incrementAndGet();
                                System.out.println("FAILED i=" + id + " -> " + ex);
                                latch.countDown();
                            }

                            @Override
                            public void cancelled() {
                                failed.incrementAndGet();
                                System.out.println("CANCELLED i=" + id);
                                latch.countDown();
                            }

                        });
            });
        }

        final boolean done = latch.await(120, TimeUnit.SECONDS);
        final long t1 = System.nanoTime();

        exec.shutdownNow();

        System.out.println("done=" + done
                + " ok=" + ok.get()
                + " failed=" + failed.get()
                + " clientCap=" + maxConcurrentPerClient
                + " serverMaxInFlightSeen=" + serverMaxInFlight.get()
                + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(t1 - t0));

        client.close(CloseMode.GRACEFUL);
        server.close(CloseMode.GRACEFUL);
        scheduler.shutdownNow();
    }

    private AsyncSharedClientQueueLocalExample() {
    }

}
