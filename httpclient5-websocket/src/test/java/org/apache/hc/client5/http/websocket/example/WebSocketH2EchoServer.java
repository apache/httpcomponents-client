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

import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.server.WebSocketH2Server;
import org.apache.hc.core5.websocket.server.WebSocketH2ServerBootstrap;

/**
 * Example WebSocket echo server using HTTP/2 Extended CONNECT.
 *
 * <p>A minimal WebSocket echo server that accepts connections over HTTP/2
 * using the Extended CONNECT method. The server validates incoming CONNECT
 * requests with the {@code :protocol} pseudo-header set to {@code "websocket"},
 * upgrades the stream, and echoes back any TEXT or BINARY messages it
 * receives.</p>
 *
 * <p>Unlike the HTTP/1.1 variant ({@link WebSocketEchoServer}), this server
 * uses {@link WebSocketH2ServerBootstrap} which handles HTTP/2 settings
 * negotiation (including {@code SETTINGS_ENABLE_CONNECT_PROTOCOL}) and
 * multiplexes each WebSocket session on its own HTTP/2 stream.</p>
 *
 * <p>The server demonstrates:</p>
 * <ul>
 *   <li>Bootstrapping an HTTP/2 WebSocket server with
 *       {@link WebSocketH2ServerBootstrap}</li>
 *   <li>Registering an echo handler at the {@code /echo} path</li>
 *   <li>Echoing text and binary payloads back to the client</li>
 *   <li>Responding to pings with matching pong payloads</li>
 *   <li>Graceful shutdown via a JVM shutdown hook</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   # Start on the default port (8080)
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketH2EchoServer
 *
 *   # Start on a custom port
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketH2EchoServer 9090
 * </pre>
 *
 * <p>Once started, the server listens on {@code ws://localhost:<port>/echo}.
 * Pair with {@link WebSocketH2EchoClient} for a complete local test.</p>
 *
 * @since 5.7
 */
public final class WebSocketH2EchoServer {

    private WebSocketH2EchoServer() {
    }

    public static void main(final String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        final CountDownLatch done = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(done::countDown));

        final WebSocketH2Server server = WebSocketH2ServerBootstrap.bootstrap()
                .setListenerPort(port)
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
        System.out.println("[H2] echo server started at ws://localhost:" + server.getLocalPort() + "/echo");

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
