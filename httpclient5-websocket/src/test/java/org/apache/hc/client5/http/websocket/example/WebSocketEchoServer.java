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

import java.nio.ByteBuffer;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * <h1>WebSocketEchoServer</h1>
 *
 * <p>A tiny embedded Jetty WebSocket server that echoes back any TEXT or BINARY message
 * it receives. This is intended for local development and interoperability testing of
 * {@code WebSocketClient} and is <em>not</em> production hardened.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>HTTP upgrade to RFC&nbsp;6455 WebSocket on path {@code /echo}</li>
 *   <li>Echoes TEXT and BINARY frames</li>
 *   <li>Compatible with permessage-deflate (RFC&nbsp;7692); Jetty will negotiate it if offered</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   # Default port 8080
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketEchoServer
 *
 *   # Custom port
 *   java -cp ... org.apache.hc.client5.http.websocket.example.WebSocketEchoServer 9090
 * </pre>
 *
 * <p>Once started, the server listens on {@code ws://localhost:&lt;port&gt;/echo}.</p>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>If the port is already in use, Jetty will fail to start with {@code BindException}.</li>
 *   <li>Idle timeout is set to 30&nbsp;seconds for simplicity.</li>
 * </ul>
 */
public final class WebSocketEchoServer {

    public static void main(final String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        final Server server = new Server(port);
        final ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");
        server.setHandler(ctx);

        ctx.addServlet(new ServletHolder(new EchoServlet()), "/echo");
        server.start();
        System.out.println("[WS-Server] up at ws://localhost:" + port + "/echo");
        server.join();
    }

    /**
     * Simple servlet that wires a Jetty WebSocket endpoint at {@code /echo}.
     */
    public static final class EchoServlet extends WebSocketServlet {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.getPolicy().setIdleTimeout(30_000);
            // Jetty will negotiate permessage-deflate automatically if supported.
            factory.setCreator((req, resp) -> new EchoSocket());
        }
    }

    /**
     * Echoes back text and binary messages.
     */
    public static final class EchoSocket extends WebSocketAdapter {
        @Override
        public void onWebSocketText(final String msg) {
            final Session s = getSession();
            if (s != null && s.isOpen()) {
                s.getRemote().sendString(msg, null);
            }
        }

        @Override
        public void onWebSocketBinary(final byte[] payload, final int off, final int len) {
            final Session s = getSession();
            if (s != null && s.isOpen()) {
                s.getRemote().sendBytes(ByteBuffer.wrap(payload, off, len), null);
            }
        }
    }
}
