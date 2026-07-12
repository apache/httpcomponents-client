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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.apache.hc.core5.websocket.extension.PerMessageDeflate;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.junit.jupiter.api.Test;

/**
 * Reproduces two inbound RFC 6455 conformance gaps in the live NIO client engine that
 * {@link WebSocketInboundRfcTest} does not cover: a control frame carrying RSV1 while an extension
 * is negotiated (RFC 6455 §5.2), and delivery of a truncated over-limit message to the listener
 * after the connection has already been failed with 1009 (RFC 6455 §5.4 / §7.1.7).
 */
class WebSocketInboundRfcGapTest {

    @Test
    void rsv1OnControlFrameWithNegotiatedExtension_closesWith1002() {
        final CaptureListener listener = new CaptureListener();
        // permessage-deflate negotiated: the engine owns RSV1 for DATA frames, but a control frame
        // is never compressed, so RSV1 on a PING has no defined meaning and MUST fail the connection.
        final ExtensionChain chain = new ExtensionChain();
        chain.add(new PerMessageDeflate(true, false, false, null, null));
        final WebSocketSessionEngine engine = newEngine(listener, 0L, chain);

        engine.onData(controlFrameWithRsv1(FrameOpcode.PING));

        assertEquals(1002, listener.closeCode.get(),
                "RSV1 on a control frame must fail the connection with 1002");
    }

    @Test
    void overLimitFinalFragment_doesNotDeliverTruncatedMessageAfter1009() {
        final CaptureListener listener = new CaptureListener();
        final WebSocketSessionEngine engine = newEngine(listener, 4L, null);

        engine.onData(unmaskedFrame(FrameOpcode.BINARY, false, false, new byte[]{1, 2, 3}));
        engine.onData(unmaskedFrame(FrameOpcode.CONT, true, false, new byte[]{4, 5}));

        assertEquals(1009, listener.closeCode.get(), "over-limit message must fail with 1009");
        assertFalse(listener.messageDelivered.get(),
                "a message must not be delivered to the listener after the connection is failed");
    }

    private static WebSocketSessionEngine newEngine(final CaptureListener listener,
                                                    final long maxMessageSize,
                                                    final ExtensionChain chain) {
        final WebSocketClientConfig.Builder cfg = WebSocketClientConfig.custom()
                .enablePerMessageDeflate(false);
        if (maxMessageSize > 0) {
            cfg.setMaxMessageSize(maxMessageSize);
        }
        return new WebSocketSessionEngine(new StubTransport(), listener, cfg.build(), chain, null);
    }

    private static ByteBuffer unmaskedFrame(final int opcode, final boolean fin, final boolean rsv1,
                                            final byte[] payload) {
        final int len = payload != null ? payload.length : 0;
        final ByteBuffer buf = ByteBuffer.allocate(2 + len);
        buf.put((byte) ((fin ? 0x80 : 0x00) | (rsv1 ? 0x40 : 0x00) | (opcode & 0x0F)));
        buf.put((byte) len);
        if (len > 0) {
            buf.put(payload);
        }
        buf.flip();
        return buf;
    }

    private static ByteBuffer controlFrameWithRsv1(final int opcode) {
        return unmaskedFrame(opcode, true, true, new byte[0]);
    }

    private static final class CaptureListener implements WebSocketListener {
        private final AtomicInteger closeCode = new AtomicInteger(-1);
        private final AtomicBoolean messageDelivered = new AtomicBoolean(false);

        @Override
        public void onText(final CharBuffer data, final boolean last) {
            messageDelivered.set(true);
        }

        @Override
        public void onBinary(final ByteBuffer data, final boolean last) {
            messageDelivered.set(true);
        }

        @Override
        public void onClose(final int statusCode, final String reason) {
            closeCode.compareAndSet(-1, statusCode);
        }
    }
}
