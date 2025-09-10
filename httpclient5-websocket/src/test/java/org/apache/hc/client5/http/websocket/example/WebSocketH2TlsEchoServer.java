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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.server.WebSocketH2Server;
import org.apache.hc.core5.websocket.server.WebSocketH2ServerBootstrap;

/**
 * Example WebSocket echo server using HTTP/2 Extended CONNECT over TLS.
 *
 * <p>A minimal secure WebSocket echo server ({@code wss://}) that accepts
 * connections over HTTP/2 with TLS. It combines the HTTP/2 Extended CONNECT
 * server (see {@link WebSocketH2EchoServer}) with TLS encryption using
 * {@link H2ServerTlsStrategy}, which advertises {@code h2} via ALPN during
 * the TLS handshake.</p>
 *
 * <p>The example loads both trust material and key material from the
 * classpath ({@code /test.keystore}) using a self-signed certificate. In
 * production, a CA-signed certificate and proper key management should be
 * used instead.</p>
 *
 * <p>The server demonstrates:</p>
 * <ul>
 *   <li>Configuring a {@link SSLContext} with key and trust material</li>
 *   <li>Bootstrapping a TLS-enabled HTTP/2 WebSocket server with
 *       {@link WebSocketH2ServerBootstrap} and {@link H2ServerTlsStrategy}</li>
 *   <li>Registering an echo handler at the {@code /echo} path</li>
 *   <li>Echoing text and binary payloads back to the client</li>
 *   <li>Responding to pings with matching pong payloads</li>
 *   <li>Graceful shutdown via a JVM shutdown hook</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   # Start on the default port (8443)
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketH2TlsEchoServer
 *
 *   # Start on a custom port
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketH2TlsEchoServer 9443
 * </pre>
 *
 * <p>Once started, the server listens on {@code wss://localhost:<port>/echo}.
 * Pair with {@link WebSocketH2TlsEchoClient} for a complete local test.</p>
 *
 * @since 5.7
 */
public final class WebSocketH2TlsEchoServer {

    private WebSocketH2TlsEchoServer() {
    }

    public static void main(final String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : 8443;
        final CountDownLatch done = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(done::countDown));

        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(WebSocketH2TlsEchoServer.class.getResource("/test.keystore"),
                        "nopassword".toCharArray())
                .loadKeyMaterial(WebSocketH2TlsEchoServer.class.getResource("/test.keystore"),
                        "nopassword".toCharArray(), "nopassword".toCharArray())
                .build();

        final WebSocketH2Server server = WebSocketH2ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setCanonicalHostName("localhost")
                .setTlsStrategy(new H2ServerTlsStrategy(sslContext))
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

                    @Override
                    public void onClose(final WebSocketSession session, final int code, final String reason) {
                        // Keep server running for additional sessions.
                    }

                    @Override
                    public void onError(final WebSocketSession session, final Exception cause) {
                        cause.printStackTrace(System.err);
                    }
                })
                .create();

        server.start();
        System.out.println("[H2/TLS] echo server started at wss://localhost:" + server.getLocalPort() + "/echo");

        try {
            done.await();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            server.initiateShutdown();
            server.stop();
        }
    }
}
