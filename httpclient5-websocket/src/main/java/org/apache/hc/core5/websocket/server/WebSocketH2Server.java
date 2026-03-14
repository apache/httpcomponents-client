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
import java.util.concurrent.ExecutionException;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

public final class WebSocketH2Server {

    private final HttpAsyncServer server;
    private final InetAddress localAddress;
    private final int port;
    private final URIScheme scheme;
    private volatile ListenerEndpoint endpoint;

    WebSocketH2Server(final HttpAsyncServer server, final InetAddress localAddress, final int port, final URIScheme scheme) {
        this.server = Args.notNull(server, "server");
        this.localAddress = localAddress;
        this.port = port;
        this.scheme = scheme != null ? scheme : URIScheme.HTTP;
    }

    public void start() throws IOException {
        server.start();
        try {
            final InetSocketAddress address = localAddress != null
                    ? new InetSocketAddress(localAddress, Math.max(port, 0))
                    : new InetSocketAddress(Math.max(port, 0));
            this.endpoint = server.listen(address, scheme).get();
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException(ex.getMessage(), ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex.getMessage(), ex);
        }
    }

    public void stop() {
        server.close(CloseMode.GRACEFUL);
    }

    public void initiateShutdown() {
        server.initiateShutdown();
    }

    public InetAddress getInetAddress() {
        if (endpoint != null && endpoint.getAddress() instanceof InetSocketAddress) {
            return ((InetSocketAddress) endpoint.getAddress()).getAddress();
        }
        return localAddress;
    }

    public int getLocalPort() {
        if (endpoint != null && endpoint.getAddress() instanceof InetSocketAddress) {
            return ((InetSocketAddress) endpoint.getAddress()).getPort();
        }
        return port;
    }

    public void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        server.awaitShutdown(waitTime);
    }
}
