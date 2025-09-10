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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.websocket.frame.FrameOpcode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OutboundFlowSupportTest {

    @Test
    void outboundByteLimitZeroMeansUnlimited() {
        Assertions.assertFalse(OutboundFlowSupport.exceedsOutboundByteLimit(0, Long.MAX_VALUE - 1, 1));
        Assertions.assertTrue(OutboundFlowSupport.exceedsOutboundByteLimit(8, 7, 2));
        Assertions.assertFalse(OutboundFlowSupport.exceedsOutboundByteLimit(8, 7, 1));
    }

    @Test
    void sendFragmentedSplitsAndResetsOpcodeOnFinal() {
        final List<Integer> opcodes = new ArrayList<>();
        final List<Boolean> fins = new ArrayList<>();

        final OutboundFlowSupport.SendResult result = OutboundFlowSupport.sendFragmented(
                FrameOpcode.TEXT,
                -1,
                ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5}),
                true,
                2,
                false,
                () -> true,
                (opcode, payload, fin, first) -> {
                    opcodes.add(opcode);
                    fins.add(fin);
                    return true;
                });

        Assertions.assertTrue(result.accepted());
        Assertions.assertEquals(-1, result.nextOpcode());
        Assertions.assertEquals(Arrays.asList(FrameOpcode.TEXT, FrameOpcode.CONT, FrameOpcode.CONT), opcodes);
        Assertions.assertEquals(Arrays.asList(false, false, true), fins);
    }

    @Test
    void sendFragmentedResetsOpcodeOnFailure() {
        final AtomicInteger seen = new AtomicInteger();
        final OutboundFlowSupport.SendResult result = OutboundFlowSupport.sendFragmented(
                FrameOpcode.BINARY,
                -1,
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                false,
                1,
                false,
                () -> true,
                (opcode, payload, fin, first) -> seen.incrementAndGet() == 1);

        Assertions.assertFalse(result.accepted());
        Assertions.assertEquals(-1, result.nextOpcode());
    }

    @Test
    void sendFragmentedCanEmitEmptyFrame() {
        final AtomicInteger seen = new AtomicInteger();
        final OutboundFlowSupport.SendResult result = OutboundFlowSupport.sendFragmented(
                FrameOpcode.TEXT,
                -1,
                ByteBuffer.allocate(0),
                true,
                1024,
                true,
                () -> true,
                (opcode, payload, fin, first) -> {
                    seen.incrementAndGet();
                    Assertions.assertEquals(FrameOpcode.TEXT, opcode);
                    Assertions.assertTrue(fin);
                    Assertions.assertTrue(first);
                    Assertions.assertEquals(0, payload.remaining());
                    return true;
                });

        Assertions.assertTrue(result.accepted());
        Assertions.assertEquals(1, seen.get());
        Assertions.assertEquals(-1, result.nextOpcode());
    }
}
