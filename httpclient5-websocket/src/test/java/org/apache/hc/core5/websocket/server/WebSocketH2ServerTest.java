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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebSocketH2ServerTest {

    @Test
    void startUsesEndpointAddressAndScheme() throws Exception {
        final InetAddress local = InetAddress.getLoopbackAddress();
        final InetSocketAddress endpointAddress = new InetSocketAddress("127.0.0.2", 12345);
        final StubHttpAsyncServer server = new StubHttpAsyncServer(endpointAddress);
        final WebSocketH2Server ws = new WebSocketH2Server(server, local, 0, URIScheme.HTTPS);
        try {
            ws.start();
            Assertions.assertTrue(server.started);
            Assertions.assertEquals(URIScheme.HTTPS, server.lastScheme);
            Assertions.assertEquals(12345, ws.getLocalPort());
            Assertions.assertEquals("127.0.0.2", ws.getInetAddress().getHostAddress());
        } finally {
            ws.stop();
        }
    }

    @Test
    void startPropagatesIOExceptionFromListen() {
        final StubHttpAsyncServer server = new StubHttpAsyncServer(null);
        server.failWith = new IOException("boom");
        final WebSocketH2Server ws = new WebSocketH2Server(server, null, 0, URIScheme.HTTP);
        Assertions.assertThrows(IOException.class, ws::start);
    }

    @Test
    void accessorsReturnConfiguredValuesBeforeStart() {
        final InetAddress local = InetAddress.getLoopbackAddress();
        final WebSocketH2Server ws = new WebSocketH2Server(new StubHttpAsyncServer(null), local, 8443, URIScheme.HTTP);
        Assertions.assertEquals(local, ws.getInetAddress());
        Assertions.assertEquals(8443, ws.getLocalPort());
    }

    @Test
    void stopAndShutdownDelegateToServer() {
        final StubHttpAsyncServer server = new StubHttpAsyncServer(null);
        final WebSocketH2Server ws = new WebSocketH2Server(server, null, 0, URIScheme.HTTP);
        ws.initiateShutdown();
        Assertions.assertTrue(server.shutdownCalled);
        ws.stop();
        Assertions.assertEquals(CloseMode.GRACEFUL, server.closeMode);
    }

    private static final class StubHttpAsyncServer extends HttpAsyncServer {
        private final InetSocketAddress endpointAddress;
        private boolean started;
        private boolean shutdownCalled;
        private CloseMode closeMode;
        private URIScheme lastScheme;
        private SocketAddress lastAddress;
        private IOException failWith;

        StubHttpAsyncServer(final InetSocketAddress endpointAddress) {
            super((ioSession, attachment) -> null,
                    IOReactorConfig.DEFAULT,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            this.endpointAddress = endpointAddress;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public Future<ListenerEndpoint> listen(final SocketAddress address, final URIScheme scheme) {
            lastAddress = address;
            lastScheme = scheme;
            final CompletableFuture<ListenerEndpoint> future = new CompletableFuture<>();
            if (failWith != null) {
                future.completeExceptionally(failWith);
            } else {
                final SocketAddress effective = endpointAddress != null ? endpointAddress : address;
                future.complete(new StubListenerEndpoint(effective));
            }
            return future;
        }

        @Override
        public void initiateShutdown() {
            shutdownCalled = true;
        }

        @Override
        public void close(final CloseMode closeMode) {
            this.closeMode = closeMode;
        }
    }

    private static final class StubListenerEndpoint implements ListenerEndpoint {
        private final SocketAddress address;
        private boolean closed;

        StubListenerEndpoint(final SocketAddress address) {
            this.address = address;
        }

        @Override
        public SocketAddress getAddress() {
            return address;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close(final CloseMode closeMode) {
            closed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
