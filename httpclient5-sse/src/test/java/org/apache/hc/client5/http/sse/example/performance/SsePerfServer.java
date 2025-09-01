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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;

/**
 * Scaled local SSE server (HTTP/1.1) implemented with Apache HttpComponents Core 5 classic server.
 * <p>
 * Endpoint: {@code /sse}
 * <br>Query params:
 * <ul>
 *   <li>{@code rate} – events/sec per connection (default: 20)</li>
 *   <li>{@code size} – payload size (bytes) inside {@code data:} (default: 64)</li>
 *   <li>{@code sync} – send an {@code event: sync} with server nano time every N seconds (default: 10; 0 disables)</li>
 * </ul>
 *
 * <p><b>Run (IntelliJ):</b>
 * <ul>
 *   <li><b>Program arguments:</b> {@code 8089}</li>
 *   <li><b>VM options (optional, GC/tuning):</b>
 *   {@code -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -Xms1g -Xmx1g}</li>
 * </ul>
 *
 * <p>Example client test:
 * <pre>
 *   curl -N "<a href="http://localhost:8089/sse?rate=50&size=64">...</a>"
 * </pre>
 */
public final class SsePerfServer {

    private SsePerfServer() {
    }

    public static void main(final String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : 8089;

        final HttpRequestHandler sseHandler = (request, response, context) -> {
            final URI uri = URI.create(request.getRequestUri());
            final Map<String, String> q = parseQuery(uri.getRawQuery());
            final int rate = parseInt(q.get("rate"), 20);
            final int size = Math.max(1, parseInt(q.get("size"), 64));
            final int syncSec = Math.max(0, parseInt(q.get("sync"), 10));

            response.setCode(HttpStatus.SC_OK);
            response.addHeader("Content-Type", "text/event-stream");
            response.addHeader("Cache-Control", "no-cache");
            response.addHeader("Connection", "keep-alive");

            response.setEntity(new SseStreamEntity(rate, size, syncSec));
        };

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .register("/sse", sseHandler)
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "sse-server-stop"));

        server.start();

        System.out.printf(Locale.ROOT, "[SSE-SERVER] listening on %d%n", port);
        System.out.println("[SSE-SERVER] try: curl -N \"http://localhost:" + port + "/sse?rate=50&size=64\"");
    }

    /**
     * Streaming entity that writes an infinite SSE stream with tight nanosecond scheduling.
     */
    private static final class SseStreamEntity extends AbstractHttpEntity {

        private final int rate;
        private final int size;
        private final int syncSec;

        SseStreamEntity(final int rate, final int size, final int syncSec) {
            super(ContentType.TEXT_EVENT_STREAM, null, true); // chunked
            this.rate = rate;
            this.size = size;
            this.syncSec = syncSec;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public void writeTo(final OutputStream outStream) throws IOException {
            // buffered writes; still flush each event to keep latency low
            final BufferedOutputStream os = new BufferedOutputStream(outStream, 8192);

            // one-time random payload (base64) of requested size
            final byte[] pad = new byte[size];
            ThreadLocalRandom.current().nextBytes(pad);
            final String padB64 = Base64.getEncoder().encodeToString(pad);

            // initial sync with server monotonic time in nanoseconds
            long nowNano = System.nanoTime();
            writeAndFlush(os, "event: sync\ndata: tn=" + nowNano + "\n\n");

            // schedule params
            final long intervalNanos = (rate <= 0) ? 0L : (1_000_000_000L / rate);
            long seq = 0L;
            long next = System.nanoTime();
            long nextSync = syncSec > 0 ? System.nanoTime() + TimeUnit.SECONDS.toNanos(syncSec) : Long.MAX_VALUE;

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    nowNano = System.nanoTime();

                    // periodic sync tick
                    if (nowNano >= nextSync) {
                        writeAndFlush(os, "event: sync\ndata: tn=" + nowNano + "\n\n");
                        nextSync = nowNano + TimeUnit.SECONDS.toNanos(syncSec);
                    }

                    if (intervalNanos == 0L || nowNano >= next) {
                        // emit one event
                        final long tMs = System.currentTimeMillis();
                        final long tn = System.nanoTime();
                        final String frame =
                                "id: " + (++seq) + "\n" +
                                        "event: m\n" +
                                        "data: t=" + tMs + ",tn=" + tn + ",p=" + padB64 + "\n\n";
                        writeAndFlush(os, frame);

                        if (intervalNanos > 0L) {
                            // advance by exactly one period to avoid drift
                            next += intervalNanos;
                            // if we've fallen far behind (e.g. GC), realign to avoid bursts
                            if (nowNano - next > intervalNanos * 4L) {
                                next = nowNano + intervalNanos;
                            }
                        }
                    } else {
                        // tight, short sleep with nanosecond resolution
                        final long sleepNs = next - nowNano;
                        if (sleepNs > 0L) {
                            LockSupport.parkNanos(sleepNs);
                        }
                    }
                }
            } catch (final IOException closed) {
                // client disconnected; finish quietly
            }
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public InputStream getContent() throws IOException, UnsupportedOperationException {
            return null;
        }

        @Override
        public boolean isStreaming() {
            return true;
        }

        @Override
        public void close() throws IOException { /* no-op */ }

        private static void writeAndFlush(final BufferedOutputStream os, final String s) throws IOException {
            os.write(s.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    // -------- helpers --------

    private static int parseInt(final String s, final int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s);
        } catch (final Exception ignore) {
            return def;
        }
    }

    private static Map<String, String> parseQuery(final String raw) {
        final Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return m;
        }
        final String[] parts = raw.split("&");
        for (final String part : parts) {
            final int eq = part.indexOf('=');
            if (eq > 0) {
                m.put(urlDecode(part.substring(0, eq)), urlDecode(part.substring(eq + 1)));
            }
        }
        return m;
    }

    private static String urlDecode(final String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (final Exception e) {
            return s;
        }
    }
}
