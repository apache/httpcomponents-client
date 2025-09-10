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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.websocket.frame.FrameOpcode;

/**
 * Shared outbound queue and fragmentation helpers used by HTTP/1.1 and HTTP/2 WebSocket transports.
 */
@Internal
public final class OutboundFlowSupport {

    @FunctionalInterface
    public interface SendGate {
        boolean canSend();
    }

    @FunctionalInterface
    public interface FrameEnqueuer {
        boolean enqueue(int opcode, ByteBuffer payload, boolean fin, boolean firstFragment);
    }

    public static final class SendResult {
        private final boolean accepted;
        private final int nextOpcode;

        SendResult(final boolean accepted, final int nextOpcode) {
            this.accepted = accepted;
            this.nextOpcode = nextOpcode;
        }

        public boolean accepted() {
            return accepted;
        }

        public int nextOpcode() {
            return nextOpcode;
        }
    }

    private OutboundFlowSupport() {
    }

    public static boolean exceedsOutboundByteLimit(final long maxOutboundDataBytes, final long queuedBytes, final long frameBytes) {
        return maxOutboundDataBytes > 0 && queuedBytes + frameBytes > maxOutboundDataBytes;
    }

    public static SendResult sendFragmented(
            final int messageOpcode,
            final int currentOutOpcode,
            final ByteBuffer data,
            final boolean finalFragment,
            final int outChunk,
            final boolean emitEmptyFrame,
            final SendGate gate,
            final FrameEnqueuer enqueuer) {

        int nextOpcode = currentOutOpcode;
        int currentOpcode = nextOpcode == -1 ? messageOpcode : FrameOpcode.CONT;
        if (nextOpcode == -1) {
            nextOpcode = messageOpcode;
        }
        boolean firstFragment = currentOpcode != FrameOpcode.CONT;
        boolean ok = true;

        final ByteBuffer ro = data.asReadOnlyBuffer();
        boolean shouldEmit = emitEmptyFrame || ro.hasRemaining();
        while (shouldEmit) {
            if (!gate.canSend()) {
                ok = false;
                break;
            }

            final int n = Math.min(ro.remaining(), outChunk);
            final int oldLimit = ro.limit();
            final int newLimit = ro.position() + n;
            ro.limit(newLimit);
            final ByteBuffer slice = ro.slice();
            ro.limit(oldLimit);
            ro.position(newLimit);

            final boolean lastSlice = !ro.hasRemaining() && finalFragment;
            if (!enqueuer.enqueue(currentOpcode, slice, lastSlice, firstFragment)) {
                ok = false;
                break;
            }
            currentOpcode = FrameOpcode.CONT;
            firstFragment = false;
            shouldEmit = ro.hasRemaining();
        }

        if (finalFragment || !ok) {
            nextOpcode = -1;
        }
        return new SendResult(ok, nextOpcode);
    }
}
