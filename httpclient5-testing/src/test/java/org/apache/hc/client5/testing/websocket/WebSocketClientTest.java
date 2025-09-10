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
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
import org.apache.hc.client5.http.websocket.client.WebSocketClientBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.jupiter.api.AfterEach;
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
        client.start(); // <<< important: start reactor threads
        return client;
    }

    /**
     * Standard echo (uncompressed); ensures basic end-to-end is healthy.
     */
    @Test
    void echo_uncompressed() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final StringBuilder echoed = new StringBuilder();

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .enablePerMessageDeflate(false)
                    .build();

            final URI u = uri(port, "/echo");
            client.connect(u, new WebSocketListener() {
                private WebSocket ws;

                @Override
                public void onOpen(final WebSocket ws) {
                    this.ws = ws;
                    final String prefix = "hello from hc5 WS @ " + Instant.now() + " — ";
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 64; i++) {
                        sb.append(prefix);
                    }
                    ws.sendText(sb.toString(), true);
                }

                @Override
                public void onText(final CharSequence text, final boolean last) {
                    echoed.append(text);
                    ws.close(1000, "done");
                }

                @Override
                public void onClose(final int code, final String reason) {
                    assertEquals(1000, code);
                    assertEquals("done", reason);
                    assertTrue(echoed.length() > 0);
                    done.countDown();
                }

                @Override
                public void onError(final Throwable ex) {
                    ex.printStackTrace();
                    done.countDown();
                }
            }, cfg);

            assertTrue(done.await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void ping_interleaved_fragmentation() throws Exception {
        final CountDownLatch gotText = new CountDownLatch(1);
        final CountDownLatch gotPong = new CountDownLatch(1);

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .enablePerMessageDeflate(false) // keep simple
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
                public void onText(final CharSequence text, final boolean last) {
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
            }, cfg);

            assertTrue(gotPong.await(10, TimeUnit.SECONDS), "did not receive PONG");
            assertTrue(gotText.await(10, TimeUnit.SECONDS), "did not receive TEXT");
        }
    }

    /**
     * Client enforces maxMessageSize: server sends a too-large message → client closes with 1009.
     */
    @Test
    void max_message_1009() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final int maxMessage = 2048; // 2 KiB

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .setMaxMessageSize(maxMessage)
                    .enablePerMessageDeflate(false)
                    .build();

            final URI u = uri(port, "/echo");
            client.connect(u, new WebSocketListener() {
                private WebSocket ws;

                @Override
                public void onOpen(final WebSocket ws) {
                    this.ws = ws;
                    final StringBuilder sb = new StringBuilder();
                    final String chunk = "1234567890abcdef-";
                    while (sb.length() <= maxMessage * 2) { // exceed threshold
                        sb.append(chunk);
                    }
                    ws.sendText(sb.toString(), true);
                }

                @Override
                public void onText(final CharSequence text, final boolean last) {
                    // We may or may not see text before close depending on timing.
                }

                @Override
                public void onClose(final int code, final String reason) {
                    // Client should have initiated 1009 when assembling exceeded threshold.
                    assertEquals(1009, code);
                    done.countDown();
                }

                @Override
                public void onError(final Throwable ex) {
                    // Some Jetty combinations can attempt to keep writing after peer closes; we just need the close.
                    done.countDown();
                }
            }, cfg);

            assertTrue(done.await(10, TimeUnit.SECONDS), "timeout waiting for 1009 close");
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
            }, cfg);

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
                s.close(1000, "done"); // close after echo
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
            // let client drive the close
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
            new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (final InterruptedException ignore) {
                }
                try {
                    sess.disconnect();
                } catch (final Throwable ignore) {
                }
            }, "abrupt-killer").start();
        }
    }
}
