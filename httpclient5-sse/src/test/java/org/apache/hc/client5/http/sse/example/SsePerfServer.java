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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal SSE server for benchmarking.
 * <p>
 * Uses the JDK built-in {@code com.sun.net.httpserver.HttpServer} (HTTP/1.1) and emits SSE events at a fixed rate.
 * Each connection gets its own scheduled emitter task.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>
 *   SsePerfServer [port] [eventsPerSecond] [payloadBytes] [path]
 * </pre>
 *
 * <p>
 * Defaults: {@code port=8080}, {@code eventsPerSecond=50}, {@code payloadBytes=32}, {@code path=/events}.
 * </p>
 *
 * <p>
 * Note: This is intentionally tiny and not tuned for huge fanout. For large-scale fanout, use a shared
 * publisher loop and write to multiple connections (or use HttpCore5 server bootstrap).
 * </p>
 */
public final class SsePerfServer {

    private static int intArg(final String[] args, final int idx, final int def) {
        return args.length > idx ? Integer.parseInt(args[idx]) : def;
    }

    private static String payload(final int bytes) {
        if (bytes <= 0) {
            return "ok";
        }
        final StringBuilder sb = new StringBuilder(bytes);
        while (sb.length() < bytes) {
            sb.append('x');
        }
        return sb.toString();
    }

    public static void main(final String[] args) throws Exception {
        final int port = intArg(args, 0, 8080);
        final int eventsPerSecond = intArg(args, 1, 50);
        final int payloadBytes = intArg(args, 2, 32);
        final String path = args.length > 3 ? args[3] : "/events";

        final HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()));

        final AtomicLong idSeq = new AtomicLong(0);
        final String dataPayload = payload(payloadBytes);

        final long periodNanos;
        if (eventsPerSecond > 0) {
            final long p = 1_000_000_000L / (long) eventsPerSecond;
            periodNanos = Math.max(1L, p);
        } else {
            periodNanos = 1_000_000_000L;
        }

        server.createContext(path, exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            final Headers h = exchange.getResponseHeaders();
            h.add("Content-Type", "text/event-stream; charset=utf-8");
            h.add("Cache-Control", "no-cache");
            h.add("Connection", "keep-alive");
            h.add("Access-Control-Allow-Origin", "*");

            exchange.sendResponseHeaders(200, 0);

            final OutputStream out = exchange.getResponseBody();
            try {
                out.write((": connected " + Instant.now().toString() + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (final IOException ex) {
                try {
                    out.close();
                } catch (final IOException ignore) {
                }
                exchange.close();
                return;
            }

            final Runnable emitter = () -> {
                final long id = idSeq.incrementAndGet();
                final String msg =
                        "id: " + id + "\n" +
                                "event: message\n" +
                                "data: " + dataPayload + "\n\n";
                try {
                    out.write(msg.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (final IOException ex) {
                    throw new RuntimeException(ex);
                }
            };

            final ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                    emitter,
                    0L,
                    periodNanos,
                    TimeUnit.NANOSECONDS);

            try {
                // Blocks until cancelled or the task terminates (e.g. client disconnect -> IOException).
                future.get();
            } catch (final CancellationException ignore) {
                // expected on shutdown / cancel
            } catch (final ExecutionException ignore) {
                // expected on client disconnect (write fails)
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                future.cancel(true);
                try {
                    out.close();
                } catch (final IOException ignore) {
                }
                exchange.close();
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.printf(Locale.ROOT,
                "SsePerfServer listening on http://localhost:%d%s (rate=%d/s payload=%d)%n",
                port, path, eventsPerSecond, payloadBytes);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            scheduler.shutdownNow();
        }, "sse-perfserver-shutdown"));
    }

}
