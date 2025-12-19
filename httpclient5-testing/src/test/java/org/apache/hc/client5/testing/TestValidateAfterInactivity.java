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

package org.apache.hc.client5.testing;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.testing.TestValidateAfterInactivity.TcpReset;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hc.core5.util.TimeValue.MAX_VALUE;
import static org.apache.hc.core5.util.TimeValue.ZERO_MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests validateAfterInactivity behavior in both sync and async clients.
 */
class AbstractTestValidateAfterInactivity {
    final AtomicInteger connectionsEstablished = new AtomicInteger(0);
    final AtomicReference<SocketChannel> currentConnection = new AtomicReference<>();
    volatile ServerSocketChannel serverSocket;
    volatile int port;
    volatile Thread serverThread;

    @BeforeEach
    void setup() throws Exception {
        serverSocket = ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        port = ((InetSocketAddress) serverSocket.getLocalAddress()).getPort();

        serverThread = new Thread(this::runServer);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        final SocketChannel socket = currentConnection.getAndSet(null);
        if (socket != null) {
            socket.close();
        }

        if (serverSocket != null) {
            serverSocket.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join(1000);
        }
    }

    @Test
    void testSyncClientWithStaleConnection() throws Exception {
        try (final CloseableHttpClient client = syncClient(false)) {
            sendPing(client);
            sendPing(client);
            assertEquals(1, connectionsEstablished.getAndSet(0));

            closeServerEndOfConnection();

            /*
             * There are two things that can happen when reusing a stale connection.
             *
             * If we manage to send off the request and then read the end of the TCP stream, we will get a
             * NoHttpResponseException. This corresponds to a TCP half-close: the server has only closed its own end of
             * the connection, not the client's, so theoretically the client could send (and the server could swallow)
             * an arbitrary amount of data.
             *
             * If we are unable to send the request at all, or we can only send part of the request, we will get a
             * SocketException. This corresponds to a TCP reset; there's no such thing as "TCP half-reset."
             */
            final Class<? extends IOException> expectedException = this instanceof TcpReset ?
                SocketException.class : IOException.class;
            assertThrows(expectedException, () -> sendPing(client));
            assertEquals(0, connectionsEstablished.get());
        }
    }

    @Test
    void testSyncClientWithValidateAfterInactivity() throws Exception {
        try (final CloseableHttpClient client = syncClient(true)) {
            sendPing(client);
            sendPing(client);
            assertEquals(1, connectionsEstablished.getAndSet(0));

            closeServerEndOfConnection();

            sendPing(client);
            assertEquals(1, connectionsEstablished.get());
        }
    }

    @Test
    void testAsyncClientWithStaleConnection() throws Exception {
        testAsyncClient(false);
    }

    @Test
    void testAsyncClientWithValidateAfterInactivity() throws Exception {
        testAsyncClient(true);
    }

    private void testAsyncClient(final boolean validateAfterInactivity) throws Exception {
        try (final CloseableHttpAsyncClient client = asyncClient(validateAfterInactivity)) {
            sendPing(client);
            sendPing(client);
            assertEquals(1, connectionsEstablished.getAndSet(0));

            closeServerEndOfConnection();

            sendPing(client);
            assertEquals(1, connectionsEstablished.get());
        }
    }

    protected void closeServerEndOfConnection() throws IOException, InterruptedException {
        currentConnection.get().close();

        // It is impossible to guarantee that a connection from the connection pool will not be closed mid-request.
        // Even over localhost, closing a socket is inherently an asynchronous operation prone to race conditions.
        // Not only do we need to see the TCP `FIN` or `RST` in time, but also the IOReactor (in the case of the async
        // client) is asynchronously notified of the connection's closure; until processClosedSessions() runs, the stale
        // connection will remain in the thread pool.
        //
        // These sorts of inherent race condition are unrelated to what is being asserted in these tests: it is always
        // possible that a request will fail due to a closure race condition, but we want to ensure that connections
        // are not reused when we know *from the beginning* that they are already closed.
        Thread.sleep(50);
    }

    private void sendPing(final CloseableHttpClient client) throws URISyntaxException, IOException {
        final HttpHost target = new HttpHost("localhost", port);
        final URI requestUri = new URI("/ping");

        final String response = client.execute(target, new HttpGet(requestUri), new BasicHttpClientResponseHandler());

        assertEquals("OK", response);
    }

    private void sendPing(final CloseableHttpAsyncClient client) throws ExecutionException, InterruptedException {
        final HttpHost target = new HttpHost("localhost", port);
        final SimpleHttpRequest request = SimpleRequestBuilder.get().setHttpHost(target).setPath("/ping").build();

        final SimpleHttpResponse response = client.execute(request, null).get();

        assertEquals(200, response.getCode());
    }

    private void runServer() {
        try {
            while (!Thread.currentThread().isInterrupted() && serverSocket.isOpen()) {
                final SocketChannel socketChannel = serverSocket.accept();
                socketChannel.configureBlocking(true);
                connectionsEstablished.incrementAndGet();
                currentConnection.set(socketChannel);
                handleConnection(socketChannel);
            }
        } catch (final IOException e) {
            if (!Thread.currentThread().isInterrupted() && serverSocket.isOpen()) {
                System.err.println("Server error: " + e.getClass() + e.getMessage());
            }
        }
    }

    private static void handleConnection(final SocketChannel socketChannel) throws IOException {
        try {
            final ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (socketChannel.isOpen()) {
                buffer.clear();
                final int bytesRead = socketChannel.read(buffer);
                if (bytesRead <= 0) {
                    return;
                }

                final String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: 2\r\n" +
                    "Connection: keep-alive\r\n" +
                    "\r\n" +
                    "OK";

                final ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(UTF_8));
                while (responseBuffer.hasRemaining()) {
                    socketChannel.write(responseBuffer);
                }
            }
        } catch (final AsynchronousCloseException ignore) {
            // Connection closure was initiated on the server's end
        } catch (final IOException ex) {
            if (ex.getMessage().startsWith("Connection reset")) {
                System.err.println("Server saw connection closed by client");
                return;
            }
            throw ex;
        }
    }

    private CloseableHttpClient syncClient(final boolean validateAfterInactivity) {
        final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setDefaultConnectionConfig(getConnectionConfig(validateAfterInactivity));
        return HttpClients.custom()
            .setConnectionManager(connManager)
            .disableAutomaticRetries()
            .build();
    }

    private CloseableHttpAsyncClient asyncClient(final boolean validateAfterInactivity) {
        final PoolingAsyncClientConnectionManager connManager = new PoolingAsyncClientConnectionManager();
        connManager.setDefaultConnectionConfig(getConnectionConfig(validateAfterInactivity));
        connManager.setDefaultMaxPerRoute(1);
        connManager.setMaxTotal(1);
        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
            .setConnectionManager(connManager)
            .disableAutomaticRetries()
            .build();
        client.start();
        return client;
    }

    private static ConnectionConfig getConnectionConfig(final boolean validateAfterInactivity) {
        return ConnectionConfig.custom()
            .setTimeToLive(MAX_VALUE)
            .setValidateAfterInactivity(validateAfterInactivity ? ZERO_MILLISECONDS : MAX_VALUE)
            .build();
    }
}

public class TestValidateAfterInactivity {
    @Nested
    class TcpClose extends AbstractTestValidateAfterInactivity {
    }

    @Nested
    class TcpReset extends AbstractTestValidateAfterInactivity {
        @Override
        protected void closeServerEndOfConnection() throws IOException, InterruptedException {
            currentConnection.get().setOption(StandardSocketOptions.SO_LINGER, 0);
            super.closeServerEndOfConnection();
        }
    }
}
