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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.websocket.WebSocketCloseStatus;
import org.apache.hc.core5.websocket.WebSocketConfig;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.apache.hc.core5.websocket.WebSocketException;
import org.apache.hc.core5.websocket.WebSocketExtensionNegotiation;
import org.apache.hc.core5.websocket.WebSocketExtensionRegistry;
import org.apache.hc.core5.websocket.WebSocketExtensions;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketHandshake;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;

final class WebSocketH2ServerExchangeHandler implements AsyncServerExchangeHandler {

    private static final byte[] END_INBOUND = new byte[0];
    private static final ByteBuffer END_OUTBOUND = ByteBuffer.allocate(0);

    /** Bounded in-flight inbound byte budget advertised via HTTP/2 flow control. */
    private static final int INBOUND_WINDOW = 256 * 1024;

    /** Maximum number of buffered outbound frames before the application writer is back-pressured. */
    private static final int OUTBOUND_QUEUE_CAPACITY = 1024;

    /**
     * Default execution strategy (no explicit thread creation in the handler).
     * Note: tasks are typically long-lived (one per WS session). The bootstrap should ideally inject an executor.
     */
    private static final Executor DEFAULT_EXECUTOR =
            Executors.newCachedThreadPool(new DefaultThreadFactory("ws-h2-server", true));

    private final WebSocketHandler handler;
    private final WebSocketConfig config;
    private final WebSocketExtensionRegistry extensionRegistry;
    private final Executor executor;

    private final BlockingQueue<byte[]> inbound = new LinkedBlockingQueue<>();
    private final BlockingQueue<ByteBuffer> outbound = new LinkedBlockingQueue<>(OUTBOUND_QUEUE_CAPACITY);

    private final ReentrantLock outLock = new ReentrantLock();
    private ByteBuffer currentOutbound;

    private volatile boolean responseSent;
    private volatile boolean outboundEnd;
    private volatile boolean shutdown;
    private volatile DataStreamChannel dataChannel;
    private volatile CapacityChannel capacityChannel;
    private final AtomicBoolean initialCreditGranted = new AtomicBoolean(false);

    WebSocketH2ServerExchangeHandler(
            final WebSocketHandler handler,
            final WebSocketConfig config,
            final WebSocketExtensionRegistry extensionRegistry) {
        this(handler, config, extensionRegistry, null);
    }

    WebSocketH2ServerExchangeHandler(
            final WebSocketHandler handler,
            final WebSocketConfig config,
            final WebSocketExtensionRegistry extensionRegistry,
            final Executor executor) {
        this.handler = Args.notNull(handler, "WebSocket handler");
        this.config = config != null ? config : WebSocketConfig.DEFAULT;
        this.extensionRegistry = extensionRegistry != null ? extensionRegistry : WebSocketExtensionRegistry.createDefault();
        this.executor = executor != null ? executor : DEFAULT_EXECUTOR;
        this.responseSent = false;
        this.outboundEnd = false;
        this.shutdown = false;
    }

    @Override
    public void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel,
            final HttpContext context) throws HttpException, IOException {

        if (!Method.CONNECT.isSame(request.getMethod())) {
            responseChannel.sendResponse(new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST), null, context);
            return;
        }

        final String protocol = request.getFirstHeader(WebSocketConstants.PSEUDO_PROTOCOL) != null
                ? request.getFirstHeader(WebSocketConstants.PSEUDO_PROTOCOL).getValue()
                : null;
        if (!"websocket".equalsIgnoreCase(protocol)) {
            responseChannel.sendResponse(new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST), null, context);
            return;
        }

        final WebSocketExtensionNegotiation negotiation = extensionRegistry.negotiate(
                WebSocketExtensions.parse(request.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER)),
                true);

        // The worker thread takes ownership of the negotiated extensions once the task is accepted
        // and releases them in its finally after the stream has been torn down. If the hand-off
        // never happens (a failed sendResponse or a rejected executor) this method releases them,
        // attaching any close failure as a suppressed exception rather than masking the original.
        try {
            final BasicHttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
            final String extensionsHeader = negotiation.formatResponseHeader();
            if (extensionsHeader != null) {
                response.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER, extensionsHeader);
            }

            final List<String> offeredProtocols = WebSocketHandshake.parseSubprotocols(
                    request.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_PROTOCOL_LOWER));
            final String protocolResponse = handler.selectSubprotocol(offeredProtocols);
            if (protocolResponse != null) {
                response.addHeader(WebSocketConstants.SEC_WEBSOCKET_PROTOCOL_LOWER, protocolResponse);
            }

            responseChannel.sendResponse(response, new BasicEntityDetails(-1, null), context);
            responseSent = true;

            final InputStream inputStream = new QueueInputStream(inbound);
            final OutputStream outputStream = new QueueOutputStream();
            final WebSocketSession session = new WebSocketSession(
                    config, inputStream, outputStream, null, null, negotiation.getExtensions());

            executor.execute(() -> {
                try {
                    handler.onOpen(session);
                    new WebSocketServerProcessor(session, handler, config.getMaxMessageSize()).process();
                } catch (final WebSocketProtocolException ex) {
                    handler.onError(session, ex);
                    try {
                        session.close(ex.closeCode, ex.getMessage());
                    } catch (final IOException ignore) {
                        // ignore
                    }
                } catch (final WebSocketException ex) {
                    handler.onError(session, ex);
                    try {
                        session.close(WebSocketCloseStatus.PROTOCOL_ERROR.getCode(), ex.getMessage());
                    } catch (final IOException ignore) {
                        // ignore
                    }
                } catch (final Exception ex) {
                    handler.onError(session, ex);
                    try {
                        session.close(WebSocketCloseStatus.INTERNAL_ERROR.getCode(), "WebSocket error");
                    } catch (final IOException ignore) {
                        // ignore
                    }
                } finally {
                    // Tear the stream down first so a failing extension close cannot leave the
                    // HTTP/2 stream without an END_STREAM; release the extensions last, always.
                    try {
                        shutdown = true;
                        enqueueOutboundQuietly(END_OUTBOUND);
                        inbound.offer(END_INBOUND);

                        final DataStreamChannel channel = dataChannel;
                        if (channel != null) {
                            channel.requestOutput();
                        }
                    } finally {
                        negotiation.close();
                    }
                }
            });
        } catch (final Exception primary) {
            // The task was never handed off to the worker (sendResponse failed or the executor
            // rejected it); release the negotiated extensions here, preserving the original failure.
            try {
                negotiation.close();
            } catch (final RuntimeException closeEx) {
                primary.addSuppressed(closeEx);
            }
            throw primary;
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        this.capacityChannel = capacityChannel;
        // Advertise a bounded window once; further credit is replenished as the worker thread
        // drains buffered bytes, so inbound memory stays bounded even if the worker stalls.
        if (initialCreditGranted.compareAndSet(false, true)) {
            capacityChannel.update(INBOUND_WINDOW);
        }
    }

    private void replenishInbound(final int n) {
        final CapacityChannel channel = capacityChannel;
        if (channel != null && n > 0) {
            try {
                channel.update(n);
            } catch (final IOException ignore) {
                // channel already gone; nothing to replenish
            }
        }
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        if (src == null || !src.hasRemaining() || shutdown) {
            return;
        }
        final byte[] data = new byte[src.remaining()];
        src.get(data);
        inbound.offer(data);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        inbound.offer(END_INBOUND);
    }

    @Override
    public int available() {
        if (!responseSent || outboundEnd) {
            return 0;
        }
        final ByteBuffer next;
        outLock.lock();
        try {
            next = currentOutbound != null ? currentOutbound : outbound.peek();
        } finally {
            outLock.unlock();
        }
        if (next == null) {
            return 0;
        }
        if (next == END_OUTBOUND) {
            // Force produce() so we can emit END_STREAM.
            return 1;
        }
        return next.remaining();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (!responseSent || outboundEnd) {
            return;
        }
        this.dataChannel = channel;

        for (; ; ) {
            final ByteBuffer buf;
            outLock.lock();
            try {
                if (currentOutbound == null) {
                    currentOutbound = outbound.poll();
                }
                buf = currentOutbound;
            } finally {
                outLock.unlock();
            }
            if (buf == null) {
                return;
            }

            if (buf == END_OUTBOUND) {
                outLock.lock();
                try {
                    currentOutbound = null;
                } finally {
                    outLock.unlock();
                }
                outboundEnd = true;
                channel.endStream(null);
                return;
            }

            if (!buf.hasRemaining()) {
                outLock.lock();
                try {
                    currentOutbound = null;
                } finally {
                    outLock.unlock();
                }
                continue;
            }

            final int n = channel.write(buf);
            if (n == 0) {
                channel.requestOutput();
                return;
            }
            if (buf.hasRemaining()) {
                channel.requestOutput();
                return;
            }

            outLock.lock();
            try {
                currentOutbound = null;
            } finally {
                outLock.unlock();
            }
        }
    }

    @Override
    public void failed(final Exception cause) {
        shutdown = true;
        // Clear first so a worker blocked on a full outbound queue is released, then post the
        // sentinels so the worker's blocking reads/writes unwind.
        outbound.clear();
        inbound.clear();
        outbound.offer(END_OUTBOUND);
        inbound.offer(END_INBOUND);

        final DataStreamChannel channel = dataChannel;
        if (channel != null) {
            channel.requestOutput();
        }
    }

    @Override
    public void releaseResources() {
        shutdown = true;
        outbound.clear();
        inbound.clear();
        inbound.offer(END_INBOUND);
        outLock.lock();
        try {
            currentOutbound = null;
        } finally {
            outLock.unlock();
        }
    }

    private void enqueueOutbound(final ByteBuffer buf) throws IOException {
        // Once the exchange has failed or been released nothing drains the queue any more,
        // so a blocking put would wedge the worker thread; fail the write instead.
        if (shutdown) {
            throw new IOException("WebSocket stream already terminated");
        }
        try {
            // Bounded blocking put: applies backpressure to the application writer (worker thread)
            // when the reactor has not yet drained the outbound queue.
            outbound.put(buf);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while queuing outbound data", ex);
        }
    }

    private void enqueueOutboundQuietly(final ByteBuffer buf) {
        try {
            outbound.put(buf);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private final class QueueInputStream extends InputStream {

        private final BlockingQueue<byte[]> queue;
        private byte[] current;
        private int pos;

        QueueInputStream(final BlockingQueue<byte[]> queue) {
            this.queue = queue;
        }

        @Override
        public int read() throws IOException {
            if (current == null || pos >= current.length) {
                // The previous chunk is fully consumed: replenish inbound flow-control credit for
                // its bytes so the peer may send more, keeping buffered memory bounded.
                if (current != null && current != END_INBOUND && current.length > 0) {
                    replenishInbound(current.length);
                }
                try {
                    current = queue.take();
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ex.getMessage(), ex);
                }
                pos = 0;
                if (current == END_INBOUND) {
                    return -1;
                }
            }
            return current[pos++] & 0xFF;
        }
    }

    private final class QueueOutputStream extends OutputStream {

        @Override
        public void write(final int b) throws IOException {
            enqueueOutbound(ByteBuffer.wrap(new byte[]{(byte) b}));
            requestOutput();
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            if (len == 0) {
                return;
            }
            final byte[] copy = new byte[len];
            System.arraycopy(b, off, copy, 0, len);
            enqueueOutbound(ByteBuffer.wrap(copy));
            requestOutput();
        }

        @Override
        public void close() throws IOException {
            enqueueOutbound(END_OUTBOUND);
            requestOutput();
        }

        private void requestOutput() {
            final DataStreamChannel channel = dataChannel;
            if (responseSent && channel != null) {
                channel.requestOutput();
            }
        }
    }
}
