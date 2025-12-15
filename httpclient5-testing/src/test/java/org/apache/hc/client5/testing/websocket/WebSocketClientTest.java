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
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebSocketClientTest {

    private Server server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(0); // auto-bind free port
        server.addConnector(connector);

        final ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/");
        ctx.addServlet(new ServletHolder(new EchoServlet()), "/echo");
        ctx.addServlet(new ServletHolder(new InterleaveServlet()), "/interleave");
        ctx.addServlet(new ServletHolder(new AbruptServlet()), "/abrupt");
        ctx.addServlet(new ServletHolder(new TooBigServlet()), "/too-big");
        server.setHandler(ctx);

        server.start();
        port = connector.getLocalPort();
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

    /**
     * Build a client and ensure it is started before use.
     */
    private static CloseableWebSocketClient newClient() {
        final CloseableWebSocketClient client = WebSocketClientBuilder.create().build();
        client.start(); // start reactor threads
        return client;
    }

    /**
     * Standard echo (uncompressed); ensures basic end-to-end is healthy.
     */
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
    void ping_interleaved_fragmentation() throws Exception {
        final CountDownLatch gotText = new CountDownLatch(1);
        final CountDownLatch gotPong = new CountDownLatch(1);

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .enablePerMessageDeflate(false)
                    .build();

            final URI u = uri(port, "/interleave");
            client.connect(u, new WebSocketListener() {
                private WebSocket ws;

                @Override
                public void onOpen(final WebSocket ws) {
                    ws.ping(null);
                    this.ws = ws;
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

    /**
     * Client enforces maxMessageSize: server sends a too-large message → client closes with 1009.
     * <p>
     * We use a dedicated /too-big endpoint that proactively sends a large text frame so the
     * client-side size check is the only reason for closure – no races with server-initiated CLOSE.
     */
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
                    // Client sends nothing; server pushes an oversized message.
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    // Depending on timing, we may or may not see text before the 1009 close.
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

    /**
     * Server drops TCP without sending CLOSE → client reports abnormal closure 1006.
     */
    @Test
    void abnormal_close_1006() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();

            final URI u = uri(port, "/abrupt");
            client.connect(u, new WebSocketListener() {
                @Override
                public void onOpen(final WebSocket ws) {
                    // do nothing; server will disconnect abruptly
                }

                @Override
                public void onClose(final int code, final String reason) {
                    assertEquals(1006, code);
                    done.countDown();
                }

                @Override
                public void onError(final Throwable ex) {
                    // acceptable; still expect onClose(1006)
                }
            }, cfg, null);

            assertTrue(done.await(10, TimeUnit.SECONDS), "did not see 1006 abnormal closure");
        }
    }

    // -------------------- Embedded servlets/sockets ---------------------------

    public static final class EchoServlet extends WebSocketServlet {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30000);
            factory.setCreator((req, resp) -> new EchoSocket());
        }
    }

    public static final class EchoSocket extends WebSocketAdapter {
        @Override
        public void onWebSocketText(final String msg) {
            final Session s = getSession();
            if (s != null && s.isOpen()) {
                s.getRemote().sendString(msg, null);
                s.close(1000, "done");
            }
        }
    }

    public static final class InterleaveServlet extends WebSocketServlet {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30000);
            factory.setCreator((req, resp) -> new InterleaveSocket());
        }
    }

    public static final class InterleaveSocket extends WebSocketAdapter {
        @Override
        public void onWebSocketText(final String msg) {
            final Session s = getSession();
            if (s == null) {
                return;
            }
            try {
                s.getRemote().sendPing(ByteBuffer.wrap(new byte[]{'p', 'i', 'n', 'g'}));
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            s.getRemote().sendString(msg, null);
        }
    }

    public static final class AbruptServlet extends WebSocketServlet {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30000);
            factory.setCreator((req, resp) -> new AbruptSocket());
        }
    }

    public static final class AbruptSocket extends WebSocketAdapter {
        @Override
        public void onWebSocketConnect(final Session sess) {
            super.onWebSocketConnect(sess);
            // Immediately drop the TCP connection without sending a CLOSE frame.
            try {
                sess.disconnect();
            } catch (final Throwable ignore) {
                // ignore
            }
        }
    }

    public static final class TooBigServlet extends WebSocketServlet {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30000);
            factory.setCreator((req, resp) -> new TooBigSocket());
        }
    }

    /**
     * Sends a single oversized text message as soon as the WebSocket is established.
     */
    public static final class TooBigSocket extends WebSocketAdapter {
        @Override
        public void onWebSocketConnect(final Session sess) {
            super.onWebSocketConnect(sess);
            final StringBuilder sb = new StringBuilder();
            final String chunk = "1234567890abcdef-";
            // Build something comfortably larger than the maxMessage (2 KiB in the test)
            while (sb.length() <= 8192) {
                sb.append(chunk);
            }
            final String msg = sb.toString();
            sess.getRemote().sendString(msg, null);
            // Do not send CLOSE here; the client should close with 1009.
        }
    }
}
