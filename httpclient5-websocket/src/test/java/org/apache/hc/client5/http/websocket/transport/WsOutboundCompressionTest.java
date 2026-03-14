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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.client5.http.websocket.api.WebSocket;
import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.apache.hc.core5.websocket.extension.PerMessageDeflate;
import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.junit.jupiter.api.Test;

final class WsOutboundCompressionTest {

    @Test
    void outboundPmce_setsRsv1_and_roundTrips() throws Exception {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .setOutgoingChunkSize(64 * 1024)
                .build();
        final ExtensionChain chain = new ExtensionChain();
        final PerMessageDeflate pmce = new PerMessageDeflate(true, true, true, null, null);
        chain.add(pmce);

        final WebSocketSessionState state = new WebSocketSessionState(dummySession(), new WebSocketListener() {
        }, cfg, chain);
        final WebSocketOutbound out = new WebSocketOutbound(state);
        final WebSocket ws = out.facade();

        final String text = "hello hello hello hello hello";
        assertTrue(ws.sendText(text, true));

        final WebSocketOutbound.OutFrame f = state.dataOutbound.poll();
        assertNotNull(f);

        final Frame frame = parseFrame(f.buf.asReadOnlyBuffer());
        assertEquals(FrameOpcode.TEXT, frame.opcode);
        assertTrue(frame.rsv1);
        assertTrue(frame.masked);

        final byte[] decoded = pmce.newDecoder().decode(frame.payload);
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), decoded);

        release(state, f);
    }

    @Test
    void outboundPmce_rsv1_onlyOnFirstFragment() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .build();
        final ExtensionChain chain = new ExtensionChain();
        chain.add(new PerMessageDeflate(true, true, true, null, null));

        final WebSocketSessionState state = new WebSocketSessionState(dummySession(), new WebSocketListener() {
        }, cfg, chain);
        final WebSocketOutbound out = new WebSocketOutbound(state);
        final WebSocket ws = out.facade();

        assertTrue(ws.sendTextBatch(Arrays.asList("alpha", "beta"), true));

        final WebSocketOutbound.OutFrame first = state.dataOutbound.poll();
        final WebSocketOutbound.OutFrame second = state.dataOutbound.poll();
        assertNotNull(first);
        assertNotNull(second);

        final Frame f1 = parseFrame(first.buf.asReadOnlyBuffer());
        final Frame f2 = parseFrame(second.buf.asReadOnlyBuffer());

        assertEquals(FrameOpcode.TEXT, f1.opcode);
        assertTrue(f1.rsv1);
        assertEquals(FrameOpcode.CONT, f2.opcode);
        assertFalse(f2.rsv1);

        release(state, first);
        release(state, second);
    }

    private static void release(final WebSocketSessionState state, final WebSocketOutbound.OutFrame f) {
    }

    private static ProtocolIOSession dummySession() {
        return (ProtocolIOSession) Proxy.newProxyInstance(
                ProtocolIOSession.class.getClassLoader(),
                new Class<?>[]{ProtocolIOSession.class},
                (proxy, method, args) -> {
                    final Class<?> rt = method.getReturnType();
                    if (rt == void.class) {
                        return null;
                    }
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    if (rt == float.class) {
                        return 0f;
                    }
                    if (rt == double.class) {
                        return 0d;
                    }
                    return null;
                });
    }

    private static Frame parseFrame(final ByteBuffer buf) {
        final int b0 = buf.get() & 0xFF;
        final int b1 = buf.get() & 0xFF;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = buf.getShort() & 0xFFFF;
        } else if (len == 127) {
            len = buf.getLong();
        }
        final boolean masked = (b1 & 0x80) != 0;
        final byte[] mask = masked ? new byte[4] : null;
        if (mask != null) {
            buf.get(mask);
        }
        final byte[] payload = new byte[(int) len];
        buf.get(payload);
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i & 3]);
            }
        }
        return new Frame(b0, masked, payload);
    }

    private static final class Frame {
        final int opcode;
        final boolean rsv1;
        final boolean masked;
        final byte[] payload;

        Frame(final int b0, final boolean masked, final byte[] payload) {
            this.opcode = b0 & 0x0F;
            this.rsv1 = (b0 & 0x40) != 0;
            this.masked = masked;
            this.payload = payload;
        }
    }
}
