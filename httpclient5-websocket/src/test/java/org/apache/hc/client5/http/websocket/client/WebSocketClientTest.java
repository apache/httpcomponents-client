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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;

final class WebSocketClientTest {

    private static final class NoNetworkClient extends CloseableWebSocketClient {

        @Override
        public void start() {
            // no-op
        }

        @Override
        public IOReactorStatus getStatus() {
            return IOReactorStatus.ACTIVE;
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) {
            // no-op
        }

        @Override
        public void initiateShutdown() {
            // no-op
        }

        // ModalCloseable (if your ModalCloseable declares this)
        public void close(final CloseMode closeMode) {
            // no-op
        }

        // Closeable
        @Override
        public void close() {
            // no-op – needed for try-with-resources
        }

        @Override
        protected CompletableFuture<WebSocket> doConnect(
                final URI uri,
                final WebSocketListener listener,
                final WebSocketClientConfig cfg,
                final HttpContext context) {

            final CompletableFuture<WebSocket> f = new CompletableFuture<>();
            final LocalLoopWebSocket ws = new LocalLoopWebSocket(listener, cfg);
            try {
                listener.onOpen(ws);
            } catch (final Throwable ignore) {
            }
            f.complete(ws);
            return f;
        }
    }

    private static final class LocalLoopWebSocket implements WebSocket {
        private final WebSocketListener listener;
        private final WebSocketClientConfig cfg;
        private volatile boolean open = true;

        LocalLoopWebSocket(final WebSocketListener listener, final WebSocketClientConfig cfg) {
            this.listener = listener;
            this.cfg = cfg != null ? cfg : WebSocketClientConfig.custom().build();
        }

        @Override
        public boolean sendText(final CharSequence data, final boolean finalFragment) {
            if (!open) {
                return false;
            }
            if (cfg.getMaxMessageSize() > 0 && data != null && data.length() > cfg.getMaxMessageSize()) {
                // Simulate client closing due to oversized message
                try {
                    listener.onClose(1009, "Message too big");
                } catch (final Throwable ignore) {
                }
                open = false;
                return false;
            }
            try {
                final CharBuffer cb = data != null ? CharBuffer.wrap(data) : CharBuffer.allocate(0);
                listener.onText(cb, finalFragment);
            } catch (final Throwable ignore) {
            }
            return true;
        }

        @Override
        public boolean sendBinary(final ByteBuffer data, final boolean finalFragment) {
            if (!open) {
                return false;
            }
            try {
                listener.onBinary(data != null ? data.asReadOnlyBuffer() : ByteBuffer.allocate(0), finalFragment);
            } catch (final Throwable ignore) {
            }
            return true;
        }

        @Override
        public boolean ping(final ByteBuffer data) {
            if (!open) {
                return false;
            }
            try {
                listener.onPong(data != null ? data.asReadOnlyBuffer() : ByteBuffer.allocate(0));
            } catch (final Throwable ignore) {
            }
            return true;
        }

        @Override
        public boolean pong(final ByteBuffer data) {
            // In a real client this would send a PONG; here it's a no-op.
            return open;
        }

        @Override
        public CompletableFuture<Void> close(final int statusCode, final String reason) {
            final CompletableFuture<Void> f = new CompletableFuture<>();
            if (!open) {
                f.complete(null);
                return f;
            }
            open = false;
            try {
                listener.onClose(statusCode, reason != null ? reason : "");
            } catch (final Throwable ignore) {
            }
            f.complete(null);
            return f;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public boolean sendTextBatch(final List<CharSequence> fragments, final boolean finalFragment) {
            if (!open) {
                return false;
            }
            if (fragments == null || fragments.isEmpty()) {
                return true;
            }
            for (int i = 0; i < fragments.size(); i++) {
                final boolean last = i == fragments.size() - 1 && finalFragment;
                if (!sendText(fragments.get(i), last)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean sendBinaryBatch(final List<ByteBuffer> fragments, final boolean finalFragment) {
            if (!open) {
                return false;
            }
            if (fragments == null || fragments.isEmpty()) {
                return true;
            }
            for (int i = 0; i < fragments.size(); i++) {
                final boolean last = i == fragments.size() - 1 && finalFragment;
                if (!sendBinary(fragments.get(i), last)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public long queueSize() {
            return 0;
        }
    }

    private static CloseableWebSocketClient newClient() {
        final CloseableWebSocketClient c = new NoNetworkClient();
        c.start();
        return c;
    }

    // ------------------------------- Tests -----------------------------------

    @Test
    void echo_uncompressed_no_network() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final StringBuilder echoed = new StringBuilder();

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .enablePerMessageDeflate(false)
                    .build();

            client.connect(URI.create("ws://example/echo"), new WebSocketListener() {
                private WebSocket ws;

                @Override
                public void onOpen(final WebSocket ws) {
                    this.ws = ws;
                    final String prefix = "hello @ " + Instant.now() + " — ";
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 16; i++) {
                        sb.append(prefix);
                    }
                    ws.sendText(sb, true);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
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
                    done.countDown();
                }
            }, cfg, null);

            assertTrue(done.await(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void ping_interleaved_fragmentation_no_network() throws Exception {
        final CountDownLatch gotText = new CountDownLatch(1);
        final CountDownLatch gotPong = new CountDownLatch(1);

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .enablePerMessageDeflate(false)
                    .build();

            client.connect(URI.create("ws://example/interleave"), new WebSocketListener() {

                @Override
                public void onOpen(final WebSocket ws) {
                    ws.ping(StandardCharsets.UTF_8.encode("ping"));
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
            }, cfg, null);

            assertTrue(gotPong.await(2, TimeUnit.SECONDS));
            assertTrue(gotText.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void max_message_1009_no_network() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final int maxMessage = 2048;

        try (final CloseableWebSocketClient client = newClient()) {
            final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                    .setMaxMessageSize(maxMessage)
                    .enablePerMessageDeflate(false)
                    .build();

            client.connect(URI.create("ws://example/echo"), new WebSocketListener() {
                @Override
                public void onOpen(final WebSocket ws) {
                    final StringBuilder sb = new StringBuilder();
                    final String chunk = "1234567890abcdef-";
                    while (sb.length() <= maxMessage * 2) {
                        sb.append(chunk);
                    }
                    ws.sendText(sb, true);
                }

                @Override
                public void onClose(final int code, final String reason) {
                    assertEquals(1009, code);
                    done.countDown();
                }
            }, cfg, null);

            assertTrue(done.await(2, TimeUnit.SECONDS));
        }
    }
}
