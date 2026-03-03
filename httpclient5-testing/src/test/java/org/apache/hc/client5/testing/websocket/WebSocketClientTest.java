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

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
import org.apache.hc.client5.http.websocket.client.WebSocketClientBuilder;
import org.apache.hc.client5.http.websocket.client.WebSocketClients;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.server.WebSocketServer;
import org.apache.hc.core5.websocket.server.WebSocketServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebSocketClientTest {

    private WebSocketServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = WebSocketServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setCanonicalHostName("localhost")
                .register("/echo", EchoHandler::new)
                .register("/pmce", PmceHandler::new)
                .register("/interleave", InterleaveHandler::new)
                .register("/abrupt", AbruptHandler::new)
                .register("/too-big", TooBigHandler::new)
                .create();
        server.start();
        port = server.getLocalPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    private static URI uri(final int port, final String path) {
        return URI.create("ws://localhost:" + port + path);
    }

    private static CloseableWebSocketClient newClient() {
        final CloseableWebSocketClient client = WebSocketClientBuilder.create().build();
        client.start(); // start reactor threads
        return client;
    }

    @Test
    void echo_uncompressed() throws Exception {
        final URI uri = uri(port, "/echo");

        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .enablePerMessageDeflate(false)
                .build();

        try (final CloseableWebSocketClient client = WebSocketClients.createDefault()) {
            client.start();

            final CountDownLatch done = new CountDownLatch(1);
            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
            final StringBuilder echoed = new StringBuilder();
            final AtomicReference<WebSocket> wsRef = new AtomicReference<>();

            System.out.println("[TEST] connecting: " + uri);

            final WebSocketListener listener = new WebSocketListener() {

                @Override
                public void onOpen(final WebSocket ws) {
                    wsRef.set(ws);
                    final String payload = buildPayload();
                    System.out.println("[TEST] open: " + uri);
                    final boolean sent = ws.sendText(payload, true);
                    System.out.println("[TEST] sent (chars=" + payload.length() + ") sent=" + sent);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    echoed.append(text);
                    if (last) {
                        System.out.println("[TEST] text (chars=" + text.length() + "): " +
                                (text.length() > 80 ? text.subSequence(0, 80) + "…" : text));
                        final WebSocket ws = wsRef.get();
                        if (ws != null) {
                            ws.close(1000, "done");
                        }
                    }
                }

                @Override
                public void onClose(final int code, final String reason) {
                    try {
                        System.out.println("[TEST] close: " + code + " " + reason);
                        assertEquals(1000, code);
                        assertTrue(echoed.length() > 0, "No text echoed back");
                    } finally {
                        done.countDown();
                    }
                }

                @Override
                public void onError(final Throwable ex) {
                    ex.printStackTrace(System.out);
                    errorRef.set(ex);
                    done.countDown();
                }

                private String buildPayload() {
                    final String base = "hello from hc5 WS @ " + Instant.now() + " — ";
                    final StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < 256; i++) {
                        buf.append(base);
                    }
                    return buf.toString();
                }

            };

            final CompletableFuture<WebSocket> future = client.connect(uri, listener, cfg, null);
            future.whenComplete((ws, ex) -> {
                if (ex != null) {
                    errorRef.set(ex);
                    done.countDown();
                }
            });

            assertTrue(done.await(10, TimeUnit.SECONDS), "WebSocket did not close in time");

            final Throwable error = errorRef.get();
            if (error != null) {
                Assertions.fail("WebSocket error: " + error.getMessage(), error);
            }
        }
    }

    @Test
    void echo_compressed_pmce() throws Exception {
        final URI uri = uri(port, "/pmce");

        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .enablePerMessageDeflate(true)
                .offerServerNoContextTakeover(true)
                .offerClientNoContextTakeover(true)
                .offerClientMaxWindowBits(15)
                .build();

        try (final CloseableWebSocketClient client = WebSocketClients.createDefault()) {
            client.start();

            final CountDownLatch done = new CountDownLatch(1);
            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
            final StringBuilder echoed = new StringBuilder();
            final AtomicReference<WebSocket> wsRef = new AtomicReference<>();

            final WebSocketListener listener = new WebSocketListener() {

                @Override
                public void onOpen(final WebSocket ws) {
                    wsRef.set(ws);
                    final String payload = "pmce test " + Instant.now();
                    ws.sendText(payload, true);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    echoed.append(text);
                    if (last) {
                        final WebSocket ws = wsRef.get();
                        if (ws != null) {
                            ws.close(1000, "done");
                        }
                    }
                }

                @Override
                public void onClose(final int code, final String reason) {
                    try {
                        assertEquals(1000, code);
                        assertTrue(echoed.length() > 0, "No text echoed back");
                    } finally {
                        done.countDown();
                    }
                }

                @Override
                public void onError(final Throwable ex) {
                    errorRef.set(ex);
                    done.countDown();
                }
            };

            client.connect(uri, listener, cfg, null);
            assertTrue(done.await(10, TimeUnit.SECONDS), "WebSocket did not close in time");

            final Throwable error = errorRef.get();
            if (error != null) {
                Assertions.fail("WebSocket error: " + error.getMessage(), error);
            }
        }
    }

    @Test
    void ping_interleaved_fragmentation() throws Exception {
        final CountDownLatch gotText = new CountDownLatch(1);
        final CountDownLatch gotPong = new CountDownLatch(1);

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .enablePerMessageDeflate(false)
                    .build();

            final URI u = uri(port, "/interleave");
            client.connect(u, new WebSocketListener() {
                @Override
                public void onOpen(final WebSocket ws) {
                    ws.ping(null);
                    final String prefix = "hello from hc5 WS @ " + Instant.now() + " — ";
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 256; i++) {
                        sb.append(prefix);
                    }
                    ws.sendText(sb.toString(), true);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    gotText.countDown();
                }

                @Override
                public void onPong(final ByteBuffer payload) {
                    gotPong.countDown();
                }

                @Override
                public void onClose(final int code, final String reason) {
                    // the servlet closes after echo
                }

                @Override
                public void onError(final Throwable ex) {
                    gotText.countDown();
                    gotPong.countDown();
                }
            }, cfg, null);

            assertTrue(gotPong.await(10, TimeUnit.SECONDS), "did not receive PONG");
            assertTrue(gotText.await(10, TimeUnit.SECONDS), "did not receive TEXT");
        }
    }

    @Test
    void max_message_1009() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Integer> codeRef = new AtomicReference<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final int maxMessage = 2048; // 2 KiB

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .setMaxMessageSize(maxMessage)
                    .enablePerMessageDeflate(false)
                    .build();

            final URI u = uri(port, "/too-big");
            client.connect(u, new WebSocketListener() {
                @Override
                public void onOpen(final WebSocket ws) {
                    // Trigger the server to send an oversized text message.
                    ws.sendText("trigger-too-big", true);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    // We may or may not see some text before the 1009 close.
                }

                @Override
                public void onClose(final int code, final String reason) {
                    codeRef.set(code);
                    done.countDown();
                }

                @Override
                public void onError(final Throwable ex) {
                    errorRef.set(ex);
                    done.countDown();
                }
            }, cfg, null);

            assertTrue(done.await(10, TimeUnit.SECONDS), "timeout waiting for 1009 close");

            final Throwable error = errorRef.get();
            if (error != null) {
                Assertions.fail("WebSocket error: " + error.getMessage(), error);
            }

            assertEquals(Integer.valueOf(1009), codeRef.get(), "expected 1009 close code");
        }
    }

    private static final class EchoHandler implements WebSocketHandler {
        @Override
        public void onText(final WebSocketSession session, final String text) {
            try {
                session.sendText(text);
                session.close(1000, "done");
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static final class PmceHandler implements WebSocketHandler {
        @Override
        public void onText(final WebSocketSession session, final String text) {
            try {
                session.sendText(text);
                session.close(1000, "done");
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static final class InterleaveHandler implements WebSocketHandler {
        @Override
        public void onText(final WebSocketSession session, final String text) {
            try {
                session.sendPing(ByteBuffer.wrap(new byte[]{'p', 'i', 'n', 'g'}));
                session.sendText(text);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static final class AbruptHandler implements WebSocketHandler {
        @Override
        public void onOpen(final WebSocketSession session) {
            // no-op
        }
    }

    private static final class TooBigHandler implements WebSocketHandler {
        @Override
        public void onText(final WebSocketSession session, final String text) {
            final StringBuilder sb = new StringBuilder();
            final String chunk = "1234567890abcdef-";
            while (sb.length() <= 8192) {
                sb.append(chunk);
            }
            final String big = sb.toString();
            try {
                session.sendText(big);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
