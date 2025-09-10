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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.apache.hc.core5.websocket.extension.WebSocketExtensionChain;
import org.apache.hc.core5.websocket.frame.FrameHeaderBits;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.apache.hc.core5.websocket.frame.WebSocketFrameWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WsInboundCoverageTest {

    @Test
    void autoPongQueuesPong() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final CapturingListener listener = new CapturingListener();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, listener, cfg, null, null);

        final WebSocketFrameWriter writer = new WebSocketFrameWriter();
        final ByteBuffer ping = writer.frame(FrameOpcode.PING, ByteBuffer.wrap(new byte[] { 1 }), true, false);
        engine.onData(ping);

        Assertions.assertEquals(1, listener.pingCount.get());
        Assertions.assertFalse(engine.ctrlOutbound.isEmpty());
    }

    @Test
    void fragmentedControlFrameTriggersClose() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, new CapturingListener(), cfg, null, null);

        final ByteBuffer badPing = ByteBuffer.allocate(2);
        badPing.put((byte) FrameOpcode.PING);
        badPing.put((byte) 0);
        badPing.flip();

        engine.onData(badPing);

        Assertions.assertTrue(engine.closeSent.get());
    }

    @Test
    void continuationWithoutStartTriggersClose() {
        final CapturingListener listener = new CapturingListener();
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, listener, cfg, null, null);

        final WebSocketFrameWriter writer = new WebSocketFrameWriter();
        final ByteBuffer cont = writer.frame(FrameOpcode.CONT, ByteBuffer.allocate(0), true, false);
        engine.onData(cont);

        Assertions.assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void closeFrameLengthOneIsRejected() {
        final CapturingListener listener = new CapturingListener();
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, listener, cfg, null, null);

        final WebSocketFrameWriter writer = new WebSocketFrameWriter();
        final ByteBuffer close = writer.frame(FrameOpcode.CLOSE, ByteBuffer.wrap(new byte[] { 1 }), true, false);
        engine.onData(close);

        Assertions.assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void oversizedMessageTriggersClose() {
        final CapturingListener listener = new CapturingListener();
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().setMaxMessageSize(1).build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, listener, cfg, null, null);

        final WebSocketFrameWriter writer = new WebSocketFrameWriter();
        final ByteBuffer text = writer.frame(FrameOpcode.TEXT, ByteBuffer.wrap(new byte[] { 1, 2 }), true, false);
        engine.onData(text);

        Assertions.assertEquals(1009, listener.closeCode.get());
    }

    @Test
    void rsv1WithoutExtensionTriggersClose() {
        final CapturingListener listener = new CapturingListener();
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, listener, cfg, null, null);

        final WebSocketFrameWriter writer = new WebSocketFrameWriter();
        final ByteBuffer text = writer.frameWithRSV(FrameOpcode.TEXT, ByteBuffer.wrap(new byte[] { 1 }), true, false,
                FrameHeaderBits.RSV1);
        engine.onData(text);

        Assertions.assertEquals(1002, listener.closeCode.get());
    }

    @Test
    void fragmentedCompressedDecodeFailureTriggersClose() {
        final CapturingListener listener = new CapturingListener();
        final ExtensionChain chain = new ExtensionChain();
        chain.add(new WebSocketExtensionChain() {
            @Override
            public int rsvMask() {
                return FrameHeaderBits.RSV1;
            }

            @Override
            public Encoder newEncoder() {
                return (data, first, fin) -> new Encoded(data, first);
            }

            @Override
            public Decoder newDecoder() {
                return payload -> {
                    throw new IllegalStateException("decode failure");
                };
            }
        });
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final StubTransport transport = new StubTransport();
        final WebSocketSessionEngine engine =
                new WebSocketSessionEngine(transport, listener, cfg, chain, null);

        final WebSocketFrameWriter writer = new WebSocketFrameWriter();
        final ByteBuffer first = writer.frameWithRSV(FrameOpcode.TEXT, ByteBuffer.wrap(new byte[] { 1 }), false, false,
                FrameHeaderBits.RSV1);
        final ByteBuffer cont = writer.frame(FrameOpcode.CONT, ByteBuffer.wrap(new byte[] { 2 }), true, false);
        engine.onData(first);
        engine.onData(cont);

        Assertions.assertEquals(1007, listener.closeCode.get());
        Assertions.assertTrue(engine.closeSent.get());
    }

    private static final class CapturingListener implements WebSocketListener {
        private final AtomicInteger pingCount = new AtomicInteger();
        private final AtomicInteger closeCode = new AtomicInteger(-1);
        private final AtomicReference<CharBuffer> text = new AtomicReference<>();

        @Override
        public void onPing(final ByteBuffer data) {
            pingCount.incrementAndGet();
        }

        @Override
        public void onText(final CharBuffer data, final boolean last) {
            text.set(data);
        }

        @Override
        public void onClose(final int statusCode, final String reason) {
            closeCode.compareAndSet(-1, statusCode);
        }
    }
}
