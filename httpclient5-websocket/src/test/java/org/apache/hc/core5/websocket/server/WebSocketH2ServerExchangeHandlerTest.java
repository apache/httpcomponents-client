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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.apache.hc.core5.websocket.WebSocketExtension;
import org.apache.hc.core5.websocket.WebSocketExtensionData;
import org.apache.hc.core5.websocket.WebSocketExtensionFactory;
import org.apache.hc.core5.websocket.WebSocketExtensionRegistry;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;

class WebSocketH2ServerExchangeHandlerTest {

    private static final class CapturingResponseChannel implements ResponseChannel {
        private HttpResponse response;

        @Override
        public void sendInformation(final HttpResponse response, final HttpContext context) {
            // not used
        }

        @Override
        public void sendResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context) {
            this.response = response;
        }

        @Override
        public void pushPromise(final HttpRequest promise, final AsyncPushProducer responseProducer, final HttpContext context) {
            // not used
        }

        HttpResponse getResponse() {
            return response;
        }
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

    @Test
    void rejectsNonConnectMethod() throws Exception {
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, WebSocketExtensionRegistry.createDefault());

        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");

        final CapturingResponseChannel channel = new CapturingResponseChannel();
        handler.handleRequest(request, null, channel, HttpCoreContext.create());

        assertNotNull(channel.getResponse());
        assertEquals(HttpStatus.SC_BAD_REQUEST, channel.getResponse().getCode());
    }

    @Test
    void rejectsMissingProtocolHeader() throws Exception {
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, WebSocketExtensionRegistry.createDefault());

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");

        final CapturingResponseChannel channel = new CapturingResponseChannel();
        handler.handleRequest(request, null, channel, HttpCoreContext.create());

        assertNotNull(channel.getResponse());
        assertEquals(HttpStatus.SC_BAD_REQUEST, channel.getResponse().getCode());
    }

    @Test
    void rejectsUnknownProtocol() throws Exception {
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, WebSocketExtensionRegistry.createDefault());

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "chat");

        final CapturingResponseChannel channel = new CapturingResponseChannel();
        handler.handleRequest(request, null, channel, HttpCoreContext.create());

        assertNotNull(channel.getResponse());
        assertEquals(HttpStatus.SC_BAD_REQUEST, channel.getResponse().getCode());
    }

    @Test
    void releasesExtensionsWhenOnOpenThrows() throws Exception {
        final AtomicInteger closed = new AtomicInteger();
        final AtomicReference<Runnable> worker = new AtomicReference<>();
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(new CountingExtensionFactory(closed));
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                    @Override
                    public void onOpen(final WebSocketSession session) {
                        throw new IllegalStateException("onOpen failed");
                    }
                }, null, registry, worker::set);

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER, "x-test");
        handler.handleRequest(request, null, new CapturingResponseChannel(), HttpCoreContext.create());

        worker.get().run();

        assertEquals(1, closed.get(), "onOpen failure must release negotiated extensions exactly once");
    }

    @Test
    void releasesExtensionsWhenExecutorRejects() throws Exception {
        final AtomicInteger closed = new AtomicInteger();
        final Executor rejecting = command -> {
            throw new RejectedExecutionException("rejected");
        };
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(new CountingExtensionFactory(closed));
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, registry, rejecting);

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER, "x-test");

        assertThrows(RejectedExecutionException.class, () ->
                handler.handleRequest(request, null, new CapturingResponseChannel(), HttpCoreContext.create()));

        assertEquals(1, closed.get(), "a rejected executor must release negotiated extensions exactly once");
    }

    @Test
    void releasesExtensionsOnNormalCompletion() throws Exception {
        final AtomicInteger closed = new AtomicInteger();
        final AtomicReference<Runnable> worker = new AtomicReference<>();
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(new CountingExtensionFactory(closed));
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, registry, worker::set);

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER, "x-test");
        handler.handleRequest(request, null, new CapturingResponseChannel(), HttpCoreContext.create());

        // A clean end-of-stream lets the processor read loop terminate normally.
        handler.streamEnd(null);
        worker.get().run();

        assertEquals(1, closed.get(), "normal processor completion must release negotiated extensions exactly once");
    }

    @Test
    void executorRejectionIsNotMaskedByExtensionCloseFailure() throws Exception {
        final RuntimeException closeFailure = new IllegalStateException("close failed");
        final Executor rejecting = command -> {
            throw new RejectedExecutionException("rejected");
        };
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(throwingCloseFactory(closeFailure));
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, registry, rejecting);

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER, "x-test");

        final RejectedExecutionException ex = assertThrows(RejectedExecutionException.class, () ->
                handler.handleRequest(request, null, new CapturingResponseChannel(), HttpCoreContext.create()));
        assertEquals("rejected", ex.getMessage(), "the original rejection must be preserved");
        assertEquals(1, ex.getSuppressed().length, "the extension close failure must be suppressed, not masking");
        assertSame(closeFailure, ex.getSuppressed()[0]);
    }

    @Test
    void streamIsTornDownEvenWhenExtensionCloseThrows() throws Exception {
        final RuntimeException closeFailure = new IllegalStateException("close failed");
        final AtomicReference<Runnable> worker = new AtomicReference<>();
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(throwingCloseFactory(closeFailure));
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, registry, worker::set);

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER, "x-test");
        handler.handleRequest(request, null, new CapturingResponseChannel(), HttpCoreContext.create());

        handler.streamEnd(null);
        // The worker's extension close() throws, but the stream teardown must still have run first.
        assertThrows(RuntimeException.class, () -> worker.get().run());

        final CollectingDataStreamChannel channel = new CollectingDataStreamChannel();
        while (handler.available() > 0) {
            handler.produce(channel);
        }
        assertTrue(channel.endStreamCalled(),
                "the HTTP/2 stream must be terminated with END_STREAM despite the extension close failure");
    }

    @Test
    void protocolViolationClosesWith1002() throws Exception {
        final AtomicReference<Runnable> worker = new AtomicReference<>();
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, WebSocketExtensionRegistry.createDefault(), worker::set);

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        handler.handleRequest(request, null, new CapturingResponseChannel(), HttpCoreContext.create());

        // A fragmented (FIN=0) masked PING violates RFC 6455 section 5.5 and raises a
        // checked WebSocketException, which must map to close code 1002, not 1011.
        handler.consume(ByteBuffer.wrap(new byte[]{0x09, (byte) 0x80, 1, 2, 3, 4}));
        worker.get().run();

        final CollectingDataStreamChannel channel = new CollectingDataStreamChannel();
        while (handler.available() > 0) {
            handler.produce(channel);
        }
        final byte[] out = channel.bytes();
        assertEquals((byte) 0x88, out[0], "expected a CLOSE frame");
        final int code = ((out[2] & 0xFF) << 8) | (out[3] & 0xFF);
        assertEquals(1002, code, "protocol violation must close with 1002");
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

    private static final class CollectingDataStreamChannel implements DataStreamChannel {
        private final ByteArrayOutputStream collected = new ByteArrayOutputStream();
        private boolean endStreamCalled;

        @Override
        public void requestOutput() {
        }

        @Override
        public int write(final ByteBuffer src) {
            final int n = src.remaining();
            final byte[] chunk = new byte[n];
            src.get(chunk);
            collected.write(chunk, 0, n);
            return n;
        }

        @Override
        public void endStream() {
            endStreamCalled = true;
        }

        @Override
        public void endStream(final List<? extends Header> trailers) {
            endStreamCalled = true;
        }

        byte[] bytes() {
            return collected.toByteArray();
        }

        boolean endStreamCalled() {
            return endStreamCalled;
        }
    }

    @Test
    void writeAfterStreamFailureFailsInsteadOfBlocking() throws Exception {
        final AtomicReference<Runnable> worker = new AtomicReference<>();
        final AtomicReference<Exception> error = new AtomicReference<>();
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                    @Override
                    public void onOpen(final WebSocketSession session) {
                        try {
                            session.sendText("hello");
                        } catch (final Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    @Override
                    public void onError(final WebSocketSession session, final Exception cause) {
                        error.set(cause);
                    }
                }, null, WebSocketExtensionRegistry.createDefault(), worker::set);

        final HttpRequest request = new BasicHttpRequest(Method.CONNECT, "/echo");
        request.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        handler.handleRequest(request, null, new CapturingResponseChannel(), HttpCoreContext.create());

        // The stream fails before the worker gets to run; a write must then surface an
        // error through the session instead of blocking on the dead outbound queue.
        handler.failed(new IOException("stream reset"));
        worker.get().run();

        assertNotNull(error.get(), "write after stream failure must fail, not block");
    }

    @Test
    void advertisesBoundedInboundCapacity() throws Exception {
        final WebSocketH2ServerExchangeHandler handler = new WebSocketH2ServerExchangeHandler(
                new WebSocketHandler() {
                }, null, WebSocketExtensionRegistry.createDefault());

        final AtomicInteger granted = new AtomicInteger();
        final CapacityChannel channel = new CapacityChannel() {
            @Override
            public void update(final int increment) {
                granted.addAndGet(increment);
            }
        };

        handler.updateCapacity(channel);
        handler.updateCapacity(channel); // a repeated query must not re-grant the initial window

        assertEquals(256 * 1024, granted.get(),
                "inbound credit must be a bounded window, not Integer.MAX_VALUE");
    }
}
