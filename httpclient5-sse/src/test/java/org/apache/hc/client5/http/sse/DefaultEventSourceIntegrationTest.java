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
package org.apache.hc.client5.http.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sse.impl.SseParser;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.H2StreamTimeoutException;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

final class DefaultEventSourceIntegrationTest {

    @Test
    void openStreamReceivesEventAndCloses() throws Exception {
        final CapturingClient fake = new CapturingClient();
        final SseExecutor exec = SseExecutor.newInstance(fake);

        final CountDownLatch opened = new CountDownLatch(1);
        final CountDownLatch got = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);

        final EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onOpen() {
                opened.countDown();
            }

            @Override
            public void onEvent(final String id, final String type, final String data) {
                if ("1".equals(id) && "ping".equals(type) && "hello".equals(data)) {
                    got.countDown();
                }
            }

            @Override
            public void onClosed() {
                closed.countDown();
            }
        };

        final EventSource es = exec.open(
                new URI("http://example.org/sse"),
                Collections.emptyMap(),
                listener,
                EventSourceConfig.DEFAULT,
                SseParser.BYTE,
                null,
                null);

        es.start();

        final AsyncResponseConsumer<Void> c = fake.lastConsumer;
        assertNotNull(c, "consumer captured");

        final HttpContext context = HttpClientContext.create();
        c.consumeResponse(
                new BasicHttpResponse(HttpStatus.SC_OK, "OK"),
                new TestEntityDetails("text/event-stream"),
                context,
                new FutureCallback<Void>() {
                    @Override
                    public void completed(final Void result) {
                    }

                    @Override
                    public void failed(final Exception ex) {
                    }

                    @Override
                    public void cancelled() {
                    }
                });

        c.consume(ByteBuffer.wrap("id: 1\nevent: ping\n".getBytes(StandardCharsets.UTF_8)));
        c.consume(ByteBuffer.wrap("data: hello\n\n".getBytes(StandardCharsets.UTF_8)));
        c.streamEnd(null);

        assertTrue(opened.await(1, TimeUnit.SECONDS), "opened");
        assertTrue(got.await(1, TimeUnit.SECONDS), "event received");

        es.cancel();
        assertTrue(closed.await(1, TimeUnit.SECONDS), "closed");

        exec.close();
    }

    @Test
    void socketTimeoutIsReportedAsFailureAndReconnects() throws Exception {
        final CapturingClient fake = new CapturingClient();
        final SseExecutor exec = SseExecutor.newInstance(fake);

        final CountDownLatch failed = new CountDownLatch(1);
        final AtomicBoolean willReconnect = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();

        final EventSource es = exec.open(
                new URI("http://example.org/sse"),
                new EventSourceListener() {
                    @Override
                    public void onEvent(final String id, final String type, final String data) {
                    }

                    @Override
                    public void onFailure(final Throwable t, final boolean reconnect) {
                        if (t instanceof SocketTimeoutException) {
                            willReconnect.set(reconnect);
                            failed.countDown();
                        }
                    }

                    @Override
                    public void onClosed() {
                        closed.set(true);
                    }
                });
        es.start();

        assertNotNull(fake.lastCallback, "callback captured");
        fake.lastCallback.failed(new SocketTimeoutException("Socket timeout"));

        assertTrue(failed.await(1, TimeUnit.SECONDS), "socket timeout reported through onFailure");
        assertTrue(willReconnect.get(), "socket timeout schedules a reconnect");
        assertFalse(closed.get(), "socket timeout is not a clean close");

        es.cancel();
        exec.close();
    }

    @Test
    void interruptedIoIsStillBenign() throws Exception {
        final CapturingClient fake = new CapturingClient();
        final SseExecutor exec = SseExecutor.newInstance(fake);

        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicBoolean failure = new AtomicBoolean();

        final EventSource es = exec.open(
                new URI("http://example.org/sse"),
                new EventSourceListener() {
                    @Override
                    public void onEvent(final String id, final String type, final String data) {
                    }

                    @Override
                    public void onFailure(final Throwable t, final boolean reconnect) {
                        failure.set(true);
                    }

                    @Override
                    public void onClosed() {
                        closed.countDown();
                    }
                });
        es.start();

        assertNotNull(fake.lastCallback, "callback captured");
        fake.lastCallback.failed(new InterruptedIOException("interrupted"));

        assertTrue(closed.await(1, TimeUnit.SECONDS), "interrupted I/O closes the stream");
        assertFalse(failure.get(), "interrupted I/O is not reported as a failure");

        exec.close();
    }

    @Test
    void connectTimeoutIsReportedAsFailureAndReconnects() throws Exception {
        assertTimeoutReportedAndReconnected(new ConnectTimeoutException("Connect timeout"));
    }

    @Test
    void h2StreamTimeoutIsReportedAsFailureAndReconnects() throws Exception {
        assertTimeoutReportedAndReconnected(
                new H2StreamTimeoutException("Stream timeout", 1, Timeout.ofSeconds(1)));
    }

    @Test
    void socketTimeoutIsFollowedBySuccessfulReconnect() throws Exception {
        assertTimeoutReportedAndReconnected(new SocketTimeoutException("Socket timeout"));
    }

    private static void assertTimeoutReportedAndReconnected(final Exception timeout) throws Exception {
        final CapturingClient fake = new CapturingClient();
        final SseExecutor exec = SseExecutor.newInstance(fake);
        final RecordingListener listener = new RecordingListener();

        final EventSource es = exec.open(
                new URI("http://example.org/sse"),
                Collections.emptyMap(),
                listener,
                EventSourceConfig.builder().backoff((attempt, previousDelayMs, hint) -> 10L).build(),
                SseParser.BYTE,
                null,
                null);
        es.start();

        assertNotNull(fake.lastCallback, "callback captured");
        fake.lastCallback.failed(timeout);

        assertTrue(awaitExecutions(fake, 2), "reconnect issued after the configured backoff");
        assertEquals(1, listener.failures.size(), "onFailure exactly once");
        assertSame(timeout, listener.failures.get(0));
        assertTrue(listener.reconnects.get(0), "failure announces a reconnect");
        assertEquals(0, listener.closed.get(), "no onClosed while reconnecting");

        // the reconnected stream ends the regular way
        fake.lastCallback.completed(null);
        assertTrue(awaitExecutions(fake, 3), "reconnect after a regular stream end");
        assertEquals(1, listener.failures.size(), "no failure for a regular stream end");

        es.cancel();
        assertTrue(listener.closedLatch.await(1, TimeUnit.SECONDS), "closed after cancel");
        assertEquals(1, listener.closed.get(), "onClosed exactly once");
        exec.close();
    }

    @Test
    void explicitCancelNeverReconnects() throws Exception {
        final CapturingClient fake = new CapturingClient();
        final SseExecutor exec = SseExecutor.newInstance(fake);
        final RecordingListener listener = new RecordingListener();

        final EventSource es = exec.open(
                new URI("http://example.org/sse"),
                Collections.emptyMap(),
                listener,
                EventSourceConfig.builder().backoff((attempt, previousDelayMs, hint) -> 10L).build(),
                SseParser.BYTE,
                null,
                null);
        es.start();
        es.cancel();

        // the transport reports the aborted exchange as a timeout
        fake.lastCallback.failed(new SocketTimeoutException("Socket timeout"));

        assertTrue(listener.closedLatch.await(1, TimeUnit.SECONDS), "closed after cancel");
        Thread.sleep(200);
        assertEquals(1, fake.executions.get(), "no reconnect after cancel");
        assertTrue(listener.failures.isEmpty(), "no onFailure after cancel");
        assertEquals(1, listener.closed.get(), "onClosed exactly once");
        exec.close();
    }

    @Test
    void h2StreamTimeoutFromRealServerIsReportedAndReconnects() throws Exception {
        final AtomicInteger streams = new AtomicInteger();
        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setCanonicalHostName("127.0.0.1")
                .register("*", () -> new StreamingHandler(streams))
                .create();
        server.start();
        try (CloseableHttpAsyncClient client = HttpAsyncClients.customHttp2()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(500))
                        .build())
                .build()) {
            client.start();
            final int port = ((InetSocketAddress) server.listen(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTP).get().getAddress()).getPort();

            final SseExecutor exec = SseExecutor.newInstance(client);
            final RecordingListener listener = new RecordingListener();
            final CountDownLatch event = new CountDownLatch(1);
            final EventSource es = exec.open(
                    new URI("http://127.0.0.1:" + port + "/sse"),
                    Collections.emptyMap(),
                    new EventSourceListener() {
                        @Override
                        public void onEvent(final String id, final String type, final String data) {
                            if ("back".equals(data)) {
                                event.countDown();
                            }
                        }

                        @Override
                        public void onFailure(final Throwable t, final boolean reconnect) {
                            listener.onFailure(t, reconnect);
                        }

                        @Override
                        public void onClosed() {
                            listener.onClosed();
                        }
                    },
                    EventSourceConfig.builder().backoff((attempt, previousDelayMs, hint) -> 10L).build(),
                    SseParser.BYTE,
                    null,
                    null);
            es.start();

            // Stream timeouts are checked on connection I/O, at most once a second. A request
            // on the same connection wakes the multiplexer up; its own outcome is irrelevant.
            assertTrue(awaitCount(streams, 1), "event stream opened");
            Thread.sleep(1500);
            client.execute(SimpleRequestBuilder.get("http://127.0.0.1:" + port + "/ping").build(), null);

            assertTrue(event.await(10, TimeUnit.SECONDS), "event received after the reconnect");
            assertFalse(listener.failures.isEmpty(), "stream timeout reported through onFailure");
            assertTrue(listener.failures.get(0) instanceof H2StreamTimeoutException,
                    "H2 stream timeout is not mapped to a different exception: " + listener.failures.get(0));
            assertTrue(listener.reconnects.get(0), "stream timeout schedules a reconnect");
            assertEquals(0, listener.closed.get(), "stream timeout is not a clean close");
            assertEquals(1, listener.failures.size(), "onFailure exactly once");

            es.cancel();
            assertTrue(listener.closedLatch.await(2, TimeUnit.SECONDS), "closed after cancel");
            exec.close();
        } finally {
            server.close(CloseMode.IMMEDIATE);
        }
    }

    private static boolean awaitCount(final AtomicInteger counter, final int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (counter.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return counter.get() >= expected;
    }

    private static boolean awaitExecutions(final CapturingClient fake, final int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (fake.executions.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return fake.executions.get() >= expected;
    }

    static final class RecordingListener implements EventSourceListener {
        final List<Throwable> failures = new CopyOnWriteArrayList<>();
        final List<Boolean> reconnects = new CopyOnWriteArrayList<>();
        final AtomicInteger closed = new AtomicInteger();
        final CountDownLatch closedLatch = new CountDownLatch(1);

        @Override
        public void onEvent(final String id, final String type, final String data) {
        }

        @Override
        public void onFailure(final Throwable t, final boolean reconnect) {
            failures.add(t);
            reconnects.add(reconnect);
        }

        @Override
        public void onClosed() {
            closed.incrementAndGet();
            closedLatch.countDown();
        }
    }

    /**
     * Answers {@code /ping} at once. Any other request opens an event stream that stays
     * silent, except that the second stream carries one event.
     */
    static final class StreamingHandler implements AsyncServerExchangeHandler {
        private final AtomicInteger streams;
        private volatile ByteBuffer pending;

        StreamingHandler(final AtomicInteger streams) {
            this.streams = streams;
        }

        @Override
        public void handleRequest(final HttpRequest request, final EntityDetails entityDetails,
                                  final ResponseChannel responseChannel, final HttpContext context)
                throws HttpException, IOException {
            final BasicHttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
            if (request.getPath().endsWith("/ping")) {
                responseChannel.sendResponse(response, null, context);
                return;
            }
            final String event = streams.incrementAndGet() > 1 ? "data: back\n\n" : "";
            pending = ByteBuffer.wrap(event.getBytes(StandardCharsets.UTF_8));
            responseChannel.sendResponse(response,
                    new BasicEntityDetails(-1, ContentType.create("text/event-stream")), context);
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        }

        @Override
        public int available() {
            final ByteBuffer b = pending;
            return b != null ? b.remaining() : 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            final ByteBuffer b = pending;
            if (b != null && b.hasRemaining()) {
                channel.write(b);
            }
        }

        @Override
        public void failed(final Exception cause) {
        }

        @Override
        public void releaseResources() {
        }
    }

    // ---- fake async client that captures the consumer & callback via doExecute() ----
    static final class CapturingClient extends CloseableHttpAsyncClient {
        volatile AsyncResponseConsumer<Void> lastConsumer;
        volatile FutureCallback<Void> lastCallback;
        volatile boolean closed;
        final AtomicInteger executions = new AtomicInteger();

        @Override
        public void start() { /* no-op */ }

        @Override
        public IOReactorStatus getStatus() {
            return closed ? IOReactorStatus.SHUT_DOWN : IOReactorStatus.ACTIVE;
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) throws InterruptedException { /* no-op */ }

        @Override
        public void initiateShutdown() { /* no-op */ }

        @Override
        public void close(final CloseMode closeMode) {
            closed = true;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        protected <T> Future<T> doExecute(
                final HttpHost target,
                final AsyncRequestProducer requestProducer,
                final AsyncResponseConsumer<T> responseConsumer,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final HttpContext context,
                final FutureCallback<T> callback) {

            @SuppressWarnings("unchecked") final AsyncResponseConsumer<Void> c = (AsyncResponseConsumer<Void>) responseConsumer;
            this.executions.incrementAndGet();
            this.lastConsumer = c;

            @SuppressWarnings("unchecked") final FutureCallback<Void> cb = (FutureCallback<Void>) callback;
            this.lastCallback = cb;

            return new CompletableFuture<>(); // never completed; fine for this test
        }

        @Override
        @Deprecated
        public void register(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
            // deprecated; not used
        }
    }

    // Minimal EntityDetails stub
    static final class TestEntityDetails implements EntityDetails {
        private final String ct;

        TestEntityDetails(final String ct) {
            this.ct = ct;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public String getContentType() {
            return ct;
        }

        @Override
        public String getContentEncoding() {
            return null;
        }

        @Override
        public boolean isChunked() {
            return true;
        }

        @Override
        public Set<String> getTrailerNames() {
            return Collections.<String>emptySet();
        }
    }
}
