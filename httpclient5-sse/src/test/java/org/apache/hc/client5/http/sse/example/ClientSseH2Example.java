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
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sse.EventSource;
import org.apache.hc.client5.http.sse.EventSourceConfig;
import org.apache.hc.client5.http.sse.EventSourceListener;
import org.apache.hc.client5.http.sse.SseExecutor;
import org.apache.hc.client5.http.sse.impl.ExponentialJitterBackoff;
import org.apache.hc.client5.http.sse.impl.SseParser;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;

/**
 * HTTP/2 SSE demo.
 * <p>
 * This example connects to an SSE endpoint using the async transport with HTTP/2 forced via TLS + ALPN,
 * probes the negotiated protocol, and then opens multiple SSE subscriptions concurrently to demonstrate
 * HTTP/2 stream multiplexing.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>
 *   ClientSseH2Example [uri] [streamCount] [maxEventsPerStream]
 * </pre>
 *
 * <p>
 * Defaults:
 * </p>
 * <ul>
 *   <li>{@code uri=https://stream.wikimedia.org/v2/stream/recentchange}</li>
 *   <li>{@code streamCount=4}</li>
 *   <li>{@code maxEventsPerStream=25}</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>HTTP/2 is enforced with {@link org.apache.hc.core5.http2.HttpVersionPolicy#FORCE_HTTP_2}. If the origin
 *       cannot negotiate H2, the connection attempt fails (no silent downgrade).</li>
 *   <li>The probe request prints {@code HTTP/2.0} when ALPN negotiation succeeds.</li>
 *   <li>With {@code maxConnPerRoute=1}, multiple active SSE subscriptions are only possible with HTTP/2
 *       multiplexing (each subscription is a separate H2 stream).</li>
 * </ul>
 */
public final class ClientSseH2Example {

    private static void probeProtocol(final CloseableHttpAsyncClient httpClient, final URI sseUri) throws Exception {
        final HttpHost target = new HttpHost(sseUri.getScheme(), sseUri.getHost(), sseUri.getPort());
        final HttpClientContext ctx = HttpClientContext.create();

        final SimpleHttpRequest req = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .build();

        final Future<SimpleHttpResponse> f = httpClient.execute(
                SimpleRequestProducer.create(req),
                SimpleResponseConsumer.create(),
                ctx,
                null);

        final SimpleHttpResponse resp = f.get(10, TimeUnit.SECONDS);

        System.out.println("[probe] " + req + " -> " + new StatusLine(resp));
        System.out.println("[probe] negotiated protocol: " + ctx.getProtocolVersion());

        final SSLSession sslSession = ctx.getSSLSession();
        if (sslSession != null) {
            System.out.println("[probe] TLS protocol: " + sslSession.getProtocol());
            System.out.println("[probe] TLS cipher: " + sslSession.getCipherSuite());
        }

        if (!HttpVersion.HTTP_2.equals(ctx.getProtocolVersion())) {
            System.out.println("[probe] WARNING: not HTTP/2 (server / proxy downgraded?)");
        }
    }

    public static void main(final String[] args) throws Exception {
        final URI uri = URI.create(args.length > 0
                ? args[0]
                : "https://stream.wikimedia.org/v2/stream/recentchange");

        final int streamCount = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        final int maxEventsPerStream = args.length > 2 ? Integer.parseInt(args[2]) : 25;

        final IOReactorConfig ioCfg = IOReactorConfig.custom()
                .setIoThreadCount(Math.max(2, Runtime.getRuntime().availableProcessors()))
                .setSoKeepAlive(true)
                .setTcpNoDelay(true)
                .build();

        final PoolingAsyncClientConnectionManager connMgr =
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .setMessageMultiplexing(true)
                        .setMaxConnPerRoute(1)
                        .setMaxConnTotal(4)
                        .setDefaultTlsConfig(TlsConfig.custom()
                                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                                .build())
                        .build();

        final CloseableHttpAsyncClient httpClient = HttpAsyncClientBuilder.create()
                .setIOReactorConfig(ioCfg)
                .setConnectionManager(connMgr)
                .setH2Config(H2Config.custom()
                        .setPushEnabled(false)
                        .setMaxConcurrentStreams(Math.max(64, streamCount * 8))
                        .build())
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(1))
                .build();

        httpClient.start();
        probeProtocol(httpClient, uri);

        final ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(2, new DefaultThreadFactory("sse-backoff", true));
        scheduler.setRemoveOnCancelPolicy(true);

        final Executor callbacks = Runnable::run;

        final EventSourceConfig cfg = EventSourceConfig.builder()
                .backoff(new ExponentialJitterBackoff(500L, 30_000L, 2.0, 250L))
                .maxReconnects(-1)
                .build();

        final Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("User-Agent", "Apache-HttpClient-SSE/5.x");
        defaultHeaders.put("Accept-Language", "en");

        final SseExecutor exec = SseExecutor.custom()
                .setHttpClient(httpClient)
                .setScheduler(scheduler)
                .setCallbackExecutor(callbacks)
                .setEventSourceConfig(cfg)
                .setDefaultHeaders(defaultHeaders)
                .setParserStrategy(SseParser.BYTE)
                .build();

        final CountDownLatch done = new CountDownLatch(streamCount);
        final EventSource[] sources = new EventSource[streamCount];

        for (int i = 0; i < streamCount; i++) {
            final int idx = i;
            final AtomicInteger count = new AtomicInteger(0);

            final Map<String, String> headers = new HashMap<>();
            headers.put("X-Client-Stream", Integer.toString(idx));

            final EventSourceListener listener = new EventSourceListener() {

                @Override
                public void onOpen() {
                    System.out.printf(Locale.ROOT, "[SSE/%d] open: %s%n", idx, uri);
                }

                @Override
                public void onEvent(final String id, final String type, final String data) {
                    final int n = count.incrementAndGet();
                    final String shortData = data.length() > 120 ? data.substring(0, 120) + "…" : data;
                    System.out.printf(Locale.ROOT, "[SSE/%d] #%d %s id=%s %s%n",
                            idx, n, type != null ? type : "message", id, shortData);

                    if (n >= maxEventsPerStream) {
                        sources[idx].cancel();
                    }
                }

                @Override
                public void onClosed() {
                    System.out.printf(Locale.ROOT, "[SSE/%d] closed%n", idx);
                    done.countDown();
                }

                @Override
                public void onFailure(final Throwable t, final boolean willReconnect) {
                    System.err.printf(Locale.ROOT, "[SSE/%d] failure: %s willReconnect=%s%n",
                            idx, t, willReconnect);
                    if (!willReconnect) {
                        done.countDown();
                    }
                }
            };

            sources[i] = exec.open(uri, headers, listener, cfg, SseParser.BYTE, scheduler, callbacks);
        }

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
        }, "sse-shutdown"));

        for (final EventSource es : sources) {
            es.start();
        }

        done.await();

        for (final EventSource es : sources) {
            es.cancel();
        }
        exec.close();
        scheduler.shutdownNow();
    }
}
