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
package org.apache.hc.core5.websocket.extension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class ExtensionChainTest {

    @Test
    void addAndUsePmce_decodeRoundTrip() throws Exception {
        final ExtensionChain chain = new ExtensionChain();
        final PerMessageDeflate pmce = new PerMessageDeflate(true, true, true, null, null);
        chain.add(pmce);

        final byte[] data = "compress me please".getBytes(StandardCharsets.UTF_8);

        final WebSocketExtensionChain.Encoded enc = pmce.newEncoder().encode(data, true, true);
        final byte[] back = chain.newDecodeChain().decode(enc.payload);

        assertArrayEquals(data, back);
    }

    @Test
    void encodeChainClosesAllEncodersEvenWhenOneThrows() {
        final AtomicInteger closed = new AtomicInteger();
        final RuntimeException boom = new IllegalStateException("boom");
        final WebSocketExtensionChain.Encoder throwing = new WebSocketExtensionChain.Encoder() {
            @Override
            public WebSocketExtensionChain.Encoded encode(final byte[] data, final boolean first, final boolean fin) {
                return new WebSocketExtensionChain.Encoded(data, first);
            }

            @Override
            public void close() {
                throw boom;
            }
        };
        final WebSocketExtensionChain.Encoder counting = new WebSocketExtensionChain.Encoder() {
            @Override
            public WebSocketExtensionChain.Encoded encode(final byte[] data, final boolean first, final boolean fin) {
                return new WebSocketExtensionChain.Encoded(data, first);
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
        final ExtensionChain.EncodeChain chain = new ExtensionChain.EncodeChain(Arrays.asList(throwing, counting));

        final RuntimeException ex = assertThrows(RuntimeException.class, chain::close);

        assertSame(boom, ex);
        assertEquals(1, closed.get(), "the second encoder must still be closed");
    }

    @Test
    void decodeChainClosesAllDecodersEvenWhenOneThrows() {
        final AtomicInteger closed = new AtomicInteger();
        final RuntimeException boom = new IllegalStateException("boom");
        final WebSocketExtensionChain.Decoder throwing = new WebSocketExtensionChain.Decoder() {
            @Override
            public byte[] decode(final byte[] payload) {
                return payload;
            }

            @Override
            public void close() {
                throw boom;
            }
        };
        final WebSocketExtensionChain.Decoder counting = new WebSocketExtensionChain.Decoder() {
            @Override
            public byte[] decode(final byte[] payload) {
                return payload;
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
        final ExtensionChain.DecodeChain chain = new ExtensionChain.DecodeChain(Arrays.asList(throwing, counting));

        final RuntimeException ex = assertThrows(RuntimeException.class, chain::close);

        assertSame(boom, ex);
        assertEquals(1, closed.get(), "the second decoder must still be closed");
    }
}
