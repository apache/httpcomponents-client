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
package org.apache.hc.client5.testing.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
import org.apache.hc.client5.http.websocket.client.WebSocketClientBuilder;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.server.WebSocketH2Server;
import org.apache.hc.core5.websocket.server.WebSocketH2ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class H2WebSocketEchoIT {

    private WebSocketH2Server server;

    @BeforeEach
    void setUp() throws Exception {
        server = WebSocketH2ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setCanonicalHostName("localhost")
                .register("/echo", () -> new WebSocketHandler() {
                    @Override
                    public void onText(final WebSocketSession session, final String text) {
                        try {
                            session.sendText(text);
                        } catch (final IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    @Override
                    public void onBinary(final WebSocketSession session, final ByteBuffer data) {
                        try {
                            session.sendBinary(data);
                        } catch (final IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    @Override
                    public void onPing(final WebSocketSession session, final ByteBuffer data) {
                        try {
                            session.sendPong(data);
                        } catch (final IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                })
                .create();
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.initiateShutdown();
            server.stop();
        }
    }

    @Test
    void echoesOverHttp2ExtendedConnect() throws Exception {
        final URI uri = URI.create("ws://localhost:" + server.getLocalPort() + "/echo");
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> echo = new AtomicReference<>();

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
                    echo.set(text.toString());
                    done.countDown();
                    ws.close(1000, "done");
                }

                @Override
                public void onClose(final int code, final String reason) {
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

            assertTrue(done.await(10, TimeUnit.SECONDS), "timed out waiting for echo");
            assertEquals("hello-h2", echo.get());
            client.initiateShutdown();
            client.awaitShutdown(TimeValue.ofSeconds(2));
        }
    }

    @Test
    void echoesOverHttp2ExtendedConnectWithPmce() throws Exception {
        final URI uri = URI.create("ws://localhost:" + server.getLocalPort() + "/echo");
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> echo = new AtomicReference<>();

        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .enableHttp2(true)
                .enablePerMessageDeflate(true)
                .offerClientMaxWindowBits(15)
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
                    ws.sendText("hello-h2-pmce hello-h2-pmce hello-h2-pmce", true);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    echo.set(text.toString());
                    done.countDown();
                    ws.close(1000, "done");
                }

                @Override
                public void onClose(final int code, final String reason) {
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

            assertTrue(done.await(10, TimeUnit.SECONDS), "timed out waiting for echo");
            assertEquals("hello-h2-pmce hello-h2-pmce hello-h2-pmce", echo.get());
            client.initiateShutdown();
            client.awaitShutdown(TimeValue.ofSeconds(2));
        }
    }
}
