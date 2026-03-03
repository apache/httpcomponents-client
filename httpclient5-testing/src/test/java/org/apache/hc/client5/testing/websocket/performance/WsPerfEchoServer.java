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
package org.apache.hc.client5.testing.websocket.performance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.server.WebSocketServer;
import org.apache.hc.core5.websocket.server.WebSocketServerBootstrap;

public final class WsPerfEchoServer {
    private WebSocketServer server;
    private int port;

    public void start() throws Exception {
        start(0);
    }

    public void start(final int listenerPort) throws Exception {
        server = WebSocketServerBootstrap.bootstrap()
                .setListenerPort(listenerPort)
                .setCanonicalHostName("127.0.0.1")
                .register("/echo", EchoHandler::new)
                .create();
        server.start();
        this.port = server.getLocalPort();
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    public String uri() {
        return "ws://127.0.0.1:" + port + "/echo";
    }

    public static void main(final String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        final WsPerfEchoServer server = new WsPerfEchoServer();
        server.start(port);
        System.out.println("[PERF] echo server started at " + server.uri());
        final CountDownLatch done = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (final Exception ignore) {
            } finally {
                done.countDown();
            }
        }));
        done.await();
    }

    private static final class EchoHandler implements WebSocketHandler {
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
                session.sendBinary(data != null ? data.asReadOnlyBuffer() : ByteBuffer.allocate(0));
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
