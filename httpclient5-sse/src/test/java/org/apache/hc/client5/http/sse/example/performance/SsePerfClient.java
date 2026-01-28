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
package org.apache.hc.client5.http.sse.example.performance;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.sse.EventSource;
import org.apache.hc.client5.http.sse.EventSourceListener;
import org.apache.hc.client5.http.sse.SseExecutor;
import org.apache.hc.client5.http.sse.impl.SseParser;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;

/**
 * Scaled SSE client harness with nano-time calibration and batched ramp-up.
 * <p>
 * Args:
 * uri connections durationSec parser(BYTE|CHAR) h2(true|false) openBatch openBatchPauseMs
 * <p>
 * Examples:
 * # Local server @ 50 eps per conn, 64B payload:
 * #   java ... SsePerfServer 8089
 * java ... SsePerfClient <a href="http://localhost:8089/sse?rate=50&size=64">...</a> 2000 120 BYTE false 200 100
 * <p>
 * # External SSE (H2 negotiation):
 * java ... SsePerfClient <a href="https://stream.wikimedia.org/v2/stream/recentchange">...</a> 1000 120 BYTE true 100 200
 */
public final class SsePerfClient {

    public static void main(final String[] args) throws Exception {
        final URI uri = URI.create(args.length > 0 ? args[0] : "http://localhost:8089/sse?rate=20&size=64");
        final int connections = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        final int durationSec = args.length > 2 ? Integer.parseInt(args[2]) : 60;
        final SseParser parser = args.length > 3 ? SseParser.valueOf(args[3]) : SseParser.BYTE;
        final boolean h2 = args.length > 4 ? Boolean.parseBoolean(args[4]) : false;
        final int openBatch = args.length > 5 ? Integer.parseInt(args[5]) : 200;
        final int openBatchPauseMs = args.length > 6 ? Integer.parseInt(args[6]) : 100;

        System.out.printf(Locale.ROOT,
                "Target=%s%nConnections=%d Duration=%ds Parser=%s H2=%s Batch=%d Pause=%dms%n",
                uri, connections, durationSec, parser, h2, openBatch, openBatchPauseMs);

        // --- Client & pool tuned for fan-out ---
        final IOReactorConfig ioCfg = IOReactorConfig.custom()
                .setIoThreadCount(Math.max(2, Runtime.getRuntime().availableProcessors()))
                .setSoKeepAlive(true)
                .setTcpNoDelay(true)
                .build();

        final PoolingAsyncClientConnectionManager connMgr =
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .setMessageMultiplexing(true) // enable H2 multiplexing if negotiated
                        .setMaxConnPerRoute(Math.max(64, connections))
                        .setMaxConnTotal(Math.max(128, connections))
                        .setDefaultTlsConfig(
                                TlsConfig.custom()
                                        .setVersionPolicy(h2 ? HttpVersionPolicy.NEGOTIATE : HttpVersionPolicy.FORCE_HTTP_1)
                                        .build())
                        .build();

        final CloseableHttpAsyncClient httpClient = HttpAsyncClientBuilder.create()
                .setIOReactorConfig(ioCfg)
                .setConnectionManager(connMgr)
                .setH2Config(H2Config.custom()
                        .setPushEnabled(false)
                        .setMaxConcurrentStreams(512)
                        .build())
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(1))
                .build();

        final ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors())),
                        new DefaultThreadFactory("sse-perf-backoff", true));
        scheduler.setRemoveOnCancelPolicy(true);

        final Executor callbacks = Runnable::run;

        // --- Metrics ---
        final AtomicInteger openCount = new AtomicInteger();
        final AtomicInteger connectedNow = new AtomicInteger();
        final AtomicLong events = new AtomicLong();
        final AtomicLong reconnects = new AtomicLong();
        final AtomicLong failures = new AtomicLong();
        final LogHistogram latencyNs = new LogHistogram();

        // --- SSE executor ---
        final SseExecutor exec = SseExecutor.custom()
                .setHttpClient(httpClient)
                .setScheduler(scheduler)
                .setCallbackExecutor(callbacks)
                .setParserStrategy(parser)
                .build();

        // --- Open connections in batches to avoid thundering herd ---
        final CountDownLatch started = new CountDownLatch(connections);
        final CountDownLatch done = new CountDownLatch(connections);

        int opened = 0;
        while (opened < connections) {
            final int toOpen = Math.min(openBatch, connections - opened);
            for (int i = 0; i < toOpen; i++) {
                final EventSource es = exec.open(uri,
                        newListener(events, reconnects, failures, openCount, connectedNow, latencyNs, done));
                es.start();
                started.countDown();
            }
            opened += toOpen;
            if (opened < connections && openBatchPauseMs > 0) {
                Thread.sleep(openBatchPauseMs);
            }
        }

        final long startMs = System.currentTimeMillis();
        final ScheduledFuture<?> reporter = scheduler.scheduleAtFixedRate(new Runnable() {
            long lastEvents = 0;
            long lastTs = System.currentTimeMillis();

            @Override
            public void run() {
                final long now = System.currentTimeMillis();
                final long ev = events.get();
                final long deltaE = ev - lastEvents;
                final long deltaMs = Math.max(1L, now - lastTs);
                final double eps = (deltaE * 1000.0) / deltaMs;

                final LogHistogram.Snapshot s = latencyNs.snapshot();
                final long p50us = s.percentile(50) / 1000;
                final long p95us = s.percentile(95) / 1000;
                final long p99us = s.percentile(99) / 1000;

                System.out.printf(Locale.ROOT,
                        "t=+%4ds con=%d open=%d ev=%d (%.0f/s) rec=%d fail=%d p50=%dµs p95=%dµs p99=%dµs%n",
                        (int) ((now - startMs) / 1000),
                        connectedNow.get(), openCount.get(), ev, eps,
                        reconnects.get(), failures.get(),
                        p50us, p95us, p99us);

                lastEvents = ev;
                lastTs = now;
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        // --- Run for duration, then shutdown ---
        started.await();
        Thread.sleep(Math.max(1, durationSec) * 1000L);

        reporter.cancel(true);
        scheduler.shutdownNow();
        exec.close();
        httpClient.close();

        done.await(5, TimeUnit.SECONDS);
        System.out.println("DONE");
    }

    private static EventSourceListener newListener(
            final AtomicLong events,
            final AtomicLong reconnects,
            final AtomicLong failures,
            final AtomicInteger openCount,
            final AtomicInteger connectedNow,
            final LogHistogram latencyNs,
            final CountDownLatch done) {

        return new EventSourceListener() {
            // Per-stream calibration state
            volatile boolean calibrated;
            volatile long nanoOffset;     // clientNano - serverNano
            volatile long lastArrivalNs;

            @Override
            public void onOpen() {
                openCount.incrementAndGet();
                connectedNow.incrementAndGet();
                lastArrivalNs = System.nanoTime();
                calibrated = false;
                nanoOffset = 0L;
            }

            @Override
            public void onEvent(final String id, final String type, final String data) {
                final long nowNano = System.nanoTime();

                if ("sync".equals(type)) {
                    final long sn = parseFieldLong(data, "tn=");
                    if (sn > 0) {
                        nanoOffset = nowNano - sn;
                        calibrated = true;
                    }
                    return;
                }

                events.incrementAndGet();

                // Prefer monotonic tn if calibrated
                final long sn = parseFieldLong(data, "tn=");
                if (calibrated && sn > 0) {
                    final long oneWayNs = nowNano - (sn + nanoOffset);
                    if (oneWayNs > 0) {
                        latencyNs.recordNanos(oneWayNs);
                    }
                } else {
                    // Fallbacks
                    final long ms = parseFieldLong(data, "t=");
                    if (ms > 0) {
                        final long oneWayNs = (System.currentTimeMillis() - ms) * 1_000_000L;
                        if (oneWayNs > 0) {
                            latencyNs.recordNanos(oneWayNs);
                        }
                    } else {
                        final long delta = nowNano - lastArrivalNs;
                        if (delta > 0) {
                            latencyNs.recordNanos(delta);
                        }
                    }
                }
                lastArrivalNs = nowNano;
            }

            @Override
            public void onClosed() {
                connectedNow.decrementAndGet();
                done.countDown();
            }

            @Override
            public void onFailure(final Throwable t, final boolean willReconnect) {
                failures.incrementAndGet();
                if (willReconnect) {
                    reconnects.incrementAndGet();
                }
            }
        };
    }

    private static long parseFieldLong(final String data, final String keyEq) {
        if (data == null) {
            return -1;
        }
        final int i = data.indexOf(keyEq);
        if (i < 0) {
            return -1;
        }
        final int j = i + keyEq.length();
        int end = j;
        while (end < data.length()) {
            final char c = data.charAt(end);
            if (c < '0' || c > '9') {
                break;
            }
            end++;
        }
        try {
            return Long.parseLong(data.substring(j, end));
        } catch (final Exception ignore) {
            return -1;
        }
    }

    // ---- Self-contained log2 histogram in nanoseconds ----
    static final class LogHistogram {
        private final LongAdder[] buckets = new LongAdder[64];

        LogHistogram() {
            for (int i = 0; i < buckets.length; i++) {
                buckets[i] = new LongAdder();
            }
        }

        void recordNanos(final long v) {
            if (v <= 0) {
                buckets[0].increment();
                return;
            }
            int idx = 63 - Long.numberOfLeadingZeros(v);
            if (idx < 0) {
                idx = 0;
            }
            else if (idx > 63) {
                idx = 63;
            }
            buckets[idx].increment();
        }

        Snapshot snapshot() {
            final long[] c = new long[64];
            long total = 0;
            for (int i = 0; i < 64; i++) {
                c[i] = buckets[i].sum();
                total += c[i];
            }
            return new Snapshot(c, total);
        }

        static final class Snapshot {
            final long[] counts;
            final long total;

            Snapshot(final long[] counts, final long total) {
                this.counts = counts;
                this.total = total;
            }

            long percentile(final double p) {
                if (total == 0) {
                    return 0;
                }
                long rank = (long) Math.ceil((p / 100.0) * total);
                if (rank <= 0) {
                    rank = 1;
                }
                long cum = 0;
                for (int i = 0; i < 64; i++) {
                    cum += counts[i];
                    if (cum >= rank) {
                        return (i == 63) ? Long.MAX_VALUE : ((1L << (i + 1)) - 1);
                    }
                }
                return (1L << 63) - 1;
            }
        }
    }
}
