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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpServerRequestHandler.ResponseTrigger;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.websocket.WebSocketConfig;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.apache.hc.core5.websocket.WebSocketExtension;
import org.apache.hc.core5.websocket.WebSocketExtensionData;
import org.apache.hc.core5.websocket.WebSocketExtensionFactory;
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
    void releasesExtensionsWhenOnOpenThrows() throws Exception {
        final AtomicInteger closed = new AtomicInteger();
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(new CountingExtensionFactory(closed));
        final Supplier<WebSocketHandler> supplier = () -> new WebSocketHandler() {
            @Override
            public void onOpen(final WebSocketSession session) {
                throw new IllegalStateException("onOpen failed");
            }
        };
        final HttpRequestMapper<Supplier<WebSocketHandler>> mapper = (request, context) -> supplier;
        final WebSocketServerRequestHandler handler = new WebSocketServerRequestHandler(
                mapper, WebSocketConfig.DEFAULT, registry);

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/ws");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS, "x-test");

        final RecordingTrigger trigger = new RecordingTrigger();
        final HttpContext context = HttpCoreContext.create();
        final WebSocketServerConnection connection = createConnection();
        context.setAttribute(WebSocketContextKeys.CONNECTION, connection);
        try {
            handler.handle(request, trigger, context);
        } catch (final IOException ignore) {
            // A failed handshake may surface an I/O error while writing the close frame; this
            // is independent of whether the negotiated extensions were released.
        }

        assertEquals(1, closed.get(), "onOpen failure must release negotiated extensions exactly once");
        connection.close();
    }

    @Test
    void releasesExtensionsWhenSubmitResponseFails() throws Exception {
        final AtomicInteger closed = new AtomicInteger();
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(new CountingExtensionFactory(closed));
        final HttpRequestMapper<Supplier<WebSocketHandler>> mapper =
                (request, context) -> () -> new WebSocketHandler() {
                };
        final WebSocketServerRequestHandler handler = new WebSocketServerRequestHandler(
                mapper, WebSocketConfig.DEFAULT, registry);

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/ws");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS, "x-test");

        final ResponseTrigger throwingTrigger = new ResponseTrigger() {
            @Override
            public void sendInformation(final ClassicHttpResponse response) {
            }

            @Override
            public void submitResponse(final ClassicHttpResponse response) throws IOException {
                throw new IOException("submit failed");
            }
        };
        final HttpContext context = HttpCoreContext.create();
        final WebSocketServerConnection connection = createConnection();
        context.setAttribute(WebSocketContextKeys.CONNECTION, connection);

        assertThrows(IOException.class, () -> handler.handle(request, throwingTrigger, context));

        assertEquals(1, closed.get(), "a failed submitResponse must release negotiated extensions exactly once");
        connection.close();
    }

    @Test
    void releasesExtensionsOnNormalCompletion() throws Exception {
        final AtomicInteger closed = new AtomicInteger();
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(new CountingExtensionFactory(closed));
        final HttpRequestMapper<Supplier<WebSocketHandler>> mapper =
                (request, context) -> () -> new WebSocketHandler() {
                };
        final WebSocketServerRequestHandler handler = new WebSocketServerRequestHandler(
                mapper, WebSocketConfig.DEFAULT, registry);

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/ws");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS, "x-test");

        final RecordingTrigger trigger = new RecordingTrigger();
        final HttpContext context = HttpCoreContext.create();
        // The peer end of the socket is already closed, so the processor reads EOF and the
        // read loop terminates normally.
        final WebSocketServerConnection connection = createConnection();
        context.setAttribute(WebSocketContextKeys.CONNECTION, connection);

        handler.handle(request, trigger, context);

        assertEquals(1, closed.get(), "normal processor completion must release negotiated extensions exactly once");
        connection.close();
    }

    @Test
    void submitResponseFailureIsNotMaskedByExtensionCloseFailure() throws Exception {
        final RuntimeException closeFailure = new IllegalStateException("close failed");
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(throwingCloseFactory(closeFailure));
        final HttpRequestMapper<Supplier<WebSocketHandler>> mapper =
                (request, context) -> () -> new WebSocketHandler() {
                };
        final WebSocketServerRequestHandler handler = new WebSocketServerRequestHandler(
                mapper, WebSocketConfig.DEFAULT, registry);

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/ws");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS, "x-test");

        final ResponseTrigger throwingTrigger = new ResponseTrigger() {
            @Override
            public void sendInformation(final ClassicHttpResponse response) {
            }

            @Override
            public void submitResponse(final ClassicHttpResponse response) throws IOException {
                throw new IOException("submit failed");
            }
        };
        final HttpContext context = HttpCoreContext.create();
        final WebSocketServerConnection connection = createConnection();
        context.setAttribute(WebSocketContextKeys.CONNECTION, connection);

        final IOException ex = assertThrows(IOException.class,
                () -> handler.handle(request, throwingTrigger, context));
        assertEquals("submit failed", ex.getMessage(), "the original failure must be preserved");
        assertEquals(1, ex.getSuppressed().length, "the extension close failure must be suppressed, not masking");
        assertSame(closeFailure, ex.getSuppressed()[0]);
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
        // RFC 9110 section 15.5.22: a 426 response MUST carry an Upgrade header field, with the
        // matching Connection: Upgrade option (section 7.8).
        assertNotNull(trigger.response.getFirstHeader(HttpHeaders.UPGRADE));
        assertEquals("websocket", trigger.response.getFirstHeader(HttpHeaders.UPGRADE).getValue());
        assertNotNull(trigger.response.getFirstHeader(HttpHeaders.CONNECTION));
        assertTrue(trigger.response.getFirstHeader(HttpHeaders.CONNECTION).getValue()
                .equalsIgnoreCase(HeaderElements.UPGRADE));
        // RFC 6455 section 4.4: the rejection must advertise the supported version.
        assertNotNull(trigger.response.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION));
        assertEquals("13", trigger.response.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION).getValue());
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

    private static final class CountingExtensionFactory implements WebSocketExtensionFactory {

        private final AtomicInteger closed;

        CountingExtensionFactory(final AtomicInteger closed) {
            this.closed = closed;
        }

        @Override
        public String getName() {
            return "x-test";
        }

        @Override
        public WebSocketExtension create(final WebSocketExtensionData request, final boolean server) {
            return new WebSocketExtension() {
                @Override
                public String getName() {
                    return "x-test";
                }

                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        }
    }

    private static WebSocketExtensionFactory throwingCloseFactory(final RuntimeException closeFailure) {
        return new WebSocketExtensionFactory() {
            @Override
            public String getName() {
                return "x-test";
            }

            @Override
            public WebSocketExtension create(final WebSocketExtensionData request, final boolean server) {
                return new WebSocketExtension() {
                    @Override
                    public String getName() {
                        return "x-test";
                    }

                    @Override
                    public void close() {
                        throw closeFailure;
                    }
                };
            }
        };
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
