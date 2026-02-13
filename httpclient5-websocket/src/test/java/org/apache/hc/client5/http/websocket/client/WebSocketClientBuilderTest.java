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
package org.apache.hc.client5.http.websocket.client;

import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.impl.DefaultClientConnectionReuseStrategy;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolStats;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorMetricsListener;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebSocketClientBuilderTest {

    @Test
    void buildWithCustomSettingsUsesLaxPool() throws Exception {
        final CloseableWebSocketClient client = WebSocketClientBuilder.create()
                .defaultConfig(WebSocketClientConfig.custom().enableHttp2(true).build())
                .setIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build())
                .setHttp1Config(Http1Config.custom().setMaxHeaderCount(64).build())
                .setCharCodingConfig(CharCodingConfig.custom().setCharset(StandardCharsets.UTF_8).build())
                .setHttpProcessor(HttpProcessors.client())
                .setConnectionReuseStrategy(new DefaultClientConnectionReuseStrategy())
                .setDefaultMaxPerRoute(2)
                .setMaxTotal(4)
                .setTimeToLive(Timeout.ofSeconds(2))
                .setPoolReusePolicy(PoolReusePolicy.FIFO)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX)
                .setTlsStrategy(new BasicClientTlsStrategy())
                .setTlsHandshakeTimeout(Timeout.ofSeconds(3))
                .setIOSessionDecorator(ioSession -> ioSession)
                .setExceptionCallback(exception -> {
                })
                .setIOSessionListener(new NoopSessionListener())
                .setStreamListener(new NoopStreamListener())
                .setConnPoolListener(new NoopConnPoolListener())
                .setThreadFactory(r -> new Thread(r, "ws-test"))
                .setReactorMetricsListener(new NoopMetricsListener())
                .setMaxPendingCommandsPerConnection(5)
                .build();

        Assertions.assertNotNull(client);
        client.close(CloseMode.IMMEDIATE);
    }

    @Test
    void buildWithDefaultsUsesStrictPool() throws Exception {
        final CloseableWebSocketClient client = WebSocketClientBuilder.create().build();
        Assertions.assertNotNull(client);
        client.close(CloseMode.IMMEDIATE);
    }

    private static final class NoopSessionListener implements IOSessionListener {
        @Override
        public void connected(final IOSession session) {
        }

        @Override
        public void startTls(final IOSession session) {
        }

        @Override
        public void inputReady(final IOSession session) {
        }

        @Override
        public void outputReady(final IOSession session) {
        }

        @Override
        public void timeout(final IOSession session) {
        }

        @Override
        public void exception(final IOSession session, final Exception ex) {
        }

        @Override
        public void disconnected(final IOSession session) {
        }
    }

    private static final class NoopStreamListener implements Http1StreamListener {
        @Override
        public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
        }

        @Override
        public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
        }

        @Override
        public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
        }
    }

    private static final class NoopConnPoolListener implements ConnPoolListener<HttpHost> {
        @Override
        public void onLease(final HttpHost route,
                            final ConnPoolStats<HttpHost> connPoolStats) {
        }

        @Override
        public void onRelease(final HttpHost route,
                              final ConnPoolStats<HttpHost> connPoolStats) {
        }
    }

    private static final class NoopMetricsListener implements IOReactorMetricsListener {
        @Override
        public void onThreadPoolStatus(final int activeThreads, final int pendingConnections) {
        }

        @Override
        public void onThreadPoolSaturation(final double saturationPercentage) {
        }

        @Override
        public void onResourceStarvationDetected() {
        }

        @Override
        public void onQueueWaitTime(final long averageWaitTimeMillis) {
        }
    }
}
