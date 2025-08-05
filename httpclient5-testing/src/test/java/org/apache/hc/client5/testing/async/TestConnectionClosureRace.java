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

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2PseudoResponseHeaders;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.http2.impl.io.FrameInputBuffer;
import org.apache.hc.core5.http2.impl.io.FrameOutputBuffer;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hc.core5.http2.HttpVersionPolicy.FORCE_HTTP_1;
import static org.apache.hc.core5.http2.HttpVersionPolicy.FORCE_HTTP_2;
import static org.apache.hc.core5.util.TimeValue.MAX_VALUE;
import static org.apache.hc.core5.util.TimeValue.NEG_ONE_MILLISECOND;
import static org.apache.hc.core5.util.TimeValue.ZERO_MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test exercises a race condition between client connection reuse and server-initiated connection closure. The
 * test matrix consists of two protocols (HTTP/1.1 and HTTP/2), two connection layers (TCP and TLS 1.2), and two,
 * settings for {@link ConnectionConfig#getValidateAfterInactivity}: -1 ms (never validate) and 0 ms (always validate).
 * <p>
 * The tests work by sending simple ping requests to a test server over a single connection. Requests are batched in
 * various ways and sent at various rates. The test server closes the connection immediately upon sending a response.
 * HTTP/1.1 connections are closed silently (without any {@code Connection: close} header), in order to simulate
 * connection closure caused by idleness or by the network itself. HTTP/2 connections are closed with a {@code GOAWAY}
 * frame. Logically, a TCP {@code FIN} on HTTP/1.1 and a {@code GOAWAY} frame on HTTP/2 both indicate that no additional
 * requests should be sent on the connection, and this test case investigates under what circumstances we can handle
 * that signal before leasing the connection to a new request.
 * <p>
 * The only thing the test actually asserts is that the requests don't time out; the client should never hang due to
 * connection closure. Any additional assertions (such as minimum success rates) would be difficult to make reliably,
 * since server-initiated connection closure is inherently prone to race conditions, even when testing with a single
 * process talking to itself over localhost. However, each test case prints statistics showing the request success rate
 * before and after enabling connection validation. This shows the effectiveness of our inactive connection validation
 * strategy in mitigating races within the client.
 */
@TestMethodOrder(OrderAnnotation.class)
abstract class AbstractTestConnectionClosureRace {
    final AtomicInteger connectionsEstablished = new AtomicInteger(0);
    final String scheme;
    final HttpVersionPolicy httpVersionPolicy;

    volatile ServerSocket serverSocket;
    volatile int port;
    volatile Thread serverThread;

    AbstractTestConnectionClosureRace(final String scheme, final HttpVersionPolicy httpVersionPolicy) {
        this.scheme = scheme;
        this.httpVersionPolicy = httpVersionPolicy;
    }

    @BeforeAll
    static void newline() {
        System.out.println();
    }

    @BeforeEach
    void setup() throws Exception {
        if ("http".equals(scheme)) {
            serverSocket = ServerSocketFactory.getDefault().createServerSocket(0);
        } else {
            final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext();
            final SSLServerSocketFactory sslServerSocketFactory = serverSSLContext.getServerSocketFactory();
            serverSocket = sslServerSocketFactory.createServerSocket(0);
        }
        port = serverSocket.getLocalPort();

        serverThread = new Thread(this::runServer);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (serverSocket != null) {
            serverSocket.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join(1000);
        }
    }

    @Test
    @Timeout(5)
    @Order(1)
    void smokeTest() throws Exception {
        try (final CloseableHttpAsyncClient client = asyncClient(false)) {
            final SimpleHttpResponse simpleHttpResponse = sendPing(client).get();
            assertEquals(200, simpleHttpResponse.getCode());
        }
    }

    @ParameterizedTest(name = "Validation: {0}")
    @ValueSource(booleans = { false, true })
    @Timeout(5)
    @Order(2)
    void testSlowSequentialRequests(final boolean validateConnections) throws Exception {
        try (final CloseableHttpAsyncClient client = asyncClient(validateConnections)) {
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final List<Future<SimpleHttpResponse>> batchFutures = sendRequestBatch(client, 1);
                futures.addAll(batchFutures);
            }

            checkResults(getValidationPrefix(validateConnections) + "Sequential requests (slow)", futures);
        }
    }

    @ParameterizedTest(name = "Validation: {0}")
    @ValueSource(booleans = { false, true })
    @Timeout(10)
    @Order(3)
    void testRapidSequentialRequests(final boolean validateConnections) throws Exception {
        try (final CloseableHttpAsyncClient client = asyncClient(validateConnections)) {
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 2500; i++) {
                final List<Future<SimpleHttpResponse>> batchFutures = sendRequestBatch(client, 1);
                futures.addAll(batchFutures);
            }

            checkResults(getValidationPrefix(validateConnections) + "Sequential requests (rapid)", futures);
        }
    }

    @ParameterizedTest(name = "Validation: {0}")
    @ValueSource(booleans = { false, true })
    @Timeout(5)
    @Order(4)
    void testOneLargeBatchOfRequests(final boolean validateConnections) throws Exception {
        try (final CloseableHttpAsyncClient client = asyncClient(validateConnections)) {
            final List<Future<SimpleHttpResponse>> futures = sendRequestBatch(client, 30);

            checkResults(getValidationPrefix(validateConnections) + "Single large batch", futures);
        }
    }

    @ParameterizedTest(name = "Validation: {0}")
    @ValueSource(booleans = { false, true })
    @Timeout(5)
    @Order(5)
    void testSpacedOutBatchesOfRequests(final boolean validateConnections) throws Exception {
        try (final CloseableHttpAsyncClient client = asyncClient(validateConnections)) {
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final List<Future<SimpleHttpResponse>> batchFutures = sendRequestBatch(client, 3);
                futures.addAll(batchFutures);
            }

            checkResults(getValidationPrefix(validateConnections) + "Multiple small batches", futures);
        }
    }

    private List<Future<SimpleHttpResponse>> sendRequestBatch(
        final CloseableHttpAsyncClient client,
        final int batchSize
    ) {
        final List<Future<SimpleHttpResponse>> futures = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            futures.add(sendPing(client));
        }
        waitForCompletion(futures);
        return futures;
    }

    private static void waitForCompletion(final List<Future<SimpleHttpResponse>> futures) {
        for (final Future<SimpleHttpResponse> future : futures) {
            try {
                future.get();
            } catch (final Throwable ignore) {
            }
        }
    }

    private static String getValidationPrefix(final boolean validateConnections) {
        if (validateConnections) {
            return "Validation enabled:  ";
        } else {
            return "Validation disabled: ";
        }
    }

    private void checkResults(final String name, final List<Future<SimpleHttpResponse>> futures) {
        int ok = 0, error = 0, notExecuted = 0, closed = 0, reset = 0, other = 0;
        for (final Future<SimpleHttpResponse> future : futures) {
            try {
                future.get();
                ok++;
            } catch (final Exception ex) {
                error++;
                if (ex.getCause() instanceof RequestNotExecutedException) {
                    notExecuted++;
                } else if (ex.getCause() instanceof ConnectionClosedException) {
                    closed++;
                } else if (ex.getCause() instanceof SocketException && ex.getCause().getMessage().contains("reset")) {
                    reset++;
                } else {
                    other++;
                }
            }
        }

        if (error > 0) {
            System.out.printf("%s: %s: %,d succeeded; %,d failed (%.2f%% success rate, %.2f%% retriable)%n",
                getClass().getSimpleName().toLowerCase(), name, ok, error,
                (double) ok / (ok + error) * 100d,
                (double) notExecuted / (ok + error) * 100d);
        } else {
            System.out.printf("%s: %s: %,d succeeded; %,d failed (%.2f%% success rate)%n",
                getClass().getSimpleName().toLowerCase(), name, ok, error, (((double) ok) / (ok + error)) * 100d);
        }
        if (false) {
            System.out.printf("  %,d not executed, %,d closed, %,d reset, %,d other%n", notExecuted, closed, reset,
                other);
        }
    }

    private Future<SimpleHttpResponse> sendPing(final CloseableHttpAsyncClient client) {
        final HttpHost target = new HttpHost(scheme, "localhost", port);
        final SimpleHttpRequest request = SimpleRequestBuilder.get().setHttpHost(target).setPath("/ping").build();

        return client.execute(request, null);
    }

    private void runServer() {
        try {
            while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                final Socket socket = serverSocket.accept();
                connectionsEstablished.incrementAndGet();
                handleConnection(socket);
            }
        } catch (final IOException e) {
            if (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                System.err.println("Server error: " + e.getClass() + e.getMessage());
            }
        }
    }

    private void handleConnection(final Socket socket) throws IOException {
        try {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).startHandshake();
            }

            final InputStream inputStream = socket.getInputStream();
            final OutputStream outputStream = socket.getOutputStream();

            if (httpVersionPolicy == FORCE_HTTP_2) {
                sendHttp2Response(inputStream, outputStream);
            } else {
                sendHttp1Response(inputStream, outputStream);
            }
            outputStream.flush();
            socket.close();
        } catch (final SocketException ignore) {
            // Connection closure was initiated on the server's end
        } catch (final IOException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Connection reset")) {
                System.err.println("Server saw connection closed by client");
                return;
            }
            throw ex;
        }
    }

    private static void sendHttp1Response(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[4096];
        final int bytesRead = inputStream.read(buffer);
        if (bytesRead <= 0) {
            return;
        }

        final String response = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/plain\r\n" +
            "Content-Length: 2\r\n" +
            "\r\n" +
            "OK";

        final byte[] responseBytes = response.getBytes(UTF_8);
        outputStream.write(responseBytes);
    }

    private static void sendHttp2Response(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        final FrameOutputBuffer out = new FrameOutputBuffer(8192);
        final FrameInputBuffer frameInputBuffer = new FrameInputBuffer(8192);
        final DefaultFrameFactory ff = new DefaultFrameFactory();

        for (int i = 0; i < 24; i++) inputStream.read(); // Read magic
        while (!frameInputBuffer.read(inputStream).isType(FrameType.SETTINGS)) {
            // Do nothing
        }
        out.write(ff.createSettingsAck(), outputStream);

        out.write(ff.createSettings(
            new H2Setting(H2Param.HEADER_TABLE_SIZE, 8192),
            new H2Setting(H2Param.ENABLE_PUSH, 0),
            new H2Setting(H2Param.MAX_CONCURRENT_STREAMS, 1),
            new H2Setting(H2Param.INITIAL_WINDOW_SIZE, 65535),
            new H2Setting(H2Param.MAX_FRAME_SIZE, 65536),
            new H2Setting(H2Param.MAX_HEADER_LIST_SIZE, 16 * 1048576)
        ), outputStream);

        while (!frameInputBuffer.read(inputStream).isType(FrameType.HEADERS)) {
            // Do nothing
        }

        final HPackEncoder hPackEncoder = new HPackEncoder(8192, UTF_8);
        final ByteArrayBuffer headerBuffer = new ByteArrayBuffer(8192);
        hPackEncoder.encodeHeaders(headerBuffer, Arrays.asList(
            new BasicHeader(H2PseudoResponseHeaders.STATUS, Integer.toString(200), false),
            new BasicHeader("content-type", "text/plain")
        ), true);

        out.write(ff.createHeaders(1, ByteBuffer.wrap(headerBuffer.toByteArray()), true, false), outputStream);
        out.write(ff.createData(1, ByteBuffer.wrap("OK".getBytes(UTF_8)), true), outputStream);
        out.write(ff.createGoAway(1, H2Error.NO_ERROR, null), outputStream);
    }

    private CloseableHttpAsyncClient asyncClient(final boolean validateConnections) throws Exception {
        final PoolingAsyncClientConnectionManager connManager = PoolingAsyncClientConnectionManagerBuilder.create()
            .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
            .setDefaultTlsConfig(TlsConfig.custom()
                .setSupportedProtocols(TLS.V_1_2)
                .setVersionPolicy(httpVersionPolicy)
                .build())
            .build();
        connManager.setDefaultConnectionConfig(getConnectionConfig(validateConnections));
        connManager.setDefaultMaxPerRoute(1);
        connManager.setMaxTotal(1);
        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
            .setIOReactorConfig(IOReactorConfig.custom()
                .setSelectInterval(TimeValue.ofMilliseconds(1000))
                .setIoThreadCount(1)
                .build())
            .setConnectionManager(connManager)
            .disableAutomaticRetries()
            .build();
        client.start();
        return client;
    }

    private static ConnectionConfig getConnectionConfig(final boolean validateConnections) {
        return ConnectionConfig.custom()
            .setTimeToLive(MAX_VALUE)
            .setValidateAfterInactivity(validateConnections ? ZERO_MILLISECONDS : NEG_ONE_MILLISECOND)
            .build();
    }
}

@Tag("slow")
public class TestConnectionClosureRace {
    @Nested
    class Http extends AbstractTestConnectionClosureRace {
        public Http() {
            super("http", FORCE_HTTP_1);
        }
    }

    @Nested
    class Https extends AbstractTestConnectionClosureRace {
        public Https() {
            super("https", FORCE_HTTP_1);
        }
    }

    @Nested
    class H2c extends AbstractTestConnectionClosureRace {
        public H2c() {
            super("http", FORCE_HTTP_2);
        }
    }

    @Nested
    class H2 extends AbstractTestConnectionClosureRace {
        public H2() {
            super("https", FORCE_HTTP_2);
        }
    }
}
