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
package org.apache.hc.client5.testing.websocket.performance;

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
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.server.WebSocketH2Server;
import org.apache.hc.core5.websocket.server.WebSocketH2ServerBootstrap;
import org.apache.hc.core5.websocket.server.WebSocketServer;
import org.apache.hc.core5.websocket.server.WebSocketServerBootstrap;

/**
 * Simple H1/H2 WebSocket performance harness that starts a local echo server
 * and drives multiple clients against it.
 * <p>
 * Example:
 * protocol=h1 mode=THROUGHPUT clients=8 durationSec=10 bytes=512 inflight=32 pmce=false compressible=true
 * protocol=h2 mode=LATENCY clients=4 durationSec=10 bytes=64 inflight=4 pmce=false compressible=false
 */
public final class WsPerfHarness {

    private WsPerfHarness() {
    }

    public static void main(final String[] args) throws Exception {
        final Args a = Args.parse(args);
        final HarnessServer server = a.uri != null ? null : startServer(a);
        final String uri = a.uri != null ? a.uri : server.uri();

        System.out.printf(Locale.ROOT,
                "protocol=%s mode=%s uri=%s clients=%d durationSec=%d bytes=%d inflight=%d pmce=%s compressible=%s%n",
                a.protocol, a.mode, uri, a.clients, a.durationSec, a.bytes, a.inflight, a.pmce, a.compressible);

        final ExecutorService pool = Executors.newFixedThreadPool(Math.min(a.clients, 64));
        final AtomicLong sends = new AtomicLong();
        final AtomicLong recvs = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final ConcurrentLinkedQueue<Long> lats = new ConcurrentLinkedQueue<>();
        final CountDownLatch ready = new CountDownLatch(a.clients);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(a.clients);

        final byte[] payload = a.compressible ? makeCompressible(a.bytes) : makeRandom(a.bytes);
        final AtomicLong deadlineRef = new AtomicLong();

        for (int i = 0; i < a.clients; i++) {
            final int id = i;
            pool.submit(() -> runClient(id, a, uri, payload, sends, recvs, errors, lats, ready, go, done, deadlineRef));
        }

        final long awaitMs = TimeUnit.SECONDS.toMillis(a.durationSec + 15L);
        if (!ready.await(awaitMs, TimeUnit.MILLISECONDS)) {
            System.out.println("[PERF] timeout waiting for clients to connect");
        }
        deadlineRef.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(a.durationSec));
        go.countDown();
        if (!done.await(awaitMs, TimeUnit.MILLISECONDS)) {
            System.out.println("[PERF] timeout waiting for clients to finish");
        }
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

        if (server != null) {
            server.stop();
        }
    }

    private static HarnessServer startServer(final Args a) throws Exception {
        if (a.protocol == Protocol.H2) {
            final WebSocketH2Server server = WebSocketH2ServerBootstrap.bootstrap()
                    .setListenerPort(a.port)
                    .setCanonicalHostName(a.host)
                    .register("/echo", EchoHandler::new)
                    .create();
            server.start();
            return new HarnessServer(server.getLocalPort(), server);
        }
        final WebSocketServer server = WebSocketServerBootstrap.bootstrap()
                .setListenerPort(a.port)
                .setCanonicalHostName(a.host)
                .register("/echo", EchoHandler::new)
                .create();
        server.start();
        return new HarnessServer(server.getLocalPort(), server);
    }

    private static void runClient(
            final int id, final Args a, final String uri, final byte[] payload,
            final AtomicLong sends, final AtomicLong recvs, final AtomicLong errors,
            final ConcurrentLinkedQueue<Long> lats,
            final CountDownLatch ready, final CountDownLatch go,
            final CountDownLatch done, final AtomicLong deadlineRef) {

        final WebSocketClientConfig.Builder b = WebSocketClientConfig.custom()
                .setConnectTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(5))
                .setCloseWaitTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(3))
                .setOutgoingChunkSize(4096)
                .setAutoPong(true)
                .enableHttp2(a.protocol == Protocol.H2);

        if (a.pmce) {
            b.enablePerMessageDeflate(true)
                    .offerClientNoContextTakeover(false)
                    .offerServerNoContextTakeover(false)
                    .offerClientMaxWindowBits(null)
                    .offerServerMaxWindowBits(null);
        }
        final WebSocketClientConfig cfg = b.build();

        try (final CloseableWebSocketClient client =
                     WebSocketClientBuilder.create()
                             .defaultConfig(cfg)
                             .build()) {
            client.start();
            waitForStart(client, id);

            final AtomicInteger inflight = new AtomicInteger();
            final AtomicBoolean open = new AtomicBoolean(false);
            final AtomicBoolean readyCounted = new AtomicBoolean(false);

            System.out.printf(Locale.ROOT, "[PERF] client-%d connecting to %s%n", id, uri);
            final CompletableFuture<WebSocket> cf = client.connect(
                    URI.create(uri),
                    new WebSocketListener() {
                        @Override
                        public void onOpen(final WebSocket ws) {
                            open.set(true);
                            if (readyCounted.compareAndSet(false, true)) {
                                ready.countDown();
                            }
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
                            open.set(false);
                            if (!(ex instanceof ConnectionClosedException)) {
                                System.out.printf(Locale.ROOT, "[PERF] client-%d error: %s%n", id, ex.toString());
                            }
                        }

                        @Override
                        public void onClose(final int code, final String reason) {
                            open.set(false);
                        }
                    }, cfg, HttpCoreContext.create());
            cf.whenComplete((ws, ex) -> {
                if (ex != null) {
                    errors.incrementAndGet();
                    if (!(ex instanceof ConnectionClosedException)) {
                        System.out.printf(Locale.ROOT, "[PERF] client-%d connect failed: %s%n", id, ex.toString());
                    }
                }
            });

            try {
                final WebSocket ws = cf.get(15, TimeUnit.SECONDS);
                if (!go.await(10, TimeUnit.SECONDS)) {
                    System.out.printf(Locale.ROOT, "[PERF] client-%d start timeout%n", id);
                }
                final long deadlineNanos = deadlineRef.get();

                while (System.nanoTime() < deadlineNanos) {
                    while (open.get() && inflight.get() < a.inflight) {
                        sendOne(ws, a, payload, sends, inflight);
                    }
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                }

                Thread.sleep(200);
                ws.close(1000, "bye");
            } catch (final Exception e) {
                errors.incrementAndGet();
                System.out.printf(Locale.ROOT, "[PERF] client-%d connect timeout/failure: %s%n", id, e);
                if (readyCounted.compareAndSet(false, true)) {
                    ready.countDown();
                }
            } finally {
                if (readyCounted.compareAndSet(false, true)) {
                    ready.countDown();
                }
                done.countDown();
            }
        } catch (final Exception ignore) {
        }
    }

    private static void waitForStart(final CloseableWebSocketClient client, final int id) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (client.getStatus() != null && client.getStatus() == IOReactorStatus.ACTIVE) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        System.out.printf(Locale.ROOT, "[PERF] client-%d start timeout (status=%s)%n", id, client.getStatus());
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

    private enum Protocol { H1, H2 }

    private static final class Args {
        Protocol protocol = Protocol.H1;
        String uri;
        String host = "127.0.0.1";
        int port = 0;
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
                    case "protocol":
                        r.protocol = Protocol.valueOf(kv[1].toUpperCase(Locale.ROOT));
                        break;
                    case "uri":
                        r.uri = kv[1];
                        break;
                    case "host":
                        r.host = kv[1];
                        break;
                    case "port":
                        r.port = Integer.parseInt(kv[1]);
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
                        r.mode = Mode.valueOf(kv[1].toUpperCase(Locale.ROOT));
                        break;
                }
            }
            return r;
        }
    }

    private static final class HarnessServer {
        private final int port;
        private final WebSocketServer h1;
        private final WebSocketH2Server h2;

        HarnessServer(final int port, final WebSocketServer h1) {
            this.port = port;
            this.h1 = h1;
            this.h2 = null;
        }

        HarnessServer(final int port, final WebSocketH2Server h2) {
            this.port = port;
            this.h1 = null;
            this.h2 = h2;
        }

        String uri() {
            return "ws://127.0.0.1:" + port + "/echo";
        }

        void stop() throws Exception {
            if (h2 != null) {
                h2.stop();
            } else if (h1 != null) {
                h1.stop();
            }
        }
    }

    private static final class EchoHandler implements WebSocketHandler {
        @Override
        public void onBinary(final WebSocketSession session, final ByteBuffer data) {
            try {
                session.sendBinary(data != null ? data.asReadOnlyBuffer() : ByteBuffer.allocate(0));
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void onText(final WebSocketSession session, final String text) {
            try {
                session.sendText(text);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
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
