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

import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.sse.EventSource;
import org.apache.hc.client5.http.sse.EventSourceConfig;
import org.apache.hc.client5.http.sse.EventSourceListener;
import org.apache.hc.client5.http.sse.impl.ExponentialJitterBackoff;
import org.apache.hc.client5.http.sse.SseExecutor;
import org.apache.hc.client5.http.sse.impl.SseParser;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;

public final class ClientSseExample {

    public static void main(final String[] args) throws Exception {
        final URI uri = URI.create(args.length > 0
                ? args[0]
                : "https://stream.wikimedia.org/v2/stream/recentchange");

        // 1) IO & pool tuned for low latency + H2 multiplexing
        final IOReactorConfig ioCfg = IOReactorConfig.custom()
                .setIoThreadCount(Math.max(2, Runtime.getRuntime().availableProcessors()))
                .setSoKeepAlive(true)
                .setTcpNoDelay(true)
                .build();

        final PoolingAsyncClientConnectionManager connMgr =
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .useSystemProperties()
                        .setMessageMultiplexing(true)      // HTTP/2 stream multiplexing
                        .setMaxConnPerRoute(32)
                        .setMaxConnTotal(256)
                        .setDefaultTlsConfig(
                                TlsConfig.custom()
                                        .setVersionPolicy(HttpVersionPolicy.NEGOTIATE) // or FORCE_HTTP_2 / FORCE_HTTP_1
                                        .build())
                        .build();

        final CloseableHttpAsyncClient httpClient = HttpAsyncClientBuilder.create()
                .setIOReactorConfig(ioCfg)
                .setConnectionManager(connMgr)
                .setH2Config(H2Config.custom()
                        .setPushEnabled(false)
                        .setMaxConcurrentStreams(256)
                        .build())
                .useSystemProperties()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(1))
                .build();

        // 2) Scheduler for reconnects (multithreaded; cancels are purged)
        final ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(4, new DefaultThreadFactory("sse-backoff", true));
        scheduler.setRemoveOnCancelPolicy(true);

        // 3) Callback executor (direct = lowest latency; swap for a small pool if your handler is heavy)
        final Executor callbacks = Runnable::run;

        // 4) Default EventSource policy (backoff + unlimited retries)
        final EventSourceConfig defaultCfg = EventSourceConfig.builder()
                .backoff(new ExponentialJitterBackoff(500L, 30_000L, 2.0, 250L))
                .maxReconnects(-1)
                .build();

        // 5) Default headers for all streams
        final Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("User-Agent", "Apache-HttpClient-SSE/5.x");
        defaultHeaders.put("Accept-Language", "en");

        // 6) Build SSE executor with BYTE parser (minimal allocations)
        final SseExecutor exec = SseExecutor.custom()
                .setHttpClient(httpClient)
                .setScheduler(scheduler)
                .setCallbackExecutor(callbacks)
                .setEventSourceConfig(defaultCfg)
                .setDefaultHeaders(defaultHeaders)
                .setParserStrategy(SseParser.BYTE)
                .build();

        // 7) Listener
        final CountDownLatch done = new CountDownLatch(1);
        final EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onOpen() {
                System.out.println("[SSE] open: " + uri);
            }

            @Override
            public void onEvent(final String id, final String type, final String data) {
                final String shortData = data.length() > 120 ? data.substring(0, 120) + "…" : data;
                System.out.printf(Locale.ROOT, "[SSE] %s id=%s %s%n",
                        type != null ? type : "message", id, shortData);
            }

            @Override
            public void onClosed() {
                System.out.println("[SSE] closed");
                done.countDown();
            }

            @Override
            public void onFailure(final Throwable t, final boolean willReconnect) {
                System.err.println("[SSE] failure: " + t + " willReconnect=" + willReconnect);
                if (!willReconnect) {
                    done.countDown();
                }
            }
        };

        // 8) Per-stream overrides (optional)
        final Map<String, String> perStreamHeaders = new HashMap<>();
        final EventSourceConfig perStreamCfg = EventSourceConfig.builder()
                .backoff(new ExponentialJitterBackoff(750L, 20_000L, 2.0, 250L))
                .maxReconnects(-1)
                .build();

        final EventSource es = exec.open(
                uri,
                perStreamHeaders,
                listener,
                perStreamCfg,
                SseParser.BYTE,
                scheduler,
                callbacks
        );

        // Clean shutdown on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                es.cancel();
            } catch (final Exception ignore) {
            }
            try {
                exec.close();
            } catch (final Exception ignore) {
            }
            try {
                scheduler.shutdownNow();
            } catch (final Exception ignore) {
            }
        }, "sse-shutdown"));

        es.start();
        done.await();

        es.cancel();
        exec.close();
        scheduler.shutdownNow();
    }
}
