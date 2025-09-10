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

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
import org.apache.hc.client5.http.websocket.client.WebSocketClientBuilder;

public final class WsPerfRunner {

    public static void main(final String[] args) throws Exception {
        final Args a = Args.parse(args);
        System.out.printf(Locale.ROOT,
                "mode=%s uri=%s clients=%d durationSec=%d bytes=%d inflight=%d pmce=%s compressible=%s%n",
                a.mode, a.uri, a.clients, a.durationSec, a.bytes, a.inflight, a.pmce, a.compressible);

        final ExecutorService pool = Executors.newFixedThreadPool(Math.min(a.clients, 64));
        final AtomicLong sends = new AtomicLong();
        final AtomicLong recvs = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final ConcurrentLinkedQueue<Long> lats = new ConcurrentLinkedQueue<>();
        final CountDownLatch ready = new CountDownLatch(a.clients);
        final CountDownLatch done = new CountDownLatch(a.clients);

        final byte[] payload = a.compressible ? makeCompressible(a.bytes) : makeRandom(a.bytes);
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(a.durationSec);

        for (int i = 0; i < a.clients; i++) {
            final int id = i;
            pool.submit(() -> runClient(id, a, payload, sends, recvs, errors, lats, ready, done, deadline));
        }

        ready.await(); // all connected
        done.await();  // test finished
        pool.shutdown();

        final long totalRecv = recvs.get();
        final long totalSend = sends.get();
        final double secs = a.durationSec;
        final double msgps = totalRecv / secs;
        final double mbps = (totalRecv * (long) a.bytes) / (1024.0 * 1024.0) / secs;

        System.out.printf(Locale.ROOT, "sent=%d recv=%d errors=%d%n", totalSend, totalRecv, errors.get());
        System.out.printf(Locale.ROOT, "throughput: %.0f msg/s, %.2f MiB/s%n", msgps, mbps);

        if (!lats.isEmpty()) {
            final long[] arr = lats.stream().mapToLong(Long::longValue).toArray();
            Arrays.sort(arr);
            System.out.printf(Locale.ROOT,
                    "latency (ms): p50=%.3f p95=%.3f p99=%.3f max=%.3f samples=%d%n",
                    nsToMs(p(arr, 0.50)), nsToMs(p(arr, 0.95)), nsToMs(p(arr, 0.99)), nsToMs(arr[arr.length - 1]), arr.length);
        }
    }

    private static void runClient(
            final int id, final Args a, final byte[] payload,
            final AtomicLong sends, final AtomicLong recvs, final AtomicLong errors,
            final ConcurrentLinkedQueue<Long> lats,
            final CountDownLatch ready, final CountDownLatch done, final long deadlineNanos) {

        // Per-connection WebSocket config
        final WebSocketClientConfig.Builder b = WebSocketClientConfig.custom()
                .setConnectTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(5))
//                .setExchangeTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(5))
                .setCloseWaitTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(3))
                .setOutgoingChunkSize(4096)
                .setAutoPong(true);

        if (a.pmce) {
            b.enablePerMessageDeflate(true)
                    .offerClientNoContextTakeover(false)
                    .offerServerNoContextTakeover(false)
                    .offerClientMaxWindowBits(null)
                    .offerServerMaxWindowBits(null);
        }
        final WebSocketClientConfig cfg = b.build();

        // Build a client instance (closeable) with our default per-connection config
        try (final CloseableWebSocketClient client =
                     WebSocketClientBuilder.create()
                             .defaultConfig(cfg)
                             .build()) {

            final AtomicInteger inflight = new AtomicInteger();
            final AtomicBoolean open = new AtomicBoolean(false);

            final CompletableFuture<WebSocket> cf = client.connect(
                    URI.create(a.uri),
                    new WebSocketListener() {
                        @Override
                        public void onOpen(final WebSocket ws) {
                            open.set(true);
                            ready.countDown();
                            // Prime in-flight
                            for (int j = 0; j < a.inflight; j++) {
                                sendOne(ws, a, payload, sends, inflight);
                            }
                        }

                        @Override
                        public void onBinary(final ByteBuffer p, final boolean last) {
                            final long t1 = System.nanoTime();
                            if (a.mode == Mode.LATENCY) {
                                if (p.remaining() >= 8) {
                                    final long t0 = p.getLong(p.position());
                                    lats.add(t1 - t0);
                                }
                            }
                            recvs.incrementAndGet();
                            inflight.decrementAndGet();
                        }

                        @Override
                        public void onError(final Throwable ex) {
                            errors.incrementAndGet();
                        }

                        @Override
                        public void onClose(final int code, final String reason) {
                            open.set(false);
                        }
                    });

            try {
                final WebSocket ws = cf.get(15, TimeUnit.SECONDS);

                // Main loop: keep target inflight until deadline
                while (System.nanoTime() < deadlineNanos) {
                    while (open.get() && inflight.get() < a.inflight) {
                        sendOne(ws, a, payload, sends, inflight);
                    }
                    // backoff a bit
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                }

                // Drain a bit more
                Thread.sleep(200);
                ws.close(1000, "bye");
            } catch (final Exception e) {
                errors.incrementAndGet();
                // If connect failed early, make sure the "all connected" gate doesn't block the whole run
                ready.countDown();
            } finally {
                done.countDown();
            }
        } catch (final Exception ignore) {
        }
    }

    private static void sendOne(final WebSocket ws, final Args a, final byte[] payload,
                                final AtomicLong sends, final AtomicInteger inflight) {
        final ByteBuffer p = ByteBuffer.allocate(payload.length + 8);
        final long t0 = System.nanoTime();
        p.putLong(t0).put(payload).flip();
        if (ws.sendBinary(p, true)) {
            inflight.incrementAndGet();
            sends.incrementAndGet();
        }
    }


    private enum Mode { THROUGHPUT, LATENCY }

    private static final class Args {
        String uri = "ws://localhost:8080/echo";
        int clients = 8;
        int durationSec = 15;
        int bytes = 512;
        int inflight = 32;
        boolean pmce = false;
        boolean compressible = true;
        Mode mode = Mode.THROUGHPUT;

        static Args parse(final String[] a) {
            final Args r = new Args();
            for (final String s : a) {
                final String[] kv = s.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                switch (kv[0]) {
                    case "uri":
                        r.uri = kv[1];
                        break;
                    case "clients":
                        r.clients = Integer.parseInt(kv[1]);
                        break;
                    case "durationSec":
                        r.durationSec = Integer.parseInt(kv[1]);
                        break;
                    case "bytes":
                        r.bytes = Integer.parseInt(kv[1]);
                        break;
                    case "inflight":
                        r.inflight = Integer.parseInt(kv[1]);
                        break;
                    case "pmce":
                        r.pmce = Boolean.parseBoolean(kv[1]);
                        break;
                    case "compressible":
                        r.compressible = Boolean.parseBoolean(kv[1]);
                        break;
                    case "mode":
                        r.mode = Mode.valueOf(kv[1]);
                        break;
                }
            }
            return r;
        }
    }

    private static byte[] makeCompressible(final int n) {
        final byte[] b = new byte[n];
        Arrays.fill(b, (byte) 'A');
        return b;
    }

    private static byte[] makeRandom(final int n) {
        final byte[] b = new byte[n];
        ThreadLocalRandom.current().nextBytes(b);
        return b;
    }

    private static double nsToMs(final long ns) {
        return ns / 1_000_000.0;
    }

    private static long p(final long[] arr, final double q) {
        final int i = (int) Math.min(arr.length - 1, Math.max(0, Math.round((arr.length - 1) * q)));
        return arr[i];
    }
}
