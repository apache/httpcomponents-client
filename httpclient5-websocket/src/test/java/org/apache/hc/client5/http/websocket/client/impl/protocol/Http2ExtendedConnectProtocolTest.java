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
package org.apache.hc.client5.http.websocket.client.impl.protocol;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.transport.OutboundFlowSupport;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class Http2ExtendedConnectProtocolTest {

    @Test
    void connectFailsWhenRequesterMissing() throws Exception {
        final Http2ExtendedConnectProtocol protocol = new Http2ExtendedConnectProtocol(null);
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final CompletableFuture<WebSocket> future =
                protocol.connect(URI.create("ws://localhost"), new WebSocketListener() {
                }, cfg, HttpCoreContext.create());

        final ExecutionException ex = Assertions.assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        Assertions.assertTrue(ex.getCause() instanceof Http2ExtendedConnectProtocol.H2NotAvailable);
    }

    @Test
    void connectRejectsInvalidScheme() throws Exception {
        final RecordingRequester requester = new RecordingRequester();
        final Http2ExtendedConnectProtocol protocol = new Http2ExtendedConnectProtocol(requester);
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final CompletableFuture<WebSocket> future =
                protocol.connect(URI.create("http://localhost"), new WebSocketListener() {
                }, cfg, HttpCoreContext.create());

        final ExecutionException ex = Assertions.assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        Assertions.assertTrue(ex.getCause() instanceof IllegalArgumentException);
        Assertions.assertNull(requester.requestRef.get());
    }

    @Test
    void connectBuildsRequestWithProtocolHeader() {
        final RecordingRequester requester = new RecordingRequester();
        final Http2ExtendedConnectProtocol protocol = new Http2ExtendedConnectProtocol(requester);
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();

        protocol.connect(URI.create("ws://example.com"), new WebSocketListener() {
        }, cfg, HttpCoreContext.create());

        final HttpRequest request = requester.requestRef.get();
        Assertions.assertNotNull(request);
        Assertions.assertEquals("CONNECT", request.getMethod());
        Assertions.assertEquals("/", request.getPath());
        Assertions.assertEquals("websocket", request.getFirstHeader(WebSocketConstants.PSEUDO_PROTOCOL).getValue());
    }

    @Test
    void outboundLimitTreatsZeroAsUnlimited() {
        Assertions.assertFalse(OutboundFlowSupport.exceedsOutboundByteLimit(0, Integer.MAX_VALUE - 1, 1));
        Assertions.assertTrue(OutboundFlowSupport.exceedsOutboundByteLimit(8, 7, 2));
        Assertions.assertFalse(OutboundFlowSupport.exceedsOutboundByteLimit(8, 7, 1));
    }

    private static final class RecordingRequester extends H2MultiplexingRequester {
        private final AtomicReference<HttpRequest> requestRef = new AtomicReference<>();

        RecordingRequester() {
            super(IOReactorConfig.DEFAULT,
                    (ioSession, attachment) -> null,
                    null,
                    null,
                    null,
                    DefaultAddressResolver.INSTANCE,
                    null,
                    null,
                    null
            );
        }

        @Override
        public Cancellable execute(
                final HttpHost target,
                final AsyncClientExchangeHandler exchangeHandler,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final Timeout timeout,
                final HttpContext context) {
            final HttpContext ctx = context != null ? context : HttpCoreContext.create();
            try {
                exchangeHandler.produceRequest(new RequestChannel() {
                    @Override
                    public void sendRequest(final HttpRequest request, final EntityDetails entityDetails,
                                            final HttpContext context) {
                        requestRef.set(request);
                    }
                }, ctx);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            return () -> true;
        }
    }
}
