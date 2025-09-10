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
package org.apache.hc.client5.http.websocket.example;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
import org.apache.hc.client5.http.websocket.client.WebSocketClientBuilder;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Example WebSocket echo client using HTTP/2 Extended CONNECT.
 *
 * <p>This example demonstrates how to establish a WebSocket connection over
 * HTTP/2 using the Extended CONNECT method. Instead of the HTTP/1.1 Upgrade
 * handshake, the client opens a single HTTP/2 stream with a
 * {@code :protocol} pseudo-header set to {@code "websocket"}, which allows
 * WebSocket traffic to be multiplexed alongside regular HTTP/2 requests.</p>
 *
 * <p>The client lifecycle is identical to the HTTP/1.1 variant
 * ({@link WebSocketEchoClient}), but the configuration enables the HTTP/2
 * transport path via {@link WebSocketClientConfig.Builder#enableHttp2(boolean)}.</p>
 *
 * <p>If the server does not support HTTP/2 WebSocket, the client will
 * automatically fall back to the HTTP/1.1 Upgrade handshake when the failure
 * is a transport-level error (connection refused, protocol negotiation failure).
 * Application-level errors are not retried.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 *   # Connect to the default endpoint (ws://localhost:8080/echo)
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketH2EchoClient
 *
 *   # Connect to a custom endpoint
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketH2EchoClient ws://host:port/path
 * </pre>
 *
 * <p>Pair with {@link WebSocketH2EchoServer} for a complete local test.</p>
 *
 * @since 5.7
 */
public final class WebSocketH2EchoClient {

    private WebSocketH2EchoClient() {
    }

    public static void main(final String[] args) throws Exception {
        final URI uri = URI.create(args.length > 0 ? args[0] : "ws://localhost:8080/echo");
        final CountDownLatch done = new CountDownLatch(1);

        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .enableHttp2(true)
                .setCloseWaitTimeout(Timeout.ofSeconds(2))
                .build();

        try (final CloseableWebSocketClient client = WebSocketClientBuilder.create()
                .defaultConfig(cfg)
                .build()) {

            client.start();
            client.connect(uri, new WebSocketListener() {
                private WebSocket ws;

                @Override
                public void onOpen(final WebSocket ws) {
                    this.ws = ws;
                    ws.sendText("hello-h2", true);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    System.out.println("[H2] echo: " + text);
                    ws.close(1000, "done");
                }

                @Override
                public void onBinary(final ByteBuffer payload, final boolean last) {
                    System.out.println("[H2] binary: " + payload.remaining());
                }

                @Override
                public void onClose(final int code, final String reason) {
                    done.countDown();
                }

                @Override
                public void onError(final Throwable ex) {
                    if (!(ex instanceof ConnectionClosedException)) {
                        ex.printStackTrace(System.err);
                    }
                    done.countDown();
                }
            }, cfg).exceptionally(ex -> {
                if (!(ex instanceof ConnectionClosedException)) {
                    ex.printStackTrace(System.err);
                }
                done.countDown();
                return null;
            });

            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for H2 echo");
            }
            client.initiateShutdown();
            client.awaitShutdown(TimeValue.ofSeconds(2));
        }
    }
}
