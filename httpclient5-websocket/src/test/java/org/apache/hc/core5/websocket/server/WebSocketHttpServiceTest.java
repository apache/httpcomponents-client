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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.SocketAddress;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

class WebSocketHttpServiceTest {

    @Test
    void setsConnectionInContext() throws Exception {
        final HttpProcessor processor = HttpProcessors.server();
        final HttpServerRequestHandler handler = (request, trigger, context) -> {
        };
        final WebSocketHttpService service = new WebSocketHttpService(processor, handler, null, null, null);
        final HttpContext context = HttpCoreContext.create();

        final FailingConnection connection = new FailingConnection();
        final IOException thrown = assertThrows(IOException.class,
                () -> service.handleRequest(connection, context));
        assertEquals("boom", thrown.getMessage());
        assertEquals(connection, context.getAttribute(WebSocketContextKeys.CONNECTION));
    }

    private static final class FailingConnection implements HttpServerConnection {
        @Override
        public ClassicHttpRequest receiveRequestHeader() throws HttpException, IOException {
            throw new IOException("boom");
        }

        @Override
        public void receiveRequestEntity(final ClassicHttpRequest request) throws HttpException, IOException {
        }

        @Override
        public void sendResponseHeader(final ClassicHttpResponse response) throws HttpException, IOException {
        }

        @Override
        public void sendResponseEntity(final ClassicHttpResponse response) throws HttpException, IOException {
        }

        @Override
        public boolean isDataAvailable(final Timeout timeout) {
            return false;
        }

        @Override
        public boolean isStale() {
            return false;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }

        @Override
        public Timeout getSocketTimeout() {
            return Timeout.ZERO_MILLISECONDS;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
        }

        @Override
        public EndpointDetails getEndpointDetails() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return null;
        }

        @Override
        public SSLSession getSSLSession() {
            return null;
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}
