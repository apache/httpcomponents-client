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
package org.apache.hc.client5.http.websocket.transport;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.websocket.WebSocketBufferOps;
import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.apache.hc.core5.websocket.extension.WebSocketExtensionChain;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.apache.hc.core5.websocket.frame.WebSocketFrameWriter;
import org.apache.hc.core5.websocket.message.CloseCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified WebSocket session engine shared by both HTTP/1.1 and HTTP/2 transports.
 *
 * <p>Combines frame decoding (inbound), frame encoding with queue management
 * (outbound), close-handshake logic, and the application-facing {@link WebSocket}
 * facade. All transport-specific I/O is delegated to a {@link WebSocketTransport}.</p>
 *
 * <p>Thread model: {@link #onData(ByteBuffer)} and {@link #onOutputReady()} must be
 * called from the I/O thread. The {@link WebSocket} facade methods may be called
 * from any thread; they use a write lock for queue access.</p>
 */
@Internal
public final class WebSocketSessionEngine {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSessionEngine.class);

    // ---- constructor parameters ----
    private final WebSocketTransport transport;
    private final WebSocketListener listener;
    private final WebSocketClientConfig cfg;
    private final int outChunk;
    private final ScheduledExecutorService closeTimer; // null for H1

    // ---- read-side state (I/O thread confined) ----
    private final WebSocketFrameDecoder decoder;
    private final ExtensionChain.DecodeChain decChain;
    private final CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    private ByteBuffer inbuf = ByteBuffer.allocate(4096);
    private int assemblingOpcode = -1;
    private boolean assemblingCompressed;
    private ByteBuffer assemblingBuf;
    private long assemblingSize;

    // ---- write-side state ----
    private final WebSocketFrameWriter writer = new WebSocketFrameWriter();
    private final ExtensionChain.EncodeChain encChain;
    private final int rsvMask;
    final ConcurrentLinkedQueue<OutFrame> ctrlOutbound = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<OutFrame> dataOutbound = new ConcurrentLinkedQueue<>();
    private OutFrame activeWrite;
    final AtomicLong dataQueuedBytes = new AtomicLong();
    private final ReentrantLock writeLock = new ReentrantLock();
    private int outOpcode = -1;
    private final int maxFramesPerTick;

    // ---- sync flags ----
    final AtomicBoolean open = new AtomicBoolean(true);
    final AtomicBoolean closeSent = new AtomicBoolean(false);
    private final AtomicBoolean closeReceived = new AtomicBoolean(false);
    volatile boolean closeAfterFlush;
    private volatile ScheduledFuture<?> closeTimeoutFuture;

    // ---- facade ----
    private final WebSocket facade;

    /**
     * @param transport   the underlying I/O channel
     * @param listener    application callback
     * @param cfg         client configuration
     * @param chain       negotiated extension chain (may be {@code null})
     * @param closeTimer  scheduled executor for H2 close timeout; {@code null} for H1
     *                    (H1 uses {@link WebSocketTransport#setTimeout} instead)
     */
    public WebSocketSessionEngine(final WebSocketTransport transport,
                                  final WebSocketListener listener,
                                  final WebSocketClientConfig cfg,
                                  final ExtensionChain chain,
                                  final ScheduledExecutorService closeTimer) {
        this.transport = transport;
        this.listener = listener;
        this.cfg = cfg;
        this.outChunk = Math.max(256, cfg.getOutgoingChunkSize());
        this.closeTimer = closeTimer;
        this.maxFramesPerTick = Math.max(1, cfg.getMaxFramesPerTick());

        final boolean noExtensions = chain == null || chain.isEmpty();
        this.decoder = new WebSocketFrameDecoder(cfg.getMaxFrameSize(), noExtensions, false);
        this.decChain = noExtensions ? null : chain.newDecodeChain();
        this.encChain = noExtensions ? null : chain.newEncodeChain();
        this.rsvMask = noExtensions ? 0 : chain.rsvMask();

        this.facade = new Facade();
    }

    /** Returns the application-facing WebSocket. */
    public WebSocket facade() {
        return facade;
    }

    // ==================================================================
    //  Inbound (push model — data is pushed in by the transport layer)
    // ==================================================================

    /**
     * Push received bytes into the engine for frame decoding and dispatch.
     * Called from the I/O thread.
     */
    public void onData(final ByteBuffer src) {
        try {
            if (!open.get() && !closeSent.get()) {
                return;
            }
            appendToInbuf(src);
            inbuf.flip();

            for (;;) {
                final boolean has;
                try {
                    has = decoder.decode(inbuf);
                } catch (final RuntimeException rte) {
                    final int code = rte instanceof WebSocketProtocolException
                            ? ((WebSocketProtocolException) rte).closeCode
                            : 1002;
                    initiateClose(code, rte.getMessage());
                    inbuf.clear();
                    return;
                }
                if (!has) {
                    break;
                }
                handleFrame();
                if (!open.get() && !closeAfterFlush) {
                    break;
                }
            }
            inbuf.compact();
        } catch (final Exception ex) {
            onError(ex);
            transport.closeGracefully();
        }
    }

    /**
     * Called when the underlying transport disconnects or the stream ends
     * without a proper close handshake.
     */
    public void onDisconnected() {
        if (open.getAndSet(false)) {
            try {
                listener.onClose(1006, "abnormal closure");
            } catch (final Throwable ex) {
                LOG.warn("WebSocket listener onClose threw", ex);
            }
        }
        drainAndRelease();
    }

    /**
     * Called when the transport signals an error.
     */
    public void onError(final Exception cause) {
        try {
            listener.onError(cause);
        } catch (final Throwable ex) {
            LOG.warn("WebSocket listener onError threw", ex);
        }
    }

    // ==================================================================
    //  Outbound (transport-driven drain)
    // ==================================================================

    /**
     * Drain the outbound queues into the transport. Called from the I/O
     * thread when the channel is ready for writing.
     *
     * @return {@code true} if more output is pending (caller should keep
     *         the write interest), {@code false} if queues are empty
     */
    public boolean onOutputReady() {
        try {
            int framesThisTick = 0;

            while (framesThisTick < maxFramesPerTick) {

                if (activeWrite != null && activeWrite.buf.hasRemaining()) {
                    final int written = transport.write(activeWrite.buf);
                    if (written == 0) {
                        transport.requestOutput();
                        return true;
                    }
                    if (!activeWrite.buf.hasRemaining()) {
                        if (activeWrite.dataFrame) {
                            dataQueuedBytes.addAndGet(-activeWrite.size);
                        }
                        activeWrite = null;
                        framesThisTick++;
                    } else {
                        transport.requestOutput();
                        return true;
                    }
                    continue;
                }

                final OutFrame ctrl = ctrlOutbound.poll();
                if (ctrl != null) {
                    activeWrite = ctrl;
                    continue;
                }

                final OutFrame data = dataOutbound.poll();
                if (data != null) {
                    activeWrite = data;
                    continue;
                }

                // Nothing left to write
                if (closeAfterFlush && activeWrite == null
                        && ctrlOutbound.isEmpty() && dataOutbound.isEmpty()) {
                    try {
                        transport.endStream();
                    } catch (final Exception ex) {
                        LOG.debug("Error ending stream: {}", ex.getMessage(), ex);
                    }
                }
                return false;
            }

            // Tick limit reached
            final boolean pending = activeWrite != null && activeWrite.buf.hasRemaining();
            if (pending) {
                transport.requestOutput();
            }
            if (closeAfterFlush && !pending
                    && ctrlOutbound.isEmpty() && dataOutbound.isEmpty()) {
                try {
                    transport.endStream();
                } catch (final Exception ex) {
                    LOG.debug("Error ending stream: {}", ex.getMessage(), ex);
                }
            }
            return pending;
        } catch (final Exception ex) {
            onError(ex);
            transport.closeGracefully();
            return false;
        }
    }

    /**
     * Returns queued byte count for the H2 {@code available()} callback.
     */
    public int availableForOutput() {
        return closeAfterFlush ? 1 : (int) Math.min(dataQueuedBytes.get(), Integer.MAX_VALUE);
    }

    // ==================================================================
    //  Frame handling (inbound dispatch)
    // ==================================================================

    private void handleFrame() {
        final int op = decoder.opcode();
        final boolean fin = decoder.fin();
        final boolean r1 = decoder.rsv1();
        final boolean r2 = decoder.rsv2();
        final boolean r3 = decoder.rsv3();
        final ByteBuffer payload = decoder.payload();

        if (r2 || r3) {
            initiateClose(1002, "RSV2/RSV3 not supported");
            inbuf.clear();
            return;
        }
        if (r1 && decChain == null) {
            initiateClose(1002, "RSV1 without negotiated extension");
            inbuf.clear();
            return;
        }
        if (closeReceived.get() && op != FrameOpcode.CLOSE) {
            return;
        }
        if (FrameOpcode.isControl(op)) {
            if (!fin) {
                initiateClose(1002, "fragmented control frame");
                inbuf.clear();
                return;
            }
            if (payload.remaining() > 125) {
                initiateClose(1002, "control frame too large");
                inbuf.clear();
                return;
            }
        }

        switch (op) {
            case FrameOpcode.PING: {
                try {
                    listener.onPing(payload.asReadOnlyBuffer());
                } catch (final Throwable ex) {
                    LOG.warn("WebSocket listener onPing threw", ex);
                }
                if (cfg.isAutoPong()) {
                    sendPong(payload.asReadOnlyBuffer());
                }
                break;
            }
            case FrameOpcode.PONG: {
                try {
                    listener.onPong(payload.asReadOnlyBuffer());
                } catch (final Throwable ex) {
                    LOG.warn("WebSocket listener onPong threw", ex);
                }
                break;
            }
            case FrameOpcode.CLOSE: {
                handleCloseFrame(payload);
                break;
            }
            case FrameOpcode.CONT: {
                if (assemblingOpcode == -1) {
                    initiateClose(1002, "Unexpected continuation frame");
                    inbuf.clear();
                    return;
                }
                if (r1) {
                    initiateClose(1002, "RSV1 set on continuation");
                    inbuf.clear();
                    return;
                }
                appendToMessage(payload);
                if (fin) {
                    deliverAssembledMessage();
                }
                break;
            }
            case FrameOpcode.TEXT:
            case FrameOpcode.BINARY: {
                if (assemblingOpcode != -1) {
                    initiateClose(1002, "New data frame while fragmented message in progress");
                    inbuf.clear();
                    return;
                }
                if (!fin) {
                    startMessage(op, payload, r1);
                    break;
                }
                if (cfg.getMaxMessageSize() > 0 && payload.remaining() > cfg.getMaxMessageSize()) {
                    initiateClose(1009, "Message too big");
                    break;
                }
                if (r1) {
                    final byte[] comp = WebSocketBufferOps.toBytes(payload);
                    final byte[] plain;
                    try {
                        plain = decChain.decode(comp);
                    } catch (final Exception e) {
                        initiateClose(1007, "Extension decode failed");
                        inbuf.clear();
                        return;
                    }
                    deliverSingle(op, ByteBuffer.wrap(plain));
                } else {
                    deliverSingle(op, payload.asReadOnlyBuffer());
                }
                break;
            }
            default: {
                initiateClose(1002, "Unsupported opcode: " + op);
                inbuf.clear();
            }
        }
    }

    private void handleCloseFrame(final ByteBuffer payload) {
        final ByteBuffer ro = payload.asReadOnlyBuffer();
        int code = 1005;
        String reason = "";
        final int len = ro.remaining();

        if (len == 1) {
            initiateClose(1002, "Close frame length of 1 is invalid");
            inbuf.clear();
            return;
        } else if (len >= 2) {
            final ByteBuffer dup = ro.slice();
            code = CloseCodec.readCloseCode(dup);

            if (!CloseCodec.isValidToReceive(code)) {
                initiateClose(1002, "Invalid close code: " + code);
                inbuf.clear();
                return;
            }

            if (dup.hasRemaining()) {
                utf8Decoder.reset();
                try {
                    reason = utf8Decoder.decode(dup.asReadOnlyBuffer()).toString();
                } catch (final CharacterCodingException badUtf8) {
                    initiateClose(1007, "Invalid UTF-8 in close reason");
                    inbuf.clear();
                    return;
                }
            }
        }

        notifyCloseOnce(code, reason);
        closeReceived.set(true);

        if (!closeSent.get()) {
            sendCloseEcho(ro);
        }

        // For H1: set socket timeout so we don't wait forever
        transport.setTimeout(cfg.getCloseWaitTimeout());
        closeAfterFlush = true;
        transport.requestOutput();
        inbuf.clear();
    }

    // ---- message assembly ----

    private void startMessage(final int opcode, final ByteBuffer payload, final boolean rsv1) {
        assemblingOpcode = opcode;
        assemblingCompressed = rsv1 && decChain != null;
        final int initial = Math.max(1024, payload.remaining());
        assemblingBuf = ByteBuffer.allocate(initial);
        assemblingSize = 0L;
        appendToMessage(payload);
    }

    private void appendToMessage(final ByteBuffer payload) {
        final int n = payload.remaining();
        assemblingSize += n;
        if (cfg.getMaxMessageSize() > 0 && assemblingSize > cfg.getMaxMessageSize()) {
            initiateClose(1009, "Message too big");
            return;
        }
        assemblingBuf = WebSocketBufferOps.ensureCapacity(assemblingBuf, n);
        assemblingBuf.put(payload.asReadOnlyBuffer());
    }

    private void deliverAssembledMessage() {
        assemblingBuf.flip();
        final byte[] body = new byte[assemblingBuf.remaining()];
        assemblingBuf.get(body);
        final int op = assemblingOpcode;
        final boolean compressed = assemblingCompressed;

        assemblingOpcode = -1;
        assemblingCompressed = false;
        assemblingBuf = null;
        assemblingSize = 0L;

        byte[] data = body;
        if (compressed && decChain != null) {
            try {
                data = decChain.decode(body);
            } catch (final Exception e) {
                try {
                    listener.onError(e);
                } catch (final Throwable ex) {
                    LOG.warn("WebSocket listener onError threw", ex);
                }
                initiateClose(1007, "Extension decode failed");
                return;
            }
        }

        if (op == FrameOpcode.TEXT) {
            utf8Decoder.reset();
            try {
                final CharBuffer cb = utf8Decoder.decode(ByteBuffer.wrap(data));
                try {
                    listener.onText(cb, true);
                } catch (final Throwable ex) {
                    LOG.warn("WebSocket listener onText threw", ex);
                }
            } catch (final CharacterCodingException cce) {
                initiateClose(1007, "Invalid UTF-8 in text message");
            }
        } else if (op == FrameOpcode.BINARY) {
            try {
                listener.onBinary(ByteBuffer.wrap(data).asReadOnlyBuffer(), true);
            } catch (final Throwable ex) {
                LOG.warn("WebSocket listener onBinary threw", ex);
            }
        }
    }

    private void deliverSingle(final int op, final ByteBuffer payloadRO) {
        if (op == FrameOpcode.TEXT) {
            utf8Decoder.reset();
            try {
                final CharBuffer cb = utf8Decoder.decode(payloadRO);
                try {
                    listener.onText(cb, true);
                } catch (final Throwable ex) {
                    LOG.warn("WebSocket listener onText threw", ex);
                }
            } catch (final CharacterCodingException cce) {
                initiateClose(1007, "Invalid UTF-8 in text message");
            }
        } else if (op == FrameOpcode.BINARY) {
            try {
                listener.onBinary(payloadRO, true);
            } catch (final Throwable ex) {
                LOG.warn("WebSocket listener onBinary threw", ex);
            }
        }
    }

    // ---- inbound helpers ----

    private void appendToInbuf(final ByteBuffer src) {
        if (src == null || !src.hasRemaining()) {
            return;
        }
        inbuf = WebSocketBufferOps.ensureCapacity(inbuf, src.remaining());
        inbuf.put(src);
    }

    private void initiateClose(final int code, final String reason) {
        if (!closeSent.get()) {
            try {
                sendClose(code, reason);
            } catch (final Throwable ex) {
                LOG.warn("WebSocket sendClose threw", ex);
            }
            if (closeTimer != null) {
                scheduleCloseTimeout();
            } else {
                transport.setTimeout(cfg.getCloseWaitTimeout());
            }
        }
        notifyCloseOnce(code, reason);
    }

    private void notifyCloseOnce(final int code, final String reason) {
        if (open.getAndSet(false)) {
            try {
                listener.onClose(code, reason == null ? "" : reason);
            } catch (final Throwable ex) {
                LOG.warn("WebSocket listener onClose threw", ex);
            }
        }
    }

    // ==================================================================
    //  Control frame sending (internal)
    // ==================================================================

    private void sendPong(final ByteBuffer payload) {
        final ByteBuffer ro = payload == null ? ByteBuffer.allocate(0) : payload.asReadOnlyBuffer();
        enqueueCtrl(buildFrame(FrameOpcode.PONG, ro, true, false));
    }

    private void sendCloseEcho(final ByteBuffer rawClosePayload) {
        final ByteBuffer ro = rawClosePayload == null ? ByteBuffer.allocate(0) : rawClosePayload.asReadOnlyBuffer();
        enqueueCtrl(buildCloseEcho(ro));
    }

    private void sendClose(final int code, final String reason) {
        final String truncated = CloseCodec.truncateReasonUtf8(reason);
        final byte[] payloadBytes = CloseCodec.encode(code, truncated);
        enqueueCtrl(buildFrame(FrameOpcode.CLOSE, ByteBuffer.wrap(payloadBytes), true, false));
    }

    // ==================================================================
    //  Queue management
    // ==================================================================

    boolean enqueueCtrl(final OutFrame frame) {
        final boolean closeFrame = isCloseFrame(frame.buf);

        if (!closeFrame && (!open.get() || closeSent.get())) {
            return false;
        }

        if (closeFrame) {
            if (!closeSent.compareAndSet(false, true)) {
                return false;
            }
            // RFC 6455 §5.5.1: no data frames may be sent after Close
            OutFrame queued;
            while ((queued = dataOutbound.poll()) != null) {
                if (queued.dataFrame) {
                    dataQueuedBytes.addAndGet(-queued.size);
                }
            }
        } else {
            final int max = cfg.getMaxOutboundControlQueue();
            if (max > 0 && ctrlOutbound.size() >= max) {
                return false;
            }
        }
        ctrlOutbound.offer(frame);
        transport.requestOutput();
        return true;
    }

    boolean enqueueData(final OutFrame frame) {
        if (!open.get() || closeSent.get()) {
            return false;
        }
        final long limit = cfg.getMaxOutboundDataBytes();
        final long newSize = dataQueuedBytes.addAndGet(frame.size);
        if (OutboundFlowSupport.exceedsOutboundByteLimit(limit, newSize - frame.size, frame.size)) {
            dataQueuedBytes.addAndGet(-frame.size);
            return false;
        }
        dataOutbound.offer(frame);
        transport.requestOutput();
        return true;
    }

    private void drainAndRelease() {
        if (activeWrite != null) {
            if (activeWrite.dataFrame) {
                dataQueuedBytes.addAndGet(-activeWrite.size);
            }
            activeWrite = null;
        }
        OutFrame f;
        while ((f = ctrlOutbound.poll()) != null) {
            if (f.dataFrame) {
                dataQueuedBytes.addAndGet(-f.size);
            }
        }
        while ((f = dataOutbound.poll()) != null) {
            if (f.dataFrame) {
                dataQueuedBytes.addAndGet(-f.size);
            }
        }
        cancelCloseTimeout();
    }

    // ==================================================================
    //  Frame building
    // ==================================================================

    private static int maskedHeaderSize(final int payloadLen) {
        if (payloadLen <= 125) {
            return 2 + 4;
        } else if (payloadLen <= 0xFFFF) {
            return 4 + 4;
        } else {
            return 10 + 4;
        }
    }

    OutFrame buildFrame(final int opcode, final ByteBuffer payload, final boolean fin, final boolean dataFrame) {
        final ByteBuffer ro = payload == null ? ByteBuffer.allocate(0) : payload.asReadOnlyBuffer();
        final int len = ro.remaining();
        final int totalSize = maskedHeaderSize(len) + len;

        final ByteBuffer buf = cfg.isDirectBuffers()
                ? ByteBuffer.allocateDirect(totalSize)
                : ByteBuffer.allocate(totalSize);

        buf.clear();
        writer.frameInto(opcode, ro, fin, true, buf);
        buf.flip();

        return new OutFrame(buf, dataFrame);
    }

    OutFrame buildFrameWithRsv(final int opcode, final ByteBuffer payload, final boolean fin,
                               final int rsvBits, final boolean dataFrame) {
        final ByteBuffer ro = payload == null ? ByteBuffer.allocate(0) : payload.asReadOnlyBuffer();
        final int len = ro.remaining();
        final int totalSize = maskedHeaderSize(len) + len;

        final ByteBuffer buf = cfg.isDirectBuffers()
                ? ByteBuffer.allocateDirect(totalSize)
                : ByteBuffer.allocate(totalSize);

        buf.clear();
        writer.frameIntoWithRSV(opcode, ro, fin, true, rsvBits, buf);
        buf.flip();

        return new OutFrame(buf, dataFrame);
    }

    private OutFrame buildCloseEcho(final ByteBuffer payload) {
        final ByteBuffer ro = payload == null ? ByteBuffer.allocate(0) : payload.asReadOnlyBuffer();
        final int len = ro.remaining();
        final int totalSize = maskedHeaderSize(len) + len;

        final ByteBuffer buf = cfg.isDirectBuffers()
                ? ByteBuffer.allocateDirect(totalSize)
                : ByteBuffer.allocate(totalSize);

        buf.clear();
        writer.frameInto(FrameOpcode.CLOSE, ro, true, true, buf);
        buf.flip();

        return new OutFrame(buf, false);
    }

    private OutFrame buildDataFrame(final int opcode, final ByteBuffer payload,
                                    final boolean fin, final boolean firstFragment) {
        if (encChain == null) {
            return buildFrame(opcode, payload, fin, true);
        }
        final byte[] plain = WebSocketBufferOps.toBytes(payload);
        final WebSocketExtensionChain.Encoded enc =
                encChain.encode(plain, firstFragment, fin);
        final int rsv = enc.setRsvOnFirst && firstFragment ? rsvMask : 0;
        return buildFrameWithRsv(opcode, ByteBuffer.wrap(enc.payload), fin, rsv, true);
    }

    // ---- close timeout (H2) ----

    private void scheduleCloseTimeout() {
        if (closeTimer == null) {
            return;
        }
        final Timeout t = cfg.getCloseWaitTimeout();
        if (t == null || t.isDisabled()) {
            return;
        }
        if (closeTimeoutFuture != null) {
            closeTimeoutFuture.cancel(false);
        }
        closeTimeoutFuture = closeTimer.schedule(() -> {
            open.set(false);
            closeAfterFlush = true;
            transport.requestOutput();
        }, t.toMilliseconds(), TimeUnit.MILLISECONDS);
    }

    private void cancelCloseTimeout() {
        if (closeTimeoutFuture != null) {
            closeTimeoutFuture.cancel(false);
            closeTimeoutFuture = null;
        }
    }

    // ---- helpers ----

    private static boolean isCloseFrame(final ByteBuffer buf) {
        if (buf.remaining() < 2) {
            return false;
        }
        final int pos = buf.position();
        final byte b1 = buf.get(pos);
        final int opcode = b1 & 0x0F;
        return opcode == FrameOpcode.CLOSE;
    }

    // ==================================================================
    //  OutFrame
    // ==================================================================

    static final class OutFrame {
        final ByteBuffer buf;
        final boolean dataFrame;
        final int size;

        OutFrame(final ByteBuffer buf, final boolean dataFrame) {
            this.buf = buf;
            this.dataFrame = dataFrame;
            this.size = buf.remaining();
        }
    }

    // ==================================================================
    //  Facade (application-facing WebSocket)
    // ==================================================================

    private final class Facade implements WebSocket {

        @Override
        public boolean isOpen() {
            return open.get() && !closeSent.get();
        }

        @Override
        public boolean ping(final ByteBuffer data) {
            if (!open.get() || closeSent.get()) {
                return false;
            }
            final ByteBuffer ro = data == null ? ByteBuffer.allocate(0) : data.asReadOnlyBuffer();
            if (ro.remaining() > 125) {
                return false;
            }
            return enqueueCtrl(buildFrame(FrameOpcode.PING, ro, true, false));
        }

        @Override
        public boolean pong(final ByteBuffer data) {
            if (!open.get() || closeSent.get()) {
                return false;
            }
            final ByteBuffer ro = data == null ? ByteBuffer.allocate(0) : data.asReadOnlyBuffer();
            if (ro.remaining() > 125) {
                return false;
            }
            return enqueueCtrl(buildFrame(FrameOpcode.PONG, ro, true, false));
        }

        @Override
        public boolean sendText(final CharSequence data, final boolean finalFragment) {
            if (!open.get() || closeSent.get() || data == null) {
                return false;
            }
            final ByteBuffer utf8 = StandardCharsets.UTF_8.encode(data.toString());
            return sendDataMessage(FrameOpcode.TEXT, utf8, finalFragment);
        }

        @Override
        public boolean sendBinary(final ByteBuffer data, final boolean finalFragment) {
            if (!open.get() || closeSent.get() || data == null) {
                return false;
            }
            return sendDataMessage(FrameOpcode.BINARY, data, finalFragment);
        }

        private boolean sendDataMessage(final int opcode, final ByteBuffer data, final boolean fin) {
            writeLock.lock();
            try {
                if (encChain != null && outOpcode == -1 && fin) {
                    // Compress whole message, then fragment the compressed payload
                    final byte[] plain = WebSocketBufferOps.toBytes(data);
                    final WebSocketExtensionChain.Encoded enc =
                            encChain.encode(plain, true, true);
                    final OutboundFlowSupport.SendResult sendResult = OutboundFlowSupport.sendFragmented(
                            opcode,
                            outOpcode,
                            ByteBuffer.wrap(enc.payload),
                            true,
                            outChunk,
                            true,
                            () -> open.get() && !closeSent.get(),
                            (frameOpcode, payload, frameFin, firstFragment) -> {
                                final int rsv = enc.setRsvOnFirst && firstFragment ? rsvMask : 0;
                                return enqueueData(buildFrameWithRsv(frameOpcode, payload, frameFin, rsv, true));
                            });
                    outOpcode = sendResult.nextOpcode();
                    return sendResult.accepted();
                }

                final OutboundFlowSupport.SendResult sendResult = OutboundFlowSupport.sendFragmented(
                        opcode,
                        outOpcode,
                        data,
                        fin,
                        outChunk,
                        false,
                        () -> open.get() && !closeSent.get(),
                        (frameOpcode, payload, frameFin, firstFragment) ->
                                enqueueData(buildDataFrame(frameOpcode, payload, frameFin, firstFragment)));
                outOpcode = sendResult.nextOpcode();
                return sendResult.accepted();
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public boolean sendTextBatch(final List<CharSequence> fragments, final boolean finalFragment) {
            if (!open.get() || closeSent.get() || fragments == null || fragments.isEmpty()) {
                return false;
            }
            writeLock.lock();
            try {
                int currentOpcode = outOpcode == -1 ? FrameOpcode.TEXT : FrameOpcode.CONT;
                if (outOpcode == -1) {
                    outOpcode = FrameOpcode.TEXT;
                }
                boolean firstFragment = currentOpcode != FrameOpcode.CONT;

                for (int i = 0; i < fragments.size(); i++) {
                    final CharSequence part = Args.notNull(fragments.get(i), "fragment");
                    final ByteBuffer utf8 = StandardCharsets.UTF_8.encode(part.toString());
                    final ByteBuffer ro = utf8.asReadOnlyBuffer();

                    while (ro.hasRemaining()) {
                        if (!open.get() || closeSent.get()) {
                            outOpcode = -1;
                            return false;
                        }
                        final int n = Math.min(ro.remaining(), outChunk);

                        final int oldLimit = ro.limit();
                        final int newLimit = ro.position() + n;
                        ro.limit(newLimit);
                        final ByteBuffer slice = ro.slice();
                        ro.limit(oldLimit);
                        ro.position(newLimit);

                        final boolean isLastFragment = i == fragments.size() - 1;
                        final boolean lastSlice = !ro.hasRemaining() && isLastFragment && finalFragment;

                        if (!enqueueData(buildDataFrame(currentOpcode, slice, lastSlice, firstFragment))) {
                            outOpcode = -1;
                            return false;
                        }
                        currentOpcode = FrameOpcode.CONT;
                        firstFragment = false;
                    }
                }

                if (finalFragment) {
                    outOpcode = -1;
                }
                return true;
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public boolean sendBinaryBatch(final List<ByteBuffer> fragments, final boolean finalFragment) {
            if (!open.get() || closeSent.get() || fragments == null || fragments.isEmpty()) {
                return false;
            }
            writeLock.lock();
            try {
                int currentOpcode = outOpcode == -1 ? FrameOpcode.BINARY : FrameOpcode.CONT;
                if (outOpcode == -1) {
                    outOpcode = FrameOpcode.BINARY;
                }
                boolean firstFragment = currentOpcode != FrameOpcode.CONT;

                for (int i = 0; i < fragments.size(); i++) {
                    final ByteBuffer src = Args.notNull(fragments.get(i), "fragment").asReadOnlyBuffer();

                    while (src.hasRemaining()) {
                        if (!open.get() || closeSent.get()) {
                            outOpcode = -1;
                            return false;
                        }
                        final int n = Math.min(src.remaining(), outChunk);

                        final int oldLimit = src.limit();
                        final int newLimit = src.position() + n;
                        src.limit(newLimit);
                        final ByteBuffer slice = src.slice();
                        src.limit(oldLimit);
                        src.position(newLimit);

                        final boolean isLastFragment = i == fragments.size() - 1;
                        final boolean lastSlice = !src.hasRemaining() && isLastFragment && finalFragment;

                        if (!enqueueData(buildDataFrame(currentOpcode, slice, lastSlice, firstFragment))) {
                            outOpcode = -1;
                            return false;
                        }
                        currentOpcode = FrameOpcode.CONT;
                        firstFragment = false;
                    }
                }

                if (finalFragment) {
                    outOpcode = -1;
                }
                return true;
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public long queueSize() {
            return dataQueuedBytes.get();
        }

        @Override
        public CompletableFuture<Void> close(final int statusCode, final String reason) {
            final CompletableFuture<Void> future = new CompletableFuture<>();

            if (!open.get()) {
                future.completeExceptionally(
                        new IllegalStateException("WebSocket is already closed"));
                return future;
            }

            if (!CloseCodec.isValidToSend(statusCode)) {
                future.completeExceptionally(
                        new IllegalArgumentException("Invalid close status code: " + statusCode));
                return future;
            }

            final String truncated = CloseCodec.truncateReasonUtf8(reason);
            final byte[] payloadBytes = CloseCodec.encode(statusCode, truncated);
            final ByteBuffer payload = ByteBuffer.wrap(payloadBytes);

            if (!enqueueCtrl(buildFrame(FrameOpcode.CLOSE, payload, true, false))) {
                future.completeExceptionally(
                        new IllegalStateException("WebSocket is closing or already closed"));
                return future;
            }

            if (closeTimer != null) {
                scheduleCloseTimeout();
            } else {
                transport.setTimeout(cfg.getCloseWaitTimeout());
            }
            future.complete(null);
            return future;
        }
    }
}
