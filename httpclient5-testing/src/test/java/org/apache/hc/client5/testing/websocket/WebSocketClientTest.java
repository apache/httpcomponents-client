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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebSocketClientTest {

    private Server server;
    private int port;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void startServer() throws Exception {
        server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "ws-test-scheduler");
            t.setDaemon(true);
            return t;
        });

        final ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/");
        ctx.addServlet(new ServletHolder(new EchoServlet()), "/echo");
        ctx.addServlet(new ServletHolder(new InterleaveServlet()), "/interleave");
        ctx.addServlet(new ServletHolder(new TooLargeServlet()), "/too-large");
        ctx.addServlet(new ServletHolder(new AbruptServlet(scheduler)), "/abrupt");
        server.setHandler(ctx);

        server.start();
        port = connector.getLocalPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (server != null) {
            server.stop();
        }
    }

    private URI uri(final String path) {
        return URI.create("ws://localhost:" + port + path);
    }

    private static CloseableWebSocketClient newClient() {
        final CloseableWebSocketClient client = WebSocketClients.createDefault();
        client.start();
        return client;
    }

    @Test
    void echo_uncompressed() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final StringBuilder echoed = new StringBuilder();

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .enablePerMessageDeflate(false)
                    .build();

            final CompletableFuture<WebSocket> f = client.connect(uri("/echo"), new WebSocketListener() {
                private WebSocket ws;

                @Override
                public void onOpen(final WebSocket ws) {
                    this.ws = ws;
                    final String msg = "hello @ " + Instant.now();
                    ws.sendText(msg, true);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    echoed.append(text);
                    ws.close(1000, "done");
                }

                @Override
                public void onClose(final int code, final String reason) {
                    try {
                        assertEquals(1000, code);
                        assertEquals("done", reason);
                        assertTrue(echoed.length() > 0);
                    } finally {
                        done.countDown();
                    }
                }

                @Override
                public void onError(final Throwable ex) {
                    errorRef.set(ex);
                    done.countDown();
                }
            }, cfg);

            f.whenComplete((ws, ex) -> {
                if (ex != null) {
                    errorRef.set(ex);
                    done.countDown();
                }
            });

            assertTrue(done.await(10, TimeUnit.SECONDS), "timeout");
            final Throwable error = errorRef.get();
            if (error != null) {
                throw new AssertionError("WebSocket error", error);
            }
        }
    }

    @Test
    void ping_interleaved_fragmentation() throws Exception {
        final CountDownLatch gotText = new CountDownLatch(1);
        final CountDownLatch gotPong = new CountDownLatch(1);
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .enablePerMessageDeflate(false)
                    .build();

            final CompletableFuture<WebSocket> f = client.connect(uri("/interleave"), new WebSocketListener() {
                @Override
                public void onOpen(final WebSocket ws) {
                    ws.ping(null);
                    ws.sendText("hello", true);
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
                public void onError(final Throwable ex) {
                    errorRef.set(ex);
                    gotText.countDown();
                    gotPong.countDown();
                }
            }, cfg);

            f.whenComplete((ws, ex) -> {
                if (ex != null) {
                    errorRef.set(ex);
                    gotText.countDown();
                    gotPong.countDown();
                }
            });

            assertTrue(gotPong.await(10, TimeUnit.SECONDS), "did not receive PONG");
            assertTrue(gotText.await(10, TimeUnit.SECONDS), "did not receive TEXT");

            final Throwable error = errorRef.get();
            if (error != null) {
                throw new AssertionError("WebSocket error", error);
            }
        }
    }

    @Test
    void max_message_1009() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Integer> closeCode = new AtomicReference<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .setMaxMessageSize(2048)
                    .enablePerMessageDeflate(false)
                    .build();

            final CompletableFuture<WebSocket> f = client.connect(uri("/too-large"), new WebSocketListener() {
                @Override
                public void onClose(final int code, final String reason) {
                    closeCode.set(code);
                    done.countDown();
                }

                @Override
                public void onError(final Throwable ex) {
                    errorRef.set(ex);
                    done.countDown();
                }
            }, cfg);

            f.whenComplete((ws, ex) -> {
                if (ex != null) {
                    errorRef.set(ex);
                    done.countDown();
                }
            });

            assertTrue(done.await(15, TimeUnit.SECONDS), "timeout waiting for close");
            if (errorRef.get() != null) {
                throw new AssertionError("WebSocket error", errorRef.get());
            }
            assertEquals(1009, closeCode.get());
        }
    }

    @Test
    void abnormal_close_1006() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Integer> closeCode = new AtomicReference<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();

            final CompletableFuture<WebSocket> f = client.connect(uri("/abrupt"), new WebSocketListener() {
                @Override
                public void onClose(final int code, final String reason) {
                    closeCode.set(code);
                    done.countDown();
                }

                @Override
                public void onError(final Throwable ex) {
                    // error is fine here, but we still expect onClose(1006)
                    errorRef.set(ex);
                }
            }, cfg);

            f.whenComplete((ws, ex) -> {
                if (ex != null) {
                    errorRef.set(ex);
                    done.countDown();
                }
            });

            assertTrue(done.await(15, TimeUnit.SECONDS), "timeout waiting for close");
            assertEquals(1006, closeCode.get());
        }
    }

    // -------------------- Jetty endpoints --------------------

    private static final class EchoServlet extends WebSocketServlet {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30000);
            factory.setCreator((req, resp) -> new EchoSocket());
        }
    }

    private static final class EchoSocket extends WebSocketAdapter {
        @Override
        public void onWebSocketText(final String msg) {
            final Session s = getSession();
            if (s != null && s.isOpen()) {
                s.getRemote().sendString(msg, null);
            }
        }
    }

    private static final class InterleaveServlet extends WebSocketServlet {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30000);
            factory.setCreator((req, resp) -> new InterleaveSocket());
        }
    }

    private static final class InterleaveSocket extends WebSocketAdapter {
        @Override
        public void onWebSocketText(final String msg) {
            final Session s = getSession();
            if (s != null && s.isOpen()) {
                try {
                    s.getRemote().sendPing(ByteBuffer.wrap(new byte[]{'p', 'i', 'n', 'g'}));
                } catch (final Exception ignore) {
                    // ignore
                }
                s.getRemote().sendString(msg, null);
            }
        }
    }

    private static final class TooLargeServlet extends WebSocketServlet {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30000);
            factory.setCreator((req, resp) -> new TooLargeSocket());
        }
    }

    private static final class TooLargeSocket extends WebSocketAdapter {
        @Override
        public void onWebSocketConnect(final Session sess) {
            super.onWebSocketConnect(sess);
            final String base = "0123456789abcdef";
            final StringBuilder sb = new StringBuilder();
            while (sb.length() < 8192) { // > 2048 bytes, deterministic
                sb.append(base);
            }
            sess.getRemote().sendString(sb.toString(), null);
        }
    }

    private static final class AbruptServlet extends WebSocketServlet {
        private final ScheduledExecutorService scheduler;

        AbruptServlet(final ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30000);
            factory.setCreator((req, resp) -> new AbruptSocket(scheduler));
        }
    }

    private static final class AbruptSocket extends WebSocketAdapter {
        private final ScheduledExecutorService scheduler;

        AbruptSocket(final ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void onWebSocketConnect(final Session sess) {
            super.onWebSocketConnect(sess);
            scheduler.schedule(() -> {
                try {
                    sess.disconnect();
                } catch (final Throwable ignore) {
                    // ignore
                }
            }, 50, TimeUnit.MILLISECONDS);
        }
    }
}
