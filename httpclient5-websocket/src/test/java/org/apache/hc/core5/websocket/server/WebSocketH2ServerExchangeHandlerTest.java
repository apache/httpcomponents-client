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

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.apache.hc.core5.websocket.WebSocketExtensionRegistry;
import org.apache.hc.core5.websocket.WebSocketHandler;
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
}
