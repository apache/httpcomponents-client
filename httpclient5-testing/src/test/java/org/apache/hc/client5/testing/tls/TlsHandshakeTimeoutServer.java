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
package org.apache.hc.client5.testing.tls;

import org.apache.hc.client5.testing.SSLTestContexts;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLSession;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * This test server accepts a single TLS connection request, which will then time out. The server can run in two modes.
 * If {@code sendServerHello} is false, the Client Hello message will be swallowed, and the client will time out while
 * waiting for the Server Hello record. Else, the server will respond to the Client Hello with a Server Hello, and the
 * client's connection attempt will subsequently time out while waiting for the Change Cipher Spec record from the
 * server.
 */
public class TlsHandshakeTimeoutServer implements Closeable {
    private final boolean sendServerHello;

    private volatile int port = -1;
    private volatile boolean requestReceived = false;
    private volatile ServerSocketChannel serverSocket;
    private volatile SocketChannel socket;
    private volatile Throwable throwable;

    public TlsHandshakeTimeoutServer(final boolean sendServerHello) {
        this.sendServerHello = sendServerHello;
    }

    public void start() throws IOException {
        this.serverSocket = ServerSocketChannel.open();
        this.serverSocket.bind(new InetSocketAddress("0.0.0.0", 0));
        this.port = ((InetSocketAddress) this.serverSocket.getLocalAddress()).getPort();
        new Thread(this::run).start();
    }

    private void run() {
        try {
            socket = serverSocket.accept();
            requestReceived = true;

            if (sendServerHello) {
                final SSLEngine sslEngine = initHandshake();

                receiveClientHello(sslEngine);
                sendServerHello(sslEngine);
            }
        } catch (final Throwable t) {
            this.throwable = t;
        }
    }

    private SSLEngine initHandshake() throws Exception {
        final SSLContext sslContext = SSLTestContexts.createServerSSLContext();
        final SSLEngine sslEngine = sslContext.createSSLEngine();
        // TLSv1.2 always uses a four-way handshake, which is what we want
        sslEngine.setEnabledProtocols(new String[]{ "TLSv1.2" });
        sslEngine.setUseClientMode(false);
        sslEngine.setNeedClientAuth(false);

        sslEngine.beginHandshake();
        return sslEngine;
    }

    private void receiveClientHello(final SSLEngine sslEngine) throws IOException {
        final SSLSession session = sslEngine.getSession();
        final ByteBuffer clientNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        final ByteBuffer clientAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        while (sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
            socket.read(clientNetData);
            clientNetData.flip();
            final Status status = sslEngine.unwrap(clientNetData, clientAppData).getStatus();
            if (status != Status.OK) {
                throw new RuntimeException("Bad status while unwrapping data: " + status);
            }
            clientNetData.compact();
            if (sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                sslEngine.getDelegatedTask().run();
            }
        }
    }

    private void sendServerHello(final SSLEngine sslEngine) throws IOException {
        final SSLSession session = sslEngine.getSession();
        final ByteBuffer serverAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        final ByteBuffer serverNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        while (sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
            serverAppData.flip();
            final Status status = sslEngine.wrap(serverAppData, serverNetData).getStatus();
            if (status != Status.OK) {
                throw new RuntimeException("Bad status while wrapping data: " + status);
            }
            serverNetData.flip();
            socket.write(serverNetData);
            serverNetData.compact();
            if (sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                sslEngine.getDelegatedTask().run();
            }
        }
    }

    public int getPort() {
        if (port == -1) {
            throw new IllegalStateException("Server has not been started yet");
        }
        return port;
    }

    @Override
    public void close() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (final IOException ignore) {
        }

        if (throwable != null) {
            throw new RuntimeException("Exception thrown while TlsHandshakeTimerOuter was running", throwable);
        } else if (!requestReceived) {
            throw new IllegalStateException("Never received a request");
        }
    }
}
