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
import org.apache.hc.core5.websocket.WebSocketExtensionNegotiation;
import org.apache.hc.core5.websocket.WebSocketExtensionRegistry;
import org.apache.hc.core5.websocket.WebSocketExtensions;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketHandshake;
import org.apache.hc.core5.websocket.WebSocketSession;

final class WebSocketH2ServerExchangeHandler implements AsyncServerExchangeHandler {

    private static final byte[] END_INBOUND = new byte[0];
    private static final ByteBuffer END_OUTBOUND = ByteBuffer.allocate(0);

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
    private final BlockingQueue<ByteBuffer> outbound = new LinkedBlockingQueue<>();

    private final ReentrantLock outLock = new ReentrantLock();
    private ByteBuffer currentOutbound;

    private volatile boolean responseSent;
    private volatile boolean outboundEnd;
    private volatile boolean shutdown;
    private volatile DataStreamChannel dataChannel;

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
        final OutputStream outputStream = new QueueOutputStream(outbound);
        final WebSocketSession session = new WebSocketSession(
                config, inputStream, outputStream, null, null, negotiation.getExtensions());

        executor.execute(() -> {
            try {
                handler.onOpen(session);
                new WebSocketServerProcessor(session, handler, config.getMaxMessageSize()).process();
            } catch (final Exception ex) {
                handler.onError(session, ex);
                try {
                    session.close(WebSocketCloseStatus.INTERNAL_ERROR.getCode(), "WebSocket error");
                } catch (final IOException ignore) {
                    // ignore
                }
            } finally {
                shutdown = true;
                outbound.offer(END_OUTBOUND);
                inbound.offer(END_INBOUND);

                final DataStreamChannel channel = dataChannel;
                if (channel != null) {
                    channel.requestOutput();
                }
            }
        });
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE);
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
        outLock.lock();
        try {
            currentOutbound = null;
        } finally {
            outLock.unlock();
        }
    }

    private static final class QueueInputStream extends InputStream {

        private final BlockingQueue<byte[]> queue;
        private byte[] current;
        private int pos;

        QueueInputStream(final BlockingQueue<byte[]> queue) {
            this.queue = queue;
        }

        @Override
        public int read() throws IOException {
            if (current == null || pos >= current.length) {
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

        private final BlockingQueue<ByteBuffer> queue;

        QueueOutputStream(final BlockingQueue<ByteBuffer> queue) {
            this.queue = queue;
        }

        @Override
        public void write(final int b) throws IOException {
            queue.offer(ByteBuffer.wrap(new byte[]{(byte) b}));
            requestOutput();
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            if (len == 0) {
                return;
            }
            final byte[] copy = new byte[len];
            System.arraycopy(b, off, copy, 0, len);
            queue.offer(ByteBuffer.wrap(copy));
            requestOutput();
        }

        @Override
        public void close() {
            queue.offer(END_OUTBOUND);
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
