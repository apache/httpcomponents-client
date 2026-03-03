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
package org.apache.hc.client5.http.websocket.client.impl.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.client5.http.websocket.transport.OutboundFlowSupport;
import org.apache.hc.client5.http.websocket.transport.WebSocketFrameDecoder;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.apache.hc.core5.websocket.extension.WebSocketExtensionChain;
import org.apache.hc.core5.websocket.frame.FrameHeaderBits;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.apache.hc.core5.websocket.frame.WebSocketFrameWriter;
import org.apache.hc.core5.websocket.message.CloseCodec;

/**
 * RFC 8441 (HTTP/2 Extended CONNECT) placeholder.
 */
@Internal
public final class Http2ExtendedConnectProtocol implements WebSocketProtocolStrategy {

    private static final ScheduledExecutorService CLOSE_TIMER =
            Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("ws-h2-close", true));

    public static final class H2NotAvailable extends RuntimeException {
        public H2NotAvailable(final String msg) {
            super(msg);
        }
    }

    private final H2MultiplexingRequester requester;

    public Http2ExtendedConnectProtocol(final H2MultiplexingRequester requester) {
        this.requester = requester;
    }

    @Override
    public CompletableFuture<WebSocket> connect(
            final URI uri,
            final WebSocketListener listener,
            final WebSocketClientConfig cfg,
            final HttpContext context) {

        final CompletableFuture<WebSocket> f = new CompletableFuture<>();

        if (requester == null) {
            f.completeExceptionally(new H2NotAvailable("HTTP/2 requester not configured"));
            return f;
        }
        Args.notNull(uri, "uri");
        Args.notNull(listener, "listener");
        Args.notNull(cfg, "cfg");

        final boolean secure = "wss".equalsIgnoreCase(uri.getScheme());
        if (!secure && !"ws".equalsIgnoreCase(uri.getScheme())) {
            f.completeExceptionally(new IllegalArgumentException("Scheme must be ws or wss"));
            return f;
        }

        final String scheme = secure ? URIScheme.HTTPS.id : URIScheme.HTTP.id;
        final int port = uri.getPort() > 0 ? uri.getPort() : (secure ? 443 : 80);

        final String host = Args.notBlank(uri.getHost(), "host");
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        final String fullPath = uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;

        final HttpHost target = new HttpHost(scheme, host, port);

        final BasicHttpRequest req = new BasicHttpRequest(Method.CONNECT.name(), target, fullPath);
        req.addHeader(WebSocketConstants.PSEUDO_PROTOCOL, "websocket");
        req.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION_LOWER, "13");

        if (cfg.getSubprotocols() != null && !cfg.getSubprotocols().isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (final String p : cfg.getSubprotocols()) {
                if (p != null && !p.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(p);
                }
            }
            if (sb.length() > 0) {
                req.addHeader(WebSocketConstants.SEC_WEBSOCKET_PROTOCOL_LOWER, sb.toString());
            }
        }

        if (cfg.isPerMessageDeflateEnabled()) {
            final StringBuilder ext = new StringBuilder("permessage-deflate");
            if (cfg.isOfferServerNoContextTakeover()) {
                ext.append("; server_no_context_takeover");
            }
            if (cfg.isOfferClientNoContextTakeover()) {
                ext.append("; client_no_context_takeover");
            }
            // Your implementation supports only 15 safely.
            if (cfg.getOfferClientMaxWindowBits() != null && cfg.getOfferClientMaxWindowBits() == 15) {
                ext.append("; client_max_window_bits=15");
            }
            if (cfg.getOfferServerMaxWindowBits() != null) {
                ext.append("; server_max_window_bits=").append(cfg.getOfferServerMaxWindowBits());
            }
            req.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER, ext.toString());
        }

        final Timeout timeout = cfg.getConnectTimeout() != null ? cfg.getConnectTimeout() : Timeout.ofSeconds(10);
        requester.execute(target, new H2WebSocketExchangeHandler(req, listener, cfg, f), null, timeout, context);
        return f;
    }

    private static final class H2WebSocketExchangeHandler implements AsyncClientExchangeHandler {

        private final BasicHttpRequest request;
        private final WebSocketListener listener;
        private final WebSocketClientConfig cfg;
        private final CompletableFuture<WebSocket> future;

        private final H2WebSocket webSocket;
        private final WebSocketFrameWriter writer;
        private WebSocketFrameDecoder decoder;

        private ByteBuffer inbuf = ByteBuffer.allocate(8192);
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicBoolean outputPrimed = new AtomicBoolean(false);

        private ExtensionChain.EncodeChain encChain;
        private ExtensionChain.DecodeChain decChain;

        private int assemblingOpcode = -1;
        private boolean assemblingCompressed;
        private ByteArrayOutputStream assemblingBytes;
        private final AtomicLong assemblingBytesSize = new AtomicLong();

        private volatile DataStreamChannel dataChannel;

        H2WebSocketExchangeHandler(
                final BasicHttpRequest request,
                final WebSocketListener listener,
                final WebSocketClientConfig cfg,
                final CompletableFuture<WebSocket> future) {
            this.request = request;
            this.listener = listener;
            this.cfg = cfg;
            this.future = future;
            this.writer = new WebSocketFrameWriter();
            this.webSocket = new H2WebSocket();
        }

        @Override
        public void produceRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
            channel.sendRequest(request, new BasicEntityDetails(-1, null), context);
        }

        @Override
        public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context)
                throws HttpException, IOException {

            if (response.getCode() != HttpStatus.SC_OK) {
                failFuture(new IllegalStateException("Unexpected status: " + response.getCode()));
                return;
            }

            try {
                final String selectedProto = headerValue(response, WebSocketConstants.SEC_WEBSOCKET_PROTOCOL_LOWER);
                if (selectedProto != null && !selectedProto.isEmpty()) {
                    final List<String> offered = cfg.getSubprotocols();
                    if (offered == null || !offered.contains(selectedProto)) {
                        throw new ProtocolException("Server selected unsupported subprotocol: " + selectedProto);
                    }
                }

                final ExtensionChain chain = Http1UpgradeProtocol.buildExtensionChain(
                        cfg, headerValue(response, WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS_LOWER));
                this.encChain = chain.isEmpty() ? null : chain.newEncodeChain();
                this.decChain = chain.isEmpty() ? null : chain.newDecodeChain();
                this.decoder = new WebSocketFrameDecoder(cfg.getMaxFrameSize(), chain.isEmpty(), false);

                if (!future.isDone()) {
                    future.complete(webSocket);
                }
                listener.onOpen(webSocket);
            } catch (final Exception ex) {
                failFuture(ex);
            }
        }

        @Override
        public void consumeInformation(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            if (!open.get() || decoder == null) {
                return;
            }
            appendToInbuf(src);
            inbuf.flip();
            for (; ; ) {
                final boolean has;
                try {
                    has = decoder.decode(inbuf);
                } catch (final RuntimeException ex) {
                    listener.onError(ex);
                    failFuture(ex);
                    open.set(false);
                    return;
                }
                if (!has) {
                    break;
                }
                handleFrame();
                if (!open.get()) {
                    break;
                }
            }
            inbuf.compact();
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            open.set(false);
            if (!future.isDone()) {
                failFuture(new ProtocolException("Stream ended before handshake completed"));
            } else if (!webSocket.isCloseReceived()) {
                listener.onClose(1006, "");
            }
        }

        @Override
        public int available() {
            if (dataChannel == null && outputPrimed.compareAndSet(false, true)) {
                return 1;
            }
            return webSocket.endStreamPending() ? 1 : webSocket.available();
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            this.dataChannel = channel;
            webSocket.produce(channel);
        }

        @Override
        public void failed(final Exception cause) {
            listener.onError(cause);
            failFuture(cause);
            open.set(false);
        }

        @Override
        public void releaseResources() {
            if (!future.isDone()) {
                failFuture(new ProtocolException("WebSocket exchange released"));
            }
            open.set(false);
        }

        @Override
        public void cancel() {
            if (!future.isDone()) {
                failFuture(new ProtocolException("WebSocket exchange cancelled"));
            }
            open.set(false);
        }

        private void handleFrame() {
            final int op = decoder.opcode();
            final boolean fin = decoder.fin();
            final boolean r1 = decoder.rsv1();
            final boolean r2 = decoder.rsv2();
            final boolean r3 = decoder.rsv3();
            final ByteBuffer payload = decoder.payload();

            if (r2 || r3) {
                final WebSocketProtocolException ex = new WebSocketProtocolException(1002, "RSV2/RSV3 not supported");
                listener.onError(ex);
                webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                failFuture(ex);
                open.set(false);
                return;
            }
            if (r1 && decChain == null) {
                final WebSocketProtocolException ex = new WebSocketProtocolException(1002, "RSV1 without negotiated extension");
                listener.onError(ex);
                webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                failFuture(ex);
                open.set(false);
                return;
            }
            if (FrameOpcode.isControl(op)) {
                if (!fin) {
                    final WebSocketProtocolException ex = new WebSocketProtocolException(1002, "fragmented control frame");
                    listener.onError(ex);
                    webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                    failFuture(ex);
                    open.set(false);
                    return;
                }
                if (payload.remaining() > 125) {
                    final WebSocketProtocolException ex = new WebSocketProtocolException(1002, "control frame too large");
                    listener.onError(ex);
                    webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                    failFuture(ex);
                    open.set(false);
                    return;
                }
            }

            switch (op) {
                case FrameOpcode.PING:
                    listener.onPing(payload.asReadOnlyBuffer());
                    if (cfg.isAutoPong()) {
                        webSocket.pong(payload.asReadOnlyBuffer());
                    }
                    break;
                case FrameOpcode.PONG:
                    listener.onPong(payload.asReadOnlyBuffer());
                    break;
                case FrameOpcode.CLOSE:
                    int code = 1005;
                    String reason = "";
                    if (payload.remaining() == 1) {
                        final WebSocketProtocolException ex = new WebSocketProtocolException(1002, "Invalid close payload length");
                        listener.onError(ex);
                        webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                        failFuture(ex);
                        open.set(false);
                        return;
                    } else if (payload.remaining() >= 2) {
                        final ByteBuffer dup = payload.slice();
                        code = CloseCodec.readCloseCode(dup);
                        if (!CloseCodec.isValidToReceive(code)) {
                            final WebSocketProtocolException ex = new WebSocketProtocolException(1002, "Invalid close code");
                            listener.onError(ex);
                            webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                            failFuture(ex);
                            open.set(false);
                            return;
                        }
                        if (dup.hasRemaining()) {
                            try {
                                reason = decodeTextStrict(dup.asReadOnlyBuffer());
                            } catch (final WebSocketProtocolException ex) {
                                listener.onError(ex);
                                webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                                failFuture(ex);
                                open.set(false);
                                return;
                            }
                        }
                    }
                    listener.onClose(code, reason);
                    if (!webSocket.isCloseSent()) {
                        final int replyCode = code == 1005 ? 1000 : code;
                        final String replyReason = code == 1005 ? "" : reason;
                        webSocket.sendCloseIfNeeded(replyCode, replyReason);
                    }
                    webSocket.onCloseReceived();
                    break;

                case FrameOpcode.TEXT:
                case FrameOpcode.BINARY:
                    if (assemblingOpcode != -1) {
                        final WebSocketProtocolException ex =
                                new WebSocketProtocolException(1002, "New data frame while fragmented message in progress");
                        listener.onError(ex);
                        webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                        failFuture(ex);
                        open.set(false);
                        return;
                    }
                    if (isTooLarge(payload.remaining())) {
                        final WebSocketProtocolException ex = new WebSocketProtocolException(1009, "Message too big");
                        listener.onError(ex);
                        webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                        failFuture(ex);
                        open.set(false);
                        return;
                    }
                    if (!fin) {
                        assemblingOpcode = op;
                        assemblingCompressed = r1 && decChain != null;
                        assemblingBytes = new ByteArrayOutputStream(Math.max(1024, payload.remaining()));
                        assemblingBytesSize.set(0);
                        try {
                            appendPayload(payload);
                        } catch (final WebSocketProtocolException ex) {
                            listener.onError(ex);
                            webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                            failFuture(ex);
                            open.set(false);
                        }
                        return;
                    }
                    deliverSingle(op, payload, r1);
                    break;

                case FrameOpcode.CONT:
                    if (assemblingOpcode == -1) {
                        final WebSocketProtocolException ex = new WebSocketProtocolException(1002, "Unexpected continuation frame");
                        listener.onError(ex);
                        webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                        failFuture(ex);
                        open.set(false);
                        return;
                    }
                    try {
                        appendPayload(payload);
                    } catch (final WebSocketProtocolException ex) {
                        listener.onError(ex);
                        webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                        failFuture(ex);
                        open.set(false);
                        return;
                    }
                    if (fin) {
                        final ByteBuffer full = ByteBuffer.wrap(assemblingBytes.toByteArray());
                        final int opcode = assemblingOpcode;
                        final boolean compressed = assemblingCompressed;
                        assemblingOpcode = -1;
                        assemblingCompressed = false;
                        assemblingBytes = null;
                        assemblingBytesSize.set(0);
                        deliverSingle(opcode, full, compressed);
                    }
                    break;

                default:
                    final WebSocketProtocolException ex = new WebSocketProtocolException(1002, "Unsupported opcode: " + op);
                    listener.onError(ex);
                    webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                    failFuture(ex);
                    open.set(false);
            }
        }

        private void deliverSingle(final int opcode, final ByteBuffer payload, final boolean rsv1) {
            ByteBuffer data = payload.asReadOnlyBuffer();
            if (rsv1 && decChain != null) {
                try {
                    final byte[] decoded = decChain.decode(toBytes(data));
                    if (isTooLarge(decoded.length)) {
                        throw new WebSocketProtocolException(1009, "Message too big");
                    }
                    data = ByteBuffer.wrap(decoded);
                } catch (final Exception ex) {
                    // Treat inflate failures as protocol errors.
                    final WebSocketProtocolException wex = ex instanceof WebSocketProtocolException
                            ? (WebSocketProtocolException) ex
                            : new WebSocketProtocolException(1002, "Bad compressed payload");
                    listener.onError(ex);
                    webSocket.sendCloseIfNeeded(wex.closeCode, wex.getMessage());
                    failFuture(ex);
                    open.set(false);
                    return;
                }
            }
            if (opcode == FrameOpcode.TEXT) {
                try {
                    listener.onText(CharBuffer.wrap(decodeTextStrict(data)), true);
                } catch (final WebSocketProtocolException ex) {
                    listener.onError(ex);
                    webSocket.sendCloseIfNeeded(ex.closeCode, ex.getMessage());
                    failFuture(ex);
                    open.set(false);
                }
            } else {
                listener.onBinary(data, true);
            }
        }

        private void appendPayload(final ByteBuffer payload) {
            if (assemblingBytes == null) {
                assemblingBytes = new ByteArrayOutputStream();
            }
            if (isTooLarge(assemblingBytesSize.get() + payload.remaining())) {
                throw new WebSocketProtocolException(1009, "Message too big");
            }
            final byte[] tmp = toBytes(payload);
            assemblingBytes.write(tmp, 0, tmp.length);
            assemblingBytesSize.addAndGet(tmp.length);
        }

        private void appendToInbuf(final ByteBuffer src) {
            if (src == null || !src.hasRemaining()) {
                return;
            }
            if (inbuf.remaining() < src.remaining()) {
                final int need = inbuf.position() + src.remaining();
                final int newCap = Math.max(inbuf.capacity() * 2, need);
                final ByteBuffer bigger = ByteBuffer.allocate(newCap);
                inbuf.flip();
                bigger.put(inbuf);
                inbuf = bigger;
            }
            inbuf.put(src);
        }

        private byte[] toBytes(final ByteBuffer buf) {
            final ByteBuffer b = buf.asReadOnlyBuffer();
            final byte[] out = new byte[b.remaining()];
            b.get(out);
            return out;
        }

        private static String headerValue(final HttpResponse r, final String name) {
            final Header h = r.getFirstHeader(name);
            return h != null ? h.getValue() : null;
        }

        private static String decodeTextStrict(final ByteBuffer payload) throws WebSocketProtocolException {
            try {
                final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                return decoder.decode(payload.asReadOnlyBuffer()).toString();
            } catch (final CharacterCodingException ex) {
                throw new WebSocketProtocolException(1007, "Invalid UTF-8 payload");
            }
        }

        private void failFuture(final Exception ex) {
            if (!future.isDone()) {
                future.completeExceptionally(ex);
            }
        }

        private boolean isTooLarge(final long size) {
            final long max = cfg.getMaxMessageSize();
            return max > 0 && size > max;
        }

        private final class H2WebSocket implements WebSocket {

            private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
            // Queue accounting invariant: bytes accepted for send but not fully written yet.
            private int queuedBytes;
            private final ReentrantLock queueLock = new ReentrantLock();
            private final ReentrantLock sendLock = new ReentrantLock();
            private int outOpcode = -1;
            private final int outChunk = Math.max(256, cfg.getOutgoingChunkSize());

            private final AtomicBoolean closeSent = new AtomicBoolean(false);
            private volatile boolean endStreamAfterClose;
            private volatile boolean closeReceived;
            private volatile ScheduledFuture<?> closeTimeoutFuture;

            @Override
            public boolean isOpen() {
                return open.get();
            }

            @Override
            public boolean ping(final ByteBuffer data) {
                return enqueue(writer.ping(data), false);
            }

            @Override
            public boolean pong(final ByteBuffer data) {
                return enqueue(writer.pong(data), false);
            }

            @Override
            public boolean sendText(final CharSequence data, final boolean finalFragment) {
                if (closeSent.get()) {
                    return false;
                }
                final ByteBuffer utf8 = StandardCharsets.UTF_8.encode(data.toString());
                return sendData(FrameOpcode.TEXT, utf8, finalFragment);
            }

            @Override
            public boolean sendBinary(final ByteBuffer data, final boolean finalFragment) {
                if (closeSent.get()) {
                    return false;
                }
                return sendData(FrameOpcode.BINARY, data, finalFragment);
            }

            @Override
            public long queueSize() {
                queueLock.lock();
                try {
                    return queuedBytes;
                } finally {
                    queueLock.unlock();
                }
            }

            @Override
            public CompletableFuture<Void> close(final int statusCode, final String reason) {
                if (!CloseCodec.isValidToSend(statusCode)) {
                    throw new IllegalArgumentException("Invalid close code: " + statusCode);
                }
                final CompletableFuture<Void> cf = new CompletableFuture<>();
                if (!open.get()) {
                    cf.completeExceptionally(new IllegalStateException("WebSocket is closed"));
                    return cf;
                }
                final boolean ok = sendCloseIfNeeded(statusCode, reason);
                if (ok) {
                    cf.complete(null);
                } else {
                    cf.completeExceptionally(new IllegalStateException("Close could not be initiated"));
                }
                return cf;
            }

            @Override
            public boolean sendTextBatch(final List<CharSequence> fragments, final boolean finalFragment) {
                if (fragments == null || fragments.isEmpty()) {
                    throw new IllegalArgumentException("fragments must not be empty");
                }
                final StringBuilder sb = new StringBuilder();
                for (final CharSequence s : fragments) {
                    if (s != null) {
                        sb.append(s);
                    }
                }
                return sendText(sb, finalFragment);
            }

            @Override
            public boolean sendBinaryBatch(final List<ByteBuffer> fragments, final boolean finalFragment) {
                if (fragments == null || fragments.isEmpty()) {
                    throw new IllegalArgumentException("fragments must not be empty");
                }
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (final ByteBuffer b : fragments) {
                    if (b != null) {
                        final byte[] bytes = toBytes(b);
                        out.write(bytes, 0, bytes.length);
                    }
                }
                return sendBinary(ByteBuffer.wrap(out.toByteArray()), finalFragment);
            }

            private boolean sendData(final int opcode, final ByteBuffer data, final boolean fin) {
                sendLock.lock();
                try {
                    if (!open.get()) {
                        return false;
                    }
                    final OutboundFlowSupport.SendResult sendResult = OutboundFlowSupport.sendFragmented(
                            opcode,
                            outOpcode,
                            data,
                            fin,
                            outChunk,
                            false,
                            open::get,
                            this::enqueueDataFrame);
                    outOpcode = sendResult.nextOpcode();
                    return sendResult.accepted();
                } finally {
                    sendLock.unlock();
                }
            }

            private boolean enqueueDataFrame(final int opcode, final ByteBuffer payload, final boolean fin, final boolean firstFragment) {
                if (!open.get()) {
                    return false;
                }
                byte[] out = toBytes(payload);
                int rsv = 0;
                if (encChain != null) {
                    final WebSocketExtensionChain.Encoded encRes = encChain.encode(out, firstFragment, fin);
                    out = encRes.payload;
                    if (encRes.setRsvOnFirst && firstFragment) {
                        rsv = FrameHeaderBits.RSV1;
                    }
                }
                final ByteBuffer frame = writer.frameWithRSV(opcode, ByteBuffer.wrap(out), fin, true, rsv);
                return enqueue(frame, false);
            }

            private boolean enqueue(final ByteBuffer frame, final boolean closeAfter) {
                if (!open.get()) {
                    return false;
                }
                queueLock.lock();
                try {
                    if (closeAfter) {
                        queue.clear();
                        queuedBytes = 0;
                    } else {
                        if (OutboundFlowSupport.exceedsOutboundByteLimit(cfg.getMaxOutboundDataBytes(), queuedBytes, frame.remaining())) {
                            return false;
                        }
                    }
                    queue.add(frame);
                    queuedBytes += frame.remaining();
                } finally {
                    queueLock.unlock();
                }
                if (closeAfter) {
                    closeSent.set(true);
                    scheduleCloseTimeout();
                }
                final DataStreamChannel ch = dataChannel;
                if (ch != null) {
                    ch.requestOutput();
                }
                return true;
            }

            int available() {
                queueLock.lock();
                try {
                    return queuedBytes;
                } finally {
                    queueLock.unlock();
                }
            }

            boolean endStreamPending() {
                return endStreamAfterClose;
            }

            void produce(final DataStreamChannel channel) throws IOException {
                for (; ; ) {
                    final ByteBuffer buf;
                    queueLock.lock();
                    try {
                        buf = queue.peek();
                    } finally {
                        queueLock.unlock();
                    }
                    if (buf == null) {
                        if (endStreamAfterClose) {
                            endStreamAfterClose = false;
                            channel.endStream(null);
                        }
                        return;
                    }
                    final int n = channel.write(buf);
                    if (n == 0) {
                        channel.requestOutput();
                        return;
                    }
                    queueLock.lock();
                    try {
                        queuedBytes = Math.max(0, queuedBytes - n);
                        if (!buf.hasRemaining()) {
                            queue.poll();
                        } else {
                            channel.requestOutput();
                            return;
                        }
                    } finally {
                        queueLock.unlock();
                    }
                }
            }

            boolean isCloseSent() {
                return closeSent.get();
            }

            boolean isCloseReceived() {
                return closeReceived;
            }

            boolean sendCloseIfNeeded(final int code, final String reason) {
                if (closeSent.get()) {
                    return true;
                }
                return enqueue(writer.close(code, reason), true);
            }

            void onCloseReceived() {
                closeReceived = true;
                open.set(false);
                cancelCloseTimeout();
                endStreamAfterClose = true;
                final DataStreamChannel ch = dataChannel;
                if (ch != null) {
                    ch.requestOutput();
                }
            }

            private void scheduleCloseTimeout() {
                final Timeout t = cfg.getCloseWaitTimeout();
                if (t == null || t.isDisabled()) {
                    return;
                }
                if (closeTimeoutFuture != null) {
                    closeTimeoutFuture.cancel(false);
                }
                closeTimeoutFuture = CLOSE_TIMER.schedule(() -> {
                    open.set(false);
                    endStreamAfterClose = true;
                    final DataStreamChannel ch = dataChannel;
                    if (ch != null) {
                        ch.requestOutput();
                    }
                }, t.toMilliseconds(), TimeUnit.MILLISECONDS);
            }

            private void cancelCloseTimeout() {
                if (closeTimeoutFuture != null) {
                    closeTimeoutFuture.cancel(false);
                    closeTimeoutFuture = null;
                }
            }
        }
    }
}
