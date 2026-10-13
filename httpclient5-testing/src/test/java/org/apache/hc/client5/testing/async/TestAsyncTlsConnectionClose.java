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
package org.apache.hc.client5.testing.async;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestAsyncTlsConnectionClose {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    enum CloseInitiator {
        // The origin sends Connection: close; HttpClient initiates TLS shutdown.
        CLIENT,
        // The origin sends close_notify after a reusable HTTP response.
        SERVER;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static Stream<Arguments> tlsCloseScenarios() {
        return Stream.of(TLS.V_1_2, TLS.V_1_3)
                .flatMap(tlsVersion -> Stream.of(CloseInitiator.CLIENT, CloseInitiator.SERVER)
                        .map(closeInitiator -> Arguments.of(tlsVersion, closeInitiator)));
    }

    @ParameterizedTest(name = "{0}, {1}-initiated TLS close")
    @MethodSource("tlsCloseScenarios")
    void testConnectionCloseCompletesTlsShutdownWithoutReset(
            final TLS tlsVersion,
            final CloseInitiator closeInitiator) throws Exception {
        try (TlsConnectionCloseServer server = new TlsConnectionCloseServer(tlsVersion, closeInitiator);
                PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                        .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                        .setDefaultTlsConfig(TlsConfig.custom()
                                .setSupportedProtocols(tlsVersion)
                                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                                .build())
                        .build();
                CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                        .setConnectionManager(connectionManager)
                        .build()) {
            server.start();
            client.start();

            final HttpHost target = new HttpHost(
                    "https", InetAddress.getByName("127.0.0.1"), "localhost", server.getPort());
            final Future<SimpleHttpResponse> responseFuture = client.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/")
                            .build(),
                    null);

            Assertions.assertEquals(200, responseFuture.get(
                    TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()).getCode());
            Assertions.assertTrue(server.awaitCleanClose(),
                    () -> "the client reset the TCP connection during the "
                            + tlsVersion.id + " " + closeInitiator
                            + "-initiated TLS close_notify exchange");
        }
    }

    private static final class TlsConnectionCloseServer implements Closeable {

        private final TLS tlsVersion;
        private final CloseInitiator closeInitiator;
        private final ServerSocket serverSocket;
        private final CompletableFuture<Boolean> cleanCloseFuture;
        private Thread worker;

        TlsConnectionCloseServer(final TLS tlsVersion, final CloseInitiator closeInitiator) throws IOException {
            this.tlsVersion = tlsVersion;
            this.closeInitiator = closeInitiator;
            this.serverSocket = new ServerSocket();
            this.serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            this.cleanCloseFuture = new CompletableFuture<>();
        }

        void start() {
            worker = new Thread(this::run, "tls-connection-close-server");
            worker.setDaemon(true);
            worker.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        boolean awaitCleanClose() throws Exception {
            return cleanCloseFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        }

        private void run() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
                final SSLContext sslContext = SSLTestContexts.createServerSSLContext();
                try (SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(
                        socket, "localhost", socket.getPort(), false)) {
                    sslSocket.setUseClientMode(false);
                    sslSocket.setEnabledProtocols(new String[] { tlsVersion.id });
                    sslSocket.startHandshake();
                    if (!tlsVersion.id.equals(sslSocket.getSession().getProtocol())) {
                        throw new IOException("Unexpected negotiated TLS protocol "
                                + sslSocket.getSession().getProtocol());
                    }

                    readRequestHead(sslSocket.getInputStream());
                    final OutputStream outputStream = sslSocket.getOutputStream();
                    outputStream.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Length: 0\r\n"
                            + (closeInitiator == CloseInitiator.CLIENT ? "Connection: close\r\n" : "")
                            + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    outputStream.flush();

                    if (closeInitiator == CloseInitiator.CLIENT) {
                        // Connection: close causes the client to initiate TLS
                        // shutdown. Wait for its close_notify before returning ours.
                        if (sslSocket.getInputStream().read() != -1) {
                            throw new IOException("Unexpected application data after the request");
                        }
                    }
                    // Otherwise closing this SSLSocket sends the server's
                    // close_notify first.
                }

                // The layered SSLSocket does not own the TCP socket. Check the
                // transport after TLS shutdown to distinguish FIN from RST.
                cleanCloseFuture.complete(socket.getInputStream().read() == -1);
            } catch (final IOException ex) {
                if (isConnectionReset(ex)) {
                    cleanCloseFuture.complete(false);
                } else {
                    cleanCloseFuture.completeExceptionally(ex);
                }
            } catch (final Exception ex) {
                cleanCloseFuture.completeExceptionally(ex);
            }
        }

        private static boolean isConnectionReset(final Throwable cause) {
            for (Throwable current = cause; current != null; current = current.getCause()) {
                if (current.getMessage() != null
                        && current.getMessage().toLowerCase(Locale.ROOT).contains("connection reset")) {
                    return true;
                }
            }
            return false;
        }

        private static void readRequestHead(final InputStream inputStream) throws IOException {
            int matched = 0;
            while (matched < 4) {
                final int b = inputStream.read();
                if (b == -1) {
                    throw new IOException("Connection closed before the request head was complete");
                }
                if (b == "\r\n\r\n".charAt(matched)) {
                    matched++;
                } else {
                    matched = b == '\r' ? 1 : 0;
                }
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            if (worker != null) {
                try {
                    worker.join(TimeUnit.SECONDS.toMillis(1));
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
