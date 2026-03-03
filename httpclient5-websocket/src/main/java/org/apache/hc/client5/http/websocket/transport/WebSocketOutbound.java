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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.core5.websocket.extension.WebSocketExtensionChain;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.apache.hc.core5.websocket.message.CloseCodec;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.EventMask;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

/**
 * Outbound path: frame building, queues, writing, and the app-facing WebSocket facade.
 */
@Internal
final class WebSocketOutbound {

    static final class OutFrame {

        final ByteBuffer buf;
        final boolean pooled;
        final boolean dataFrame;
        final int size;

        OutFrame(final ByteBuffer buf, final boolean pooled, final boolean dataFrame) {
            this.buf = buf;
            this.pooled = pooled;
            this.dataFrame = dataFrame;
            this.size = buf.remaining();
        }
    }

    private final WebSocketSessionState s;
    private final WebSocket facade;

    WebSocketOutbound(final WebSocketSessionState s) {
        this.s = s;
        this.facade = new Facade();
    }

    WebSocket facade() {
        return facade;
    }

    // ---------------------------------------------------- IO writing ---------

    void onOutputReady(final IOSession ioSession) {
        try {
            int framesThisTick = 0;

            while (framesThisTick < s.maxFramesPerTick) {

                if (s.activeWrite != null && s.activeWrite.buf.hasRemaining()) {
                    final int written = ioSession.write(s.activeWrite.buf);
                    if (written == 0) {
                        ioSession.setEvent(EventMask.WRITE);
                        return;
                    }
                    if (!s.activeWrite.buf.hasRemaining()) {
                        if (s.activeWrite.dataFrame) {
                            s.dataQueuedBytes.addAndGet(-s.activeWrite.size);
                        }
                        release(s.activeWrite);
                        s.activeWrite = null;
                        framesThisTick++;
                    } else {
                        ioSession.setEvent(EventMask.WRITE);
                        return;
                    }
                    continue;
                }

                final OutFrame ctrl = s.ctrlOutbound.poll();
                if (ctrl != null) {
                    s.activeWrite = ctrl;
                    continue;
                }

                final OutFrame data = s.dataOutbound.poll();
                if (data != null) {
                    s.activeWrite = data;
                    continue;
                }

                ioSession.clearEvent(EventMask.WRITE);
                if (s.closeAfterFlush && s.activeWrite == null && s.ctrlOutbound.isEmpty() && s.dataOutbound.isEmpty()) {
                    ioSession.close(CloseMode.GRACEFUL);
                }
                return;
            }

            if (s.activeWrite != null && s.activeWrite.buf.hasRemaining()) {
                ioSession.setEvent(EventMask.WRITE);
            } else {
                ioSession.clearEvent(EventMask.WRITE);
            }

            if (s.closeAfterFlush && s.activeWrite == null && s.ctrlOutbound.isEmpty() && s.dataOutbound.isEmpty()) {
                ioSession.close(CloseMode.GRACEFUL);
            }

        } catch (final Exception ex) {
            try {
                s.listener.onError(ex);
            } finally {
                s.session.close(CloseMode.GRACEFUL);
            }
        }
    }

    private void release(final OutFrame frame) {
        // No-op: buffers are not pooled.
    }

    boolean enqueueCtrl(final OutFrame frame) {
        final boolean closeFrame = isCloseFrame(frame.buf);

        if (!closeFrame && (!s.open.get() || s.closeSent.get())) {
            release(frame);
            return false;
        }

        if (closeFrame) {
            if (!s.closeSent.compareAndSet(false, true)) {
                release(frame);
                return false;
            }
        } else {
            final int max = s.cfg.getMaxOutboundControlQueue();
            if (max > 0 && s.ctrlOutbound.size() >= max) {
                release(frame);
                return false;
            }
        }
        s.ctrlOutbound.offer(frame);
        s.session.setEvent(EventMask.WRITE);
        return true;
    }


    boolean enqueueData(final OutFrame frame) {
        if (!s.open.get() || s.closeSent.get()) {
            release(frame);
            return false;
        }
        final long limit = s.cfg.getMaxOutboundDataBytes();
        final long newSize = s.dataQueuedBytes.addAndGet(frame.size);
        if (OutboundFlowSupport.exceedsOutboundByteLimit(limit, newSize - frame.size, frame.size)) {
            s.dataQueuedBytes.addAndGet(-frame.size);
            release(frame);
            return false;
        }
        s.dataOutbound.offer(frame);
        s.session.setEvent(EventMask.WRITE);
        return true;
    }

    private static boolean isCloseFrame(final ByteBuffer buf) {
        if (buf.remaining() < 2) {
            return false;
        }
        final int pos = buf.position();
        final byte b1 = buf.get(pos);
        final int opcode = b1 & 0x0F;
        return opcode == FrameOpcode.CLOSE;
    }

    // package-private so WebSocketInbound can use them
    OutFrame pooledFrame(final int opcode, final ByteBuffer payload, final boolean fin, final boolean dataFrame) {
        final ByteBuffer ro = payload == null ? ByteBuffer.allocate(0) : payload.asReadOnlyBuffer();
        final int len = ro.remaining();

        final int headerEstimate;
        if (len <= 125) {
            headerEstimate = 2 + 4;   // 2-byte header + 4-byte mask
        } else if (len <= 0xFFFF) {
            headerEstimate = 4 + 4;   // 4-byte header + 4-byte mask
        } else {
            headerEstimate = 10 + 4;  // 10-byte header + 4-byte mask
        }

        final int totalSize = headerEstimate + len;

        final ByteBuffer buf = s.cfg.isDirectBuffers()
                ? ByteBuffer.allocateDirect(totalSize)
                : ByteBuffer.allocate(totalSize);
        final boolean pooled = false;

        buf.clear();
        // opcode (int), payload (ByteBuffer), fin (boolean), mask (boolean), out (ByteBuffer)
        s.writer.frameInto(opcode, ro, fin, true, buf);
        buf.flip();

        return new OutFrame(buf, pooled, dataFrame);
    }

    // package-private for outbound compression (RSV1 when negotiated)
    OutFrame pooledFrameWithRsv(final int opcode, final ByteBuffer payload, final boolean fin, final int rsvBits, final boolean dataFrame) {
        final ByteBuffer ro = payload == null ? ByteBuffer.allocate(0) : payload.asReadOnlyBuffer();
        final int len = ro.remaining();

        final int headerEstimate;
        if (len <= 125) {
            headerEstimate = 2 + 4;
        } else if (len <= 0xFFFF) {
            headerEstimate = 4 + 4;
        } else {
            headerEstimate = 10 + 4;
        }

        final int totalSize = headerEstimate + len;

        final ByteBuffer buf = s.cfg.isDirectBuffers()
                ? ByteBuffer.allocateDirect(totalSize)
                : ByteBuffer.allocate(totalSize);
        final boolean pooled = false;

        buf.clear();
        s.writer.frameIntoWithRSV(opcode, ro, fin, true, rsvBits, buf);
        buf.flip();

        return new OutFrame(buf, pooled, dataFrame);
    }

    // package-private so WebSocketInbound can use it for close echo
    OutFrame pooledCloseEcho(final ByteBuffer payload) {
        final ByteBuffer ro = payload == null ? ByteBuffer.allocate(0) : payload.asReadOnlyBuffer();
        final int len = ro.remaining();

        final int headerEstimate;
        if (len <= 125) {
            headerEstimate = 2 + 4;
        } else if (len <= 0xFFFF) {
            headerEstimate = 4 + 4;
        } else {
            headerEstimate = 10 + 4;
        }

        final int totalSize = headerEstimate + len;

        final ByteBuffer buf = s.cfg.isDirectBuffers()
                ? ByteBuffer.allocateDirect(totalSize)
                : ByteBuffer.allocate(totalSize);
        final boolean pooled = false;

        buf.clear();
        s.writer.frameInto(FrameOpcode.CLOSE, ro, true, true, buf);
        buf.flip();

        return new OutFrame(buf, pooled, false);
    }

    // package-private: used by WebSocketInbound.onDisconnected()
    void drainAndRelease() {
        if (s.activeWrite != null) {
            if (s.activeWrite.dataFrame) {
                s.dataQueuedBytes.addAndGet(-s.activeWrite.size);
            }
            release(s.activeWrite);
            s.activeWrite = null;
        }
        OutFrame f;
        while ((f = s.ctrlOutbound.poll()) != null) {
            release(f);
        }
        while ((f = s.dataOutbound.poll()) != null) {
            if (f.dataFrame) {
                s.dataQueuedBytes.addAndGet(-f.size);
            }
            release(f);
        }
    }

    // --------------------------------------------------------- Facade --------

    private final class Facade implements WebSocket {

        @Override
        public boolean isOpen() {
            return s.open.get() && !s.closeSent.get();
        }

        @Override
        public boolean ping(final ByteBuffer data) {
            if (!s.open.get() || s.closeSent.get()) {
                return false;
            }
            final ByteBuffer ro = data == null ? ByteBuffer.allocate(0) : data.asReadOnlyBuffer();
            if (ro.remaining() > 125) {
                return false;
            }
            return enqueueCtrl(pooledFrame(FrameOpcode.PING, ro, true, false));
        }

        @Override
        public boolean pong(final ByteBuffer data) {
            if (!s.open.get() || s.closeSent.get()) {
                return false;
            }
            final ByteBuffer ro = data == null ? ByteBuffer.allocate(0) : data.asReadOnlyBuffer();
            if (ro.remaining() > 125) {
                return false;
            }
            return enqueueCtrl(pooledFrame(FrameOpcode.PONG, ro, true, false));
        }

        @Override
        public boolean sendText(final CharSequence data, final boolean finalFragment) {
            if (!s.open.get() || s.closeSent.get() || data == null) {
                return false;
            }
            final ByteBuffer utf8 = StandardCharsets.UTF_8.encode(data.toString());
            return sendData(FrameOpcode.TEXT, utf8, finalFragment);
        }

        @Override
        public boolean sendBinary(final ByteBuffer data, final boolean finalFragment) {
            if (!s.open.get() || s.closeSent.get() || data == null) {
                return false;
            }
            return sendData(FrameOpcode.BINARY, data, finalFragment);
        }

        private boolean sendData(final int opcode, final ByteBuffer data, final boolean fin) {
            s.writeLock.lock();
            try {
                if (s.encChain != null && s.outOpcode == -1 && fin) {
                    // Compress the whole message, then fragment the compressed payload.
                    final byte[] plain = toBytes(data);
                    final WebSocketExtensionChain.Encoded enc =
                            s.encChain.encode(plain, true, true);
                    final OutboundFlowSupport.SendResult sendResult = OutboundFlowSupport.sendFragmented(
                            opcode,
                            s.outOpcode,
                            ByteBuffer.wrap(enc.payload),
                            true,
                            s.outChunk,
                            true,
                            () -> s.open.get() && !s.closeSent.get(),
                            (frameOpcode, payload, frameFin, firstFragment) -> {
                                final int rsv = enc.setRsvOnFirst && firstFragment ? s.rsvMask : 0;
                                return enqueueData(pooledFrameWithRsv(frameOpcode, payload, frameFin, rsv, true));
                            });
                    s.outOpcode = sendResult.nextOpcode();
                    return sendResult.accepted();
                }

                final OutboundFlowSupport.SendResult sendResult = OutboundFlowSupport.sendFragmented(
                        opcode,
                        s.outOpcode,
                        data,
                        fin,
                        s.outChunk,
                        false,
                        () -> s.open.get() && !s.closeSent.get(),
                        (frameOpcode, payload, frameFin, firstFragment) ->
                                enqueueData(buildDataFrame(frameOpcode, payload, frameFin, firstFragment)));
                s.outOpcode = sendResult.nextOpcode();
                return sendResult.accepted();
            } finally {
                s.writeLock.unlock();
            }
        }


        @Override
        public boolean sendTextBatch(final List<CharSequence> fragments, final boolean finalFragment) {
            if (!s.open.get() || s.closeSent.get() || fragments == null || fragments.isEmpty()) {
                return false;
            }
            s.writeLock.lock();
            try {
                int currentOpcode = s.outOpcode == -1 ? FrameOpcode.TEXT : FrameOpcode.CONT;
                if (s.outOpcode == -1) {
                    s.outOpcode = FrameOpcode.TEXT;
                }
                boolean firstFragment = currentOpcode != FrameOpcode.CONT;

                for (int i = 0; i < fragments.size(); i++) {
                    final CharSequence part = Args.notNull(fragments.get(i), "fragment");
                    final ByteBuffer utf8 = StandardCharsets.UTF_8.encode(part.toString());
                    final ByteBuffer ro = utf8.asReadOnlyBuffer();

                    while (ro.hasRemaining()) {
                        if (!s.open.get() || s.closeSent.get()) {
                            s.outOpcode = -1;
                            return false;
                        }
                        final int n = Math.min(ro.remaining(), s.outChunk);

                        final int oldLimit = ro.limit();
                        final int newLimit = ro.position() + n;
                        ro.limit(newLimit);
                        final ByteBuffer slice = ro.slice();
                        ro.limit(oldLimit);
                        ro.position(newLimit);

                        final boolean isLastFragment = i == fragments.size() - 1;
                        final boolean lastSlice = !ro.hasRemaining() && isLastFragment && finalFragment;

                        if (!enqueueData(buildDataFrame(currentOpcode, slice, lastSlice, firstFragment))) {
                            s.outOpcode = -1;
                            return false;
                        }
                        currentOpcode = FrameOpcode.CONT;
                        firstFragment = false;
                    }
                }

                if (finalFragment) {
                    s.outOpcode = -1;
                }
                return true;
            } finally {
                s.writeLock.unlock();
            }
        }

        @Override
        public boolean sendBinaryBatch(final List<ByteBuffer> fragments, final boolean finalFragment) {
            if (!s.open.get() || s.closeSent.get() || fragments == null || fragments.isEmpty()) {
                return false;
            }
            s.writeLock.lock();
            try {
                int currentOpcode = s.outOpcode == -1 ? FrameOpcode.BINARY : FrameOpcode.CONT;
                if (s.outOpcode == -1) {
                    s.outOpcode = FrameOpcode.BINARY;
                }
                boolean firstFragment = currentOpcode != FrameOpcode.CONT;

                for (int i = 0; i < fragments.size(); i++) {
                    final ByteBuffer src = Args.notNull(fragments.get(i), "fragment").asReadOnlyBuffer();

                    while (src.hasRemaining()) {
                        if (!s.open.get() || s.closeSent.get()) {
                            s.outOpcode = -1;
                            return false;
                        }
                        final int n = Math.min(src.remaining(), s.outChunk);

                        final int oldLimit = src.limit();
                        final int newLimit = src.position() + n;
                        src.limit(newLimit);
                        final ByteBuffer slice = src.slice();
                        src.limit(oldLimit);
                        src.position(newLimit);

                        final boolean isLastFragment = i == fragments.size() - 1;
                        final boolean lastSlice = !src.hasRemaining() && isLastFragment && finalFragment;

                        if (!enqueueData(buildDataFrame(currentOpcode, slice, lastSlice, firstFragment))) {
                            s.outOpcode = -1;
                            return false;
                        }
                        currentOpcode = FrameOpcode.CONT;
                        firstFragment = false;
                    }
                }

                if (finalFragment) {
                    s.outOpcode = -1;
                }
                return true;
            } finally {
                s.writeLock.unlock();
            }
        }

        @Override
        public long queueSize() {
            return s.dataQueuedBytes.get();
        }

        @Override
        public CompletableFuture<Void> close(final int statusCode, final String reason) {
            final CompletableFuture<Void> future = new CompletableFuture<>();

            if (!s.open.get()) {
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

            if (!enqueueCtrl(pooledFrame(FrameOpcode.CLOSE, payload, true, false))) {
                future.completeExceptionally(
                        new IllegalStateException("WebSocket is closing or already closed"));
                return future;
            }

            // cfg.getCloseWaitTimeout() is a Timeout, IOSession.setSocketTimeout(Timeout)
            s.session.setSocketTimeout(s.cfg.getCloseWaitTimeout());
            future.complete(null);
            return future;
        }
    }

    private OutFrame buildDataFrame(final int opcode, final ByteBuffer payload, final boolean fin, final boolean firstFragment) {
        if (s.encChain == null) {
            return pooledFrame(opcode, payload, fin, true);
        }
        final byte[] plain = toBytes(payload);
        final WebSocketExtensionChain.Encoded enc =
                s.encChain.encode(plain, firstFragment, fin);
        final int rsv = enc.setRsvOnFirst && firstFragment ? s.rsvMask : 0;
        return pooledFrameWithRsv(opcode, ByteBuffer.wrap(enc.payload), fin, rsv, true);
    }

    private static byte[] toBytes(final ByteBuffer buf) {
        final ByteBuffer b = buf.asReadOnlyBuffer();
        final byte[] out = new byte[b.remaining()];
        b.get(out);
        return out;
    }
}
