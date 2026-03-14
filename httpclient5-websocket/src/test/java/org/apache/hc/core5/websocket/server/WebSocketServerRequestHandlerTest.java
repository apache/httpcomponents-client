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
package org.apache.hc.core5.websocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpServerRequestHandler.ResponseTrigger;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.websocket.WebSocketConfig;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.apache.hc.core5.websocket.WebSocketExtensionRegistry;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;

class WebSocketServerRequestHandlerTest {

    @Test
    void upgradesValidRequest() throws Exception {
        final AtomicBoolean opened = new AtomicBoolean(false);
        final Supplier<WebSocketHandler> supplier = () -> new WebSocketHandler() {
            @Override
            public void onOpen(final WebSocketSession session) {
                opened.set(true);
            }

            @Override
            public void onText(final WebSocketSession session, final String payload) {
            }

            @Override
            public void onBinary(final WebSocketSession session, final ByteBuffer payload) {
            }

            @Override
            public void onPing(final WebSocketSession session, final ByteBuffer payload) {
            }

            @Override
            public void onPong(final WebSocketSession session, final ByteBuffer payload) {
            }

            @Override
            public void onClose(final WebSocketSession session, final int code, final String reason) {
            }

            @Override
            public void onError(final WebSocketSession session, final Exception cause) {
            }

            @Override
            public String selectSubprotocol(final List<String> protocols) {
                return null;
            }
        };

        final HttpRequestMapper<Supplier<WebSocketHandler>> mapper = (request, context) -> supplier;
        final WebSocketServerRequestHandler handler = new WebSocketServerRequestHandler(
                mapper,
                WebSocketConfig.DEFAULT,
                WebSocketExtensionRegistry.createDefault());

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/ws");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");

        final RecordingTrigger trigger = new RecordingTrigger();
        final HttpContext context = HttpCoreContext.create();

        final WebSocketServerConnection connection = createConnection();
        context.setAttribute(WebSocketContextKeys.CONNECTION, connection);
        handler.handle(request, trigger, context);

        assertNotNull(trigger.response);
        assertEquals(HttpStatus.SC_SWITCHING_PROTOCOLS, trigger.response.getCode());
        assertEquals("websocket", trigger.response.getFirstHeader(HttpHeaders.UPGRADE).getValue());
        assertTrue(opened.get());
        connection.close();
    }

    @Test
    void returnsUpgradeRequiredForNonUpgradeRequest() throws Exception {
        final WebSocketServerRequestHandler handler = new WebSocketServerRequestHandler(
                (request, context) -> () -> null,
                WebSocketConfig.DEFAULT,
                new WebSocketExtensionRegistry());

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/ws");
        final RecordingTrigger trigger = new RecordingTrigger();
        handler.handle(request, trigger, HttpCoreContext.create());

        assertEquals(HttpStatus.SC_UPGRADE_REQUIRED, trigger.response.getCode());
    }

    @Test
    void returnsUpgradeRequiredForInvalidSecWebSocketKey() throws Exception {
        final WebSocketServerRequestHandler handler = new WebSocketServerRequestHandler(
                (request, context) -> () -> null,
                WebSocketConfig.DEFAULT,
                new WebSocketExtensionRegistry());

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/ws");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "AQIDBA==");
        final RecordingTrigger trigger = new RecordingTrigger();
        handler.handle(request, trigger, HttpCoreContext.create());

        assertEquals(HttpStatus.SC_UPGRADE_REQUIRED, trigger.response.getCode());
    }

    private static WebSocketServerConnection createConnection() throws IOException {
        final ServerSocket server = new ServerSocket(0);
        final Socket client = new Socket("127.0.0.1", server.getLocalPort());
        final Socket socket = server.accept();
        client.close();
        server.close();
        final WebSocketServerConnectionFactory factory = new WebSocketServerConnectionFactory("http", null, null);
        return factory.createConnection(socket);
    }

    private static final class RecordingTrigger implements ResponseTrigger {
        ClassicHttpResponse response;

        @Override
        public void sendInformation(final ClassicHttpResponse response) {
            this.response = response;
        }

        @Override
        public void submitResponse(final ClassicHttpResponse response) {
            this.response = response;
        }
    }
}
