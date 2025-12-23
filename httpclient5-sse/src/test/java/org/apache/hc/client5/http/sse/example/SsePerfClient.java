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
package org.apache.hc.client5.http.sse.example;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.sse.EventSource;
import org.apache.hc.client5.http.sse.EventSourceConfig;
import org.apache.hc.client5.http.sse.EventSourceListener;
import org.apache.hc.client5.http.sse.SseExecutor;
import org.apache.hc.client5.http.sse.impl.ExponentialJitterBackoff;
import org.apache.hc.client5.http.sse.impl.SseParser;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;

/**
 * Synthetic SSE load client.
 * <p>
 * Opens N concurrent SSE subscriptions and reports throughput once per second. Useful for quick
 * benchmarking of backoff policies and server implementations.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>
 *   SsePerfClient &lt;uri&gt; [streams] [durationSeconds] [--h2] [--quiet]
 * </pre>
 *
 * <ul>
 *   <li>{@code --h2} forces HTTP/2 (TLS+ALPN endpoints only). If the origin cannot do H2, the run fails.</li>
 *   <li>{@code --quiet} disables per-event logging.</li>
 * </ul>
 *
 * <p>
 * Defaults: {@code uri=http://localhost:8080/events}, {@code streams=16}, {@code durationSeconds=30}.
 * </p>
 */
public final class SsePerfClient {

    private static int intArg(final String[] args, final int idx, final int def) {
        return args.length > idx ? Integer.parseInt(args[idx]) : def;
    }

    private static boolean hasFlag(final String[] args, final String flag) {
        for (final String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }

    public static void main(final String[] args) throws Exception {
        final URI uri = URI.create(args.length > 0 ? args[0] : "http://localhost:8080/events");
        final int streams = intArg(args, 1, 16);
        final int durationSeconds = intArg(args, 2, 30);
        final boolean forceH2 = hasFlag(args, "--h2");
        final boolean quiet = hasFlag(args, "--quiet");

        final IOReactorConfig ioCfg = IOReactorConfig.custom()
                .setIoThreadCount(Math.max(2, Runtime.getRuntime().availableProcessors()))
                .setSoKeepAlive(true)
                .setTcpNoDelay(true)
                .build();

        final HttpVersionPolicy versionPolicy = forceH2 ? HttpVersionPolicy.FORCE_HTTP_2 : HttpVersionPolicy.NEGOTIATE;

        final PoolingAsyncClientConnectionManager connMgr =
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .useSystemProperties()
                        .setMessageMultiplexing(true)
                        .setMaxConnPerRoute(16)
                        .setMaxConnTotal(16)
                        .setDefaultTlsConfig(TlsConfig.custom()
                                .setVersionPolicy(versionPolicy)
                                .build())
                        .build();

        final CloseableHttpAsyncClient httpClient = HttpAsyncClientBuilder.create()
                .setIOReactorConfig(ioCfg)
                .setConnectionManager(connMgr)
                .setH2Config(H2Config.custom()
                        .setPushEnabled(false)
                        .setMaxConcurrentStreams(Math.max(64, streams * 4))
                        .build())
                .useSystemProperties()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(1))
                .build();

        final ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(2, new DefaultThreadFactory("sse-perf", true));
        scheduler.setRemoveOnCancelPolicy(true);

        final Executor callbacks = new Executor() {
            @Override
            public void execute(final Runnable command) {
                command.run();
            }
        };

        final EventSourceConfig cfg = EventSourceConfig.builder()
                .backoff(new ExponentialJitterBackoff(250L, 10_000L, 2.0, 100L))
                .maxReconnects(-1)
                .build();

        final Map<String, String> defaultHeaders = new HashMap<String, String>();
        defaultHeaders.put("User-Agent", "Apache-HttpClient-SSE/5.x");
        defaultHeaders.put("Accept", "text/event-stream");

        final SseExecutor exec = SseExecutor.custom()
                .setHttpClient(httpClient)
                .setScheduler(scheduler)
                .setCallbackExecutor(callbacks)
                .setEventSourceConfig(cfg)
                .setDefaultHeaders(defaultHeaders)
                .setParserStrategy(SseParser.BYTE)
                .build();

        final LongAdder eventsPerSec = new LongAdder();
        final LongAdder charsPerSec = new LongAdder();
        final LongAdder totalEvents = new LongAdder();
        final LongAdder totalChars = new LongAdder();

        final AtomicInteger opens = new AtomicInteger(0);
        final AtomicInteger closes = new AtomicInteger(0);
        final AtomicInteger failures = new AtomicInteger(0);
        final AtomicInteger reconnectingFailures = new AtomicInteger(0);

        final EventSource[] sources = new EventSource[streams];
        final CountDownLatch done = new CountDownLatch(streams);

        for (int i = 0; i < streams; i++) {
            final int idx = i;

            final EventSourceListener listener = new EventSourceListener() {

                @Override
                public void onOpen() {
                    opens.incrementAndGet();
                    if (!quiet) {
                        System.out.printf(Locale.ROOT, "[SSE/%d] open%n", idx);
                    }
                }

                @Override
                public void onEvent(final String id, final String type, final String data) {
                    eventsPerSec.increment();
                    totalEvents.increment();

                    if (data != null) {
                        final int len = data.length();
                        charsPerSec.add(len);
                        totalChars.add(len);
                    }

                    if (!quiet) {
                        final String t = type != null ? type : "message";
                        System.out.printf(Locale.ROOT, "[SSE/%d] %s id=%s%n", idx, t, id);
                    }
                }

                @Override
                public void onClosed() {
                    closes.incrementAndGet();
                    done.countDown();
                    if (!quiet) {
                        System.out.printf(Locale.ROOT, "[SSE/%d] closed%n", idx);
                    }
                }

                @Override
                public void onFailure(final Throwable t, final boolean willReconnect) {
                    failures.incrementAndGet();
                    if (willReconnect) {
                        reconnectingFailures.incrementAndGet();
                    }
                    if (!quiet) {
                        System.err.printf(Locale.ROOT, "[SSE/%d] failure: %s willReconnect=%s%n",
                                idx, t, Boolean.toString(willReconnect));
                    }
                    if (!willReconnect) {
                        done.countDown();
                    }
                }
            };

            sources[i] = exec.open(
                    uri,
                    new HashMap<String, String>(),
                    listener,
                    cfg,
                    SseParser.BYTE,
                    scheduler,
                    callbacks
            );
        }

        final long startNanos = System.nanoTime();

        scheduler.scheduleAtFixedRate(() -> {
            final long ev = eventsPerSec.sumThenReset();
            final long ch = charsPerSec.sumThenReset();
            final double elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;

            System.out.printf(Locale.ROOT,
                    "[perf] t=%.0fs streams=%d eps=%d chars/s=%d open=%d closed=%d fail=%d (reconn=%d) totalEv=%d totalChars=%d pool=%s%n",
                    elapsedSec,
                    streams,
                    ev,
                    ch,
                    opens.get(),
                    closes.get(),
                    failures.get(),
                    reconnectingFailures.get(),
                    totalEvents.sum(),
                    totalChars.sum(),
                    connMgr.getTotalStats());
        }, 1, 1, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (final EventSource es : sources) {
                if (es != null) {
                    es.cancel();
                }
            }
            try {
                exec.close();
            } catch (final Exception ignore) {
            }
            scheduler.shutdownNow();
        }, "sse-perf-shutdown"));

        for (final EventSource es : sources) {
            es.start();
        }

        scheduler.schedule(() -> {
            for (final EventSource es : sources) {
                if (es != null) {
                    es.cancel();
                }
            }
        }, durationSeconds, TimeUnit.SECONDS);

        done.await();

        for (final EventSource es : sources) {
            if (es != null) {
                es.cancel();
            }
        }
        exec.close();
        scheduler.shutdownNow();
    }

}
