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

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
import org.apache.hc.client5.http.websocket.client.WebSocketClientBuilder;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Example WebSocket echo client using HTTP/2 Extended CONNECT over TLS.
 *
 * <p>This example demonstrates how to establish a secure WebSocket connection
 * ({@code wss://}) over HTTP/2 with TLS. It combines the HTTP/2 Extended
 * CONNECT transport (see {@link WebSocketH2EchoClient}) with TLS encryption
 * using {@link H2ClientTlsStrategy}, which performs ALPN negotiation to
 * select the {@code h2} protocol during the TLS handshake.</p>
 *
 * <p>The example loads a trust store from the classpath
 * ({@code /test.keystore}) to trust the self-signed certificate used by
 * {@link WebSocketH2TlsEchoServer}. In production, the default JVM trust
 * store or a CA-signed certificate should be used instead.</p>
 *
 * <p>The client lifecycle is the same as the plaintext HTTP/2 variant:</p>
 * <ol>
 *   <li>Build an {@link SSLContext} with the appropriate trust material</li>
 *   <li>Configure the builder with {@link H2ClientTlsStrategy} and enable
 *       HTTP/2 in the {@link WebSocketClientConfig}</li>
 *   <li>Connect, send a text message, receive the echo, and close</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>
 *   # Connect to the default endpoint (wss://localhost:8443/echo)
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketH2TlsEchoClient
 *
 *   # Connect to a custom endpoint
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketH2TlsEchoClient wss://host:port/path
 * </pre>
 *
 * <p>Pair with {@link WebSocketH2TlsEchoServer} for a complete local test.</p>
 *
 * @since 5.7
 */
public final class WebSocketH2TlsEchoClient {

    private WebSocketH2TlsEchoClient() {
    }

    public static void main(final String[] args) throws Exception {
        final URI uri = URI.create(args.length > 0 ? args[0] : "wss://localhost:8443/echo");
        final CountDownLatch done = new CountDownLatch(1);

        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(WebSocketH2TlsEchoClient.class.getResource("/test.keystore"),
                        "nopassword".toCharArray())
                .build();

        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .enableHttp2(true)
                .setCloseWaitTimeout(Timeout.ofSeconds(2))
                .build();

        try (final CloseableWebSocketClient client = WebSocketClientBuilder.create()
                .setTlsStrategy(new H2ClientTlsStrategy(sslContext))
                .defaultConfig(cfg)
                .build()) {

            client.start();
            client.connect(uri, new WebSocketListener() {
                private WebSocket ws;

                @Override
                public void onOpen(final WebSocket ws) {
                    this.ws = ws;
                    ws.sendText("hello-h2-tls", true);
                }

                @Override
                public void onText(final CharBuffer text, final boolean last) {
                    System.out.println("[H2/TLS] echo: " + text);
                    ws.close(1000, "done");
                }

                @Override
                public void onBinary(final ByteBuffer payload, final boolean last) {
                    System.out.println("[H2/TLS] binary: " + payload.remaining());
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
                throw new IllegalStateException("Timed out waiting for H2/TLS echo");
            }
            client.initiateShutdown();
            client.awaitShutdown(TimeValue.ofSeconds(2));
        }
    }
}
