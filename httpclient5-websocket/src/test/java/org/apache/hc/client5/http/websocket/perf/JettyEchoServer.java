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
package org.apache.hc.client5.http.websocket.perf;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public final class JettyEchoServer {
    private final Server server = new Server();
    private int port;

    public void start() throws Exception {
        // Ephemeral port
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.setConnectors(new Connector[]{connector});

        // Context + WebSocket servlet at /echo
        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(EchoServlet.class, "/echo");
        server.setHandler(context);

        server.start();
        this.port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    public void stop() throws Exception {
        server.stop();
    }

    public String uri() {
        return "ws://127.0.0.1:" + port + "/echo";
    }

    public static class EchoServlet extends WebSocketServlet {
        private static final long serialVersionUID = 1L;

        @Override
        public void configure(final WebSocketServletFactory factory) {
            // PMCE (permessage-deflate) is available by default in Jetty 9.4 when on classpath.
            // No need to call the deprecated getExtensionFactory().
            factory.getPolicy().setMaxTextMessageSize(65536);
            factory.getPolicy().setMaxBinaryMessageSize(65536);
            factory.register(EchoSocket.class);
        }
    }

    @WebSocket
    public static class EchoSocket {
        @OnWebSocketMessage
        public void onText(final Session session, final String message) {
            try {
                session.getRemote().sendString(message);
            } catch (final IOException ignore) { }
        }

        @OnWebSocketMessage
        public void onBinary(final Session session, final byte[] payload, final int offset, final int len) {
            try {
                session.getRemote().sendBytes(ByteBuffer.wrap(payload, offset, len));
            } catch (final IOException ignore) { }
        }
    }
}

